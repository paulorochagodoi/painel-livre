package br.painellivre.ui.cockpit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import br.painellivre.R
import br.painellivre.car.TelemetrySnapshot
import java.util.Locale
import kotlin.math.min

/** Renders locais; anima somente a troca de vista e o pulso durante a carga. */
class SongCarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {
    var modelEnabled = true
    var palette = Palette.NIGHT
        set(value) { field = value; invalidate() }
    /** Cor do halo sob o carro; null = verde do tema (cockpit antigo). A tela Aura usa platform-glow. */
    var glow: Int? = null
        set(value) { field = value; glowShader = null; invalidate() }
    var snapshot: TelemetrySnapshot? = null
        set(value) {
            field = value
            val next = CarOverlay.state(value)
            val changed = state.highlight != next.highlight
            state = next
            contentDescription = "Song Plus" + if (next.highlight) ", destaques dos dados do carro" else ""
            if (changed && isShown && windowVisibility == VISIBLE) {
                fade.cancel()
                fade.setFloatValues(mix, if (next.highlight) 1f else 0f)
                fade.start()
            } else if (!isShown || windowVisibility != VISIBLE) mix = if (next.highlight) 1f else 0f
            syncPulse(); invalidate()
        }
    private var state = CarOverlay.state(null)
    private var mix = 0f
    private var pulse = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var glowColor = 0
    private var glowShader: Shader? = null
    private val fade = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 250
        addUpdateListener { mix = it.animatedValue as Float; invalidate() }
    }
    private val chargingPulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }
    // Cache compartilhado entre recriações e rotações; sem decodificação em onDraw.
    private val base = bitmap(context, R.drawable.car_34_base)
    private val top = bitmap(context, R.drawable.car_top)
    private fun syncPulse() {
        if (state.charging && modelEnabled && isShown && windowVisibility == VISIBLE && isAttachedToWindow) {
            if (!chargingPulse.isStarted) chargingPulse.start()
        } else chargingPulse.cancel()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncPulse() }
    override fun onDetachedFromWindow() { fade.cancel(); chargingPulse.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // ImageView pode chamar isto antes dos animadores serem inicializados.
        if (isAttachedToWindow) { syncPulse(); if (!isShown) { fade.cancel(); mix = if (state.highlight) 1f else 0f } }
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) { if (visibility == VISIBLE) syncPulse() else { chargingPulse.cancel(); fade.cancel(); mix = if (state.highlight) 1f else 0f } }
    }
    override fun onDraw(c: Canvas) {
        if (!modelEnabled) { super.onDraw(c); return }
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0 || h <= 0) return
        fun image(b: Bitmap, alpha: Float): RectF {
            val scale = min(w * (if (b === top) .85f else 1f) / b.width, h * (if (b === top) .68f else .9f) / b.height)
            val bw = b.width * scale; val bh = b.height * scale
            rect.set(paddingLeft + (w-bw)/2, paddingTop + (h-bh)/2, paddingLeft+(w+bw)/2, paddingTop+(h+bh)/2)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.alpha = (255*alpha).toInt()
            c.drawBitmap(b, null, rect, paint)
            return rect
        }
        // O render base já inclui sombra de contato; halo elíptico discreto integra o fundo.
        val glowSave = c.save()
        c.translate(paddingLeft+w*.5f, paddingTop+h*.76f)
        c.scale(w*.36f,h*.12f)
        paint.alpha = 255
        val g = glow ?: ((palette.ok and 0xFFFFFF) or 0x18000000)
        if (glowColor != g || glowShader == null) {
            glowColor = g
            glowShader = RadialGradient(0f,0f,1f,intArrayOf(g,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        }
        paint.shader = glowShader
        c.drawCircle(0f,0f,1f,paint)
        paint.shader = null
        c.restoreToCount(glowSave)
        if (mix < 1f) image(base, 1f-mix)
        if (mix > 0f) {
            val r = image(top, mix)
            val save = c.save()
            c.translate(r.left, r.top); c.scale(r.width(), r.height())
            fun segment(m: CarOverlay.Mark, color: Int) {
                paint.color = color; paint.alpha = (mix*255).toInt(); paint.strokeWidth = .012f; paint.strokeCap = Paint.Cap.ROUND
                c.drawLine(m.x, m.y, m.x2, m.y2, paint)
            }
            state.open.forEach { segment(CarOverlay.openings.getValue(it), palette.warn) }
            state.windows.forEach { segment(CarOverlay.windows.getValue(it), palette.warn) }
            CarOverlay.tyres.forEach { (k, m) ->
                val status = state.tyres.getValue(k)
                segment(CarOverlay.Mark(m.x-.025f,m.y,m.x+.025f,m.y), when(status) {
                    CarOverlay.Tyre.ALERT -> palette.warn; CarOverlay.Tyre.NORMAL -> palette.ok; else -> palette.text3
                })
                val text = state.pressures[k]?.let { String.format(Locale("pt", "BR"), "%.2f bar", it/100) } ?: "—"
                val label = c.save()
                c.scale(1f/r.width(), 1f/r.height())
                paint.textSize = 16f * resources.displayMetrics.scaledDensity
                paint.textAlign = Paint.Align.CENTER
                c.drawText(text, m.x*r.width(), (if (m.y < .5f) m.y-.055f else m.y+.085f)*r.height(), paint)
                c.restoreToCount(label)
            }
            val m = CarOverlay.charger
            if (state.lid) { paint.color = palette.warn; paint.alpha = (255*mix).toInt(); c.drawCircle(m.x,m.y,.025f,paint) }
            if (state.charging) {
                paint.color = palette.ok; paint.alpha = ((130+100*pulse)*mix).toInt()
                c.drawCircle(m.x,m.y,.033f+.008f*pulse,paint)
                paint.color = Color.BLACK; paint.alpha = (255*mix).toInt(); paint.strokeWidth = .008f
                c.drawLine(m.x+.008f,m.y-.025f,m.x-.008f,m.y,paint)
                c.drawLine(m.x-.008f,m.y,m.x+.008f,m.y,paint)
                c.drawLine(m.x+.008f,m.y,m.x-.008f,m.y+.025f,paint)
            }
            c.restoreToCount(save)
        }
        // Cadeado fica legível nas duas vistas e não altera a decisão de destaque.
        if (state.locked != null) {
            val m = if (mix >= .5f) CarOverlay.lock else CarOverlay.baseLock
            val r = imageBounds(if (mix >= .5f) top else base,w,h)
            val x = r.left+r.width()*m.x; val y = r.top+r.height()*m.y
            val size = min(w,h)*.045f
            paint.alpha=255; paint.color=if(state.locked == true) palette.ok else palette.text2
            paint.style=Paint.Style.STROKE; paint.strokeWidth=size*.16f
            c.drawArc(x-size*.3f,y-size*.6f,x+size*.3f,y+size*.2f,if (state.locked == true) 180f else 210f,if (state.locked == true) 180f else 140f,false,paint)
            paint.style=Paint.Style.FILL
            c.drawRoundRect(x-size*.45f,y,x+size*.45f,y+size*.65f,size*.1f,size*.1f,paint)
            if (state.locked == false) { paint.color=palette.text2; c.drawLine(x+size*.3f,y-size*.4f,x+size*.55f,y-size*.65f,paint) }
        }
        paint.alpha=255
    }
    private fun imageBounds(b: Bitmap,w:Float,h:Float): RectF {
        val scale=min(w*(if(b === top) .85f else 1f)/b.width,h*(if(b === top) .68f else .9f)/b.height)
        return RectF(paddingLeft+(w-b.width*scale)/2,paddingTop+(h-b.height*scale)/2,paddingLeft+(w+b.width*scale)/2,paddingTop+(h+b.height*scale)/2)
    }
    companion object {
        private val cache = mutableMapOf<Int,Bitmap>()
        @Synchronized private fun bitmap(c:Context,id:Int):Bitmap = cache.getOrPut(id) {
            BitmapFactory.decodeResource(c.resources,id,BitmapFactory.Options().apply { inScaled=false })
        }
    }
}
