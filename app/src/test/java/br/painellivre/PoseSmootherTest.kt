package br.painellivre

import br.painellivre.cluster.PoseSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseSmootherTest {
    private val lat0 = -23.5614
    private val lon0 = -46.6565

    private fun dist(a: PoseSmoother.Pose, lat: Double, lon: Double) =
        PoseSmoother.distanceMeters(a.lat, a.lon, lat, lon)

    @Test fun noFixNoPose() {
        assertNull(PoseSmoother().poseAt(0))
    }

    @Test fun parkedCarDoesNotMove() {
        val s = PoseSmoother()
        s.onFix(lat0, lon0, 90.0, 0.0, 1_000)
        val p = s.poseAt(3_000)!!
        assertEquals(0.0, dist(p, lat0, lon0), 0.01)
        assertFalse(s.isAnimating(3_000))
    }

    @Test fun parkedGpsJitterIgnored() {
        val s = PoseSmoother()
        s.onFix(lat0, lon0, 90.0, 0.0, 1_000)
        val (lat, lon) = PoseSmoother.project(lat0, lon0, 45.0, 4.0) // "passeio" de 4 m parado
        s.onFix(lat, lon, 90.0, 0.2, 2_000)
        assertEquals(0.0, dist(s.poseAt(2_000)!!, lat0, lon0), 0.01)
        assertFalse(s.isAnimating(2_000))
    }

    @Test fun coastsForwardBetweenFixes() {
        val s = PoseSmoother()
        s.onFix(lat0, lon0, 0.0, 10.0, 1_000) // 10 m/s para o norte
        val p = s.poseAt(1_500)!!
        assertEquals(5.0, dist(p, lat0, lon0), 0.05)
        assertTrue(p.lat > lat0)
        assertTrue(s.isAnimating(1_500))
    }

    @Test fun coastIsLimited() {
        val s = PoseSmoother(maxCoastMs = 1_500)
        s.onFix(lat0, lon0, 0.0, 10.0, 1_000)
        assertEquals(15.0, dist(s.poseAt(10_000)!!, lat0, lon0), 0.05)
        assertFalse(s.isAnimating(10_000))
    }

    @Test fun newFixHasNoJumpAndConverges() {
        val s = PoseSmoother(blendMs = 600)
        s.onFix(lat0, lon0, 0.0, 10.0, 1_000)
        val before = s.poseAt(2_000)!! // previsto: 10 m ao norte
        // GPS diz que o carro está 3 m atrás do previsto.
        val (lat1, lon1) = PoseSmoother.project(lat0, lon0, 0.0, 7.0)
        s.onFix(lat1, lon1, 0.0, 10.0, 2_000)
        val after = s.poseAt(2_000)!!
        assertEquals(0.0, PoseSmoother.distanceMeters(before.lat, before.lon, after.lat, after.lon), 0.01)
        // Depois do blend, segue a previsão a partir do fix novo.
        val (latExp, lonExp) = PoseSmoother.project(lat1, lon1, 0.0, 7.0)
        assertEquals(0.0, dist(s.poseAt(2_700)!!, latExp, lonExp), 0.05)
    }

    @Test fun bigJumpTeleports() {
        val s = PoseSmoother(teleportMeters = 150.0)
        s.onFix(lat0, lon0, 0.0, 10.0, 1_000)
        val (lat1, lon1) = PoseSmoother.project(lat0, lon0, 90.0, 500.0)
        s.onFix(lat1, lon1, 90.0, 10.0, 2_000)
        assertEquals(0.0, dist(s.poseAt(2_000)!!, lat1, lon1), 0.01)
    }

    @Test fun bearingBlendsTheShortWay() {
        val s = PoseSmoother(blendMs = 600)
        s.onFix(lat0, lon0, 350.0, 10.0, 1_000)
        s.onFix(lat0, lon0, 10.0, 10.0, 1_001)
        val mid = s.poseAt(1_301)!!.bearing // metade do blend
        assertTrue("rumo $mid deveria passar perto de 0°, não de 180°", mid > 340.0 || mid < 20.0)
        assertEquals(10.0, s.poseAt(1_700)!!.bearing, 0.001)
    }

    @Test fun angleHelpers() {
        assertEquals(20.0, PoseSmoother.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, PoseSmoother.angleDiff(10.0, 350.0), 1e-9)
        assertEquals(180.0, PoseSmoother.angleDiff(0.0, 180.0), 1e-9)
        assertEquals(350.0, PoseSmoother.normalize(-10.0), 1e-9)
    }
}
