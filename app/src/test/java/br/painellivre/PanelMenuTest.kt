package br.painellivre

import br.painellivre.cluster.ClusterController
import br.painellivre.cluster.PanelMenu
import br.painellivre.cluster.PanelMenu.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #9: menu de navegação do painel (Simples/Pequeno/Cheio/OFF). */
class PanelMenuTest {

    @Test fun rawValuesFollowTheDiPlayTable() {
        assertEquals(Mode.OFF, PanelMenu.modeOf(1))
        assertEquals(Mode.SIMPLE, PanelMenu.modeOf(2))
        assertEquals(Mode.SMALL, PanelMenu.modeOf(3))
        assertEquals(Mode.FULL, PanelMenu.modeOf(4))
        // Erros do SDK costumam vir negativos; zero e null = sem leitura.
        for (raw in listOf(null, 0, 5, -1, Int.MIN_VALUE, -2147482648)) assertEquals("raw=$raw", Mode.UNKNOWN, PanelMenu.modeOf(raw))
    }

    @Test fun featureIdUsesTheRealSdkConstantOnlyWhenItIsNotAStub() {
        assertEquals(0x40C03032, PanelMenu.featureId(null))
        assertEquals(0x40C03032, PanelMenu.featureId(0)) // molde do BYD Navigator
        assertEquals(0x40C03099, PanelMenu.featureId(0x40C03099))
        assertEquals(1086337074, PanelMenu.NAVI_TYPE_FEATURE) // o número do "service call" do DiPlay
    }

    @Test fun unknownAndFullKeepTheUsualDrawing() {
        assertEquals(PanelMenu.NORMAL, PanelMenu.layoutFor(Mode.UNKNOWN))
        assertEquals(PanelMenu.NORMAL, PanelMenu.layoutFor(Mode.FULL))
        assertTrue(PanelMenu.layoutFor(Mode.FULL).isNormal)
    }

    @Test fun offHidesEverythingAndSimpleOnlyTheMap() {
        val off = PanelMenu.layoutFor(Mode.OFF)
        assertFalse(off.showMap); assertFalse(off.showCards); assertFalse(off.showCar)
        val simple = PanelMenu.layoutFor(Mode.SIMPLE)
        assertFalse(simple.showMap); assertTrue(simple.showCards); assertFalse(simple.showCar)
    }

    @Test fun smallKeepsTheContentInTheMiddle() {
        val s = PanelMenu.layoutFor(Mode.SMALL)
        assertTrue(s.showMap && s.showCards && s.showCar)
        assertEquals(s.left, s.right, 0f) // centralizado
        assertTrue(s.left > 0f && s.left + s.right < 1f && s.top + s.bottom < 1f)
    }

    @Test fun followingIsOptInAndTheDebugTestWins() {
        assertEquals(Mode.UNKNOWN, PanelMenu.effective(follow = false, override = null, stable = Mode.OFF))
        assertEquals(Mode.OFF, PanelMenu.effective(follow = true, override = null, stable = Mode.OFF))
        assertEquals(Mode.SMALL, PanelMenu.effective(follow = false, override = Mode.SMALL, stable = Mode.OFF))
        assertEquals(Mode.FULL, PanelMenu.effective(follow = true, override = Mode.FULL, stable = Mode.OFF))
    }

    @Test fun followerNeedsTwoEqualReadingsBeforeChanging() {
        val f = PanelMenu.Follower()
        assertEquals(Mode.UNKNOWN, f.update(Mode.OFF))
        assertEquals(Mode.OFF, f.update(Mode.OFF))
        // Uma leitura isolada diferente não pisca o mapa.
        assertEquals(Mode.OFF, f.update(Mode.FULL))
        assertEquals(Mode.OFF, f.update(Mode.OFF))
        assertEquals(Mode.OFF, f.update(Mode.SMALL))
        assertEquals(Mode.SMALL, f.update(Mode.SMALL))
    }

    @Test fun followerFallsBackToNormalAfterRepeatedFailures() {
        val f = PanelMenu.Follower(maxMisses = 3)
        f.update(Mode.OFF); f.update(Mode.OFF)
        repeat(3) { assertEquals("falha ${it + 1}", Mode.OFF, f.update(Mode.UNKNOWN)) }
        assertEquals(Mode.UNKNOWN, f.update(Mode.UNKNOWN))
        // Uma leitura boa no meio zera a contagem.
        val g = PanelMenu.Follower(maxMisses = 2)
        g.update(Mode.SMALL); g.update(Mode.SMALL)
        g.update(Mode.UNKNOWN); g.update(Mode.SMALL); g.update(Mode.UNKNOWN); g.update(Mode.UNKNOWN)
        assertEquals(Mode.SMALL, g.stable)
    }

    @Test fun debugReadsOnlyGetters() {
        assertTrue(PanelMenu.DEBUG_GETTERS.isNotEmpty())
        PanelMenu.DEBUG_GETTERS.forEach { assertTrue(it, PanelMenu.isReadOnlyGetter(it)) }
        for (bad in listOf("setViewSwitch", "sendAutoNaviStatus", "clearInfo", "registerListener", "get", "getter", "target"))
            assertFalse(bad, PanelMenu.isReadOnlyGetter(bad))
        assertTrue(PanelMenu.isReadOnlyGetter("get2in1MenuState"))
    }

    @Test fun menuSupportAddsNoAutoContainerCommand() {
        // 17 (meia tela) e 39 (navegação simples) do byd-dolphin-hacking seguem fora da lista segura.
        assertEquals(setOf(16, 35, 18, 0), ClusterController.SAFE_COMMANDS.toSet())
        assertFalse(17 in ClusterController.SAFE_COMMANDS)
        assertFalse(39 in ClusterController.SAFE_COMMANDS)
    }
}
