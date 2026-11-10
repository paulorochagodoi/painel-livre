package br.painellivre.info

import br.painellivre.units.Units
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Previsão completa (issue #4): agora, próximas horas e próximos dias. */
data class Forecast(val current: Current, val hours: List<Hour>, val days: List<Day>) {

    data class Current(
        val time: String,
        val tempC: Int,
        val code: Int,
        val isDay: Boolean,
        val feelsLikeC: Int?,
        val humidityPct: Int?,
        val windKmh: Int?,
    )

    /** [label] "Agora", "15h"… */
    data class Hour(val time: String, val label: String, val tempC: Int, val code: Int, val isDay: Boolean, val rainPct: Int?)

    /** [label] "Hoje", "Amanhã", "Sáb 25"… [sunrise]/[sunset] em "HH:mm". */
    data class Day(
        val date: String,
        val label: String,
        val code: Int,
        val minC: Int,
        val maxC: Int,
        val rainPct: Int?,
        val sunrise: String?,
        val sunset: String?,
    )
}

/**
 * **Kotlin puro.** Pedido e leitura da previsão do Open-Meteo. Um pedido só traz o tempo de agora, as
 * horas e os dias, com a mesma posição arredondada a 0,1° (~10 km) de antes — nenhum dado novo sai do carro.
 */
object ForecastParser {

    const val HOURS = 24
    const val DAYS = 7

    fun url(lat: Double, lon: Double): String {
        val la = String.format(Locale.ROOT, "%.1f", lat)
        val lo = String.format(Locale.ROOT, "%.1f", lon)
        return "https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=$DAYS"
    }

    /** Lê a resposta. Sem o bloco `current` devolve null; horas e dias faltando viram listas vazias. */
    fun parse(json: String, hours: Int = HOURS, days: Int = DAYS): Forecast? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val cur = root.optJSONObject("current") ?: return null
        if (!cur.has("temperature_2m") || !cur.has("weather_code")) return null
        val now = cur.optString("time", "")
        val current = Forecast.Current(
            time = now,
            tempC = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            feelsLikeC = cur.optRounded("apparent_temperature"),
            humidityPct = cur.optRounded("relative_humidity_2m"),
            windKmh = cur.optRounded("wind_speed_10m"),
        )
        return Forecast(current, parseHours(root.optJSONObject("hourly"), now, hours), parseDays(root.optJSONObject("daily"), now, days))
    }

    private fun parseHours(h: JSONObject?, now: String, max: Int): List<Forecast.Hour> {
        h ?: return emptyList()
        val times = h.optJSONArray("time") ?: return emptyList()
        val temps = h.optJSONArray("temperature_2m")
        val codes = h.optJSONArray("weather_code")
        val rain = h.optJSONArray("precipitation_probability")
        val day = h.optJSONArray("is_day")
        // A hora cheia de agora ("2026-10-23T14:15" → "2026-10-23T14:00") é a primeira da lista.
        val from = if (now.length >= 13) now.take(13) + ":00" else ""
        val out = mutableListOf<Forecast.Hour>()
        for (i in 0 until times.length()) {
            val t = times.optString(i)
            if (t < from) continue
            val temp = temps.doubleAt(i) ?: continue
            val code = codes.intAt(i) ?: continue
            out += Forecast.Hour(
                time = t,
                label = if (out.isEmpty() && from.isNotEmpty()) "Agora" else hourLabel(t),
                tempC = temp.roundToInt(),
                code = code,
                isDay = (day.intAt(i) ?: 1) == 1,
                rainPct = rain.intAt(i),
            )
            if (out.size >= max) break
        }
        return out
    }

    private fun parseDays(d: JSONObject?, now: String, max: Int): List<Forecast.Day> {
        d ?: return emptyList()
        val dates = d.optJSONArray("time") ?: return emptyList()
        val codes = d.optJSONArray("weather_code")
        val maxT = d.optJSONArray("temperature_2m_max")
        val minT = d.optJSONArray("temperature_2m_min")
        val rain = d.optJSONArray("precipitation_probability_max")
        val rise = d.optJSONArray("sunrise")
        val set = d.optJSONArray("sunset")
        val today = runCatching { LocalDate.parse(now.take(10)) }.getOrNull()
        val out = mutableListOf<Forecast.Day>()
        for (i in 0 until dates.length()) {
            val date = dates.optString(i)
            if (today != null && date < today.toString()) continue
            val hi = maxT.doubleAt(i) ?: continue
            val lo = minT.doubleAt(i) ?: continue
            out += Forecast.Day(
                date = date,
                label = dayLabel(date, today),
                code = codes.intAt(i) ?: -1,
                minC = lo.roundToInt(),
                maxC = hi.roundToInt(),
                rainPct = rain.intAt(i),
                sunrise = rise?.optString(i)?.clock(),
                sunset = set?.optString(i)?.clock(),
            )
            if (out.size >= max) break
        }
        return out
    }

    /** "2026-10-23T15:00" → "15h". */
    fun hourLabel(time: String): String = time.substringAfter('T', "").take(2).trimStart('0').ifEmpty { "0" } + "h"

    private val WEEKDAYS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    /** "Hoje", "Amanhã" ou "Sáb 25"; [today] vem da hora da própria resposta (fuso do lugar). */
    fun dayLabel(date: String, today: LocalDate?): String {
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
        return when (today) {
            d -> "Hoje"
            d.minusDays(1) -> "Amanhã"
            else -> "${WEEKDAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth}"
        }
    }

    private fun String.clock(): String? = substringAfter('T', "").take(5).takeIf { it.length == 5 }

    private fun JSONObject.optRounded(key: String): Int? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() }?.roundToInt() else null

    private fun JSONArray?.doubleAt(i: Int): Double? =
        if (this == null || i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }

    private fun JSONArray?.intAt(i: Int): Int? = doubleAt(i)?.roundToInt()
}

/** **Kotlin puro.** Textos curtos da tela de previsão. */
object ForecastText {

    /** "Sensação 27°C · Umidade 60% · Vento 12 km/h" (some o que a resposta não trouxe). */
    fun details(c: Forecast.Current, units: Units.Choice = Units.Choice.DEFAULT): String = listOfNotNull(
        c.feelsLikeC?.let { "Sensação ${Units.temperature(it.toDouble(), units, whole = true)}" },
        c.humidityPct?.let { "Umidade $it%" },
        c.windKmh?.let { "Vento ${Units.speedKmh(it.toDouble(), units)}" },
    ).joinToString(" · ")

    /** "Máx 30°C · Mín 19°C · Sol 06:01–18:22". */
    fun today(d: Forecast.Day?, units: Units.Choice = Units.Choice.DEFAULT): String {
        d ?: return ""
        val sun = if (d.sunrise != null && d.sunset != null) "Sol ${d.sunrise}–${d.sunset}" else null
        fun temp(c: Int) = Units.temperature(c.toDouble(), units, whole = true)
        return listOfNotNull("Máx ${temp(d.maxC)}", "Mín ${temp(d.minC)}", sun).joinToString(" · ")
    }

    /** Chance de chuva: abaixo de 10% não vale ocupar espaço. */
    fun rain(pct: Int?): String? = pct?.takeIf { it >= 10 }?.let { "$it%" }
}
