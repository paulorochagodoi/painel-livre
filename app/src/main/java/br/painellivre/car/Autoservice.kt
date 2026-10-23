package br.painellivre.car

/**
 * **Kotlin puro.** Leitura (somente leitura!) do Binder `autoservice` da BYD pelo shell do ADB local
 * (uid 2000), o mesmo caminho do `sendInfo` do painel (issue #16, docs/TELEMETRIA.md).
 *
 * Por que `service call` e não o SDK: as permissões `BYDAUTO_*_GET` não podem ser dadas por `pm grant`
 * na DiLink 3 e o uid do app recebe recusa. O shell lê o Binder direto.
 *
 * Transações (interface `android.gui.BYDAutoServer`): 5 = `getInt(dev, fid)`, 7 = `getFloat(dev, fid)`
 * (o valor é a representação IEEE-754 no inteiro da resposta). A 6 é `setInt` (escrita) e **não existe
 * aqui**: [command] só aceita [READ_TRANSACTIONS] e o teste confere. Outras transações com aridade
 * diferente já derrubaram o serviço em outro carro (docs/TELEMETRIA.md, "Riscos").
 *
 * O número da transação depende da interface: antes de ler, [Environment.descriptorOk] confere no
 * `service list` que o `autoservice` é mesmo o `android.gui.BYDAutoServer`. Se não for, nada é lido.
 */
object Autoservice {

    const val SERVICE = "autoservice"
    const val DESCRIPTOR = "android.gui.BYDAutoServer"
    const val TX_GET_INT = 5
    const val TX_GET_FLOAT = 7

    /** Únicas transações que este app manda. Nunca acrescentar a 6 (setInt). */
    val READ_TRANSACTIONS = setOf(TX_GET_INT, TX_GET_FLOAT)

    /** Tipos de dispositivo da BYD (1000 = ar-condicionado … 1043 = sensores). */
    val DEVICE_RANGE = 1000..1099

    /** Respostas que significam "sem dado" (docs/TELEMETRIA.md, "Valores que não são dados"). */
    const val NO_DATA = -10011          // 0xffffd8e5: sem dado nesta geração / carro parado
    const val WRONG_TRANSACTION = -10013 // 0xffffd8e3: transação ou direção errada
    const val LINK_ERROR = 0xFFFF       // ligação fid↔CAN não estabelecida
    const val NOT_INITIALIZED = 0xFFFFF // 20 bits "não inicializado"
    val INT_SENTINELS = setOf(NO_DATA, WRONG_TRANSACTION, LINK_ERROR, NOT_INITIALIZED)
    const val FLOAT_INVALID_BITS = 0xBF800000.toInt() // -1.0f

    private val READ_LINE = Regex("""service call autoservice [57] i32 10\d{2} i32 -?\d{1,10}""")

    /** Comando de uma leitura. O fid vai em decimal com sinal (bit alto → negativo), como o shell pede. */
    fun command(tx: Int, dev: Int, fid: Int): String {
        require(tx in READ_TRANSACTIONS) { "transação $tx bloqueada: só leitura (5/7)" }
        require(dev in DEVICE_RANGE) { "dispositivo $dev fora da faixa BYD" }
        return "service call $SERVICE $tx i32 $dev i32 $fid"
    }

    /** Defesa extra antes de mandar ao shell: a linha tem exatamente a forma de uma leitura. */
    fun isReadOnly(cmd: String): Boolean = READ_LINE.matches(cmd)

    // ── Lote: um comando de shell por varredura ─────────────────────────────

    private const val MARK = "@@PL"
    private const val END = "@@PLFIM"

    /**
     * Junta as leituras num só comando de shell (uma ida ao ADB por varredura). Cada leitura vem
     * depois de um `echo @@PL<i>`: se uma delas não imprimir nada, as seguintes não escorregam para o
     * dado errado.
     */
    fun batch(commands: List<String>): String {
        commands.forEach { require(isReadOnly(it)) { "comando fora da lista de leitura: $it" } }
        return commands.mapIndexed { i, c -> "echo $MARK$i; $c" }.plus("echo $END").joinToString("; ")
    }

    /** Separa a saída do [batch]: um texto por leitura, ou null se faltou. */
    fun splitBatch(output: String, count: Int): List<String?> {
        val parts = arrayOfNulls<StringBuilder>(count)
        var current = -1
        for (line in output.lineSequence()) {
            val t = line.trim()
            if (t == END) { current = -1; continue }
            if (t.startsWith(MARK)) {
                current = t.removePrefix(MARK).toIntOrNull()?.takeIf { it in 0 until count } ?: -1
                if (current >= 0) parts[current] = StringBuilder()
                continue
            }
            if (current >= 0) parts[current]!!.append(line).append('\n')
        }
        return parts.map { it?.toString()?.trim() }
    }

    // ── Resposta do Parcel ──────────────────────────────────────────────────

    sealed class Reply {
        /** Status 0 e a palavra de 32 bits da resposta. */
        data class Value(val word: Int) : Reply()
        /** O Binder respondeu com exceção (status ≠ 0). */
        data class Failure(val status: Int) : Reply()
        /** O serviço não existe (central não é BYD, emulador). */
        object NoService : Reply()
        /** Saída que não é um Parcel (erro do shell, vazio). */
        data class Garbled(val text: String) : Reply()
    }

    private val WORD = Regex("""\b[0-9a-fA-F]{8}\b""")
    private val OFFSET = Regex("""0x[0-9a-fA-F]{8}:""")

    /**
     * Lê `Result: Parcel(00000000 00000001   '........')` (Android 10). Também aceita espaços depois
     * do parêntese e o formato longo com deslocamentos (`0x00000000: 00000000 00000001 …`).
     * A primeira palavra é o status (0 = ok); a segunda é o valor.
     */
    fun parse(text: String?): Reply {
        if (text.isNullOrBlank()) return Reply.Garbled("")
        if (text.contains("does not exist", ignoreCase = true) || text.contains("Can't find service", ignoreCase = true)) {
            return Reply.NoService
        }
        val start = text.indexOf("Parcel(")
        if (start < 0) return Reply.Garbled(text.take(120))
        // Descarta a coluna ASCII de cada linha, sem perder palavras das linhas seguintes.
        val body = text.substring(start + 7).substringBefore(')').lineSequence()
            .joinToString(" ") { it.substringBefore('\'').replace(OFFSET, " ") }
        val words = WORD.findAll(body).map { it.value.toLong(16).toInt() }.toList()
        if (words.isEmpty()) return Reply.Garbled(text.take(120))
        if (words[0] != 0) return Reply.Failure(words[0])
        return words.getOrNull(1)?.let { Reply.Value(it) } ?: Reply.Garbled(text.take(120))
    }

    /** Inteiro útil ou null se for um dos sentinelas. */
    fun intValue(word: Int): Int? = word.takeUnless { it in INT_SENTINELS }

    /** Float útil (transação 7) ou null se for inválido. */
    fun floatValue(word: Int): Float? {
        if (word == FLOAT_INVALID_BITS || word in INT_SENTINELS) return null
        val f = java.lang.Float.intBitsToFloat(word)
        return f.takeIf { it.isFinite() }
    }

    fun hex(word: Int) = "0x%08x".format(word)

    // ── Ambiente: interface do serviço e protocolo do carro ─────────────────

    /** Comando único de reconhecimento (só leitura): interface do `autoservice` e protocolo CAN/CAN-FD. */
    const val ENVIRONMENT_COMMAND =
        "service list | grep -i 'autoservice:'; echo @@PLPROTO; getprop ro.car.protocol; getprop sys.car.protocol"

    enum class Protocol { CAN, CANFD, UNKNOWN }

    data class Environment(val serviceLine: String?, val protocol: Protocol) {
        /** O serviço existe e fala a interface cujas transações 5/7 são getInt/getFloat. */
        val descriptorOk get() = serviceLine?.contains("[$DESCRIPTOR]") == true
        val hasService get() = serviceLine != null

        companion object {
            fun parse(output: String): Environment {
                val (svc, props) = output.split("@@PLPROTO").let { it[0] to it.getOrElse(1) { "" } }
                val line = svc.lineSequence().map { it.trim() }.firstOrNull { Regex("""^\d+\s+autoservice:""").containsMatchIn(it) }
                val values = props.lineSequence().map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toList()
                val protocol = when (values.firstOrNull()) {
                    "CANFD", "CAN-FD", "CAN_FD" -> Protocol.CANFD
                    "CAN" -> Protocol.CAN
                    else -> Protocol.UNKNOWN
                }
                return Environment(line, protocol)
            }
        }
    }
}
