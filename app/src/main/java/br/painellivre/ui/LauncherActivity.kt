package br.painellivre.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.painellivre.R
import br.painellivre.adb.SelfAdb
import br.painellivre.adb.SystemSetup
import br.painellivre.car.TelemetryCatalog
import br.painellivre.car.TelemetrySnapshot
import br.painellivre.car.TelemetryText
import br.painellivre.car.VehicleData
import br.painellivre.car.VehicleTelemetry
import br.painellivre.cluster.ClusterService
import br.painellivre.cluster.ClusterStatus
import br.painellivre.cluster.ManeuverView
import br.painellivre.cluster.PanelSize
import br.painellivre.info.MediaRepository
import br.painellivre.info.WeatherRepository
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.nav.NavState
import br.painellivre.nav.Place
import br.painellivre.nav.Places
import br.painellivre.nav.native.NativeNavigation
import br.painellivre.nav.native.NativeRouteLayer
import br.painellivre.ui.aura.AnchoredLayout
import br.painellivre.ui.aura.AuraBackdropDrawable
import br.painellivre.ui.aura.AuraColors
import br.painellivre.ui.aura.AuraDims
import br.painellivre.ui.aura.AuraFormat
import br.painellivre.ui.aura.AuraPainter
import br.painellivre.ui.aura.AuraScale
import br.painellivre.ui.aura.CarReadouts
import br.painellivre.ui.aura.EqualizerView
import br.painellivre.ui.aura.LevelBars
import br.painellivre.ui.aura.PopoverAnchor
import br.painellivre.ui.aura.PopoverFrame
import br.painellivre.ui.aura.PopoverState
import br.painellivre.ui.aura.RingView
import br.painellivre.ui.aura.animationsOn
import br.painellivre.ui.cockpit.Palette
import br.painellivre.ui.cockpit.SongCarView
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela central no padrão visual **Aura** (docs/INTERFACE-AURA.md, desenho da designer em
 * docs/design/aura-launcher): barra de status; herói com relógio, carro 3D com os chips de estado e o
 * resumo; quatro atalhos (Navegação, Bateria, Climatização, Mídia) que abrem janelas ancoradas;
 * dock. A única diferença de propósito em relação ao SealNav continua: a rota é do Google Maps, do
 * Waze ou a nativa — a busca abre a rota no app escolhido.
 *
 * Tudo do carro é **só leitura**: clima, bancos, vidros, porta-malas e trava aparecem como o carro
 * informa; nenhum controle daqui manda comando ao carro.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var colors: AuraColors = AuraColors.LIGHT
    private lateinit var painter: AuraPainter
    private var themeApplied = false
    private var adbOk: Boolean? = null
    private var adbTesting = false
    private var page = PAGE_HOME
    private val density get() = resources.displayMetrics.density

    // Janelas flutuantes (só uma por vez).
    private val popovers = PopoverState()
    private lateinit var popViews: Map<PopoverState.Id, View>
    private val popWidths = HashMap<PopoverState.Id, Int>()

    // Últimas leituras (para repintar ao trocar de tema ou abrir uma janela).
    private var lastSdk: VehicleData.Snapshot? = null
    private var lastTelemetry: TelemetrySnapshot? = null

    // Navegação
    private lateinit var placesAdapter: PlacesAdapter
    private var searchJob: Job? = null
    private var pickMode: String? = null // "home" / "work" enquanto escolhe o endereço
    private var navMap: MapView? = null
    private var navMapLibre: MapLibreMap? = null
    private var navCar: ImageView? = null

    /** Desenho base 1280 × 720: 1 dp do layout = 1 px do desenho, em qualquer tela (AuraScale). */
    override fun attachBaseContext(newBase: Context) {
        val dm = newBase.resources.displayMetrics
        val config = Configuration(newBase.resources.configuration)
        val dpi = AuraScale.densityDpi(dm.widthPixels, dm.heightPixels)
        config.densityDpi = dpi
        config.screenWidthDp = dm.widthPixels * 160 / dpi
        config.screenHeightDp = dm.heightPixels * 160 / dpi
        config.smallestScreenWidthDp = minOf(config.screenWidthDp, config.screenHeightDp)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema com as fontes Aura em código: o manifesto (que a BYD confere na instalação) fica igual.
        setTheme(R.style.Theme_PainelLivre_Aura)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        prefs = Prefs(this)
        painter = AuraPainter(colors, density)

        setupPopovers()
        setupDock()
        setupHome()
        setupNavPage()
        setupApps()

        try { ClusterService.start(this) } catch (t: Throwable) { /* app em segundo plano no boot */ }
        if (!LocationRepository.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
        applyTheme(force = true)
        showPage(PAGE_HOME)
        consumeRouteSearch()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { NavRepository.state.collect { renderNav(it) } }
                launch { NativeNavigation.route.collect { NativeRouteLayer.draw(navMapLibre?.style) } }
                launch { ClusterService.status.collect { renderCluster(it) } }
                launch { MediaRepository.now.collect { renderMedia(it) } }
                launch { WeatherRepository.weather.collect { renderWeather(it) } }
                launch { VehicleTelemetry.observe(this@LauncherActivity).collect { renderTelemetry(it) } }
                launch {
                    SelfAdb.ready.collect { if (it != null && it != adbOk) { adbOk = it; refreshSetup() } }
                }
                launch {
                    LocationRepository.location.collect { loc ->
                        loc ?: return@collect
                        WeatherRepository.refresh(loc)
                        updateNavMap()
                        renderFavorites()
                    }
                }
                launch {
                    while (true) {
                        tick()
                        delay(5_000)
                    }
                }
            }
        }
    }

    /** Como tela inicial (singleTask), o botão Início chega aqui: fecha a janela e volta ao Início. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRouteSearch()
        if (intent.hasCategory(Intent.CATEGORY_HOME)) { closePopover(); if (page != PAGE_HOME) showPage(PAGE_HOME) }
    }

    private fun consumeRouteSearch() {
        val query = intent.getStringExtra("route_search") ?: return
        intent.removeExtra("route_search")
        closePopover()
        showPage(PAGE_NAV)
        findViewById<EditText>(R.id.navSearch).setText(query)
    }

    override fun onResume() {
        super.onResume()
        MediaRepository.start(this)
        applyTheme()
        refreshSetup()
        renderPlaces()
        renderFavorites()
        navMap?.onResume()
    }

    override fun onStart() { super.onStart(); navMap?.onStart() }
    override fun onPause() { navMap?.onPause(); super.onPause() }
    override fun onStop() { navMap?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); navMap?.onLowMemory() }
    override fun onDestroy() { navMap?.onDestroy(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (closePopover()) return
        if (page != PAGE_HOME) { showPage(PAGE_HOME); return }
        // Como tela inicial, "voltar" não deve fechar o launcher.
        if (intent?.hasCategory(Intent.CATEGORY_HOME) != true) super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && LocationRepository.hasPermission(this)) {
            LocationRepository.start(this)
            refreshSetup()
        }
    }

    // ── Tema claro/escuro ───────────────────────────────────────────────────

    private val palette: Palette get() = colors.toPalette()

    private fun applyTheme(force: Boolean = false) {
        val c = AuraColors.forSetting(prefs.theme)
        if (!force && themeApplied && c == colors) return
        colors = c
        themeApplied = true
        painter = AuraPainter(c, density)
        window.statusBarColor = c.bg
        window.navigationBarColor = c.surfaceDock or (0xFF shl 24)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (c.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        findViewById<ImageView>(R.id.backdrop).setImageDrawable(AuraBackdropDrawable(c))
        painter.apply(findViewById(R.id.root))
        popViews.values.forEach { if (it.parent == null) painter.apply(it) }
        findViewById<ImageView>(R.id.themeIcon).setImageResource(if (c.dark) R.drawable.au_moon else R.drawable.au_sun)
        findViewById<TextView>(R.id.themeText).text = c.label
        renderCarArt()
        renderAll()
        navMapLibre?.setStyle(Style.Builder().fromUri(mapStyleUri())) { NativeRouteLayer.draw(it) }
        navCar?.setImageDrawable(carArrow())
        (findViewById<RecyclerView>(R.id.appsGrid).adapter as? AppsAdapter)?.let { @Suppress("NotifyDataSetChanged") it.notifyDataSetChanged() }
    }

    /** Botão da barra de status: alterna Claro/Escuro (o automático volta em Ajustes → Tema). */
    private fun toggleTheme() {
        prefs.theme = AuraColors.toggled(colors)
        applyTheme()
    }

    private fun renderAll() {
        renderNav(NavRepository.state.value)
        renderCluster(ClusterService.status.value)
        renderMedia(MediaRepository.now.value)
        renderWeather(WeatherRepository.weather.value)
        renderTelemetry(lastTelemetry ?: VehicleTelemetry.state.value)
        renderSegments()
        renderFavorites()
        renderVolume()
        highlight()
    }

    private fun renderCarArt() {
        val iv = findViewById<SongCarView>(R.id.carArt)
        iv.palette = palette.copy(warn = colors.warn, ok = colors.ok)
        iv.glow = colors.platformGlow
        iv.modelEnabled = true
        val photo = prefs.carPhoto
        if (photo != null) {
            try {
                contentResolver.openInputStream(Uri.parse(photo))?.use { iv.modelEnabled = false; iv.setImageDrawable(Drawable.createFromStream(it, "carro")) }
                return
            } catch (t: Throwable) { /* foto removida: volta à ilustração */ }
        }
        iv.setImageDrawable(null)
        iv.invalidate()
    }

    private fun mapStyleUri() = "https://tiles.openfreemap.org/styles/" + if (colors.dark) "dark" else "positron"

    // ── Barra de status e herói ─────────────────────────────────────────────

    private fun tick() {
        val now = Date()
        findViewById<TextView>(R.id.clock).text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greet = getString(when (hour) { in 5..11 -> R.string.good_morning; in 12..17 -> R.string.good_afternoon; else -> R.string.good_evening })
        val day = AuraFormat.capitalized(SimpleDateFormat("EEEE, d 'de' MMMM", BR).format(now))
        findViewById<TextView>(R.id.date).text = "$greet · $day"
        applyTheme()
        renderStatusIcons()
        lifecycleScope.launch { withContext(Dispatchers.IO) { VehicleData.read(this@LauncherActivity) }.also { lastSdk = it; renderBattery() } }
    }

    @SuppressLint("MissingPermission")
    private fun renderStatusIcons() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val wifi = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val bt = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
        val loc = LocationRepository.location.value
        val gps = loc != null && (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) < 15_000_000_000L
        // Desligado fica apagado e diz isso na descrição (leitor de tela), não só pela cor.
        fun icon(id: Int, on: Boolean, name: Int) = findViewById<View>(id).apply {
            alpha = if (on) 1f else 0.35f
            contentDescription = getString(name) + if (on) "" else " " + getString(R.string.status_off)
        }
        icon(R.id.statusWifi, wifi, R.string.status_wifi)
        icon(R.id.statusBt, bt, R.string.status_bt)
        icon(R.id.statusGps, gps, R.string.status_gps)
    }

    private fun renderWeather(w: WeatherRepository.Weather?) {
        val row = findViewById<View>(R.id.weatherRow)
        val inside = CarReadouts.climate(lastTelemetry)?.insideC
        val parts = listOfNotNull(
            w?.let { "${it.tempC}° · ${it.description}" },
            inside?.let { getString(R.string.temp_inside, AuraFormat.tempWhole(it)) },
        )
        row.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.weatherText).text = parts.joinToString(" · ")
        findViewById<ImageView>(R.id.weatherIcon).setImageResource(
            when (w?.kind) {
                WeatherRepository.Kind.CLEAR -> if (w.isDay) R.drawable.au_sun else R.drawable.au_moon
                WeatherRepository.Kind.PARTLY -> if (w.isDay) R.drawable.au_sun else R.drawable.au_cloud
                null -> R.drawable.au_fan
                else -> R.drawable.au_cloud
            },
        )
    }

    private fun renderTelemetry(t: TelemetrySnapshot) {
        lastTelemetry = t
        findViewById<SongCarView>(R.id.carArt).snapshot = t
        renderChips(t)
        // Trava e resumo: só com leitura. Sem dado, somem (nunca "Travado" ou "Tudo fechado" chutado).
        val lock = CarReadouts.lock(t)
        val pill = findViewById<View>(R.id.lockPill)
        pill.visibility = if (lock == null) View.GONE else View.VISIBLE
        if (lock != null) {
            val locked = t.locked == true
            findViewById<TextView>(R.id.lockText).text = lock
            findViewById<ImageView>(R.id.lockIcon).setImageResource(if (locked) R.drawable.au_lock else R.drawable.au_unlock)
            painter.set(findViewById(R.id.lockIcon), if (locked) "a_icon_ok" else "a_icon_warn")
        }
        val sum = CarReadouts.summary(t)
        val line = findViewById<View>(R.id.summaryLine)
        line.visibility = if (sum == null) View.GONE else View.VISIBLE
        if (sum != null) {
            findViewById<TextView>(R.id.summaryText).text = sum.text
            findViewById<ImageView>(R.id.summaryIcon).setImageResource(if (sum.warn) R.drawable.au_alert else R.drawable.au_check)
            painter.set(findViewById(R.id.summaryIcon), if (sum.warn) "a_icon_warn" else "a_icon_ok")
            painter.set(findViewById(R.id.summaryText), if (sum.warn) "a_warn" else "a_ok")
        }
        findViewById<TextView>(R.id.model).text = modelName()
        renderClimate()
        renderBattery()
        renderWeather(WeatherRepository.weather.value)
    }

    /** Nome do modelo pela identificação da central (a mesma do PanelSize); sem chute de versão. */
    private fun modelName(): String {
        val id = listOf(Build.MODEL, Build.PRODUCT, Build.DEVICE, Build.FINGERPRINT).joinToString(" ")
        return when (PanelSize.matchModel(id)?.take(4)) {
            "song" -> "BYD Song Plus"
            "seal" -> "BYD Seal U"
            else -> getString(R.string.app_name)
        }
    }

    /** Chips ao redor do carro, cada um perto da sua peça (car_top: frente à esquerda, lado direito em cima). */
    private fun renderChips(t: TelemetrySnapshot) {
        val box = findViewById<AnchoredLayout>(R.id.carChips)
        box.removeAllViews()
        for (chip in CarReadouts.chips(t)) {
            val v = layoutInflater.inflate(R.layout.aura_chip, box, false)
            v.layoutParams = AnchoredLayout.LayoutParams(CHIP_POS.getValue(chip.id).first, CHIP_POS.getValue(chip.id).second)
            v.tag = if (chip.level == CarReadouts.Level.WARN) "a_chip_warn" else "a_chip"
            val icon = v.findViewById<ImageView>(R.id.chipIcon)
            icon.setImageResource(when (chip.icon) {
                CarReadouts.Icon.DOOR -> R.drawable.au_door
                CarReadouts.Icon.WINDOW -> R.drawable.au_window
                CarReadouts.Icon.HOOD -> R.drawable.au_hood
                CarReadouts.Icon.TRUNK -> R.drawable.au_trunk
                CarReadouts.Icon.PLUG -> R.drawable.au_plug
                CarReadouts.Icon.GAUGE -> R.drawable.au_gauge
            })
            icon.tag = when (chip.level) { CarReadouts.Level.WARN -> "a_ic_warn"; CarReadouts.Level.ACCENT -> "a_ic_acc"; else -> "a_ic" }
            v.findViewById<TextView>(R.id.chipLabel).apply { text = chip.label; tag = if (chip.level == CarReadouts.Level.WARN) "a_warn" else "a_muted" }
            v.findViewById<TextView>(R.id.chipValue).apply {
                text = chip.value
                tag = when (chip.level) { CarReadouts.Level.WARN -> "a_warn"; CarReadouts.Level.ACCENT -> "a_accent"; CarReadouts.Level.UNKNOWN -> "a_muted"; else -> "a_ink" }
            }
            v.contentDescription = "${chip.label}: ${chip.value}"
            painter.apply(v)
            box.addView(v)
        }
    }

    // ── Atalhos e janelas ───────────────────────────────────────────────────

    private fun setupHome() {
        click(R.id.tileNav) { togglePopover(PopoverState.Id.NAV) }
        click(R.id.tileBattery) { togglePopover(PopoverState.Id.BATTERY) }
        click(R.id.tileClimate) { togglePopover(PopoverState.Id.CLIMATE) }
        click(R.id.tileMedia) { togglePopover(PopoverState.Id.MEDIA) }
        click(R.id.tileMediaPlay) { playOrResume() }
        click(R.id.tileMediaNext) { MediaRepository.next() }
        click(R.id.carChips) { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        click(R.id.lockPill) { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        click(R.id.weatherRow) { startActivity(Intent(this, WeatherActivity::class.java)) } // issue #4
        click(R.id.setupButton) { runSetup() }
        click(R.id.themeButton) { toggleTheme() }
    }

    private fun setupDock() {
        click(R.id.dockHome) { closePopover(); showPage(PAGE_HOME) }
        click(R.id.dockPanel) { togglePopover(PopoverState.Id.PANEL) }
        click(R.id.dockNavApp) {
            closePopover()
            if (prefs.navApp == "native") showPage(PAGE_NAV)
            else if (!Places.openApp(this, prefs.navApp)) toast(getString(R.string.app_missing))
        }
        click(R.id.dockClimate) { togglePopover(PopoverState.Id.CLIMATE) }
        click(R.id.dockMedia) { togglePopover(PopoverState.Id.MEDIA) }
        click(R.id.dockTempDriver) { showPopover(PopoverState.Id.CLIMATE) }
        click(R.id.dockTempPassenger) { showPopover(PopoverState.Id.CLIMATE) }
        // Discador próprio: a DiLink nem sempre tem app para ACTION_DIAL (issue #3).
        click(R.id.tabPhone) { closePopover(); startActivity(Intent(this, DialerActivity::class.java)) }
        click(R.id.tabApps) { closePopover(); showPage(if (page == PAGE_APPS) PAGE_HOME else PAGE_APPS) }
        click(R.id.tabSettings) { closePopover(); startActivity(Intent(this, SettingsActivity::class.java)) }
        // Segurar mostra o nome (os botões do dock são só ícone, como no Aura).
        listOf(R.id.dockHome, R.id.dockPanel, R.id.dockNavApp, R.id.dockClimate, R.id.tabPhone, R.id.tabApps, R.id.tabSettings, R.id.dockMedia)
            .forEach { id -> findViewById<View>(id)?.let { it.tooltipText = it.contentDescription } }
    }

    private fun setupPopovers() {
        val inflater = LayoutInflater.from(this)
        fun inflate(layout: Int) = inflater.inflate(layout, findViewById<FrameLayout>(R.id.popover), false)
        popViews = mapOf(
            PopoverState.Id.NAV to inflate(R.layout.pop_nav),
            PopoverState.Id.BATTERY to inflate(R.layout.pop_battery),
            PopoverState.Id.CLIMATE to inflate(R.layout.pop_climate),
            PopoverState.Id.MEDIA to inflate(R.layout.pop_media),
            PopoverState.Id.PANEL to inflate(R.layout.pop_panel),
        )
        popViews.forEach { (id, v) -> popWidths[id] = v.layoutParams.width }
        findViewById<View>(R.id.popScrim).setOnClickListener { closePopover() }

        val nav = popViews.getValue(PopoverState.Id.NAV)
        nav.findViewById<View>(R.id.popNavClose).setOnClickListener { closePopover() }
        nav.findViewById<View>(R.id.popNavSearch).setOnClickListener { closePopover(); showPage(PAGE_NAV); focusSearch() }
        nav.findViewById<View>(R.id.popNavRouteAction).setOnClickListener { activeRouteAction() }
        nav.findViewById<View>(R.id.popNavHome).apply {
            setOnClickListener { closePopover(); goSaved("home") }
            setOnLongClickListener { closePopover(); showPage(PAGE_NAV); setPickMode("home"); true }
        }
        nav.findViewById<View>(R.id.popNavWork).apply {
            setOnClickListener { closePopover(); goSaved("work") }
            setOnLongClickListener { closePopover(); showPage(PAGE_NAV); setPickMode("work"); true }
        }
        mapOf(R.id.popCatCharge to R.string.cat_charge_query, R.id.popCatFuel to R.string.cat_fuel_query, R.id.popCatParking to R.string.cat_parking_query)
            .forEach { (id, q) -> nav.findViewById<View>(id).setOnClickListener { closePopover(); searchCategory(getString(q)) } }

        val bat = popViews.getValue(PopoverState.Id.BATTERY)
        bat.findViewById<View>(R.id.popBatteryClose).setOnClickListener { closePopover() }
        bat.findViewById<View>(R.id.popBatteryMore).setOnClickListener { closePopover(); startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        bat.findViewById<View>(R.id.popBatteryEmpty).setOnClickListener { closePopover(); startActivity(Intent(this, SettingsActivity::class.java)) }

        val clim = popViews.getValue(PopoverState.Id.CLIMATE)
        clim.findViewById<View>(R.id.popClimateClose).setOnClickListener { closePopover() }
        clim.findViewById<View>(R.id.popClimateEmpty).setOnClickListener { closePopover(); startActivity(Intent(this, SettingsActivity::class.java)) }
        listOf(
            Triple(R.id.modeAuto, R.drawable.au_auto, R.string.mode_auto), Triple(R.id.modeAc, R.drawable.au_snow, R.string.mode_ac),
            Triple(R.id.modeRecirc, R.drawable.au_recirc, R.string.mode_recirc), Triple(R.id.modeDefrost, R.drawable.au_def_f, R.string.mode_defrost),
        ).forEach { (id, icon, label) ->
            clim.findViewById<View>(id).apply {
                findViewById<ImageView>(R.id.modeIcon).setImageResource(icon)
                findViewById<TextView>(R.id.modeLabel).setText(label)
            }
        }
        clim.findViewById<View>(R.id.popClimateDriver).findViewById<TextView>(R.id.tempLabel).setText(R.string.climate_driver)
        clim.findViewById<View>(R.id.popClimatePassenger).findViewById<TextView>(R.id.tempLabel).setText(R.string.climate_passenger)
        clim.findViewById<View>(R.id.seatDriver).findViewById<TextView>(R.id.seatLabel).setText(R.string.seat_driver)
        clim.findViewById<View>(R.id.seatPassenger).findViewById<TextView>(R.id.seatLabel).setText(R.string.seat_passenger)
        clim.findViewById<LevelBars>(R.id.popClimateFan).apply { count = 7; rising = true }

        val media = popViews.getValue(PopoverState.Id.MEDIA)
        media.findViewById<View>(R.id.popMediaClose).setOnClickListener { closePopover() }
        media.findViewById<View>(R.id.popMediaOpen).setOnClickListener { closePopover(); openMedia() }
        media.findViewById<View>(R.id.popMediaPrev).setOnClickListener { MediaRepository.previous() }
        media.findViewById<View>(R.id.popMediaNext).setOnClickListener { MediaRepository.next() }
        media.findViewById<View>(R.id.popMediaPlay).setOnClickListener { playOrResume() }
        media.findViewById<View>(R.id.popVolDown).setOnClickListener { adjustVolume(AudioManager.ADJUST_LOWER) }
        media.findViewById<View>(R.id.popVolUp).setOnClickListener { adjustVolume(AudioManager.ADJUST_RAISE) }

        val panel = popViews.getValue(PopoverState.Id.PANEL)
        panel.findViewById<View>(R.id.popPanelClose).setOnClickListener { closePopover() }
        panel.findViewById<View>(R.id.panelToggle).setOnClickListener { toggleCluster() }
        panel.findViewById<View>(R.id.panelSettings).setOnClickListener { closePopover(); startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun togglePopover(id: PopoverState.Id) {
        if (popovers.open == id) closePopover() else showPopover(id)
    }

    /** Abre a janela [id] ancorada no atalho dela (a do painel, no botão do dock). Fecha a anterior. */
    private fun showPopover(id: PopoverState.Id) {
        if (id != PopoverState.Id.PANEL && page != PAGE_HOME) showPage(PAGE_HOME)
        popovers.show(id)
        val frame = findViewById<PopoverFrame>(R.id.popover)
        val layer = findViewById<FrameLayout>(R.id.popLayer)
        val content = popViews.getValue(id)
        frame.animate().cancel()
        frame.removeAllViews()
        val margin = (AuraDims.SPACE_5 * density).toInt()
        val maxW = (layer.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - 2 * margin
        content.layoutParams = FrameLayout.LayoutParams(minOf(popWidths.getValue(id), maxW), ViewGroup.LayoutParams.WRAP_CONTENT)
        frame.addView(content)
        painter.apply(frame)
        when (id) {
            PopoverState.Id.NAV -> renderNav(NavRepository.state.value)
            PopoverState.Id.BATTERY -> renderBattery()
            PopoverState.Id.CLIMATE -> renderClimate()
            PopoverState.Id.MEDIA -> { renderMedia(MediaRepository.now.value); renderVolume() }
            PopoverState.Id.PANEL -> renderCluster(ClusterService.status.value)
        }
        layer.visibility = View.VISIBLE
        frame.visibility = View.INVISIBLE
        highlight()
        frame.post { placePopover(id) }
    }

    private fun placePopover(id: PopoverState.Id) {
        if (popovers.open != id) return
        val frame = findViewById<PopoverFrame>(R.id.popover)
        val layer = findViewById<FrameLayout>(R.id.popLayer)
        val anchor = findViewById<View>(anchorOf(id))
        val la = IntArray(2).also(layer::getLocationInWindow)
        val aa = IntArray(2).also(anchor::getLocationInWindow)
        val margin = (AuraDims.SPACE_5 * density).toInt()
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(layer.width - 2 * margin, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(layer.height, View.MeasureSpec.AT_MOST),
        )
        val cx = aa[0] - la[0] + anchor.width / 2
        val spot = PopoverAnchor.place(cx, frame.measuredWidth, layer.width, margin, (AuraDims.RADIUS_XL * density + 18 * density).toInt())
        val top = (aa[1] - la[1] - (6 * density).toInt() - frame.measuredHeight).coerceAtLeast(((AuraDims.STATUS_HEIGHT + 8) * density).toInt())
        frame.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = spot.left
            topMargin = top
        }
        frame.caretX = spot.caretX.toFloat()
        frame.visibility = View.VISIBLE
        // Entrada de 180 ms (fade + 10 dp para cima); com animações desligadas no Android, aparece direto.
        if (animationsOn()) {
            frame.alpha = 0f
            frame.translationY = AuraDims.POPOVER_RISE_DP * density
            frame.animate().alpha(1f).translationY(0f).setDuration(AuraDims.POPOVER_IN_MS).setInterpolator(DecelerateInterpolator()).start()
        } else { frame.alpha = 1f; frame.translationY = 0f }
    }

    private fun anchorOf(id: PopoverState.Id) = when (id) {
        PopoverState.Id.NAV -> R.id.tileNav
        PopoverState.Id.BATTERY -> R.id.tileBattery
        PopoverState.Id.CLIMATE -> R.id.tileClimate
        PopoverState.Id.MEDIA -> R.id.tileMedia
        PopoverState.Id.PANEL -> R.id.dockPanel
    }

    /** Fecha a janela aberta (toque fora, X, Início, voltar). true se havia uma. */
    private fun closePopover(): Boolean {
        if (!popovers.close()) return false
        val frame = findViewById<PopoverFrame>(R.id.popover)
        frame.animate().cancel()
        frame.removeAllViews()
        findViewById<View>(R.id.popLayer).visibility = View.GONE
        highlight()
        return true
    }

    /** Tile aberto com borda accent; botões do dock acesos conforme a janela/página/painel. */
    private fun highlight() {
        if (!::painter.isInitialized) return
        val open = popovers.open
        painter.set(findViewById(R.id.tileNav), if (open == PopoverState.Id.NAV) "a_tile_on" else "a_tile")
        painter.set(findViewById(R.id.tileBattery), if (open == PopoverState.Id.BATTERY) "a_tile_on" else "a_tile")
        painter.set(findViewById(R.id.tileClimate), if (open == PopoverState.Id.CLIMATE) "a_tile_on" else "a_tile")
        painter.set(findViewById(R.id.tileMedia), if (open == PopoverState.Id.MEDIA) "a_tile_on" else "a_tile")
        val panelOn = open == PopoverState.Id.PANEL || ClusterService.status.value is ClusterStatus.Active
        fun db(id: Int, on: Boolean) = findViewById<View>(id)?.let { painter.set(it, if (on) "a_db_on" else "a_db") }
        db(R.id.dockHome, open == null && page == PAGE_HOME)
        db(R.id.dockPanel, panelOn)
        db(R.id.dockNavApp, open == null && page == PAGE_NAV)
        db(R.id.dockClimate, open == PopoverState.Id.CLIMATE)
        db(R.id.dockMedia, open == PopoverState.Id.MEDIA)
        db(R.id.tabApps, open == null && page == PAGE_APPS)
        db(R.id.tabPhone, false)
        db(R.id.tabSettings, false)
    }

    // ── Bateria ─────────────────────────────────────────────────────────────

    private fun renderBattery() {
        val b = CarReadouts.battery(lastSdk, lastTelemetry)
        // Atalho
        val big = findViewById<TextView>(R.id.tileBatteryBig)
        val small = findViewById<TextView>(R.id.tileBatterySmall)
        val bar = findViewById<ProgressBar>(R.id.tileBatteryBar)
        if (b.hasAny) {
            big.text = b.totalKm?.toString() ?: AuraFormat.percent(b.soc)
            small.text = listOfNotNull(b.totalKm?.let { "km" }, b.soc?.takeIf { b.totalKm != null }?.let(AuraFormat::percent)).joinToString(" · ")
            bar.visibility = if (b.soc == null) View.GONE else View.VISIBLE
            bar.progress = b.soc?.toInt() ?: 0
            findViewById<TextView>(R.id.tileBatterySub).text = when {
                b.charging && b.charge != null -> b.charge
                b.evKm != null && b.fuelKm != null -> getString(R.string.range_split, b.evKm, b.fuelKm)
                b.evKm != null -> getString(R.string.range_ev, b.evKm)
                else -> getString(R.string.battery_title)
            }
        } else {
            big.setText(R.string.no_data)
            small.text = ""
            bar.visibility = View.GONE
            findViewById<TextView>(R.id.tileBatterySub).text = carDataHint(short = true)
        }
        big.textSize = if (b.hasAny) 30f else 22f
        // Barra de status
        findViewById<View>(R.id.statusBattery).visibility = if (b.soc == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.statusBatteryText).text = AuraFormat.percent(b.soc)
        // Janela
        val pop = popViews.getValue(PopoverState.Id.BATTERY)
        pop.findViewById<View>(R.id.popBatteryEmpty).visibility = if (b.hasAny) View.GONE else View.VISIBLE
        pop.findViewById<TextView>(R.id.popBatteryEmpty).text = carDataHint()
        pop.findViewById<View>(R.id.popBatteryBody).visibility = if (b.hasAny) View.VISIBLE else View.GONE
        if (!b.hasAny) return
        pop.findViewById<RingView>(R.id.popBatteryRing).percent = b.soc
        pop.findViewById<TextView>(R.id.popBatterySoc).text = AuraFormat.percent(b.soc)
        pop.findViewById<TextView>(R.id.popBatteryTotal).text = b.totalKm?.let { "$it km" } ?: "—"
        val charge = pop.findViewById<TextView>(R.id.popBatteryCharge)
        val chargeIcon = pop.findViewById<ImageView>(R.id.popBatteryChargeIcon)
        charge.text = b.charge ?: when (b.plugged) { false -> getString(R.string.charge_unplugged); else -> getString(R.string.charge_unknown) }
        painter.set(charge, if (b.charging) "a_accent" else "a_muted")
        chargeIcon.setImageResource(if (b.charging) R.drawable.au_bolt else R.drawable.au_plug)
        painter.set(chargeIcon, if (b.charging) "a_icon_accent" else "a_icon_muted")
        fun stat(id: Int, icon: Int, label: Int, value: String) = pop.findViewById<View>(id).apply {
            findViewById<ImageView>(R.id.statIcon).setImageResource(icon)
            findViewById<TextView>(R.id.statLabel).setText(label)
            findViewById<TextView>(R.id.statValue).text = value
        }
        stat(R.id.popBatteryEv, R.drawable.au_bolt, R.string.range_ev_label, b.evKm?.let { "$it km" } ?: "—")
        stat(R.id.popBatteryFuel, R.drawable.au_fuel, R.string.range_fuel_label, b.fuelKm?.let { "$it km" } ?: "—")
        // Linhas: só o que o carro informou.
        val rows = pop.findViewById<LinearLayout>(R.id.popBatteryRows)
        rows.removeAllViews()
        val t = lastTelemetry?.takeIf { it.status == TelemetrySnapshot.Status.OK }
        val items = listOfNotNull(
            b.fuelPercent?.let { Triple(getString(R.string.fuel_level), null, "$it%") },
            t?.value("gun")?.let { Triple(getString(R.string.charge_cable), null, AuraFormat.capitalized(TelemetryText.format(TelemetryCatalog.Kind.GUN, it))) },
            b.remainingMin?.let { Triple(getString(R.string.charge_remaining), null, AuraFormat.duration(it)) },
            t?.value("power_kw")?.let { Triple(getString(R.string.battery_power), getString(R.string.battery_power_sub), "${it.toInt()} kW") },
            b.volt12?.let { Triple(getString(R.string.battery_12v), null, String.format(BR, "%.1f V", it)) },
        )
        items.forEachIndexed { i, (title, sub, value) ->
            val row = layoutInflater.inflate(R.layout.pop_row, rows, false)
            row.findViewById<TextView>(R.id.rowTitle).text = title
            row.findViewById<TextView>(R.id.rowSub).apply { text = sub; visibility = if (sub == null) View.GONE else View.VISIBLE }
            row.findViewById<TextView>(R.id.rowValue).text = value
            rows.addView(row)
            if (i < items.lastIndex) rows.addView(View(this).apply { tag = "a_divider" }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.toInt().coerceAtLeast(1)))
        }
        if (items.isEmpty()) rows.addView(TextView(this).apply {
            setText(R.string.battery_no_more); textSize = 13f; tag = "a_muted"
        })
        painter.apply(rows)
    }

    /** Texto do estado vazio: diz por que não há dado (desligado em Ajustes, sem ADB…). */
    private fun carDataHint(short: Boolean = false): String {
        val off = getString(if (short) R.string.car_data_off_short else R.string.car_data_off)
        val t = lastTelemetry ?: return off
        return when (t.status) {
            TelemetrySnapshot.Status.OFF -> off
            TelemetrySnapshot.Status.WAITING -> t.status.label
            TelemetrySnapshot.Status.OK -> getString(R.string.car_data_unavailable)
            TelemetrySnapshot.Status.NO_ADB -> if (short) getString(R.string.car_data_no_adb) else t.message
            else -> if (short) getString(R.string.car_data_error) else t.message
        }
    }

    // ── Climatização (só mostra) ────────────────────────────────────────────

    private fun renderClimate() {
        val c = CarReadouts.climate(lastTelemetry)
        // Atalho
        val big = findViewById<TextView>(R.id.tileClimateBig)
        if (c?.driverC != null) {
            big.text = AuraFormat.temp(c.driverC); big.textSize = 30f
            findViewById<TextView>(R.id.tileClimateSmall).text = c.passengerC?.let { getString(R.string.climate_pass_short, AuraFormat.temp(it)) } ?: ""
        } else {
            big.setText(R.string.no_data); big.textSize = 22f
            findViewById<TextView>(R.id.tileClimateSmall).text = ""
        }
        findViewById<TextView>(R.id.tileClimateSub).text = c?.tileLine ?: if (c == null) carDataHint(short = true) else getString(R.string.read_only)
        // Dock e barra de status
        findViewById<TextView>(R.id.dockTempDriverValue)?.text = AuraFormat.temp(c?.driverC)
        findViewById<TextView>(R.id.dockTempPassengerValue)?.text = AuraFormat.temp(c?.passengerC)
        findViewById<View>(R.id.statusClimate).visibility = if (c?.driverC == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.statusClimateText).text = AuraFormat.temp(c?.driverC)
        // Janela
        val pop = popViews.getValue(PopoverState.Id.CLIMATE)
        pop.findViewById<View>(R.id.popClimateEmpty).visibility = if (c == null) View.VISIBLE else View.GONE
        pop.findViewById<TextView>(R.id.popClimateEmpty).text = carDataHint()
        pop.findViewById<View>(R.id.popClimateBody).visibility = if (c == null) View.GONE else View.VISIBLE
        if (c == null) return
        pop.findViewById<View>(R.id.popClimateDriver).findViewById<TextView>(R.id.tempValue).text = AuraFormat.temp(c.driverC)
        pop.findViewById<View>(R.id.popClimatePassenger).findViewById<TextView>(R.id.tempValue).text = AuraFormat.temp(c.passengerC)
        pop.findViewById<TextView>(R.id.popClimateFanLabel).text =
            if (c.fan != null) getString(R.string.fan_level, c.fan) else getString(R.string.fan_unknown)
        pop.findViewById<LevelBars>(R.id.popClimateFan).level = c.fan ?: 0
        pop.findViewById<TextView>(R.id.popClimateAir).apply {
            text = listOfNotNull(c.insideC?.let { getString(R.string.temp_inside, AuraFormat.tempWhole(it)) },
                c.outsideC?.let { getString(R.string.temp_outside, AuraFormat.tempWhole(it)) }).joinToString(" · ")
            visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        fun mode(id: Int, on: Boolean?) = pop.findViewById<View>(id).apply {
            visibility = if (on == null) View.GONE else View.VISIBLE
            tag = if (on == true) "a_mode_on" else "a_mode_off"
            findViewById<View>(R.id.modeIcon).tag = if (on == true) "a_icon_accent" else "a_icon"
            findViewById<TextView>(R.id.modeLabel).let { it.tag = if (on == true) "a_accent" else "a_muted"; contentDescription = "${it.text}: ${getString(if (on == true) R.string.state_on else R.string.state_off)}" }
            painter.apply(this)
        }
        mode(R.id.modeAuto, c.auto); mode(R.id.modeAc, c.ac); mode(R.id.modeRecirc, c.recirc); mode(R.id.modeDefrost, c.defrost)
        pop.findViewById<View>(R.id.popClimateModes).visibility = if (listOf(c.auto, c.ac, c.recirc, c.defrost).all { it == null }) View.GONE else View.VISIBLE
        fun seat(id: Int, s: CarReadouts.Seat) = pop.findViewById<View>(id).apply {
            fun part(box: Int, icon: Int, bars: Int, level: Int?, onTag: String, iconTag: String, color: Int, name: Int) {
                val b = findViewById<View>(box)
                b.tag = if ((level ?: 0) > 0) onTag else "a_seat_off"
                b.alpha = if (level == null) 0.45f else 1f
                findViewById<View>(icon).tag = if ((level ?: 0) > 0) iconTag else "a_icon_muted"
                findViewById<LevelBars>(bars).apply { count = 3; rising = false; onColor = color; this.level = level ?: 0 }
                b.contentDescription = getString(name) + ": " + when { level == null -> getString(R.string.no_data); level == 0 -> getString(R.string.state_off); else -> getString(R.string.level_n, level) }
            }
            part(R.id.seatHeat, R.id.seatHeatIcon, R.id.seatHeatLevel, s.heat, "a_heat_on", "a_icon_heat", colors.heat, R.string.seat_heat)
            part(R.id.seatVent, R.id.seatVentIcon, R.id.seatVentLevel, s.vent, "a_cool_on", "a_icon_cool", colors.cool, R.string.seat_vent)
            painter.apply(this)
        }
        seat(R.id.seatDriver, c.driverSeat); seat(R.id.seatPassenger, c.passengerSeat)
        pop.findViewById<View>(R.id.popClimateSeats).visibility =
            if (listOf(c.driverSeat.heat, c.driverSeat.vent, c.passengerSeat.heat, c.passengerSeat.vent).all { it == null }) View.GONE else View.VISIBLE
    }

    // ── Mídia ───────────────────────────────────────────────────────────────

    private fun renderMedia(m: MediaRepository.NowPlaying?) {
        val playing = m?.playing == true
        val hasTrack = m != null && !m.title.isNullOrBlank()
        val app = m?.appPackage?.let(::appLabel)
        findViewById<TextView>(R.id.tileMediaTitle).text = if (hasTrack) m!!.title else getString(R.string.media_nothing)
        findViewById<TextView>(R.id.tileMediaSub).text = if (hasTrack) listOfNotNull(m!!.artist, app).joinToString(" · ") else getString(R.string.media_open)
        val icon = if (playing) R.drawable.au_pause else R.drawable.au_play
        findViewById<ImageView>(R.id.tileMediaPlay).setImageResource(icon)
        val pop = popViews.getValue(PopoverState.Id.MEDIA)
        pop.findViewById<TextView>(R.id.popMediaTitle).text = if (hasTrack) m!!.title else getString(R.string.media_nothing)
        pop.findViewById<TextView>(R.id.popMediaArtist).text = if (hasTrack) m!!.artist.orEmpty() else getString(R.string.media_pick_app)
        pop.findViewById<TextView>(R.id.popMediaSource).apply { text = app.orEmpty(); visibility = if (app == null) View.GONE else View.VISIBLE }
        pop.findViewById<ImageView>(R.id.popMediaPlay).setImageResource(icon)
        // Sem sessão de mídia, anterior/próxima não fazem nada: ficam apagados (tocar retoma o último app).
        listOf(findViewById<View>(R.id.tileMediaNext), pop.findViewById(R.id.popMediaPrev), pop.findViewById(R.id.popMediaNext))
            .forEach { it.isEnabled = m != null; it.alpha = if (m != null) 1f else 0.4f }
        val art = pop.findViewById<ImageView>(R.id.popMediaArt)
        if (m?.art != null) { art.setImageBitmap(m.art); art.visibility = View.VISIBLE } else { art.setImageDrawable(null); art.visibility = View.GONE }
        pop.findViewById<EqualizerView>(R.id.popMediaEq).playing = playing && m?.art == null
    }

    private fun appLabel(pkg: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun openMedia() {
        val pkg = MediaRepository.now.value?.appPackage
    /** Tocar/pausar dentro da interface; sem sessão, retoma o último app de mídia sem abri-lo. */
    private fun playOrResume() {
        if (MediaRepository.now.value == null) MediaRepository.resumeLast(this) else MediaRepository.playPause()
    }

    /** "Abrir app" da janela Mídia: função antiga (cartão Mídia do cockpit), mantida para o Paulo decidir. */
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        startSafely(launch)
    }

    /** Volume da mídia do Android (o mesmo das teclas de volume); não é comando ao carro. */
    private fun adjustVolume(direction: Int) {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
        renderVolume()
    }

    private fun renderVolume() {
        if (!::popViews.isInitialized) return
        val am = getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pop = popViews.getValue(PopoverState.Id.MEDIA)
        pop.findViewById<ProgressBar>(R.id.popVolBar).apply { this.max = max; progress = cur }
        pop.findViewById<TextView>(R.id.popVolValue).text = cur.toString()
    }

    // ── Painel de instrumentos ──────────────────────────────────────────────

    private fun renderCluster(st: ClusterStatus) {
        val active = st is ClusterStatus.Active
        val title = getString(when (st) {
            is ClusterStatus.Active -> R.string.panel_on
            ClusterStatus.Working -> R.string.panel_working
            ClusterStatus.Yielded -> R.string.panel_yielded
            else -> R.string.panel_native
        })
        findViewById<TextView>(R.id.statusPanel).text = getString(R.string.status_panel, title)
        val pop = popViews.getValue(PopoverState.Id.PANEL)
        pop.findViewById<TextView>(R.id.panelTitle).text = title
        val sub = pop.findViewById<TextView>(R.id.panelSub)
        sub.text = when (st) {
            is ClusterStatus.Failed -> st.reason
            ClusterStatus.Yielded -> getString(R.string.panel_yielded_hint)
            is ClusterStatus.Active -> getString(if (prefs.clusterMode == "nav") R.string.panel_active_nav else R.string.panel_active)
            else -> getString(R.string.panel_auto_hint)
        }
        sub.setTextColor(if (st is ClusterStatus.Failed) colors.alert else colors.inkMuted)
        pop.findViewById<TextView>(R.id.panelToggle).apply {
            setText(if (active) R.string.cluster_restore_action else R.string.cluster_show)
            painter.set(this, if (active) "a_sunken_pill" else "a_primary_pill")
        }
        painter.set(pop.findViewById(R.id.panelIcon), if (active) "a_badge_on" else "a_badge")
        highlight()
    }

    private fun toggleCluster() {
        val action = if (ClusterService.status.value is ClusterStatus.Active) ClusterService.ACTION_RESTORE else ClusterService.ACTION_SHOW
        ClusterService.start(this, action)
    }

    // ── Navegação ───────────────────────────────────────────────────────────

    private fun renderNav(s: NavState) {
        val nav = s.nav
        val active = s.active && nav != null
        // Atalho
        findViewById<View>(R.id.tileNavFavs).visibility = if (active) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tileNavRoute).visibility = if (active) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tileNavBadge).visibility = if (active) View.INVISIBLE else View.VISIBLE
        val tileMan = findViewById<ManeuverView>(R.id.tileNavManeuver)
        tileMan.visibility = if (active) View.VISIBLE else View.GONE
        val pop = popViews.getValue(PopoverState.Id.NAV)
        pop.findViewById<TextView>(R.id.popNavApp).text = getString(R.string.route_by, navAppName())
        pop.findViewById<View>(R.id.popNavRoute).visibility = if (active) View.VISIBLE else View.GONE
        pop.findViewById<View>(R.id.popNavCats).visibility = if (active) View.GONE else View.VISIBLE
        val box = findViewById<View>(R.id.navActive)
        box.visibility = if (active) View.VISIBLE else View.GONE
        if (!active || nav == null) return
        val eta = listOfNotNull(nav.etaClock?.let { getString(R.string.arrival, it) }, nav.remainingDistanceText).joinToString(" · ")
        fun ManeuverView.show() { tint = true; color = colors.onAccent; bitmap = s.maneuverIcon; maneuver = nav.maneuver; roundaboutExit = nav.roundaboutExit }
        tileMan.show()
        findViewById<TextView>(R.id.tileNavDistance).text = nav.distanceText ?: nav.instruction
        findViewById<TextView>(R.id.tileNavSub).text = listOfNotNull(nav.street ?: nav.instruction.takeIf { nav.distanceText != null }, eta.ifEmpty { null }).joinToString(" · ")
        pop.findViewById<ManeuverView>(R.id.popNavManeuver).show()
        pop.findViewById<TextView>(R.id.popNavRouteTitle).text = listOfNotNull(nav.distanceText, nav.street ?: nav.instruction).joinToString(" · ")
        pop.findViewById<TextView>(R.id.popNavRouteSub).text = listOfNotNull(eta.ifEmpty { null }, s.sourceLabel.ifEmpty { null }).joinToString(" · ")
        pop.findViewById<TextView>(R.id.popNavRouteAction).text =
            if (s.sourcePackage == NativeNavigation.PACKAGE) getString(R.string.route_end) else getString(R.string.route_open, s.sourceLabel.ifEmpty { getString(R.string.nav_title) })
        // Busca: cartão da rota atual.
        findViewById<ManeuverView>(R.id.navActiveManeuver).show()
        findViewById<TextView>(R.id.navActiveDistance).text = nav.distanceText ?: ""
        findViewById<TextView>(R.id.navActiveStreet).text = nav.street ?: nav.instruction
        findViewById<TextView>(R.id.navActiveEta).text = listOfNotNull(eta.ifEmpty { null }, s.sourceLabel.ifEmpty { null }).joinToString(" · ")
    }

    /** Rota ativa: a nativa pergunta se encerra; a do Maps/Waze abre o app (como antes). */
    private fun activeRouteAction() {
        val pkg = NavRepository.state.value.sourcePackage
        if (pkg == NativeNavigation.PACKAGE) {
            AlertDialog.Builder(this).setMessage(R.string.route_end_question)
                .setPositiveButton(R.string.route_end) { _, _ -> NativeNavigation.stop(); closePopover() }
                .setNegativeButton(R.string.route_continue, null).show()
            return
        }
        closePopover()
        val i = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (i != null) startSafely(i) else Places.openApp(this, prefs.navApp)
    }

    /** Casa e Trabalho: distância em linha reta até lá (sem rota calculada não há minutos de verdade). */
    private fun renderFavorites() {
        if (!::popViews.isInitialized) return
        val loc = LocationRepository.location.value
        fun text(p: Place?, legacy: String): String = when {
            p != null -> p.distanceKm(loc)?.let(AuraFormat::km) ?: getString(R.string.fav_saved)
            legacy.isNotBlank() -> getString(R.string.fav_saved)
            else -> getString(R.string.fav_set)
        }
        val home = Places.home(this); val work = Places.work(this)
        findViewById<TextView>(R.id.tileNavHome).text = text(home, prefs.homeAddress)
        findViewById<TextView>(R.id.tileNavWork).text = text(work, prefs.workAddress)
        val pop = popViews.getValue(PopoverState.Id.NAV)
        fun fav(id: Int, p: Place?, legacy: String, name: Int, icon: Int) = pop.findViewById<View>(id).apply {
            findViewById<ImageView>(R.id.favIcon).setImageResource(icon)
            findViewById<TextView>(R.id.favName).setText(name)
            findViewById<TextView>(R.id.favDistance).text = p?.distanceKm(loc)?.let(AuraFormat::km) ?: ""
            findViewById<TextView>(R.id.favSub).text = p?.address?.ifEmpty { p.name } ?: legacy.ifEmpty { getString(R.string.fav_tap_to_set) }
        }
        fav(R.id.popNavHome, home, prefs.homeAddress, R.string.go_home, R.drawable.au_home)
        fav(R.id.popNavWork, work, prefs.workAddress, R.string.go_work, R.drawable.au_work)
    }

    private fun showPage(p: Int) {
        page = p
        findViewById<View>(R.id.viewHome).visibility = if (p == PAGE_HOME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.viewNav).visibility = if (p == PAGE_NAV) View.VISIBLE else View.GONE
        findViewById<View>(R.id.viewApps).visibility = if (p == PAGE_APPS) View.VISIBLE else View.GONE
        if (p == PAGE_NAV) { ensureNavMap(); renderPlaces() } else { hideKeyboard(); if (pickMode != null) setPickMode(null) }
        highlight()
    }

    private fun setupNavPage() {
        placesAdapter = PlacesAdapter(
            onClick = { row -> onPlaceClicked(row) },
            onLongClick = { row -> if (row.kind == "home" || row.kind == "work") { setPickMode(row.kind); true } else false },
        )
        findViewById<RecyclerView>(R.id.navList).apply {
            layoutManager = LinearLayoutManager(this@LauncherActivity)
            adapter = placesAdapter
        }
        val search = findViewById<EditText>(R.id.navSearch)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                findViewById<View>(R.id.navClear).visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                scheduleSearch(s?.toString().orEmpty(), 450)
            }
        })
        search.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                scheduleSearch(v.text.toString(), 0); true
            } else false
        }
        click(R.id.navClear) { search.setText("") }
        click(R.id.navBack) { showPage(PAGE_HOME) }
        click(R.id.navLocate) { updateNavMap() }
        click(R.id.appNative) { prefs.navApp = "native"; renderSegments() }
        click(R.id.appMaps) { prefs.navApp = "maps"; renderSegments() }
        click(R.id.appWaze) { prefs.navApp = "waze"; renderSegments() }
        click(R.id.navActive) { activeRouteAction() }
        click(R.id.catCharge) { searchCategory(getString(R.string.cat_charge_query)) }
        click(R.id.catFuel) { searchCategory(getString(R.string.cat_fuel_query)) }
        click(R.id.catParking) { searchCategory(getString(R.string.cat_parking_query)) }
    }

    /** Categoria = busca de verdade pelo termo, perto da posição (Photon). Nada de lista pronta. */
    private fun searchCategory(query: String) {
        showPage(PAGE_NAV)
        findViewById<EditText>(R.id.navSearch).setText(query)
    }

    private fun focusSearch() {
        val search = findViewById<EditText>(R.id.navSearch)
        search.requestFocus()
        search.post { getSystemService(InputMethodManager::class.java)?.showSoftInput(search, 0) }
    }

    private fun renderSegments() {
        if (!::painter.isInitialized) return
        listOf(R.id.appNative to "native", R.id.appMaps to "maps", R.id.appWaze to "waze").forEach { (id, app) ->
            painter.set(findViewById(id), if (prefs.navApp == app) "a_seg_on" else "a_seg_off")
        }
        findViewById<View>(R.id.dockNavApp)?.let {
            it.contentDescription = getString(R.string.open_app, navAppName())
            it.tooltipText = it.contentDescription
        }
        if (::popViews.isInitialized) popViews.getValue(PopoverState.Id.NAV).findViewById<TextView>(R.id.popNavApp).text = getString(R.string.route_by, navAppName())
    }

    private fun scheduleSearch(q: String, delayMs: Long) {
        searchJob?.cancel()
        if (q.isBlank()) { renderPlaces(); return }
        searchJob = lifecycleScope.launch {
            delay(delayMs)
            findViewById<TextView>(R.id.navListTitle).setText(R.string.results)
            val results = Places.search(q, LocationRepository.location.value)
            val rows = results.map { PlaceRow("result", it, null) }.ifEmpty {
                listOf(PlaceRow("text", Place(q, getString(R.string.search_in, navAppName()), 0.0, 0.0), null))
            }
            placesAdapter.submit(rows, painter, LocationRepository.location.value)
        }
    }

    private fun renderPlaces() {
        if (!::placesAdapter.isInitialized) return
        val q = findViewById<EditText>(R.id.navSearch).text?.toString().orEmpty()
        if (q.isNotBlank()) return
        findViewById<TextView>(R.id.navListTitle).setText(R.string.places)
        val rows = mutableListOf<PlaceRow>()
        rows += PlaceRow("home", Places.home(this), getString(if (hasHome()) R.string.go_home else R.string.set_home))
        rows += PlaceRow("work", Places.work(this), getString(if (hasWork()) R.string.go_work else R.string.set_work))
        Places.recents(this).forEach { rows += PlaceRow("recent", it, null) }
        placesAdapter.submit(rows, painter, LocationRepository.location.value)
    }

    private fun onPlaceClicked(row: PlaceRow) {
        val place = row.place
        when {
            pickMode != null && row.kind == "result" && place != null -> {
                if (pickMode == "home") Places.setHome(this, place) else Places.setWork(this, place)
                toast(getString(if (pickMode == "home") R.string.home_set else R.string.work_set, place.name))
                setPickMode(null)
                findViewById<EditText>(R.id.navSearch).setText("")
                renderNav(NavRepository.state.value)
                renderFavorites()
            }
            row.kind == "home" || row.kind == "work" -> goSaved(row.kind)
            row.kind == "text" && place != null -> {
                if (!Places.navigateText(this, place.name, prefs.navApp)) toast(getString(R.string.app_not_installed, navAppName()))
            }
            place != null -> {
                hideKeyboard()
                if (!Places.navigate(this, place, prefs.navApp)) toast(getString(R.string.app_not_installed, navAppName()))
            }
        }
    }

    /** Casa/Trabalho: vai direto se já definido; senão abre a busca para escolher. */
    private fun goSaved(kind: String) {
        val place = if (kind == "home") Places.home(this) else Places.work(this)
        val legacy = if (kind == "home") prefs.homeAddress else prefs.workAddress
        when {
            place != null -> if (!Places.navigate(this, place, prefs.navApp)) toast(getString(R.string.app_not_installed, navAppName()))
            legacy.isNotBlank() -> Places.navigateText(this, legacy, prefs.navApp)
            else -> { showPage(PAGE_NAV); setPickMode(kind) }
        }
    }

    private fun setPickMode(kind: String?) {
        pickMode = kind
        val banner = findViewById<TextView>(R.id.navPickBanner)
        banner.visibility = if (kind == null) View.GONE else View.VISIBLE
        if (kind != null) {
            banner.setText(if (kind == "home") R.string.pick_home else R.string.pick_work)
            focusSearch()
        }
    }

    private fun hasHome() = Places.home(this) != null || prefs.homeAddress.isNotBlank()
    private fun hasWork() = Places.work(this) != null || prefs.workAddress.isNotBlank()
    private fun navAppName() = getString(if (prefs.navApp == "native") R.string.open_native else if (prefs.navApp == "waze") R.string.open_waze else R.string.open_maps)

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(findViewById<View>(R.id.navSearch).windowToken, 0)
    }

    /** Mapa da busca: posição atual do carro e a rota nativa, no tema claro/escuro. Criado só ao abrir. */
    private fun ensureNavMap() {
        if (navMap != null) return
        val host = findViewById<FrameLayout>(R.id.navMapHost)
        host.clipToOutline = true
        val mv = MapView(this)
        host.addView(mv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val car = ImageView(this).apply { setImageDrawable(carArrow()) }
        val size = (44 * density).toInt()
        host.addView(car, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        navMap = mv
        navCar = car
        mv.onCreate(null)
        mv.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mv.onResume()
        mv.getMapAsync { m ->
            navMapLibre = m
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setStyle(Style.Builder().fromUri(mapStyleUri())) {
                NativeRouteLayer.draw(it)
                updateNavMap()
            }
        }
    }

    private fun updateNavMap() {
        val m = navMapLibre ?: return
        val loc = LocationRepository.location.value
        val target = loc?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(-23.5505, -46.6333)
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(if (loc != null) 15.5 else 11.0).build()))
        navCar?.visibility = if (loc != null) View.VISIBLE else View.INVISIBLE
        navCar?.rotation = loc?.bearing ?: 0f
    }

    private fun carArrow(): Drawable = br.painellivre.cluster.CarArrowDrawable()

    // ── Apps ────────────────────────────────────────────────────────────────

    private fun setupApps() {
        val grid = findViewById<RecyclerView>(R.id.appsGrid)
        val adapter = AppsAdapter { app ->
            startSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component))
        }
        grid.adapter = adapter
        grid.layoutManager = GridLayoutManager(this, 6)
        grid.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val span = (v.width / (density * 132)).toInt().coerceIn(3, 10)
            (grid.layoutManager as GridLayoutManager).let { if (it.spanCount != span) it.spanCount = span }
        }
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { AppsAdapter.loadApps(packageManager, packageName) }
            adapter.submit(apps) { v -> painter.apply(v) }
        }
    }

    // ── Configuração inicial ────────────────────────────────────────────────

    private fun refreshSetup() {
        val missing = mutableListOf<String>()
        if (!SystemSetup.hasNotificationAccess(this)) missing += getString(R.string.missing_notifications)
        if (!LocationRepository.hasPermission(this)) missing += getString(R.string.missing_location)
        if (adbOk == false) missing += getString(R.string.missing_adb)
        val card = findViewById<View>(R.id.setupCard)
        card.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.setupText).text = getString(R.string.missing_prefix, missing.joinToString(", "))
        if (adbOk == null && !adbTesting) {
            // Primeira conexão: a central mostra "Permitir depuração USB?" — aceite uma vez.
            adbTesting = true
            lifecycleScope.launch {
                val r = SelfAdb.shell(this@LauncherActivity, "echo ok")
                adbOk = r is SelfAdb.Result.Ok
                adbTesting = false
                refreshSetup()
            }
        }
    }

    private fun runSetup() {
        lifecycleScope.launch {
            val out = SystemSetup.grantAll(this@LauncherActivity)
            adbOk = !out.startsWith("ERRO")
            if (LocationRepository.hasPermission(this@LauncherActivity)) LocationRepository.start(this@LauncherActivity)
            MediaRepository.start(this@LauncherActivity)
            refreshSetup()
            if (!SystemSetup.hasNotificationAccess(this@LauncherActivity)) {
                AlertDialog.Builder(this@LauncherActivity).setTitle(R.string.setup_title).setMessage(
                    "$out\n" + getString(R.string.setup_notif_missing),
                ).setPositiveButton(android.R.string.ok) { _, _ ->
                    startSafely(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }.show()
            } else if (out.startsWith("ERRO")) {
                AlertDialog.Builder(this@LauncherActivity).setTitle(R.string.setup_title).setMessage(out)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private fun startSafely(i: Intent) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_app_for_this))
        } catch (e: SecurityException) {
            toast(getString(R.string.central_blocked, e.message.orEmpty()))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun click(id: Int, action: () -> Unit) = findViewById<View>(id)?.setOnClickListener { action() }

    // ── Lista de lugares ────────────────────────────────────────────────────

    data class PlaceRow(val kind: String, val place: Place?, val label: String?)

    private class PlacesAdapter(
        val onClick: (PlaceRow) -> Unit,
        val onLongClick: (PlaceRow) -> Boolean,
    ) : RecyclerView.Adapter<PlacesAdapter.Holder>() {
        private var rows: List<PlaceRow> = emptyList()
        private var painter: AuraPainter? = null
        private var from: android.location.Location? = null

        fun submit(list: List<PlaceRow>, p: AuraPainter, loc: android.location.Location?) {
            rows = list; painter = p; from = loc
            @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.placeIcon)
            val name: TextView = v.findViewById(R.id.placeName)
            val address: TextView = v.findViewById(R.id.placeAddress)
            val distance: TextView = v.findViewById(R.id.placeDistance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_place, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val row = rows[position]
            painter?.apply(h.itemView)
            h.icon.setImageResource(
                when (row.kind) {
                    "home" -> R.drawable.au_home
                    "work" -> R.drawable.au_work
                    "recent" -> R.drawable.au_clock
                    "text" -> R.drawable.au_search
                    else -> R.drawable.au_pin
                },
            )
            painter?.let { h.icon.imageTintList = android.content.res.ColorStateList.valueOf(it.c.accent) }
            val p = row.place
            h.name.text = row.label?.let { if (p != null) "$it · ${p.name}" else it } ?: p?.name.orEmpty()
            h.address.text = p?.address?.ifEmpty { null } ?: if (p == null) h.itemView.context.getString(R.string.fav_tap_to_pick) else ""
            h.address.visibility = if (h.address.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            val km = if (row.kind == "text") null else p?.distanceKm(from)
            h.distance.text = km?.let(AuraFormat::km) ?: ""
            h.itemView.setOnClickListener { onClick(row) }
            h.itemView.setOnLongClickListener { onLongClick(row) }
        }
    }

    companion object {
        private const val REQ_LOCATION = 10
        private const val PAGE_HOME = 0
        private const val PAGE_NAV = 1
        private const val PAGE_APPS = 2
        private val BR = Locale("pt", "BR")

        /** Centro de cada chip na área do carro (680 × 360): perto da peça na vista de cima. */
        private val CHIP_POS = mapOf(
            "doors" to (.34f to .07f),
            "windows" to (.5f to .93f),
            "hood" to (.08f to .5f),
            "trunk" to (.92f to .5f),
            "charge" to (.82f to .07f),
            "tyres" to (.16f to .93f),
        )
    }
}
