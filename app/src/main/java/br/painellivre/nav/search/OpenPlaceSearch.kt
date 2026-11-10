package br.painellivre.nav.search

import br.painellivre.nav.Place
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Busca aberta, sem chave: Photon para endereço, Overpass para categoria perto do carro
 * e Nominatim só quando o Photon não responde. A posição sai com 4 casas (~11 m).
 *
 * Overpass e Nominatim são instâncias públicas: no máximo um pedido a cada
 * [minOsmIntervalMs]. Photon não entra nesse intervalo (a tela já espera entre teclas).
 */
class OpenPlaceSearch(
    private val fetch: suspend (url: String, userAgent: String) -> String,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val minOsmIntervalMs: Long = 1_100L,
) : PlaceProvider {
    override val id: String = "open"
    private var lastOsmMs: Long? = null

    override suspend fun search(query: PlaceQuery): PlaceSearchResult {
        val text = query.text.trim()
        val category = query.category
        if (text.isEmpty() && category == null) return PlaceSearchResult(emptyList(), PlaceSearchResult.Status.EMPTY)
        val lat = query.nearLat
        val lon = query.nearLon
        if (category != null && lat != null && lon != null && osmAllowed()) {
            markOsm()
            val body = runCatching { fetch(OverpassSearch.url(category, lat, lon), OSM_AGENT) }.getOrNull()
            val parsed = body?.let { OverpassSearch.parse(it, category.fallbackName) }
            if (parsed != null) {
                val places = PlaceRank.nearest(parsed, lat, lon).take(query.limit)
                return PlaceSearchResult(places, if (places.isEmpty()) PlaceSearchResult.Status.EMPTY else PlaceSearchResult.Status.OK)
            }
        }
        if (category != null) {
            return photon(
                text.ifBlank { category.fallbackName }, lat, lon,
                radiusKm = if (lat != null && lon != null) PhotonSearch.CATEGORY_RADIUS_KM else null,
                osmTag = "${category.osmKey}:${category.osmValue}",
                limit = query.limit,
            ) ?: PlaceSearchResult(emptyList(), PlaceSearchResult.Status.UNAVAILABLE)
        }
        return textSearch(text, lat, lon, query.limit)
    }

    /** Texto: Photon com viés e bbox. Se o bbox não achar nada, tenta de novo sem ele. Se o Photon cair, Nominatim. */
    private suspend fun textSearch(text: String, lat: Double?, lon: Double?, limit: Int): PlaceSearchResult {
        val radius = if (lat != null && lon != null) PhotonSearch.TEXT_RADIUS_KM else null
        val nearby = photon(text, lat, lon, radius, osmTag = null, limit)
        if (nearby != null && (nearby.status == PlaceSearchResult.Status.OK || lat == null)) return nearby
        if (nearby != null && radius != null) {
            val wide = photon(text, lat, lon, radiusKm = null, osmTag = null, limit)
            if (wide != null) return wide
        }
        return nominatim(text, lat, lon, limit)
    }

    /** null = o Photon não respondeu ou devolveu algo que não é GeoJSON. */
    private suspend fun photon(
        text: String,
        lat: Double?,
        lon: Double?,
        radiusKm: Double?,
        osmTag: String?,
        limit: Int,
    ): PlaceSearchResult? {
        val body = runCatching { fetch(PhotonSearch.url(text, lat, lon, radiusKm, osmTag), PHOTON_AGENT) }.getOrNull()
            ?: return null
        val parsed = runCatching { PhotonSearch.parse(body) }.getOrNull() ?: return null
        val places = PlaceRank.nearest(parsed, lat, lon).take(limit)
        return PlaceSearchResult(places, if (places.isEmpty()) PlaceSearchResult.Status.EMPTY else PlaceSearchResult.Status.OK)
    }

    private suspend fun nominatim(text: String, lat: Double?, lon: Double?, limit: Int): PlaceSearchResult {
        if (!osmAllowed()) return PlaceSearchResult(emptyList(), PlaceSearchResult.Status.UNAVAILABLE)
        markOsm()
        val body = runCatching { fetch(NominatimSearch.url(text, lat, lon), OSM_AGENT) }.getOrNull()
            ?: return PlaceSearchResult(emptyList(), PlaceSearchResult.Status.UNAVAILABLE)
        val parsed = NominatimSearch.parse(body) ?: return PlaceSearchResult(emptyList(), PlaceSearchResult.Status.UNAVAILABLE)
        val places = PlaceRank.nearest(parsed, lat, lon).take(limit)
        return PlaceSearchResult(places, if (places.isEmpty()) PlaceSearchResult.Status.EMPTY else PlaceSearchResult.Status.OK)
    }

    private fun osmAllowed() = lastOsmMs?.let { nowMs() - it >= minOsmIntervalMs } ?: true
    private fun markOsm() { lastOsmMs = nowMs() }

    companion object {
        const val PHOTON_AGENT = "PainelLivre (app aberto para BYD)"
        const val OSM_AGENT = "PainelLivre/navegacao (https://github.com/paulorochagodoi/painel-livre)"
    }
}

/** Photon (komoot), GeoJSON. Sem chave. Viés com 4 casas e bbox opcional. */
object PhotonSearch {
    const val ENDPOINT = "https://photon.komoot.io/api/"
    const val TEXT_RADIUS_KM = 25.0
    const val CATEGORY_RADIUS_KM = 12.0

    fun url(text: String, lat: Double?, lon: Double?, radiusKm: Double?, osmTag: String?): String {
        val b = StringBuilder(ENDPOINT)
            .append("?q=").append(urlEncode(text))
            .append("&limit=15&lang=pt")
        if (lat != null && lon != null) {
            b.append("&lat=").append(fmt(lat)).append("&lon=").append(fmt(lon))
            b.append("&location_bias_scale=1&zoom=15")
            if (radiusKm != null) b.append("&bbox=").append(bbox(lat, lon, radiusKm))
        }
        // Tag fixa (amenity:fuel etc.). Os dois-pontos ficam literais, como a API do Photon pede.
        if (!osmTag.isNullOrBlank()) b.append("&osm_tag=").append(osmTag)
        return b.toString()
    }

    fun parse(body: String): List<Place> {
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
}

/** Overpass público: categorias OSM perto do carro. Não serve para endereço livre. */
object OverpassSearch {
    const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    /** 8 km em volta do carro. Sem corte de quantidade: um limite fixo poderia deixar o mais perto de fora. */
    const val RADIUS_M = 8_000

    fun url(category: PlaceCategory, lat: Double, lon: Double): String {
        val la = fmt(lat)
        val lo = fmt(lon)
        val q = "[out:json][timeout:15];(" +
            "node[\"${category.osmKey}\"=\"${category.osmValue}\"](around:$RADIUS_M,$la,$lo);" +
            "way[\"${category.osmKey}\"=\"${category.osmValue}\"](around:$RADIUS_M,$la,$lo);" +
            ");out center;"
        return "$ENDPOINT?data=${urlEncode(q)}"
    }

    /** null quando a resposta não é um JSON de elementos (erro ou página HTML). Lista vazia é "não há ponto". */
    fun parse(body: String, fallbackName: String): List<Place>? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (!root.has("elements")) return null
        val elements = root.optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val (lat, lon) = when {
                e.has("lat") && e.has("lon") -> e.getDouble("lat") to e.getDouble("lon")
                e.optJSONObject("center") != null -> {
                    val c = e.getJSONObject("center")
                    c.optDouble("lat") to c.optDouble("lon")
                }
                else -> return@mapNotNull null
            }
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            val name = tags.optString("name").ifEmpty {
                tags.optString("brand").ifEmpty { tags.optString("operator").ifEmpty { fallbackName } }
            }
            val street = listOfNotNull(
                tags.optString("addr:street").ifEmpty { null },
                tags.optString("addr:housenumber").ifEmpty { null },
            ).joinToString(", ")
            val area = listOfNotNull(
                street.ifEmpty { null },
                tags.optString("addr:suburb").ifEmpty { null },
                tags.optString("addr:city").ifEmpty { null },
            ).distinct().joinToString(" · ")
            Place(name, area, lat, lon)
        }
    }
}

/** Nominatim: reserva da busca de texto. Mesma política de uso do Overpass (intervalo e User-Agent). */
object NominatimSearch {
    const val ENDPOINT = "https://nominatim.openstreetmap.org/search"

    fun url(text: String, lat: Double?, lon: Double?): String {
        val b = StringBuilder(ENDPOINT)
            .append("?format=jsonv2&limit=10&accept-language=pt-BR&q=")
            .append(urlEncode(text))
        if (lat != null && lon != null) b.append("&lat=").append(fmt(lat)).append("&lon=").append(fmt(lon))
        return b.toString()
    }

    fun parse(body: String): List<Place>? = runCatching {
        val arr = JSONArray(body)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val display = o.optString("display_name")
            val name = o.optString("name").ifEmpty { display.substringBefore(',').trim() }
            if (name.isEmpty()) return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val address = display.substringAfter(',', "").trim()
            Place(name, address, lat, lon)
        }
    }.getOrNull()
}

/** Mais perto primeiro. Sem posição, mantém a ordem do provedor. */
object PlaceRank {
    fun nearest(places: List<Place>, lat: Double?, lon: Double?): List<Place> {
        val unique = places.distinctBy { "${it.name}|${it.address}|${fmt(it.lat)}|${fmt(it.lon)}" }
        if (lat == null || lon == null) return unique
        return unique.sortedBy { meters(lat, lon, it.lat, it.lon) }
    }

    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a1 = Math.toRadians(lat1)
        val a2 = Math.toRadians(lat2)
        val h = sin((a2 - a1) / 2).let { it * it } +
            cos(a1) * cos(a2) * sin(Math.toRadians(lon2 - lon1) / 2).let { it * it }
        return 6_371_000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

internal fun fmt(value: Double) = String.format(Locale.ROOT, "%.4f", value)

internal fun bbox(lat: Double, lon: Double, radiusKm: Double): String {
    val dLat = radiusKm / 111.32
    val dLon = radiusKm / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.2))
    return "${fmt(lon - dLon)},${fmt(lat - dLat)},${fmt(lon + dLon)},${fmt(lat + dLat)}"
}

private fun urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")
