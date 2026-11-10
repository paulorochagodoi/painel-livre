package br.painellivre.car

import br.painellivre.car.Autoservice.Reply
import br.painellivre.car.TelemetryCatalog.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Endereços alternativos e overrides da issue #6/#16. Só leitura. */
class TelemetryPlanTest {

    private val NO_DATA = "Result: Parcel(00000000 ffffd8e5   '........')"
    private fun word(v: Int) = "Result: Parcel(00000000 %08x   '........')".format(v)

    @Test fun sentinelsStayUnavailableIncludingTenths() {
        assertNull(TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, -10011))
        assertNull(TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, 0xFFFF))
        assertNull(TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, 1001))
        assertEquals(63.0, TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, 63)!!, 0.0)
        assertEquals(63.0, TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, 630)!!, 0.0)
        assertEquals(100.0, TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, 100)!!, 0.0)
        assertEquals(100.0, TelemetrySnapshot.decode(Kind.PERCENT_TENTHS, 1000)!!, 0.0)
    }

    @Test fun socFallsBackToBmsTenthsWhenPrimaryIsUnavailable() {
        val soc = TelemetryCatalog.byKey("soc")
        val plan = TelemetryPlan.probes(listOf(soc), Autoservice.Protocol.UNKNOWN, emptyMap())
        assertTrue(plan.size >= 2)
        assertEquals(soc.fid, plan.first().fid)
        assertTrue(plan.any { it.fid == 0x44700028 && it.kind == Kind.PERCENT_TENTHS && !it.primary })
        val replies = plan.map { probe ->
            when (probe.fid) {
                0x44700028 -> Autoservice.parse(word(630))
                else -> Autoservice.parse(NO_DATA)
            }
        }
        val reading = TelemetryPlan.assemble(listOf(soc), plan, replies).getValue("soc")
        assertEquals(63.0, reading.value!!, 0.0)
        assertTrue(reading.alternate)
        assertTrue(reading.debugLine().contains("endereço alternativo"))
        assertTrue(reading.debugLine().contains("0x44700028"))
    }

    @Test fun primaryWinsWhenItHasAValue() {
        val soc = TelemetryCatalog.byKey("soc")
        val plan = TelemetryPlan.probes(listOf(soc), Autoservice.Protocol.CANFD, emptyMap())
        val replies = plan.map { Autoservice.parse(if (it.primary) word(0x422c0000) else word(10)) }
        val reading = TelemetryPlan.assemble(listOf(soc), plan, replies).getValue("soc")
        assertEquals(43.0, reading.value!!, 0.0)
        assertFalse(reading.alternate)
    }

    @Test fun doorsHaveNoAlternateThatCouldFakeAnOpenDoor() {
        for (key in TelemetrySnapshot.OPENING_KEYS + listOf("lock", "charge_lid")) {
            val signal = TelemetryCatalog.byKey(key)
            assertTrue(key, signal.alternates.isEmpty())
            val plan = TelemetryPlan.probes(listOf(signal), Autoservice.Protocol.UNKNOWN, emptyMap())
            assertEquals(key, 1, plan.size)
        }
    }

    @Test fun overrideReplacesOnlyThePrimaryAndRejectsWrites() {
        val text = "soc=1014:0x4A505038:5, porta=1001:1:5, soc=1014:1:6, fuel_pct=1999:10:5, ev_range=1014:99:7"
        val parsed = TelemetryOverrides.parse(text)
        assertEquals(setOf("soc", "ev_range"), parsed.keys)
        assertEquals(5, parsed.getValue("soc").tx)
        assertEquals(0x4A505038, parsed.getValue("soc").fid)
        assertEquals(7, parsed.getValue("ev_range").tx)
        val soc = TelemetryCatalog.byKey("soc")
        val plan = TelemetryPlan.probes(listOf(soc), Autoservice.Protocol.UNKNOWN, emptyMap(), parsed)
        assertEquals(1, plan.size)
        assertEquals(5, plan.single().tx)
        assertTrue(Autoservice.isReadOnly(plan.single().command()))
        assertTrue(TelemetryOverrides.parse(null).isEmpty())
        assertTrue(TelemetryOverrides.parse("lixo").isEmpty())
    }

    @Test fun everyProbeInThePolledPlanIsARead() {
        val plan = TelemetryPlan.probes(TelemetryCatalog.POLLED, Autoservice.Protocol.UNKNOWN, emptyMap())
        assertTrue(plan.size > TelemetryCatalog.POLLED.size) // soc e autonomias ganham endereço extra
        for (probe in plan) {
            assertTrue(probe.key, probe.tx in setOf(5, 7))
            assertTrue(Autoservice.isReadOnly(probe.command()))
        }
        val batch = Autoservice.batch(plan.map { it.command() })
        assertFalse(batch.contains("autoservice 6 "))
    }
}
