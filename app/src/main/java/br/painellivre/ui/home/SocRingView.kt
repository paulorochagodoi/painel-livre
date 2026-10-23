package br.painellivre.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Anel de carga. Sem brilho borrado: um traço redondo e, só carregando, um segundo traço
 * cuja opacidade o pulso altera. Invalidar esta View não redesenha a tela inteira.
 */
class SocRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var percent: Int? = null
        set(value) {
            field = value
            invalidate()
        }
    var charging: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var pulse: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (charging) invalidate()
        }
    var trackColor: Int = 0x66C8D0DC
    var fillColor: Int = 0xFF34C778.toInt()
    var pulseColor: Int = 0xFF2A78DC.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val stroke = size * 0.11f
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - stroke
        paint.strokeWidth = stroke
        paint.color = trackColor
        canvas.drawCircle(cx, cy, radius, paint)
        val value = percent
        if (value != null && value > 0) {
            paint.color = fillColor
            arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arc, -90f, 360f * value / 100f, false, paint)
        }
        if (charging) {
            paint.color = pulseColor
            paint.alpha = (50 + 150 * pulse).toInt()
            paint.strokeWidth = stroke * 0.42f
            canvas.drawCircle(cx, cy, radius + stroke * 0.85f, paint)
            paint.alpha = 255
        }
    }
}
