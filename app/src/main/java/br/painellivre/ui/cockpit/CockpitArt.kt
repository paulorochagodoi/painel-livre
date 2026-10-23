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
        val sky = if (dark) intArrayOf(0xFF070B14.toInt(), 0xFF111A2B.toInt(), 0xFF1B2638.toInt())
        else intArrayOf(0xFFD5DCE4.toInt(), 0xFFE7EAEE.toInt(), 0xFFF2EFEA.toInt())
        val floor = if (dark) intArrayOf(0xFF18202D.toInt(), 0xFF0C111A.toInt(), 0xFF06090F.toInt())
        else intArrayOf(0xFFE9E4DD.toInt(), 0xFFD9D2C8.toInt(), 0xFFC4BCB1.toInt())
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, sky, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, horizon, paint)
        paint.shader = LinearGradient(0f, horizon, 0f, h, floor, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, horizon, w, h, paint)
        // Foco de luz no chão, onde o carro fica.
        val glow = if (dark) 0x553C7BD9 else 0x66FFFFFF
        paint.shader = RadialGradient(w * 0.5f, horizon + h * 0.06f, w * 0.42f, intArrayOf(glow, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.32f, w * 0.5f, horizon + h * 0.06f)
        canvas.drawCircle(w * 0.5f, horizon + h * 0.06f, w * 0.42f, paint)
        canvas.restore()
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/**
 * Ilustração de um SUV de perfil (desenho vetorial próprio), usada no lugar do carro 3D do
 * SealNav. O motorista pode trocar por uma foto do próprio carro nas Configurações.
 */
class CarArtDrawable(private val dark: Boolean) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Espaço de desenho: 1000 × 420, chão em y = 384. SUV médio de perfil (frente à direita).
    private val body = Path().apply {
        moveTo(64f, 320f)
        cubicTo(54f, 300f, 50f, 256f, 54f, 220f)
        cubicTo(56f, 196f, 60f, 176f, 70f, 160f)
        cubicTo(84f, 130f, 110f, 112f, 150f, 104f)
        lineTo(540f, 86f)
        cubicTo(590f, 84f, 626f, 94f, 652f, 110f)
        lineTo(754f, 172f)
        cubicTo(822f, 178f, 882f, 186f, 922f, 198f)
        cubicTo(950f, 206f, 964f, 224f, 968f, 248f)
        lineTo(970f, 292f)
        cubicTo(970f, 310f, 960f, 318f, 942f, 320f)
        lineTo(844f, 320f)
        arcTo(RectF(696f, 250f, 844f, 398f), 0f, -180f)
        lineTo(314f, 320f)
        arcTo(RectF(166f, 250f, 314f, 398f), 0f, -180f)
        close()
    }
    private val glass = Path().apply {
        moveTo(162f, 124f)
        cubicTo(176f, 116f, 190f, 112f, 212f, 110f)
        lineTo(540f, 100f)
        cubicTo(580f, 100f, 608f, 108f, 632f, 124f)
        lineTo(716f, 174f)
        lineTo(170f, 180f)
        cubicTo(158f, 162f, 156f, 138f, 162f, 124f)
        close()
    }
    private val cladding = Path().apply {
        moveTo(314f, 294f); lineTo(696f, 294f); lineTo(696f, 320f); lineTo(314f, 320f); close()
        moveTo(56f, 280f); cubicTo(58f, 302f, 60f, 314f, 64f, 320f); lineTo(166f, 320f); lineTo(168f, 286f); close()
        moveTo(844f, 296f); lineTo(970f, 292f); cubicTo(970f, 310f, 960f, 318f, 942f, 320f); lineTo(844f, 320f); close()
    }
    private val headlight = Path().apply {
        moveTo(904f, 200f); lineTo(964f, 220f); lineTo(962f, 230f); lineTo(900f, 212f); close()
    }
    private val taillight = Path().apply {
        moveTo(54f, 178f); lineTo(104f, 160f); lineTo(106f, 172f); lineTo(56f, 192f); close()
    }
    private val mirror = Path().apply {
        moveTo(690f, 168f); cubicTo(702f, 156f, 724f, 156f, 730f, 166f); lineTo(726f, 184f); lineTo(694f, 184f); close()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = min(b.width() / 1000f, b.height() / 420f)
        canvas.save()
        canvas.translate(b.left + (b.width() - 1000f * s) / 2f, b.top + (b.height() - 420f * s) / 2f)
        canvas.scale(s, s)

        // Sombra no chão.
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(515f, 384f, 480f, intArrayOf(if (dark) 0xAA000000.toInt() else 0x66000000, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.save(); canvas.scale(1f, 0.06f, 515f, 384f); canvas.drawCircle(515f, 384f, 480f, p); canvas.restore()
        p.shader = null

        // Carroceria prata com luz de cima.
        val paint = if (dark) intArrayOf(0xFFA3ACB8.toInt(), 0xFF6F7884.toInt(), 0xFF3B414A.toInt())
        else intArrayOf(0xFFE2E6EB.toInt(), 0xFFAAB2BD.toInt(), 0xFF6C7480.toInt())
        p.shader = LinearGradient(0f, 86f, 0f, 320f, paint, floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(body, p)
        p.shader = null

        // Fundo das caixas de roda (só a metade de cima, dentro da carroceria).
        canvas.save()
        canvas.clipRect(0f, 0f, 1000f, 324f)
        p.color = 0xFF101317.toInt()
        canvas.drawCircle(240f, 324f, 76f, p)
        canvas.drawCircle(770f, 324f, 76f, p)
        canvas.restore()

        // Plásticos escuros: soleira, para-choques e moldura dos para-lamas.
        p.color = if (dark) 0xFF1E2228.toInt() else 0xFF33383F.toInt()
        canvas.drawPath(cladding, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 14f
        canvas.drawArc(RectF(162f, 246f, 318f, 402f), 180f, 180f, false, p)
        canvas.drawArc(RectF(692f, 246f, 848f, 402f), 180f, 180f, false, p)
        p.style = Paint.Style.FILL

        // Vidros escuros com reflexo.
        p.shader = LinearGradient(0f, 102f, 0f, 180f, intArrayOf(0xFF3A4452.toInt(), 0xFF12161C.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(glass, p)
        p.shader = LinearGradient(330f, 102f, 470f, 180f, intArrayOf(0x44FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawPath(glass, p)
        p.shader = null

        // Colunas na cor da carroceria (C e B) e rack de teto.
        p.color = paint[1]
        p.style = Paint.Style.STROKE
        p.strokeWidth = 16f
        canvas.drawLine(318f, 106f, 306f, 180f, p)
        p.strokeWidth = 12f
        canvas.drawLine(490f, 102f, 492f, 178f, p)
        p.color = if (dark) 0xFF2A2F36.toInt() else 0xFF3A3F46.toInt()
        p.strokeWidth = 7f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(176f, 92f, 530f, 76f, p)
        p.strokeWidth = 5f
        canvas.drawLine(196f, 92f, 198f, 104f, p)
        canvas.drawLine(510f, 77f, 512f, 90f, p)
        p.strokeCap = Paint.Cap.BUTT

        // Linha de cintura (reflexo) e cortes das portas.
        p.strokeWidth = 3f
        p.color = 0x66FFFFFF
        canvas.drawLine(80f, 214f, 940f, 222f, p)
        p.color = 0x55000000
        p.strokeWidth = 2.5f
        canvas.drawLine(306f, 184f, 310f, 292f, p)
        canvas.drawLine(492f, 182f, 496f, 292f, p)
        canvas.drawLine(690f, 178f, 694f, 290f, p)
        p.style = Paint.Style.FILL

        canvas.drawPath(mirror, p.apply { color = paint[2] })
        canvas.drawPath(headlight, p.apply { color = 0xFFEAF4FF.toInt() })
        canvas.drawPath(taillight, p.apply { color = 0xFFD8323A.toInt() })

        wheel(canvas, 240f, 324f)
        wheel(canvas, 770f, 324f)
        canvas.restore()
    }

    private fun wheel(c: Canvas, cx: Float, cy: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0xFF15181D.toInt()
        c.drawCircle(cx, cy, 60f, p)
        p.shader = RadialGradient(cx - 10f, cy - 12f, 46f, intArrayOf(0xFFDDE2E8.toInt(), 0xFF7E8692.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 40f, p)
        p.shader = null
        p.color = 0xFF2A2F37.toInt()
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            val path = Path().apply {
                moveTo(cx + 9f * cos(a + 0.9).toFloat(), cy + 9f * sin(a + 0.9).toFloat())
                lineTo(cx + 36f * cos(a + 0.24).toFloat(), cy + 36f * sin(a + 0.24).toFloat())
                lineTo(cx + 36f * cos(a + 1.0).toFloat(), cy + 36f * sin(a + 1.0).toFloat())
                close()
            }
            c.drawPath(path, p)
        }
        p.color = 0xFF555C66.toInt()
        c.drawCircle(cx, cy, 8f, p)
    }

    override fun getIntrinsicWidth() = 1000
    override fun getIntrinsicHeight() = 420
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
