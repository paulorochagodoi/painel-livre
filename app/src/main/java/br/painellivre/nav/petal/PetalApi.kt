package br.painellivre.nav.petal

import br.painellivre.nav.Maneuver
import br.painellivre.nav.Place
import br.painellivre.nav.native.NativeRoute
import br.painellivre.nav.native.RoutePoint
import br.painellivre.nav.native.RouteStep
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * **Kotlin puro.** Prova de conceito do Petal Maps (HUAWEI Map Kit / Site Kit, APIs REST), sem SDK e
 * sem HMS Core. Monta pedidos e lê respostas; quem faz a chamada HTTP é [MapProviders].
 *
 * Atenção: o contrato de uso da Huawei (HUAWEI Map Service Agreement, cláusulas 2.1.1 e 2.1.2) proíbe
 * uso em carro e em navegação. Isto é só um teste técnico, desligado por padrão (docs/PETAL_POC.md).
 *
 * Fontes: Keyword Search (Site Kit REST), Directions API (Map Kit / Navi Kit REST), Tile API (Map Kit REST).
 */
object PetalApi {
    const val SEARCH_URL = "https://siteapi.cloud.huawei.com/mapApi/v1/siteService/searchByText"
    const val DRIVING_URL = "https://mapapi.cloud.huawei.com/mapApi/v1/routeService/driving"
    const val TILE_URL = "https://mapapi.cloud.huawei.com/mapApi/v1/mapService/getTile"

    /** A Huawei pede a chave codificada na URL (ex.: `ABC/DFG+` → `ABC%2FDFG%2B`). */
    fun encodeKey(key: String): String = URLEncoder.encode(key.trim(), "UTF-8")

    fun searchUrl(key: String) = "$SEARCH_URL?key=${encodeKey(key)}"
    fun drivingUrl(key: String) = "$DRIVING_URL?key=${encodeKey(key)}"

    /** Corpo do Keyword Search. Com [near], prioriza resultados num raio de [radius] metros. */
    fun searchBody(query: String, near: RoutePoint?, radius: Int = 50_000, pageSize: Int = 8): String {
        val o = JSONObject().put("query", query.trim()).put("language", "pt").put("pageSize", pageSize)
        if (near != null) {
            o.put("location", JSONObject().put("lat", near.lat).put("lng", near.lon))
            o.put("radius", radius.coerceIn(1, 50_000))
        }
        return o.toString()
    }

    /** Corpo do Driving Route Planning: só origem e destino (o trânsito vem em `durationInTraffic`). */
    fun drivingBody(from: RoutePoint, to: RoutePoint): String = JSONObject()
        .put("origin", JSONObject().put("lat", from.lat).put("lng", from.lon))
        .put("destination", JSONObject().put("lat", to.lat).put("lng", to.lon))
        .toString()

    /** Erro devolvido pela Huawei com HTTP 200 (`returnCode` diferente de "0"). */
    class PetalException(val code: String, message: String) : Exception(message)

    private fun checkCode(root: JSONObject) {
        val code = root.optString("returnCode", "0")
        if (code != "0") throw PetalException(code, "Petal: ${root.optString("returnDesc", "erro")} ($code)")
    }

    // ── Busca ───────────────────────────────────────────────────────────────

    fun parseSearch(body: String): List<Place> {
        val root = JSONObject(body)
        checkCode(root)
        val sites = root.optJSONArray("sites") ?: return emptyList()
        return (0 until sites.length()).mapNotNull { i ->
            val s = sites.optJSONObject(i) ?: return@mapNotNull null
            val loc = s.optJSONObject("location") ?: return@mapNotNull null
            if (!loc.has("lat") || !loc.has("lng")) return@mapNotNull null
            val lat = loc.optDouble("lat"); val lon = loc.optDouble("lng")
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val a = s.optJSONObject("address")
            val street = listOfNotNull(a?.str("thoroughfare"), a?.str("streetNumber")).joinToString(", ")
            val name = s.str("name") ?: street.ifEmpty { null } ?: s.str("formatAddress") ?: return@mapNotNull null
            val area = listOfNotNull(
                street.takeIf { it.isNotEmpty() && it != name },
                a?.str("subLocality"),
                a?.str("locality") ?: a?.str("tertiaryAdminArea"),
                a?.str("adminArea"),
            ).distinct().joinToString(" · ").ifEmpty { s.str("formatAddress").orEmpty() }
            Place(name, area, lat, lon)
        }.distinctBy { "${it.name}|${it.address}" }
    }

    // ── Rota ────────────────────────────────────────────────────────────────

    /** Rota do Petal com os dois tempos: sem trânsito ([durationSec]) e com trânsito ([trafficSec]). */
    data class Route(val route: NativeRoute, val distanceMeters: Double, val durationSec: Double, val trafficSec: Double?) {
        /** Tempo a usar no ETA: com trânsito quando a Huawei devolve, senão o normal. */
        val etaSec: Double get() = trafficSec?.takeIf { it > 0 } ?: durationSec
    }

    fun parseDriving(body: String): Route {
        val root = JSONObject(body)
        checkCode(root)
        val path = root.optJSONArray("routes")?.optJSONObject(0)?.optJSONArray("paths")?.optJSONObject(0)
            ?: throw PetalException("NO_ROUTE", "Petal: sem rota")
        val duration = path.optDouble("duration", 0.0).takeIf { it.isFinite() } ?: 0.0
        val traffic = path.optDouble("durationInTraffic", Double.NaN).takeIf { it.isFinite() && it > 0 }
        // O ETA do RouteTracker soma o tempo de cada trecho; escalamos para refletir o trânsito.
        val factor = if (traffic != null && duration > 0) (traffic / duration).coerceIn(0.2, 10.0) else 1.0
        val items = path.optJSONArray("steps") ?: JSONArray()
        val points = mutableListOf<RoutePoint>(); val steps = mutableListOf<RouteStep>()
        // Hipótese a validar com a chave real: a `action` do trecho i acontece no FIM dele (o último
        // trecho vem com "end"). Então a manobra que abre o trecho i é a `action` do trecho i-1,
        // como no OSRM, onde a manobra fica no começo do trecho.
        var previousAction: String? = null
        for (i in 0 until items.length()) {
            val step = items.getJSONObject(i)
            val start = if (points.isEmpty()) 0 else points.lastIndex
            val poly = step.optJSONArray("polyline") ?: JSONArray()
            for (j in 0 until poly.length()) {
                val c = poly.getJSONObject(j)
                val p = runCatching { RoutePoint(c.getDouble("lat"), c.getDouble("lng")) }.getOrNull() ?: continue
                if (points.lastOrNull() != p) points += p
            }
            val maneuver = if (i == 0) Maneuver.STRAIGHT else maneuver(previousAction).let {
                if (it == Maneuver.DESTINATION) Maneuver.STRAIGHT else it
            }
            steps += RouteStep(start, step.optDouble("distance", 0.0), step.optDouble("duration", 0.0) * factor,
                maneuver, instruction(maneuver, null), step.optString("roadName"), null)
            previousAction = step.optString("action")
        }
        require(points.size >= 2 && steps.isNotEmpty()) { "Petal: rota sem geometria" }
        steps += RouteStep(points.lastIndex, 0.0, 0.0, Maneuver.DESTINATION, "Você chegou", "", null)
        val distance = path.optDouble("distance", steps.sumOf { it.meters })
        return Route(NativeRoute(points, steps), distance, duration, traffic)
    }

    /** Ações da Huawei (`turn-left`, `TURN_LEFT`, `roundabout-right`…) → [Maneuver]. */
    fun maneuver(action: String?): Maneuver = when (action.orEmpty().trim().lowercase(Locale.ROOT).replace('_', '-')) {
        "end" -> Maneuver.DESTINATION
        "turn-left" -> Maneuver.LEFT
        "turn-right" -> Maneuver.RIGHT
        "ramp-left" -> Maneuver.EXIT_LEFT
        "ramp-right" -> Maneuver.EXIT_RIGHT
        "fork-left" -> Maneuver.KEEP_LEFT
        "fork-right" -> Maneuver.KEEP_RIGHT
        "merge" -> Maneuver.MERGE
        "turn-slight-left" -> Maneuver.SLIGHT_LEFT
        "turn-slight-right" -> Maneuver.SLIGHT_RIGHT
        "turn-sharp-left" -> Maneuver.SHARP_LEFT
        "turn-sharp-right" -> Maneuver.SHARP_RIGHT
        "uturn-left" -> Maneuver.UTURN_LEFT
        "uturn-right" -> Maneuver.UTURN_RIGHT
        "roundabout-left", "roundabout-right" -> Maneuver.ROUNDABOUT
        else -> Maneuver.STRAIGHT
    }

    private fun instruction(m: Maneuver, exit: Int?): String = when (m) {
        Maneuver.DESTINATION -> "Você chegou"
        Maneuver.ROUNDABOUT -> exit?.let { "Pegue a ${it}ª saída" } ?: "Entre na rotatória"
        Maneuver.UTURN_LEFT, Maneuver.UTURN_RIGHT -> "Faça o retorno"
        Maneuver.LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT -> "Vire à esquerda"
        Maneuver.RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT -> "Vire à direita"
        Maneuver.EXIT_LEFT -> "Pegue a saída à esquerda"
        Maneuver.EXIT_RIGHT -> "Pegue a saída à direita"
        Maneuver.KEEP_LEFT -> "Mantenha-se à esquerda"
        Maneuver.KEEP_RIGHT -> "Mantenha-se à direita"
        else -> "Siga em frente"
    }

    // ── Tiles (raster) ──────────────────────────────────────────────────────

    /** URL de tiles para o MapLibre; ele troca `{x}`, `{y}` e `{z}` em qualquer parte da URL. */
    fun tileUrl(key: String) = "$TILE_URL?x={x}&y={y}&z={z}&language=pt&scale=2&key=${encodeKey(key)}"

    /** Estilo MapLibre mínimo com uma camada raster do Petal (não há tema escuro nos tiles raster). */
    fun rasterStyle(key: String): String = JSONObject()
        .put("version", 8)
        .put("name", "Petal raster (prova de conceito)")
        .put("sources", JSONObject().put("petal", JSONObject()
            .put("type", "raster")
            .put("tiles", JSONArray().put(tileUrl(key)))
            .put("tileSize", 256)
            .put("maxzoom", 20)
            .put("attribution", "© Petal Maps / Huawei")))
        .put("layers", JSONArray()
            .put(JSONObject().put("id", "background").put("type", "background")
                .put("paint", JSONObject().put("background-color", "#f2efe9")))
            .put(JSONObject().put("id", "petal").put("type", "raster").put("source", "petal")))
        .toString()

    private fun JSONObject.str(name: String): String? = optString(name, "").trim().takeIf { it.isNotEmpty() && it != "null" }
}
