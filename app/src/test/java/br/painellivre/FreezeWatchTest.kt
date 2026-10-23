package br.painellivre

import br.painellivre.cluster.FreezeWatch
import br.painellivre.cluster.FreezeWatch.Action
import br.painellivre.cluster.YieldPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeWatchTest {

    private fun sample(now: Long, first: Long? = 100, lastFrame: Long = now, beat: Long = now, moving: Boolean = true) =
        FreezeWatch.Sample(now = now, createdAt = 0, firstFrameAt = first, lastFrameAt = lastFrame, mainBeatAt = beat, moving = moving)

    @Test fun relaunchOnceWithoutFirstFrame() {
        val w = FreezeWatch()
        assertEquals(Action.NONE, w.evaluate(sample(9_000, first = null)))
        assertEquals(Action.RELAUNCH, w.evaluate(sample(10_500, first = null)))
        assertEquals(Action.NONE, w.evaluate(sample(12_000, first = null)))
    }

    @Test fun parkedWithoutFramesIsNormal() {
        val w = FreezeWatch()
        assertEquals(Action.NONE, w.evaluate(sample(60_000, lastFrame = 1_000, moving = false)))
    }

    @Test fun escalatesWhileMovingAndFrozen() {
        val w = FreezeWatch()
        assertEquals(Action.NONE, w.evaluate(sample(5_000, lastFrame = 1_000)))
        assertEquals(Action.REPAINT, w.evaluate(sample(6_500, lastFrame = 1_000)))
        assertEquals(Action.NONE, w.evaluate(sample(7_000, lastFrame = 1_000))) // espera a resposta
        assertEquals(Action.REPAINT, w.evaluate(sample(8_000, lastFrame = 1_000)))
        assertEquals(Action.RECREATE_SURFACE, w.evaluate(sample(9_500, lastFrame = 1_000)))
        assertEquals(Action.NONE, w.evaluate(sample(11_000, lastFrame = 1_000)))
        // Muito tempo depois, ainda travado: recomeça a escada.
        assertEquals(Action.REPAINT, w.evaluate(sample(40_000, lastFrame = 1_000)))
    }

    @Test fun recoveryResetsTheLadder() {
        val w = FreezeWatch()
        assertEquals(Action.REPAINT, w.evaluate(sample(6_500, lastFrame = 1_000)))
        assertEquals(Action.NONE, w.evaluate(sample(8_000, lastFrame = 7_900))) // voltou a desenhar
        assertEquals(Action.REPAINT, w.evaluate(sample(14_000, lastFrame = 7_900)))
    }

    @Test fun mainThreadStallReportedOnce() {
        val w = FreezeWatch()
        assertEquals(Action.MAIN_STALLED, w.evaluate(sample(10_000, beat = 4_000)))
        assertEquals(Action.NONE, w.evaluate(sample(11_500, beat = 4_000)))
        assertEquals(Action.NONE, w.evaluate(sample(13_000, beat = 13_000)))
        assertEquals(Action.MAIN_STALLED, w.evaluate(sample(20_000, beat = 14_000)))
    }

    @Test fun budgetLimitsPerWindow() {
        val b = FreezeWatch.Budget(max = 3, windowMs = 600_000)
        assertTrue(b.tryTake(0)); assertTrue(b.tryTake(1)); assertTrue(b.tryTake(2))
        assertFalse(b.tryTake(3))
        assertTrue(b.tryTake(600_001))
    }

    @Test fun yieldAfterSecondTakeoverIn30s() {
        val p = YieldPolicy()
        assertTrue(p.onTakeover(0))          // primeira vez: retoma
        assertFalse(p.onTakeover(20_000))    // de novo em 20 s: cede
        assertTrue(p.yielded)
        assertFalse(p.onTakeover(100_000))   // continua cedido até o motorista pedir
        p.reset()
        assertTrue(p.onTakeover(200_000))
    }

    @Test fun takeoversFarApartDoNotYield() {
        val p = YieldPolicy()
        assertTrue(p.onTakeover(0))
        assertTrue(p.onTakeover(31_000))
        assertFalse(p.yielded)
    }
}
