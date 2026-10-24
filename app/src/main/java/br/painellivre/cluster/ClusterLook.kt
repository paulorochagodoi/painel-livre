package br.painellivre.cluster

import br.painellivre.ui.aura.AuraColors

/**
 * **Kotlin puro.** Visual Aura da tela do painel de instrumentos: cores dos cartões a partir do
 * [AuraColors] (mesma preferência "theme" da central) e tamanhos em fração de [SafeArea.Plan.unit]
 * (a altura do painel: 720 px no Song Plus).
 *
 * O painel é lido de relance, a 70–80 cm: os números da manobra e da chegada usam Sora (como o
 * relógio da central) e são os maiores; nada de texto abaixo de [MIN_TEXT] da unidade. Os cartões
 * ficam quase opacos por cima do mapa para o contraste não depender do que está atrás.
 */
data class ClusterLook(
    val dark: Boolean,
    val card: Int,
    val cardStroke: Int,
    val ink: Int,
    val inkMuted: Int,
    val accent: Int,
    val onAccent: Int,
    val warn: Int,
    val statusBg: Int,
    val statusInk: Int,
    val attribution: Int,
) {
    companion object {
        /** Opacidade dos cartões sobre o mapa (0xF2 = 95%). */
        const val CARD_ALPHA = 0xF2

        fun of(c: AuraColors) = ClusterLook(
            dark = c.dark,
            card = (CARD_ALPHA shl 24) or (c.surface and 0xFFFFFF),
            cardStroke = c.line,
            ink = c.ink,
            inkMuted = c.inkMuted,
            accent = c.accent,
            onAccent = c.onAccent,
            warn = c.warn,
            statusBg = c.warnSoft,
            statusInk = c.warn,
            attribution = (0x99 shl 24) or (c.inkMuted and 0xFFFFFF),
        )

        // Tamanhos (fração da unidade). Raio do Aura (24 dp no desenho 720) = 0,033.
        const val RADIUS = 0.033f
        const val PAD = 0.025f
        const val STROKE = 0.003f
        const val ICON_BOX = 0.19f
        const val DISTANCE = 0.085f
        const val STREET = 0.046f
        const val INSTRUCTION = 0.036f
        const val ETA_VALUE = 0.058f
        const val ETA_LABEL = 0.028f
        const val ETA_ICON = 0.032f
        const val TELEMETRY = 0.04f
        const val STATUS = 0.032f
        const val ATTRIBUTION = 0.02f

        /** Menor texto de leitura (a atribuição do mapa não conta: é obrigação de licença). */
        const val MIN_TEXT = 0.026f

        /** Textos de leitura, para o teste de tamanho mínimo. */
        val READING_TEXT = listOf(DISTANCE, STREET, INSTRUCTION, ETA_VALUE, ETA_LABEL, TELEMETRY, STATUS)

        /** Cor sólida do cartão por cima de [behind] (mistura pela opacidade). */
        fun over(card: Int, behind: Int): Int {
            val a = (card ushr 24) / 255.0
            fun ch(shift: Int) = (((card shr shift) and 0xFF) * a + ((behind shr shift) and 0xFF) * (1 - a)).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
