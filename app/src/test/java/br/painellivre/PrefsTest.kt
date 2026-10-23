package br.painellivre

import android.content.SharedPreferences
import br.painellivre.util.Prefs
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PrefsTest {
    // Só a fronteira de armazenamento é falsa; conversões e padrões são os usados pelo app.
    private fun prefs(vararg values: Pair<String, Any?>): Prefs {
        val data = values.toMap()
        val sp = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString", "getBoolean", "getLong" ->
                    if (data.containsKey(args!![0])) data[args[0]] else args[1]
                else -> error("Acesso inesperado: ${method.name}")
            }
        } as SharedPreferences
        return Prefs(sp)
    }

    @Test fun defaults() {
        val p = prefs()
        assertEquals("nav", p.clusterMode)
        assertEquals(20, p.restoreDelaySec)
        assertEquals("auto", p.panelSize)
        assertEquals("auto", p.theme)
        assertEquals("auto", p.mapStyle)
        assertEquals(45.0, p.mapTilt, 0.0)
        assertEquals(60, p.maxFps)
        assertEquals("maps", p.navApp)
        assertTrue(p.startOnBoot)
        assertTrue(p.autoHeal)
        assertFalse(p.projectionPowered)
        assertNull(p.panelGeometry)
        assertNull(p.carPhoto)
        assertEquals(0L, p.lastBootAt)
    }

    @Test fun modesAndNavigationAppAreReadFromStorage() {
        for (mode in listOf("nav", "always", "manual")) {
            assertEquals(mode, prefs(Prefs.KEY_CLUSTER_MODE to mode).clusterMode)
        }
        assertEquals("waze", prefs(Prefs.KEY_NAV_APP to "waze").navApp)
    }

    @Test fun delayBoundsAndMalformedValues() {
        for ((raw, expected) in listOf(null to 20, "abc" to 20, "" to 20,
            "-1" to 0, "0" to 0, "600" to 600, "601" to 600, "999999999999" to 20)) {
            assertEquals("atraso=$raw", expected, prefs(Prefs.KEY_RESTORE_DELAY to raw).restoreDelaySec)
        }
    }

    @Test fun tiltBoundsAndFallback() {
        for ((raw, expected) in listOf(null to 45.0, "abc" to 45.0, "-2" to 0.0,
            "61" to 60.0, "30.5" to 30.5)) {
            assertEquals(expected, prefs(Prefs.KEY_MAP_TILT to raw).mapTilt, 0.0)
        }
    }

    @Test fun fpsBoundsAndFallback() {
        for ((raw, expected) in listOf(null to 60, "abc" to 60, "0" to 10, "30" to 30, "90" to 60)) {
            assertEquals(expected, prefs(Prefs.KEY_MAX_FPS to raw).maxFps)
        }
    }

    @Test fun allMarginsUseSameLimits() {
        val keys = listOf(Prefs.KEY_INSET_LEFT, Prefs.KEY_INSET_TOP, Prefs.KEY_INSET_RIGHT, Prefs.KEY_INSET_BOTTOM)
        for ((raw, expected) in listOf(null to 0, "abc" to 0, "-1" to 0, "1501" to 1500, "42" to 42)) {
            val p = prefs(*keys.map { it to raw }.toTypedArray())
            assertEquals(listOf(expected, expected, expected, expected),
                listOf(p.insetLeft, p.insetTop, p.insetRight, p.insetBottom))
        }
    }

    @Test fun storedFlagsOverrideDefaults() {
        val p = prefs(Prefs.KEY_START_ON_BOOT to false, Prefs.KEY_AUTO_HEAL to false)
        assertFalse(p.startOnBoot)
        assertFalse(p.autoHeal)
    }

    @Test fun nullStringsKeepDefaults() {
        val p = prefs(Prefs.KEY_CLUSTER_MODE to null, Prefs.KEY_NAV_APP to null,
            Prefs.KEY_THEME to null, Prefs.KEY_PANEL_SIZE to null, Prefs.KEY_MAP_STYLE to null)
        assertEquals("nav", p.clusterMode)
        assertEquals("maps", p.navApp)
        assertEquals("auto", p.theme)
        assertEquals("auto", p.panelSize)
        assertEquals("auto", p.mapStyle)
    }
}
