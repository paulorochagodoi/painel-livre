package br.painellivre.nav.native

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*

/** Recriada também após a troca de tema: o estilo anterior perde sources/layers. */
object NativeRouteLayer {
    fun draw(style: Style?) {
        style ?: return
        if (!style.isFullyLoaded) return
        val points = NativeNavigation.route.value?.points.orEmpty()
        val json = JSONObject().put("type", "Feature").put("properties", JSONObject())
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates",
                JSONArray(points.map { listOf(it.lon, it.lat) })))
        val data = if (points.size >= 2) json.toString() else "{\"type\":\"FeatureCollection\",\"features\":[]}"
        val source = style.getSourceAs<GeoJsonSource>("native-route")
        if (source != null) source.setGeoJson(data) else {
            style.addSource(GeoJsonSource("native-route", data))
            style.addLayer(LineLayer("native-route-line", "native-route").withProperties(
                lineColor("#1688ff"), lineWidth(7f), lineOpacity(0.9f)))
        }
    }
}
