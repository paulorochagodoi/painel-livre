package br.painellivre.info

import android.location.Location
import br.painellivre.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tempo agora no lugar do carro, pelo Open-Meteo (aberto, sem chave). Só vai a posição
 * arredondada a ~10 km, e no máximo a cada 15 minutos.
 */
object WeatherRepository {

    data class Weather(val tempC: Int, val code: Int, val isDay: Boolean) {
        val description: String get() = describe(code)
        val kind: Kind get() = kindOf(code)
    }

    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, STORM }

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private var lastFetch = 0L

    suspend fun refresh(loc: Location?, force: Boolean = false) {
        loc ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastFetch < 15 * 60_000L && _weather.value != null) return
        lastFetch = now
        val lat = String.format(Locale.ROOT, "%.1f", loc.latitude)
        val lon = String.format(Locale.ROOT, "%.1f", loc.longitude)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"
        try {
            val json = withContext(Dispatchers.IO) {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 8_000; readTimeout = 8_000
                    setRequestProperty("User-Agent", "PainelLivre (app aberto para BYD)")
                    inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
                }
            }
            val cur = JSONObject(json).getJSONObject("current")
            _weather.value = Weather(
                tempC = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
            )
        } catch (t: Throwable) {
            AppLog.w("Tempo indisponível: ${t.javaClass.simpleName}")
        }
    }

    /** Códigos WMO usados pelo Open-Meteo. */
    fun describe(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Poucas nuvens"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55, 56, 57 -> "Garoa"
        61, 63, 66, 80, 81 -> "Chuva"
        65, 67, 82 -> "Chuva forte"
        71, 73, 75, 77, 85, 86 -> "Neve"
        95, 96, 99 -> "Tempestade"
        else -> "—"
    }

    fun kindOf(code: Int): Kind = when (code) {
        0, 1 -> Kind.CLEAR
        2 -> Kind.PARTLY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        95, 96, 99 -> Kind.STORM
        in 51..86 -> Kind.RAIN
        else -> Kind.CLOUDY
    }
}
