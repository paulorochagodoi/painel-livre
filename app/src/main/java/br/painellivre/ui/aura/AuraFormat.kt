package br.painellivre.ui.aura

import br.painellivre.units.Units
import java.util.Locale
import kotlin.math.roundToInt

/**
 * **Kotlin puro.** Números no formato brasileiro do Aura (design-system.md, "Conteúdo e tom"):
 * `22,0°C`, `6,4 km`, `1h40`. Temperatura, distância e as outras grandezas passam por [Units]
 * para a escolha de Ajustes valer aqui, no painel e na navegação.
 */
object AuraFormat {
    private val BR = Locale("pt", "BR")

    /** Temperatura do ar com uma casa: 22 → "22,0°C". Null vira "—" (nunca zero). */
    fun temp(c: Double?, units: Units.Choice = Units.Choice.DEFAULT): String =
        Units.temperature(c, units, whole = false)

    /** Temperatura ambiente (externa/interna, tempo) inteira: "24°C". */
    fun tempWhole(c: Double?, units: Units.Choice = Units.Choice.DEFAULT): String =
        Units.temperature(c, units, whole = true)

    /** Distância longa: abaixo de 10 uma casa ("6,4 km"), acima inteira ("12 km"). */
    fun km(v: Double?, units: Units.Choice = Units.Choice.DEFAULT): String = Units.distanceKm(v, units)

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
