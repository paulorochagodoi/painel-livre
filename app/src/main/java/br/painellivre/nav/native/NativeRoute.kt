package br.painellivre.nav.native

import br.painellivre.nav.Maneuver
import br.painellivre.nav.ParsedNav
import br.painellivre.units.Units
import org.json.JSONObject
import java.net.URLDecoder
import kotlin.math.*

data class RoutePoint(val lat: Double, val lon: Double) {
    init { require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) }
    fun distance(other: RoutePoint): Double {
        val a = Math.toRadians(lat); val b = Math.toRadians(other.lat)
        val h = sin((b-a)/2).pow(2) + cos(a)*cos(b)*sin(Math.toRadians(other.lon-lon)/2).pow(2)
        return 6371000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
data class RouteStep(val start: Int, val meters: Double, val seconds: Double, val maneuver: Maneuver,
                     val instruction: String, val street: String, val exit: Int?)
data class NativeRoute(val points: List<RoutePoint>, val steps: List<RouteStep>)
data class RouteProgress(val along: Double, val offRoute: Double, val remaining: Double, val arrived: Boolean, val nav: ParsedNav)

/** Geometria e orientação OSRM, sem Android. A projeção fica limitada ao avanço já feito. */
class RouteTracker(val route: NativeRoute) {
    private val cumulative = DoubleArray(route.points.size)
    private var progress = 0.0
    init {
        require(route.points.size >= 2 && route.steps.isNotEmpty())
        for (i in 1 until cumulative.size) cumulative[i] = cumulative[i-1] + route.points[i-1].distance(route.points[i])
    }
    fun update(point: RoutePoint): RouteProgress {
        var best = Double.MAX_VALUE; var along = progress
        for (i in 0 until route.points.lastIndex) {
            if (cumulative[i+1] < progress - 25) continue
            // Janela evita saltar para outro trecho em rotas que cruzam a si mesmas.
            if (cumulative[i] > progress + 1500) break
            val a = route.points[i]; val b = route.points[i+1]
            val scale = cos(Math.toRadians(point.lat))
            val x = (b.lon-a.lon)*scale; val y = b.lat-a.lat
            val t = if (x*x+y*y == 0.0) 0.0 else
                (((point.lon-a.lon)*scale*x+(point.lat-a.lat)*y)/(x*x+y*y)).coerceIn(0.0,1.0)
            val distance = point.distance(RoutePoint(a.lat+(b.lat-a.lat)*t, a.lon+(b.lon-a.lon)*t))
            if (distance < best) { best = distance; along = cumulative[i]+(cumulative[i+1]-cumulative[i])*t }
        }
        if (best <= 60) progress = max(progress, along)
        val remaining = (cumulative.last()-progress).coerceAtLeast(0.0)
        val arrived = remaining < 30 && point.distance(route.points.last()) < 40
        val next = route.steps.firstOrNull { cumulative[it.start] > progress + 8 } ?: route.steps.last()
        val distance = (cumulative[next.start]-progress).coerceAtLeast(0.0).roundToInt()
        val seconds = route.steps.sumOf { step ->
            val start = cumulative[step.start]
            val end = start + step.meters
            if (end <= progress) 0.0 else step.seconds * ((end-progress)/step.meters.coerceAtLeast(1.0)).coerceIn(0.0,1.0)
        }.roundToInt()
        return RouteProgress(progress, best, remaining, arrived,
            ParsedNav(if (arrived) Maneuver.DESTINATION else next.maneuver, next.exit, distance,
                meters(distance), if (arrived) "Você chegou" else next.instruction, next.street.ifBlank { null },
                null, seconds, remaining.roundToInt(), meters(remaining.roundToInt())))
    }
    companion object {
        /** Texto de reserva; a tela reformata com a unidade escolhida a partir dos metros. */
        fun meters(value: Int) = Units.navDistance(value, null) ?: "$value m"
    }
}

object OsrmRoute {
    fun url(from: RoutePoint, to: RoutePoint) =
        "https://router.project-osrm.org/route/v1/driving/${from.lon},${from.lat};${to.lon},${to.lat}?steps=true&geometries=geojson&overview=full"
    fun parse(body: String): NativeRoute {
        val root = JSONObject(body)
        require(root.optString("code") == "Ok") { "Rota indisponível" }
        val legs = root.getJSONArray("routes").getJSONObject(0).getJSONArray("legs")
        val points = mutableListOf<RoutePoint>(); val steps = mutableListOf<RouteStep>()
        for (l in 0 until legs.length()) {
            val items = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until items.length()) {
                val step = items.getJSONObject(i); val m = step.getJSONObject("maneuver")
                val coords = step.getJSONObject("geometry").getJSONArray("coordinates")
                val start = if (points.isEmpty()) 0 else points.lastIndex
                for (j in 0 until coords.length()) {
                    val c = coords.getJSONArray(j); val p = RoutePoint(c.getDouble(1), c.getDouble(0))
                    if (points.lastOrNull() != p) points += p
                }
                val type = m.getString("type"); val modifier = m.optString("modifier")
                val maneuver = when {
                    type == "arrive" -> Maneuver.DESTINATION
                    type in setOf("roundabout", "rotary", "roundabout turn") -> Maneuver.ROUNDABOUT
                    modifier == "uturn" -> Maneuver.UTURN_LEFT
                    modifier == "slight left" -> Maneuver.SLIGHT_LEFT
                    modifier == "slight right" -> Maneuver.SLIGHT_RIGHT
                    modifier == "sharp left" -> Maneuver.SHARP_LEFT
                    modifier == "sharp right" -> Maneuver.SHARP_RIGHT
                    modifier == "left" -> Maneuver.LEFT
                    modifier == "right" -> Maneuver.RIGHT
                    else -> Maneuver.STRAIGHT
                }
                val exit = m.optInt("exit").takeIf { it > 0 }
                val instruction = when (maneuver) {
                    Maneuver.DESTINATION -> "Você chegou"
                    Maneuver.ROUNDABOUT -> exit?.let { "Pegue a ${it}ª saída" } ?: "Entre na rotatória"
                    Maneuver.UTURN_LEFT -> "Faça o retorno"
                    Maneuver.LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT -> "Vire à esquerda"
                    Maneuver.RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT -> "Vire à direita"
                    else -> "Siga em frente"
                }
                steps += RouteStep(start, step.getDouble("distance"), step.getDouble("duration"), maneuver,
                    instruction, step.optString("name"), exit)
            }
        }
        require(points.size >= 2 && steps.isNotEmpty())
        return NativeRoute(points, steps)
    }
}

/** Só extrai destinos explícitos; links encurtados não são abertos automaticamente. */
object RouteDestination {
    fun query(text: String): String? = runCatching {
        val value = text.trim().take(4096)
        val raw = when {
            value.startsWith("google.navigation:", true) -> value.substringAfter(':').substringAfter("q=", "").substringBefore('&')
            value.startsWith("geo:", true) -> {
                val q = value.substringAfter("?q=", "").substringBefore('&')
                q.ifBlank { value.substringAfter(':').substringBefore('?').takeUnless { it == "0,0" }.orEmpty() }
            }
            value.startsWith("https://", true) || value.startsWith("http://", true) -> {
                val uri = java.net.URI(value)
                if (uri.host !in setOf("maps.google.com", "www.google.com", "google.com", "www.google.com.br", "maps.google.com.br", "waze.com", "www.waze.com")) return null
                uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') in setOf("q", "query", "destination", "ll") }?.substringAfter('=') ?: return null
            }
            else -> value.takeUnless { it.contains("://") } ?: return null
        }
        URLDecoder.decode(raw, "UTF-8").trim().takeIf { it.isNotBlank() }
    }.getOrNull()
    fun point(query: String): RoutePoint? {
        val m = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)(?:\\(.*\\))?\\s*$").matchEntire(query) ?: return null
        return runCatching { RoutePoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }.getOrNull()
    }
}
