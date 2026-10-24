package br.painellivre.nav.native

import br.painellivre.nav.MapLook
import br.painellivre.ui.aura.AuraColors
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Linha da rota nativa: `accent` com contorno ([MapLook.routeColors]), largura pelo zoom.
 * Recriada também após a troca de tema: o estilo anterior perde sources/layers.
 */
object NativeRouteLayer {
    private const val SOURCE = "native-route"
    private const val CASING = "native-route-casing"
    private const val LINE = "native-route-line"

    fun draw(style: Style?, colors: AuraColors) {
        style ?: return
        if (!style.isFullyLoaded) return
        val points = NativeNavigation.route.value?.points.orEmpty()
        val json = JSONObject().put("type", "Feature").put("properties", JSONObject())
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates",
                JSONArray(points.map { listOf(it.lon, it.lat) })))
        val data = if (points.size >= 2) json.toString() else "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val source = style.getSourceAs<GeoJsonSource>(SOURCE)
        if (source != null) source.setGeoJson(data) else style.addSource(GeoJsonSource(SOURCE, data))
        val c = MapLook.routeColors(colors)
        val casing = style.getLayerAs<LineLayer>(CASING) ?: LineLayer(CASING, SOURCE).also { style.addLayer(it) }
        val line = style.getLayerAs<LineLayer>(LINE) ?: LineLayer(LINE, SOURCE).also { style.addLayerAbove(it, CASING) }
        casing.setProperties(lineColor(c.casing), lineWidth(width(MapLook.routeCasingStops())),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND))
        line.setProperties(lineColor(c.line), lineWidth(width(MapLook.ROUTE_STOPS)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND))
    }

    private fun width(stops: List<Pair<Double, Double>>): Expression =
        Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(),
            *stops.map { (z, w) -> Expression.stop(z, w) }.toTypedArray())
}
