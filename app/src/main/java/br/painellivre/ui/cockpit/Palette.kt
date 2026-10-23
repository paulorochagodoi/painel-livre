package br.painellivre.ui.cockpit

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Calendar

/**
 * Cores da tela central e do painel, em dois temas (dia claro, noite escuro), como o cockpit do
 * SealNav. As Views marcam o papel com `android:tag` no XML e [apply] pinta a árvore inteira:
 *  - "card" / "card_click" / "chip" / "chip_on" / "capsule" / "dockbar": fundos;
 *  - "t1" (texto principal), "t2" (secundário), "t3" (rótulo fraco), "accent" (destaque);
 *  - "icon" / "icon2" / "icon_accent" / "icon_on" / "icon_ok": ícones (tint); "progress": barra;
 *  - "round" / "round_on": botão redondo (fundo neutro / azul).
 */
data class Palette(
    val dark: Boolean,
    val text1: Int,
    val text2: Int,
    val text3: Int,
    val accent: Int,
    val onAccent: Int,
    val card: Int,
    val cardStroke: Int,
    val chip: Int,
    val ok: Int,
    val warn: Int,
    val danger: Int,
) {
    fun cardBg(radius: Float) = GradientDrawable().apply {
        setColor(card); cornerRadius = radius; setStroke(1, cardStroke)
    }

    fun chipBg(radius: Float, on: Boolean = false) = GradientDrawable().apply {
        setColor(if (on) accent else chip); cornerRadius = radius
    }

    private fun ripple(content: GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000), content, null)

    /** Pinta [root] e todos os filhos conforme a tag de cada um. [density] converte dp. */
    fun apply(root: View, density: Float) {
        val r = 26f * density
        when (root.tag) {
            "card" -> root.background = cardBg(r)
            "card_click" -> root.background = ripple(cardBg(r))
            "chip" -> root.background = ripple(chipBg(18f * density))
            "chip_on" -> root.background = ripple(chipBg(18f * density, on = true))
            "capsule" -> root.background = chipBg(40f * density)
            "dockbar" -> root.background = GradientDrawable().apply { setColor(card); setStroke(1, cardStroke) }
            "icon_ok" -> (root as? ImageView)?.imageTintList = ColorStateList.valueOf(ok)
            "round" -> (root as? ImageView)?.apply {
                background = ripple(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chip) })
                imageTintList = ColorStateList.valueOf(text1)
            }
            "round_on" -> (root as? ImageView)?.apply {
                background = ripple(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) })
                imageTintList = ColorStateList.valueOf(onAccent)
            }
            "t1" -> (root as? TextView)?.apply { setTextColor(text1); setHintTextColor(text2) }
            "t2" -> (root as? TextView)?.setTextColor(text2)
            "t3" -> (root as? TextView)?.setTextColor(text3)
            "accent" -> (root as? TextView)?.setTextColor(accent)
            "on_accent" -> (root as? TextView)?.setTextColor(onAccent)
            "icon" -> (root as? ImageView)?.imageTintList = ColorStateList.valueOf(text1)
            "icon2" -> (root as? ImageView)?.imageTintList = ColorStateList.valueOf(text2)
            "icon_accent" -> (root as? ImageView)?.imageTintList = ColorStateList.valueOf(accent)
            "icon_on" -> (root as? ImageView)?.imageTintList = ColorStateList.valueOf(onAccent)
            "progress" -> (root as? ProgressBar)?.apply {
                progressTintList = ColorStateList.valueOf(ok)
                progressBackgroundTintList = ColorStateList.valueOf(text3)
            }
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(root.getChildAt(i), density)
    }

    companion object {
        // Sem android.graphics.Color aqui, para os testes rodarem na JVM.
        private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
        private fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)

        val DAY = Palette(
            dark = false,
            text1 = rgb(24, 28, 34),
            text2 = rgb(84, 92, 104),
            text3 = argb(70, 24, 28, 34),
            accent = rgb(22, 119, 255),
            onAccent = 0xFFFFFFFF.toInt(),
            card = argb(200, 255, 255, 255),
            cardStroke = argb(40, 255, 255, 255),
            chip = argb(150, 236, 239, 243),
            ok = rgb(46, 182, 104),
            warn = rgb(230, 160, 20),
            danger = rgb(222, 64, 64),
        )
        val NIGHT = Palette(
            dark = true,
            text1 = rgb(240, 244, 248),
            text2 = rgb(160, 170, 184),
            text3 = argb(70, 240, 244, 248),
            accent = rgb(64, 150, 255),
            onAccent = 0xFFFFFFFF.toInt(),
            card = argb(190, 26, 32, 42),
            cardStroke = argb(28, 255, 255, 255),
            chip = argb(160, 44, 52, 64),
            ok = rgb(61, 220, 132),
            warn = rgb(255, 200, 80),
            danger = rgb(255, 90, 95),
        )

        /** "auto" = dia das 6h às 18h30; "day"/"night" fixos (Configurações → Tema). */
        fun forSetting(setting: String, now: Calendar = Calendar.getInstance()): Palette = when (setting) {
            "day" -> DAY
            "night" -> NIGHT
            else -> if (isDaytime(now)) DAY else NIGHT
        }

        fun isDaytime(now: Calendar): Boolean {
            val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            return m in (6 * 60) until (18 * 60 + 30)
        }
    }
}
