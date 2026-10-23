package br.painellivre.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import br.painellivre.cluster.SafeArea

/** Acesso tipado às preferências (as chaves batem com res/xml/preferences.xml). */
class Prefs internal constructor(private val sp: SharedPreferences) {
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context.applicationContext))

    /** "nav" = só durante a navegação, "always" = sempre, "manual" = só pelos botões. */
    val panelContent: String get() = sp.getString(KEY_PANEL_CONTENT, "integrated") ?: "integrated"
    val clusterMode: String get() = sp.getString(KEY_CLUSTER_MODE, "nav") ?: "nav"
    val restoreDelaySec: Int get() = sp.getString(KEY_RESTORE_DELAY, "20")?.toIntOrNull()?.coerceIn(0, 600) ?: 20
    val mapStyle: String get() = sp.getString(KEY_MAP_STYLE, "auto") ?: "auto"
    val mapTilt: Double get() = sp.getString(KEY_MAP_TILT, "45")?.toDoubleOrNull()?.coerceIn(0.0, 60.0) ?: 45.0
    /** Área segura do mapa no painel: "songplus" (padrão) ou "none" ([br.painellivre.cluster.SafeArea]). */
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

    /** Tema da central e do mapa: "auto" (dia/noite pela hora), "day" ou "night". */
    val theme: String get() = sp.getString(KEY_THEME, "auto") ?: "auto"

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
    /** Seguir o menu de navegação do painel (Simples/Pequeno/Cheio/OFF). Experimental, desligado. */
    val panelMenuFollow: Boolean get() = sp.getBoolean(KEY_PANEL_MENU_FOLLOW, false)
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
