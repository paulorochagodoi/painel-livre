package br.painellivre

import br.painellivre.nav.Maneuver
import br.painellivre.nav.NavParser
import org.junit.Assert.*
import org.junit.Test

/** Exemplos sintéticos PT-BR: não são capturas confirmadas no carro. */
class ExternalNavigationTest {
    @Test fun mapsDistanceStreetAndSummary() {
        val nav = NavParser.parse("300 m", "Vire à direita na Avenida Brasil", null,
            "18 min · 7,2 km · 14:35")!!
        assertEquals(Maneuver.RIGHT, nav.maneuver)
        assertEquals(300, nav.distanceMeters)
        assertEquals("Avenida Brasil", nav.street)
        assertEquals("14:35", nav.etaClock)
        assertEquals(1080, nav.remainingSeconds)
        assertEquals(7200, nav.remainingMeters)
    }

    @Test fun wazeRoundaboutWithExpandedSummary() {
        val nav = NavParser.parse("Em 800 m", "Na rotatória, pegue a segunda saída para Rua das Flores",
            "25 min • 12 km • 18:10", null)!!
        assertEquals(Maneuver.ROUNDABOUT, nav.maneuver)
        assertEquals(2, nav.roundaboutExit)
        assertEquals(800, nav.distanceMeters)
        assertEquals("Rua das Flores", nav.street)
        assertEquals("18:10", nav.etaClock)
        assertEquals(12000, nav.remainingMeters)
    }

    @Test fun mapsStreetOnlyKeepsDistanceWithoutInventingTurn() {
        val nav = NavParser.parse("150 m", "Rua São João", null, "8 min · 2 km · 09:45")!!
        assertEquals(Maneuver.UNKNOWN, nav.maneuver)
        assertEquals("Rua São João", nav.street)
        assertEquals(150, nav.distanceMeters)
        assertEquals(2000, nav.remainingMeters)
    }

    @Test fun summaryAndGpsStatusAreNotManeuvers() {
        assertNull(NavParser.parse("12 min · 5 km · 10:30", null, null, null))
        assertNull(NavParser.parse("Buscando GPS", "300 m", null, null))
        assertNull(NavParser.parse("Toque para abrir o Waze", null, null, null))
    }

    @Test fun semanticIconNamesMapButAppLogosDoNot() {
        assertEquals(Maneuver.RIGHT, NavParser.iconManeuver("ic_turn_right"))
        assertEquals(Maneuver.SLIGHT_LEFT, NavParser.iconManeuver("maneuver_turn_slight_left"))
        assertEquals(Maneuver.STRAIGHT, NavParser.iconManeuver("ic_straight"))
        assertEquals(Maneuver.UNKNOWN, NavParser.iconManeuver("waze_logo"))
        assertEquals(Maneuver.UNKNOWN, NavParser.iconManeuver(null))
    }
}
