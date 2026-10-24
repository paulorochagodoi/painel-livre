package br.painellivre.cluster

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import br.painellivre.nav.MapLook
import kotlin.math.min

/**
 * Seta do carro no mapa, no estilo do Waze: larga e cheia em `accent`, contorno claro e sombra leve
 * (forma e cores em [MapLook.ARROW]/[MapLook.arrowColors]). No painel e na busca da central.
 */
class CarArrowDrawable(colors: MapLook.ArrowColors) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.fill; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.outline; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.shadow; style = Paint.Style.FILL_AND_STROKE }

    override fun draw(c: Canvas) {
        val b = bounds
        // Margem para o contorno e a sombra não saírem da caixa.
        val s = min(b.width(), b.height()).toFloat() * 0.86f
        val x0 = b.exactCenterX() - s / 2f
        val y0 = b.exactCenterY() - s / 2f
        fun path(dy: Float) = Path().apply {
            MapLook.ARROW.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(x0 + s * x.toFloat(), y0 + s * y.toFloat() + dy) else lineTo(x0 + s * x.toFloat(), y0 + s * y.toFloat() + dy)
            }
            close()
        }
        val corner = CornerPathEffect(s * MapLook.ARROW_CORNER.toFloat())
        // O traço fica metade dentro da seta (coberta pelo preenchimento): o dobro aparece inteiro fora.
        val outline = 2 * s * MapLook.ARROW_OUTLINE.toFloat()
        shadow.pathEffect = corner
        shadow.strokeWidth = outline
        c.drawPath(path(s * MapLook.ARROW_SHADOW_DY.toFloat()), shadow)
        val p = path(0f)
        stroke.pathEffect = corner
        stroke.strokeWidth = outline
        c.drawPath(p, stroke)
        fill.pathEffect = corner
        c.drawPath(p, fill)
    }

    override fun getIntrinsicWidth() = 96
    override fun getIntrinsicHeight() = 96
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
