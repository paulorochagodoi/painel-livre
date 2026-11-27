package br.painellivre.cluster

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.nav.NavState
import br.painellivre.R
import br.painellivre.nav.MapLook
import br.painellivre.nav.MapStyler
import br.painellivre.ui.aura.AuraColors
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
    private var safeBase = SafeArea.SONG_PLUS
    private var safeAdjust = SafeArea.NONE
    private var showSafeOutline = false
    /** Posições calculadas da área segura ([SafeArea.plan]); refeitas em [applySizes]. */
    private var plan: SafeArea.Plan? = null
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
        colors = AuraColors.forSetting(prefs.theme)
        look = ClusterLook.of(colors)
        styleLoaded = styleUri()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        mapView = MapView(this)
        root.addView(mapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = FrameLayout(this)
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        buildOverlay()
        // Moldura do menu de navegação do painel (issue #9): área e o que aparece em cada opção.
        val menuFrame = PanelMenuFrame(this, root)
        displayFrame = menuFrame.frame
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
            m.setStyle(br.painellivre.nav.petal.MapTiles.builder(this, styleUri())) { onStyleReady() }
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
                        // Simples mostra somente manobra/chegada, mesmo se os dados mudarem depois.
                        telemetryCard.alpha = if (it.showMap) 1f else 0f
                        statusText.alpha = if (it.showMap) 1f else 0f
                        osmText.alpha = if (it.showMap) 1f else 0f
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
        val name = prefs.mapStyle.takeIf { it != "auto" } ?: if (colors.dark) "dark" else "positron"
        return br.painellivre.nav.petal.MapTiles.styleFor(this, STYLE_BASE + name)
    }

    /** Tema Aura claro/escuro (mesma preferência da central): cores dos cartões, da rota e do mapa. */
    private fun applyTheme(force: Boolean = false) {
        val c = AuraColors.forSetting(prefs.theme)
        if (!force && c == colors && styleLoaded == styleUri()) return
        colors = c
        look = ClusterLook.of(c)
        applyColors()
        drawNativeRoute()
        val uri = styleUri()
        if (uri != styleLoaded) {
            styleLoaded = uri
            map?.setStyle(br.painellivre.nav.petal.MapTiles.builder(this, uri)) { onStyleReady() }
        }
    }

    private fun loadPrefs() {
        tilt = prefs.mapTilt
        safeBase = SafeArea.preset(prefs.safeArea)
        safeAdjust = prefs.insetAdjust
        showSafeOutline = prefs.debugMode && prefs.safeAreaEnabled
        maxFps = prefs.maxFps
        autoHeal = prefs.autoHeal
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        loadPrefs()
        if (::overlay.isInitialized) applySizes()
        if (key == Prefs.KEY_MAP_STYLE || key == Prefs.KEY_THEME || key == Prefs.KEY_PETAL_ENABLED || key == Prefs.KEY_PETAL_TILES) applyTheme(force = true)
        postFrame()
    }

    // ── Overlay: layout do SealNav, dentro da área segura do painel ────────
    //
    // No painel real os instrumentos da BYD ficam por cima da projeção (faixa de cima, cantos
    // curvos, avisos dos lados, faixa de baixo e assistente de faixa; medidas em [SafeArea]). Tudo
    // que o Painel Livre desenha fica dentro da área segura: manobra colada no topo dela, chegada
    // logo abaixo, carro no centro a 67,5% da altura (y 360 no Song Plus) e a atribuição no canto
    // de baixo à direita. Nada de relógio: a BYD já mostra.

    private var colors: AuraColors = AuraColors.DARK
    private var look: ClusterLook = ClusterLook.of(colors)

    /** Cartão Aura sobre o mapa: `surface` quase opaco, borda `line` e raio do Aura. */
    private fun cardBg(radius: Float, stroke: Int = look.cardStroke) = GradientDrawable().apply {
        setColor(look.card)
        cornerRadius = radius
        setStroke((h * ClusterLook.STROKE).toInt().coerceAtLeast(2), stroke)
    }

    /** Fontes Aura dentro do APK (funcionam offline): Sora nos números, Albert Sans no texto. */
    private fun font(id: Int): Typeface = runCatching { ResourcesCompat.getFont(this, id) }.getOrNull() ?: Typeface.DEFAULT

    private fun label(fontId: Int = R.font.albert_sans_medium) = TextView(this).apply {
        typeface = font(fontId)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private lateinit var root: FrameLayout
    private lateinit var displayFrame: FrameLayout
    private lateinit var leftColumn: LinearLayout
    /** Contorno da área segura, só no modo de debug (para ajustar as margens olhando o painel). */
    private lateinit var safeOutline: TextView
    private lateinit var tbtIconBox: FrameLayout
    private lateinit var instructionText: TextView
    private val etaValues = arrayOfNulls<TextView>(3)
    private val etaLabels = arrayOfNulls<TextView>(3)
    /** Ícones Aura de cada coluna da chegada (bandeira, relógio, rota). */
    private val etaIcons = intArrayOf(R.drawable.au_pin, R.drawable.au_clock, R.drawable.au_route)

    private lateinit var telemetryCard: LinearLayout
    private lateinit var carStatus: CarStatusView
    private lateinit var telemetryText: TextView

    private fun buildOverlay() {
        // O carro fica fixo no ponto focal da câmera (a câmera segue o carro), então é uma View
        // por cima do mapa: não depende de atualizar uma camada do mapa a cada quadro.
        carView = ImageView(this).apply { visibility = View.INVISIBLE; setImageDrawable(CarArrowDrawable(MapLook.arrowColors(colors))) }
        overlay.addView(carView)

        leftColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overlay.addView(leftColumn)

        // Cartão da manobra: seta no quadrado `accent` + distância (Sora), instrução e rua.
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
        distanceText = label(R.font.sora_semibold)
        instructionText = label(R.font.albert_sans_medium)
        streetText = label(R.font.albert_sans_semibold).apply { maxLines = 2 }
        texts.addView(distanceText)
        texts.addView(instructionText)
        texts.addView(streetText)
        tbtCard.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        leftColumn.addView(tbtCard)

        // Cartão da chegada: três colunas, como no SealNav.
        etaCard = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        for (i in 0 until 3) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val l = label(R.font.albert_sans_semibold).apply { isAllCaps = true; letterSpacing = 0.06f }
            // Valor nunca cortado ("16 min", "1 h 05 min"): a coluna cresce até caber.
            val v = label(R.font.sora_semibold).apply { ellipsize = null }
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
        telemetryText = label(R.font.albert_sans_semibold).apply { maxLines = 2 }
        telemetryCard.addView(telemetryText, LinearLayout.LayoutParams(0, -2, 1f))
        overlay.addView(telemetryCard)

        statusText = label(R.font.albert_sans_semibold).apply { gravity = Gravity.CENTER_VERTICAL }
        overlay.addView(statusText)
        osmText = label(R.font.albert_sans_regular).apply { text = "© OpenStreetMap" }
        overlay.addView(osmText)
        safeOutline = label(R.font.albert_sans_regular).apply {
            // Rótulo embaixo à esquerda: em cima fica o cartão da manobra.
            gravity = Gravity.BOTTOM or Gravity.START
            setTextColor(Color.rgb(255, 64, 160))
            background = GradientDrawable().apply { setStroke(3, Color.rgb(255, 64, 160)) }
            visibility = View.GONE
        }
        overlay.addView(safeOutline)
        applyColors()
    }

    private fun applyColors() {
        if (!::tbtCard.isInitialized) return
        val r = h * ClusterLook.RADIUS
        tbtCard.background = cardBg(r)
        etaCard.background = cardBg(r)
        // Aviso do carro (porta aberta, tampa de carga): borda `warn`, texto em palavras.
        telemetryCard.background = cardBg(r, look.warn)
        carStatus.palette = colors.toPalette()
        telemetryText.setTextColor(look.ink)
        tbtIconBox.background = GradientDrawable().apply { setColor(look.accent); cornerRadius = r * 0.8f }
        maneuverView.color = look.onAccent
        maneuverView.tint = true
        distanceText.setTextColor(look.ink)
        instructionText.setTextColor(look.inkMuted)
        streetText.setTextColor(look.ink)
        etaLabels.forEach { it?.setTextColor(look.inkMuted) }
        etaValues.forEachIndexed { i, v -> v?.setTextColor(if (i == 0) look.accent else look.ink) }
        statusText.setTextColor(look.statusInk)
        statusText.background = GradientDrawable().apply { setColor(look.statusBg); setStroke(2, look.warn); cornerRadius = h.toFloat() }
        osmText.setTextColor(look.attribution)
        carView.setImageDrawable(CarArrowDrawable(MapLook.arrowColors(colors)))
        applyIcons()
    }

    /** Ícones de linha Aura nos rótulos da chegada e no aviso de status, na cor do texto. */
    private fun applyIcons() {
        val u = plan?.unit ?: h.toFloat()
        val size = (u * ClusterLook.ETA_ICON).toInt().coerceAtLeast(12)
        fun icon(id: Int, color: Int) = ResourcesCompat.getDrawable(resources, id, theme)?.mutate()?.apply {
            setBounds(0, 0, size, size)
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        etaLabels.forEachIndexed { i, l ->
            l?.setCompoundDrawables(icon(etaIcons[i], look.inkMuted), null, null, null)
            l?.compoundDrawablePadding = size / 3
        }
        statusText.setCompoundDrawables(icon(R.drawable.au_alert, look.statusInk), null, null, null)
        statusText.compoundDrawablePadding = size / 3
    }

    private fun applySizes() {
        // A área segura é do display do painel; o conteúdo pode estar encolhido pelo menu (#9).
        val dw = displayFrame.width.takeIf { it > 0 } ?: w
        val dh = displayFrame.height.takeIf { it > 0 } ?: h
        val insets = SafeArea.effectiveInsets(prefs.safeAreaEnabled, dw, dh, safeBase, safeAdjust)
        val content = SafeArea.Rect(root.left, root.top, root.left + w, root.top + h)
        val p = if (prefs.safeAreaEnabled) SafeArea.plan(dw, dh, insets, content)
            else SafeArea.defaultPlan(dw, dh, content)
        if (p != plan) AppLog.i("Área segura do painel ${dw}x$dh: margens $insets, carro em ${p.carX},${p.carY}")
        plan = p
        val safe = p.safe
        val u = p.unit
        fun px(f: Float) = u * f
        val pad = px(ClusterLook.PAD).toInt()
        leftColumn.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP).apply {
            // Manobra colada na faixa de cima da BYD, na borda da área segura.
            leftMargin = safe.left; topMargin = safe.top
        }
        tbtCard.layoutParams = LinearLayout.LayoutParams(
            if (p.compact) p.cardWidth else ClusterLook.maneuverWidth(p),
            if (p.compact) (u * 0.25f).toInt() else -2)
        tbtCard.setPadding(pad, pad, (pad * 1.4f).toInt(), pad)
        val icon = px(ClusterLook.ICON_BOX).toInt()
        tbtIconBox.layoutParams = LinearLayout.LayoutParams(icon, icon).apply { marginEnd = (pad * 1.2f).toInt() }
        tbtIconBox.setPadding(pad, pad, pad, pad)
        distanceText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(if (p.compact) 0.073f else ClusterLook.DISTANCE))
        instructionText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(if (p.compact) 0.030f else ClusterLook.INSTRUCTION))
        streetText.maxLines = if (p.compact) 1 else 2
        streetText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(if (p.compact) 0.042f else ClusterLook.STREET))

        etaCard.layoutParams = LinearLayout.LayoutParams(if (p.compact) p.cardWidth else -2,
            if (p.compact) px(0.215f).toInt() else -2).apply { topMargin = px(if (p.compact) 0.02f else 0.025f).toInt() }
        etaCard.setPadding((pad * 1.2f).toInt(), (pad * 0.8f).toInt(), (pad * 1.2f).toInt(), (pad * 0.8f).toInt())
        etaCard.children().forEachIndexed { i, col ->
            col.layoutParams = LinearLayout.LayoutParams(if (p.compact) 0 else -2, -2,
                if (p.compact) 1f else 0f).apply { if (i > 0) marginStart = (pad * 1.6f).toInt() }
        }
        etaLabels.forEach { it?.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(ClusterLook.ETA_LABEL)) }
        etaValues.forEach { it?.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(ClusterLook.ETA_VALUE)) }

        // O aviso tem seu próprio canto seguro: não aumenta a pilha de manobra/chegada.
        val telemetry = p.telemetryBounds
        telemetryCard.layoutParams = FrameLayout.LayoutParams(telemetry.width, telemetry.height, Gravity.TOP or Gravity.START).apply {
            leftMargin = telemetry.left; topMargin = telemetry.top
        }
        telemetryCard.setPadding(pad, pad / 2, (pad * 1.4f).toInt(), pad / 2)
        carStatus.layoutParams = LinearLayout.LayoutParams(px(0.09f).toInt(), px(0.17f).toInt()).apply { marginEnd = pad }
        telemetryText.maxWidth = (telemetry.width - px(0.09f).toInt() - 4 * pad).coerceAtLeast(0)
        telemetryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(ClusterLook.TELEMETRY))

        // Aviso centralizado na área segura (as margens laterais entram no centro do FrameLayout).
        statusText.layoutParams = FrameLayout.LayoutParams(if (p.compact) p.statusBounds.width else -2,
            if (p.compact) p.statusBounds.height else -2,
            (if (p.compact) Gravity.START else Gravity.CENTER_HORIZONTAL) or Gravity.TOP).apply {
            leftMargin = safe.left; rightMargin = if (p.compact) w - safe.left - p.cardWidth else w - safe.right
            topMargin = if (p.compact) p.statusBounds.top else safe.top + pad
        }
        statusText.setPadding(pad, pad / 2, pad, pad / 2)
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(ClusterLook.STATUS))
        // Atribuição no canto de baixo à direita da área segura: longe dos cartões, dos avisos e
        // do assistente de faixa (que fica abaixo dela no Song Plus).
        osmText.layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = w - safe.right + pad / 2; bottomMargin = h - safe.bottom + pad / 2
        }
        osmText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(ClusterLook.ATTRIBUTION))

        safeOutline.layoutParams = FrameLayout.LayoutParams(safe.width, safe.height, Gravity.TOP or Gravity.START).apply {
            leftMargin = safe.left; topMargin = safe.top
        }
        safeOutline.setPadding(pad / 2, pad / 3, pad / 2, pad / 3)
        safeOutline.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(0.022f))
        safeOutline.text = "Área segura $insets"
        safeOutline.visibility = if (showSafeOutline) View.VISIBLE else View.GONE

        val car = (u * SafeArea.CAR_SIZE).toInt().coerceAtLeast(40)
        carView.layoutParams = FrameLayout.LayoutParams(car, car, Gravity.TOP or Gravity.START)
        carView.rotationX = (tilt * 0.8).toFloat() // deitado como o mapa inclinado
        placeCar(car, car)
        applyColors()
    }

    /** O carro fica sempre no ponto focal da câmera (centro da área segura, a 67,5% da altura). */
    private fun placeCar(carW: Int = carView.width, carH: Int = carView.height) {
        val p = plan ?: return
        carView.translationX = (p.carX - carW / 2.0).toFloat()
        carView.translationY = (p.carY - carH / 2.0).toFloat()
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
        br.painellivre.nav.native.NativeRouteLayer.draw(map?.style, colors)
    }

    private fun onStyleReady() {
        MapStyler.widenRoads(map?.style)
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
        // Ponto focal no carro (centro da área segura, acima da faixa de baixo da BYD).
        val pad = plan?.cameraPadding ?: SafeArea.NONE

        m.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(pose.lat, pose.lon))
                .zoom(zoom)
                .bearing(pose.bearing)
                .tilt(tilt)
                .padding(pad.left.toDouble(), pad.top.toDouble(), pad.right.toDouble(), pad.bottom.toDouble())
                .build(),
        ))
        cameraMovedAt = now
        placeCar()
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
