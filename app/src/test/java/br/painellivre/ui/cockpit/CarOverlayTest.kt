package br.painellivre.ui.cockpit

import br.painellivre.car.*
import org.junit.Assert.*
import org.junit.Test

class CarOverlayTest {
    private fun snapshot(vararg values: Pair<String, Int>) = TelemetrySnapshot(TelemetrySnapshot.Status.OK,
        readings = values.associate { (key, word) -> TelemetryCatalog.byKey(key).let {
            key to TelemetrySnapshot.reading(it, it.fid, Autoservice.Reply.Value(word))
        } })
    @Test fun unknownAndLockOnlyUseBase() {
        assertFalse(CarOverlay.state(null).highlight)
        assertFalse(CarOverlay.state(snapshot("lock" to 2)).highlight)
        assertFalse(CarOverlay.state(snapshot("door_fl" to -10011, "tyre_p_fl" to 65535)).highlight)
        assertEquals(CarOverlay.Tyre.UNKNOWN, CarOverlay.state(null).tyres["fl"])
    }
    @Test fun eachOpeningMapsOnlyToItsOverlay() {
        CarOverlay.openings.keys.forEach { key ->
            val state = CarOverlay.state(snapshot(key to 1))
            assertTrue(key, state.highlight)
            assertEquals(setOf(key), state.open)
            assertFalse(CarOverlay.state(snapshot(key to 0)).highlight)
        }
    }
    @Test fun eachWindowAndRoofMapsIndependently() {
        CarOverlay.windows.keys.forEach { key ->
            val state = CarOverlay.state(snapshot(key to 1))
            assertTrue(state.highlight); assertEquals(setOf(key),state.windows)
            assertFalse(CarOverlay.state(snapshot(key to 0)).highlight)
        }
    }
    @Test fun chargeLidAndBmsTriggerHighlight() {
        assertTrue(CarOverlay.state(snapshot("charge_lid" to 1)).lid)
        assertTrue(CarOverlay.state(snapshot("bms" to 1)).charging)
        assertTrue(CarOverlay.state(snapshot("charge_lid" to 1)).highlight)
        assertFalse(CarOverlay.state(snapshot("bms" to 2, "gun" to 2)).highlight)
    }
    @Test fun tyreBoundsAndPositionsAreIndependent() {
        CarOverlay.tyres.keys.forEach { key ->
            for (p in listOf(180,219,311,400)) {
                val s=CarOverlay.state(snapshot("tyre_p_$key" to p))
                assertTrue(s.highlight); assertEquals(CarOverlay.Tyre.ALERT,s.tyres[key])
                assertEquals(p.toDouble(),s.pressures[key])
                assertEquals(1,s.tyres.values.count { it == CarOverlay.Tyre.ALERT })
            }
            for(p in listOf(220,250,310)) assertFalse(CarOverlay.state(snapshot("tyre_p_$key" to p)).highlight)
        }
    }
    @Test fun allCoordinatesAreWithinImage() {
        (CarOverlay.openings.values+CarOverlay.windows.values+CarOverlay.tyres.values+listOf(CarOverlay.charger,CarOverlay.lock,CarOverlay.baseLock)).forEach {
            assertTrue(it.x in 0f..1f && it.y in 0f..1f && it.x2 in 0f..1f && it.y2 in 0f..1f)
        }
        // Frente à esquerda: lado esquerdo do veículo fica embaixo na imagem.
        assertTrue(CarOverlay.tyres.getValue("fl").y > CarOverlay.tyres.getValue("fr").y)
        assertTrue(CarOverlay.charger.x > .5f && CarOverlay.charger.y < .5f)
    }
}
