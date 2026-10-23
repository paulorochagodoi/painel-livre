package br.painellivre.cluster

/**
 * **Kotlin puro.** Menu de navegação do próprio painel da BYD (Simples / Pequeno / Cheio / OFF,
 * issue #9) e o que o mapa do Painel Livre faz em cada opção.
 *
 * De onde vem o modo (docs/ENGENHARIA_REVERSA.md §6): o DiPlay mediu num Tang com DiLink 5.0 o
 * registrador `INSTRUMENT_NAVI_TYPE` do painel (device 1007, feature 0x40C03032) com 1 = Off,
 * 2 = "Turn on by navi", 3 = Small, 4 = Full. O SDK da BYD que vem no BYD Navigator tem o mesmo nome,
 * mas com valor de molde (0). **Nada disso foi confirmado no Song Plus com DiLink 3**: por isso
 * seguir o menu é opcional (desligado por padrão) e qualquer leitura estranha cai no comportamento
 * de sempre (mapa em tela cheia).
 *
 * Só leitura: o app nunca escreve `INSTRUMENT_NAVI_TYPE_SET` nem manda códigos novos ao AutoContainer
 * (17 = meia tela e 39 = navegação simples ficam fora de `SAFE_COMMANDS`).
 */
object PanelMenu {

    enum class Mode(val label: String) {
        OFF("OFF"),
        /** Valor 2 do DiPlay ("Turn on by navi"); hipótese: é o "Simples" do menu em português. */
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

    fun modeOf(raw: Int?): Mode = when (raw) {
        1 -> Mode.OFF
        2 -> Mode.SIMPLE
        3 -> Mode.SMALL
        4 -> Mode.FULL
        else -> Mode.UNKNOWN
    }

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
