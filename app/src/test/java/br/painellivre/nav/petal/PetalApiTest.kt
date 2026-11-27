package br.painellivre.nav.petal

import br.painellivre.nav.Maneuver
import br.painellivre.nav.native.RoutePoint
import br.painellivre.nav.native.RouteTracker
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Respostas de exemplo no formato da documentação da Huawei (Site Kit e Directions REST). */
class PetalApiTest {

    private val searchSample = """
        {"returnCode":"0","returnDesc":"OK","totalCount":3,"sites":[
          {"siteId":"1","name":"Shopping ABC","formatAddress":"Av. Pereira Barreto, 42, Santo André - SP, Brasil",
           "address":{"country":"Brasil","countryCode":"BR","thoroughfare":"Avenida Pereira Barreto","streetNumber":"42",
                      "subLocality":"Paraíso","locality":"Santo André","adminArea":"São Paulo"},
           "location":{"lat":-23.6629,"lng":-46.5383},"poi":{"hwPoiTypes":["SHOPPING_CENTRE"]}},
          {"siteId":"2","formatAddress":"Rua Sem Nome, São Caetano do Sul",
           "address":{"thoroughfare":"Rua Manoel Coelho","streetNumber":"600","locality":"São Caetano do Sul"},
           "location":{"lat":-23.6229,"lng":-46.5548}},
          {"siteId":"3","name":"Sem coordenada","formatAddress":"x"},
          {"siteId":"4","name":"Shopping ABC","formatAddress":"duplicado",
           "address":{"thoroughfare":"Avenida Pereira Barreto","streetNumber":"42","subLocality":"Paraíso",
                      "locality":"Santo André","adminArea":"São Paulo"},
           "location":{"lat":-23.6629,"lng":-46.5383}}
        ]}""".trimIndent()

    @Test fun searchParsesNameAddressAndCoordinates() {
        val list = PetalApi.parseSearch(searchSample)
        assertEquals(2, list.size)
        val shop = list[0]
        assertEquals("Shopping ABC", shop.name)
        assertEquals("Avenida Pereira Barreto, 42 · Paraíso · Santo André · São Paulo", shop.address)
        assertEquals(-23.6629, shop.lat, 1e-9)
        assertEquals(-46.5383, shop.lon, 1e-9)
        // Sem "name": usa rua e número; o resto vai para o endereço.
        assertEquals("Rua Manoel Coelho, 600", list[1].name)
        assertEquals("São Caetano do Sul", list[1].address)
    }

    @Test fun searchEmptyAndErrorCodes() {
        assertTrue(PetalApi.parseSearch("""{"returnCode":"0","totalCount":0,"sites":[]}""").isEmpty())
        assertTrue(PetalApi.parseSearch("""{"returnCode":"0"}""").isEmpty())
        val e = assertThrows(PetalApi.PetalException::class.java) {
            PetalApi.parseSearch("""{"returnCode":"010010","returnDesc":"INVALID_API_KEY"}""")
        }
        assertEquals("010010", e.code)
        assertFalse("a mensagem não deve levar a chave", e.message!!.contains("key="))
    }

    @Test fun searchIgnoresInvalidCoordinates() {
        val body = """{"returnCode":"0","sites":[{"name":"A","location":{"lat":123,"lng":10}},
            {"name":"B","location":{"lat":"x","lng":10}},{"name":"C","location":{"lat":-23.5,"lng":-46.6}}]}"""
        assertEquals(listOf("C"), PetalApi.parseSearch(body).map { it.name })
    }

    @Test fun searchBodyUsesPortugueseAndBias() {
        val o = JSONObject(PetalApi.searchBody("  padaria ", RoutePoint(-23.6, -46.5), radius = 999_999))
        assertEquals("padaria", o.getString("query"))
        assertEquals("pt", o.getString("language"))
        assertEquals(-23.6, o.getJSONObject("location").getDouble("lat"), 0.0)
        assertEquals(-46.5, o.getJSONObject("location").getDouble("lng"), 0.0)
        assertEquals(50_000, o.getInt("radius"))
        val noGps = JSONObject(PetalApi.searchBody("padaria", null))
        assertFalse(noGps.has("location")); assertFalse(noGps.has("radius"))
    }

    @Test fun keyIsUrlEncoded() {
        assertEquals("ABC%2FDFG%2B", PetalApi.encodeKey("ABC/DFG+"))
        assertTrue(PetalApi.searchUrl("ABC/DFG+").endsWith("searchByText?key=ABC%2FDFG%2B"))
        assertTrue(PetalApi.drivingUrl(" k ").endsWith("routeService/driving?key=k"))
    }

    // Formato da Directions API: routes[].paths[] com duration, durationInTraffic, distance e steps.
    private val routeSample = """
        {"returnCode":"0","returnDesc":"OK","routes":[{"paths":[{
          "distance":300.0,"duration":60.0,"durationInTraffic":120.0,
          "steps":[
            {"distance":111.2,"duration":20.0,"action":"turn-left","roadName":"Rua A",
             "polyline":[{"lat":0.0,"lng":0.0},{"lat":0.0,"lng":0.001}]},
            {"distance":111.2,"duration":20.0,"action":"ROUNDABOUT_RIGHT","roadName":"Rua B",
             "polyline":[{"lat":0.0,"lng":0.001},{"lat":0.001,"lng":0.001}]},
            {"distance":77.6,"duration":20.0,"action":"end","roadName":"Rua C",
             "polyline":[{"lat":0.001,"lng":0.001},{"lat":0.001,"lng":0.0017}]}
          ]}]}]}""".trimIndent()

    @Test fun drivingParsesDistanceDurationsAndTraffic() {
        val r = PetalApi.parseDriving(routeSample)
        assertEquals(300.0, r.distanceMeters, 0.0)
        assertEquals(60.0, r.durationSec, 0.0)
        assertEquals(120.0, r.trafficSec!!, 0.0)
        assertEquals(120.0, r.etaSec, 0.0)
        // Pontos sem repetição na emenda dos trechos.
        assertEquals(4, r.route.points.size)
        // A ação do trecho anterior abre o trecho seguinte; o último é o destino.
        assertEquals(listOf(Maneuver.STRAIGHT, Maneuver.LEFT, Maneuver.ROUNDABOUT, Maneuver.DESTINATION),
            r.route.steps.map { it.maneuver })
        assertEquals(listOf("Rua A", "Rua B", "Rua C", ""), r.route.steps.map { it.street })
        // O ETA dos trechos fica escalado pelo trânsito (120/60 = 2x).
        assertEquals(120.0, r.route.steps.sumOf { it.seconds }, 1e-9)
    }

    @Test fun drivingWithoutTrafficKeepsNormalTime() {
        val body = routeSample.replace("\"durationInTraffic\":120.0,", "\"durationInTraffic\":0,")
        val r = PetalApi.parseDriving(body)
        assertNull(r.trafficSec)
        assertEquals(60.0, r.etaSec, 0.0)
        assertEquals(60.0, r.route.steps.sumOf { it.seconds }, 1e-9)
    }

    @Test fun drivingRouteWorksWithTheExistingTracker() {
        val tracker = RouteTracker(PetalApi.parseDriving(routeSample).route)
        val start = tracker.update(RoutePoint(0.0, 0.0))
        assertEquals(Maneuver.LEFT, start.nav.maneuver)
        assertEquals("Rua B", start.nav.street)
        assertEquals(111, start.nav.distanceMeters)
        assertEquals(120, start.nav.remainingSeconds)
    }

    @Test fun drivingErrors() {
        assertThrows(PetalApi.PetalException::class.java) {
            PetalApi.parseDriving("""{"returnCode":"010001","returnDesc":"NO_PERMISSION"}""")
        }
        assertThrows(PetalApi.PetalException::class.java) { PetalApi.parseDriving("""{"returnCode":"0","routes":[]}""") }
        assertThrows(IllegalArgumentException::class.java) {
            PetalApi.parseDriving("""{"returnCode":"0","routes":[{"paths":[{"duration":1,"steps":[]}]}]}""")
        }
    }

    @Test fun drivingBodyHasOriginAndDestination() {
        val o = JSONObject(PetalApi.drivingBody(RoutePoint(-23.1, -46.1), RoutePoint(-23.2, -46.2)))
        assertEquals(-46.1, o.getJSONObject("origin").getDouble("lng"), 0.0)
        assertEquals(-23.2, o.getJSONObject("destination").getDouble("lat"), 0.0)
    }

    @Test fun maneuverMappingAcceptsBothSpellings() {
        assertEquals(Maneuver.LEFT, PetalApi.maneuver("TURN_LEFT"))
        assertEquals(Maneuver.LEFT, PetalApi.maneuver("turn-left"))
        assertEquals(Maneuver.SHARP_RIGHT, PetalApi.maneuver("turn-sharp-right"))
        assertEquals(Maneuver.SLIGHT_LEFT, PetalApi.maneuver("TURN_SLIGHT_LEFT"))
        assertEquals(Maneuver.UTURN_RIGHT, PetalApi.maneuver("uturn-right"))
        assertEquals(Maneuver.UTURN_LEFT, PetalApi.maneuver("UTURN_LEFT"))
        assertEquals(Maneuver.ROUNDABOUT, PetalApi.maneuver("roundabout-left"))
        assertEquals(Maneuver.EXIT_RIGHT, PetalApi.maneuver("ramp-right"))
        assertEquals(Maneuver.KEEP_LEFT, PetalApi.maneuver("FORK_LEFT"))
        assertEquals(Maneuver.DESTINATION, PetalApi.maneuver("end"))
        assertEquals(Maneuver.MERGE, PetalApi.maneuver("merge"))
        assertEquals(Maneuver.STRAIGHT, PetalApi.maneuver("straight"))
        assertEquals(Maneuver.STRAIGHT, PetalApi.maneuver("ferry"))
        assertEquals(Maneuver.STRAIGHT, PetalApi.maneuver(null))
    }

    @Test fun rasterStyleIsValidMapLibreStyle() {
        val o = JSONObject(PetalApi.rasterStyle("ABC/DFG+"))
        assertEquals(8, o.getInt("version"))
        val tiles = o.getJSONObject("sources").getJSONObject("petal").getJSONArray("tiles").getString(0)
        assertTrue(tiles.startsWith("https://mapapi.cloud.huawei.com/mapApi/v1/mapService/getTile?x={x}&y={y}&z={z}"))
        assertTrue(tiles.endsWith("key=ABC%2FDFG%2B"))
        assertEquals("raster", o.getJSONArray("layers").getJSONObject(1).getString("type"))
    }

    @Test fun apiKeyNeverGoesToTheRepository() {
        val root = listOf(File("."), File("..")).first { File(it, ".gitignore").isFile && File(it, "settings.gradle.kts").isFile }
        val ignore = File(root, ".gitignore").readLines().map { it.trim() }
        assertTrue("local.properties" in ignore)
        assertTrue("petal_api_key.txt" in ignore)
        // A chave só entra pelo local.properties/ambiente; o build.gradle.kts não traz valor fixo.
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("getProperty(\"petal.apiKey\")"))
    }
}
