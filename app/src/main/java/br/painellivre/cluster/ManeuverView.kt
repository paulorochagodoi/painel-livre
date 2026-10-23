package br.painellivre.cluster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import br.painellivre.nav.Maneuver
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Desenha a seta da próxima manobra. Usa a figura que o próprio Google Maps/Waze mandou na
 * notificação quando existe (é a mais fiel, inclui saídas e faixas); senão desenha um vetor.
 */
class ManeuverView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var maneuver: Maneuver = Maneuver.UNKNOWN
        set(v) { field = v; invalidate() }
    var roundaboutExit: Int? = null
        set(v) { field = v; invalidate() }
    var bitmap: Bitmap? = null
        set(v) { field = v; invalidate() }
    var tint: Boolean = true
        set(v) { field = v; bmpPaint.colorFilter = if (v) PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) else null; invalidate() }
    var color: Int = Color.WHITE
        set(v) { field = v; paint.color = v; fill.color = v; tint = tint }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(90, 255, 255, 255)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0) return
        val b = bitmap
        if (b != null && !b.isRecycled) {
            val pad = s * 0.04f
            val src = Rect(0, 0, b.width, b.height)
            val scale = min((width - 2 * pad) / b.width, (height - 2 * pad) / b.height)
            val w = b.width * scale
            val h = b.height * scale
            c.drawBitmap(b, src, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), bmpPaint)
            return
        }
        val cx = width / 2f
        val stroke = s * 0.12f
        paint.strokeWidth = stroke
        ghost.strokeWidth = stroke * 0.8f
        val bottom = height / 2f + s * 0.42f
        val mid = height / 2f + s * 0.05f
        when (maneuver) {
            Maneuver.DESTINATION -> drawPin(c, cx, height / 2f, s)
            Maneuver.ROUNDABOUT -> drawRoundabout(c, cx, height / 2f, s)
            Maneuver.UTURN_LEFT, Maneuver.UTURN_RIGHT -> drawUturn(c, cx, height / 2f, s, maneuver == Maneuver.UTURN_RIGHT)
            Maneuver.UNKNOWN -> {}
            else -> {
                // Haste vertical + segmento girado no ângulo da manobra + ponta.
                val ang = Math.toRadians(maneuver.angle.toDouble())
                val len = s * 0.36f
                val ex = cx + (sin(ang) * len).toFloat()
                val ey = mid - (cos(ang) * len).toFloat()
                if (maneuver == Maneuver.KEEP_LEFT || maneuver == Maneuver.KEEP_RIGHT ||
                    maneuver == Maneuver.EXIT_LEFT || maneuver == Maneuver.EXIT_RIGHT) {
                    c.drawLine(cx, mid, cx, height / 2f - s * 0.36f, ghost) // pista que continua reto
                }
                path.reset(); path.moveTo(cx, bottom); path.lineTo(cx, mid); path.lineTo(ex, ey)
                c.drawPath(path, paint)
                drawHead(c, ex, ey, ang, s * 0.2f)
            }
        }
    }

    private fun drawHead(c: Canvas, x: Float, y: Float, ang: Double, size: Float) {
        val back = ang + Math.PI
        val l = back - 0.55
        val r = back + 0.55
        path.reset()
        path.moveTo(x + (sin(ang) * size * 0.35).toFloat(), y - (cos(ang) * size * 0.35).toFloat())
        path.lineTo(x + (sin(l) * size).toFloat(), y - (cos(l) * size).toFloat())
        path.lineTo(x + (sin(r) * size).toFloat(), y - (cos(r) * size).toFloat())
        path.close()
        c.drawPath(path, fill)
    }

    private fun drawUturn(c: Canvas, cx: Float, cy: Float, s: Float, right: Boolean) {
        // Retorno à esquerda (padrão no Brasil): sobe pela direita, faz o arco, desce pela esquerda.
        val r = s * 0.17f
        val top = cy - s * 0.1f
        val startX = if (right) cx - r else cx + r
        val endX = if (right) cx + r else cx - r
        path.reset()
        path.moveTo(startX, cy + s * 0.4f)
        path.lineTo(startX, top)
        path.arcTo(RectF(cx - r, top - r, cx + r, top + r), if (right) 180f else 0f, if (right) 180f else -180f)
        path.lineTo(endX, cy + s * 0.1f)
        c.drawPath(path, paint)
        drawHead(c, endX, cy + s * 0.18f, Math.PI, s * 0.2f)
    }

    private fun drawRoundabout(c: Canvas, cx: Float, cy: Float, s: Float) {
        // Mão direita: entra por baixo e gira no sentido anti-horário, saindo no alto à direita.
        val r = s * 0.2f
        c.drawCircle(cx, cy, r, ghost)
        path.reset()
        path.moveTo(cx, cy + s * 0.45f)
        path.lineTo(cx, cy + r)
        path.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 90f, -135f)
        c.drawPath(path, paint)
        val ang = Math.toRadians(45.0)
        val sx = cx + (r * cos(Math.toRadians(-45.0))).toFloat()
        val sy = cy + (r * sin(Math.toRadians(-45.0))).toFloat()
        val ex = sx + (sin(ang) * s * 0.18f).toFloat()
        val ey = sy - (cos(ang) * s * 0.18f).toFloat()
        c.drawLine(sx, sy, ex, ey, paint)
        drawHead(c, ex, ey, ang, s * 0.18f)
        roundaboutExit?.let {
            text.textSize = s * 0.2f
            c.drawCircle(cx, cy, r * 0.75f, fill)
            c.drawText(it.toString(), cx, cy + text.textSize * 0.35f, text)
        }
    }

    private fun drawPin(c: Canvas, cx: Float, cy: Float, s: Float) {
        val r = s * 0.2f
        path.reset()
        path.moveTo(cx, cy + s * 0.38f)
        path.lineTo(cx - r * 0.95f, cy - s * 0.02f)
        path.arcTo(RectF(cx - r, cy - s * 0.12f - r, cx + r, cy - s * 0.12f + r), 150f, 240f)
        path.close()
        c.drawPath(path, fill)
        c.drawCircle(cx, cy - s * 0.12f, r * 0.4f, hole)
    }

    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
}
