package br.painellivre.ui.aura

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Pinta a tela Aura pelas tags do XML (`android:tag="a_…"`), como o [br.painellivre.ui.cockpit.Palette]
 * faz no cockpit antigo: as cores não vão no XML para os dois temas trocarem sem recriar a tela.
 * Tabela das tags em docs/INTERFACE-AURA.md. Tags desconhecidas (ex.: "t1" das telas antigas) ficam
 * para o Palette.
 */
class AuraPainter(val c: AuraColors, private val density: Float) {

    private fun dp(v: Int) = v * density
    private val pill = 999f * density

    private fun shape(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 1, oval: Boolean = false) =
        GradientDrawable().apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius
            setColor(fill)
            if (stroke != 0) setStroke((strokeWidth * density).toInt().coerceAtLeast(1), stroke)
        }

    private fun ripple(content: Drawable, radius: Float, oval: Boolean = false): Drawable {
        val mask = GradientDrawable().apply { if (oval) shape = GradientDrawable.OVAL else cornerRadius = radius; setColor(0xFF000000.toInt()) }
        return RippleDrawable(ColorStateList.valueOf(if (c.dark) 0x29FFFFFF else 0x1F0E1822), content, mask)
    }

    private fun tint(v: View, color: Int) { (v as? ImageView)?.imageTintList = ColorStateList.valueOf(color) }
    private fun text(v: View, color: Int) { (v as? TextView)?.apply { setTextColor(color); setHintTextColor(c.inkMuted) } }

    /** Sombra do tile só no tema claro; no escuro a borda faz o trabalho (shadow-tile). */
    private fun tileShadow(v: View) { v.elevation = if (c.dark) 0f else dp(3); v.outlineSpotShadowColor = c.ink; v.outlineAmbientShadowColor = c.ink }

    fun apply(root: View) {
        val r = RADII
        when (root.tag) {
            // Superfícies
            "a_tile" -> { root.background = ripple(shape(c.surface, dp(r.lg), c.line), dp(r.lg)); tileShadow(root) }
            "a_tile_on" -> { root.background = ripple(shape(c.surface, dp(r.lg), c.accent, 3), dp(r.lg)); tileShadow(root) }
            "a_chip" -> { root.background = shape(c.surface, pill, c.line); tileShadow(root) }
            "a_chip_click" -> { root.background = ripple(shape(c.surface, pill, c.line), pill); tileShadow(root) }
            "a_chip_warn" -> { root.background = shape(c.warnSoft, pill, c.warn); tileShadow(root) }
            "a_chip_warn_click" -> { root.background = ripple(shape(c.warnSoft, pill, c.warn), pill); tileShadow(root) }
            "a_sunken" -> { root.background = shape(c.surfaceSunken, dp(r.md)); tint(root, c.ink) }
            "a_sunken_click" -> { root.background = ripple(shape(c.surfaceSunken, dp(r.md)), dp(r.md)); tint(root, c.ink) }
            "a_sunken_sm" -> { root.background = ripple(shape(c.surfaceSunken, dp(r.sm)), dp(r.sm)); tint(root, c.ink) }
            "a_sunken_pill" -> { root.background = ripple(shape(c.surfaceSunken, pill), pill); text(root, c.ink) }
            "a_sunken_round" -> { root.background = ripple(shape(c.surfaceSunken, 0f, oval = true), 0f, oval = true); tint(root, c.ink) }
            "a_raised_round" -> { root.background = ripple(shape(c.surfaceRaised, 0f, oval = true), 0f, oval = true); tint(root, c.ink) }
            "a_raised_sm" -> { root.background = ripple(shape(c.surfaceRaised, dp(r.sm), c.line), dp(r.sm)); text(root, c.ink) }
            "a_raised_md" -> { root.background = ripple(shape(c.surfaceRaised, dp(r.md), c.line), dp(r.md)); tileShadow(root); tint(root, c.ink) }
            "a_primary_round" -> { root.background = ripple(shape(c.accent, 0f, oval = true), 0f, oval = true); tint(root, c.onAccent) }
            "a_primary_pill" -> { root.background = ripple(shape(c.accent, pill), pill); text(root, c.onAccent) }
            "a_badge" -> { root.background = shape(c.accentSoft, dp(11)); tint(root, c.accent) }
            "a_badge_on" -> { root.background = shape(c.accent, dp(r.sm)); tint(root, c.onAccent) }
            "a_soft_pill" -> { root.background = shape(c.accentSoft, pill); text(root, c.accent) }
            "a_soft" -> root.background = shape(c.accentSoft, dp(r.md))
            "a_seg_on" -> { root.background = ripple(shape(c.surfaceRaised, pill, c.line), pill); text(root, c.accent) }
            "a_seg_off" -> { root.background = ripple(ColorDrawable(0), pill); text(root, c.inkMuted) }
            "a_mode_on" -> root.background = shape(c.accentSoft, dp(r.sm), c.accent)
            "a_mode_off" -> root.background = shape(c.surfaceSunken, dp(r.sm))
            "a_heat_on" -> root.background = shape(c.heatSoft, dp(r.sm), c.heat)
            "a_cool_on" -> root.background = shape(c.coolSoft, dp(r.sm), c.cool)
            "a_seat_off" -> root.background = shape(c.surfaceRaised, dp(r.sm))
            "a_field" -> root.background = shape(c.surfaceSunken, dp(r.md), c.accent, 2)
            "a_side" -> root.background = LayerDrawable(arrayOf(ColorDrawable(c.line), InsetDrawable(ColorDrawable(c.surfaceRaised), 0, 0, dp(1).toInt(), 0)))
            "a_dock" -> root.background = LayerDrawable(arrayOf(ColorDrawable(c.line), InsetDrawable(ColorDrawable(c.surfaceDock), 0, dp(1).toInt(), 0, 0)))
            "a_db" -> { root.background = ripple(ColorDrawable(0), dp(r.md)); tint(root, c.ink) }
            "a_db_on" -> { root.background = ripple(shape(c.accentSoft, dp(r.md)), dp(r.md)); tint(root, c.accent) }
            "a_plain_click" -> root.background = ripple(ColorDrawable(0), dp(r.md))
            "a_divider" -> root.background = ColorDrawable(c.line)
            "a_banner" -> root.background = shape(c.warnSoft, dp(r.md), c.warn)
            "a_ic" -> { root.background = shape(c.surfaceSunken, 0f, oval = true); tint(root, c.inkMuted) }
            "a_ic_warn" -> { root.background = shape(c.warn, 0f, oval = true); tint(root, c.warnSoft) }
            "a_ic_acc" -> { root.background = shape(c.accent, 0f, oval = true); tint(root, c.onAccent) }
            "a_bar" -> (root as? ProgressBar)?.apply {
                progressTintList = ColorStateList.valueOf(c.accent)
                progressBackgroundTintList = ColorStateList.valueOf(c.surfaceSunken)
            }
            // Textos
            "a_ink" -> text(root, c.ink)
            "a_muted" -> text(root, c.inkMuted)
            "a_accent" -> text(root, c.accent)
            "a_warn" -> text(root, c.warn)
            "a_ok" -> text(root, c.ok)
            "a_on_accent" -> text(root, c.onAccent)
            // Ícones
            "a_icon" -> tint(root, c.ink)
            "a_icon_muted" -> tint(root, c.inkMuted)
            "a_icon_accent" -> tint(root, c.accent)
            "a_icon_ok" -> tint(root, c.ok)
            "a_icon_warn" -> tint(root, c.warn)
            "a_icon_heat" -> tint(root, c.heat)
            "a_icon_cool" -> tint(root, c.cool)
            "a_icon_on_accent" -> tint(root, c.onAccent)
        }
        (root as? AuraThemed)?.onAura(c)
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(root.getChildAt(i))
    }

    /** Troca a tag e repinta só esta View (e filhos). */
    fun set(v: View?, tag: String) { v ?: return; v.tag = tag; apply(v) }

    private object RADII { const val sm = AuraDims.RADIUS_SM; const val md = AuraDims.RADIUS_MD; const val lg = AuraDims.RADIUS_LG }
}

/** Views desenhadas à mão (anel, equalizador, janela) recebem as cores por aqui. */
interface AuraThemed { fun onAura(c: AuraColors) }
