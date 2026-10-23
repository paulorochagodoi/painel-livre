package br.painellivre.ui.cockpit

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Fundo da tela central: "estúdio" com horizonte e um foco de luz no chão, no tema do dia ou da
 * noite. Desenho próprio (o SealNav usa fotos; aqui nada é copiado).
 */
class BackdropDrawable(private val dark: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val horizon = h * 0.56f
        // Noite bem mais escura: na central real o azul claro lava o carro e os cartões.
        val sky = if (dark) intArrayOf(0xFF05070C.toInt(), 0xFF0A1018.toInt(), 0xFF101820.toInt())
        else intArrayOf(0xFFC5CED8.toInt(), 0xFFDDE2E8.toInt(), 0xFFE8E4DE.toInt())
        val floor = if (dark) intArrayOf(0xFF121820.toInt(), 0xFF080C12.toInt(), 0xFF05070B.toInt())
        else intArrayOf(0xFFD9D3CB.toInt(), 0xFFC9C2B8.toInt(), 0xFFB4ADA3.toInt())
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, sky, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, horizon, paint)
        paint.shader = LinearGradient(0f, horizon, 0f, h, floor, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, horizon, w, h, paint)
        // Foco curto e frio, sem o azul claro que some com a carroceria na tela do carro.
        val glow = if (dark) 0x22304458 else 0x55FFFFFF
        paint.shader = RadialGradient(w * 0.5f, horizon + h * 0.06f, w * 0.38f, intArrayOf(glow, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.28f, w * 0.5f, horizon + h * 0.06f)
        canvas.drawCircle(w * 0.5f, horizon + h * 0.06f, w * 0.38f, paint)
        canvas.restore()
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/**
 * Silhueta própria de um SUV médio de perfil (frente à direita, lado do motorista).
 * Não é o Song Plus: é um desenho novo, com a proporção de um crossover (teto alto,
 * cintura alta, rodas grandes, rack). Cada estado em [CarVisual] é um traço à parte,
 * para um dia animar a abertura sem redesenhar o resto.
 *
 * O pulso de carga só muda a opacidade de um círculo. Sem desfoque e sem camada extra,
 * para não pesar na Adreno 610.
 */
class CarArtDrawable(
    val dark: Boolean,
    visual: CarVisual = CarVisual(),
) : Drawable() {
    var visual: CarVisual = visual
        set(value) {
            field = value
            invalidateSelf()
        }

    /** 0..1, só usado com [CarVisual.charging]. Quem chama limita a taxa. */
    var pulse: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (visual.charging == true) invalidateSelf()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Espaço 1000 × 460. Chão em y = 420. Rodas grandes em (248, 368) e (770, 368):
    // crossover, cintura alta, vidro curto, traseira inclinada. Não é uma perua.
    private val rearWheel = RectF(178f, 298f, 318f, 438f)
    private val frontWheel = RectF(700f, 298f, 840f, 438f)
    private val body = Path().apply {
        moveTo(118f, 318f)
        cubicTo(100f, 286f, 96f, 236f, 120f, 196f)
        cubicTo(140f, 160f, 184f, 132f, 236f, 120f)
        lineTo(498f, 104f)
        cubicTo(548f, 100f, 592f, 120f, 642f, 168f)
        lineTo(830f, 186f)
        cubicTo(888f, 194f, 936f, 214f, 962f, 252f)
        lineTo(972f, 296f)
        cubicTo(976f, 316f, 958f, 328f, 934f, 330f)
        lineTo(840f, 330f)
        arcTo(frontWheel, 0f, -180f)
        lineTo(318f, 330f)
        arcTo(rearWheel, 0f, -180f)
        close()
    }
    private val glass = Path().apply {
        moveTo(246f, 134f)
        lineTo(486f, 118f)
        cubicTo(528f, 116f, 566f, 134f, 606f, 174f)
        lineTo(258f, 186f)
        cubicTo(238f, 166f, 236f, 146f, 246f, 134f)
        close()
    }
    private val hatch = Path().apply {
        moveTo(126f, 200f)
        cubicTo(146f, 162f, 184f, 136f, 236f, 124f)
        lineTo(244f, 184f)
        lineTo(142f, 210f)
        close()
    }
    private val doorFront = Path().apply {
        moveTo(452f, 180f)
        lineTo(618f, 170f)
        lineTo(626f, 304f)
        lineTo(456f, 312f)
        close()
    }
    private val doorRear = Path().apply {
        moveTo(256f, 188f)
        lineTo(438f, 176f)
        lineTo(442f, 308f)
        lineTo(258f, 316f)
        close()
    }
    private val cladding = Path().apply {
        moveTo(318f, 300f); lineTo(700f, 300f); lineTo(700f, 330f); lineTo(318f, 330f); close()
        moveTo(108f, 286f); cubicTo(110f, 308f, 114f, 322f, 122f, 330f); lineTo(178f, 330f); lineTo(184f, 294f); close()
        moveTo(840f, 304f); lineTo(972f, 296f); cubicTo(976f, 316f, 958f, 328f, 934f, 330f); lineTo(840f, 330f); close()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = min(b.width() / 1000f, b.height() / 460f)
        canvas.save()
        canvas.translate(b.left + (b.width() - 1000f * s) / 2f, b.top + (b.height() - 460f * s) / 2f)
        canvas.scale(s, s)

        // Sombra no chão, mais fechada que o halo azul antigo.
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(520f, 408f, 420f, intArrayOf(if (dark) 0xCC000000.toInt() else 0x66000000, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.07f, 520f, 408f)
        canvas.drawCircle(520f, 408f, 420f, p)
        canvas.restore()
        p.shader = null

        if (visual.charging == true) {
            val glow = 0xFF3DDC84.toInt()
            p.shader = RadialGradient(150f, 268f, 78f + 18f * pulse, intArrayOf(withAlpha(glow, 0.20f + 0.45f * pulse), 0x00000000), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(150f, 268f, 78f + 18f * pulse, p)
            p.shader = null
        }

        val paint = if (dark) intArrayOf(0xFFF2F5F8.toInt(), 0xFFB4BEC8.toInt(), 0xFF5A636E.toInt())
        else intArrayOf(0xFFF7F8FA.toInt(), 0xFFC5CCD4.toInt(), 0xFF7A838E.toInt())
        p.shader = LinearGradient(0f, 110f, 0f, 338f, paint, floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(body, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = if (dark) 0xFF14181E.toInt() else 0xFF2A3038.toInt()
        canvas.drawPath(body, p)
        p.style = Paint.Style.FILL

        canvas.save()
        canvas.clipRect(0f, 0f, 1000f, 334f)
        p.color = 0xFF101317.toInt()
        canvas.drawCircle(248f, 368f, 74f, p)
        canvas.drawCircle(770f, 368f, 74f, p)
        canvas.restore()

        p.color = if (dark) 0xFF1A1E24.toInt() else 0xFF2E343C.toInt()
        canvas.drawPath(cladding, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 16f
        canvas.drawArc(RectF(174f, 294f, 322f, 442f), 200f, 140f, false, p)
        canvas.drawArc(RectF(696f, 294f, 844f, 442f), 200f, 140f, false, p)
        p.style = Paint.Style.FILL

        p.shader = LinearGradient(0f, 126f, 0f, 198f, intArrayOf(0xFF3C4654.toInt(), 0xFF10141A.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(glass, p)
        p.shader = null
        drawHatch(canvas, paint[1])

        // Colunas escuras (teto flutuante) e rack.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = if (dark) 0xFF161A20.toInt() else 0xFF2A3038.toInt()
        p.strokeWidth = 16f
        canvas.drawLine(420f, 116f, 416f, 186f, p)
        p.strokeWidth = 12f
        canvas.drawLine(268f, 128f, 264f, 186f, p)
        p.strokeWidth = 7f
        canvas.drawLine(250f, 108f, 470f, 94f, p)
        p.strokeWidth = 5f
        canvas.drawLine(268f, 108f, 270f, 122f, p)
        canvas.drawLine(452f, 96f, 454f, 110f, p)
        p.strokeCap = Paint.Cap.BUTT

        // Linha de cintura e cortes das portas (estado fechado ou desconhecido).
        p.strokeWidth = 3.5f
        p.color = 0x88FFFFFF.toInt()
        canvas.drawLine(130f, 214f, 940f, 222f, p)
        p.color = 0x66000000
        p.strokeWidth = 2.5f
        if (visual.frontDoorOpen != true) canvas.drawLine(452f, 180f, 456f, 308f, p)
        if (visual.rearDoorOpen != true) canvas.drawLine(256f, 188f, 258f, 312f, p)
        p.style = Paint.Style.FILL

        drawDoor(canvas, doorRear, visual.rearDoorOpen)
        drawDoor(canvas, doorFront, visual.frontDoorOpen)

        // Retrovisor, farol em barra e lanterna.
        p.color = paint[2]
        canvas.drawRoundRect(RectF(630f, 156f, 678f, 182f), 8f, 8f, p)
        p.color = 0xFFEAF4FF.toInt()
        canvas.drawRoundRect(RectF(888f, 198f, 960f, 216f), 6f, 6f, p)
        p.color = 0xFFD8323A.toInt()
        canvas.drawRoundRect(RectF(108f, 176f, 132f, 224f), 6f, 6f, p)

        drawChargePort(canvas)
        drawLock(canvas)
        if (visual.farDoorOpen == true) {
            p.color = 0xFFFFBA40.toInt()
            canvas.drawCircle(360f, 96f, 11f, p)
            p.color = 0xFF1A1208.toInt()
            canvas.drawCircle(360f, 96f, 4f, p)
        }

        wheel(canvas, 248f, 368f)
        wheel(canvas, 770f, 368f)
        canvas.restore()
    }

    private fun drawHatch(canvas: Canvas, bodyMid: Int) {
        val open = visual.trunkOpen == true
        canvas.save()
        if (open) canvas.translate(-8f, -22f)
        p.style = Paint.Style.FILL
        p.color = if (open) 0xFF8E98A4.toInt() else bodyMid
        canvas.drawPath(hatch, p)
        if (open) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.color = 0xFFFFBA40.toInt()
            canvas.drawPath(hatch, p)
            p.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    /**
     * Porta aberta: só o contorno âmbar, deslocado poucos pixels.
     * Sem preencher um painel claro por cima da carroceria (isso virava um retângulo branco).
     * O [Path] separado existe para um animator interpolar o deslocamento depois.
     */
    private fun drawDoor(canvas: Canvas, door: Path, open: Boolean?) {
        if (open != true) return
        canvas.save()
        canvas.translate(-4f, -8f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0xFFFFBA40.toInt()
        canvas.drawPath(door, p)
        p.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun drawChargePort(canvas: Canvas) {
        val open = visual.chargePortOpen == true
        if (visual.chargePortOpen == null && visual.charging != true) return
        if (open) {
            p.color = if (dark) 0xFF2A313C.toInt() else 0xFF3A424C.toInt()
            canvas.drawRoundRect(RectF(128f, 248f, 176f, 292f), 8f, 8f, p)
            p.color = 0xFF10141A.toInt()
            canvas.drawCircle(152f, 270f, 10f, p)
            p.color = if (visual.charging == true) 0xFF3DDC84.toInt() else 0xFFD7DEE6.toInt()
            canvas.drawRoundRect(RectF(126f, 214f, 178f, 246f), 6f, 6f, p)
        } else {
            p.color = if (dark) 0xFF8E98A4.toInt() else 0xFF9AA3AE.toInt()
            canvas.drawRoundRect(RectF(128f, 246f, 176f, 292f), 8f, 8f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = 0xFF1A1E24.toInt()
            canvas.drawRoundRect(RectF(128f, 246f, 176f, 292f), 8f, 8f, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawLock(canvas: Canvas) {
        val locked = visual.locked ?: return
        val color = if (locked) 0xFF3DDC84.toInt() else 0xFFFFBA40.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4.5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        val top = if (locked) 232f else 218f
        canvas.drawArc(RectF(592f, top, 618f, top + 28f), 200f, 140f, false, p)
        p.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(586f, 252f, 624f, 278f), 4f, 4f, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun wheel(c: Canvas, cx: Float, cy: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0xFF12151A.toInt()
        c.drawCircle(cx, cy, 64f, p)
        p.shader = RadialGradient(cx - 12f, cy - 14f, 48f, intArrayOf(0xFFF0F3F6.toInt(), 0xFF7A828C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 42f, p)
        p.shader = null
        p.color = 0xFF2A3038.toInt()
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            val spoke = Path().apply {
                moveTo(cx + 10f * cos(a + 0.85).toFloat(), cy + 10f * sin(a + 0.85).toFloat())
                lineTo(cx + 38f * cos(a + 0.22).toFloat(), cy + 38f * sin(a + 0.22).toFloat())
                lineTo(cx + 38f * cos(a + 0.95).toFloat(), cy + 38f * sin(a + 0.95).toFloat())
                close()
            }
            c.drawPath(spoke, p)
        }
        p.color = 0xFF4E565F.toInt()
        c.drawCircle(cx, cy, 9f, p)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun getIntrinsicWidth() = 1000
    override fun getIntrinsicHeight() = 460
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
