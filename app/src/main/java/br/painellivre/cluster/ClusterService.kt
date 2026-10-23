package br.painellivre.cluster

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import br.painellivre.App
import br.painellivre.R
import br.painellivre.adb.SelfAdb
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.nav.NavSimulator
import br.painellivre.ui.LauncherActivity
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Situação da projeção no painel, para as telas mostrarem. */
sealed class ClusterStatus(val text: String) {
    object Idle : ClusterStatus("Painel original da BYD")
    object Working : ClusterStatus("Ativando o painel…")
    data class Active(val displayId: Int) : ClusterStatus("Mapa no painel (display $displayId)")
    data class Failed(val reason: String) : ClusterStatus("Falhou: $reason")
    object Yielded : ClusterStatus("Painel cedido a outro app — toque em Mostrar no painel para retomar")
}

/**
 * Serviço em primeiro plano: mantém o GPS, liga o painel quando a navegação começa (modo
 * padrão) e devolve o painel original da BYD alguns segundos depois que a rota termina.
 *
 * Convivência (docs/SEALNAV_RE.md, achado E): se outro app cobre a tela do painel, retoma uma vez;
 * se acontecer de novo em 30 s, cede o painel até o motorista pedir ([YieldPolicy]).
 */
class ClusterService : LifecycleService() {

    private lateinit var prefs: Prefs
    private lateinit var controller: ClusterController
    private val op = Mutex()
    private var restoreJob: Job? = null
    private var lastAutoAttempt = 0L
    private val yieldPolicy = YieldPolicy()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        controller = ClusterController(this)
        startForeground(NOTIF_ID, buildNotification())
        LocationRepository.start(this)
        AppLog.i("Serviço iniciado (modo do painel: ${prefs.clusterMode})")

        // ADB local: no boot o adbd demora a subir — tenta por até 30 s (docs/SEALNAV_RE.md, achado A).
        val adbReady = lifecycleScope.launch {
            val r = SelfAdb.ensureReady(this@ClusterService, attempts = 15, intervalMs = 2_000)
            AppLog.i("ADB local: ${if (r is SelfAdb.Result.Ok) "pronto" else (r as SelfAdb.Result.Error).message}")
        }

        lifecycleScope.launch {
            NavRepository.state.distinctUntilChangedBy { it.active }.collect { s ->
                if (prefs.clusterMode != "nav") return@collect
                if (s.active) {
                    restoreJob?.cancel()
                    yieldPolicy.reset() // rota nova: o motorista quer o mapa
                    adbReady.join()
                    if (_status.value !is ClusterStatus.Active) autoShow()
                } else if (_status.value is ClusterStatus.Active) {
                    restoreJob?.cancel()
                    restoreJob = launch {
                        delay(prefs.restoreDelaySec * 1000L)
                        AppLog.i("Rota terminou há ${prefs.restoreDelaySec}s — devolvendo o painel original")
                        restore()
                    }
                }
            }
        }
        if (prefs.clusterMode == "always") lifecycleScope.launch { adbReady.join(); autoShow() }

        lifecycleScope.launch {
            coverEvents.collect {
                if (_status.value !is ClusterStatus.Active) return@collect
                if (yieldPolicy.onTakeover(System.currentTimeMillis())) {
                    AppLog.w("Outro app cobriu o painel — retomando em 3 s")
                    delay(3_000)
                    if (_status.value is ClusterStatus.Active) {
                        launchOnly()
                        AppLog.i("Painel retomado: ${_status.value.text}")
                    }
                } else {
                    AppLog.w("Outro app tomou o painel de novo em menos de 30 s — cedendo o painel")
                    _status.value = ClusterStatus.Yielded
                    updateNotification()
                }
            }
        }

        // Vigia: se a tela do painel morrer (display recriado, firmware), tenta só reabrir a tela —
        // sem reenviar comandos ao AutoContainer, para nunca ficar em laço com a firmware.
        lifecycleScope.launch {
            while (true) {
                delay(15_000)
                val st = _status.value
                if (st is ClusterStatus.Active && ClusterActivity.runningOnDisplay == null) {
                    AppLog.w("Tela do painel sumiu — tentando reabrir")
                    launchOnly()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW -> lifecycleScope.launch { yieldPolicy.reset(); show() }
            ACTION_RESTORE -> lifecycleScope.launch { restore() }
            ACTION_LAUNCH_ONLY -> lifecycleScope.launch { yieldPolicy.reset(); launchOnly() }
            ACTION_RELAUNCH -> lifecycleScope.launch { relaunch() }
            ACTION_SIMULATE -> NavSimulator.toggle(lifecycleScope)
        }
        return START_STICKY
    }

    private suspend fun autoShow() {
        val now = System.currentTimeMillis()
        if (now - lastAutoAttempt < 60_000) return // não insistir em loop se a firmware recusar
        lastAutoAttempt = now
        show()
    }

    private suspend fun show() = op.withLock {
        _status.value = ClusterStatus.Working
        val target = controller.activate()
        if (target == null) {
            _status.value = ClusterStatus.Failed("display do painel não apareceu (veja Diagnóstico)")
            return@withLock
        }
        val ok = controller.launchClusterUi(target)
        _status.value = if (ok) ClusterStatus.Active(target.displayId)
        else ClusterStatus.Failed("não consegui abrir a tela no display ${target.displayId}")
        updateNotification()
    }

    private suspend fun launchOnly() = op.withLock {
        val t = controller.launchOnly()
        _status.value = if (t != null) ClusterStatus.Active(t.displayId)
        else ClusterStatus.Failed("nenhum display de painel aberto (ative o painel primeiro)")
        updateNotification()
    }

    /** Pedido do vigia da tela: fecha e reabre a tela do painel, sem mexer no AutoContainer. */
    private suspend fun relaunch() = op.withLock {
        if (_status.value !is ClusterStatus.Active) return@withLock
        val t = controller.findClusterDisplay() ?: controller.findClusterDisplayViaShell() ?: return@withLock
        val ok = controller.relaunch(t)
        AppLog.i("Tela do painel reaberta: ${if (ok) "ok" else "falhou"}")
    }

    private suspend fun restore() = op.withLock {
        restoreJob?.cancel()
        _status.value = ClusterStatus.Working
        controller.restore()
        _status.value = ClusterStatus.Idle
        updateNotification()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, LauncherActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val showPi = PendingIntent.getService(this, 1, intent(this, ACTION_SHOW), PendingIntent.FLAG_IMMUTABLE)
        val restorePi = PendingIntent.getService(this, 2, intent(this, ACTION_RESTORE), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_painel)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(_status.value.text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, getString(R.string.cluster_show), showPi)
            .addAction(0, getString(R.string.cluster_restore), restorePi)
            .build()
    }

    private fun updateNotification() {
        getSystemService(android.app.NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        LocationRepository.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        const val ACTION_SHOW = "br.painellivre.SHOW"
        const val ACTION_RESTORE = "br.painellivre.RESTORE"
        const val ACTION_SIMULATE = "br.painellivre.SIMULATE"
        const val ACTION_LAUNCH_ONLY = "br.painellivre.LAUNCH_ONLY"
        const val ACTION_RELAUNCH = "br.painellivre.RELAUNCH"

        private val coverEvents = MutableSharedFlow<Long>(extraBufferCapacity = 4)

        /** A tela do painel avisa que outro app a cobriu. */
        fun reportCovered() {
            coverEvents.tryEmit(System.currentTimeMillis())
        }

        private val _status = MutableStateFlow<ClusterStatus>(ClusterStatus.Idle)
        val status: StateFlow<ClusterStatus> = _status.asStateFlow()

        fun intent(ctx: Context, action: String? = null) =
            Intent(ctx, ClusterService::class.java).apply { this.action = action }

        fun start(ctx: Context, action: String? = null) {
            val i = intent(ctx, action)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
