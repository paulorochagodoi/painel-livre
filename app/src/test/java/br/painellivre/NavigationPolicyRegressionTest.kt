package br.painellivre

import br.painellivre.cluster.PanelContent
import br.painellivre.nav.CentralGuide
import br.painellivre.nav.Maneuver
import br.painellivre.nav.native.GuideCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão da navegação que já existia e do guia na central (issue #26).
 *
 * O painel só sobe sozinho em `nav` e `always`. Maps/Waze só vão para o display do painel
 * quando o conteúdo é "navigation" e não há rota nativa. A central guia a rota nativa no
 * modo manual, e também quando a projeção não está ativa.
 */
class NavigationPolicyRegressionTest {
    @Test fun modosEAppsMantemPainelEGuia() {
        data class Caso(
            val mode: String,
            val app: String,
            val content: String,
            val panel: Boolean,
            val native: Boolean,
            val project: Boolean,
            val external: String?,
            val guide: Boolean,
        )
        val maps = PanelContent.MAPS
        val waze = PanelContent.WAZE
        val cases = listOf(
            Caso("manual", "native", "integrated", false, true, false, null, true),
            Caso("manual", "native", "navigation", false, true, false, null, true),
            Caso("manual", "native", "integrated", true, true, false, null, true),
            Caso("manual", "maps", "navigation", false, false, false, maps, false),
            Caso("manual", "waze", "navigation", false, false, false, waze, false),
            Caso("manual", "maps", "integrated", false, false, false, null, false),
            Caso("manual", "waze", "integrated", true, false, false, null, false),
            Caso("nav", "native", "integrated", true, true, true, null, false),
            Caso("nav", "native", "navigation", true, true, true, null, false),
            Caso("nav", "native", "integrated", false, true, true, null, true),
            Caso("nav", "maps", "navigation", true, false, true, maps, false),
            Caso("nav", "waze", "navigation", true, false, true, waze, false),
            Caso("nav", "maps", "integrated", true, false, true, null, false),
            Caso("nav", "waze", "integrated", false, false, true, null, false),
            Caso("always", "native", "integrated", true, true, true, null, false),
            Caso("always", "native", "navigation", false, true, true, null, true),
            Caso("always", "maps", "navigation", true, false, true, maps, false),
            Caso("always", "waze", "navigation", false, false, true, waze, false),
            Caso("always", "maps", "integrated", true, false, true, null, false),
            Caso("always", "waze", "integrated", true, false, true, null, false),
            // Rota nativa não empurra Maps/Waze para o painel.
            Caso("nav", "maps", "navigation", true, true, true, null, false),
            Caso("nav", "waze", "navigation", true, true, true, null, false),
            Caso("always", "waze", "navigation", true, true, true, null, false),
            Caso("invalid", "maps", "navigation", false, false, false, maps, false),
            Caso("invalid", "native", "integrated", false, true, false, null, true),
        )
        for (c in cases) {
            assertEquals("projeção ${c.mode}/${c.app}", c.project, PanelContent.showOnRouteRequest(c.mode))
            assertEquals(
                "pacote ${c.mode}/${c.app}/${c.content}/nativa=${c.native}",
                c.external,
                PanelContent.externalPackage(c.content, c.app, c.native),
            )
            assertEquals(
                "guia ${c.mode} painel=${c.panel} nativa=${c.native}",
                c.guide,
                CentralGuide.guidesOnCentral(c.mode, c.panel, c.native),
            )
        }
    }

    @Test fun homeEDockSoMudamComOGuiaNaCentral() {
        assertTrue(CentralGuide.homeOpensMap(true))
        assertFalse(CentralGuide.homeOpensMap(false))
        assertTrue(CentralGuide.dockShowsManeuver(true))
        assertFalse(CentralGuide.dockShowsManeuver(false))
        assertTrue(CentralGuide.followCamera(guiding = true, userPanned = false))
        assertFalse(CentralGuide.followCamera(guiding = true, userPanned = true))
        assertFalse(CentralGuide.followCamera(guiding = false, userPanned = false))
    }

    @Test fun zoomAproximaParadoEAfastaEmVelocidade() {
        assertTrue(CentralGuide.zoomForSpeed(0.0) > CentralGuide.zoomForSpeed(30.0))
        assertTrue(CentralGuide.zoomForSpeed(30.0) > CentralGuide.zoomForSpeed(40.0))
        assertEquals(CentralGuide.zoomForSpeed(100.0), CentralGuide.zoomForSpeed(200.0), 0.001)
        assertEquals(40.0, CentralGuide.TILT, 0.0)
    }

    @Test fun linhaDasProximasManobras() {
        val line = CentralGuide.nextLine(listOf(
            GuideCue(Maneuver.LEFT, null, 800, "Vire à esquerda", "Rua B"),
            GuideCue(Maneuver.RIGHT, null, 1500, "Vire à direita", ""),
        ))
        assertTrue(line.contains("800 m"))
        assertTrue(line.contains("Rua B"))
        assertTrue(line.contains("1,5 km"))
        assertTrue(line.contains("Vire à direita"))
        assertEquals("", CentralGuide.nextLine(emptyList()))
    }
}
