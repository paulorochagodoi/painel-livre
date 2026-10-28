package br.painellivre.ui.cockpit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import br.painellivre.car.TelemetrySnapshot

/**
 * Carro visto de cima com as animações de estado da issue #16: porta/capô/porta-malas abertos
 * (a porta "abre" e pisca em âmbar), cadeado trancado/destrancado, tampa do carregador aberta e
 * raio pulsando ao carregar. Desenho próprio em código (nada do SealNav).
 *
 * A animação só roda quando há algo animado e a View está na tela; parado, não gasta quadros.
 */
class CarStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var snapshot: TelemetrySnapshot? = null
        set(v) { field = v; syncAnimation(); invalidate() }

    var palette: Palette = Palette.NIGHT
        set(v) { field = v; invalidate() }

    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun animated(s: TelemetrySnapshot?) = s != null && (s.openings.isNotEmpty() || s.chargeLidOpen == true || s.charging == true)

    private fun syncAnimation() {
        val run = animated(snapshot) && isAttachedToWindow && visibility == VISIBLE
        if (run && !animator.isStarted) animator.start()
        if (!run && animator.isStarted) { animator.cancel(); phase = 0f }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); syncAnimation() }

    override fun onDraw(canvas: Canvas) {
        val s = snapshot
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        // Carro de pé (frente para cima), proporção ~1:2,2, centralizado.
        val ch = h * 0.9f
        val cw = (ch / 2.2f).coerceAtMost(w * 0.5f)
        val left = (w - cw) / 2; val top = (h - ch) / 2
        val warn = palette.warn
        val pulse = 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.cos(phase * 2 * Math.PI).toFloat())
        val swing = (0.5f - 0.5f * kotlin.math.cos(phase * 2 * Math.PI).toFloat()).coerceIn(0f, 1f)

        body.color = if (palette.dark) 0xFF2A3340.toInt() else 0xFFE3E8EF.toInt()
        line.color = palette.text3; line.strokeWidth = cw * 0.035f
        rect.set(left, top, left + cw, top + ch)
        canvas.drawRoundRect(rect, cw * 0.38f, cw * 0.38f, body)
        canvas.drawRoundRect(rect, cw * 0.38f, cw * 0.38f, line)

        // Para-brisa e vidro traseiro.
        fill.color = if (palette.dark) 0xFF141A22.toInt() else 0xFFB9C4D2.toInt()
        rect.set(left + cw * 0.14f, top + ch * 0.24f, left + cw * 0.86f, top + ch * 0.36f)
        canvas.drawRoundRect(rect, cw * 0.1f, cw * 0.1f, fill)
        rect.set(left + cw * 0.18f, top + ch * 0.76f, left + cw * 0.82f, top + ch * 0.84f)
        canvas.drawRoundRect(rect, cw * 0.1f, cw * 0.1f, fill)

        // Capô e porta-malas abertos: faixa âmbar piscando.
        fill.color = warn
        if (s?.isOpen("hood") == true) {
            fill.alpha = (255 * pulse).toInt()
            rect.set(left + cw * 0.1f, top + ch * 0.03f, left + cw * 0.9f, top + ch * 0.2f)
            canvas.drawRoundRect(rect, cw * 0.3f, cw * 0.3f, fill)
        }
        if (s?.isOpen("trunk") == true) {
            fill.alpha = (255 * pulse).toInt()
            rect.set(left + cw * 0.1f, top + ch * 0.87f, left + cw * 0.9f, top + ch * 0.97f)
            canvas.drawRoundRect(rect, cw * 0.3f, cw * 0.3f, fill)
        }
        fill.alpha = 255

        // Portas: linha na lateral; aberta, gira para fora até 35° e fica âmbar.
        door(canvas, s?.isOpen("door_fl"), left, top + ch * 0.38f, top + ch * 0.56f, -1, cw, swing, pulse)
        door(canvas, s?.isOpen("door_rl"), left, top + ch * 0.57f, top + ch * 0.74f, -1, cw, swing, pulse)
        door(canvas, s?.isOpen("door_fr"), left + cw, top + ch * 0.38f, top + ch * 0.56f, 1, cw, swing, pulse)
        door(canvas, s?.isOpen("door_rr"), left + cw, top + ch * 0.57f, top + ch * 0.74f, 1, cw, swing, pulse)

        // Tampa do carregador (traseira esquerda) e raio da carga.
        val px = left - cw * 0.02f; val py = top + ch * 0.8f
        if (s?.charging == true) {
            fill.color = palette.ok; fill.alpha = (255 * pulse).toInt()
            canvas.drawCircle(px, py, cw * 0.16f, fill)
            fill.alpha = 255
            bolt(canvas, px, py, cw * 0.13f, if (palette.dark) 0xFF0B1118.toInt() else 0xFFFFFFFF.toInt())
        } else if (s?.chargeLidOpen == true) {
            fill.color = warn; fill.alpha = (255 * pulse).toInt()
            canvas.drawCircle(px, py, cw * 0.11f, fill)
            fill.alpha = 255
        }

        // Cadeado no meio do teto.
        when (s?.locked) {
            true -> padlock(canvas, left + cw / 2, top + ch * 0.55f, cw * 0.2f, palette.ok, closed = true)
            false -> padlock(canvas, left + cw / 2, top + ch * 0.55f, cw * 0.2f, palette.text2, closed = false)
            null -> {}
        }
    }

    private fun door(c: Canvas, open: Boolean?, x: Float, y1: Float, y2: Float, side: Int, cw: Float, swing: Float, pulse: Float) {
        line.strokeWidth = cw * 0.07f
        if (open == true) {
            line.color = palette.warn
            line.alpha = (255 * (0.6f + 0.4f * pulse)).toInt()
            // Dobradiça na frente (y1); a ponta de trás vai para fora.
            val angle = Math.toRadians((15 + 20 * swing).toDouble())
            val len = y2 - y1
            c.drawLine(x, y1, x + side * (len * kotlin.math.sin(angle)).toFloat(), y1 + (len * kotlin.math.cos(angle)).toFloat(), line)
            line.alpha = 255
        } else {
            line.color = if (open == false) palette.text2 else palette.text3
            c.drawLine(x, y1 + cw * 0.03f, x, y2 - cw * 0.03f, line)
        }
        line.strokeWidth = cw * 0.035f
    }

    private fun padlock(c: Canvas, cx: Float, cy: Float, size: Float, color: Int, closed: Boolean) {
        fill.color = color
        rect.set(cx - size / 2, cy, cx + size / 2, cy + size * 0.8f)
        c.drawRoundRect(rect, size * 0.15f, size * 0.15f, fill)
        line.color = color; line.strokeWidth = size * 0.16f
        val r = size * 0.32f
        val shift = if (closed) 0f else size * 0.28f
        rect.set(cx - r, cy - r * 1.4f - shift, cx + r, cy + r * 0.6f - shift)
        c.drawArc(rect, 180f, 180f, false, line)
        c.drawLine(cx + r, cy - r * 0.4f - shift, cx + r, cy, line)
        if (closed) c.drawLine(cx - r, cy - r * 0.4f, cx - r, cy, line)
    }

    private fun bolt(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        path.reset()
        path.moveTo(cx + s * 0.15f, cy - s)
        path.lineTo(cx - s * 0.55f, cy + s * 0.15f)
        path.lineTo(cx - s * 0.05f, cy + s * 0.15f)
        path.lineTo(cx - s * 0.2f, cy + s)
        path.lineTo(cx + s * 0.55f, cy - s * 0.2f)
        path.lineTo(cx + s * 0.05f, cy - s * 0.2f)
        path.close()
        c.drawPath(path, fill)
    }
}
