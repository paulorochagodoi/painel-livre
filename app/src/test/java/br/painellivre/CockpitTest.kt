package br.painellivre

import br.painellivre.info.WeatherRepository
import br.painellivre.nav.Places
import br.painellivre.ui.cockpit.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CockpitTest {

    @Test fun photonResultsBecomePlaces() {
        // Formato da resposta do Photon (GeoJSON), reduzido.
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
        assertEquals("Avenida Pedro Álvares Cabral · Ibirapuera · São Paulo", list[0].address)
        assertEquals(-23.5874, list[0].lat, 1e-9)
        assertEquals(-46.6576, list[0].lon, 1e-9)
        assertEquals("Rua Augusta, 1500", list[1].name) // sem nome: usa rua e número
        assertEquals("São Paulo", list[1].address)
    }

    @Test fun themeFollowsTheClock() {
        fun at(h: Int, m: Int) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
        assertSame(Palette.DAY, Palette.forSetting("auto", at(12, 0)))
        assertSame(Palette.NIGHT, Palette.forSetting("auto", at(22, 0)))
        assertSame(Palette.NIGHT, Palette.forSetting("auto", at(5, 59)))
        assertSame(Palette.DAY, Palette.forSetting("auto", at(6, 0)))
        assertSame(Palette.NIGHT, Palette.forSetting("auto", at(18, 30)))
        assertSame(Palette.NIGHT, Palette.forSetting("night", at(12, 0)))
        assertSame(Palette.DAY, Palette.forSetting("day", at(23, 0)))
        assertTrue(Palette.NIGHT.dark)
        assertFalse(Palette.DAY.dark)
    }

    @Test fun weatherCodesInPortuguese() {
        assertEquals("Céu limpo", WeatherRepository.describe(0))
        assertEquals("Chuva", WeatherRepository.describe(61))
        assertEquals(WeatherRepository.Kind.RAIN, WeatherRepository.kindOf(80))
        assertEquals(WeatherRepository.Kind.STORM, WeatherRepository.kindOf(95))
        assertEquals(WeatherRepository.Kind.CLEAR, WeatherRepository.kindOf(1))
    }
}
