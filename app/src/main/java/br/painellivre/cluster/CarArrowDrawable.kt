package br.painellivre.cluster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Seta do carro no mapa (chevron azul com borda branca e sombra), no painel e na central. */
class CarArrowDrawable : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 119, 255); style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0); style = Paint.Style.FILL }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val x0 = b.left + (b.width() - s) / 2f
        val y0 = b.top + (b.height() - s) / 2f
        fun path(dy: Float) = Path().apply {
            moveTo(x0 + s * 0.5f, y0 + s * 0.08f + dy)
            lineTo(x0 + s * 0.86f, y0 + s * 0.9f + dy)
            lineTo(x0 + s * 0.5f, y0 + s * 0.7f + dy)
            lineTo(x0 + s * 0.14f, y0 + s * 0.9f + dy)
            close()
        }
        c.drawPath(path(s * 0.05f), shadow)
        val p = path(0f)
        c.drawPath(p, fill)
        stroke.strokeWidth = s * 0.06f
        c.drawPath(p, stroke)
    }

    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
