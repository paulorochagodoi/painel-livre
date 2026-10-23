package br.painellivre.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import br.painellivre.adb.SelfAdb
import br.painellivre.car.InstrumentSdk
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coleta do modo de debug (issue #9). Ligado pelo motorista em Ajustes; desligado, não roda nada.
 * A cada [INTERVAL_MS]:
 *  - getters candidatos do SDK do painel e `INSTRUMENT_NAVI_TYPE` (uid do app, sem ADB);
 *  - pelo ADB local (uid 2000), um comando só de leitura com settings, getprop, histórico de
 *    broadcasts e logcat filtrado ([ProbeParse.COLLECT_COMMAND]) — o [ProbeTracker] mostra o que mudou;
 *  - broadcasts conhecidos (e os que o motorista digitar) com os extras.
 * A coleta pelo ADB para sozinha depois de [ADB_LIMIT_MS] para não pesar na central esquecida ligada.
 */
object DebugProbe {

    private const val INTERVAL_MS = 3_000L
    private const val ADB_RETRY_MS = 30_000L
    private const val ADB_LIMIT_MS = 30 * 60_000L

    /** Ações já documentadas (docs/ENGENHARIA_REVERSA.md §2 e issue #9). Só escuta, nunca envia. */
    val KNOWN_ACTIONS = listOf(
        "AUTONAVI_STANDARD_BROADCAST_SEND",
        "AUTONAVI_STANDARD_BROADCAST_RECV",
        "byd.intent.action.NAVIGATION_STATE_CHANGED",
        "byd.intent.action.MAP_ACTIVITY_STATE_CHANGED",
        "com.telenav.action.nav.status",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var registered: Set<String> = emptySet()

    private val _adbRunning = MutableStateFlow(false)
    /** Coleta pelo ADB em andamento (a tela mostra Pausar/Retomar). */
    val adbRunning: StateFlow<Boolean> = _adbRunning.asStateFlow()

    /** Liga/desliga conforme a preferência (App.onCreate e a chave em Ajustes). */
    @Synchronized
    fun sync(ctx: Context, on: Boolean = Prefs(ctx).debugMode) {
        val app = ctx.applicationContext
        if (on == DebugLog.enabled && (!on || job?.isActive == true)) return
        if (on) {
            DebugLog.buffer.enabled = true
            DebugLog.add("debug", "modo de debug ligado")
            AppLog.i("Modo de debug ligado")
            listen(app)
            restart(app)
        } else {
            DebugLog.add("debug", "modo de debug desligado")
            AppLog.i("Modo de debug desligado")
            job?.cancel(); job = null
            _adbRunning.value = false
            receiver?.let { runCatching { app.unregisterReceiver(it) } }
            receiver = null; registered = emptySet()
            DebugLog.buffer.enabled = false
        }
    }

    /** (Re)começa a coleta, inclusive a do ADB depois do limite de tempo. */
    @Synchronized
    fun restart(ctx: Context) {
        if (!DebugLog.enabled) return
        val app = ctx.applicationContext
        job?.cancel()
        job = scope.launch { loop(app) }
    }

    @Synchronized
    fun pause() {
        job?.cancel(); job = null
        _adbRunning.value = false
        DebugLog.add("debug", "coleta pausada")
    }

    /** Escuta também esta ação (fica salva). */
    fun addAction(ctx: Context, action: String) {
        val a = action.trim().takeIf { it.isNotEmpty() && !it.contains(' ') } ?: return
        val p = Prefs(ctx)
        p.debugActions = p.debugActions + a
        listen(ctx.applicationContext)
    }

    @Synchronized
    private fun listen(app: Context) {
        val actions = (KNOWN_ACTIONS + Prefs(app).debugActions).toSet()
        if (actions == registered && receiver != null) return
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                DebugLog.add("broadcast", "★ recebido ${i.action} pacote=${i.`package` ?: "-"} extras=${extras(i.extras)}")
            }
        }
        app.registerReceiver(r, IntentFilter().apply { actions.forEach(::addAction) })
        receiver = r; registered = actions
        DebugLog.add("debug", "escutando: ${actions.joinToString(", ")}")
    }

    private fun extras(b: Bundle?): String {
        if (b == null || b.isEmpty) return "{}"
        @Suppress("DEPRECATION")
        return b.keySet().sorted().joinToString(", ", "{", "}") { k -> "$k=${runCatching { b.get(k) }.getOrNull()}".take(200) }
    }

    private suspend fun loop(app: Context) {
        val tracker = ProbeTracker()
        val startedAt = System.currentTimeMillis()
        var sdkLast = emptyMap<String, String>()
        var nextAdbAt = 0L
        var lastAdbError: String? = null
        _adbRunning.value = true
        DebugLog.add("debug", "coleta começou (a cada ${INTERVAL_MS / 1000} s)")
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()

            // SDK do painel: sem ADB, sob o uid do app.
            val nav = InstrumentSdk.naviType(app)
            val sdk = InstrumentSdk.debugGetters(app) +
                ("INSTRUMENT_NAVI_TYPE" to (nav.error?.let { "erro: $it" } ?: "${nav.raw} (${nav.mode.label}) ${nav.detail}"))
            for ((k, v) in sdk) if (sdkLast[k] != v) DebugLog.add("sdk", "$k = $v${sdkLast[k]?.let { " (era $it)" } ?: ""}")
            sdkLast = sdk

            if (_adbRunning.value && now >= nextAdbAt) {
                if (now - startedAt > ADB_LIMIT_MS) {
                    _adbRunning.value = false
                    DebugLog.add("debug", "coleta pelo ADB parada depois de 30 min; toque em Retomar para continuar")
                } else when (val r = SelfAdb.shell(app, ProbeParse.COLLECT_COMMAND)) {
                    is SelfAdb.Result.Ok -> {
                        lastAdbError = null
                        tracker.update(ProbeParse.sections(r.output)).forEach { DebugLog.add(it.source, it.text) }
                        nextAdbAt = now + INTERVAL_MS
                    }
                    is SelfAdb.Result.Error -> {
                        if (r.message != lastAdbError) DebugLog.add("adb", "sem ADB: ${r.message} (tento de novo a cada 30 s; o SDK continua)")
                        lastAdbError = r.message
                        nextAdbAt = now + ADB_RETRY_MS
                    }
                }
            }
            delay(INTERVAL_MS)
        }
    }

    /** Fotografia completa, uma vez: pacotes, serviços, receptores, displays e telefone. */
    suspend fun snapshot(ctx: Context): String {
        val sdk = InstrumentSdk.debugGetters(ctx).entries.joinToString("\n") { "${it.key} = ${it.value}" }
        val nav = InstrumentSdk.naviType(ctx)
        val shell = when (val r = SelfAdb.shell(ctx, SNAPSHOT_COMMAND)) {
            is SelfAdb.Result.Ok -> r.output
            is SelfAdb.Result.Error -> "ERRO no ADB: ${r.message}"
        }
        return buildString {
            append("# SDK do painel (uid do app)\n").append(sdk).append('\n')
            append("INSTRUMENT_NAVI_TYPE = ").append(nav.error ?: "${nav.raw} (${nav.mode.label}) ${nav.detail}").append('\n')
            // Telefone e Bluetooth podem trazer números e endereços: escondidos antes de mostrar.
            append(ProbeParse.redactPersonal(shell))
        }
    }

    private val SNAPSHOT_COMMAND = listOf(
        "echo '# pacotes'", "pm list packages 2>&1 | grep -i -E 'amap|autonavi|example|navi|instrument|cluster|byd|bluetooth|phone|dialer|telecom|contacts'",
        "echo '# serviços'", "service list 2>&1 | grep -i -E 'auto|instrument|navi|cluster|bluetooth|phone|telecom|bt'",
        "echo '# receptores de navegação'", "dumpsys activity broadcasts 2>&1 | grep -i -E 'amap|autonavi|navi|instrument|cluster' | head -n 150",
        "echo '# propriedades candidatas'", "getprop 2>&1 | grep -i -E 'navi|instrument|cluster|amap|screen|fission|xdja|dilink'",
        "echo '# settings candidatos'", "(settings list system; settings list global; settings list secure) 2>&1 | grep -i -E 'navi|instrument|cluster|amap|screen|meter|hud'",
        "echo '# telecom'", "dumpsys telecom 2>&1 | grep -i -E 'PhoneAccount|ComponentInfo|hfp|bluetooth|dialer' | head -n 60",
        "echo '# telas retomadas por display'", "dumpsys activity activities 2>&1 | grep -E 'Display #|ResumedActivity' | head -n 40",
    ).joinToString("; ")
}
