package br.painellivre.units

import java.util.Locale
import kotlin.math.roundToInt

/**
 * **Kotlin puro.** Converte e formata o que a interface mostra (issue #25).
 *
 * O carro, o CAN e o parser continuam nas unidades de origem (km, metros, km/h, kPa, °C,
 * kWh/100 km, L/100 km). Só o texto da Aura, dos cartões do painel e da navegação muda.
 * Padrão de fábrica: métrico, com a pressão dos pneus em psi.
 */
object Units {
    private val BR = Locale("pt", "BR")

    /** 1 milha internacional, em quilômetros. */
    const val KM_PER_MILE = 1.609344
    /** 1 pé internacional, em metros. */
    const val M_PER_FOOT = 0.3048
    /** 1 psi, em kPa. */
    const val KPA_PER_PSI = 6.894757293168361
    /** Milhas em 100 km. mi/kWh = este valor ÷ (kWh/100 km). */
    const val MILES_PER_100_KM = 100.0 / KM_PER_MILE
    /** Galão americano, em litros. mpg = (milhas em 100 km × isto) ÷ (L/100 km). */
    const val LITERS_PER_US_GALLON = 3.785411784
    const val MPG_PER_L_100 = MILES_PER_100_KM * LITERS_PER_US_GALLON

    enum class Distance(val key: String) {
        KM("km"), MI("mi");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: KM }
    }
    enum class ShortDistance(val key: String) {
        M("m"), FT("ft");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: M }
    }
    enum class Speed(val key: String) {
        KMH("kmh"), MPH("mph");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: KMH }
    }
    enum class Pressure(val key: String, val label: String) {
        BAR("bar", "bar"), PSI("psi", "psi"), KPA("kpa", "kPa");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: PSI }
    }
    enum class Temperature(val key: String) {
        C("c"), F("f");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: C }
    }
    enum class Electric(val key: String) {
        KWH_100("kwh100"), MI_PER_KWH("mikwh");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: KWH_100 }
    }
    enum class Fuel(val key: String) {
        L_100("l100"), MPG("mpg");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.key == raw } ?: L_100 }
    }

    data class Choice(
        val distance: Distance = Distance.KM,
        val shortDistance: ShortDistance = ShortDistance.M,
        val speed: Speed = Speed.KMH,
        val pressure: Pressure = Pressure.PSI,
        val temperature: Temperature = Temperature.C,
        val electric: Electric = Electric.KWH_100,
        val fuel: Fuel = Fuel.L_100,
    ) {
        companion object {
            val DEFAULT = Choice()
        }
    }

    /** Número e sigla separados, para o atalho de autonomia (número grande, unidade pequena). */
    data class Split(val number: String, val unit: String) {
        val text: String get() = "$number $unit"
    }

    fun miles(km: Double) = km / KM_PER_MILE
    fun feet(meters: Double) = meters / M_PER_FOOT
    fun fahrenheit(celsius: Double) = celsius * 9.0 / 5.0 + 32.0
    fun psi(kpa: Double) = kpa / KPA_PER_PSI
    fun mph(kmh: Double) = kmh / KM_PER_MILE
    fun milesPerKwh(kwhPer100Km: Double) = MILES_PER_100_KM / kwhPer100Km
    fun mpgUs(litersPer100Km: Double) = MPG_PER_L_100 / litersPer100Km

    /** Distância longa (rota restante a partir de 1 km, autonomia, hodômetro, linha reta). */
    fun distanceKm(km: Double?, choice: Choice = Choice.DEFAULT): String {
        if (km == null || km.isNaN()) return "—"
        return distanceSplit(km, choice).text
    }

    fun distanceSplit(km: Double, choice: Choice = Choice.DEFAULT): Split {
        val miles = choice.distance == Distance.MI
        val shown = if (miles) miles(km) else km
        return Split(longNumber(shown), if (miles) "mi" else "km")
    }

    /**
     * Manobra ou restante da rota, em metros. Abaixo de 1 km usa a unidade curta (m/ft);
     * a partir daí, a longa (km/mi). Sem metros, fica o texto que veio da notificação.
     * O painel e a navegação chamam esta função para o mesmo número sair igual.
     */
    fun navDistance(meters: Int?, fallback: String?, choice: Choice = Choice.DEFAULT): String? {
        if (meters == null || meters < 0) return fallback?.takeIf { it.isNotBlank() }
        return if (meters < 1000) shortMeters(meters.toDouble(), choice) else distanceKm(meters / 1000.0, choice)
    }

    fun speedKmh(kmh: Double?, choice: Choice = Choice.DEFAULT): String {
        if (kmh == null || kmh.isNaN()) return "—"
        val miles = choice.speed == Speed.MPH
        val shown = if (miles) mph(kmh) else kmh
        return "${flexible(shown)} ${if (miles) "mph" else "km/h"}"
    }

    fun pressureKpa(kpa: Double?, choice: Choice = Choice.DEFAULT): String {
        if (kpa == null || kpa.isNaN()) return "—"
        return "${pressureNumber(kpa, choice)} ${choice.pressure.label}"
    }

    /** Só o número, para a lista "Pneus (psi): 41,6 41,2 …". */
    fun pressureNumber(kpa: Double, choice: Choice = Choice.DEFAULT): String = when (choice.pressure) {
        Pressure.BAR -> String.format(BR, "%.1f", kpa / 100.0)
        Pressure.KPA -> kpa.roundToInt().toString()
        Pressure.PSI -> String.format(BR, "%.1f", psi(kpa))
    }

    /** Clima e exterior. [whole] = inteiro (tempo, dentro/fora); senão uma casa (ajuste do ar). */
    fun temperature(celsius: Double?, choice: Choice = Choice.DEFAULT, whole: Boolean = false): String {
        if (celsius == null || celsius.isNaN()) return "—"
        val f = choice.temperature == Temperature.F
        val shown = if (f) fahrenheit(celsius) else celsius
        val body = if (whole) shown.roundToInt().toString() else String.format(BR, "%.1f", shown)
        return body + if (f) "°F" else "°C"
    }

    /** Consumo elétrico. A origem é kWh/100 km; mi/kWh inverte a escala (0 não vira infinito). */
    fun electric(kwhPer100Km: Double?, choice: Choice = Choice.DEFAULT): String {
        if (kwhPer100Km == null || kwhPer100Km.isNaN() || kwhPer100Km < 0) return "—"
        if (choice.electric == Electric.MI_PER_KWH) {
            if (kwhPer100Km == 0.0) return "—"
            return String.format(BR, "%.1f mi/kWh", milesPerKwh(kwhPer100Km))
        }
        return String.format(BR, "%.1f kWh/100 km", kwhPer100Km)
    }

    /** Consumo de combustível. A origem é L/100 km; mpg é o galão americano. */
    fun fuel(litersPer100Km: Double?, choice: Choice = Choice.DEFAULT): String {
        if (litersPer100Km == null || litersPer100Km.isNaN() || litersPer100Km < 0) return "—"
        if (choice.fuel == Fuel.MPG) {
            if (litersPer100Km == 0.0) return "—"
            return String.format(BR, "%.1f mpg", mpgUs(litersPer100Km))
        }
        return String.format(BR, "%.1f L/100 km", litersPer100Km)
    }

    private fun shortMeters(meters: Double, choice: Choice): String {
        val feet = choice.shortDistance == ShortDistance.FT
        val shown = if (feet) feet(meters) else meters
        return "${shown.roundToInt()} ${if (feet) "ft" else "m"}"
    }

    /** Abaixo de 10 (na unidade mostrada), uma casa; senão inteiro. Igual ao "6,4 km" / "12 km" da Aura. */
    private fun longNumber(v: Double): String {
        val tenths = (v * 10.0).roundToInt()
        return if (kotlin.math.abs(tenths) < 100) String.format(BR, "%.1f", tenths / 10.0)
        else (tenths / 10.0).roundToInt().toString()
    }

    /** Inteiro quando a casa decimal some; senão uma casa ("12" km/h, "7,5" mph). */
    private fun flexible(v: Double): String {
        val tenths = (v * 10.0).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(BR, "%.1f", tenths / 10.0)
    }
}
