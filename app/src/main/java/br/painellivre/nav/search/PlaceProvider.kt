package br.painellivre.nav.search

import br.painellivre.nav.Place

/**
 * Busca de lugares. A tela só fala com esta interface.
 *
 * A implementação aberta ([OpenPlaceSearch]) é a padrão, sem chave.
 * Um provedor HERE (issue #28) entra depois como outra implementação, com a mesma
 * [PlaceQuery] e a mesma [PlaceSearchResult], sem mudar a Home nem a busca.
 */
interface PlaceProvider {
    /** Identificador estável. O aberto é `"open"`; o HERE usará `"here"`. */
    val id: String

    suspend fun search(query: PlaceQuery): PlaceSearchResult
}

/** Pedido da tela. A posição, quando existe, vem do GPS da central. */
data class PlaceQuery(
    val text: String,
    val nearLat: Double? = null,
    val nearLon: Double? = null,
    val category: PlaceCategory? = null,
    val limit: Int = 12,
)

/**
 * Categorias da busca (Recarga, Combustível, Estacionamento).
 * O valor OSM é o filtro; o nome de reserva aparece quando o ponto não tem nome.
 */
enum class PlaceCategory(val osmValue: String, val fallbackName: String) {
    CHARGING("charging_station", "Eletroposto"),
    FUEL("fuel", "Posto"),
    PARKING("parking", "Estacionamento");

    val osmKey: String get() = "amenity"
}

data class PlaceSearchResult(val places: List<Place>, val status: Status) {
    enum class Status { OK, EMPTY, UNAVAILABLE }
}
