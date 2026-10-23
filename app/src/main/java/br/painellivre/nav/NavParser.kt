package br.painellivre.nav

import java.text.Normalizer
import java.util.Locale

/** Resultado da leitura de uma notificação de navegação (Google Maps / Waze). */
data class ParsedNav(
    val maneuver: Maneuver,
    val roundaboutExit: Int?,
    val distanceMeters: Int?,
    val distanceText: String?,
    val instruction: String,
    val street: String?,
    val etaClock: String?,
    val remainingSeconds: Int?,
    val remainingMeters: Int?,
    val remainingDistanceText: String?,
)

/**
 * Converte o texto das notificações de navegação em manobra + distância + rua + chegada.
 *
 * Não depende de Android (testável na JVM). As heurísticas seguem o que o DashCast aprendeu
 * com notificações reais (distância é procurada no título, depois no texto; o resumo da rota
 * fica no subText), com tabela de palavras-chave em português do Brasil + inglês.
 */
object NavParser {

    /** Somente nomes semânticos de recursos; IDs numéricos e pixels variam por versão. */
    fun iconManeuver(resourceName: String?): Maneuver {
        val name = resourceName?.lowercase(Locale.ROOT) ?: return Maneuver.UNKNOWN
        val tokens = name.split('_', '-', '.')
        if (tokens.none { it in setOf("turn", "maneuver", "straight", "roundabout", "uturn") }) return Maneuver.UNKNOWN
        return classify(name.replace('_', ' ').replace('-', ' '))
    }

    private val RX_DIST = Regex(
        """(?<![\d.,])(\d+(?:[.,]\d+)?)\s*(km|mi|ft|yd|m)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val RX_HOURS = Regex("""\b(\d+)\s*(?:h|hr|hrs|hora|horas|hour|hours)\b""", RegexOption.IGNORE_CASE)
    private val RX_MINS = Regex("""\b(\d+)\s*(?:min|mins|minuto|minutos|minute|minutes)\b""", RegexOption.IGNORE_CASE)
    private val RX_CLOCK = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\s*(am|pm|a\.m\.|p\.m\.)?""", RegexOption.IGNORE_CASE)

    // Rua: preposição + nome que começa com maiúscula ou número (Rua, Av., R., BR-116, SP-055...).
    private val RX_STREET = Regex(
        """(?:^|\s)(?:na|no|nas|nos|pela|pelo|para a|para o|para|em direção à|em direção ao|em direção a|sentido|rumo à|rumo ao|rumo a|onto|on|toward|towards|to)\s+([A-ZÁÂÃÀÉÊÍÓÔÕÚÜÇ0-9][^·•|]*)""",
    )
    private val RX_TRAILING_DIST = Regex("""\s+(?:em|in|após|after)\s+\d+(?:[.,]\d+)?\s*(?:km|mi|ft|yd|m)\b.*$""", RegexOption.IGNORE_CASE)

    private val RX_EXIT_NUM = Regex("""(\d+)\s*(?:ª|º|a|o|st|nd|rd|th)?\s*(?:saida|exit)""")
    private val RX_EXIT_NUM2 = Regex("""(?:saida|exit)\s*(\d+)""")
    private val ORDINALS = listOf(
        "primeira" to 1, "segunda" to 2, "terceira" to 3, "quarta" to 4, "quinta" to 5, "sexta" to 6,
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6,
    )

    private val SKIP = listOf(
        "toque para", "tap to", "em segundo plano", "running in the background", "app is running",
        "procurando gps", "buscando gps", "searching for gps", "sinal de gps", "gps signal",
    )

    fun parse(title: String?, text: String?, bigText: String?, subText: String?, iconHint: Maneuver = Maneuver.UNKNOWN): ParsedNav? {
        val t = clean(title)
        val x = clean(text)
        val b = clean(bigText)
        val s = clean(subText)
        val primary = listOf(t, x, b).filter { it.isNotEmpty() }.distinct()
        if (primary.isEmpty()) return null
        val combined = primary.joinToString(" · ")
        val flat = fold(combined)
        if (SKIP.any { flat.contains(it) }) return null

        // 1. Distância até a manobra: título primeiro (Google Maps põe "300 m" no título).
        //    Linhas de resumo ("12 min · 5 km · 14:35") nunca contam como distância da manobra.
        var distMeters: Int? = null
        var distText: String? = null
        for (line in primary.filterNot { isSummary(it) }) {
            val m = RX_DIST.find(line) ?: continue
            distMeters = toMeters(m.groupValues[1], m.groupValues[2])
            distText = m.value.trim().replace(Regex("""\s+"""), " ")
            break
        }

        // 2. Manobra
        val maneuver = classify(flat).let { if (it == Maneuver.UNKNOWN) iconHint else it }
        val exit = if (maneuver == Maneuver.ROUNDABOUT) roundaboutExit(flat) else null
        if (maneuver == Maneuver.UNKNOWN && distMeters == null) return null

        // 3. Instrução = a linha que descreve a manobra (sem a distância isolada).
        val lines = primary.filterNot { isSummary(it) }
        val instruction = lines.firstOrNull { classify(fold(it)) != Maneuver.UNKNOWN }
            ?: lines.filterNot { isOnlyDistance(it) }.maxByOrNull { it.length }
            ?: combined

        // 4. Rua
        val street = streetFrom(lines)

        // 5. Resumo da rota (tempo/distância restante e horário de chegada).
        val summary = when {
            s.isNotEmpty() -> s
            else -> primary.firstOrNull { isSummary(it) } ?: ""
        }
        val remSec = remainingSeconds(summary)
        val remDistMatch = if (summary.isNotEmpty()) RX_DIST.find(summary) else null
        val eta = RX_CLOCK.find(summary)?.let { formatClock(it) }

        return ParsedNav(
            maneuver = maneuver,
            roundaboutExit = exit,
            distanceMeters = distMeters,
            distanceText = distText,
            instruction = instruction,
            street = street,
            etaClock = eta,
            remainingSeconds = remSec,
            remainingMeters = remDistMatch?.let { toMeters(it.groupValues[1], it.groupValues[2]) },
            remainingDistanceText = remDistMatch?.value?.trim()?.replace(Regex("""\s+"""), " "),
        )
    }

    // ── Classificação da manobra ────────────────────────────────────────────

    private val DEST = listOf(
        "voce chegou", "chegou ao destino", "chegou a seu destino", "chegando ao destino", "seu destino",
        "o destino esta", "destino a direita", "destino a esquerda", "destino fica",
        "you have arrived", "you've arrived", "your destination", "arrive at", "destination",
    )
    private val FERRY = listOf("balsa", "ferry")
    private val UTURN = listOf("faca o retorno", "faca retorno", "fazer o retorno", "retorno", "meia-volta", "meia volta", "u-turn", "uturn", "make a u")
    private val ROUND = listOf("rotatoria", "rotunda", "balao", "roundabout", "traffic circle")
    private val SHARP = listOf("acentuad", "fortemente", "bruscamente", "curva fechada", "sharp")
    private val SLIGHT = listOf("ligeiramente", "levemente", "suavemente", "curva suave", "slight", "bear ")
    private val KEEP = listOf("mantenha-se", "mantenha se", "mantenha a", "mantenha na", "permaneca", "fique a", "fique na", "siga pela", "use a faixa", "keep ")
    private val EXIT = listOf("saida", "saia ", "acesso", "alca", "rampa", "exit", "ramp", "take the")
    private val MERGE = listOf("incorpore", "converja", "una-se", "entre na rodovia", "merge")
    private val TURN = listOf("vire", "dobre", "entre a ", "entre na ", "pegue a ", "curva a", "turn ")
    private val TOLL = listOf("pedagio", "tollbooth", "toll ")
    private val STRAIGHT = listOf("siga em frente", "siga reto", "em frente", "continue", "prossiga", "siga ", "head ", "straight")

    private fun side(s: String): Int {
        val r = listOf(s.indexOf("direita"), s.indexOf(" right")).filter { it >= 0 }.minOrNull() ?: -1
        val l = listOf(s.indexOf("esquerda"), s.indexOf(" left")).filter { it >= 0 }.minOrNull() ?: -1
        return when {
            r < 0 && l < 0 -> 0
            r < 0 -> -1
            l < 0 -> 1
            else -> if (l < r) -1 else 1
        }
    }

    /** [s] já em minúsculas e sem acentos (ver [fold]). Ordem importa: do mais específico ao genérico. */
    fun classify(s: String): Maneuver {
        val sd = side(s)
        fun has(words: List<String>) = words.any { s.contains(it) }
        return when {
            has(DEST) || (s.contains("destino") && !s.contains("em direcao")) -> Maneuver.DESTINATION
            has(FERRY) -> Maneuver.FERRY
            has(UTURN) -> if (sd > 0) Maneuver.UTURN_RIGHT else Maneuver.UTURN_LEFT
            has(ROUND) -> Maneuver.ROUNDABOUT
            has(SHARP) && sd != 0 -> if (sd < 0) Maneuver.SHARP_LEFT else Maneuver.SHARP_RIGHT
            has(SLIGHT) && sd != 0 -> if (sd < 0) Maneuver.SLIGHT_LEFT else Maneuver.SLIGHT_RIGHT
            has(KEEP) && sd != 0 -> if (sd < 0) Maneuver.KEEP_LEFT else Maneuver.KEEP_RIGHT
            has(EXIT) -> if (sd < 0) Maneuver.EXIT_LEFT else Maneuver.EXIT_RIGHT
            has(MERGE) -> Maneuver.MERGE
            has(TURN) && sd != 0 -> if (sd < 0) Maneuver.LEFT else Maneuver.RIGHT
            has(TOLL) -> Maneuver.TOLL
            sd < 0 -> Maneuver.LEFT
            sd > 0 -> Maneuver.RIGHT
            has(STRAIGHT) -> Maneuver.STRAIGHT
            else -> Maneuver.UNKNOWN
        }
    }

    private fun roundaboutExit(s: String): Int? {
        RX_EXIT_NUM.find(s)?.let { return it.groupValues[1].toIntOrNull()?.takeIf { n -> n in 1..12 } }
        RX_EXIT_NUM2.find(s)?.let { return it.groupValues[1].toIntOrNull()?.takeIf { n -> n in 1..12 } }
        return ORDINALS.firstOrNull { (w, _) -> s.contains("$w saida") || s.contains("$w exit") }?.second
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────

    private fun streetFrom(lines: List<String>): String? {
        for (line in lines) {
            val m = RX_STREET.find(line) ?: continue
            val v = m.groupValues[1].replace(RX_TRAILING_DIST, "").trim().trimEnd('.', ',', ';')
            if (v.length >= 2 && !isOnlyDistance(v)) return v
        }
        // Linha que é só o nome da rua (o Google Maps às vezes manda assim no texto).
        return lines.firstOrNull { l ->
            !isOnlyDistance(l) && classify(fold(l)) == Maneuver.UNKNOWN &&
                l.first().isUpperCase() && !RX_MINS.containsMatchIn(l) && !RX_CLOCK.containsMatchIn(l)
        }
    }

    private fun isSummary(s: String): Boolean =
        (s.contains("·") || s.contains("•")) && (RX_MINS.containsMatchIn(s) || RX_CLOCK.containsMatchIn(s))

    private fun isOnlyDistance(s: String): Boolean {
        val rest = RX_DIST.replace(s, "").replace(Regex("""(?i)\b(em|in|daqui a|após|after)\b"""), "").trim()
        return RX_DIST.containsMatchIn(s) && rest.none { it.isLetter() }
    }

    private fun remainingSeconds(s: String): Int? {
        if (s.isEmpty()) return null
        val h = RX_HOURS.find(s)?.groupValues?.get(1)?.toIntOrNull()
        val m = RX_MINS.find(s)?.groupValues?.get(1)?.toIntOrNull()
        if (h == null && m == null) return null
        return (h ?: 0) * 3600 + (m ?: 0) * 60
    }

    private fun formatClock(m: MatchResult): String {
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2]
        val ampm = m.groupValues[3].lowercase(Locale.ROOT)
        if (ampm.startsWith("p") && h < 12) h += 12
        if (ampm.startsWith("a") && h == 12) h = 0
        return String.format(Locale.ROOT, "%02d:%s", h, min)
    }

    fun toMeters(num: String, unit: String): Int? {
        val u = unit.lowercase(Locale.ROOT)
        val value: Double = if (u == "m" || u == "ft" || u == "yd") {
            // "1.200 m" / "1,200 ft" = separador de milhar; "1,5 m" não ocorre em navegação.
            val n = if (Regex("""^\d{1,3}[.,]\d{3}$""").matches(num)) num.replace(Regex("[.,]"), "") else num.replace(',', '.')
            n.toDoubleOrNull() ?: return null
        } else {
            num.replace(',', '.').toDoubleOrNull() ?: return null
        }
        val meters = when (u) {
            "km" -> value * 1000
            "mi" -> value * 1609.344
            "ft" -> value * 0.3048
            "yd" -> value * 0.9144
            else -> value
        }
        return meters.toInt()
    }

    private fun clean(s: String?): String =
        (s ?: "").replace(' ', ' ').replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

    /** minúsculas + sem acentos, para comparar com as palavras-chave. */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
}
