package br.painellivre.cluster

/**
 * **Kotlin puro.** Menu de navegação do próprio painel da BYD (Simples / Pequeno / Cheio / OFF,
 * issue #9) e o que o mapa do Painel Livre faz em cada opção.
 *
 * De onde vem o modo (docs/ENGENHARIA_REVERSA.md §6): o registrador `INSTRUMENT_NAVI_TYPE` do painel
 * (device 1007, feature 0x40C03032), medido primeiro pelo DiPlay num Tang com DiLink 5.0.
 * **Confirmado no carro (Song Plus, DiLink 3.0, Android 10, 2026-10-23)**: trocando o menu do painel,
 * `service call autoservice 5 i32 1007 i32 1086337074` respondeu 1 = OFF, 2 = Simples, 3 = Pequeno,
 * 4 = Cheio; erro/indisponível veio como 0xffffd8e5. Seguir o menu continua opcional (desligado por
 * padrão) e qualquer leitura estranha cai no comportamento de sempre (mapa em tela cheia).
 *
 * Só leitura: o app nunca escreve `INSTRUMENT_NAVI_TYPE_SET` (nem a transação 6, o set do
 * `autoservice`) e não manda códigos novos ao AutoContainer (17 = meia tela e 39 = navegação simples
 * ficam fora de `SAFE_COMMANDS`).
 */
object PanelMenu {

    enum class Mode(val label: String) {
        OFF("OFF"),
        /** Valor 2 ("Turn on by navi" no DiPlay); confirmado no carro como o "Simples" do menu. */
        SIMPLE("Simples"),
        SMALL("Pequeno"),
        FULL("Cheio"),
        /** Sem leitura, erro ou valor desconhecido: faz o de sempre. */
        UNKNOWN("desconhecido"),
    }

    const val INSTRUMENT_DEVICE = 1007
    const val NAVI_TYPE_FEATURE = 0x40C03032
    const val NAVI_TYPE_FIELD = "INSTRUMENT_NAVI_TYPE"

    /** Constante do SDK do próprio carro, se for de verdade; o molde do Navigator traz 0. */
    fun featureId(fromSdk: Int?): Int = fromSdk?.takeIf { it != 0 } ?: NAVI_TYPE_FEATURE

    /** Mapeamento confirmado no carro (DiLink 3.0, 2026-10-23). */
    fun modeOf(raw: Int?): Mode = when (raw) {
        1 -> Mode.OFF
        2 -> Mode.SIMPLE
        3 -> Mode.SMALL
        4 -> Mode.FULL
        else -> Mode.UNKNOWN
    }

    // ── Leitura pelo ADB local (uid 2000) ───────────────────────────────────

    /**
     * Transação `getInt(device, feature)` do `autoservice`, conferida no carro (DiLink 3.0, Android 10).
     * O número depende da firmware (na transação 6 fica o set): por isso o comando é fixo, só roda no
     * Android em que foi conferido ([ADB_SDK_INT]) e só depois de o SDK recusar a permissão.
     */
    const val GET_TRANSACTION = 5
    /** Android 10 (API 29), a DiLink 3.0 em que a transação 5 foi conferida. */
    const val ADB_SDK_INT = 29
    /** Resposta do painel quando o valor está indisponível (0xffffd8e5 = -10011). */
    const val UNAVAILABLE = 0xffffd8e5.toInt()

    /** Único comando de leitura do menu pelo ADB. Constante: nada vindo de fora entra nele. */
    const val ADB_READ_COMMAND = "service call autoservice $GET_TRANSACTION i32 $INSTRUMENT_DEVICE i32 $NAVI_TYPE_FEATURE"

    /** Resultado de ler o `Parcel`: valor bruto ou motivo da falha. */
    data class ParcelValue(val raw: Int?, val error: String?)

    /**
     * Lê a resposta do `service call`, por exemplo `Result: Parcel(00000000 00000002   '........')`
     * ou o formato em várias linhas com endereço (`0x00000000: 00000000 00000002 '........'`).
     * Primeira palavra = código de exceção do Binder (0 = sem erro); segunda = o valor.
     */
    fun parseParcel(output: String): ParcelValue {
        val start = output.indexOf("Parcel(")
        if (start < 0) return ParcelValue(null, "resposta sem Parcel: ${output.trim().take(80)}")
        val words = output.substring(start + "Parcel(".length).lineSequence()
            .map { it.substringBefore('\'') } // o texto entre aspas é a prévia em ASCII
            .flatMap { line -> line.replace(OFFSET, " ").split(' ', '\t', ')').asSequence() }
            .filter { WORD.matches(it) }
            .map { it.toLong(16).toInt() }
            .toList()
        if (words.isEmpty()) return ParcelValue(null, "Parcel vazio")
        if (words[0] != 0) return ParcelValue(null, "autoservice recusou (exceção ${hex(words[0])})")
        val raw = words.getOrNull(1) ?: return ParcelValue(null, "Parcel sem valor")
        if (raw == UNAVAILABLE) return ParcelValue(raw, "painel respondeu indisponível (${hex(raw)})")
        return ParcelValue(raw, null)
    }

    fun hex(v: Int): String = "0x%08x".format(v)

    private val OFFSET = Regex("0x[0-9a-fA-F]+:")
    private val WORD = Regex("[0-9a-fA-F]{8}")

    /**
     * Área usada no display do painel (frações de cada borda) e o que aparece.
     * [showMap] falso = fundo preto no lugar do mapa; [showCards] falso = sem cartões.
     */
    data class Layout(
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
        val showMap: Boolean = true,
        val showCards: Boolean = true,
        val showCar: Boolean = true,
    ) {
        val isNormal get() = this == NORMAL
    }

    val NORMAL = Layout()

    /**
     * Pequeno: o DiPlay viu o painel cortar a imagem sem o Android saber. Hipótese até o carro
     * confirmar: sobra o meio, entre os mostradores. Então o mapa e os cartões vão para o centro.
     */
    val SMALL_LAYOUT = Layout(left = 0.28f, top = 0.12f, right = 0.28f, bottom = 0.12f)

    fun layoutFor(mode: Mode): Layout = when (mode) {
        Mode.FULL, Mode.UNKNOWN -> NORMAL
        Mode.SMALL -> SMALL_LAYOUT
        // "Navegação simples": só a manobra e a chegada, sem mapa (como a seta simples do painel).
        Mode.SIMPLE -> Layout(showMap = false, showCar = false)
        // OFF: nada do Painel Livre aparece (tela preta), sem mandar comando nenhum ao AutoContainer.
        Mode.OFF -> Layout(showMap = false, showCards = false, showCar = false)
    }

    /** Teste do debug (override) > leitura estável (se seguir o menu) > de sempre. */
    fun effective(follow: Boolean, override: Mode?, stable: Mode): Mode =
        override ?: if (follow) stable else Mode.UNKNOWN

    /**
     * Getters do painel lidos no debug, por nome (nunca por número de transação). Candidatos do SDK
     * dos moldes do Navigator; nenhum tem significado confirmado para o menu.
     */
    val DEBUG_GETTERS = listOf(
        "getInstrumentView", "getViewStatus", "getFirstMenu", "getSecondMenu", "get2in1MenuState",
        "getCurrentDriveInterFace", "getInstrumentScreenType", "getNaviDestinationCommand",
    )

    /** Só getters sem efeito: "get" + maiúscula/dígito. Barra set*, send*, clear*, register*… */
    fun isReadOnlyGetter(name: String): Boolean =
        name.length > 3 && name.startsWith("get") && (name[3].isUpperCase() || name[3].isDigit())

    /**
     * Evita piscar: um modo novo só vale depois de 2 leituras iguais seguidas; leituras falhas
     * mantêm o último modo por até [maxMisses] vezes e depois voltam ao de sempre.
     */
    class Follower(private val maxMisses: Int = 3) {
        var stable: Mode = Mode.UNKNOWN
            private set
        private var pending: Mode? = null
        private var misses = 0

        fun update(reading: Mode): Mode {
            if (reading == Mode.UNKNOWN) {
                pending = null
                if (++misses > maxMisses) stable = Mode.UNKNOWN
                return stable
            }
            misses = 0
            if (reading == stable) {
                pending = null
            } else if (reading == pending) {
                stable = reading
                pending = null
            } else {
                pending = reading
            }
            return stable
        }
    }
}
