package br.painellivre.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import br.painellivre.cluster.SafeArea

/** Acesso tipado às preferências (as chaves batem com res/xml/preferences.xml). */
class Prefs internal constructor(private val sp: SharedPreferences) {
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context.applicationContext)) { migratePanelMenu(); migrateCommands() }

    /** "nav" = só durante a navegação, "always" = sempre, "manual" = só pelos botões. */
    val panelContent: String get() = sp.getString(KEY_PANEL_CONTENT, "integrated") ?: "integrated"
    val clusterMode: String get() = sp.getString(KEY_CLUSTER_MODE, "nav") ?: "nav"
    val restoreDelaySec: Int get() = sp.getString(KEY_RESTORE_DELAY, "20")?.toIntOrNull()?.coerceIn(0, 600) ?: 20
    val mapStyle: String get() = sp.getString(KEY_MAP_STYLE, "auto") ?: "auto"
    val mapTilt: Double get() = sp.getString(KEY_MAP_TILT, "45")?.toDoubleOrNull()?.coerceIn(0.0, 60.0) ?: 45.0
    /** Área segura do mapa no painel: "songplus" (padrão) ou "none" ([br.painellivre.cluster.SafeArea]). */
    val safeAreaEnabled: Boolean get() = sp.getBoolean(KEY_SAFE_AREA_ENABLED, false)
    val safeArea: String get() = sp.getString(KEY_SAFE_AREA, "songplus") ?: "songplus"
    /** Ajuste fino da área segura, somado ao padrão (pode ser negativo). */
    val insetAdjust: SafeArea.Insets
        get() = SafeArea.Insets(
            adjustPref(KEY_INSET_LEFT), adjustPref(KEY_INSET_TOP), adjustPref(KEY_INSET_RIGHT), adjustPref(KEY_INSET_BOTTOM),
        )
    val startOnBoot: Boolean get() = sp.getBoolean(KEY_START_ON_BOOT, true)
    var navApp: String
        get() = sp.getString(KEY_NAV_APP, "maps") ?: "maps"
        set(v) = sp.edit().putString(KEY_NAV_APP, v).apply()

    /** Prova de conceito Petal (docs/PETAL_POC.md): busca e rota pelo Petal, com reserva no Photon/OSRM. Desligado. */
    val petalEnabled: Boolean get() = sp.getBoolean(KEY_PETAL_ENABLED, false)
    /** Tiles raster do Petal no mapa da central e do painel (só com [petalEnabled]). Desligado. */
    val petalTiles: Boolean get() = petalEnabled && sp.getBoolean(KEY_PETAL_TILES, false)

    /** Tema da central e do mapa: "auto" (dia/noite pela hora), "day" ou "night". */
    var theme: String
        get() = sp.getString(KEY_THEME, "auto") ?: "auto"
        set(v) = sp.edit().putString(KEY_THEME, v).apply()

    /** Foto do carro escolhida pelo motorista (content://), no lugar da ilustração. */
    var carPhoto: String?
        get() = sp.getString(KEY_CAR_PHOTO, null)
        set(v) = sp.edit().putString(KEY_CAR_PHOTO, v).apply()
    val homeAddress: String get() = sp.getString(KEY_HOME, "") ?: ""
    val workAddress: String get() = sp.getString(KEY_WORK, "") ?: ""

    /** "auto", "off" ou o código 29/30/31 (ver cluster/PanelSize). */
    val panelSize: String get() = sp.getString(KEY_PANEL_SIZE, "auto") ?: "auto"
    /** Limite de quadros por segundo do mapa com o carro andando (parado fica em 5). */
    val maxFps: Int get() = sp.getString(KEY_MAX_FPS, "60")?.toIntOrNull()?.coerceIn(10, 60) ?: 60
    /** Recriar a superfície do mapa se ele congelar andando (vigia, docs/SEALNAV_RE.md achado G). */
    val autoHeal: Boolean get() = sp.getBoolean(KEY_AUTO_HEAL, true)

    /** Modo de debug (issue #9): registra settings, propriedades e broadcasts. Desligado por padrão. */
    val debugMode: Boolean get() = sp.getBoolean(KEY_DEBUG_MODE, false)
    /** Ausência da chave segue o painel; após a migração, desligamentos são respeitados. */
    val panelMenuFollow: Boolean get() = sp.getBoolean(KEY_PANEL_MENU_FOLLOW, true)
    val panelMenuManual: br.painellivre.cluster.PanelMenu.Mode?
        get() = br.painellivre.cluster.PanelMenu.manualMode(sp.getString("panel_map_mode", "follow"))

    /** O XML antigo gravava false também sem escolha do usuário; não é distinguível. */
    internal fun migratePanelMenu() {
        if (sp.getInt("panel_menu_migration", 0) >= 1) return
        sp.edit().putBoolean(KEY_PANEL_MENU_FOLLOW, true).putInt("panel_menu_migration", 1).commit()
    }

    /** Migração 1 (0.5.4): liga o mestre e grava cada chave conforme confirmed na época.
     * Migração 2 (0.5.7): bancos/porta-malas confirmados no carro — a migração 1 tinha
     * gravado false neles por serem hipótese; ninguém pôde desligá-los depois (Diagnóstico
     * só habilita confirmed), então é seguro liberar. Desligamentos de chaves já confirmadas
     * (ac_*, media_*) continuam preservados. */
    internal fun migrateCommands() {
        val ver = sp.getInt("car_commands_migration", 0)
        if (ver < 1) {
            val e = sp.edit().putBoolean("car_commands_enabled", true)
            br.painellivre.car.CarCommands.CATALOG.forEach {
                e.putBoolean("car_command_${it.key}", it.confirmed)
            }
            e.putInt("car_commands_migration", 1).commit()
        }
        if (sp.getInt("car_commands_migration", 0) < 2) {
            val newly = listOf(
                "seat_heat_driver", "seat_vent_driver", "seat_heat_pass", "seat_vent_pass", "trunk",
            )
            val e = sp.edit()
            newly.forEach { e.putBoolean("car_command_$it", true) }
            e.putInt("car_commands_migration", 2).commit()
        }
    }
    val carCommandsEnabled: Boolean get() = sp.getBoolean("car_commands_enabled", true)
    fun carCommandAllowed(key: String): Boolean =
        br.painellivre.car.CarCommands.CATALOG.any { it.key == key && it.confirmed } &&
            sp.getBoolean("car_command_$key", true)
    /** Ler dados do carro pelo `autoservice` via ADB local (issue #16). Experimental, desligado. */
    val telemetryEnabled: Boolean get() = sp.getBoolean(KEY_TELEMETRY, false)
    /** Aviso compacto no painel (porta aberta, carregando). Desligado por padrão. */
    val telemetryPanel: Boolean get() = sp.getBoolean(KEY_TELEMETRY_PANEL, false)
    /** Segundos entre leituras dos dados do carro (5–60). */
    val telemetryIntervalSec: Int get() = sp.getString(KEY_TELEMETRY_INTERVAL, "10")?.toIntOrNull()?.coerceIn(5, 60) ?: 10
    /** Ações de broadcast extras que o debug escuta (digitadas pelo motorista). */
    var debugActions: Set<String>
        get() = sp.getStringSet(KEY_DEBUG_ACTIONS, null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_DEBUG_ACTIONS, v).apply()

    /** Projeção ligada por este app e ainda não desligada (para limpar estado velho no boot). */
    var projectionPowered: Boolean
        get() = sp.getBoolean(KEY_PROJECTION_POWERED, false)
        set(v) { sp.edit().putBoolean(KEY_PROJECTION_POWERED, v).commit() }

    /** Último tamanho medido do display do painel, "LxA". */
    var panelGeometry: String?
        get() = sp.getString(KEY_PANEL_GEOMETRY, null)
        set(v) = sp.edit().putString(KEY_PANEL_GEOMETRY, v).apply()

    var lastBootAt: Long
        get() = sp.getLong(KEY_LAST_BOOT, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_BOOT, v).apply()

    var originalHome: String?
        get() = sp.getString(KEY_ORIGINAL_HOME, null)
        set(v) = sp.edit().putString(KEY_ORIGINAL_HOME, v).apply()

    var autoContainerName: String?
        get() = sp.getString(KEY_AC_NAME, null)
        set(v) = sp.edit().putString(KEY_AC_NAME, v).apply()

    private fun adjustPref(key: String) =
        sp.getString(key, "0")?.trim()?.toIntOrNull()?.coerceIn(-SafeArea.ADJUST_LIMIT, SafeArea.ADJUST_LIMIT) ?: 0

    companion object {
        const val KEY_PANEL_CONTENT = "panel_content"
        const val KEY_CLUSTER_MODE = "cluster_mode"
        const val KEY_RESTORE_DELAY = "restore_delay"
        const val KEY_MAP_STYLE = "map_style"
        const val KEY_MAP_TILT = "map_tilt"
        const val KEY_SAFE_AREA_ENABLED = "safe_area_enabled"
        const val KEY_SAFE_AREA = "safe_area"
        const val KEY_INSET_LEFT = "inset_left"
        const val KEY_INSET_TOP = "inset_top"
        const val KEY_INSET_RIGHT = "inset_right"
        const val KEY_INSET_BOTTOM = "inset_bottom"
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_NAV_APP = "nav_app"
        const val KEY_HOME = "home_address"
        const val KEY_WORK = "work_address"
        const val KEY_PANEL_SIZE = "panel_size"
        const val KEY_THEME = "theme"
        const val KEY_CAR_PHOTO = "car_photo"
        const val KEY_MAX_FPS = "max_fps"
        const val KEY_AUTO_HEAL = "auto_heal"
        const val KEY_DEBUG_MODE = "debug_mode"
        const val KEY_PANEL_MENU_FOLLOW = "panel_menu_follow"
        const val KEY_PETAL_ENABLED = "petal_enabled"
        const val KEY_PETAL_TILES = "petal_tiles"
        const val KEY_TELEMETRY = "telemetry"
        const val KEY_TELEMETRY_PANEL = "telemetry_panel"
        const val KEY_TELEMETRY_INTERVAL = "telemetry_interval"
        private const val KEY_DEBUG_ACTIONS = "debug_actions"
        private const val KEY_PROJECTION_POWERED = "projection_powered"
        private const val KEY_PANEL_GEOMETRY = "panel_geometry"
        private const val KEY_LAST_BOOT = "last_boot_at"
        private const val KEY_ORIGINAL_HOME = "original_home_component"
        private const val KEY_AC_NAME = "autocontainer_service_name"
    }
}
