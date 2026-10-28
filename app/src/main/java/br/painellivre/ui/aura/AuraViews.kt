package br.painellivre.ui.aura

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import kotlin.math.min

/** Animações ligadas? Com "remover animações" do Android (escala 0), o Aura não anima nada. */
fun animationsOn(): Boolean = ValueAnimator.areAnimatorsEnabled()

/**
 * Janela flutuante do Aura (`.au-pop`): `surface-raised`, borda `line`, raio `radius-xl`, sombra
 * `shadow-pop` e a seta embaixo apontando para o atalho ([caretX] em px a partir da esquerda).
 * A altura nunca passa de [maxHeightPx] (o conteúdo rola por dentro).
 */
class PopoverFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs), AuraThemed {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d }
    private val path = Path()
    private val caret = 9 * d
    var maxHeightPx = (AuraDims.POPOVER_MAX_HEIGHT * d).toInt()
    var caretX = -1f
        set(v) { field = v; invalidate() }

    init {
        setWillNotDraw(false)
        clipToPadding = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, (view.height - caret).toInt(), AuraDims.RADIUS_XL * d)
            }
        }
        elevation = 24 * d
    }

    override fun onAura(c: AuraColors) {
        fill.color = c.surfaceRaised
        stroke.color = c.line
        outlineSpotShadowColor = if (c.dark) 0xFF000000.toInt() else c.ink
        outlineAmbientShadowColor = outlineSpotShadowColor
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit = maxHeightPx + caret.toInt()
        val spec = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(min(limit, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.getMode(heightMeasureSpec))
        }
        super.onMeasure(widthMeasureSpec, spec)
    }

    private val tip = Path()
    private val outline = Path()
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = AuraDims.RADIUS_XL * d
        val h = height - caret
        val cx = if (caretX < 0) width / 2f else caretX.coerceIn(r + caret, width - r - caret)
        fun shape(into: Path, inset: Float) {
            into.reset(); tip.reset()
            box.set(inset, inset, width - inset, h - inset)
            into.addRoundRect(box, r, r, Path.Direction.CW)
            tip.moveTo(cx - caret * 1.3f, h - 1 - inset); tip.lineTo(cx, h + caret - inset); tip.lineTo(cx + caret * 1.3f, h - 1 - inset); tip.close()
            into.op(tip, Path.Op.UNION)
        }
        shape(path, 0f)
        canvas.drawPath(path, fill)
        shape(outline, stroke.strokeWidth / 2)
        canvas.drawPath(outline, stroke)
        super.onDraw(canvas)
    }
}

/**
 * Posiciona cada filho com o centro numa fração da área (chips ao redor do carro, `.au-chip`
 * com `translate(-50%,-50%)`), sem sair das bordas.
 */
class AnchoredLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    class LayoutParams(var fx: Float = .5f, var fy: Float = .5f) : ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

    override fun generateDefaultLayoutParams() = LayoutParams()
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LayoutParams
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?) = LayoutParams()
    override fun generateLayoutParams(attrs: AttributeSet?) = LayoutParams()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val child = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (i in 0 until childCount) getChildAt(i).measure(child, child)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == GONE) continue
            val p = v.layoutParams as LayoutParams
            val x = (p.fx * w - v.measuredWidth / 2f).toInt().coerceIn(0, (w - v.measuredWidth).coerceAtLeast(0))
            val y = (p.fy * h - v.measuredHeight / 2f).toInt().coerceIn(0, (h - v.measuredHeight).coerceAtLeast(0))
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
    }
}

/** Anel da bateria (`.au-ring`): trilho `surface-sunken`, carga em `accent`. */
class RingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), AuraThemed {
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12 * d }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12 * d; strokeCap = Paint.Cap.ROUND }
    /** 0–100, ou null sem dado (só o trilho). */
    var percent: Double? = null
        set(v) { field = v; invalidate() }

    override fun onAura(c: AuraColors) { track.color = c.surfaceSunken; bar.color = c.accent; invalidate() }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) - track.strokeWidth
        rect.set((width - s) / 2, (height - s) / 2, (width + s) / 2, (height + s) / 2)
        canvas.drawOval(rect, track)
        percent?.let { canvas.drawArc(rect, -90f, (360 * it / 100).toFloat().coerceIn(0f, 360f), false, bar) }
    }
}

/**
 * Barras de nível (ventilação 1–7, `.au-fanbars`; aquecimento/ventilação do banco 1–3, `.au-lv`).
 * Só mostra o nível lido do carro.
 */
class LevelBars @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), AuraThemed {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var count = 7
    var rising = true
    var level = 0
        set(v) { field = v; invalidate() }
    private var on = 0
    private var off = 0
    /** Cor do nível ligado (accent, ou heat/cool nos bancos). */
    var onColor: Int? = null
        set(v) { field = v; invalidate() }

    override fun onAura(c: AuraColors) { on = c.accent; off = if (rising) c.surfaceSunken else c.line; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val gap = (if (rising) 5 else 4) * d
        val w = (width - gap * (count - 1)) / count
        for (i in 1..count) {
            val h = if (rising) height * (0.3f + i * 0.1f).coerceAtMost(1f) else height.toFloat()
            val x = (i - 1) * (w + gap)
            paint.color = if (i <= level) (onColor ?: on) else off
            canvas.drawRoundRect(x, height - h, x + w, height.toFloat(), 4 * d, 4 * d, paint)
        }
    }
}

/** Equalizador da capa (`.au-eq`): anima só tocando e com animações ligadas. */
class EqualizerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), AuraThemed {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val base = floatArrayOf(.4f, .8f, .55f, .95f, .35f)
    private val phase = floatArrayOf(0f, .3f, .6f, .15f, .45f)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    var playing = false
        set(v) { field = v; sync() }

    override fun onAura(c: AuraColors) { paint.color = c.accent; invalidate() }
    private fun sync() { if (playing && isAttachedToWindow && animationsOn()) { if (!anim.isStarted) anim.start() } else { anim.cancel(); t = 0f; invalidate() } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = 7 * d; val gap = 5 * d
        val total = base.size * w + (base.size - 1) * gap
        var x = (width - total) / 2
        for (i in base.indices) {
            val k = if (anim.isRunning) .35f + .65f * ((t + phase[i]) % 1f) else 1f
            val h = height * base[i] * k
            canvas.drawRoundRect(x, height - h, x + w, height.toFloat(), 3 * d, 3 * d, paint)
            x += w + gap
        }
    }
}

/**
 * Fundo da tela (`.au-backdrop`): cor `bg`, duas faixas de horizonte em `horizon` (a de trás a 55 %)
 * e o halo `platform-glow` sob o carro. Só decoração, nunca atrás de texto importante.
 */
class AuraBackdropDrawable(private val c: AuraColors, private val glowCenterY: Float = .52f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Silhueta do desenho base 1280 × 720 (pontos x, y da linha de cima).
    private val far = floatArrayOf(0f, 300f, 70f, 262f, 130f, 280f, 210f, 226f, 280f, 262f, 350f, 236f, 420f, 270f, 500f, 246f, 560f, 268f,
        640f, 232f, 720f, 270f, 800f, 240f, 880f, 266f, 960f, 220f, 1040f, 262f, 1110f, 238f, 1190f, 268f, 1280f, 244f)
    private val near = floatArrayOf(0f, 330f, 90f, 300f, 170f, 318f, 260f, 290f, 360f, 312f, 470f, 296f, 600f, 320f, 720f, 300f, 840f, 318f,
        960f, 296f, 1080f, 316f, 1180f, 300f, 1280f, 314f)

    private fun ridge(canvas: Canvas, pts: FloatArray, alpha: Int) {
        val b = bounds
        val sx = b.width() / 1280f; val sy = b.height() / 720f
        val p = Path().apply {
            moveTo(b.left + pts[0] * sx, b.top + pts[1] * sy)
            var i = 2
            while (i < pts.size) { lineTo(b.left + pts[i] * sx, b.top + pts[i + 1] * sy); i += 2 }
            lineTo(b.right.toFloat(), b.bottom.toFloat()); lineTo(b.left.toFloat(), b.bottom.toFloat()); close()
        }
        paint.shader = null; paint.color = c.horizon; paint.alpha = alpha
        canvas.drawPath(p, paint)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(c.bg)
        ridge(canvas, far, (255 * .55f).toInt())
        ridge(canvas, near, 255)
        // O chão fica um pouco mais claro/escuro para o carro "pousar" (sem texto por cima).
        paint.alpha = 255
        paint.shader = LinearGradient(0f, b.top + b.height() * .45f, 0f, b.bottom.toFloat(), c.horizon, c.bg, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, b.top + b.height() * .45f, b.right.toFloat(), b.bottom.toFloat(), paint)
        val cx = b.exactCenterX(); val cy = b.top + b.height() * glowCenterY
        val rx = b.width() * .26f
        paint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(c.platformGlow, c.platformGlow and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.save(); canvas.translate(cx, cy); canvas.scale(rx, rx * .42f); canvas.drawCircle(0f, 0f, 1f, paint); canvas.restore()
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
}
