package br.painellivre

import br.painellivre.nav.Maneuver
import br.painellivre.nav.NavParser
import org.junit.Assert.*
import org.junit.Test

/** Exemplos de NavSimulator e NavParserTest; são imitações, não capturas validadas no carro. */
class NavRegressionTest {
    @Test fun mapsCompleteNotificationFromSimulator() {
        val p = NavParser.parse("400 m", "Vire à direita na Rua Augusta", null, "18 min · 7,4 km · 14:35")!!
        assertEquals(Maneuver.RIGHT, p.maneuver)
        assertEquals(400, p.distanceMeters)
        assertEquals("400 m", p.distanceText)
        assertEquals("Vire à direita na Rua Augusta", p.instruction)
        assertEquals("Rua Augusta", p.street)
        assertEquals("14:35", p.etaClock)
        assertEquals(1080, p.remainingSeconds)
        assertEquals(7400, p.remainingMeters)
        assertEquals("7,4 km", p.remainingDistanceText)
        assertNull(p.roundaboutExit)
    }

    @Test fun wazeStreetTitleWithFullSummary() {
        val p = NavParser.parse("Rua da Consolação", "Em 400 m, vire à esquerda", null, "12 min · 4,5 km · 14:35")!!
        assertEquals(Maneuver.LEFT, p.maneuver)
        assertEquals(400, p.distanceMeters)
        assertEquals("Rua da Consolação", p.street)
        assertEquals("Em 400 m, vire à esquerda", p.instruction)
        assertEquals(720, p.remainingSeconds)
        assertEquals(4500, p.remainingMeters)
        assertEquals("14:35", p.etaClock)
    }

    @Test fun expandedTextWorksWithoutCompactText() {
        val p = NavParser.parse("300 m", null, "Vire à direita na Rua Augusta", null)!!
        assertEquals(Maneuver.RIGHT, p.maneuver)
        assertEquals("Rua Augusta", p.street)
        assertEquals(300, p.distanceMeters)
    }

    @Test fun duplicatedExpandedTextDoesNotAlterResult() {
        val text = "Vire à direita na Rua Augusta"
        assertEquals(NavParser.parse("300 m", text, null, null),
            NavParser.parse("300 m", text, text, null))
    }

    @Test fun titleDistanceTakesPrecedenceOverExpandedText() {
        val p = NavParser.parse("300 m", "Vire à direita na Rua Augusta", "Vire à direita em 400 m", null)!!
        assertEquals(300, p.distanceMeters)
    }

    @Test fun summaryInTextProvidesRouteTotals() {
        val p = NavParser.parse("700 m", "Vire à direita · 25 min · 18 km · 10:05", null, null)!!
        assertEquals(700, p.distanceMeters)
        assertEquals(1500, p.remainingSeconds)
        assertEquals(18000, p.remainingMeters)
        assertEquals("10:05", p.etaClock)
    }

    @Test fun summaryAloneDoesNotStartRoute() {
        assertNull(NavParser.parse("12 min · 4,5 km · 14:35", null, null, null))
    }

    @Test fun arrivalWithoutDistanceIsRecognized() {
        val p = NavParser.parse("Você chegou ao destino", null, null, null)!!
        assertEquals(Maneuver.DESTINATION, p.maneuver)
        assertNull(p.distanceMeters)
        assertNull(p.remainingSeconds)
        assertNull(p.etaClock)
    }

    @Test fun simulatorDestinationKeepsSummary() {
        val p = NavParser.parse("80 m", "O destino está à direita", null, "1 min · 80 m · 14:38")!!
        assertEquals(Maneuver.DESTINATION, p.maneuver)
        assertEquals(80, p.distanceMeters)
        assertEquals(60, p.remainingSeconds)
        assertEquals(80, p.remainingMeters)
    }

    @Test fun unicodeSpacesAndAccentsAreNormalized() {
        val p = NavParser.parse(" 1,2\u00a0km ", " Vire\nà direita na Rua Augusta ", null, "1 h 05 min • 62 km • 15:40")!!
        assertEquals(1200, p.distanceMeters)
        assertEquals("1,2 km", p.distanceText)
        assertEquals(Maneuver.RIGHT, p.maneuver)
        assertEquals("Rua Augusta", p.street)
        assertEquals(3900, p.remainingSeconds)
    }

    @Test fun emptyAndUnrelatedNotificationsAreIgnored() {
        assertNull(NavParser.parse(null, null, null, null))
        assertNull(NavParser.parse(" \n", "", null, null))
        assertNull(NavParser.parse("Google Maps", "Toque para abrir", null, null))
        assertNull(NavParser.parse("Waze", "Procurando GPS…", null, null))
        assertNull(NavParser.parse("Google Maps", "running in the background", null, null))
    }

    @Test fun englishSummaryConvertsImperialAndMidnight() {
        val p = NavParser.parse("500 ft", "Turn left onto Main St", null, "8 min · 2.1 mi · 12:05 AM")!!
        assertEquals(152, p.distanceMeters)
        assertEquals(3379, p.remainingMeters)
        assertEquals(480, p.remainingSeconds)
        assertEquals("00:05", p.etaClock)
        assertEquals("Main St", p.street)
    }

    @Test fun roundaboutExitDoesNotLeakIntoNextTurn() {
        assertEquals(2, NavParser.parse("250 m", "Na rotatória, pegue a 2ª saída para Av. Paulista", null, null)!!.roundaboutExit)
        assertNull(NavParser.parse("400 m", "Vire à direita na Rua Augusta", null, null)!!.roundaboutExit)
    }
}
