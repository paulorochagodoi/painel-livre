package br.painellivre.cluster

/**
 * Política de conteúdo: notificações trazem manobras, nunca a geometria da rota.
 *
 * Mover o Maps/Waze para o display do painel fica atrás de [mirrorNavApp] (desligado). O painel
 * não tem toque (issue #24): o caminho normal é a manobra no [br.painellivre.cluster.ClusterActivity].
 */
object PanelContent {
    const val MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"
    fun externalPackage(
        content: String,
        app: String,
        nativeRoute: Boolean = false,
        mirrorNavApp: Boolean = false,
    ): String? =
        if (mirrorNavApp && content == "navigation" && app != "native" && !nativeRoute) {
            if (app == "waze") WAZE else MAPS
        } else null

    fun showOnRouteRequest(mode: String) = mode == "nav" || mode == "always"

    /** Exige a Activity retomada no display certo; sucesso do am start sozinho não basta. */
    fun isResumedOnDisplay(dump: String, displayId: Int, pkg: String): Boolean {
        if (displayId <= 0 || pkg !in setOf(MAPS, WAZE)) return false
        var current = -1
        return dump.lineSequence().any { line ->
            Regex("Display #(\\d+)").find(line)?.let { current = it.groupValues[1].toInt() }
            current == displayId && line.contains("mResumedActivity:") &&
                Regex("\\s${Regex.escape(pkg)}/").containsMatchIn(line)
        }
    }
}
