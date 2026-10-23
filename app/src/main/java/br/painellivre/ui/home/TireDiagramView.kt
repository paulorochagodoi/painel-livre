package br.painellivre.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Carro visto de cima com a pressão de cada pneu. Frente para cima; motorista à esquerda.
 * Porta aberta pinta a borda daquele lado. Sem animação: é um desenho estático e barato.
 */
class TireDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var frontLeft: String = "—"
    var frontRight: String = "—"
    var rearLeft: String = "—"
    var rearRight: String = "—"
    var frontLeftOpen: Boolean? = null
    var frontRightOpen: Boolean? = null
    var rearLeftOpen: Boolean? = null
    var rearRightOpen: Boolean? = null
    var trunkOpen: Boolean? = null

    var bodyColor: Int = 0xFF8A93A0.toInt()
    var cabinColor: Int = 0xFF2A313C.toInt()
    var textColor: Int = 0xFFF4F7FB.toInt()
    var warnColor: Int = 0xFFFFBA40.toInt()
    var mutedColor: Int = 0xFF8E98A6.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val box = RectF()

    fun bind(readout: TireReadout) {
        frontLeft = readout.frontLeft
        frontRight = readout.frontRight
        rearLeft = readout.rearLeft
        rearRight = readout.rearRight
        frontLeftOpen = readout.frontLeftOpen
        frontRightOpen = readout.frontRightOpen
        rearLeftOpen = readout.rearLeftOpen
        rearRightOpen = readout.rearRightOpen
        trunkOpen = readout.trunkOpen
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 8f || h < 8f) return
        val bodyW = w * 0.34f
        val bodyH = h * 0.78f
        val left = (w - bodyW) / 2f
        val top = (h - bodyH) / 2f
        val radius = bodyW * 0.22f
        fill.color = bodyColor
        box.set(left, top, left + bodyW, top + bodyH)
        canvas.drawRoundRect(box, radius, radius, fill)
        fill.color = cabinColor
        box.set(left + bodyW * 0.16f, top + bodyH * 0.22f, left + bodyW * 0.84f, top + bodyH * 0.72f)
        canvas.drawRoundRect(box, radius * 0.6f, radius * 0.6f, fill)

        stroke.strokeWidth = (h * 0.035f).coerceAtLeast(2f)
        fun edge(open: Boolean?, x1: Float, y1: Float, x2: Float, y2: Float) {
            if (open != true) return
            stroke.color = warnColor
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }
        edge(frontLeftOpen == true || rearLeftOpen == true, left + 2f, top + bodyH * 0.16f, left + 2f, top + bodyH * 0.84f)
        edge(frontRightOpen == true || rearRightOpen == true, left + bodyW - 2f, top + bodyH * 0.16f, left + bodyW - 2f, top + bodyH * 0.84f)
        edge(trunkOpen, left + bodyW * 0.2f, top + bodyH - 2f, left + bodyW * 0.8f, top + bodyH - 2f)

        val wheelW = bodyW * 0.22f
        val wheelH = bodyH * 0.18f
        fun wheel(cx: Float, cy: Float, open: Boolean?) {
            fill.color = if (open == true) warnColor else cabinColor
            box.set(cx - wheelW / 2f, cy - wheelH / 2f, cx + wheelW / 2f, cy + wheelH / 2f)
            canvas.drawRoundRect(box, wheelW * 0.35f, wheelW * 0.35f, fill)
        }
        wheel(left - wheelW * 0.15f, top + bodyH * 0.22f, frontLeftOpen)
        wheel(left + bodyW + wheelW * 0.15f, top + bodyH * 0.22f, frontRightOpen)
        wheel(left - wheelW * 0.15f, top + bodyH * 0.78f, rearLeftOpen)
        wheel(left + bodyW + wheelW * 0.15f, top + bodyH * 0.78f, rearRightOpen)

        text.textSize = h * 0.22f
        fun label(value: String, cx: Float, cy: Float) {
            text.color = if (value == HomeFormat.DASH) mutedColor else textColor
            val baseline = cy - (text.ascent() + text.descent()) / 2f
            canvas.drawText(value, cx, baseline, text)
        }
        val labelPad = w * 0.16f
        label(frontLeft, labelPad, top + bodyH * 0.22f)
        label(frontRight, w - labelPad, top + bodyH * 0.22f)
        label(rearLeft, labelPad, top + bodyH * 0.78f)
        label(rearRight, w - labelPad, top + bodyH * 0.78f)
    }
}
