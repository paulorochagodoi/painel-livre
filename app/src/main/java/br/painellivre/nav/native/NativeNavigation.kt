package br.painellivre.nav.native

import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import br.painellivre.cluster.ClusterService
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.nav.Places
import br.painellivre.util.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

/** Vive no serviço, não na Activity: sair da central não interrompe a orientação. */
object NativeNavigation {
    const val PACKAGE = "br.painellivre"
    private val _route = MutableStateFlow<NativeRoute?>(null)
    val route = _route.asStateFlow()
    private var service: LifecycleService? = null
    private var request: Job? = null
    private var tracker: RouteTracker? = null
    private var destination: RoutePoint? = null
    private val fixes = RouteFixPolicy()

    fun attach(owner: LifecycleService) {
        service = owner
        owner.lifecycleScope.launch {
            LocationRepository.location.collect { loc ->
                loc ?: return@collect
                val track = tracker ?: return@collect
                // Não encerra nem recalcula com um fix antigo ou impreciso.
                if (!usable(loc)) return@collect
                val now = android.os.SystemClock.elapsedRealtime()
                val progress = track.update(RoutePoint(loc.latitude, loc.longitude))
                val recalculate = fixes.shouldRecalculate(progress.offRoute, now, request?.isActive == true)
                if (progress.offRoute > 60) {
                    if (recalculate) {
                        destination?.let { calculate(it) }
                    }
                    // Não oferece uma manobra antiga enquanto está fora da rota.
                    NavRepository.update(PACKAGE, progress.nav.copy(maneuver = br.painellivre.nav.Maneuver.UNKNOWN,
                        instruction = "Fora da rota · recalculando", street = null, distanceText = null), null)
                } else {
                    publish(progress.nav)
                    if (progress.arrived) { stop(); notice(owner, "Você chegou ao destino") }
                }
            }
        }
    }

    fun detach(owner: LifecycleService) {
        if (service === owner) { stop(); service = null }
    }

    fun start(ctx: Context, query: String) {
        // O serviço recebe o destino no intent para não depender da ordem de onCreate.
        ClusterService.startNativeRoute(ctx, query)
    }

    fun calculate(query: String) {
        val owner = service ?: return
        request?.cancel()
        stopState()
        request = owner.lifecycleScope.launch {
            try {
                val direct = RouteDestination.point(query)
                val target = direct ?: run {
                    val results = Places.search(query, LocationRepository.location.value)
                    // Texto ambíguo volta à busca; nunca escolhe um endereço arbitrário.
                    if (results.size != 1) {
                        notice(owner, "Escolha o endereço na busca do Painel Livre")
                        return@launch
                    }
                    results.single().let { RoutePoint(it.lat, it.lon) }
                }
                destination = target
                calculateRoute(owner, target)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(owner, false) }
        }
    }

    private fun calculate(target: RoutePoint) {
        val owner = service ?: return
        request = owner.lifecycleScope.launch {
            try { calculateRoute(owner, target) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(owner, true) }
        }
    }

    private suspend fun calculateRoute(owner: LifecycleService, target: RoutePoint) {
        LocationRepository.start(owner)
        val loc = LocationRepository.location.value
        if (loc == null || !usable(loc)) {
            notice(owner, "Aguarde o GPS e escolha o destino novamente")
            return
        }
        fixes.requested(android.os.SystemClock.elapsedRealtime())
        notice(owner, "Calculando rota no mapa nativo…")
        val result = withContext(Dispatchers.IO) {
            val connection = URL(OsrmRoute.url(RoutePoint(loc.latitude, loc.longitude), target)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000; connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "PainelLivre/rota-nativa")
                require(connection.responseCode == 200)
                OsrmRoute.parse(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
        }
        currentCoroutineContext().ensureActive()
        tracker = RouteTracker(result)
        _route.value = result
        val fix = LocationRepository.location.value ?: loc
        publish(tracker!!.update(RoutePoint(fix.latitude, fix.longitude)).nav)
        ClusterService.start(owner, ClusterService.ACTION_ROUTE_REQUEST)
        AppLog.i("Rota nativa pronta; pedido enviado ao painel")
    }

    private fun publish(nav: br.painellivre.nav.ParsedNav) {
        val eta = nav.remainingSeconds?.let { seconds ->
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
                .format(java.util.Date(System.currentTimeMillis() + seconds * 1000L))
        }
        NavRepository.update(PACKAGE, nav.copy(etaClock = eta), null)
    }

    private fun failed(ctx: Context, recalculating: Boolean) {
        if (!recalculating) stopState()
        notice(ctx, "Não foi possível calcular a rota. Confira a internet ou use Maps/Waze")
        AppLog.w("Rota nativa: cálculo indisponível${if (recalculating) " durante recálculo" else ""}")
    }

    fun stop() { request?.cancel(); request = null; stopState() }
    private fun stopState() {
        tracker = null; destination = null; fixes.reset(); _route.value = null
        NavRepository.end(PACKAGE)
    }
    private fun usable(loc: Location): Boolean =
        RouteFixPolicy.usable(android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos,
            loc.accuracy.takeIf { loc.hasAccuracy() })
    private fun notice(ctx: Context, text: String) { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
}
