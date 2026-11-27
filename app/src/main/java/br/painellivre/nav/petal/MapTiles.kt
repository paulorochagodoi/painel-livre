package br.painellivre.nav.petal

import android.content.Context
import org.maplibre.android.maps.Style

/** Escolhe o desenho do mapa: OpenFreeMap (padrão) ou tiles raster do Petal (prova de conceito). */
object MapTiles {
    /** Marcador do estilo Petal, para quem compara "estilo carregado" com "estilo desejado". */
    const val PETAL_STYLE = "petal:raster"

    /** [default] quando o Petal está desligado ou sem chave; senão [PETAL_STYLE]. */
    fun styleFor(ctx: Context, default: String): String =
        if (MapProviders.tilesKey(ctx) != null) PETAL_STYLE else default

    fun builder(ctx: Context, style: String): Style.Builder {
        if (style == PETAL_STYLE) {
            val key = MapProviders.tilesKey(ctx)
            if (key != null) return Style.Builder().fromJson(PetalApi.rasterStyle(key))
        }
        return Style.Builder().fromUri(if (style == PETAL_STYLE) DEFAULT_LIGHT else style)
    }

    private const val DEFAULT_LIGHT = "https://tiles.openfreemap.org/styles/positron"
}
