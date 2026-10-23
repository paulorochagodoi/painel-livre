package br.painellivre.car

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class VehicleReadingsTest {
    @Test fun validZeroIsNotUnavailable() {
        val r = VehicleReadings { 0 }
        assertEquals(0.0, r.number("battery", 100.0)!!, 0.0)
        assertTrue(r.errors.isEmpty())
    }
    @Test fun invalidSentinelsAreUnavailable() {
        for (value in listOf(-1, 101, Double.NaN, Double.POSITIVE_INFINITY, null, "50")) {
            val r = VehicleReadings { value }
            assertNull(r.number("battery", 100.0))
            assertTrue(r.errors.single().contains("battery"))
        }
    }
    @Test fun permissionFailureKeepsOtherReadings() {
        val r = VehicleReadings { if (it == "battery") throw InvocationTargetException(SecurityException()) else 250 }
        assertNull(r.number("battery", 100.0))
        assertEquals(250.0, r.number("range", 3000.0)!!, 0.0)
        assertTrue(r.errors.single().contains("permissão BYD recusada"))
    }
    @Test fun missingMethodIsExplained() {
        val r = VehicleReadings { throw NoSuchMethodException() }
        assertNull(r.number("fuel", 100.0))
        assertTrue(r.errors.single().contains("método ausente"))
    }
    @Test fun sdkAbsentIsExplained() {
        assertEquals("SDK BYD não encontrado", VehicleReadings.failure(ClassNotFoundException()))
    }
    @Test fun errorsDoNotExposeExceptionMessage() {
        assertFalse(VehicleReadings.failure(IllegalStateException("segredo")).contains("segredo"))
    }
    @Test fun diagnosticMakesMissingFieldsAndDoorsVisible() {
        val s = VehicleData.Snapshot(null, null, null, null, null, "permissão recusada")
        assertTrue(s.isEmpty)
        assertTrue(s.diagnosticText().contains("Combustível: indisponível"))
        assertTrue(s.diagnosticText().contains("Portas/travas: não lidas pelo SDK"))
        assertTrue(s.diagnosticText().contains("permissão recusada"))
    }
    @Test fun partialSnapshotDoesNotHideValidBattery() {
        val s = VehicleData.Snapshot(0.0, null, null, null, null)
        assertFalse(s.isEmpty)
        assertTrue(s.diagnosticText().contains("Bateria: 0.0 %"))
        assertTrue(s.diagnosticText().contains("Autonomia elétrica: indisponível"))
    }
}
