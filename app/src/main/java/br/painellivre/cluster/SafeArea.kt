package br.painellivre.cluster

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Área segura do mapa no painel: o retângulo que os desenhos da BYD não cobrem.
 *
 * Na foto do painel do Song Plus (1920×720), os instrumentos da BYD ficam por cima da projeção:
 *  - topo: faixa com hora, HEV, P, NORMAL, ASE, temperatura e luzes (y 0–75 no centro), e a moldura
 *    curva recorta os cantos de cima (o cartão perde 40–60 px à esquerda);
 *  - laterais: avisos à esquerda (x < 200, y 380–460) e à direita (x > 1720, y 60–300);
 *  - base: kW, bateria, autonomia, OK, velocidade e odômetro (y > 500 em toda a largura), mais o
 *    assistente de faixa (x 1290–1450, y 420–520).
 * Daí o padrão [SONG_PLUS] (esquerda 230, topo 90, direita 230, base 230). Os valores são em pixels
 * do painel de referência ([REF_W]×[REF_H]) e escalam com o tamanho real do display.
 *
 * Kotlin puro (testado na JVM, `SafeAreaTest`): nada de API Android aqui.
 */
object SafeArea {

    const val REF_W = 1920
    const val REF_H = 720

    /** Margens em pixels (esquerda, topo, direita, base). */
    data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        operator fun plus(o: Insets) = Insets(left + o.left, top + o.top, right + o.right, bottom + o.bottom)
        override fun toString() = "$left/$top/$right/$bottom"
    }

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2
        fun intersects(o: Rect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
        fun contains(o: Rect) = o.left >= left && o.top >= top && o.right <= right && o.bottom <= bottom
    }

    val NONE = Insets(0, 0, 0, 0)
    val SONG_PLUS = Insets(230, 90, 230, 230)

    /** Desenhos da BYD medidos na foto do Song Plus (coordenadas do painel de referência). */
    val SONG_PLUS_OVERLAYS = listOf(
        Rect(0, 0, REF_W, 75),          // faixa de cima
        // Cantos curvos de cima: a foto só mostra que o recorte come 40–60 px do cartão; o tamanho
        // daqui é estimativa até medir no carro.
        Rect(0, 0, 200, 140),
        Rect(REF_W - 200, 0, REF_W, 140),
        Rect(0, 380, 200, 460),         // avisos à esquerda
        Rect(1720, 60, REF_W, 300),     // avisos à direita
        Rect(1290, 420, 1450, 520),     // assistente de faixa
        Rect(0, 500, REF_W, REF_H),     // faixa de baixo
    )

    /** Limite do ajuste fino (por lado), em pixels de referência. */
    const val ADJUST_LIMIT = 400

    /** A área segura nunca fica menor que esta fração do display (ajuste exagerado não some com o mapa). */
    const val MIN_WIDTH_FRACTION = 0.30f
    const val MIN_HEIGHT_FRACTION = 0.30f

    /** Carro a 67,5% da altura da área segura: y 360 no Song Plus, acima da faixa de baixo. */
    const val CAR_Y_FRACTION = 0.675f

    /** "songplus" (padrão) ou "none" (tela inteira, para painéis sem desenho por cima). */
    fun preset(name: String?): Insets = if (name == "none") NONE else SONG_PLUS

    /**
     * Margens finais no display [w]×[h]: padrão + ajuste fino (cada lado limitado a ±[ADJUST_LIMIT]),
     * escaladas do painel de referência e presas para sobrar pelo menos [MIN_WIDTH_FRACTION] ×
     * [MIN_HEIGHT_FRACTION] do display.
     */
    fun resolve(w: Int, h: Int, base: Insets, adjust: Insets): Insets {
        if (w <= 0 || h <= 0) return NONE
        fun side(b: Int, a: Int) = max(0, b + a.coerceIn(-ADJUST_LIMIT, ADJUST_LIMIT))
        val sx = w.toFloat() / REF_W
        val sy = h.toFloat() / REF_H
        var l = (side(base.left, adjust.left) * sx).roundToInt()
        var r = (side(base.right, adjust.right) * sx).roundToInt()
        var t = (side(base.top, adjust.top) * sy).roundToInt()
        var b = (side(base.bottom, adjust.bottom) * sy).roundToInt()
        val maxX = w - (w * MIN_WIDTH_FRACTION).roundToInt()
        if (l + r > maxX) { val k = maxX.toFloat() / (l + r); l = (l * k).toInt(); r = (r * k).toInt() }
        val maxY = h - (h * MIN_HEIGHT_FRACTION).roundToInt()
        if (t + b > maxY) { val k = maxY.toFloat() / (t + b); t = (t * k).toInt(); b = (b * k).toInt() }
        return Insets(l, t, r, b)
    }

    /**
     * Tudo que a tela do painel posiciona, em coordenadas do conteúdo ([content] dentro do display):
     * o conteúdo pode estar encolhido pelo menu do painel ("Pequeno", issue #9), e a área segura é do
     * display, então ela é recortada pelo conteúdo.
     */
    data class Plan(
        /** Área segura dentro do conteúdo. */
        val safe: Rect,
        /** Centro do carro (= ponto focal da câmera). */
        val carX: Int,
        val carY: Int,
        /** Padding da câmera que põe o ponto focal em ([carX], [carY]). */
        val cameraPadding: Insets,
        /** Unidade de tamanho dos cartões (a altura do display, menor se a área segura for baixa). */
        val unit: Float,
        /** Largura do cartão da manobra: não passa do carro. */
        val cardWidth: Int,
    )

    /** Fração de [Plan.unit] ocupada pelos cartões (manobra + chegada) e a atribuição, com folga. */
    const val CARDS_HEIGHT_UNITS = 0.45f

    fun plan(displayW: Int, displayH: Int, insets: Insets, content: Rect): Plan {
        val cw = max(1, content.width)
        val ch = max(1, content.height)
        // Área segura do display passada para o conteúdo e recortada por ele.
        var sl = (insets.left - content.left).coerceIn(0, cw)
        var st = (insets.top - content.top).coerceIn(0, ch)
        var sr = (displayW - insets.right - content.left).coerceIn(0, cw)
        var sb = (displayH - insets.bottom - content.top).coerceIn(0, ch)
        if (sr - sl < 1 || sb - st < 1) { sl = 0; st = 0; sr = cw; sb = ch } // conteúdo fora da área segura
        val safe = Rect(sl, st, sr, sb)
        val carX = safe.centerX
        val carY = safe.top + (safe.height * CAR_Y_FRACTION).roundToInt()
        val unit = min(ch.toFloat(), safe.height / CARDS_HEIGHT_UNITS)
        val carHalf = (unit * CAR_SIZE / 2).roundToInt()
        val cardWidth = min((cw * 0.30f).roundToInt(), carX - carHalf - (unit * 0.03f).roundToInt() - safe.left)
            .coerceAtLeast(min(safe.width, (unit * 0.5f).roundToInt()))
        return Plan(safe, carX, carY, focusPadding(cw, ch, carX, carY), unit, cardWidth)
    }

    /** Tamanho do carro em [Plan.unit]. */
    const val CAR_SIZE = 0.095f

    /**
     * Padding mínimo que leva o centro da câmera do MapLibre (meio da área sem padding) ao ponto
     * ([x], [y]) de uma vista [w]×[h].
     */
    fun focusPadding(w: Int, h: Int, x: Int, y: Int): Insets {
        val cx = x.coerceIn(0, w)
        val cy = y.coerceIn(0, h)
        val l = max(0, 2 * cx - w)
        val r = max(0, w - 2 * cx)
        val t = max(0, 2 * cy - h)
        val b = max(0, h - 2 * cy)
        return Insets(l, t, r, b)
    }
}
