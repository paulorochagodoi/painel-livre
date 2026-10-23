package br.painellivre.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import br.painellivre.car.VehicleData
import br.painellivre.cluster.ClusterService
import br.painellivre.cluster.ClusterStatus
import br.painellivre.cluster.ManeuverView
import br.painellivre.info.MediaRepository
import br.painellivre.info.WeatherRepository
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.nav.NavState
import br.painellivre.nav.Place
import br.painellivre.nav.Places
import br.painellivre.ui.cockpit.BackdropDrawable
import br.painellivre.ui.cockpit.CarArtDrawable
import br.painellivre.ui.cockpit.Palette
import br.painellivre.ui.cockpit.WeatherIconDrawable
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
 * Tela central no estilo do cockpit do SealNav (docs/SEALNAV_RE.md): barra de cima com saudação, hora
 * e sinais; o carro com a autonomia e o tempo; quatro cartões (mídia, bateria, painel, destino);
 * e a barra de baixo com as abas. A única diferença de propósito: a rota é do Google Maps ou do
 * Waze — a aba Navegação busca o destino e abre a rota no app escolhido.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var palette: Palette = Palette.DAY
    private var paletteApplied = false
    private var adbOk: Boolean? = null
    private var adbTesting = false
    private var page = PAGE_HOME
    private val density get() = resources.displayMetrics.density

    // Navegação
    private lateinit var placesAdapter: PlacesAdapter
    private var searchJob: Job? = null
    private var pickMode: String? = null // "home" / "work" enquanto escolhe o endereço
    private var navMap: MapView? = null
    private var navMapLibre: MapLibreMap? = null
    private var navCar: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        prefs = Prefs(this)

        setupTabs()
        setupCards()
        setupNavPage()
        setupApps()
        findViewById<View>(R.id.hero).addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ ->
            if (r - l != or_ - ol) {
                val side = ((r - l) * 0.2f).toInt()
                findViewById<View>(R.id.carArt).setPadding(side, (24 * density).toInt(), side, (8 * density).toInt())
            }
        }

        try { ClusterService.start(this) } catch (t: Throwable) { /* app em segundo plano no boot */ }
        if (!LocationRepository.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
        applyPalette(force = true)
        showPage(PAGE_HOME)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { NavRepository.state.collect { renderNav(it) } }
                launch { ClusterService.status.collect { renderCluster(it) } }
                launch { MediaRepository.now.collect { renderMedia(it) } }
                launch { WeatherRepository.weather.collect { renderWeather(it) } }
                launch {
                    SelfAdb.ready.collect { if (it != null && it != adbOk) { adbOk = it; refreshSetup() } }
                }
                launch {
                    LocationRepository.location.collect { loc ->
                        loc ?: return@collect
                        WeatherRepository.refresh(loc)
                        updateNavMap()
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

    override fun onResume() {
        super.onResume()
        MediaRepository.start(this)
        applyPalette()
        refreshSetup()
        renderPlaces()
        navMap?.onResume()
    }

    override fun onStart() { super.onStart(); navMap?.onStart() }
    override fun onPause() { navMap?.onPause(); super.onPause() }
    override fun onStop() { navMap?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); navMap?.onLowMemory() }
    override fun onDestroy() { navMap?.onDestroy(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
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

    // ── Tema dia/noite ──────────────────────────────────────────────────────

    private fun applyPalette(force: Boolean = false) {
        val p = Palette.forSetting(prefs.theme)
        if (!force && paletteApplied && p == palette) return
        palette = p
        paletteApplied = true
        findViewById<ImageView>(R.id.backdrop).setImageDrawable(BackdropDrawable(p.dark))
        renderCarArt()
        p.apply(findViewById(R.id.root), density)
        highlightTab()
        renderCluster(ClusterService.status.value)
        renderSegments()
        navMapLibre?.setStyle(Style.Builder().fromUri(mapStyleUri()))
        navCar?.setImageDrawable(carArrow())
    }

    private fun renderCarArt() {
        val iv = findViewById<ImageView>(R.id.carArt)
        val photo = prefs.carPhoto
        if (photo != null) {
            try {
                contentResolver.openInputStream(Uri.parse(photo))?.use { iv.setImageDrawable(Drawable.createFromStream(it, "carro")) }
                return
            } catch (t: Throwable) { /* foto removida: volta à ilustração */ }
        }
        iv.setImageDrawable(CarArtDrawable(palette.dark))
    }

    private fun mapStyleUri() = "https://tiles.openfreemap.org/styles/" + if (palette.dark) "dark" else "positron"

    // ── Barra de cima, carro, cartões ───────────────────────────────────────

    private fun tick() {
        val now = Date()
        findViewById<TextView>(R.id.clock).text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        findViewById<TextView>(R.id.date).text = SimpleDateFormat("EEE, d 'de' MMM", Locale("pt", "BR")).format(now)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.greet).setText(
            when (hour) {
                in 5..11 -> R.string.good_morning
                in 12..17 -> R.string.good_afternoon
                else -> R.string.good_evening
            },
        )
        applyPalette()
        renderStatusIcons()
        lifecycleScope.launch { renderCar(withContext(Dispatchers.IO) { VehicleData.read(this@LauncherActivity) }) }
    }

    @SuppressLint("MissingPermission")
    private fun renderStatusIcons() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val wifi = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val bt = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
        val loc = LocationRepository.location.value
        val gps = loc != null && (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) < 15_000_000_000L
        findViewById<View>(R.id.statusWifi).alpha = if (wifi) 1f else 0.3f
        findViewById<View>(R.id.statusBt).alpha = if (bt) 1f else 0.3f
        findViewById<View>(R.id.statusGps).alpha = if (gps) 1f else 0.3f
    }

    private fun renderCar(s: VehicleData.Snapshot) {
        val total = listOfNotNull(s.evRangeKm, s.fuelRangeKm).takeIf { it.isNotEmpty() }?.sum()
        // Sem dados do carro (ou fora de uma BYD): some a autonomia em vez de mostrar traços.
        findViewById<View>(R.id.rangeBlock).visibility = if (total == null) View.INVISIBLE else View.VISIBLE
        findViewById<View>(R.id.batteryPct).visibility = if (s.batteryPercent == null) View.GONE else View.VISIBLE
        findViewById<View>(R.id.batteryBar).visibility = if (s.batteryPercent == null) View.INVISIBLE else View.VISIBLE
        findViewById<TextView>(R.id.batteryValue).textSize = if (s.batteryPercent == null) 24f else 46f
        findViewById<TextView>(R.id.rangeValue).text = total?.toString() ?: ""
        findViewById<TextView>(R.id.rangeSub).text = when {
            s.evRangeKm != null && s.fuelRangeKm != null -> "Elétrica ${s.evRangeKm} · Combustão ${s.fuelRangeKm}"
            s.evRangeKm != null -> "Autonomia elétrica"
            else -> "Autonomia"
        }
        val pct = s.batteryPercent
        findViewById<ProgressBar>(R.id.rangeBar).progress = pct?.toInt() ?: 0
        findViewById<TextView>(R.id.batteryValue).text = pct?.toInt()?.toString() ?: getString(R.string.battery)
        findViewById<ProgressBar>(R.id.batteryBar).progress = pct?.toInt() ?: 0
        findViewById<TextView>(R.id.batterySub).text = when {
            s.evRangeKm != null -> "Autonomia elétrica ${s.evRangeKm} km"
            else -> getString(R.string.car_data_unavailable)
        }
    }

    private fun renderWeather(w: WeatherRepository.Weather?) {
        val card = findViewById<View>(R.id.weatherCard)
        val top = findViewById<TextView>(R.id.topTemp)
        if (w == null) { card.visibility = View.GONE; top.text = ""; return }
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.weatherTemp).text = "${w.tempC}°"
        findViewById<TextView>(R.id.weatherDesc).text = w.description
        findViewById<ImageView>(R.id.weatherIcon).setImageDrawable(WeatherIconDrawable(w.kind, w.isDay, palette))
        top.text = "${w.tempC}°"
    }

    private fun renderMedia(m: MediaRepository.NowPlaying?) {
        val art = findViewById<ImageView>(R.id.mediaArt)
        val title = findViewById<TextView>(R.id.mediaTitle)
        val sub = findViewById<TextView>(R.id.mediaSub)
        if (m == null || m.title.isNullOrBlank()) {
            title.setText(R.string.media_choose)
            sub.setText(R.string.media_open)
            art.setImageResource(R.drawable.ic_music)
            art.tag = "icon_accent"; palette.apply(art, density)
            art.setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        } else {
            title.text = m.title
            sub.text = listOfNotNull(m.artist, appLabel(m.appPackage)).joinToString(" · ")
            if (m.art != null) {
                art.imageTintList = null
                art.setPadding(0, 0, 0, 0)
                art.setImageBitmap(m.art)
            } else {
                art.setImageResource(R.drawable.ic_music)
            }
        }
        findViewById<ImageView>(R.id.mediaPlay).setImageResource(if (m?.playing == true) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun appLabel(pkg: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun renderCluster(st: ClusterStatus) {
        val title = findViewById<TextView>(R.id.panelTitle)
        val sub = findViewById<TextView>(R.id.panelSub)
        val btn = findViewById<TextView>(R.id.panelToggle)
        val active = st is ClusterStatus.Active
        title.setText(
            when (st) {
                is ClusterStatus.Active -> R.string.panel_on
                ClusterStatus.Working -> R.string.panel_working
                ClusterStatus.Yielded -> R.string.panel_yielded
                else -> R.string.panel_native
            },
        )
        sub.text = when (st) {
            is ClusterStatus.Failed -> st.reason
            ClusterStatus.Yielded -> "Toque em Mostrar no painel para voltar"
            is ClusterStatus.Active -> if (prefs.clusterMode == "nav") "Volta ao original quando a rota acabar" else "Mapa e próxima manobra"
            else -> getString(R.string.panel_auto_hint)
        }
        sub.setTextColor(if (st is ClusterStatus.Failed) palette.danger else palette.text2)
        btn.setText(if (active) R.string.cluster_restore else R.string.cluster_show)
        btn.tag = if (active) "chip" else "chip_on"
        palette.apply(btn, density)
        btn.setTextColor(if (active) palette.text1 else palette.onAccent)
        findViewById<TextView>(R.id.greetSub).text = title.text
        findViewById<View>(R.id.dockPanel)?.let { chip ->
            chip.tag = if (active) "chip_on" else "chip"
            palette.apply(chip, density)
            findViewById<ImageView>(R.id.dockPanelIcon).imageTintList =
                android.content.res.ColorStateList.valueOf(if (active) palette.onAccent else palette.text1)
            findViewById<TextView>(R.id.dockPanelText).setTextColor(if (active) palette.onAccent else palette.text1)
        }
    }

    private fun renderNav(s: NavState) {
        val nav = s.nav
        val active = s.active && nav != null
        val maneuver = findViewById<ManeuverView>(R.id.destManeuver)
        findViewById<View>(R.id.destIcon).visibility = if (active) View.GONE else View.VISIBLE
        findViewById<View>(R.id.destManeuverBox).visibility = if (active) View.VISIBLE else View.GONE
        findViewById<View>(R.id.destButtons).visibility = if (active) View.GONE else View.VISIBLE
        val open = findViewById<TextView>(R.id.destOpenApp)
        open.visibility = if (active) View.VISIBLE else View.GONE
        if (active && nav != null) {
            maneuver.tint = true // seta branca no quadrado azul, nos dois temas
            maneuver.bitmap = s.maneuverIcon
            maneuver.maneuver = nav.maneuver
            maneuver.roundaboutExit = nav.roundaboutExit
            findViewById<TextView>(R.id.destTitle).text = nav.distanceText ?: nav.instruction ?: ""
            findViewById<TextView>(R.id.destSub).text = nav.street ?: nav.instruction ?: ""
            open.text = "Abrir ${s.sourceLabel.ifEmpty { "navegação" }}"
            findViewById<TextView>(R.id.destFooter).text =
                listOfNotNull(nav.etaClock?.let { "Chegada $it" }, nav.remainingDistanceText).joinToString(" · ").ifEmpty { getString(R.string.nav_title) }
        } else {
            findViewById<TextView>(R.id.destTitle).setText(R.string.destination)
            findViewById<TextView>(R.id.destSub).setText(R.string.destination_hint)
            findViewById<TextView>(R.id.destFooter).setText(R.string.nav_title)
            findViewById<TextView>(R.id.destHomeText).setText(if (hasHome()) R.string.go_home else R.string.set_home)
            findViewById<TextView>(R.id.destWorkText).setText(if (hasWork()) R.string.go_work else R.string.set_work)
        }
        // Aba Navegação: cartão da rota atual.
        val box = findViewById<View>(R.id.navActive)
        box.visibility = if (active) View.VISIBLE else View.GONE
        if (active && nav != null) {
            findViewById<ManeuverView>(R.id.navActiveManeuver).apply {
                tint = true; bitmap = s.maneuverIcon; this.maneuver = nav.maneuver; roundaboutExit = nav.roundaboutExit
            }
            findViewById<TextView>(R.id.navActiveDistance).text = nav.distanceText ?: ""
            findViewById<TextView>(R.id.navActiveStreet).text = nav.street ?: nav.instruction ?: ""
            findViewById<TextView>(R.id.navActiveEta).text =
                listOfNotNull(nav.etaClock?.let { "Chegada $it" }, nav.remainingDistanceText, s.sourceLabel.ifEmpty { null }).joinToString(" · ")
        }
    }

    private fun setupCards() {
        click(R.id.cardMedia) { openMedia() }
        click(R.id.mediaPrev) { MediaRepository.previous() }
        click(R.id.mediaNext) { MediaRepository.next() }
        click(R.id.mediaPlay) { if (MediaRepository.now.value == null) openMedia() else MediaRepository.playPause() }
        click(R.id.cardBattery) { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        click(R.id.cardPanel) { toggleCluster() }
        click(R.id.panelToggle) { toggleCluster() }
        click(R.id.cardDest) { showPage(PAGE_NAV) }
        click(R.id.destHome) { goSaved("home") }
        click(R.id.destWork) { goSaved("work") }
        click(R.id.destOpenApp) {
            val pkg = NavRepository.state.value.sourcePackage
            val i = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            if (i != null) startSafely(i) else Places.openApp(this, prefs.navApp)
        }
        click(R.id.setupButton) { runSetup() }
        click(R.id.dockPanel) { toggleCluster() }
        click(R.id.dockNavApp) { if (!Places.openApp(this, prefs.navApp)) toast("App não instalado") }
    }

    private fun toggleCluster() {
        val action = if (ClusterService.status.value is ClusterStatus.Active) ClusterService.ACTION_RESTORE else ClusterService.ACTION_SHOW
        ClusterService.start(this, action)
    }

    private fun openMedia() {
        val pkg = MediaRepository.now.value?.appPackage
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        startSafely(launch)
    }

    // ── Abas ────────────────────────────────────────────────────────────────

    private fun setupTabs() {
        click(R.id.tabHome) { showPage(PAGE_HOME) }
        click(R.id.tabNav) { showPage(PAGE_NAV) }
        click(R.id.tabApps) { showPage(PAGE_APPS) }
        click(R.id.tabMedia) { openMedia() }
        click(R.id.tabPhone) { startSafely(Intent(Intent.ACTION_DIAL)) }
        click(R.id.tabSettings) { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun showPage(p: Int) {
        page = p
        findViewById<View>(R.id.viewHome).visibility = if (p == PAGE_HOME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.viewNav).visibility = if (p == PAGE_NAV) View.VISIBLE else View.GONE
        findViewById<View>(R.id.viewApps).visibility = if (p == PAGE_APPS) View.VISIBLE else View.GONE
        if (p == PAGE_NAV) { ensureNavMap(); renderPlaces() } else { hideKeyboard(); if (pickMode != null) setPickMode(null) }
        highlightTab()
    }

    private fun highlightTab() {
        val selected = when (page) { PAGE_NAV -> R.id.tabNav; PAGE_APPS -> R.id.tabApps; else -> R.id.tabHome }
        val tabs = findViewById<ViewGroup>(R.id.tabs)
        for (i in 0 until tabs.childCount) {
            val tab = tabs.getChildAt(i) as ViewGroup
            val on = tab.id == selected
            (tab.getChildAt(0) as ImageView).imageTintList =
                android.content.res.ColorStateList.valueOf(if (on) palette.accent else palette.text2)
            (tab.getChildAt(1) as TextView).setTextColor(if (on) palette.accent else palette.text2)
        }
    }

    // ── Aba Navegação ───────────────────────────────────────────────────────

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
        click(R.id.appMaps) { prefs.navApp = "maps"; renderSegments() }
        click(R.id.appWaze) { prefs.navApp = "waze"; renderSegments() }
        click(R.id.navActive) {
            val pkg = NavRepository.state.value.sourcePackage
            pkg?.let { packageManager.getLaunchIntentForPackage(it) }?.let { startSafely(it) }
        }
    }

    private fun renderSegments() {
        val waze = prefs.navApp == "waze"
        listOf(R.id.appMaps to !waze, R.id.appWaze to waze).forEach { (id, on) ->
            val v = findViewById<TextView>(id)
            v.tag = if (on) "chip_on" else "chip"
            palette.apply(v, density)
            v.setTextColor(if (on) palette.onAccent else palette.text1)
        }
        findViewById<TextView>(R.id.dockNavAppText)?.setText(if (waze) R.string.open_waze else R.string.open_maps)
    }

    private fun scheduleSearch(q: String, delayMs: Long) {
        searchJob?.cancel()
        if (q.isBlank()) { renderPlaces(); return }
        searchJob = lifecycleScope.launch {
            delay(delayMs)
            findViewById<TextView>(R.id.navListTitle).setText(R.string.results)
            val results = Places.search(q, LocationRepository.location.value)
            val rows = results.map { PlaceRow("result", it, null) }.ifEmpty {
                listOf(PlaceRow("text", Place(q, "Buscar no ${navAppName()}", 0.0, 0.0), null))
            }
            placesAdapter.submit(rows, palette, LocationRepository.location.value)
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
        placesAdapter.submit(rows, palette, LocationRepository.location.value)
    }

    private fun onPlaceClicked(row: PlaceRow) {
        val place = row.place
        when {
            pickMode != null && row.kind == "result" && place != null -> {
                if (pickMode == "home") Places.setHome(this, place) else Places.setWork(this, place)
                toast(if (pickMode == "home") "Casa definida: ${place.name}" else "Trabalho definido: ${place.name}")
                setPickMode(null)
                findViewById<EditText>(R.id.navSearch).setText("")
                renderNav(NavRepository.state.value)
            }
            row.kind == "home" || row.kind == "work" -> goSaved(row.kind)
            row.kind == "text" && place != null -> {
                if (!Places.navigateText(this, place.name, prefs.navApp)) toast("${navAppName()} não está instalado")
            }
            place != null -> {
                hideKeyboard()
                if (!Places.navigate(this, place, prefs.navApp)) toast("${navAppName()} não está instalado")
            }
        }
    }

    /** Casa/Trabalho: vai direto se já definido; senão abre a busca para escolher. */
    private fun goSaved(kind: String) {
        val place = if (kind == "home") Places.home(this) else Places.work(this)
        val legacy = if (kind == "home") prefs.homeAddress else prefs.workAddress
        when {
            place != null -> if (!Places.navigate(this, place, prefs.navApp)) toast("${navAppName()} não está instalado")
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
            findViewById<EditText>(R.id.navSearch).requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(findViewById(R.id.navSearch), 0)
        }
    }

    private fun hasHome() = Places.home(this) != null || prefs.homeAddress.isNotBlank()
    private fun hasWork() = Places.work(this) != null || prefs.workAddress.isNotBlank()
    private fun navAppName() = getString(if (prefs.navApp == "waze") R.string.open_waze else R.string.open_maps)

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(findViewById<View>(R.id.navSearch).windowToken, 0)
    }

    /** Mapa da aba Navegação: posição atual do carro, no tema do dia/noite. Criado só ao abrir a aba. */
    private fun ensureNavMap() {
        if (navMap != null) return
        val host = findViewById<FrameLayout>(R.id.navMapHost)
        host.clipToOutline = true
        val mv = MapView(this)
        host.addView(mv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val car = ImageView(this).apply { setImageDrawable(carArrow()) }
        val size = (44 * density).toInt()
        host.addView(car, FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER))
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
            m.setStyle(Style.Builder().fromUri(mapStyleUri())) { updateNavMap() }
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
            adapter.submit(apps) { v -> palette.apply(v, density) }
        }
    }

    // ── Configuração inicial ────────────────────────────────────────────────

    private fun refreshSetup() {
        val missing = mutableListOf<String>()
        if (!SystemSetup.hasNotificationAccess(this)) missing += "ler as notificações do Maps/Waze"
        if (!LocationRepository.hasPermission(this)) missing += "localização"
        if (adbOk == false) missing += "ADB local"
        val card = findViewById<View>(R.id.setupCard)
        card.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.setupText).text = "Falta permitir: " + missing.joinToString(", ")
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
                    "$out\nFalta o acesso às notificações. Toque em OK e ligue o Painel Livre na lista.",
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
            toast("Nenhum app para abrir isto")
        } catch (e: SecurityException) {
            toast("A central bloqueou: ${e.message}")
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
        private var palette: Palette = Palette.DAY
        private var from: android.location.Location? = null

        fun submit(list: List<PlaceRow>, p: Palette, loc: android.location.Location?) {
            rows = list; palette = p; from = loc
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
            val d = h.itemView.resources.displayMetrics.density
            palette.apply(h.itemView, d)
            h.icon.setImageResource(
                when (row.kind) {
                    "home" -> R.drawable.ic_home
                    "work" -> R.drawable.ic_work
                    "recent" -> R.drawable.ic_history
                    "text" -> R.drawable.ic_search
                    else -> R.drawable.ic_place
                },
            )
            h.icon.imageTintList = android.content.res.ColorStateList.valueOf(palette.accent)
            val p = row.place
            h.name.text = row.label?.let { if (p != null) "$it · ${p.name}" else it } ?: p?.name.orEmpty()
            h.address.text = p?.address?.ifEmpty { null } ?: if (p == null) "Toque para escolher na busca" else ""
            h.address.visibility = if (h.address.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            val km = if (row.kind == "text") null else p?.distanceKm(from)
            h.distance.text = km?.let { if (it < 10) String.format(Locale("pt", "BR"), "%.1f km", it) else "${it.toInt()} km" } ?: ""
            h.itemView.setOnClickListener { onClick(row) }
            h.itemView.setOnLongClickListener { onLongClick(row) }
        }
    }

    companion object {
        private const val REQ_LOCATION = 10
        private const val PAGE_HOME = 0
        private const val PAGE_NAV = 1
        private const val PAGE_APPS = 2
    }
}
