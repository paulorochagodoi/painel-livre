package br.painellivre

import br.painellivre.nav.Maneuver
import br.painellivre.nav.NavParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Os textos imitam o formato das notificações de navegação do Google Maps e do Waze
 * (título = distância, texto = instrução/rua, subText = resumo da rota).
 */
class NavParserTest {

    @Test fun googleMapsTurnRight() {
        val p = NavParser.parse("300 m", "Vire à direita na Rua Augusta", null, "12 min · 4,5 km · 14:35")!!
        assertEquals(Maneuver.RIGHT, p.maneuver)
        assertEquals(300, p.distanceMeters)
        assertEquals("300 m", p.distanceText)
        assertEquals("Rua Augusta", p.street)
        assertEquals("14:35", p.etaClock)
        assertEquals(720, p.remainingSeconds)
        assertEquals(4500, p.remainingMeters)
    }

    @Test fun kilometersWithComma() {
        val p = NavParser.parse("1,2 km", "Continue na Av. Paulista", null, "1 h 05 min · 62 km · 15:40")!!
        assertEquals(Maneuver.STRAIGHT, p.maneuver)
        assertEquals(1200, p.distanceMeters)
        assertEquals("Av. Paulista", p.street)
        assertEquals(3900, p.remainingSeconds)
        assertEquals(62000, p.remainingMeters)
    }

    @Test fun roundaboutWithExit() {
        val p = NavParser.parse("250 m", "Na rotatória, pegue a 2ª saída para Av. Brasil", null, null)!!
        assertEquals(Maneuver.ROUNDABOUT, p.maneuver)
        assertEquals(2, p.roundaboutExit)
        assertEquals("Av. Brasil", p.street)
    }

    @Test fun keepAndSlightAndSharp() {
        assertEquals(Maneuver.KEEP_LEFT, NavParser.parse("900 m", "Mantenha-se à esquerda para Av. 23 de Maio", null, null)!!.maneuver)
        assertEquals(Maneuver.SLIGHT_RIGHT, NavParser.parse("200 m", "Vire ligeiramente à direita", null, null)!!.maneuver)
        assertEquals(Maneuver.SHARP_LEFT, NavParser.parse("50 m", "Vire acentuadamente à esquerda", null, null)!!.maneuver)
    }

    @Test fun exitRamp() {
        val p = NavParser.parse("1,5 km", "Pegue a saída à direita para Rodovia dos Bandeirantes", null, null)!!
        assertEquals(Maneuver.EXIT_RIGHT, p.maneuver)
        assertEquals("Rodovia dos Bandeirantes", p.street)
    }

    @Test fun uTurnDefaultsLeft() {
        assertEquals(Maneuver.UTURN_LEFT, NavParser.parse("150 m", "Faça o retorno", null, null)!!.maneuver)
    }

    @Test fun destination() {
        assertEquals(Maneuver.DESTINATION, NavParser.parse("80 m", "O destino está à direita", null, null)!!.maneuver)
    }

    @Test fun wazeStyleDistanceInText() {
        val p = NavParser.parse("Rua da Consolação", "Em 400 m, vire à esquerda", null, null)!!
        assertEquals(Maneuver.LEFT, p.maneuver)
        assertEquals(400, p.distanceMeters)
        assertEquals("Rua da Consolação", p.street)
    }

    @Test fun summaryIsNotManeuverDistance() {
        // Sem subText, com o resumo dentro do texto: a distância da manobra é a do título.
        val p = NavParser.parse("700 m", "Vire à direita · 25 min · 18 km · 10:05", null, null)!!
        assertEquals(700, p.distanceMeters)
    }

    @Test fun englishAndImperial() {
        val p = NavParser.parse("500 ft", "Turn left onto Main St", null, "8 min · 2.1 mi · 2:05 PM")!!
        assertEquals(Maneuver.LEFT, p.maneuver)
        assertEquals(152, p.distanceMeters)
        assertEquals("Main St", p.street)
        assertEquals("14:05", p.etaClock)
    }

    @Test fun minutesAreNotMeters() {
        val p = NavParser.parse("Vire à direita", "25 min", null, null)
        assertNotNull(p)
        assertNull(p!!.distanceMeters)
    }

    @Test fun noiseIsIgnored() {
        assertNull(NavParser.parse("Google Maps", "Procurando GPS…", null, null))
        assertNull(NavParser.parse("Waze", "Toque para abrir", null, null))
    }

    @Test fun thousandsSeparatorMeters() {
        assertEquals(1200, NavParser.toMeters("1.200", "m"))
        assertEquals(1500, NavParser.toMeters("1,5", "km"))
    }
}
