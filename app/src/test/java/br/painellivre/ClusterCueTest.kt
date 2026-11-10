package br.painellivre

import br.painellivre.cluster.ClusterCue
import br.painellivre.nav.Maneuver
import br.painellivre.nav.NavParser
import br.painellivre.nav.NavRepository
import br.painellivre.nav.NavState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O cartão que o ClusterActivity desenha a partir da notificação (issue #24). */
class ClusterCueTest {

    @Test fun wazeNotificationBecomesArrowDistanceAndNextManeuver() {
        val parsed = NavParser.parse("300 m", "Vire à direita na Rua Augusta", null, "12 min · 4,5 km · 14:35")!!
        val card = ClusterCue.from(NavState(true, NavRepository.PKG_WAZE, parsed, null, false, 1L))
        assertTrue(card.visible)
        assertEquals(Maneuver.RIGHT, card.maneuver)
        assertEquals("300 m", card.distance)
        assertTrue(card.showDistance)
        assertEquals("Rua Augusta", card.street)
        assertEquals("Waze · Vire à direita", ClusterCue.instructionLine(card))
        assertTrue(card.showEta)
        assertEquals("14:35", card.etaClock)
        assertEquals("12 min", card.etaTime)
        assertEquals("4,5 km", card.etaDistance)
        assertFalse(card.simulated)
    }

    @Test fun mapsStreetWithoutVerbStillShowsDistance() {
        val parsed = NavParser.parse("400 m", "Rua Augusta", null, null)!!
        val card = ClusterCue.from(NavState(true, NavRepository.PKG_MAPS, parsed, null, false, 1L))
        assertTrue(card.visible)
        assertEquals(Maneuver.UNKNOWN, card.maneuver)
        assertEquals("400 m", card.distance)
        assertEquals("Rua Augusta", card.street)
        assertEquals("Google Maps", ClusterCue.instructionLine(card))
        assertFalse(card.showEta)
    }

    @Test fun idleNavigationHidesTheCard() {
        val card = ClusterCue.from(NavState())
        assertFalse(card.visible)
        assertEquals("", ClusterCue.instructionLine(card))
    }
}
