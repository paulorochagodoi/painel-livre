package br.painellivre.ui.cockpit

import br.painellivre.car.TelemetrySnapshot

/** Coordenadas na imagem car_top inteira, frente à esquerda. Sem dependência do Android. */
object CarOverlay {
    data class Mark(val x: Float, val y: Float, val x2: Float = x, val y2: Float = y)
    val openings = mapOf(
        "door_fl" to Mark(.39f, .87f, .51f, .98f),
        "door_rl" to Mark(.55f, .87f, .69f, .98f),
        "door_fr" to Mark(.39f, .13f, .51f, .02f),
        "door_rr" to Mark(.55f, .13f, .69f, .02f),
        "hood" to Mark(.12f, .3f, .12f, .7f),
        "trunk" to Mark(.94f, .28f, .94f, .72f),
    )
    val windows = mapOf(
        "win_fl" to Mark(.42f, .78f, .52f, .78f),
        "win_rl" to Mark(.57f, .78f, .68f, .78f),
        "win_fr" to Mark(.42f, .22f, .52f, .22f),
        "win_rr" to Mark(.57f, .22f, .68f, .22f),
        "sunroof" to Mark(.51f, .42f, .66f, .42f),
        "sunshade" to Mark(.51f, .58f, .66f, .58f),
    )
    val tyres = mapOf("fl" to Mark(.23f, .89f), "fr" to Mark(.23f, .11f),
        "rl" to Mark(.79f, .89f), "rr" to Mark(.79f, .11f))
    // Traseira direita: ajustar aqui se a conferência do ano/modelo indicar outra posição.
    val charger = Mark(.84f, .15f)
    val lock = Mark(.56f, .5f)
    val baseLock = Mark(.77f, .76f)
    const val LOW_KPA = 220.0
    const val HIGH_KPA = 310.0
    enum class Tyre { UNKNOWN, NORMAL, ALERT }
    data class State(val open: Set<String>, val windows: Set<String>, val tyres: Map<String, Tyre>,
        val pressures: Map<String, Double>, val lid: Boolean, val charging: Boolean, val locked: Boolean?) {
        val highlight get() = open.isNotEmpty() || windows.isNotEmpty() || lid || charging || tyres.values.any { it == Tyre.ALERT }
    }
    fun state(s: TelemetrySnapshot?): State {
        val pressures = tyres.keys.mapNotNull { k -> s?.value("tyre_p_$k")?.let { k to it } }.toMap()
        return State(openings.keys.filter { s?.isOpen(it) == true }.toSet(),
            windows.keys.filter { (s?.value(it) ?: 0.0) > 0 }.toSet(),
            tyres.keys.associateWith { k -> pressures[k]?.let { if (it < LOW_KPA || it > HIGH_KPA) Tyre.ALERT else Tyre.NORMAL } ?: Tyre.UNKNOWN },
            pressures, s?.chargeLidOpen == true, s?.charging == true, s?.locked)
    }
}
