package br.painellivre.ui.aura

import br.painellivre.ui.cockpit.Palette
import java.util.Calendar

/**
 * **Kotlin puro.** Cores do padrão visual Aura (docs/design/aura-launcher/docs/tokens.json), temas
 * Claro e Escuro. Mudou lá, mude aqui (o `AuraTokensTest` confere os valores principais).
 *
 * Sem `android.graphics.Color` na inicialização, para os testes rodarem na JVM.
 */
data class AuraColors(
    val dark: Boolean,
    val bg: Int,
    val horizon: Int,
    val platformGlow: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val surfaceSunken: Int,
    val surfaceDock: Int,
    val line: Int,
    val ink: Int,
    val inkMuted: Int,
    val accent: Int,
    val accentSoft: Int,
    val onAccent: Int,
    val warn: Int,
    val warnSoft: Int,
    val alert: Int,
    val ok: Int,
    val heat: Int,
    val heatSoft: Int,
    val cool: Int,
    val coolSoft: Int,
    val scrim: Int,
) {
    /** Nome do tema na barra de status ("Claro"/"Escuro"). */
    val label get() = if (dark) "Escuro" else "Claro"

    /**
     * As telas antigas (discador, previsão do tempo, itens de lista) pintam com [Palette] por tags.
     * Este mapeamento dá a elas as cores Aura sem reescrever os layouts. O painel de instrumentos
     * continua com o [Palette] próprio (não muda com esta interface).
     */
    fun toPalette() = Palette(
        dark = dark,
        text1 = ink,
        text2 = inkMuted,
        text3 = (line and 0xFFFFFF) or (0xFF shl 24),
        accent = accent,
        onAccent = onAccent,
        card = surface,
        cardStroke = line,
        chip = surfaceSunken,
        ok = ok,
        warn = warn,
        danger = alert,
    )

    companion object {
        /** "#rrggbb" ou "#rrggbbaa" (formato do tokens.json, alfa no fim) → ARGB. */
        fun hex(s: String): Int {
            val h = s.removePrefix("#")
            require(h.length == 6 || h.length == 8) { "cor inválida: $s" }
            val rgb = h.substring(0, 6).toLong(16).toInt()
            val a = if (h.length == 8) h.substring(6, 8).toInt(16) else 0xFF
            return (a shl 24) or rgb
        }

        val LIGHT = AuraColors(
            dark = false,
            bg = hex("#e8edf2"),
            horizon = hex("#d6dee7"),
            platformGlow = hex("#6fcad866"),
            surface = hex("#ffffff"),
            surfaceRaised = hex("#ffffff"),
            surfaceSunken = hex("#e1e7ee"),
            surfaceDock = hex("#f6f8faf2"),
            line = hex("#cdd6df"),
            ink = hex("#0e1822"),
            inkMuted = hex("#4a5867"),
            accent = hex("#0a6f82"),
            accentSoft = hex("#d4eef2"),
            onAccent = hex("#ffffff"),
            warn = hex("#8f4f00"),
            warnSoft = hex("#fbe5c6"),
            alert = hex("#b3261e"),
            ok = hex("#1d7a45"),
            heat = hex("#a63c0b"),
            heatSoft = hex("#fde2d2"),
            cool = hex("#1e64b0"),
            coolSoft = hex("#dbe8f8"),
            scrim = hex("#0e182240"),
        )

        val DARK = AuraColors(
            dark = true,
            bg = hex("#070c14"),
            horizon = hex("#0f1824"),
            platformGlow = hex("#2cc4d94d"),
            surface = hex("#121b28"),
            surfaceRaised = hex("#172231"),
            surfaceSunken = hex("#0b121c"),
            surfaceDock = hex("#0c141ff2"),
            line = hex("#24324a"),
            ink = hex("#edf3f8"),
            inkMuted = hex("#98a9bb"),
            accent = hex("#4fd4e6"),
            accentSoft = hex("#0e3440"),
            onAccent = hex("#031418"),
            warn = hex("#ffb54a"),
            warnSoft = hex("#3a2a0f"),
            alert = hex("#ff8577"),
            ok = hex("#63d796"),
            heat = hex("#ff9259"),
            heatSoft = hex("#3d1c0c"),
            cool = hex("#74b6ff"),
            coolSoft = hex("#0f2742"),
            scrim = hex("#00000080"),
        )

        /** Mesma preferência "theme" de sempre: auto (claro de dia), day = Claro, night = Escuro. */
        fun forSetting(setting: String, now: Calendar = Calendar.getInstance()): AuraColors = when (setting) {
            "day" -> LIGHT
            "night" -> DARK
            else -> if (Palette.isDaytime(now)) LIGHT else DARK
        }

        /** Botão de tema da barra de status: alterna entre Claro e Escuro (sai do automático). */
        fun toggled(current: AuraColors): String = if (current.dark) "day" else "night"

        /** Contraste WCAG entre duas cores opacas (para o teste de legibilidade). */
        fun contrast(a: Int, b: Int): Double {
            fun lum(c: Int): Double {
                fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
                return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
            }
            val l1 = lum(a); val l2 = lum(b)
            return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
        }
    }
}

/** Medidas do Aura em dp do desenho base 1280 × 720 (ver [AuraScale]). */
object AuraDims {
    const val SPACE_1 = 4
    const val SPACE_2 = 8
    const val SPACE_3 = 12
    const val SPACE_4 = 16
    const val SPACE_5 = 24
    const val SPACE_6 = 32
    const val RADIUS_SM = 12
    const val RADIUS_MD = 18
    const val RADIUS_LG = 24
    const val RADIUS_XL = 30
    const val TOUCH_MIN = 56
    const val TOUCH_PRIMARY = 80
    const val DOCK_HEIGHT = 80
    const val STATUS_HEIGHT = 40
    /** Janela flutuante: altura máxima, para nunca cobrir a barra de status. */
    const val POPOVER_MAX_HEIGHT = 420
    /** Entrada das janelas: 180 ms, fade + 10 dp para cima. */
    const val POPOVER_IN_MS = 180L
    const val POPOVER_RISE_DP = 10
}
