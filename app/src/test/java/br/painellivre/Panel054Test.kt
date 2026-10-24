package br.painellivre

import br.painellivre.cluster.PanelMenu
import br.painellivre.cluster.SafeArea
import org.junit.Assert.*
import org.junit.Test

class Panel054Test {
    @Test fun prioridadeDebugManualPainel() {
        val modes = PanelMenu.Mode.values().filter { it != PanelMenu.Mode.UNKNOWN }
        for (stable in PanelMenu.Mode.values()) for (manual in modes + null) for (debug in modes + null) {
            for (follow in listOf(true, false)) {
                assertEquals(debug ?: manual ?: if (follow) stable else PanelMenu.Mode.UNKNOWN,
                    PanelMenu.effective(follow, debug, stable, manual))
            }
        }
        assertNull(PanelMenu.manualMode("follow"))
        assertNull(PanelMenu.manualMode("inválido"))
        for (mode in modes) assertEquals(mode, PanelMenu.manualMode(mode.name.lowercase()))
    }

    @Test fun molduraAguardaMedidaERecalculaOsQuatroModos() {
        assertNull(PanelMenu.bounds(0, 0, PanelMenu.SMALL_LAYOUT))
        val modes = listOf(PanelMenu.Mode.FULL, PanelMenu.Mode.SMALL, PanelMenu.Mode.SIMPLE, PanelMenu.Mode.OFF)
        assertEquals(4, modes.map(PanelMenu::layoutFor).toSet().size)
        for ((w, h) in listOf(1920 to 720, 1280 to 480)) {
            val full = SafeArea.Rect(0, 0, w, h)
            val small = PanelMenu.bounds(w, h, PanelMenu.SMALL_LAYOUT)!!
            assertTrue(full.contains(small)); assertTrue(small.width < full.width)
            val plan = SafeArea.defaultPlan(w, h, small)
            assertTrue(plan.safe.contains(plan.telemetryBounds))
            assertTrue(plan.carY in 0..small.height)
            assertTrue(plan.safe.left + plan.cardWidth < plan.carX - plan.unit * SafeArea.CAR_SIZE / 2)
        }
    }

    @Test fun cartoesPadraoNaoTocamInstrumentosNasDuasResolucoes() {
        val overlays = listOf(
            SafeArea.Rect(0, 0, 1920, 170), SafeArea.Rect(0, 0, 120, 720),
            SafeArea.Rect(100, 520, 180, 580), SafeArea.Rect(0, 540, 1920, 720),
            SafeArea.Rect(1800, 180, 1920, 340))
        for ((w, h) in listOf(1920 to 720, 1280 to 480)) {
            val p = SafeArea.defaultPlan(w, h, SafeArea.Rect(0, 0, w, h))
            fun scale(r: SafeArea.Rect) = SafeArea.Rect(r.left*w/1920, r.top*h/720, r.right*w/1920, r.bottom*h/720)
            val visible = scale(SafeArea.Rect(170, 185, 760, 520))
            for (card in SafeArea.cardBounds(p)) {
                assertTrue("$card em $visible", visible.contains(card))
                overlays.map(::scale).forEach { assertFalse("$card cruza $it", card.intersects(it)) }
            }
            assertTrue(p.carY in (185*h/720)..(530*h/720))
            // Ajustes salvos não participam do caminho padrão (Área segura desligada).
            for (base in listOf(SafeArea.NONE, SafeArea.SONG_PLUS)) {
                assertEquals(SafeArea.NONE, SafeArea.effectiveInsets(false,w,h,base,SafeArea.Insets(400,400,400,400)))
                assertEquals(p, SafeArea.defaultPlan(w,h,SafeArea.Rect(0,0,w,h)))
            }
        }
    }
}
