package br.painellivre.nav.petal

import br.painellivre.nav.Place
import java.util.Locale

/** **Kotlin puro.** Texto lado a lado da comparação Photon/OSRM × Petal (Ajustes e registro do debug). */
object ProviderComparison {
    data class Search(val provider: String, val latencyMs: Long, val places: List<Place>, val error: String? = null)
    data class Route(
        val provider: String, val latencyMs: Long, val distanceMeters: Double?, val durationSec: Double?,
        val trafficSec: Double?, val error: String? = null,
    )

    fun format(query: String, searches: List<Search>, routes: List<Route>, maxPlaces: Int = 5): String = buildString {
        append("Busca: \"").append(query.trim()).append("\"\n")
        for (s in searches) {
            append("\n■ ").append(s.provider).append(" — ").append(s.latencyMs).append(" ms")
            if (s.error != null) { append("\n  erro: ").append(s.error); continue }
            append(" — ").append(s.places.size).append(if (s.places.size == 1) " resultado" else " resultados")
            s.places.take(maxPlaces).forEachIndexed { i, p ->
                append("\n  ").append(i + 1).append(". ").append(p.name)
                if (p.address.isNotBlank()) append(" · ").append(p.address)
            }
        }
        if (routes.isNotEmpty()) append("\n\nRota até o 1º resultado de cada busca:")
        for (r in routes) {
            append("\n■ ").append(r.provider).append(" — ").append(r.latencyMs).append(" ms")
            if (r.error != null) { append("\n  erro: ").append(r.error); continue }
            append("\n  distância: ").append(km(r.distanceMeters))
            append(" · sem trânsito: ").append(minutes(r.durationSec))
            append(" · com trânsito: ").append(r.trafficSec?.let(::minutes) ?: "não informado")
        }
    }

    fun km(meters: Double?): String = meters?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it / 1000.0) } ?: "—"

    fun minutes(seconds: Double?): String {
        val s = seconds?.takeIf { it.isFinite() && it >= 0 } ?: return "—"
        val m = Math.round(s / 60.0).toInt()
        return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)} min" else "$m min"
    }
}
