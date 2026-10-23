package br.painellivre.info

import android.location.Location
import br.painellivre.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tempo no lugar do carro, pelo Open-Meteo (aberto, sem chave). Só vai a posição arredondada a
 * ~10 km, e no máximo a cada 15 minutos. O mesmo pedido traz a previsão das próximas horas e dias
 * (issue #4, tela `WeatherActivity`); a leitura fica em [ForecastParser].
 */
object WeatherRepository {

    data class Weather(val tempC: Int, val code: Int, val isDay: Boolean) {
        val description: String get() = describe(code)
        val kind: Kind get() = kindOf(code)
    }

    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, STORM }

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _forecast = MutableStateFlow<Forecast?>(null)
    val forecast: StateFlow<Forecast?> = _forecast.asStateFlow()

    private var lastFetch = 0L

    const val INTERVAL_MS = 15 * 60_000L
    /** Mesmo "Atualizar" à força espera isto entre pedidos (o Open-Meteo é gratuito; não abusar). */
    const val FORCE_MIN_MS = 60_000L

    /** **Puro.** Decide se vale pedir de novo. */
    fun shouldFetch(now: Long, last: Long, hasData: Boolean, force: Boolean): Boolean = when {
        !hasData -> now - last >= FORCE_MIN_MS || last == 0L
        force -> now - last >= FORCE_MIN_MS
        else -> now - last >= INTERVAL_MS
    }

    /** Devolve true se a previsão está disponível depois da chamada. */
    suspend fun refresh(loc: Location?, force: Boolean = false): Boolean {
        loc ?: return _forecast.value != null
        val now = System.currentTimeMillis()
        if (!shouldFetch(now, lastFetch, _weather.value != null && _forecast.value != null, force)) return _forecast.value != null
        lastFetch = now
        val url = ForecastParser.url(loc.latitude, loc.longitude)
        try {
            val json = withContext(Dispatchers.IO) {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 8_000; readTimeout = 8_000
                    setRequestProperty("User-Agent", "PainelLivre (app aberto para BYD)")
                    inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
                }
            }
            val f = ForecastParser.parse(json) ?: throw IllegalStateException("resposta sem 'current'")
            _forecast.value = f
            _weather.value = Weather(tempC = f.current.tempC, code = f.current.code, isDay = f.current.isDay)
        } catch (t: Throwable) {
            AppLog.w("Tempo indisponível: ${t.javaClass.simpleName}")
        }
        return _forecast.value != null
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
