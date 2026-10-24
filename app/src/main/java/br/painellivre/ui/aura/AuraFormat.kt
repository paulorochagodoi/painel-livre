package br.painellivre.ui.aura

import java.util.Locale
import kotlin.math.roundToInt

/**
 * **Kotlin puro.** Números no formato brasileiro do Aura (design-system.md, "Conteúdo e tom"):
 * `22,0°`, `6,4 km`, `1h40`. Temperatura sempre com uma casa; distância com uma casa abaixo de 10 km.
 */
object AuraFormat {
    private val BR = Locale("pt", "BR")

    /** Temperatura com uma casa decimal: 22 → "22,0°". Null vira "—" (nunca zero). */
    fun temp(c: Double?): String = c?.let { String.format(BR, "%.1f°", it) } ?: "—"

    /** Temperatura ambiente (externa/interna) inteira, como no tempo: "24°". */
    fun tempWhole(c: Double?): String = c?.let { "${it.roundToInt()}°" } ?: "—"

    /** Distância: abaixo de 10 km com uma casa ("6,4 km", "0,4 km"), acima inteira ("12 km"). */
    fun km(v: Double?): String = when {
        v == null -> "—"
        Math.round(v * 10) < 100 -> String.format(BR, "%.1f km", v)
        else -> "${v.roundToInt()} km"
    }

    /** Duração: "28 min" abaixo de uma hora, "1h40" acima, "2h" na hora cheia. */
    fun duration(minutes: Int?): String = when {
        minutes == null || minutes < 0 -> "—"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h${"%02d".format(minutes % 60)}"
    }

    /** Porcentagem inteira sem espaço: "62%". */
    fun percent(v: Double?): String = v?.let { "${it.roundToInt()}%" } ?: "—"

    /** Primeira letra maiúscula, o resto como veio ("quinta-feira" → "Quinta-feira"). */
    fun capitalized(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(BR) else it.toString() }
}
