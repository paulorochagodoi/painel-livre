package br.painellivre.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.painellivre.R
import br.painellivre.info.Forecast
import br.painellivre.info.ForecastText
import br.painellivre.info.WeatherRepository
import br.painellivre.location.LocationRepository
import br.painellivre.ui.aura.AuraBackdropDrawable
import br.painellivre.ui.aura.AuraColors
import br.painellivre.ui.cockpit.Palette
import br.painellivre.ui.cockpit.WeatherIconDrawable
import br.painellivre.util.Prefs
import kotlinx.coroutines.launch

/**
 * Previsão do tempo completa (issue #4), aberta ao tocar no tempo da tela inicial: agora (sensação,
 * umidade, vento, máxima/mínima, sol), as próximas 24 horas e 7 dias. Mesma fonte e mesmo pedido do
 * cartão (Open-Meteo, posição arredondada a 0,1°); a leitura é do [br.painellivre.info.ForecastParser].
 */
class WeatherActivity : AppCompatActivity() {

    private var palette = Palette.DAY
    private val density get() = resources.displayMetrics.density
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PainelLivre_Aura) // fontes Aura (Albert Sans)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)
        // Mesmas cores da tela inicial Aura (docs/INTERFACE-AURA.md); o painel segue com o Palette próprio.
        val aura = AuraColors.forSetting(Prefs(this).theme)
        palette = aura.toPalette()
        findViewById<ImageView>(R.id.backdrop).setImageDrawable(AuraBackdropDrawable(aura))
        palette.apply(findViewById(R.id.root), density)
        findViewById<View>(R.id.wxClose).setOnClickListener { finish() }
        findViewById<View>(R.id.wxRefresh).setOnClickListener { load(force = true) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { WeatherRepository.forecast.collect { render(it) } }
                // Sem previsão ainda: busca assim que o GPS der a primeira posição.
                launch {
                    LocationRepository.location.collect { if (it != null && WeatherRepository.forecast.value == null) load(force = false) }
                }
            }
        }
        if (LocationRepository.location.value == null) message(getString(R.string.weather_no_gps))
    }

    private fun load(force: Boolean) {
        if (loading) return
        val loc = LocationRepository.location.value
        if (loc == null) {
            if (WeatherRepository.forecast.value == null) message(getString(R.string.weather_no_gps))
            return
        }
        loading = true
        if (WeatherRepository.forecast.value == null) message(getString(R.string.weather_loading))
        lifecycleScope.launch {
            val ok = WeatherRepository.refresh(loc, force)
            loading = false
            if (!ok) message(getString(R.string.weather_unavailable))
        }
    }

    private fun message(text: String) {
        findViewById<TextView>(R.id.wxMessage).apply { this.text = text; visibility = View.VISIBLE }
        findViewById<View>(R.id.wxContent).visibility = View.GONE
    }

    private fun render(f: Forecast?) {
        f ?: return
        findViewById<View>(R.id.wxMessage).visibility = View.GONE
        findViewById<View>(R.id.wxContent).visibility = View.VISIBLE
        val c = f.current
        findViewById<ImageView>(R.id.wxIcon).setImageDrawable(WeatherIconDrawable(WeatherRepository.kindOf(c.code), c.isDay, palette))
        findViewById<TextView>(R.id.wxTemp).text = "${c.tempC}°"
        findViewById<TextView>(R.id.wxDesc).text = WeatherRepository.describe(c.code)
        findViewById<TextView>(R.id.wxDetails).text = ForecastText.details(c)
        findViewById<TextView>(R.id.wxToday).text = ForecastText.today(f.days.firstOrNull { it.label == "Hoje" })
        renderHours(f.hours)
        renderDays(f.days)
    }

    private fun renderHours(hours: List<Forecast.Hour>) {
        val row = findViewById<LinearLayout>(R.id.wxHours)
        row.removeAllViews()
        val gap = (10 * density).toInt()
        hours.forEach { h ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                tag = "card"
                setPadding(gap, gap, gap, gap)
                minimumWidth = (84 * density).toInt()
            }
            cell.addView(text(h.label, 15f, "t2"))
            cell.addView(icon(WeatherRepository.kindOf(h.code), h.isDay, 40))
            cell.addView(text("${h.tempC}°", 22f, "t1"))
            cell.addView(text(ForecastText.rain(h.rainPct) ?: " ", 13f, "accent"))
            row.addView(cell, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = gap })
        }
        palette.apply(row, density)
    }

    private fun renderDays(days: List<Forecast.Day>) {
        val list = findViewById<LinearLayout>(R.id.wxDays)
        list.removeAllViews()
        val lo = days.minOfOrNull { it.minC }
        val hi = days.maxOfOrNull { it.maxC }
        days.forEach { d ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (56 * density).toInt()
            }
            line.addView(text(d.label, 18f, "t1"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(icon(WeatherRepository.kindOf(d.code), true, 34))
            line.addView(text(ForecastText.rain(d.rainPct) ?: "", 14f, "accent").apply { gravity = Gravity.END }, fixed(64))
            line.addView(text("${d.minC}°", 18f, "t2").apply { gravity = Gravity.END }, fixed(56))
            line.addView(RangeBar(d.minC, d.maxC, lo ?: d.minC, hi ?: d.maxC), fixed(140).apply { height = (6 * density).toInt(); marginStart = (12 * density).toInt(); marginEnd = (12 * density).toInt() })
            line.addView(text("${d.maxC}°", 18f, "t1"), fixed(56))
            list.addView(line)
        }
        palette.apply(list, density)
    }

    private fun fixed(dp: Int) = LinearLayout.LayoutParams((dp * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun text(s: String, sp: Float, role: String) = TextView(this).apply {
        text = s; textSize = sp; tag = role; maxLines = 1
    }

    private fun icon(kind: WeatherRepository.Kind, day: Boolean, dp: Int) = ImageView(this).apply {
        setImageDrawable(WeatherIconDrawable(kind, day, palette))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val px = (dp * density).toInt()
        layoutParams = LinearLayout.LayoutParams(px, px).apply { topMargin = (4 * density).toInt(); bottomMargin = (4 * density).toInt() }
    }

    /** Faixa da mínima à máxima do dia dentro da semana (como nos apps de tempo comuns). */
    private inner class RangeBar(private val min: Int, private val max: Int, private val weekMin: Int, private val weekMax: Int) : View(this@WeatherActivity) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: android.graphics.Canvas) {
            val h = height.toFloat()
            val r = h / 2
            paint.color = palette.text3
            c.drawRoundRect(0f, 0f, width.toFloat(), h, r, r, paint)
            val span = (weekMax - weekMin).coerceAtLeast(1).toFloat()
            val x0 = (min - weekMin) / span * width
            val x1 = ((max - weekMin) / span * width).coerceAtLeast(x0 + h)
            paint.color = palette.accent
            c.drawRoundRect(x0, 0f, x1, h, r, r, paint)
        }
    }
}
