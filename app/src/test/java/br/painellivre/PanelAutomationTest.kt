package br.painellivre

import br.painellivre.cluster.PanelAutomation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanelAutomationTest {
    private class Fixture(scope: TestScope, var mode: String = "nav", var delay: Int = 20) {
        var active = false
        val events = mutableListOf<String>()
        val automation = PanelAutomation(scope, { mode }, { delay }, { active },
            { events += "preparar" },
            { events += "mostrar"; active = true },
            { events += "restaurar"; active = false })
    }

    @Test fun navOnlyShowsWhenRouteStarts() = runTest {
        val f = Fixture(this)
        f.automation.onStart()
        f.automation.onNavigation(false)
        assertTrue(f.events.isEmpty())
        f.automation.onNavigation(true)
        assertEquals(listOf("preparar", "mostrar"), f.events)
    }

    @Test fun activePanelIsNotActivatedAgain() = runTest {
        val f = Fixture(this).apply { active = true }
        f.automation.onNavigation(true)
        assertEquals(listOf("preparar"), f.events)
    }

    @Test fun restoresExactlyAtConfiguredDeadline() = runTest {
        val f = Fixture(this).apply { active = true }
        f.automation.onNavigation(false)
        runCurrent()
        advanceTimeBy(19_999)
        assertTrue(f.events.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("restaurar"), f.events)
    }

    @Test fun newRouteCancelsPendingRestore() = runTest {
        val f = Fixture(this).apply { active = true }
        f.automation.onNavigation(false)
        runCurrent()
        advanceTimeBy(10_000)
        f.automation.onNavigation(true)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf("preparar"), f.events)
        assertTrue(f.active)
    }

    @Test fun laterRouteGetsItsOwnDeadline() = runTest {
        val f = Fixture(this).apply { active = true }
        f.automation.onNavigation(false)
        runCurrent()
        advanceTimeBy(10_000)
        f.automation.onNavigation(true)
        f.automation.onNavigation(false)
        runCurrent()
        advanceTimeBy(19_999)
        assertTrue(f.active)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(f.active)
        assertEquals(listOf("preparar", "restaurar"), f.events)
    }

    @Test fun alwaysShowsOnStartAndIgnoresRouteEnd() = runTest {
        val f = Fixture(this, "always")
        f.automation.onStart()
        f.automation.onNavigation(true)
        f.automation.onNavigation(false)
        advanceTimeBy(60_000)
        assertEquals(listOf("mostrar"), f.events)
    }

    @Test fun manualNeverActsAutomatically() = runTest {
        val f = Fixture(this, "manual").apply { active = true }
        f.automation.onStart()
        f.automation.onNavigation(true)
        f.automation.onNavigation(false)
        advanceTimeBy(60_000)
        assertTrue(f.events.isEmpty())
    }

    @Test fun explicitRestoreCancelsTimer() = runTest {
        val f = Fixture(this).apply { active = true }
        f.automation.onNavigation(false)
        runCurrent()
        f.automation.cancelRestore()
        advanceTimeBy(60_000)
        assertTrue(f.events.isEmpty())
    }

    @Test fun zeroDelayRestoresWithoutWaiting() = runTest {
        val f = Fixture(this, delay = 0).apply { active = true }
        f.automation.onNavigation(false)
        runCurrent()
        assertEquals(listOf("restaurar"), f.events)
    }

    @Test fun delayIsReadWhenTimerStarts() = runTest {
        val f = Fixture(this, delay = 600).apply { active = true }
        f.automation.onNavigation(false)
        runCurrent()
        f.delay = 0
        advanceTimeBy(599_999)
        assertTrue(f.active)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(f.active)
    }
    @Test fun timedRestoreMustNotCancelItselfBeforeFinishing() = runTest {
        var finished = false
        lateinit var automation: PanelAutomation
        automation = PanelAutomation(this, { "nav" }, { 0 }, { true }, {}, {}, {
            // O serviço cancela o temporizador ao entrar em restore(), antes da pausa 18 → 0.
            automation.cancelRestore()
            kotlinx.coroutines.delay(1_000)
            finished = true
        })
        automation.onNavigation(false)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("A restauração precisa chegar ao fim", finished)
    }

}
