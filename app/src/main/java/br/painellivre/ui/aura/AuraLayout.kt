package br.painellivre.ui.aura

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * **Kotlin puro.** Qual janela flutuante está aberta (design-system.md, "Janela perto do dedo"):
 * só uma por vez; o mesmo atalho fecha; tocar fora, no X ou em Início fecha.
 */
class PopoverState {
    enum class Id { NAV, BATTERY, CLIMATE, MEDIA, PANEL }

    var open: Id? = null
        private set

    /** Toque num atalho: abre a janela dele (fechando a outra) ou fecha se já era a dele. */
    fun toggle(id: Id): Id? { open = if (open == id) null else id; return open }

    /** Abre sem alternar (ex.: temperatura do dock abre a Climatização). */
    fun show(id: Id): Id { open = id; return id }

    /** Toque fora, no X, em Início ou ao trocar de página. Devolve true se havia janela aberta. */
    fun close(): Boolean { val was = open != null; open = null; return was }
}

/**
 * **Kotlin puro.** Tela da central (Início, Mapa ou Apps) e o balão aberto por cima dela.
 *
 * Regra (0.5.9): abrir ou fechar um controle — climatização, mídia/rádio, bancos (dentro da
 * climatização), bateria, navegação ou painel — **nunca troca de tela**. O balão vive na camada de
 * janelas da Activity (`popLayer`), acima de qualquer página, e se ancora no botão que foi tocado.
 * Antes, o balão só sabia se ancorar nos atalhos grandes do Início; por isso, tocar no ar ou na mídia
 * estando no mapa levava o usuário de volta ao Início (e perdia a busca). Só ações que pedem outra
 * tela de propósito (ex.: "Buscar destino" no balão da navegação) chamam [goTo].
 */
class CentralScreen(page: Int = HOME) {

    /** De onde o balão foi chamado. Os atalhos grandes ([tile] = true) só existem no Início. */
    enum class Anchor(val tile: Boolean) {
        TILE_NAV(true), TILE_BATTERY(true), TILE_CLIMATE(true), TILE_MEDIA(true),
        DOCK_HOME(false), DOCK_PANEL(false), DOCK_NAV_APP(false), DOCK_CLIMATE(false), DOCK_MEDIA(false),
        DOCK_TEMP_DRIVER(false), DOCK_TEMP_PASSENGER(false),
    }

    var page: Int = page
        private set
    val popovers = PopoverState()
    val open: PopoverState.Id? get() = popovers.open
    var anchor: Anchor? = null
        private set

    /** Troca de tela de propósito (Início, voltar, Buscar destino…). Não mexe no balão. */
    fun goTo(p: Int) { page = p }

    /** Toque num controle: abre o balão dele por cima da tela atual, ou fecha se já era o dele. */
    fun tapControl(id: PopoverState.Id, from: Anchor): PopoverState.Id? {
        val now = popovers.toggle(id)
        anchor = if (now != null) from else null
        return now
    }

    /** Abre sem alternar (ex.: restaurar depois de girar a tela). */
    fun showControl(id: PopoverState.Id, from: Anchor) { popovers.show(id); anchor = from }

    /** Toque fora, no X, em Início ou voltar. A tela continua a mesma. */
    fun close(): Boolean { anchor = null; return popovers.close() }

    /** Botão onde a seta do balão aponta, entre os que existem na tela atual. */
    fun visibleAnchor(): Anchor? = anchor?.let { if (it.tile && page != HOME) dockFor(it) else it }

    companion object {
        const val HOME = 0
        const val NAV = 1
        const val APPS = 2

        /** Botão do dock (sempre visível) equivalente a cada atalho do Início. */
        fun dockFor(a: Anchor): Anchor = when (a) {
            Anchor.TILE_NAV -> Anchor.DOCK_NAV_APP
            Anchor.TILE_BATTERY -> Anchor.DOCK_HOME
            Anchor.TILE_CLIMATE -> Anchor.DOCK_CLIMATE
            Anchor.TILE_MEDIA -> Anchor.DOCK_MEDIA
            else -> a
        }

        /**
         * Se o botão não existe na tela (o dock em pé não tem as temperaturas), a seta vai para o botão
         * do dock do mesmo controle; sem equivalente, para o Início do dock.
         */
        fun fallbackFor(a: Anchor): Anchor = when (a) {
            Anchor.DOCK_TEMP_DRIVER, Anchor.DOCK_TEMP_PASSENGER, Anchor.TILE_CLIMATE -> Anchor.DOCK_CLIMATE
            Anchor.TILE_MEDIA -> Anchor.DOCK_MEDIA
            Anchor.TILE_NAV -> Anchor.DOCK_NAV_APP
            else -> Anchor.DOCK_HOME
        }
    }
}

/** **Kotlin puro.** Posição da janela ancorada no atalho, com a seta apontando para ele. */
object PopoverAnchor {
    data class Place(val left: Int, val caretX: Int)

    /**
     * @param anchorCenterX centro do atalho na tela; @param width largura da janela;
     * @param screenWidth largura disponível; @param margin distância mínima da borda (space-5).
     * A seta fica sobre o atalho, mas nunca a menos de [caretInset] do canto arredondado.
     */
    fun place(anchorCenterX: Int, width: Int, screenWidth: Int, margin: Int, caretInset: Int): Place {
        val w = min(width, screenWidth - 2 * margin)
        val left = (anchorCenterX - w / 2).coerceIn(margin, max(margin, screenWidth - margin - w))
        val caret = (anchorCenterX - left).coerceIn(caretInset, max(caretInset, w - caretInset))
        return Place(left, caret)
    }
}

/**
 * **Kotlin puro.** O Aura é desenhado em 1280 × 720 e "escala para qualquer tela sem reorganizar as
 * zonas" (Launcher.md). No Android, em vez de `scale()` (que estragaria o mapa e o teclado), a tela
 * inicial usa uma densidade própria: 1 dp do layout = 1 px do desenho. Em pé, a referência vira
 * 720 × 1280.
 */
object AuraScale {
    const val BASE_LONG = 1280
    const val BASE_SHORT = 720

    /** densityDpi para que a tela caiba no desenho base (160 dpi = 1 px por dp). */
    fun densityDpi(widthPx: Int, heightPx: Int): Int {
        if (widthPx <= 0 || heightPx <= 0) return 160
        val long = max(widthPx, heightPx).toDouble()
        val short = min(widthPx, heightPx).toDouble()
        val scale = min(long / BASE_LONG, short / BASE_SHORT)
        return (160 * scale).roundToInt().coerceIn(80, 640)
    }
}
