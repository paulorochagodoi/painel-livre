package br.painellivre.nav

import br.painellivre.util.AppLog
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory

/** Aplica o [MapLook] num estilo do MapLibre já carregado (tela central e painel). */
object MapStyler {

    /** Alarga as ruas do estilo. Chamar a cada estilo novo (troca de tema recria as camadas). */
    fun widenRoads(style: Style?) {
        style ?: return
        if (!style.isFullyLoaded) return
        var changed = 0
        for (layer in style.layers) {
            if (layer !is LineLayer) continue
            val factor = MapLook.roadFactor(layer.id, layer.sourceLayer) ?: continue
            runCatching {
                val width = layer.lineWidth
                val scaled = when {
                    width.isExpression -> MapLook.scaleWidth(width.expression?.toArray(), factor)
                    else -> MapLook.scaleWidth(width.value as? Number, factor)
                } ?: return@runCatching
                if (scaled is Number) layer.setProperties(PropertyFactory.lineWidth(scaled.toFloat()))
                else layer.setProperties(PropertyFactory.lineWidth(Expression.raw(MapLook.toJson(scaled))))
                changed++
            }.onFailure { AppLog.w("Mapa: não deu para alargar a camada ${layer.id}: ${it.message}") }
        }
        if (changed == 0) AppLog.w("Mapa: nenhuma rua alargada (estilo sem a fonte ${MapLook.SOURCE_LAYER}?)")
    }
}
