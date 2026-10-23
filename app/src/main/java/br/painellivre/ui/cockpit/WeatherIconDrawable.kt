package br.painellivre.ui.cockpit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import br.painellivre.info.WeatherRepository.Kind
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ícone do tempo desenhado em código: sol/lua, nuvem, chuva, raio. */
class WeatherIconDrawable(private val kind: Kind, private val day: Boolean, private val palette: Palette) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        c.save()
        c.translate(b.left + (b.width() - s) / 2f, b.top + (b.height() - s) / 2f)
        val sunColor = if (day) Color.rgb(255, 184, 0) else palette.text2
        val cloud = if (palette.dark) Color.rgb(170, 180, 194) else Color.rgb(150, 160, 174)
        when (kind) {
            Kind.CLEAR -> sunOrMoon(c, s * 0.5f, s * 0.5f, s * 0.24f, sunColor)
            Kind.PARTLY -> { sunOrMoon(c, s * 0.38f, s * 0.38f, s * 0.18f, sunColor); cloud(c, s, cloud, 0.12f) }
            Kind.CLOUDY, Kind.FOG -> cloud(c, s, cloud, 0f)
            Kind.RAIN -> { cloud(c, s, cloud, -0.08f); drops(c, s, palette.accent) }
            Kind.STORM -> { cloud(c, s, cloud, -0.08f); bolt(c, s) }
        }
        c.restore()
    }

    private fun sunOrMoon(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(cx, cy, r, p)
        if (!day) {
            // Lua: tira um círculo de dentro com a cor do cartão.
            p.color = palette.card or (0xFF shl 24)
            c.drawCircle(cx + r * 0.45f, cy - r * 0.35f, r * 0.85f, p)
            return
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.22f
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(cx + (r * 1.35f * cos(a)).toFloat(), cy + (r * 1.35f * sin(a)).toFloat(),
                cx + (r * 1.75f * cos(a)).toFloat(), cy + (r * 1.75f * sin(a)).toFloat(), p)
        }
        p.style = Paint.Style.FILL
    }

    private fun cloud(c: Canvas, s: Float, color: Int, dy: Float) {
        p.style = Paint.Style.FILL
        p.color = color
        val y = s * (0.6f + dy)
        c.drawCircle(s * 0.42f, y - s * 0.08f, s * 0.2f, p)
        c.drawCircle(s * 0.62f, y - s * 0.02f, s * 0.16f, p)
        c.drawRoundRect(RectF(s * 0.18f, y - s * 0.02f, s * 0.82f, y + s * 0.16f), s * 0.08f, s * 0.08f, p)
    }

    private fun drops(c: Canvas, s: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.06f
        p.color = color
        for (x in listOf(0.35f, 0.52f, 0.69f)) c.drawLine(s * x, s * 0.78f, s * (x - 0.05f), s * 0.92f, p)
        p.style = Paint.Style.FILL
    }

    private fun bolt(c: Canvas, s: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 196, 0)
        val path = android.graphics.Path().apply {
            moveTo(s * 0.52f, s * 0.7f); lineTo(s * 0.42f, s * 0.86f); lineTo(s * 0.5f, s * 0.86f)
            lineTo(s * 0.44f, s * 0.98f); lineTo(s * 0.6f, s * 0.8f); lineTo(s * 0.52f, s * 0.8f); close()
        }
        c.drawPath(path, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
