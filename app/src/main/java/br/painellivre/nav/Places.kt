package br.painellivre.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.preference.PreferenceManager
import br.painellivre.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Um lugar: resultado de busca, casa, trabalho ou recente. */
data class Place(val name: String, val address: String, val lat: Double, val lon: Double) {
    fun toJson(): JSONObject = JSONObject().put("n", name).put("a", address).put("lat", lat).put("lon", lon)

    fun distanceKm(from: Location?): Double? {
        from ?: return null
        val r = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, lat, lon, r)
        return r[0] / 1000.0
    }

    companion object {
        fun fromJson(o: JSONObject) = Place(o.optString("n"), o.optString("a"), o.getDouble("lat"), o.getDouble("lon"))
    }
}

/**
 * Busca de endereços (Photon, sobre o OpenStreetMap — aberto, sem chave) e lugares guardados.
 * O destino pode ir ao mapa nativo ou aos apps externos.
 */
object Places {
    private const val KEY_HOME = "place_home"
    private const val KEY_WORK = "place_work"
    private const val KEY_RECENTS = "place_recents"

    suspend fun search(query: String, near: Location?): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val bias = near?.let { String.format(Locale.ROOT, "&lat=%.2f&lon=%.2f", it.latitude, it.longitude) } ?: ""
        val url = "https://photon.komoot.io/api/?q=$q&limit=8$bias"
        try {
            val body = (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("User-Agent", "PainelLivre (app aberto para BYD)")
                inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
            }
            parsePhoton(body)
        } catch (t: Throwable) {
            AppLog.w("Busca de endereço falhou: ${t.javaClass.simpleName}")
            emptyList()
        }
    }

    fun parsePhoton(body: String): List<Place> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            val p = f.getJSONObject("properties")
            val street = listOfNotNull(p.optString("street").ifEmpty { null }, p.optString("housenumber").ifEmpty { null })
                .joinToString(", ")
            val name = p.optString("name").ifEmpty { street.ifEmpty { null } } ?: return@mapNotNull null
            val area = listOfNotNull(
                street.takeIf { it.isNotEmpty() && it != name },
                p.optString("district").ifEmpty { null },
                p.optString("city").ifEmpty { null },
                p.optString("state").ifEmpty { null },
            ).distinct().joinToString(" · ")
            Place(name, area, c.getDouble(1), c.getDouble(0))
        }.distinctBy { "${it.name}|${it.address}" }
    }

    // ── Guardados ───────────────────────────────────────────────────────────

    private fun sp(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx.applicationContext)

    fun home(ctx: Context) = read(ctx, KEY_HOME)
    fun work(ctx: Context) = read(ctx, KEY_WORK)
    fun setHome(ctx: Context, p: Place?) = write(ctx, KEY_HOME, p)
    fun setWork(ctx: Context, p: Place?) = write(ctx, KEY_WORK, p)

    fun recents(ctx: Context): List<Place> = try {
        val a = JSONArray(sp(ctx).getString(KEY_RECENTS, "[]"))
        (0 until a.length()).map { Place.fromJson(a.getJSONObject(it)) }
    } catch (t: Throwable) {
        emptyList()
    }

    fun addRecent(ctx: Context, p: Place) {
        val list = (listOf(p) + recents(ctx).filterNot { it.name == p.name && it.address == p.address }).take(8)
        sp(ctx).edit().putString(KEY_RECENTS, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    private fun read(ctx: Context, key: String): Place? =
        sp(ctx).getString(key, null)?.let { runCatching { Place.fromJson(JSONObject(it)) }.getOrNull() }

    private fun write(ctx: Context, key: String, p: Place?) =
        sp(ctx).edit().putString(key, p?.toJson()?.toString()).apply()

    // ── Abrir a rota no app escolhido ───────────────────────────────────────

    /** Abre a navegação até [p] no Google Maps ou no Waze ([app] = "maps" ou "waze"). */
    fun navigate(ctx: Context, p: Place, app: String): Boolean {
        addRecent(ctx, p)
        if (app == "native") {
            br.painellivre.nav.native.NativeNavigation.start(ctx, "${p.lat},${p.lon}")
            return true
        }
        val i = if (app == "waze") {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=${p.lat},${p.lon}&navigate=yes")).setPackage(NavRepository.PKG_WAZE)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${p.lat},${p.lon}")).setPackage(NavRepository.PKG_MAPS)
        }
        return startRoute(ctx, i)
    }

    /** Busca por texto direto no app (quando não há resultado ou sem internet para a busca). */
    fun navigateText(ctx: Context, query: String, app: String): Boolean {
        if (app == "native") {
            br.painellivre.nav.native.NativeNavigation.start(ctx, query)
            return true
        }
        val q = Uri.encode(query)
        val i = if (app == "waze") {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage(NavRepository.PKG_WAZE)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(NavRepository.PKG_MAPS)
        }
        return startRoute(ctx, i)
    }

    fun openApp(ctx: Context, app: String): Boolean {
        if (app == "native") return start(ctx, Intent(ctx, br.painellivre.ui.LauncherActivity::class.java))
        val pkg = if (app == "waze") NavRepository.PKG_WAZE else NavRepository.PKG_MAPS
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return start(ctx, i)
    }

    fun isInstalled(ctx: Context, app: String): Boolean {
        val pkg = if (app == "waze") NavRepository.PKG_WAZE else NavRepository.PKG_MAPS
        return ctx.packageManager.getLaunchIntentForPackage(pkg) != null
    }

    private fun startRoute(ctx: Context, intent: Intent): Boolean {
        if (!start(ctx, intent)) return false
        br.painellivre.nav.native.NativeNavigation.stop()
        // Não depende da notificação: ela pode demorar ou estar sem permissão na DiLink 3.
        br.painellivre.cluster.ClusterService.start(ctx, br.painellivre.cluster.ClusterService.ACTION_ROUTE_REQUEST)
        return true
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
