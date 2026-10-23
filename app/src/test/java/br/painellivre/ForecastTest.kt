package br.painellivre

import br.painellivre.info.Forecast
import br.painellivre.info.ForecastParser
import br.painellivre.info.ForecastText
import br.painellivre.info.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Issue #4: previsão completa (horas e dias) a partir do mesmo pedido do Open-Meteo. */
class ForecastTest {

    // Resposta do Open-Meteo reduzida (timezone=auto: horários locais, sem fuso).
    private val body = """
        {"latitude":-23.5,"longitude":-46.6,"timezone":"America/Sao_Paulo",
         "current":{"time":"2026-10-23T14:15","interval":900,"temperature_2m":27.6,"apparent_temperature":29.2,
                    "relative_humidity_2m":58,"wind_speed_10m":11.7,"weather_code":2,"is_day":1},
         "hourly":{"time":["2026-10-23T12:00","2026-10-23T13:00","2026-10-23T14:00","2026-10-23T15:00","2026-10-23T16:00","2026-10-24T00:00"],
                   "temperature_2m":[25.0,26.4,27.5,27.9,null,19.2],
                   "weather_code":[1,2,2,80,3,0],
                   "precipitation_probability":[0,5,10,65,40,null],
                   "is_day":[1,1,1,1,1,0]},
         "daily":{"time":["2026-10-22","2026-10-23","2026-10-24","2026-10-25"],
                  "weather_code":[3,80,61,0],
                  "temperature_2m_max":[24.0,30.4,22.6,26.0],
                  "temperature_2m_min":[16.0,18.6,15.5,14.4],
                  "precipitation_probability_max":[0,70,90,null],
                  "sunrise":["2026-10-22T05:31","2026-10-23T05:30","2026-10-24T05:29","2026-10-25T05:29"],
                  "sunset":["2026-10-22T18:11","2026-10-23T18:12","2026-10-24T18:12","2026-10-25T18:13"]}}
    """.trimIndent()

    @Test fun parsesCurrent() {
        val c = ForecastParser.parse(body)!!.current
        assertEquals(28, c.tempC)
        assertEquals(2, c.code)
        assertTrue(c.isDay)
        assertEquals(29, c.feelsLikeC)
        assertEquals(58, c.humidityPct)
        assertEquals(12, c.windKmh)
    }

    @Test fun hoursStartAtTheCurrentHourAndSkipHoles() {
        val h = ForecastParser.parse(body)!!.hours
        assertEquals(listOf("Agora", "15h", "0h"), h.map { it.label }) // 16h sem temperatura: some
        assertEquals(listOf(28, 28, 19), h.map { it.tempC })
        assertEquals(65, h[1].rainPct)
        assertNull(h[2].rainPct)
        assertFalse(h[2].isDay)
        assertEquals(2, ForecastParser.parse(body, hours = 2)!!.hours.size)
    }

    @Test fun daysStartTodayWithPortugueseLabels() {
        val d = ForecastParser.parse(body)!!.days
        assertEquals(listOf("Hoje", "Amanhã", "Dom 25"), d.map { it.label }) // 25/10/2026 é domingo
        assertEquals(19, d[0].minC)
        assertEquals(30, d[0].maxC)
        assertEquals(70, d[0].rainPct)
        assertEquals("05:30", d[0].sunrise)
        assertEquals("18:12", d[0].sunset)
        assertNull(d[2].rainPct)
    }

    @Test fun cardStillWorksWithOnlyCurrent() {
        // Resposta antiga (só "current"): o cartão continua, a tela mostra listas vazias.
        val f = ForecastParser.parse("""{"current":{"time":"2026-10-23T09:00","temperature_2m":18.4,"weather_code":61,"is_day":1}}""")!!
        assertEquals(18, f.current.tempC)
        assertNull(f.current.feelsLikeC)
        assertTrue(f.hours.isEmpty())
        assertTrue(f.days.isEmpty())
        assertEquals("", ForecastText.details(f.current))
    }

    @Test fun badResponses() {
        assertNull(ForecastParser.parse(""))
        assertNull(ForecastParser.parse("<html>erro</html>"))
        assertNull(ForecastParser.parse("""{"error":true,"reason":"Latitude must be in range"}"""))
        assertNull(ForecastParser.parse("""{"current":{"time":"2026-10-23T09:00"}}"""))
    }

    @Test fun labels() {
        val today = LocalDate.parse("2026-10-23") // sexta
        assertEquals("Hoje", ForecastParser.dayLabel("2026-10-23", today))
        assertEquals("Amanhã", ForecastParser.dayLabel("2026-10-24", today))
        assertEquals("Seg 26", ForecastParser.dayLabel("2026-10-26", today))
        assertEquals("Sáb 31", ForecastParser.dayLabel("2026-10-31", null))
        assertEquals("xx", ForecastParser.dayLabel("xx", today))
        assertEquals("0h", ForecastParser.hourLabel("2026-10-24T00:00"))
        assertEquals("9h", ForecastParser.hourLabel("2026-10-24T09:00"))
        assertEquals("23h", ForecastParser.hourLabel("2026-10-24T23:00"))
    }

    @Test fun texts() {
        val f = ForecastParser.parse(body)!!
        assertEquals("Sensação 29° · Umidade 58% · Vento 12 km/h", ForecastText.details(f.current))
        assertEquals("Máx 30° · Mín 19° · Sol 05:30–18:12", ForecastText.today(f.days[0]))
        assertEquals("", ForecastText.today(null))
        assertEquals("Máx 1° · Mín 0°", ForecastText.today(Forecast.Day("d", "Hoje", 0, 0, 1, null, null, null)))
        assertNull(ForecastText.rain(null))
        assertNull(ForecastText.rain(5))
        assertEquals("10%", ForecastText.rain(10))
    }

    /** Privacidade (README/AGENTS): só a posição arredondada a 0,1° vai na URL, nada além. */
    @Test fun urlKeepsPositionRounded() {
        val url = ForecastParser.url(-23.55052, -46.63331)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=-23.6&longitude=-46.6&"))
        assertFalse(url.contains("23.55"))
        assertFalse(url.contains("46.63"))
        assertTrue(url.contains("hourly=") && url.contains("daily=") && url.contains("current="))
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=0.0&longitude=0.0", ForecastParser.url(0.0, 0.0).substringBefore('&') + "&" + ForecastParser.url(0.0, 0.0).split('&')[1])
    }

    @Test fun fetchIsRateLimited() {
        val min = WeatherRepository.FORCE_MIN_MS
        val interval = WeatherRepository.INTERVAL_MS
        assertTrue(WeatherRepository.shouldFetch(now = 1_000, last = 0, hasData = false, force = false))
        // Sem dados e sem internet: antes pedia a cada posição do GPS; agora espera 1 min.
        assertFalse(WeatherRepository.shouldFetch(now = 10_000 + 30_000, last = 10_000, hasData = false, force = false))
        assertTrue(WeatherRepository.shouldFetch(now = 10_000 + min, last = 10_000, hasData = false, force = false))
        assertFalse(WeatherRepository.shouldFetch(now = 10_000 + interval - 1, last = 10_000, hasData = true, force = false))
        assertTrue(WeatherRepository.shouldFetch(now = 10_000 + interval, last = 10_000, hasData = true, force = false))
        assertFalse(WeatherRepository.shouldFetch(now = 10_000 + 5_000, last = 10_000, hasData = true, force = true))
        assertTrue(WeatherRepository.shouldFetch(now = 10_000 + min, last = 10_000, hasData = true, force = true))
    }
}
