package br.painellivre

import br.painellivre.nav.Place
import br.painellivre.nav.Places
import br.painellivre.nav.search.NominatimSearch
import br.painellivre.nav.search.OpenPlaceSearch
import br.painellivre.nav.search.OverpassSearch
import br.painellivre.nav.search.PhotonSearch
import br.painellivre.nav.search.PlaceCategory
import br.painellivre.nav.search.PlaceProvider
import br.painellivre.nav.search.PlaceQuery
import br.painellivre.nav.search.PlaceRank
import br.painellivre.nav.search.PlaceSearchResult
import br.painellivre.nav.search.bbox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão da busca (issue #26): Photon continua sendo o geocoder, agora com viés,
 * e categoria (posto, estacionamento, eletroposto) vem do Overpass já ordenada por distância.
 * Sem chave. Um segundo [PlaceProvider] precisa caber na mesma interface (HERE, issue #28).
 */
class PlaceSearchTest {
    private val nearLat = -23.5505123
    private val nearLon = -46.633308

    @Test fun photonAindaNaoReordenaSozinho() {
        val body = """
            {"features":[
              {"geometry":{"coordinates":[-46.6576,-23.5874]},
               "properties":{"name":"Parque Ibirapuera","street":"Avenida Pedro Álvares Cabral","district":"Ibirapuera","city":"São Paulo","state":"São Paulo"}},
              {"geometry":{"coordinates":[-46.65,-23.56]},
               "properties":{"street":"Rua Augusta","housenumber":"1500","city":"São Paulo"}},
              {"geometry":{"coordinates":[0.0,0.0]},"properties":{}}
            ]}
        """.trimIndent()
        val list = Places.parsePhoton(body)
        assertEquals(2, list.size)
        assertEquals("Parque Ibirapuera", list[0].name)
        assertEquals("Rua Augusta, 1500", list[1].name)
    }

    @Test fun textoPedeViesBboxEQuatroCasas() {
        val url = PhotonSearch.url("Avenida Paulista", nearLat, nearLon, PhotonSearch.TEXT_RADIUS_KM, null)
        assertTrue(url.startsWith("https://photon.komoot.io/api/?"))
        assertTrue(url.contains("q=Avenida+Paulista") || url.contains("q=Avenida%20Paulista"))
        assertTrue(url.contains("lat=-23.5505"))
        assertTrue(url.contains("lon=-46.6333"))
        assertTrue(url.contains("location_bias_scale=1"))
        assertTrue(url.contains("zoom=15"))
        assertTrue(url.contains("bbox="))
        assertTrue(url.contains("lang=pt"))
        assertFalse(url.contains("-23.55051"))
        assertFalse(url.contains("osm_tag="))
    }

    @Test fun textoSemGpsNaoInventaPosicao() {
        val url = PhotonSearch.url("Praça da Sé", null, null, null, null)
        assertFalse(url.contains("lat="))
        assertFalse(url.contains("bbox="))
        assertFalse(url.contains("location_bias_scale="))
    }

    @Test fun categoriaOverpassUsaATagEAPosicaoArredondada() {
        val url = OverpassSearch.url(PlaceCategory.FUEL, nearLat, nearLon)
        assertTrue(url.startsWith("https://overpass-api.de/api/interpreter?data="))
        assertTrue(url.contains("fuel"))
        assertTrue(url.contains("around"))
        assertTrue(url.contains("-23.5505"))
        assertTrue(url.contains("-46.6333"))
        assertFalse(url.contains("-23.55051"))
        assertTrue(OverpassSearch.url(PlaceCategory.PARKING, nearLat, nearLon).contains("parking"))
        assertTrue(OverpassSearch.url(PlaceCategory.CHARGING, nearLat, nearLon).contains("charging_station"))
    }

    @Test fun overpassOrdenaOMaisPertoPrimeiro() = runBlocking {
        val body = """
            {"elements":[
              {"type":"node","lat":-23.70,"lon":-46.70,"tags":{"amenity":"fuel","name":"Longe","addr:city":"São Paulo"}},
              {"type":"way","center":{"lat":-23.5510,"lon":-46.6340},"tags":{"amenity":"fuel","name":"Perto","addr:street":"Rua A","addr:city":"São Paulo"}},
              {"type":"node","lat":-23.5520,"lon":-46.6330,"tags":{"amenity":"fuel","brand":"Shell"}}
            ]}
        """.trimIndent()
        val search = scripted { url, agent ->
            assertTrue(url.contains("overpass-api.de"))
            assertTrue(agent.contains("PainelLivre"))
            body
        }
        val result = search.search(PlaceQuery("posto de combustível", nearLat, nearLon, PlaceCategory.FUEL))
        assertEquals(PlaceSearchResult.Status.OK, result.status)
        assertEquals(listOf("Perto", "Shell", "Longe"), result.places.map { it.name })
        assertEquals("Rua A · São Paulo", result.places[0].address)
        assertTrue(PlaceRank.meters(nearLat, nearLon, result.places[0].lat, result.places[0].lon) <
            PlaceRank.meters(nearLat, nearLon, result.places[2].lat, result.places[2].lon))
    }

    @Test fun textoDoPhotonEReordenadoPelaDistancia() = runBlocking {
        val body = """
            {"features":[
              {"geometry":{"coordinates":[-46.90,-23.90]},"properties":{"name":"Longe","city":"São Paulo"}},
              {"geometry":{"coordinates":[-46.634,-23.551]},"properties":{"name":"Perto","city":"São Paulo"}}
            ]}
        """.trimIndent()
        val urls = mutableListOf<String>()
        val search = scripted { url, _ -> urls += url; body }
        val result = search.search(PlaceQuery("padaria", nearLat, nearLon))
        assertEquals(PlaceSearchResult.Status.OK, result.status)
        assertEquals(listOf("Perto", "Longe"), result.places.map { it.name })
        assertEquals(1, urls.size)
        assertTrue(urls.single().contains("photon.komoot.io"))
        assertTrue(urls.single().contains("bbox="))
    }

    @Test fun bboxVazioTentaDeNovoSemBbox() = runBlocking {
        val far = """{"features":[{"geometry":{"coordinates":[-43.2,-22.9]},"properties":{"name":"Santos Dumont","city":"Rio de Janeiro"}}]}"""
        val urls = mutableListOf<String>()
        val search = scripted { url, _ ->
            urls += url
            if (url.contains("bbox=")) """{"features":[]}""" else far
        }
        val result = search.search(PlaceQuery("aeroporto", nearLat, nearLon))
        assertEquals(PlaceSearchResult.Status.OK, result.status)
        assertEquals("Santos Dumont", result.places.single().name)
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("bbox="))
        assertFalse(urls[1].contains("bbox="))
    }

    @Test fun photonForaDoArCaiNoNominatim() = runBlocking {
        val urls = mutableListOf<String>()
        val search = scripted { url, agent ->
            urls += url
            if (url.contains("photon")) error("timeout")
            assertTrue(agent.contains("github.com/paulorochagodoi/painel-livre"))
            """[{"lat":"-23.551","lon":"-46.634","name":"Padaria","display_name":"Padaria, Rua A, São Paulo"}]"""
        }
        val result = search.search(PlaceQuery("padaria", nearLat, nearLon))
        assertEquals(PlaceSearchResult.Status.OK, result.status)
        assertEquals("Padaria", result.places.single().name)
        assertEquals("Rua A, São Paulo", result.places.single().address)
        assertTrue(urls.any { it.contains("nominatim.openstreetmap.org") })
        assertTrue(NominatimSearch.url("padaria", nearLat, nearLon).contains("lat=-23.5505"))
    }

    @Test fun listaVaziaNaoEFalha() = runBlocking {
        val search = scripted { _, _ -> """{"features":[]}""" }
        val result = search.search(PlaceQuery("lugar nenhum", nearLat, nearLon))
        // O bbox vazio dispara a segunda tentativa, também vazia: continua EMPTY, sem Nominatim.
        assertEquals(PlaceSearchResult.Status.EMPTY, result.status)
        assertTrue(result.places.isEmpty())
    }

    @Test fun photonENominatimForaSaoIndisponiveis() = runBlocking {
        val search = scripted { _, _ -> error("rede") }
        val result = search.search(PlaceQuery("padaria", nearLat, nearLon))
        assertEquals(PlaceSearchResult.Status.UNAVAILABLE, result.status)
        assertTrue(result.places.isEmpty())
    }

    @Test fun overpassRespeitaOIntervaloECaiNoPhoton() = runBlocking {
        var now = 5_000L
        var overpass = 0
        val search = OpenPlaceSearch(
            fetch = { url, _ ->
                if (url.contains("overpass")) {
                    overpass++
                    error("429")
                }
                """{"features":[{"geometry":{"coordinates":[-46.634,-23.551]},"properties":{"name":"Posto"}}]}"""
            },
            nowMs = { now },
            minOsmIntervalMs = 1_100,
        )
        val first = search.search(PlaceQuery("posto", nearLat, nearLon, PlaceCategory.FUEL))
        now = 5_400
        val second = search.search(PlaceQuery("posto", nearLat, nearLon, PlaceCategory.FUEL))
        assertEquals(1, overpass)
        assertEquals(PlaceSearchResult.Status.OK, first.status)
        assertEquals("Posto", second.places.single().name)
    }

    @Test fun categoriaSemGpsNaoChamaOverpass() = runBlocking {
        val urls = mutableListOf<String>()
        val search = scripted { url, _ ->
            urls += url
            """{"features":[{"geometry":{"coordinates":[-46.63,-23.55]},"properties":{"name":"Posto"}}]}"""
        }
        val result = search.search(PlaceQuery("posto de combustível", null, null, PlaceCategory.FUEL))
        assertEquals(PlaceSearchResult.Status.OK, result.status)
        assertTrue(urls.single().contains("photon"))
        assertTrue(urls.single().contains("osm_tag=amenity:fuel"))
        assertFalse(urls.single().contains("overpass"))
    }

    @Test fun bboxFicaEmVoltaDoPonto() {
        val box = bbox(-23.55, -46.63, 25.0).split(",")
        assertEquals(4, box.size)
        val minLon = box[0].toDouble(); val minLat = box[1].toDouble()
        val maxLon = box[2].toDouble(); val maxLat = box[3].toDouble()
        assertTrue(minLat < -23.55 && maxLat > -23.55)
        assertTrue(minLon < -46.63 && maxLon > -46.63)
        assertTrue(maxLat - minLat < 0.6)
    }

    @Test fun provedorHereEncaixaNaMesmaInterface() = runBlocking {
        val here = object : PlaceProvider {
            override val id = "here"
            override suspend fun search(query: PlaceQuery) = PlaceSearchResult(
                listOf(Place("Posto HERE", "Perto", query.nearLat ?: 0.0, query.nearLon ?: 0.0)),
                PlaceSearchResult.Status.OK,
            )
        }
        val open = scripted { _, _ -> error("aberto nao devia ser chamado") }
        val chosen = if (here.id == "here") here else open
        val result = chosen.search(PlaceQuery("posto", nearLat, nearLon, PlaceCategory.FUEL))
        assertEquals("open", open.id)
        assertEquals("here", chosen.id)
        assertEquals("Posto HERE", result.places.single().name)
        assertNull(OverpassSearch.parse("nao-json", "Posto"))
    }

    private fun scripted(fetch: suspend (String, String) -> String) =
        OpenPlaceSearch(fetch, nowMs = { 0L }, minOsmIntervalMs = 0L)
}
