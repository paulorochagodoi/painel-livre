package br.painellivre

import br.painellivre.cluster.PanelContent
import org.junit.Assert.*
import org.junit.Test

class PanelContentTest {
    @Test fun integratedAndUnknownRemainIntegrated() {
        assertNull(PanelContent.externalPackage("integrated", "waze"))
        assertNull(PanelContent.externalPackage("invalid", "waze"))
    }
    @Test fun onlyKnownNavigationPackages() {
        assertEquals(PanelContent.WAZE, PanelContent.externalPackage("navigation", "waze"))
        assertEquals(PanelContent.MAPS, PanelContent.externalPackage("navigation", "anything"))
    }
    @Test fun notificationRouteNeverMovesNavigatorToPanel() {
        assertNull(PanelContent.externalPackage("navigation", "waze", notificationRoute = true))
        assertNull(PanelContent.externalPackage("navigation", "maps", notificationRoute = true))
    }
    @Test fun explicitRouteRespectsManualMode() {
        assertTrue(PanelContent.showOnRouteRequest("nav"))
        assertTrue(PanelContent.showOnRouteRequest("always"))
        assertFalse(PanelContent.showOnRouteRequest("manual"))
        assertFalse(PanelContent.showOnRouteRequest("invalid"))
    }
    private val dump = """
        Display #0 (activities from top to bottom):
          mResumedActivity: ActivityRecord{abc u0 com.waze/.MainActivity t12}
        Display #2 (activities from top to bottom):
          mResumedActivity: ActivityRecord{abc u0 com.google.android.apps.maps/.MapsActivity t13}
    """.trimIndent()
    @Test fun confirmsOnlyResumedPackageOnCorrectDisplay() {
        assertTrue(PanelContent.isResumedOnDisplay(dump, 2, PanelContent.MAPS))
        assertFalse(PanelContent.isResumedOnDisplay(dump, 2, PanelContent.WAZE))
        assertFalse(PanelContent.isResumedOnDisplay(dump, 0, PanelContent.WAZE))
        assertFalse(PanelContent.isResumedOnDisplay(dump, 1, PanelContent.MAPS))
    }
    @Test fun unknownDumpCannotClaimSuccess() {
        assertFalse(PanelContent.isResumedOnDisplay("Status: ok", 2, PanelContent.MAPS))
        assertFalse(PanelContent.isResumedOnDisplay(dump.replace("mResumedActivity:", "lastPausedActivity:"), 2, PanelContent.MAPS))
    }
}
