package br.painellivre.cluster

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import br.painellivre.adb.SelfAdb
import br.painellivre.car.InstrumentSdk
import br.painellivre.debug.DebugLog
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Acompanha o menu de navegação do painel (issue #9) enquanto a tela do painel está aberta e
 * diz como desenhar ([PanelMenu.Layout]): OFF = mapa e cartões escondidos (tela preta), Simples =
 * só os cartões, Pequeno = no centro, Cheio = como sempre. Só lê; nunca manda comando ao AutoContainer — mudar de
 * opção só muda o que a nossa tela desenha (os vigias continuam valendo).
 */
object PanelMenuWatcher {

    /** Teste do modo de debug: força um modo sem depender do carro. Não fica salvo. */
    val override = MutableStateFlow<PanelMenu.Mode?>(null)

    private val _last = MutableStateFlow<InstrumentSdk.Reading?>(null)
    /** Última leitura (a tela de debug mostra). */
    val last: StateFlow<InstrumentSdk.Reading?> = _last.asStateFlow()

    private val readLock = Mutex()
    @Volatile private var reader: PanelMenuReader? = null

    /**
     * Uma leitura do menu: SDK primeiro e, se recusar a permissão, ADB local (ver [PanelMenuReader]).
     * Compartilhada com o modo de debug; o cadeado evita duas leituras ao mesmo tempo.
     */
    suspend fun read(ctx: Context): InstrumentSdk.Reading = readLock.withLock {
        val app = ctx.applicationContext
        val r = reader ?: PanelMenuReader(
            sdk = { InstrumentSdk.naviType(app) },
            adb = { cmd -> SelfAdb.shell(app, cmd) },
            adbAllowed = Build.VERSION.SDK_INT == PanelMenu.ADB_SDK_INT,
        ).also { reader = it }
        r.read()
    }

    /** Texto de uma leitura para o log: valor bruto (decimal e hex), modo e origem. */
    fun describe(r: InstrumentSdk.Reading): String {
        val raw = r.raw?.let { "bruto=$it (${PanelMenu.hex(it)})" } ?: "sem valor"
        return "${r.source}: $raw → ${r.mode.label}${r.error?.let { " — $it" } ?: ""}"
    }

    fun layouts(ctx: Context): Flow<PanelMenu.Layout> = flow {
        val prefs = Prefs(ctx)
        val follower = PanelMenu.Follower()
        var shown: PanelMenu.Layout? = null
        var lastReading: String? = null
        while (true) {
            val follow = prefs.panelMenuFollow
            val forced = override.value
            if (follow) {
                val r = read(ctx)
                _last.value = r
                // Leituras boas sempre entram no AppLog; falhas só quando o motivo muda.
                val text = describe(r)
                if (r.error == null || text != lastReading) {
                    lastReading = text
                    AppLog.i("Menu do painel: $text")
                    DebugLog.add("menu", "$text ${r.detail}".trim())
                }
                follower.update(r.mode)
            }
            val mode = PanelMenu.effective(follow, forced, follower.stable, prefs.panelMenuManual)
            val layout = PanelMenu.layoutFor(mode)
            if (layout != shown) {
                AppLog.i("Painel: desenho para o modo ${mode.label}${if (forced != null) " (teste do debug)" else ""}")
                DebugLog.add("menu", "desenho aplicado: ${mode.label} $layout")
                shown = layout
                emit(layout)
            }
            // Leve: só roda com a tela do painel aberta; 1 s seguindo o menu, 5 s só olhando a opção.
            delay(if (follow || forced != null) 1_000 else 5_000)
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Moldura da tela do painel: põe o conteúdo (mapa + cartões) na área do [PanelMenu.Layout] e cobre
 * com preto o que não deve aparecer. O conteúdo muda de tamanho, então a própria tela recalcula
 * posições e câmera como numa mudança de display.
 */
class PanelMenuFrame(ctx: Context, private val content: FrameLayout) {

    val frame = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }
    /** Fica entre o mapa (índice 0) e os cartões: "Simples" mostra só os cartões. */
    private val mapCover = View(ctx).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }
    /** Por cima de tudo: OFF. */
    private val allCover = View(ctx).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }
    private var layout = PanelMenu.NORMAL

    init {
        content.addView(mapCover, 1, ViewGroup.LayoutParams(-1, -1))
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        frame.addView(allCover, FrameLayout.LayoutParams(-1, -1))
        frame.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) frame.post { place() }
        }
    }

    fun apply(l: PanelMenu.Layout) {
        layout = l
        mapCover.visibility = if (l.showMap) View.GONE else View.VISIBLE
        allCover.visibility = if (l.showCards || l.showMap) View.GONE else View.VISIBLE
        place()
    }

    private fun place() {
        val w = frame.width
        val h = frame.height
        val bounds = PanelMenu.bounds(w, h, layout) ?: return
        val lp = content.layoutParams as FrameLayout.LayoutParams
        val want = intArrayOf(bounds.left, bounds.top, w - bounds.right, h - bounds.bottom)
        if (lp.leftMargin == want[0] && lp.topMargin == want[1] && lp.rightMargin == want[2] && lp.bottomMargin == want[3]) return
        AppLog.i("Painel: moldura aplicada ${w}x$h, área $bounds, mapa=${layout.showMap}, cartões=${layout.showCards}")
        lp.setMargins(want[0], want[1], want[2], want[3])
        content.layoutParams = lp
    }
}
