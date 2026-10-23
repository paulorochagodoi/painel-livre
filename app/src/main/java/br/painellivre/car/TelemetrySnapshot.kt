package br.painellivre.car

import br.painellivre.car.TelemetryCatalog.Kind
import br.painellivre.car.TelemetryCatalog.Signal
import java.util.Locale

/**
 * **Kotlin puro.** Uma varredura da telemetria (issue #16): a resposta crua de cada sinal, o valor
 * decodificado e os estados que as telas mostram (portas, trava, carga…). Valor que não passa na
 * faixa plausível vira null: "sem dado" nunca vira zero nem "fechado".
 */
data class TelemetrySnapshot(
    val status: Status,
    val message: String = status.label,
    val readings: Map<String, Reading> = emptyMap(),
    val atMillis: Long = 0L,
    val protocol: Autoservice.Protocol = Autoservice.Protocol.UNKNOWN,
) {
    enum class Status(val label: String) {
        OFF("Leitura dos dados do carro desligada (Ajustes)"),
        WAITING("Lendo os dados do carro…"),
        OK("Dados do carro pelo ADB local"),
        NO_ADB("Sem ADB local: não dá para ler os dados do carro"),
        NO_SERVICE("Esta central não tem o serviço de dados da BYD"),
        BLOCKED("Serviço de dados com interface desconhecida: leitura bloqueada por segurança"),
        ERROR("Falha ao ler os dados do carro"),
    }

    data class Reading(val signal: Signal, val fid: Int, val reply: Autoservice.Reply, val value: Double?) {
        /** Texto para o registro do debug: endereço, resposta crua e valor. */
        fun debugLine(): String {
            val raw = when (reply) {
                is Autoservice.Reply.Value -> Autoservice.hex(reply.word) + " (${reply.word})"
                is Autoservice.Reply.Failure -> "exceção ${Autoservice.hex(reply.status)}"
                Autoservice.Reply.NoService -> "sem serviço"
                is Autoservice.Reply.Garbled -> "sem resposta${if (reply.text.isNotBlank()) ": ${reply.text}" else ""}"
            }
            val shown = value?.let { TelemetryText.format(signal.kind, it) } ?: "indisponível"
            return "${signal.key} [${signal.dev}/${Autoservice.hex(fid)} tx${signal.tx}] ${signal.label}: $raw → $shown (${signal.confidence.label})"
        }
    }

    fun value(key: String): Double? = readings[key]?.value
    private fun int(key: String): Int? = value(key)?.toInt()

    val hasData get() = readings.values.any { it.value != null }

    /** true aberta, false fechada, null sem dado. */
    fun isOpen(key: String): Boolean? = int(key)?.let { it == 1 }

    /** Portas, capô e porta-malas abertos (nomes para a tela). */
    val openings: List<String> get() = OPENING_KEYS.filter { isOpen(it) == true }.map { TelemetryCatalog.byKey(it).label }

    /** true trancado, false destrancado, null sem dado (valores 1/2: hipótese, docs/TELEMETRIA.md). */
    val locked: Boolean? get() = when (int("lock")) { 2 -> true; 1 -> false; else -> null }

    val chargeLidOpen: Boolean? get() = isOpen("charge_lid")

    /** Cabo conectado (AC, DC, AC+DC ou V2L). */
    val plugged: Boolean? get() = int("gun")?.let { it in 2..5 }

    /** Carregando: o BMS diz 1 ou, com cabo, há potência de carga. */
    val charging: Boolean? get() {
        val bms = int("bms")
        if (bms != null) return bms == 1
        val kw = value("charge_kw") ?: return null
        return plugged == true && kw > 0.3
    }

    val chargeKw: Double? get() = value("charge_kw")?.takeIf { charging == true && it > 0 }

    /** Tempo restante da carga em minutos (horas + minutos do painel). */
    val chargeRemainingMin: Int? get() {
        if (charging != true) return null
        val h = int("charge_h") ?: return null
        val m = int("charge_min") ?: return null
        return (h * 60 + m).takeIf { it > 0 }
    }

    val socPercent: Double? get() = value("soc")
    val evRangeKm: Int? get() = int("ev_range")
    val fuelRangeKm: Int? get() = int("fuel_range")

    /** Algo que vale um aviso no painel: abertura, tampa de carga aberta ou carregando. */
    val notable: Boolean get() = openings.isNotEmpty() || chargeLidOpen == true || charging == true

    companion object {
        val OPENING_KEYS = listOf("door_fl", "door_fr", "door_rl", "door_rr", "hood", "trunk")

        /** Valor útil da palavra conforme o tipo, ou null (sentinela, fora da faixa). */
        fun decode(kind: Kind, word: Int): Double? {
            if (kind.float) {
                val f = Autoservice.floatValue(word)?.toDouble() ?: return null
                return when (kind) {
                    Kind.PERCENT_FLOAT -> f.takeIf { it in 0.0..100.0 }
                    Kind.KW_FLOAT -> f.takeIf { it in -1.0..400.0 }
                    Kind.VOLT_FLOAT -> f.takeIf { it in 5.0..20.0 }
                    else -> f
                }
            }
            val v = Autoservice.intValue(word) ?: return null
            val ok = when (kind) {
                Kind.OPEN_FLAG -> v == 0 || v == 1
                Kind.LOCK -> v == 1 || v == 2
                Kind.GUN -> v in 1..5
                Kind.BMS -> v in 0..255
                Kind.PERCENT -> v in 0..100
                Kind.KM -> v in 0..3000
                Kind.TEMP -> v in -40..120
                Kind.TEMP_SET -> v in 14..36
                Kind.KPA -> v in 50..600
                Kind.KW -> v in -600..600
                Kind.HOURS -> v in 0..72
                Kind.MINUTES -> v in 0..59
                Kind.GEAR -> v in 1..4
                Kind.SEAT_LEVEL -> v in 0..5
                Kind.LEVEL -> v in 0..10
                Kind.RAW -> true
                Kind.PERCENT_FLOAT, Kind.KW_FLOAT, Kind.VOLT_FLOAT -> false
            }
            return if (ok) v.toDouble() else null
        }

        fun reading(signal: Signal, fid: Int, reply: Autoservice.Reply): Reading =
            Reading(signal, fid, reply, (reply as? Autoservice.Reply.Value)?.let { decode(signal.kind, it.word) })
    }
}

/** **Kotlin puro.** Textos curtos em português para os cartões e o registro. */
object TelemetryText {
    private val BR = Locale("pt", "BR")

    fun format(kind: Kind, v: Double): String = when (kind) {
        Kind.OPEN_FLAG -> if (v == 1.0) "aberta" else "fechada"
        Kind.LOCK -> if (v == 2.0) "trancado" else "destrancado"
        Kind.GUN -> when (v.toInt()) { 1 -> "sem cabo"; 2 -> "cabo AC"; 3 -> "cabo DC"; 4 -> "cabo AC+DC"; else -> "V2L" }
        Kind.BMS -> when (v.toInt()) { 1 -> "carregando"; 2 -> "carga completa"; 13 -> "pausada"; else -> "estado ${v.toInt()}" }
        Kind.PERCENT_FLOAT, Kind.PERCENT -> "${v.toInt()} %"
        Kind.KM -> "${v.toInt()} km"
        Kind.TEMP, Kind.TEMP_SET -> "${v.toInt()} °C"
        Kind.KPA -> String.format(BR, "%.1f bar", v / 100.0)
        Kind.KW_FLOAT -> String.format(BR, "%.1f kW", v)
        Kind.KW -> "${v.toInt()} kW"
        Kind.HOURS -> "${v.toInt()} h"
        Kind.MINUTES -> "${v.toInt()} min"
        Kind.VOLT_FLOAT -> String.format(BR, "%.1f V", v)
        Kind.GEAR -> when (v.toInt()) { 1 -> "P"; 2 -> "R"; 3 -> "N"; else -> "D" }
        Kind.SEAT_LEVEL -> seatLevel(v.toInt())
        Kind.LEVEL, Kind.RAW -> v.toInt().toString()
    }

    /** 1 = desligado, 2… = nível 1… (hipótese, docs/TELEMETRIA.md). */
    fun seatLevel(raw: Int): String = if (raw <= 1) "desligado" else "nível ${raw - 1}"

    fun duration(min: Int): String = if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)} min" else "$min min"

    /** Linha da carga: "Carregando · 6,6 kW · 1 h 20 min", "Cabo AC conectado" ou null. */
    fun charge(s: TelemetrySnapshot): String? = when {
        s.charging == true -> listOfNotNull(
            "Carregando",
            s.chargeKw?.let { format(Kind.KW_FLOAT, it) },
            s.chargeRemainingMin?.let(::duration),
        ).joinToString(" · ")
        s.plugged == true -> "Cabo conectado, sem carregar"
        else -> null
    }

    /** Título do cartão "Carro" na central. */
    fun headline(s: TelemetrySnapshot): String = panel(s) ?: when (s.locked) {
        true -> if (openingsKnown(s)) "Tudo fechado e trancado" else "Trancado"
        false -> if (s.openings.isEmpty() && openingsKnown(s)) "Fechado, destrancado" else "Destrancado"
        null -> if (openingsKnown(s)) "Portas fechadas" else "Carro"
    }

    private fun openingsKnown(s: TelemetrySnapshot) = TelemetrySnapshot.OPENING_KEYS.all { s.isOpen(it) == false }

    /** Aviso compacto do painel (só o que importa ao motorista). */
    fun panel(s: TelemetrySnapshot): String? {
        val open = s.openings
        return when {
            open.size == 1 -> "${open[0]} aberta".replace("Capô aberta", "Capô aberto").replace("Porta-malas aberta", "Porta-malas aberto")
            open.size > 1 -> "${open.size} aberturas: ${open.joinToString(", ") { it.lowercase(BR) }}"
            s.chargeLidOpen == true && s.charging != true -> "Tampa do carregador aberta"
            else -> charge(s)
        }
    }

    /** Linhas do cartão "Carro" na central: clima, bancos, pneus, vidros. Só as que têm dado. */
    fun details(s: TelemetrySnapshot): List<String> {
        val lines = mutableListOf<String>()
        val td = s.value("ac_temp_driver"); val tp = s.value("ac_temp_pass")
        val out = s.value("temp_out"); val inside = s.value("temp_in")
        val climate = listOfNotNull(
            when {
                td != null && tp != null && td != tp -> "${td.toInt()}° / ${tp.toInt()}°"
                td != null -> "${td.toInt()}°"
                else -> null
            },
            s.value("ac_fan")?.takeIf { it > 0 }?.let { "ventilador ${it.toInt()}" },
            inside?.let { "dentro ${it.toInt()}°" },
            out?.let { "fora ${it.toInt()}°" },
        )
        if (climate.isNotEmpty()) lines += "Clima: " + climate.joinToString(" · ")

        val seats = listOfNotNull(
            seat(s, "seat_heat_driver", "aquec. motorista"), seat(s, "seat_vent_driver", "vent. motorista"),
            seat(s, "seat_heat_pass", "aquec. passageiro"), seat(s, "seat_vent_pass", "vent. passageiro"),
        )
        if (seats.isNotEmpty()) lines += "Bancos: " + seats.joinToString(" · ")

        val tyres = listOf("fl", "fr", "rl", "rr").map { s.value("tyre_p_$it") }
        if (tyres.any { it != null }) {
            lines += "Pneus (bar): " + tyres.joinToString(" ") { v -> v?.let { String.format(BR, "%.1f", it / 100.0) } ?: "—" }
        }

        val windows = listOf("win_fl", "win_fr", "win_rl", "win_rr", "sunroof").mapNotNull { k ->
            s.value(k)?.takeIf { it > 0 }?.let { "${TelemetryCatalog.byKey(k).label.lowercase(BR)} ${it.toInt()} %" }
        }
        val allWindowsClosed = listOf("win_fl", "win_fr", "win_rl", "win_rr").all { s.value(it) == 0.0 }
        if (windows.isNotEmpty()) lines += "Abertos: " + windows.joinToString(" · ")
        else if (allWindowsClosed) lines += "Vidros fechados"
        return lines
    }

    private fun seat(s: TelemetrySnapshot, key: String, label: String): String? =
        s.value(key)?.toInt()?.takeIf { it > 1 }?.let { "$label ${it - 1}" }

    /** Texto do Diagnóstico: um sinal por linha. */
    fun diagnostic(s: TelemetrySnapshot): String = buildString {
        append(s.message)
        if (s.readings.isNotEmpty()) append(" (protocolo ${s.protocol})")
        for (r in s.readings.values) {
            append('\n').append(r.signal.label).append(": ")
            append(r.value?.let { format(r.signal.kind, it) } ?: "indisponível")
        }
    }
}
