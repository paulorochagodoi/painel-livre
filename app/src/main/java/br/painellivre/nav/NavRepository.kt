package br.painellivre.nav

import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado atual da navegação, compartilhado entre o listener, o serviço e as telas. */
data class NavState(
    val active: Boolean = false,
    val sourcePackage: String? = null,
    val nav: ParsedNav? = null,
    /** Seta desenhada pelo próprio app de navegação (largeIcon da notificação), se houver. */
    val maneuverIcon: Bitmap? = null,
    val simulated: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val sourceLabel: String
        get() = when (sourcePackage) {
            NavRepository.PKG_MAPS, NavRepository.PKG_MAPS_REVANCED -> "Google Maps"
            NavRepository.PKG_WAZE -> "Waze"
            "br.painellivre" -> "Painel Livre"
            null -> ""
            else -> "Simulação"
        }
}

object NavRepository {
    const val PKG_MAPS = "com.google.android.apps.maps"
    const val PKG_MAPS_REVANCED = "app.revanced.android.apps.maps"
    const val PKG_WAZE = "com.waze"
    val NAV_PACKAGES = setOf(PKG_MAPS, PKG_MAPS_REVANCED, PKG_WAZE)

    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state.asStateFlow()

    fun update(pkg: String, nav: ParsedNav, icon: Bitmap?, simulated: Boolean = false) {
        if (_state.value.sourcePackage == "br.painellivre" && pkg != "br.painellivre") return
        _state.value = NavState(true, pkg, nav, icon, simulated, SystemClock.elapsedRealtime())
    }

    fun end(pkg: String?) {
        val cur = _state.value
        if (pkg == null || cur.sourcePackage == pkg) {
            _state.value = NavState(updatedAt = SystemClock.elapsedRealtime())
        }
    }
}
