package br.painellivre.cluster

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import br.painellivre.BuildConfig
import br.painellivre.adb.SelfAdb
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Liga/desliga a projeção no painel de instrumentos de uma DiLink 3 "dual-OS" (1 for 2).
 *
 * Protocolo (engenharia reversa do DashCast e do SealNav — docs/ENGENHARIA_REVERSA.md, docs/SEALNAV_RE.md):
 *  - Serviço Binder `AutoContainer` (ou `auto_container` em algumas DiLink 5), transação 2 =
 *    `sendInfo(int type, int info, String str)` com type = 1000.
 *  - 29/30/31 = tamanho do painel (8,8"/12,3"/10,25"); 16 = liga a projeção; 35 = pede ao Qt
 *    para criar o VirtualDisplay (`fission_bg_xdjaVirtualSurface`, dono com.xdja.containerservice).
 *  - 18 = encerra a projeção; 0 = recarrega o vídeo nativo do Qt (sempre depois do 18).
 *
 * Ativação (como o SealNav): limpeza de estado velho → tamanho do painel (só se [PanelSize]
 * aprovar) → 1 s → 16 → 1 s → 35. Este app NUNCA envia 1 (derruba o display) nem um código de
 * tamanho que não combine com o painel medido. Ver [SAFE_COMMANDS] e [PanelSize].
 */
class ClusterController(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = Prefs(ctx)
    private val dm = ctx.getSystemService(DisplayManager::class.java)

    data class Target(val displayId: Int, val name: String, val width: Int, val height: Int) {
        val geometry get() = PanelSize.Geometry(width, height)
    }

    // ── Encontrar o display do painel ───────────────────────────────────────

    fun findClusterDisplay(): Target? {
        val all = (dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList() + dm.displays.toList())
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .distinctBy { it.displayId }
        val known = all.filter { isKnownClusterName(it.name) }.sortedBy { priority(it.name) }
        val d = known.firstOrNull() ?: return null
        val size = android.graphics.Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }
        return Target(d.displayId, d.name, size.x, size.y).also(::remember)
    }

    /** Fallback pelo shell: o uid 2000 enxerga displays que algumas firmwares escondem dos apps. */
    suspend fun findClusterDisplayViaShell(): Target? {
        val out = SelfAdb.shellText(ctx, "dumpsys display | grep -E 'DisplayInfo\\{' | head -30")
        val rx = Regex("""DisplayInfo\{"([^"]+?)"?,\s*(?:uniqueId "[^"]*",\s*)?displayId (\d+)[^}]*?real (\d+) x (\d+)""")
        return rx.findAll(out)
            .map { Target(it.groupValues[2].toInt(), it.groupValues[1], it.groupValues[3].toInt(), it.groupValues[4].toInt()) }
            .filter { it.displayId != 0 && isKnownClusterName(it.name) }
            .sortedBy { priority(it.name) }
            .firstOrNull()
            ?.also(::remember)
    }

    /** Guarda a geometria medida: é ela que libera o código de tamanho nas próximas ativações. */
    private fun remember(t: Target) {
        val g = t.geometry.toString()
        if (t.width > 0 && t.height > 0 && prefs.panelGeometry != g) {
            AppLog.i("Painel medido: $g (display ${t.displayId} \"${t.name}\")")
            prefs.panelGeometry = g
        }
    }

    /** Modelo/produto/aparelho/fingerprint da central — o mesmo texto que o SealNav examina. */
    fun identity(): String = listOf(Build.MODEL, Build.PRODUCT, Build.DEVICE, Build.FINGERPRINT).joinToString(" ")

    fun panelSizeDecision(current: Target? = null): PanelSize.Decision =
        PanelSize.decide(prefs.panelSize, identity(), current?.geometry ?: PanelSize.Geometry.parse(prefs.panelGeometry))

    // ── AutoContainer.sendInfo ──────────────────────────────────────────────

    suspend fun autoContainerService(): String? {
        prefs.autoContainerName?.let { return it }
        val out = SelfAdb.shellText(ctx, "service check AutoContainer; service check auto_container")
        val name = when {
            out.contains("Service AutoContainer: found") -> "AutoContainer"
            out.contains("Service auto_container: found") -> "auto_container"
            else -> null
        }
        AppLog.i("Serviço do painel: ${name ?: "não encontrado"} ($out)")
        if (name != null) prefs.autoContainerName = name
        return name
    }

    suspend fun sendInfo(info: Int): SelfAdb.Result {
        require(info in SAFE_COMMANDS) { "comando $info bloqueado por segurança" }
        return send(info)
    }

    /** Código de tamanho: só passa um código que o [PanelSize] aprovou para este painel. */
    private suspend fun sendPanelSize(decision: PanelSize.Decision): SelfAdb.Result {
        val code = requireNotNull(decision.code)
        require(code in PanelSize.CODES) { "código de tamanho $code bloqueado" }
        return send(code)
    }

    private suspend fun send(info: Int): SelfAdb.Result {
        check(info != CMD_DESTROY_DISPLAY && info in 0..63) { "comando $info nunca é enviado" }
        val svc = autoContainerService()
        if (svc == null) {
            if (BuildConfig.DEBUG) {
                // Emulador não tem AutoContainer: registra a sequência para testar o fluxo inteiro.
                AppLog.i("[debug, sem AutoContainer] sendInfo($info) simulado")
                return SelfAdb.Result.Ok("simulado", 0)
            }
            return SelfAdb.Result.Error("Serviço AutoContainer não existe nesta central (single-OS?)")
        }
        val r = SelfAdb.shell(ctx, "service call $svc 2 i32 $CLUSTER_TYPE i32 $info s16 \"\"")
        AppLog.i("sendInfo($info) → $r")
        return r
    }

    // ── Ciclo de vida da projeção ───────────────────────────────────────────

    /**
     * Coloca o painel em modo projeção e devolve o display onde desenhar.
     * Sequência do SealNav (docs/SEALNAV_RE.md, achados B–D), com pausas de 1 s.
     */
    suspend fun activate(): Target? {
        val ready = SelfAdb.ensureReady(ctx, attempts = 3)
        if (ready is SelfAdb.Result.Error) {
            AppLog.e("ADB local indisponível — não dá para ativar o painel: ${ready.message}")
            return null
        }
        cleanupStaleState()
        val existing = findClusterDisplay() ?: findClusterDisplayViaShell()
        val size = panelSizeDecision(existing)
        AppLog.i("Tamanho do painel: ${size.code ?: "não enviado"} — ${size.reason}")
        if (size.code != null) {
            if (sendPanelSize(size) is SelfAdb.Result.Error) return null
            delay(STEP_DELAY_MS)
        }
        prefs.projectionPowered = true
        if (sendInfo(CMD_PROJECTION_ON) is SelfAdb.Result.Error) return null
        delay(STEP_DELAY_MS)
        sendInfo(CMD_CREATE_DISPLAY)

        val deadline = System.currentTimeMillis() + 20_000
        var nextShellCheck = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            findClusterDisplay()?.let { AppLog.i("Display do painel: $it"); return it }
            if (System.currentTimeMillis() >= nextShellCheck) {
                findClusterDisplayViaShell()?.let { AppLog.i("Display do painel (via shell): $it"); return it }
                nextShellCheck = System.currentTimeMillis() + 3_000
            }
            delay(500)
        }
        AppLog.w("Display do painel não apareceu em 20 s")
        return null
    }

    /**
     * Uma vez por execução do app: se a projeção ficou ligada de uma sessão anterior (carro
     * desligado com o mapa no painel) e não há display do painel, devolve o painel original
     * antes de ativar de novo (docs/SEALNAV_RE.md, achado C).
     */
    private suspend fun cleanupStaleState() {
        if (staleChecked) return
        staleChecked = true
        if (!prefs.projectionPowered) return
        if (findClusterDisplay() != null || findClusterDisplayViaShell() != null) return
        AppLog.i("Projeção ficou ligada de uma sessão anterior — limpando (18 → 0)")
        sendInfo(CMD_STOP_PROJECTION)
        delay(STEP_DELAY_MS)
        sendInfo(CMD_RESTORE_NATIVE)
        delay(STEP_DELAY_MS)
        prefs.projectionPowered = false
    }

    /** Só abre a tela no display do painel já existente (sem enviar comandos ao AutoContainer). */
    suspend fun launchOnly(): Target? {
        val t = findClusterDisplay() ?: findClusterDisplayViaShell() ?: return null
        return if (launchClusterUi(t)) t else null
    }

    /**
     * Abre a tela do Painel Livre no display do painel. Primeiro pelo shell em tela cheia
     * (`--windowingMode 1`), depois em janela livre ajustada ao display, e por fim direto pelo app
     * (docs/SEALNAV_RE.md, achado E).
     */
    suspend fun launchClusterUi(target: Target): Boolean {
        if (ClusterActivity.runningOnDisplay == target.displayId) {
            // Já aberta: só traz para a frente.
            shellStart(target, WINDOWING_FULLSCREEN)
            return true
        }
        if (SelfAdb.connected || SelfAdb.isPortOpen()) {
            if (shellStart(target, WINDOWING_FULLSCREEN) && waitForCluster(target.displayId)) return true
            AppLog.w("Tela cheia pelo shell não abriu no display ${target.displayId}; tentando janela livre")
            if (shellStart(target, WINDOWING_FREEFORM) && waitForCluster(target.displayId)) {
                fitTaskToDisplay(target)
                return true
            }
            AppLog.w("Shell não abriu a tela no painel; tentando direto pelo app")
        }
        try {
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
            val i = Intent(ctx, ClusterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            ctx.startActivity(i, opts.toBundle())
            if (waitForCluster(target.displayId)) return true
            AppLog.w("startActivity também não abriu no display ${target.displayId}")
        } catch (t: Throwable) {
            AppLog.w("startActivity no painel recusado", t)
        }
        return false
    }

    /** Somente os dois navegadores escolhidos pelo motorista; nunca abre no display central. */
    suspend fun launchNavigation(target: Target, pkg: String): Boolean {
        require(target.displayId > 0 && pkg in setOf(PanelContent.MAPS, PanelContent.WAZE))
        val component = ctx.packageManager.getLaunchIntentForPackage(pkg)?.component ?: run {
            AppLog.w("App de navegação não instalado: $pkg")
            return false
        }
        ClusterActivity.finishInstance()
        val cmp = component.flattenToString()
        // Componente vem do PackageManager, mas ainda recebe aspas para não virar código shell.
        val quoted = "'" + cmp.replace("'", "'\"'\"'") + "'"
        val result = SelfAdb.shell(ctx,
            "am start --display ${target.displayId} --windowingMode 1 -n $quoted -f 0x10020000 2>&1")
        if (result !is SelfAdb.Result.Ok || result.exitCode != 0) return false
        repeat(10) {
            if (PanelContent.isResumedOnDisplay(SelfAdb.shellText(ctx, "dumpsys activity activities"), target.displayId, pkg)) {
                AppLog.i("App de navegação confirmado no painel: $pkg (display ${target.displayId})")
                return true
            }
            delay(300)
        }
        AppLog.w("Não foi possível confirmar $pkg no painel; a central pode recusar mover a tarefa. Use mapa integrado.")
        return false
    }

    /** Fecha e reabre a tela do painel (sem mexer no AutoContainer). */
    suspend fun relaunch(target: Target): Boolean {
        ClusterActivity.finishInstance()
        for (i in 0 until 20) {
            if (ClusterActivity.runningOnDisplay == null) break
            delay(100)
        }
        return launchClusterUi(target)
    }

    private suspend fun shellStart(target: Target, windowingMode: Int): Boolean {
        val cmp = "${ctx.packageName}/${ClusterActivity::class.java.name}"
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        val r = SelfAdb.shell(
            ctx,
            "am start --display ${target.displayId} --windowingMode $windowingMode -n $cmp -f 0x${Integer.toHexString(flags)} 2>&1",
        )
        val out = (r as? SelfAdb.Result.Ok)?.output ?: (r as SelfAdb.Result.Error).message
        val ok = r is SelfAdb.Result.Ok && !out.contains("Error", ignoreCase = true) && !out.contains("Exception")
        if (!ok) AppLog.w("am start (modo $windowingMode) → ${out.take(200)}")
        return ok
    }

    /** Janela livre menor que o display: estica a tarefa para ocupar o painel inteiro. */
    private suspend fun fitTaskToDisplay(target: Target) {
        val task = ClusterActivity.runningTaskId ?: return
        val r = SelfAdb.shellText(ctx, "am task resize $task 0 0 ${target.width} ${target.height} 2>&1")
        AppLog.i("Ajustando a janela ao painel (tarefa $task, ${target.width}x${target.height}) → ${r.take(120)}")
    }

    private suspend fun waitForCluster(displayId: Int): Boolean {
        repeat(20) {
            if (ClusterActivity.runningOnDisplay == displayId) return true
            delay(150)
        }
        return false
    }

    suspend fun restore() {
        ClusterActivity.finishInstance()
        sendInfo(CMD_STOP_PROJECTION)
        delay(STEP_DELAY_MS)
        sendInfo(CMD_RESTORE_NATIVE)
        prefs.projectionPowered = false
    }

    companion object {
        const val CLUSTER_TYPE = 1000
        const val CMD_RESTORE_NATIVE = 0
        const val CMD_DESTROY_DISPLAY = 1
        const val CMD_PROJECTION_ON = 16
        const val CMD_STOP_PROJECTION = 18
        const val CMD_CREATE_DISPLAY = 35
        val SAFE_COMMANDS = setOf(CMD_RESTORE_NATIVE, CMD_PROJECTION_ON, CMD_STOP_PROJECTION, CMD_CREATE_DISPLAY)

        private const val STEP_DELAY_MS = 1_000L
        private const val WINDOWING_FULLSCREEN = 1
        private const val WINDOWING_FREEFORM = 5

        /** Limpeza de estado velho: uma vez por execução do processo. */
        @Volatile private var staleChecked = false

        fun isKnownClusterName(name: String?): Boolean {
            val n = name?.lowercase(Locale.ROOT) ?: return false
            // Debug: o display "Overlay #1" do emulador faz papel de painel nos testes no PC.
            return n.contains("xdja") || n.contains("fission") || (BuildConfig.DEBUG && n.startsWith("overlay #"))
        }

        /** DiLink 5.1: a BYD usa `shared_..._0`; nos demais, todos empatam. */
        private fun priority(name: String?): Int {
            val n = name?.lowercase(Locale.ROOT) ?: return 9
            return when {
                n.startsWith("shared_") && n.endsWith("_0") -> 0
                n.startsWith("shared_") -> 1
                else -> 2
            }
        }
    }
}
