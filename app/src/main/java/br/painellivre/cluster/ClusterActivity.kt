package br.painellivre.cluster

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.nav.NavState
import br.painellivre.ui.cockpit.Palette
import br.painellivre.car.TelemetrySnapshot
import br.painellivre.car.TelemetryText
import br.painellivre.car.VehicleTelemetry
import br.painellivre.ui.cockpit.CarStatusView
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Tela exibida NO PAINEL DE INSTRUMENTOS (display virtual do cluster, 1920×720 no Song Plus /
 * Seal). Não recebe toque nem foco: quem opera é a central. Mostra:
 *  - mapa OpenStreetMap girando com o carro (GPS da central), animado a cada quadro;
 *  - próxima manobra (seta do Google Maps/Waze), distância e rua;
 *  - horário de chegada, tempo e distância restantes.
 *
 * Fluidez e robustez (ideias do SealNav, docs/SEALNAV_RE.md achados F e G): a câmera anda a cada
 * quadro entre os fixes do GPS ([PoseSmoother]); o mapa fica limitado a [Prefs.maxFps] andando e
 * a [IDLE_FPS] parado; um vigia ([FreezeWatch]) redesenha ou recria a superfície se travar.
 */
open class ClusterActivity : ComponentActivity() {

    /** A pré-visualização (tela central, com toque para fechar) é a subclasse [ClusterPreviewActivity]. */
    protected open val isPreview: Boolean = false

    private lateinit var prefs: Prefs
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var preview = false

    private lateinit var overlay: FrameLayout
    private lateinit var carView: ImageView
    private lateinit var tbtCard: LinearLayout
    private lateinit var maneuverView: ManeuverView
    private lateinit var distanceText: TextView
    private lateinit var streetText: TextView
    private lateinit var etaCard: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var osmText: TextView

    private var w = 1920
    private var h = 720

    // Preferências lidas a cada quadro ficam em campos (recarregadas quando mudam).
    private var tilt = 45.0
    private var insetL = 0
    private var insetT = 0
    private var insetR = 0
    private var insetB = 0
    private var maxFps = 60
    private var autoHeal = true

    // Animação por quadro.
    private val smoother = PoseSmoother()
    private val choreographer by lazy { Choreographer.getInstance() }
    private var framePosted = false
    private var started = false
    private var lastCameraAt = 0L
    private var lastEaseAt = 0L
    private var zoom = Double.NaN
    private var stillSince = 0L
    private var fpsCap = 0

    // Vigia (lidos na thread do vigia).
    private val watch = FreezeWatch()
    private val createdAt = SystemClock.uptimeMillis()
    @Volatile private var firstFrameAt: Long? = null
    @Volatile private var lastFrameAt = 0L
    @Volatile private var mainBeatAt = SystemClock.uptimeMillis()
    @Volatile private var cameraMovedAt = 0L
    @Volatile private var frames = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        preview = isPreview

        @Suppress("DEPRECATION")
        val displayId = windowManager.defaultDisplay.displayId
        if (displayId == 0 && !preview) {
            // Caiu na tela central por engano (a firmware recusou o display do painel): sai já.
            AppLog.w("ClusterActivity abriu no display 0 — fechando para não cobrir a central")
            finish()
            return
        }
        if (!preview) {
            instance?.get()?.takeIf { it !== this }?.finish()
            instance = WeakReference(this)
            runningOnDisplay = displayId
            runningTaskId = taskId
            AppLog.i("Tela do painel aberta no display $displayId (tarefa $taskId)")
            getSystemService(android.hardware.display.DisplayManager::class.java)
                .registerDisplayListener(displayListener, null)
        }

        if (!preview) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        loadPrefs()
        pal = Palette.forSetting(prefs.theme)
        styleLoaded = styleUri()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        mapView = MapView(this)
        root.addView(mapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = FrameLayout(this)
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        buildOverlay()
        // Moldura do menu de navegação do painel (issue #9): área e o que aparece em cada opção.
        val menuFrame = PanelMenuFrame(this, root)
        setContentView(menuFrame.frame)
        if (preview) overlay.setOnClickListener { finish() } // por cima do mapa, que consome o toque

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) {
                w = r - l; h = b - t
                applySizes()
                postFrame()
            }
        }

        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFinishRenderingFrameListener(MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
            val now = SystemClock.uptimeMillis()
            if (firstFrameAt == null) {
                firstFrameAt = now
                AppLog.i("Primeiro quadro do mapa em ${now - createdAt} ms")
            }
            lastFrameAt = now
            frames++
        })
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                setAllGesturesEnabled(false)
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
            }
            setFpsCap(maxFps)
            m.setStyle(Style.Builder().fromUri(styleUri())) { onStyleReady() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { NavRepository.state.collect { render(it) } }
                launch { br.painellivre.nav.native.NativeNavigation.route.collect { drawNativeRoute() } }
                launch { LocationRepository.location.collect { it?.let(::onFix) } }
                launch {
                    PanelMenuWatcher.layouts(this@ClusterActivity).collect {
                        menuFrame.apply(it)
                        carView.alpha = if (it.showCar) 1f else 0f
                    }
                }
                launch {
                    // Dia/noite: troca o tema do mapa e dos cartões quando a hora vira.
                    while (true) { delay(60_000); applyTheme() }
                }
                launch {
                    // Batimento da thread principal: se parar, o vigia percebe.
                    while (true) { mainBeatAt = SystemClock.uptimeMillis(); delay(1_000) }
                }
                launch {
                    // Só lê os dados do carro daqui se o aviso no painel estiver ligado.
                    while (true) {
                        if (prefs.telemetryPanel) {
                            VehicleTelemetry.observe(this@ClusterActivity).takeWhile { prefs.telemetryPanel }.collect(::renderTelemetry)
                        }
                        renderTelemetry(null)
                        delay(3_000)
                    }
                }
                if (!preview) launch(Dispatchers.Default) { watchLoop() }
            }
        }
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    // ── Preferências ────────────────────────────────────────────────────────

    private var styleLoaded: String? = null

    private fun styleUri(): String {
        val name = prefs.mapStyle.takeIf { it != "auto" } ?: if (pal.dark) "dark" else "positron"
        return STYLE_BASE + name
    }

    /** Tema do dia/noite (Configurações → Tema): cores dos cartões e estilo do mapa. */
    private fun applyTheme(force: Boolean = false) {
        val p = Palette.forSetting(prefs.theme)
        if (!force && p == pal && styleLoaded == styleUri()) return
        pal = p
        applyColors()
        val uri = styleUri()
        if (uri != styleLoaded) {
            styleLoaded = uri
            map?.setStyle(Style.Builder().fromUri(uri)) { onStyleReady() }
        }
    }

    private fun loadPrefs() {
        tilt = prefs.mapTilt
        insetL = prefs.insetLeft; insetT = prefs.insetTop; insetR = prefs.insetRight; insetB = prefs.insetBottom
        maxFps = prefs.maxFps
        autoHeal = prefs.autoHeal
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        loadPrefs()
        if (::overlay.isInitialized) applySizes()
        if (key == Prefs.KEY_MAP_STYLE || key == Prefs.KEY_THEME) applyTheme(force = true)
        postFrame()
    }

    // ── Overlay: layout do SealNav, dentro da faixa visível do painel ──────
    //
    // No painel real os instrumentos da BYD ficam por cima da projeção: faixa de cima (hora, P,
    // ECO, temperatura), faixa de baixo (kW, bateria, autonomia, velocidade) e alertas à direita
    // (foto pública do SealNav no Seal U). Por isso os cartões ficam em cima à esquerda, entre
    // ~16% e ~62% da altura, e o carro no centro a ~59% da altura. Nada de relógio: a BYD já mostra.

    private var pal: Palette = Palette.NIGHT

    private fun cardBg(radius: Float) = GradientDrawable().apply {
        setColor(if (pal.dark) Color.argb(228, 20, 26, 34) else Color.argb(238, 255, 255, 255))
        cornerRadius = radius
        setStroke(2, if (pal.dark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0))
    }

    private fun label(bold: Boolean = false) = TextView(this).apply {
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private lateinit var leftColumn: LinearLayout
    private lateinit var tbtIconBox: FrameLayout
    private lateinit var instructionText: TextView
    private val etaValues = arrayOfNulls<TextView>(3)
    private val etaLabels = arrayOfNulls<TextView>(3)

    private lateinit var telemetryCard: LinearLayout
    private lateinit var carStatus: CarStatusView
    private lateinit var telemetryText: TextView

    private fun buildOverlay() {
        // O carro fica fixo no ponto focal da câmera (a câmera segue o carro), então é uma View
        // por cima do mapa: não depende de atualizar uma camada do mapa a cada quadro.
        carView = ImageView(this).apply { visibility = View.INVISIBLE; setImageDrawable(CarArrowDrawable()) }
        overlay.addView(carView)

        leftColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overlay.addView(leftColumn)

        // Cartão da manobra: seta branca no quadrado azul + distância, instrução e rua.
        tbtCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        tbtIconBox = FrameLayout(this)
        maneuverView = ManeuverView(this)
        tbtIconBox.addView(maneuverView, FrameLayout.LayoutParams(-1, -1))
        tbtCard.addView(tbtIconBox)
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        distanceText = label(bold = true)
        instructionText = label()
        streetText = label(bold = true).apply { maxLines = 2 }
        texts.addView(distanceText)
        texts.addView(instructionText)
        texts.addView(streetText)
        tbtCard.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        leftColumn.addView(tbtCard)

        // Cartão da chegada: três colunas, como no SealNav.
        etaCard = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        for (i in 0 until 3) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val l = label().apply { isAllCaps = true; letterSpacing = 0.08f }
            val v = label(bold = true)
            col.addView(l); col.addView(v)
            etaLabels[i] = l; etaValues[i] = v
            etaCard.addView(col)
        }
        etaLabels[0]!!.text = "Chegada"
        etaLabels[1]!!.text = "Tempo"
        etaLabels[2]!!.text = "Distância"
        leftColumn.addView(etaCard)

        // Aviso dos dados do carro (issue #16, opcional): porta aberta, tampa de carga, carregando.
        telemetryCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        carStatus = CarStatusView(this)
        telemetryCard.addView(carStatus)
        telemetryText = label(bold = true).apply { maxLines = 2 }
        telemetryCard.addView(telemetryText, LinearLayout.LayoutParams(-2, -2))
        leftColumn.addView(telemetryCard)

        statusText = label(bold = true)
        overlay.addView(statusText)
        osmText = label().apply { text = "© OpenStreetMap" }
        overlay.addView(osmText)
        applyColors()
    }

    private fun applyColors() {
        if (!::tbtCard.isInitialized) return
        val r = h * 0.035f
        tbtCard.background = cardBg(r)
        etaCard.background = cardBg(r)
        telemetryCard.background = cardBg(r)
        carStatus.palette = pal
        telemetryText.setTextColor(pal.text1)
        tbtIconBox.background = GradientDrawable().apply { setColor(pal.accent); cornerRadius = r * 0.8f }
        maneuverView.color = Color.WHITE
        maneuverView.tint = true
        distanceText.setTextColor(pal.text1)
        instructionText.setTextColor(pal.text2)
        streetText.setTextColor(pal.text1)
        etaLabels.forEach { it?.setTextColor(pal.text2) }
        etaValues.forEachIndexed { i, v -> v?.setTextColor(if (i == 0) pal.accent else pal.text1) }
        statusText.setTextColor(Color.WHITE)
        statusText.background = GradientDrawable().apply { setColor(Color.argb(200, 230, 140, 0)); cornerRadius = h * 0.03f }
        osmText.setTextColor(if (pal.dark) Color.argb(150, 255, 255, 255) else Color.argb(150, 0, 0, 0))
    }

    private fun px(fractionOfHeight: Float) = (h * fractionOfHeight)

    private fun applySizes() {
        overlay.setPadding(insetL, insetT, insetR, insetB)
        val pad = px(0.025f).toInt()
        leftColumn.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).apply {
            leftMargin = (w * 0.06f).toInt(); topMargin = px(0.16f).toInt()
        }
        val cardW = (w * 0.30f).toInt()
        tbtCard.layoutParams = LinearLayout.LayoutParams(cardW, -2)
        tbtCard.setPadding(pad, pad, (pad * 1.4f).toInt(), pad)
        val icon = px(0.19f).toInt()
        tbtIconBox.layoutParams = LinearLayout.LayoutParams(icon, icon).apply { marginEnd = (pad * 1.2f).toInt() }
        tbtIconBox.setPadding(pad, pad, pad, pad)
        distanceText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.085f))
        instructionText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.035f))
        streetText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.045f))

        etaCard.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(0.025f).toInt() }
        etaCard.setPadding((pad * 1.2f).toInt(), (pad * 0.8f).toInt(), (pad * 1.2f).toInt(), (pad * 0.8f).toInt())
        etaCard.children().forEachIndexed { i, col ->
            col.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = (pad * 1.6f).toInt() }
        }
        etaLabels.forEach { it?.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.026f)) }
        etaValues.forEach { it?.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.058f)) }

        telemetryCard.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(0.025f).toInt() }
        telemetryCard.setPadding(pad, pad / 2, (pad * 1.4f).toInt(), pad / 2)
        carStatus.layoutParams = LinearLayout.LayoutParams(px(0.09f).toInt(), px(0.17f).toInt()).apply { marginEnd = pad }
        telemetryText.maxWidth = (w * 0.24f).toInt()
        telemetryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.04f))

        statusText.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = px(0.16f).toInt() }
        statusText.setPadding(pad, pad / 3, pad, pad / 3)
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.03f))
        osmText.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).apply {
            leftMargin = (w * 0.06f).toInt(); topMargin = px(0.66f).toInt()
        }
        osmText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.02f))

        val car = (h * 0.095f).toInt().coerceAtLeast(40)
        // O carro fica sempre na posição da tela; a overlay já tem o padding das margens.
        carView.layoutParams = FrameLayout.LayoutParams(car, car, Gravity.TOP or Gravity.START)
        carView.rotationX = (tilt * 0.8).toFloat() // deitado como o mapa inclinado
        applyColors()
    }

    private fun renderTelemetry(t: TelemetrySnapshot?) {
        val text = t?.takeIf { it.status == TelemetrySnapshot.Status.OK && it.notable }?.let(TelemetryText::panel)
        telemetryCard.visibility = if (text == null) View.GONE else View.VISIBLE
        carStatus.snapshot = if (text == null) null else t
        telemetryText.text = text ?: ""
    }

    private fun ViewGroup.children() = (0 until childCount).map { getChildAt(it) }

    private fun render(s: NavState) {
        val nav = s.nav
        if (!s.active || nav == null) {
            tbtCard.visibility = View.GONE
            etaCard.visibility = View.GONE
            setStatus(if (!smoother.hasFix) "Aguardando GPS" else null)
            return
        }
        tbtCard.visibility = View.VISIBLE
        maneuverView.bitmap = s.maneuverIcon
        maneuverView.maneuver = nav.maneuver
        maneuverView.roundaboutExit = nav.roundaboutExit
        distanceText.text = nav.distanceText ?: ""
        distanceText.visibility = if (nav.distanceText.isNullOrEmpty()) View.GONE else View.VISIBLE
        val street = nav.street
        streetText.text = street ?: nav.instruction ?: ""
        instructionText.text = if (street != null) shortInstruction(nav) else ""
        instructionText.visibility = if (instructionText.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        etaValues[0]!!.text = nav.etaClock ?: "—"
        etaValues[1]!!.text = nav.remainingSeconds?.let { formatDuration(it) } ?: "—"
        etaValues[2]!!.text = nav.remainingDistanceText ?: "—"
        etaCard.visibility = if (nav.etaClock == null && nav.remainingSeconds == null && nav.remainingDistanceText == null) View.GONE else View.VISIBLE
        setStatus(if (s.simulated) "SIMULAÇÃO" else null)
    }

    /** "Vire à direita", "Pegue a 2ª saída"… — o verbo da instrução, sem a rua. */
    private fun shortInstruction(nav: br.painellivre.nav.ParsedNav): String {
        val instr = nav.instruction ?: return ""
        val street = nav.street ?: return instr
        val cut = instr.indexOf(street)
        val head = if (cut > 0) instr.substring(0, cut) else instr
        return head.trim().removeSuffix(" na").removeSuffix(" no").removeSuffix(" para").removeSuffix(" em").trim().trimEnd(',')
    }

    private fun setStatus(text: String?) {
        statusText.text = text ?: ""
        statusText.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun formatDuration(sec: Int): String {
        val hh = sec / 3600
        val mm = (sec % 3600) / 60
        return if (hh > 0) String.format(Locale.ROOT, "%d h %02d min", hh, mm) else "$mm min"
    }

    // ── Mapa e câmera por quadro ────────────────────────────────────────────

    private fun drawNativeRoute() {
        br.painellivre.nav.native.NativeRouteLayer.draw(map?.style)
    }

    private fun onStyleReady() {
        drawNativeRoute()
        if (!smoother.hasFix) {
            map?.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(-23.5505, -46.6333)).zoom(11.0).build()))
        }
        postFrame()
    }

    private fun onFix(loc: Location) {
        val now = SystemClock.uptimeMillis()
        smoother.onFix(loc.latitude, loc.longitude, loc.bearing.toDouble(), loc.speed.toDouble(), now)
        if (loc.speed >= MOVING_SPEED) stillSince = 0L else if (stillSince == 0L) stillSince = now
        if (carView.visibility != View.VISIBLE) carView.visibility = View.VISIBLE
        if (statusText.text == "Aguardando GPS") setStatus(null)
        postFrame()
    }

    private fun postFrame() {
        if (framePosted || !started) return
        framePosted = true
        choreographer.postFrameCallback(frameCallback)
    }

    private val frameCallback = Choreographer.FrameCallback {
        framePosted = false
        val now = SystemClock.uptimeMillis()
        val moving = stillSince == 0L || now - stillSince < STILL_AFTER_MS
        val busy = smoother.isAnimating(now) || easing()
        setFpsCap(if ((moving && smoother.hasFix) || busy) maxFps else IDLE_FPS)
        // A câmera não precisa andar mais rápido que o mapa consegue desenhar.
        val cap = if (fpsCap > 0) fpsCap else maxFps
        if (now - lastCameraAt >= 1000L / cap - 4) {
            applyCamera(now)
            lastCameraAt = now
        }
        if (smoother.isAnimating(now) || easing()) postFrame()
    }

    private fun targetZoom(speedMps: Double): Double {
        val kmh = speedMps * 3.6
        return when {
            kmh <= 20 -> 16.9
            kmh >= 110 -> 15.2
            else -> 16.9 - (kmh - 20) / 90.0 * 1.7
        }
    }

    private fun easing(): Boolean {
        val pose = smoother.poseAt(SystemClock.uptimeMillis()) ?: return false
        return zoom.isNaN() || abs(zoom - targetZoom(pose.speed)) > 0.005
    }

    private fun applyCamera(now: Long) {
        val m = map ?: return
        val pose = smoother.poseAt(now) ?: return
        val dt = if (lastEaseAt == 0L) 1.0 else (now - lastEaseAt).coerceIn(0, 200) / 1000.0
        lastEaseAt = now
        val tz = targetZoom(pose.speed)
        zoom = if (zoom.isNaN()) tz else zoom + (tz - zoom) * min(1.0, dt / ZOOM_EASE_S)
        // Carro no centro, a ~59% da altura: acima da faixa de baixo dos instrumentos da BYD.
        val top = (insetT + px(0.30f)).toDouble()
        val bottom = (insetB + px(0.12f)).toDouble()

        m.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(pose.lat, pose.lon))
                .zoom(zoom)
                .bearing(pose.bearing)
                .tilt(tilt)
                .padding(insetL.toDouble(), top, insetR.toDouble(), bottom)
                .build(),
        ))
        cameraMovedAt = now

        // Ponto focal = centro da área livre da câmera (a overlay já desconta as margens).
        val cx = (w - insetL - insetR) / 2.0
        val cy = top + (h - top - bottom) / 2.0 - insetT
        carView.translationX = (cx - carView.width / 2.0).toFloat()
        carView.translationY = (cy - carView.height / 2.0).toFloat()
    }

    private fun setFpsCap(fps: Int) {
        if (fps == fpsCap || map == null) return
        fpsCap = fps
        mapView.setMaximumFps(fps)
    }

    // ── Vigia de congelamento ───────────────────────────────────────────────

    private suspend fun watchLoop() {
        var lastFrames = frames
        var lastAt = SystemClock.uptimeMillis()
        while (true) {
            delay(1_500)
            val now = SystemClock.uptimeMillis()
            val f = frames
            renderFps = ((f - lastFrames) * 1000.0 / (now - lastAt).coerceAtLeast(1)).toInt()
            renderFpsCap = fpsCap
            lastFrames = f; lastAt = now
            val sample = FreezeWatch.Sample(
                now = now, createdAt = createdAt, firstFrameAt = firstFrameAt, lastFrameAt = lastFrameAt,
                mainBeatAt = mainBeatAt, moving = now - cameraMovedAt < 1_000,
            )
            when (watch.evaluate(sample)) {
                FreezeWatch.Action.NONE -> {}
                FreezeWatch.Action.RELAUNCH -> {
                    if (relaunchBudget.tryTake(now)) {
                        AppLog.w("Mapa sem primeiro quadro em 10 s — reabrindo a tela do painel")
                        ClusterService.start(applicationContext, ClusterService.ACTION_RELAUNCH)
                    } else {
                        AppLog.w("Mapa sem primeiro quadro em 10 s (já reaberto há pouco; sem internet?)")
                    }
                }
                FreezeWatch.Action.MAIN_STALLED -> AppLog.w("Thread principal parada há mais de 5 s")
                FreezeWatch.Action.REPAINT -> {
                    AppLog.w("Mapa sem quadros há ${now - lastFrameAt} ms com o carro andando — forçando redesenho")
                    withContext(Dispatchers.Main) { map?.triggerRepaint() }
                }
                FreezeWatch.Action.RECREATE_SURFACE -> {
                    if (autoHeal && recreateBudget.tryTake(now)) {
                        AppLog.w("Mapa continua travado — recriando a superfície OpenGL")
                        withContext(Dispatchers.Main) { bounceSurface() }
                    } else {
                        AppLog.w("Mapa continua travado (recuperação ${if (autoHeal) "no limite de 3 em 10 min" else "desligada"})")
                    }
                }
            }
        }
    }

    /** Recria o contexto/superfície OpenGL do mapa — o mesmo caminho de sair e voltar ao app. */
    private fun bounceSurface() {
        if (!started) return
        mapView.onPause()
        mapView.onResume()
        postFrame()
    }

    // ── Segurança: nunca ficar na tela central ─────────────────────────────

    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == runningOnDisplay && !preview) {
                AppLog.w("Display do painel $displayId removido — fechando a tela do painel")
                finish()
            }
        }
    }

    /**
     * Se o display do painel some, o Android move esta Activity para a tela central. Como ela não
     * recebe toque, cobriria a central — então fecha imediatamente.
     */
    private fun leaveIfOnCentralScreen(): Boolean {
        if (preview) return false
        @Suppress("DEPRECATION")
        val now = windowManager.defaultDisplay.displayId
        if (now == Display.DEFAULT_DISPLAY) {
            AppLog.w("Tela do painel foi movida para a central — fechando")
            finish()
            return true
        }
        if (instance?.get() === this) runningOnDisplay = now
        return false
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        leaveIfOnCentralScreen()
    }

    // ── Ciclo de vida (MapView precisa de todos) ────────────────────────────

    override fun onStart() {
        super.onStart()
        started = true
        if (::mapView.isInitialized) mapView.onStart()
        postFrame()
    }

    override fun onResume() {
        super.onResume()
        if (leaveIfOnCentralScreen()) return
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        leaveIfOnCentralScreen()
    }

    override fun onPause() { if (::mapView.isInitialized) mapView.onPause(); super.onPause() }

    override fun onStop() {
        started = false
        if (framePosted) { choreographer.removeFrameCallback(frameCallback); framePosted = false }
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
        // Parar sem estar fechando = outro app cobriu a tela no painel (se o display segue ligado).
        @Suppress("DEPRECATION")
        val displayOn = windowManager.defaultDisplay.state == Display.STATE_ON
        if (!preview && !isFinishing && !isChangingConfigurations && instance?.get() === this && displayOn) {
            AppLog.w("Tela do painel foi coberta por outro app")
            ClusterService.reportCovered()
        }
    }

    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); if (::mapView.isInitialized) mapView.onSaveInstanceState(outState) }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching {
            getSystemService(android.hardware.display.DisplayManager::class.java).unregisterDisplayListener(displayListener)
        }
        if (::mapView.isInitialized) mapView.onDestroy()
        if (instance?.get() === this) {
            instance = null
            runningOnDisplay = null
            runningTaskId = null
        }
        super.onDestroy()
    }

    companion object {
        private const val STYLE_BASE = "https://tiles.openfreemap.org/styles/"
        /** Quadros por segundo do mapa com o carro parado (SealNav usa o mesmo valor). */
        const val IDLE_FPS = 5
        private const val MOVING_SPEED = 1.5f // m/s
        private const val STILL_AFTER_MS = 5_000L
        private const val ZOOM_EASE_S = 1.5

        /** Ações caras do vigia valem entre telas recriadas (o vigia de cada tela é novo). */
        private val relaunchBudget = FreezeWatch.Budget(max = 1, windowMs = 10 * 60_000L)
        private val recreateBudget = FreezeWatch.Budget(max = 3, windowMs = 10 * 60_000L)

        @Volatile var runningOnDisplay: Int? = null
            private set
        @Volatile var runningTaskId: Int? = null
            private set
        /** Quadros por segundo do mapa no painel (medido a cada 1,5 s) e o limite atual. */
        @Volatile var renderFps = 0
            private set
        @Volatile var renderFpsCap = 0
            private set
        private var instance: WeakReference<ClusterActivity>? = null

        fun finishInstance() {
            val a = instance?.get() ?: return
            a.runOnUiThread { a.finish() }
        }
    }
}
