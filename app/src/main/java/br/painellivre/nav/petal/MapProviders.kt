package br.painellivre.nav.petal

import android.content.Context
import android.location.Location
import android.os.SystemClock
import br.painellivre.BuildConfig
import br.painellivre.debug.DebugLog
import br.painellivre.nav.Place
import br.painellivre.nav.Places
import br.painellivre.nav.native.NativeRoute
import br.painellivre.nav.native.OsrmRoute
import br.painellivre.nav.native.RoutePoint
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Escolhe o provedor de busca e de rota. Padrão: Photon e OSRM. Com "Usar Petal" ligado em Ajustes e
 * uma chave disponível, tenta o Petal primeiro e cai no Photon/OSRM se ele falhar (prova de conceito).
 *
 * A chave nunca vai para o repositório. Ordem: arquivo `petal_api_key.txt` na pasta do app
 * (`/sdcard/Android/data/br.painellivre/files/`, dá para mandar com `adb push`) e depois
 * `BuildConfig.PETAL_API_KEY`, que vem de `petal.apiKey` no `local.properties` (fora do git).
 */
object MapProviders {
    const val KEY_FILE = "petal_api_key.txt"
    private var appContext: Context? = null

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    fun apiKey(ctx: Context? = appContext): String? {
        val fromFile = ctx?.getExternalFilesDir(null)?.let { File(it, KEY_FILE) }
            ?.takeIf { it.isFile }?.let { runCatching { it.readText().trim() }.getOrNull() }
        return fromFile?.takeIf { it.isNotEmpty() } ?: BuildConfig.PETAL_API_KEY.trim().takeIf { it.isNotEmpty() }
    }

    /** De onde veio a chave, sem mostrar o valor. */
    fun keySource(ctx: Context): String {
        val file = ctx.getExternalFilesDir(null)?.let { File(it, KEY_FILE) }
        return when {
            file?.isFile == true && file.readText().isNotBlank() -> "arquivo ${file.absolutePath}"
            BuildConfig.PETAL_API_KEY.isNotBlank() -> "local.properties (BuildConfig)"
            else -> "nenhuma chave encontrada"
        }
    }

    private fun petalActive(ctx: Context?): String? {
        ctx ?: return null
        if (!Prefs(ctx).petalEnabled) return null
        return apiKey(ctx)
    }

    fun tilesKey(ctx: Context): String? = if (Prefs(ctx).petalTiles) petalActive(ctx) else null

    // ── Busca ───────────────────────────────────────────────────────────────

    suspend fun search(query: String, near: Location?): List<Place> {
        val key = petalActive(appContext) ?: return Places.searchPhoton(query, near)
        val petal = runCatching { searchPetal(query, near, key) }
        petal.onSuccess { if (it.isNotEmpty()) return it }
        petal.exceptionOrNull()?.let { log("Busca no Petal falhou (${it.message}); usando Photon") }
            ?: log("Petal sem resultado; usando Photon")
        return Places.searchPhoton(query, near)
    }

    suspend fun searchPetal(query: String, near: Location?, key: String): List<Place> = withContext(Dispatchers.IO) {
        val center = near?.let { RoutePoint(it.latitude, it.longitude) }
        PetalApi.parseSearch(post(PetalApi.searchUrl(key), PetalApi.searchBody(query, center)))
    }

    // ── Rota ────────────────────────────────────────────────────────────────

    data class RouteResult(val route: NativeRoute, val provider: String, val trafficSec: Double?)

    suspend fun route(from: RoutePoint, to: RoutePoint): RouteResult {
        val key = petalActive(appContext)
        if (key != null) {
            runCatching { routePetal(from, to, key) }
                .onSuccess { return RouteResult(it.route, "Petal", it.trafficSec) }
                .onFailure { log("Rota no Petal falhou (${it.message}); usando OSRM") }
        }
        return RouteResult(routeOsrm(from, to), "OSRM", null)
    }

    suspend fun routePetal(from: RoutePoint, to: RoutePoint, key: String): PetalApi.Route = withContext(Dispatchers.IO) {
        PetalApi.parseDriving(post(PetalApi.drivingUrl(key), PetalApi.drivingBody(from, to)))
    }

    suspend fun routeOsrm(from: RoutePoint, to: RoutePoint): NativeRoute = withContext(Dispatchers.IO) {
        val connection = URL(OsrmRoute.url(from, to)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "PainelLivre/rota-nativa")
            require(connection.responseCode == 200)
            OsrmRoute.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    // ── Comparação lado a lado ──────────────────────────────────────────────

    /** Mesma busca e mesma rota nos dois provedores. Não usa a reserva automática: mostra cada um. */
    suspend fun compare(ctx: Context, query: String, near: Location?): String {
        val key = apiKey(ctx)
        val photon = timed { Places.searchPhotonOrThrow(query, near) }
        val petal = if (key == null) null else timed { searchPetal(query, near, key) }
        val searches = listOf(
            ProviderComparison.Search("Photon", photon.ms, photon.value.orEmpty(), photon.error),
            ProviderComparison.Search("Petal", petal?.ms ?: 0, petal?.value.orEmpty(),
                if (key == null) "sem chave (${keySource(ctx)})" else petal?.error),
        )
        val origin = near?.let { RoutePoint(it.latitude, it.longitude) }
        val routes = mutableListOf<ProviderComparison.Route>()
        if (origin == null) {
            routes += ProviderComparison.Route("Rotas", 0, null, null, null, "sem GPS: rota não calculada")
        } else {
            photon.value?.firstOrNull()?.let { p ->
                val r = timed { routeOsrm(origin, RoutePoint(p.lat, p.lon)) }
                val v = r.value
                routes += ProviderComparison.Route("OSRM → ${p.name}", r.ms,
                    v?.steps?.sumOf { it.meters }, v?.steps?.sumOf { it.seconds }, null, r.error)
            }
            if (key != null) petal?.value?.firstOrNull()?.let { p ->
                val r = timed { routePetal(origin, RoutePoint(p.lat, p.lon), key) }
                val v = r.value
                routes += ProviderComparison.Route("Petal → ${p.name}", r.ms,
                    v?.distanceMeters, v?.durationSec, v?.trafficSec, r.error)
            }
        }
        val text = ProviderComparison.format(query, searches, routes)
        DebugLog.add("mapas", text)
        AppLog.i("Comparação de mapas: ${text.lineSequence().joinToString(" | ")}")
        return text
    }

    private class Timed<T>(val value: T?, val ms: Long, val error: String?)

    private suspend fun <T> timed(block: suspend () -> T): Timed<T> {
        val start = SystemClock.elapsedRealtime()
        return try {
            val v = block(); Timed(v, SystemClock.elapsedRealtime() - start, null)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timed(null, SystemClock.elapsedRealtime() - start, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun post(url: String, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 8_000; c.readTimeout = 12_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            // Nunca registra a URL: ela leva a chave.
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${text.take(160)}")
            return text
        } finally { c.disconnect() }
    }

    private fun log(msg: String) { AppLog.w(msg); DebugLog.add("mapas", msg) }
}
