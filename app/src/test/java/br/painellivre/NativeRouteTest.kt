package br.painellivre

import br.painellivre.nav.native.*
import br.painellivre.nav.Maneuver
import br.painellivre.cluster.PanelContent
import org.junit.Assert.*
import org.junit.Test

class NativeRouteTest {
    private val route = NativeRoute(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, .001), RoutePoint(.001, .001)), listOf(
        RouteStep(0, 111.2, 10.0, Maneuver.STRAIGHT, "Siga em frente", "Rua A", null),
        RouteStep(1, 111.2, 10.0, Maneuver.LEFT, "Vire à esquerda", "Rua B", null),
        RouteStep(2, 0.0, 0.0, Maneuver.DESTINATION, "Você chegou", "", null)))
    @Test fun nextTurnAndDistanceFollowGps() {
        val tracker = RouteTracker(route)
        val start = tracker.update(RoutePoint(0.0, 0.0))
        assertEquals(Maneuver.LEFT, start.nav.maneuver)
        assertEquals("Rua B", start.nav.street)
        assertEquals(111, start.nav.distanceMeters)
        val moved = tracker.update(RoutePoint(0.0, .0005))
        assertTrue(moved.remaining < start.remaining)
        assertEquals(56, moved.nav.distanceMeters)
        assertTrue(moved.nav.remainingSeconds!! < start.nav.remainingSeconds!!)
    }
    @Test fun advanceToDestinationAndArriveOnlyAtEnd() {
        val tracker = RouteTracker(route)
        assertFalse(tracker.update(RoutePoint(0.0, 0.0)).arrived)
        val t = RouteTracker(route)
        t.update(RoutePoint(0.0, .001))
        assertEquals(Maneuver.DESTINATION, t.update(RoutePoint(.0005, .001)).nav.maneuver)
        assertTrue(t.update(RoutePoint(.001, .001)).arrived)
    }
    @Test fun poorOrDeviatedFixDoesNotAdvanceProgress() {
        val tracker = RouteTracker(route)
        val first = tracker.update(RoutePoint(0.0, .0005))
        val bad = tracker.update(RoutePoint(.02, .02))
        assertTrue(bad.offRoute > 60)
        assertEquals(first.along, bad.along, .1)
        val backwards = tracker.update(RoutePoint(0.0, .0001))
        assertTrue(backwards.along >= first.along)
    }
    @Test fun geometryKeepsLongitudeLatitudeOrderAndRequestsSteps() {
        val url = OsrmRoute.url(RoutePoint(-23.0, -46.0), RoutePoint(-24.0, -47.0))
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("-46.0,-23.0;-47.0,-24.0"))
        assertTrue(url.contains("steps=true&geometries=geojson"))
    }
    @Test fun explicitNativeChoiceOverridesExperimentalExternalPanel() {
        assertNull(PanelContent.externalPackage("navigation", "native"))
        assertNull(PanelContent.externalPackage("navigation", "waze", nativeRoute = true))
        assertEquals(PanelContent.WAZE, PanelContent.externalPackage("navigation", "waze"))
        assertTrue(PanelContent.showOnRouteRequest("nav"))
        assertFalse(PanelContent.showOnRouteRequest("manual"))
    }
    @Test fun destinationInputsAndSharedLinks() {
        assertEquals("-23.5,-46.6", RouteDestination.query("geo:0,0?q=-23.5%2C-46.6"))
        assertEquals("Avenida Brasil", RouteDestination.query("google.navigation:q=Avenida%20Brasil&mode=d"))
        assertEquals("-23.5,-46.6", RouteDestination.query("geo:-23.5,-46.6"))
        assertEquals("Rua A", RouteDestination.query("https://www.google.com/maps/search/?api=1&query=Rua%20A"))
        assertEquals(RoutePoint(-23.5,-46.6), RouteDestination.point("-23.5,-46.6(Casa)"))
        assertEquals("Praça da Sé", RouteDestination.query("Praça da Sé"))
    }
    @Test fun unsupportedOrInvalidDestinationsAreRejected() {
        assertNull(RouteDestination.query("https://maps.app.goo.gl/short"))
        assertNull(RouteDestination.query("https://evil.test/?q=abc"))
        assertNull(RouteDestination.query("geo:0,0"))
        assertNull(RouteDestination.query("google.navigation:q="))
        assertNull(RouteDestination.query("geo:0,0?q=%XX"))
        assertNull(RouteDestination.point("91,0"))
        assertNull(RouteDestination.point("NaN,0"))
    }
    @Test fun osrmParsingHandlesManeuversAndRemovesDuplicateEndpoints() {
        val parsed = OsrmRoute.parse("""{"code":"Ok","routes":[{"legs":[{"steps":[
          {"distance":111,"duration":10,"name":"Rua A","maneuver":{"type":"depart"},"geometry":{"coordinates":[[0,0],[0.001,0]]}},
          {"distance":111,"duration":10,"name":"Rua B","maneuver":{"type":"roundabout","exit":2},"geometry":{"coordinates":[[0.001,0],[0.001,0.001]]}},
          {"distance":0,"duration":0,"name":"","maneuver":{"type":"arrive"},"geometry":{"coordinates":[[0.001,0.001],[0.001,0.001]]}}
        ]}]}]}""")
        assertEquals(3, parsed.points.size)
        assertEquals(listOf(0,1,2), parsed.steps.map { it.start })
        val nav = RouteTracker(parsed).update(RoutePoint(0.0, 0.0)).nav
        assertEquals(Maneuver.ROUNDABOUT, nav.maneuver)
        assertEquals(2, nav.roundaboutExit)
        assertEquals("Pegue a 2ª saída", nav.instruction)
    }
    @Test fun recalculationRequiresContinuousDeviationAndRateLimit() {
        val policy = RouteFixPolicy()
        policy.requested(0)
        assertFalse(policy.shouldRecalculate(61.0, 1_000, false))
        assertFalse(policy.shouldRecalculate(61.0, 9_000, false)) // ainda não passaram 30 s
        assertTrue(policy.shouldRecalculate(61.0, 30_000, false))
        assertFalse(policy.shouldRecalculate(61.0, 30_000, true))
        policy.requested(30_000)
        assertFalse(policy.shouldRecalculate(60.0, 40_000, false)) // volta à linha
        assertFalse(policy.shouldRecalculate(61.0, 59_000, false))
        assertFalse(policy.shouldRecalculate(61.0, 60_000, false)) // novo desvio ainda curto
        assertTrue(policy.shouldRecalculate(61.0, 67_000, false))
        policy.reset()
        assertFalse(policy.shouldRecalculate(61.0, 80_000, false))
    }
    @Test fun staleFutureAndInaccurateFixesAreRejected() {
        assertTrue(RouteFixPolicy.usable(60_000_000_000, 50f))
        assertTrue(RouteFixPolicy.usable(0, null))
        assertFalse(RouteFixPolicy.usable(-1, 10f))
        assertFalse(RouteFixPolicy.usable(60_000_000_001, 10f))
        assertFalse(RouteFixPolicy.usable(0, 51f))
        assertFalse(RouteFixPolicy.usable(0, Float.NaN))
    }
    @Test(expected = IllegalArgumentException::class) fun noRouteIsNotAnEmptySuccess() {
        OsrmRoute.parse("""{"code":"NoRoute"}""")
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinateIsRejected() {
        RoutePoint(Double.NaN, 0.0)
    }
}
