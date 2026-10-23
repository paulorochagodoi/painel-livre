package br.painellivre.car

import android.content.Context
import br.painellivre.adb.SelfAdb
import br.painellivre.debug.DebugLog
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Telemetria do carro (issue #16), **somente leitura**, pelo `autoservice` via ADB local.
 *
 * - Só lê enquanto alguma tela que mostra os dados está visível ([observe]) e a opção "Ler dados do
 *   carro" está ligada: uma varredura a cada `telemetry_interval` s, num único comando de shell.
 * - Antes da primeira leitura confere a interface do serviço ([Autoservice.Environment]); sem
 *   `android.gui.BYDAutoServer`, bloqueia (os números de transação seriam outros).
 * - Só transações 5/7 ([Autoservice.command]); escrita (6) não existe neste app.
 * - Endereços: o nome no catálogo da central (`BYDAutoFeatureIds`, por reflexão) vence o padrão.
 */
object VehicleTelemetry {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var job: Job? = null
    private var watchers = 0

    private val _state = MutableStateFlow(TelemetrySnapshot(TelemetrySnapshot.Status.OFF))
    val state: StateFlow<TelemetrySnapshot> = _state.asStateFlow()

    @Volatile private var environment: Autoservice.Environment? = null
    private val resolved = HashMap<String, Int?>()
    private var lastStatus: TelemetrySnapshot.Status? = null

    /** Estado atual enquanto quem coleta estiver ativo; liga e desliga o polling sozinho. */
    fun observe(ctx: Context): Flow<TelemetrySnapshot> = flow {
        acquire(ctx.applicationContext)
        try { emitAll(state) } finally { release() }
    }

    @Synchronized
    private fun acquire(app: Context) {
        watchers++
        if (job?.isActive != true) job = scope.launch { loop(app) }
    }

    @Synchronized
    private fun release() {
        watchers = (watchers - 1).coerceAtLeast(0)
        if (watchers == 0) { job?.cancel(); job = null }
    }

    private suspend fun loop(app: Context) {
        val prefs = Prefs(app)
        while (true) {
            if (!prefs.telemetryEnabled) {
                publish(TelemetrySnapshot(TelemetrySnapshot.Status.OFF))
                delay(2_000)
                continue
            }
            if (_state.value.status == TelemetrySnapshot.Status.OFF) _state.value = TelemetrySnapshot(TelemetrySnapshot.Status.WAITING)
            val snap = sweep(app, TelemetryCatalog.POLLED)
            publish(snap)
            // Serviço inexistente ou bloqueado não muda sozinho: tenta de novo bem devagar.
            val wait = when (snap.status) {
                TelemetrySnapshot.Status.NO_SERVICE, TelemetrySnapshot.Status.BLOCKED -> 120_000L
                TelemetrySnapshot.Status.NO_ADB -> 30_000L
                else -> prefs.telemetryIntervalSec * 1000L
            }
            delay(wait)
        }
    }

    private fun publish(s: TelemetrySnapshot) {
        _state.value = s
        // Só registra mudança de estado, para não inundar o log a cada varredura.
        if (s.status != lastStatus) {
            lastStatus = s.status
            if (s.status != TelemetrySnapshot.Status.OFF) AppLog.i("Telemetria: ${s.message}")
        }
    }

    /** Uma varredura dos [signals]. Nunca lança: falha vira um estado. */
    suspend fun sweep(ctx: Context, signals: List<TelemetryCatalog.Signal>): TelemetrySnapshot = mutex.withLock {
        val env = environment ?: when (val r = SelfAdb.shell(ctx, Autoservice.ENVIRONMENT_COMMAND)) {
            is SelfAdb.Result.Error -> return@withLock TelemetrySnapshot(TelemetrySnapshot.Status.NO_ADB, "Sem ADB local: ${r.message}")
            is SelfAdb.Result.Ok -> Autoservice.Environment.parse(r.output).also {
                AppLog.i("autoservice: ${it.serviceLine ?: "ausente"}; protocolo ${it.protocol}")
                if (it.descriptorOk) environment = it
            }
        }
        if (!env.hasService) return@withLock TelemetrySnapshot(TelemetrySnapshot.Status.NO_SERVICE)
        if (!env.descriptorOk) {
            return@withLock TelemetrySnapshot(TelemetrySnapshot.Status.BLOCKED, "${TelemetrySnapshot.Status.BLOCKED.label} (${env.serviceLine})")
        }
        val fids = signals.map { TelemetryCatalog.resolveFid(it, env.protocol, symbolValue(it.symbol)) }
        val commands = signals.mapIndexed { i, s -> s.command(fids[i]) }
        val out = when (val r = SelfAdb.shell(ctx, Autoservice.batch(commands))) {
            is SelfAdb.Result.Error -> return@withLock TelemetrySnapshot(TelemetrySnapshot.Status.NO_ADB, "Sem ADB local: ${r.message}")
            is SelfAdb.Result.Ok -> r.output
        }
        val parts = Autoservice.splitBatch(out, signals.size)
        val readings = signals.mapIndexed { i, s -> s.key to TelemetrySnapshot.reading(s, fids[i], Autoservice.parse(parts[i])) }.toMap()
        if (readings.values.all { it.reply is Autoservice.Reply.NoService }) {
            return@withLock TelemetrySnapshot(TelemetrySnapshot.Status.NO_SERVICE)
        }
        TelemetrySnapshot(TelemetrySnapshot.Status.OK, readings = readings, atMillis = System.currentTimeMillis(), protocol = env.protocol)
    }

    /**
     * Modo de debug: lê **todos** os sinais mapeados (inclusive os candidatos) uma vez e registra
     * endereço, resposta crua e valor, para conferir no carro. Funciona com a opção desligada.
     */
    suspend fun debugReadAll(ctx: Context) {
        DebugLog.add("carro", "lendo ${TelemetryCatalog.SIGNALS.size} sinais (só leitura, transações 5/7)…")
        val s = sweep(ctx.applicationContext, TelemetryCatalog.SIGNALS)
        DebugLog.add("carro", "${s.message}${if (s.readings.isNotEmpty()) " · protocolo ${s.protocol}" else ""}")
        environment?.serviceLine?.let { DebugLog.add("carro", "serviço: $it") }
        for (g in TelemetryCatalog.Group.values()) {
            val rs = s.readings.values.filter { it.signal.group == g }
            if (rs.isEmpty()) continue
            DebugLog.add("carro", "── ${g.label} ──")
            rs.forEach { DebugLog.add("carro", it.debugLine()) }
        }
        if (s.readings.isNotEmpty()) {
            val n = s.readings.values.count { it.value != null }
            DebugLog.add("carro", "$n de ${s.readings.size} com valor. Compare com o que o carro mostra e anote.")
        }
    }

    /** `BYDAutoFeatureIds$Grupo.NOME` da própria central (só leitura de constante), ou null. */
    @Synchronized
    private fun symbolValue(symbol: String?): Int? {
        if (symbol == null || symbol.endsWith("_SET")) return null
        return resolved.getOrPut(symbol) {
            val group = symbol.substringBefore('.')
            val name = symbol.substringAfter('.')
            runCatching {
                Class.forName("android.hardware.bydauto.BYDAutoFeatureIds\$$group").getField(name).getInt(null)
            }.getOrNull()?.also { AppLog.i("Telemetria: $symbol = ${Autoservice.hex(it)} (catálogo da central)") }
        }
    }
}
