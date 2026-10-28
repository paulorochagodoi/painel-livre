package br.painellivre.debug

/**
 * **Kotlin puro.** Leitura das saídas de shell que o modo de debug coleta (issue #9) e o que mudou
 * entre uma coleta e a seguinte. A ideia: o motorista troca Simples/Pequeno/Cheio/OFF no menu do
 * painel e o registro mostra qual setting, propriedade, broadcast ou linha de logcat mudou junto.
 *
 * Tudo é só leitura (`settings list`, `getprop`, `dumpsys`, `logcat -d`). Nada de `settings put`,
 * `setprop`, broadcast adivinhado ou `service call` cru (o número da transação do `autoservice` na
 * DiLink 3 não está confirmado e poderia ser um setter).
 */
object ProbeParse {

    /** Seções do comando combinado, separadas por linhas `@@NOME`. */
    val SECTIONS = listOf("SYSTEM", "GLOBAL", "SECURE", "PROPS", "BCAST", "LOGCAT")

    /** Palavras que marcam um item como candidato (★) a ser o aviso do menu do painel. */
    private val CANDIDATE = Regex(
        "navi|instrument|cluster|amap|autonavi|meter|hud|fission|xdja|dashboard|screen[._]?(current|mode)|1086337074|40c03032",
        RegexOption.IGNORE_CASE,
    )

    /** Mesmo filtro, para o `grep -i -E` do logcat (sem os grupos, que o grep do toybox aceita igual). */
    const val LOGCAT_GREP = "navi|instrument|cluster|amap|autonavi|fission|xdja|1086337074|40c03032"

    /** Um comando só, para uma conexão ADB por coleta. Só comandos de leitura. */
    val COLLECT_COMMAND: String = listOf(
        "echo @@SYSTEM", "settings list system 2>&1",
        "echo @@GLOBAL", "settings list global 2>&1",
        "echo @@SECURE", "settings list secure 2>&1",
        "echo @@PROPS", "getprop 2>&1",
        // Resumo do histórico de broadcasts: "#N: act=… flg=…" seguido de "enq=data hora …".
        "echo @@BCAST", "dumpsys activity broadcasts history 2>&1 | grep -E '^ *#[0-9]+: |enq='",
        "echo @@LOGCAT",
        "logcat -d -v time -t 1500 2>&1 | grep -i -E '$LOGCAT_GREP' | grep -v PainelLivre | tail -n 200",
    ).joinToString("; ")

    fun isCandidate(s: String) = CANDIDATE.containsMatchIn(s)

    fun sections(out: String): Map<String, String> {
        val result = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in out.lines()) {
            val name = line.trim().takeIf { it.startsWith("@@") }?.removePrefix("@@")
            if (name != null && name in SECTIONS) {
                current = StringBuilder().also { result[name] = it }
                continue
            }
            current?.append(line)?.append('\n')
        }
        return result.mapValues { it.value.toString().trimEnd('\n') }
    }

    /** `settings list`: "chave=valor" (valor pode ter "="). */
    fun settings(out: String): Map<String, String> = out.lines().mapNotNull { l ->
        val i = l.indexOf('=')
        if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1)
    }.toMap()

    private val PROP = Regex("""^\[(.+?)]: \[(.*)]$""")

    /** `getprop`: "[chave]: [valor]". */
    fun getprop(out: String): Map<String, String> = out.lines().mapNotNull { l ->
        PROP.find(l.trim())?.let { it.groupValues[1] to it.groupValues[2] }
    }.toMap()

    data class Change(val key: String, val old: String?, val new: String?) {
        fun describe(): String = when {
            old == null -> "$key = ${new.orEmpty()} (nova)"
            new == null -> "$key removida (era $old)"
            else -> "$key: $old → $new"
        }
    }

    fun diff(old: Map<String, String>, new: Map<String, String>): List<Change> =
        (old.keys + new.keys).sorted().mapNotNull { k ->
            val a = old[k]
            val b = new[k]
            if (a == b) null else Change(k, a, b)
        }

    data class Broadcast(val action: String, val enqueued: String, val line: String) {
        val key get() = "$enqueued|$line"
    }

    private val ACT = Regex("""act=(\S+)""")
    private val ENQ = Regex("""enq=(\S+ \S+)""")
    private val ENTRY = Regex("""^\s*#\d+:\s*(.*)$""")

    /** Resumo de `dumpsys activity broadcasts history` (já filtrado pelo grep do [COLLECT_COMMAND]). */
    fun broadcastHistory(out: String): List<Broadcast> {
        val list = mutableListOf<Broadcast>()
        var pending: String? = null
        for (raw in out.lines()) {
            val entry = ENTRY.find(raw)
            if (entry != null) {
                pending = entry.groupValues[1].trim()
                continue
            }
            val enq = ENQ.find(raw) ?: continue
            val line = pending ?: continue
            list += Broadcast(ACT.find(line)?.groupValues?.get(1) ?: "(sem ação)", enq.groupValues[1], line)
            pending = null
        }
        return list
    }

    private val LONG_DIGITS = Regex("""\+?\d[\d \-]{6,}\d""")
    private val MAC = Regex("""(?i)\b[0-9a-f]{2}([:\-])(?:[0-9a-f]{2}\1){4}[0-9a-f]{2}\b""")

    /**
     * Esconde números de telefone (7+ dígitos seguidos) e endereços Bluetooth do que o debug do
     * telefone mostra (issue #15): o registro pode ser copiado e enviado.
     */
    fun redactPersonal(s: String): String =
        MAC.replace(LONG_DIGITS.replace(s) { m -> "#".repeat(minOf(m.value.length, 4)) + "…" }, "XX:XX:XX:XX:XX:XX")
}

/**
 * **Kotlin puro.** Guarda a coleta anterior e devolve só as novidades. A primeira coleta vira a
 * linha de base (só a contagem), para não despejar mil propriedades na tela.
 */
class ProbeTracker(private val maxSeen: Int = 4000) {

    data class Event(val source: String, val text: String)

    private val previous = HashMap<String, Map<String, String>>()
    private val seenBroadcasts = LinkedHashSet<String>()
    private val seenLogcat = LinkedHashSet<String>()
    private var baseline = true

    fun update(sections: Map<String, String>): List<Event> {
        val events = mutableListOf<Event>()
        val tables = mapOf(
            "settings system" to sections["SYSTEM"]?.let(ProbeParse::settings),
            "settings global" to sections["GLOBAL"]?.let(ProbeParse::settings),
            "settings secure" to sections["SECURE"]?.let(ProbeParse::settings),
            "prop" to sections["PROPS"]?.let(ProbeParse::getprop),
        )
        for ((name, now) in tables) {
            now ?: continue
            val before = previous[name]
            previous[name] = now
            if (before == null) continue
            ProbeParse.diff(before, now).forEach { c ->
                events += Event(name, star(c.key) + c.describe())
            }
        }
        val broadcasts = sections["BCAST"]?.let(ProbeParse::broadcastHistory).orEmpty()
        // O histórico vem do mais novo para o mais velho; mostra na ordem em que aconteceram.
        for (b in broadcasts.asReversed()) {
            if (!remember(seenBroadcasts, b.key)) continue
            if (!baseline) events += Event("broadcast", star(b.line) + "${b.enqueued} ${b.line}")
        }
        for (line in sections["LOGCAT"]?.lines().orEmpty().map { it.trim() }.filter { it.isNotEmpty() }) {
            if (!remember(seenLogcat, line)) continue
            if (!baseline) events += Event("logcat", line)
        }
        if (baseline) {
            baseline = false
            val counts = tables.entries.joinToString(", ") { "${it.key}: ${it.value?.size ?: "—"}" }
            events += Event("base", "linha de base gravada ($counts, broadcasts: ${broadcasts.size}). Troque a opção do menu do painel agora.")
        }
        return events
    }

    private fun star(s: String) = if (ProbeParse.isCandidate(s)) "★ " else ""

    /** true se é novo. */
    private fun remember(set: LinkedHashSet<String>, key: String): Boolean {
        if (!set.add(key)) return false
        while (set.size > maxSeen) set.remove(set.first())
        return true
    }
}
