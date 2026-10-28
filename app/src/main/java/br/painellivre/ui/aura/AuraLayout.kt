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
