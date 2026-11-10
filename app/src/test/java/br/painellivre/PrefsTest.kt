package br.painellivre

import android.content.SharedPreferences
import br.painellivre.cluster.SafeArea
import br.painellivre.units.Units
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

    private fun storage(data: MutableMap<String, Any?>): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putBoolean", "putInt" -> { data[args!![0] as String] = args[1]; editor }
                "commit" -> true
                "apply" -> null
                else -> error(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean", "getInt" -> data[args!![0]] ?: args[1]
                "edit" -> editor
                else -> error(method.name)
            }
        } as SharedPreferences
    }

    @Test fun migracaoUnicaLigaSomenteComandosComEvidencia() {
        for (old in listOf(emptyMap(), mapOf("car_commands_enabled" to false, "car_command_ac_on" to false))) {
            val data = old.mapValues<String, Boolean, Any?> { it.value }.toMutableMap()
            val p = Prefs(storage(data))
            p.migrateCommands()
            assertTrue(p.carCommandsEnabled)
            br.painellivre.car.CarCommands.CATALOG.forEach { assertEquals(it.confirmed, p.carCommandAllowed(it.key)) }
            assertFalse(p.carCommandAllowed("radio_byd"))
            data["car_commands_enabled"] = false
            data["car_command_ac_on"] = false
            p.migrateCommands()
            assertFalse(p.carCommandsEnabled)
            assertFalse(p.carCommandAllowed("ac_on"))
        }
    }

    @Test fun migracao2LiberaBancosEPortaMalasConfirmadosSemMexerNoQueJaEstavaDesligado() {
        // Simula instalação que passou só pela migração 1 (bancos/porta-malas ainda hipótese → false).
        val data = mutableMapOf<String, Any?>(
            "car_commands_migration" to 1,
            "car_commands_enabled" to true,
            "car_command_ac_on" to false, // desligamento explícito do usuário
            "car_command_seat_heat_driver" to false,
            "car_command_seat_vent_driver" to false,
            "car_command_seat_heat_pass" to false,
            "car_command_seat_vent_pass" to false,
            "car_command_trunk" to false,
        )
        val p = Prefs(storage(data))
        p.migrateCommands()
        assertEquals(2, data["car_commands_migration"])
        assertTrue(p.carCommandAllowed("seat_heat_driver"))
        assertTrue(p.carCommandAllowed("seat_vent_driver"))
        assertTrue(p.carCommandAllowed("seat_heat_pass"))
        assertTrue(p.carCommandAllowed("seat_vent_pass"))
        assertTrue(p.carCommandAllowed("trunk"))
        assertFalse(p.carCommandAllowed("ac_on")) // preservado
        p.migrateCommands()
        assertEquals(2, data["car_commands_migration"]) // idempotente
    }

    @Test fun migracaoMenuLigaAntigoFalseERespeitaDesligamentoPosterior() {
        val data = mutableMapOf<String, Any?>(Prefs.KEY_PANEL_MENU_FOLLOW to false)
        val p = Prefs(storage(data))
        p.migratePanelMenu()
        assertTrue(p.panelMenuFollow)
        data[Prefs.KEY_PANEL_MENU_FOLLOW] = false
        p.migratePanelMenu()
        assertFalse(p.panelMenuFollow)
    }

    @Test fun padraoMenuMigraAusenciaMasRespeitaEscolhaExplicita() {
        assertTrue(prefs().panelMenuFollow)
        assertFalse(prefs(Prefs.KEY_PANEL_MENU_FOLLOW to false).panelMenuFollow)
        assertNull(prefs().panelMenuManual)
        assertEquals(br.painellivre.cluster.PanelMenu.Mode.SMALL, prefs("panel_map_mode" to "small").panelMenuManual)
    }

    @Test fun safeAreaRequiresExplicitOptInEvenWithSavedMargins() {
        assertFalse(prefs().safeAreaEnabled)
        assertFalse(prefs(Prefs.KEY_SAFE_AREA to "songplus", "inset_left" to "300").safeAreaEnabled)
        assertTrue(prefs(Prefs.KEY_SAFE_AREA_ENABLED to true).safeAreaEnabled)
        assertFalse(prefs(Prefs.KEY_SAFE_AREA_ENABLED to false).safeAreaEnabled)
    }

    @Test fun defaults() {
        val p = prefs()
        assertEquals("integrated", p.panelContent)
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
        // Debug continua opt-in; o menu do painel passa a ser seguido por padrão.
        assertFalse(p.debugMode)
        assertTrue(p.panelMenuFollow)
    }

    @Test fun telemetryIsOffByDefaultAndIntervalIsBounded() {
        // Issue #16: a leitura pelo autoservice e o aviso no painel só ligam pelo motorista.
        val p = prefs()
        assertFalse(p.telemetryEnabled)
        assertFalse(p.telemetryPanel)
        assertEquals(10, p.telemetryIntervalSec)
        assertTrue(prefs(Prefs.KEY_TELEMETRY to true).telemetryEnabled)
        assertTrue(prefs(Prefs.KEY_TELEMETRY_PANEL to true).telemetryPanel)
        assertEquals(5, prefs(Prefs.KEY_TELEMETRY_INTERVAL to "1").telemetryIntervalSec)
        assertEquals(60, prefs(Prefs.KEY_TELEMETRY_INTERVAL to "999").telemetryIntervalSec)
        assertEquals(10, prefs(Prefs.KEY_TELEMETRY_INTERVAL to "x").telemetryIntervalSec)
        assertEquals(30, prefs(Prefs.KEY_TELEMETRY_INTERVAL to "30").telemetryIntervalSec)
    }

    @Test fun debugAndPanelMenuSwitchesAreReadFromStorage() {
        assertTrue(prefs(Prefs.KEY_DEBUG_MODE to true).debugMode)
        assertTrue(prefs(Prefs.KEY_PANEL_MENU_FOLLOW to true).panelMenuFollow)
    }

    @Test fun panelContentIsConfigurableAndNullFallsBack() {
        assertEquals("navigation", prefs(Prefs.KEY_PANEL_CONTENT to "navigation").panelContent)
        assertEquals("integrated", prefs(Prefs.KEY_PANEL_CONTENT to null).panelContent)
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

    @Test fun safeAreaAdjustAcceptsNegativeAndClamps() {
        val keys = listOf("inset_left", Prefs.KEY_INSET_TOP, Prefs.KEY_INSET_RIGHT, Prefs.KEY_INSET_BOTTOM)
        for ((raw, expected) in listOf(null to 0, "abc" to 0, "-40" to -40, " 25 " to 25,
            "-1501" to -400, "1501" to 400, "42" to 42)) {
            val p = prefs(*keys.map { it to raw }.toTypedArray())
            assertEquals(SafeArea.Insets(expected, expected, expected, expected), p.insetAdjust)
        }
    }

    @Test fun oldMarginsRemainAdditiveAndMalformedAdjustmentsFallBack() {
        val old = prefs("inset_left" to "1500", Prefs.KEY_INSET_TOP to "90",
            Prefs.KEY_INSET_RIGHT to "999999999999", Prefs.KEY_INSET_BOTTOM to "")
        assertEquals("songplus", old.safeArea)
        assertEquals(SafeArea.Insets(400, 90, 0, 0), old.insetAdjust)
        val resolved = SafeArea.resolve(1920, 720, SafeArea.preset(old.safeArea), old.insetAdjust)
        assertEquals(SafeArea.Insets(630, 180, 230, 230), resolved)
    }

    @Test fun safeAreaPresetDefaultsToSongPlus() {
        assertEquals("songplus", prefs().safeArea)
        assertEquals("none", prefs(Prefs.KEY_SAFE_AREA to "none").safeArea)
    }

    @Test fun storedFlagsOverrideDefaults() {
        val p = prefs(Prefs.KEY_START_ON_BOOT to false, Prefs.KEY_AUTO_HEAL to false)
        assertFalse(p.startOnBoot)
        assertFalse(p.autoHeal)
    }

    @Test fun nullStringsKeepDefaults() {
        val p = prefs(Prefs.KEY_CLUSTER_MODE to null, Prefs.KEY_NAV_APP to null,
            Prefs.KEY_THEME to null, Prefs.KEY_PANEL_SIZE to null, Prefs.KEY_MAP_STYLE to null)
        assertEquals("integrated", p.panelContent)
        assertEquals("nav", p.clusterMode)
        assertEquals("maps", p.navApp)
        assertEquals("auto", p.theme)
        assertEquals("auto", p.panelSize)
        assertEquals("auto", p.mapStyle)
    }

    @Test fun unitsDefaultToMetricWithPsiAndRejectUnknownValues() {
        val factory = prefs().units
        assertEquals(Units.Choice.DEFAULT, factory)
        assertEquals(Units.Distance.KM, factory.distance)
        assertEquals(Units.ShortDistance.M, factory.shortDistance)
        assertEquals(Units.Speed.KMH, factory.speed)
        assertEquals(Units.Pressure.PSI, factory.pressure)
        assertEquals(Units.Temperature.C, factory.temperature)
        assertEquals(Units.Electric.KWH_100, factory.electric)
        assertEquals(Units.Fuel.L_100, factory.fuel)
        val chosen = prefs(
            Prefs.KEY_UNIT_DISTANCE to "mi",
            Prefs.KEY_UNIT_SHORT to "ft",
            Prefs.KEY_UNIT_SPEED to "mph",
            Prefs.KEY_UNIT_PRESSURE to "kpa",
            Prefs.KEY_UNIT_TEMPERATURE to "f",
            Prefs.KEY_UNIT_ELECTRIC to "mikwh",
            Prefs.KEY_UNIT_FUEL to "mpg",
        ).units
        assertEquals(Units.Distance.MI, chosen.distance)
        assertEquals(Units.ShortDistance.FT, chosen.shortDistance)
        assertEquals(Units.Speed.MPH, chosen.speed)
        assertEquals(Units.Pressure.KPA, chosen.pressure)
        assertEquals(Units.Temperature.F, chosen.temperature)
        assertEquals(Units.Electric.MI_PER_KWH, chosen.electric)
        assertEquals(Units.Fuel.MPG, chosen.fuel)
        assertEquals(Units.Pressure.PSI, prefs(Prefs.KEY_UNIT_PRESSURE to "libras").units.pressure)
        assertEquals(Units.Distance.KM, prefs(Prefs.KEY_UNIT_DISTANCE to null).units.distance)
    }
}
