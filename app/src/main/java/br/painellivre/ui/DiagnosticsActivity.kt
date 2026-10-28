package br.painellivre.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.painellivre.BuildConfig
import br.painellivre.R
import br.painellivre.adb.SelfAdb
import br.painellivre.adb.SystemSetup
import br.painellivre.car.TelemetryCatalog
import br.painellivre.car.TelemetrySnapshot
import br.painellivre.car.TelemetryText
import br.painellivre.car.VehicleData
import br.painellivre.car.VehicleTelemetry
import br.painellivre.cluster.ClusterActivity
import br.painellivre.cluster.ClusterController
import br.painellivre.cluster.ClusterService
import br.painellivre.location.LocationRepository
import br.painellivre.nav.NavRepository
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tudo que dá para verificar no carro sem notebook: propriedades da firmware (dual/single-OS,
 * versão do MCU do painel), displays, serviço AutoContainer, ADB local, GPS, dados do carro.
 * O texto pode ser copiado e enviado para análise.
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var out: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        out = findViewById(R.id.diagOutput)
        scroll = findViewById(R.id.diagScroll)
        val bar = findViewById<LinearLayout>(R.id.diagButtons)

        fun button(label: String, primary: Boolean = false, action: () -> Unit) {
            bar.addView(Button(this, null, 0, if (primary) R.style.BigButton_Primary else R.style.BigButton).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 12 })
        }

        button("Info da central", primary = true) { runCatchingShow { centralInfo() } }
        button("Tamanho do painel") { append(panelSizeInfo()) }
        button("Ativar painel") { ClusterService.start(this, ClusterService.ACTION_SHOW); append("→ pedido de ativação enviado; veja o Log em alguns segundos") }
        button("Só abrir a tela no painel") { ClusterService.start(this, ClusterService.ACTION_LAUNCH_ONLY); append("→ abrindo a tela no display do painel existente") }
        button("Restaurar painel") { ClusterService.start(this, ClusterService.ACTION_RESTORE); append("→ restauração pedida") }
        button("Pré-visualizar aqui") {
            startActivity(Intent(this, br.painellivre.cluster.ClusterPreviewActivity::class.java))
        }
        button("Simular rota") { ClusterService.start(this, ClusterService.ACTION_SIMULATE); append("→ simulação alternada") }
        button("Conceder permissões") { runCatchingShow { SystemSetup.grantAll(this) } }
        button("Dados do carro") { runCatchingShow { withContext(Dispatchers.IO) { carText() } } }
        button("Navegação atual") { append(NavRepository.state.value.let { "ativa=${it.active} fonte=${it.sourceLabel}\n${it.nav}" }) }
        button("Log do app") { append(AppLog.dump()) }
        button("Copiar tudo") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Painel Livre", out.text))
            Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
        }
        append("Painel Livre ${BuildConfig.VERSION_NAME} — toque em \"Info da central\" para começar.")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private suspend fun centralInfo(): String {
        val sb = StringBuilder()
        sb.append("== Displays vistos pelo app ==\n")
        getSystemService(DisplayManager::class.java).displays.forEach {
            sb.append("  id=${it.displayId} nome=${it.name} flags=0x${Integer.toHexString(it.flags)}\n")
        }
        sb.append("Display do painel (app): ${ClusterController(this).findClusterDisplay()}\n")
        sb.append("Tela do painel aberta em: ${ClusterActivity.runningOnDisplay}" +
            (ClusterActivity.runningOnDisplay?.let { "  •  mapa ${ClusterActivity.renderFps} quadros/s (limite ${ClusterActivity.renderFpsCap})" } ?: "") + "\n")
        sb.append("Leitor de notificações: ${if (SystemSetup.hasNotificationAccess(this)) "OK" else "SEM ACESSO"}\n")
        sb.append("GPS: ${LocationRepository.location.value?.let { "%.5f, %.5f (±%.0f m)".format(it.latitude, it.longitude, it.accuracy) } ?: "sem posição"}\n")
        sb.append("ADB TCP 5555 aberto: ${SelfAdb.isPortOpen()}\n")
        val boot = Prefs(this).lastBootAt
        sb.append("Último boot recebido pelo app: ${if (boot == 0L) "nunca (a BYD pode estar segurando o autoinício)" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(boot))}\n")
        sb.append("== Dados do carro (SDK no usuário do app, não pelo AutoContainer) ==\n")
        sb.append(withContext(Dispatchers.IO) { carText() }).append("\n")
        sb.append("BYDAUTO_STATISTIC_GET: ${if (checkSelfPermission("android.permission.BYDAUTO_STATISTIC_GET") == android.content.pm.PackageManager.PERMISSION_GRANTED) "concedida" else "não concedida"}\n")
        sb.append("Conteúdo escolhido para o painel: ${Prefs(this).panelContent}\n")
        sb.append(panelSizeInfo()).append("\n\n")
        sb.append("== Shell (uid 2000) ==\n")
        sb.append(
            SelfAdb.shellText(
                this,
                "id; echo; " +
                    "echo produto=\$(getprop ro.product.name) modelo=\$(getprop ro.product.model) android=\$(getprop ro.build.version.release); " +
                    "echo build=\$(getprop ro.build.display.id); " +
                    "echo fission_single_os=\$(getprop ro.build.system.fission_single_os); " +
                    "echo inswver=\$(getprop apps.setting.product.inswver); " +
                    "echo car.type=\$(getprop persist.sys.car.type) dilink=\$(getprop ro.build.dilink.version)\$(getprop ro.dilink.version); " +
                    "service check AutoContainer; service check auto_container; " +
                    "echo; dumpsys display | grep -E 'DisplayInfo\\{' | head -12; " +
                    // Issues #7 e #3: qual launcher responde ao Início, quais são candidatos e que app de telefone existe.
                    "echo; echo tela_inicial=\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1); " +
                    "echo launchers:; cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep /; " +
                    "echo telefone:; cmd package query-activities --brief -a android.intent.action.DIAL -d tel:190 | grep /; " +
                    "pm list packages | grep -iE 'launcher|dialer|phone|telecom|bluetooth'; " +
                    "echo; pm list packages | grep -iE 'xdja|telenav|amap|navi|cluster|dashcast|sealnav' ",
            ),
        )
        sb.append("\n\nInterpretação:\n")
        sb.append("• fission_single_os=1 → central single-OS: o painel não aceita projeção (só setas via CAN).\n")
        sb.append("• fission_single_os vazio/0 + AutoContainer found → dual-OS: mapa no painel deve funcionar.\n")
        return sb.toString()
    }

    /** O que o app vai fazer com o código de tamanho do painel na próxima ativação, e por quê. */
    /** SDK (uid do app) e, se ligada, a leitura pelo ADB local (issue #16, só leitura). */
    private suspend fun carText(): String {
        val sdk = VehicleData.read(this).diagnosticText()
        val adb = if (Prefs(this).telemetryEnabled) {
            TelemetryText.diagnostic(VehicleTelemetry.sweep(this, TelemetryCatalog.POLLED))
        } else TelemetrySnapshot.Status.OFF.label
        return "$sdk\n\nPelo ADB local:\n$adb"
    }

    private fun panelSizeInfo(): String {
        val c = ClusterController(this)
        val p = Prefs(this)
        val d = c.panelSizeDecision(c.findClusterDisplay())
        return "== Tamanho do painel ==\n" +
            "Configuração: ${p.panelSize}  •  painel medido: ${p.panelGeometry ?: "ainda não"}\n" +
            "Identificação: ${c.identity()}\n" +
            "Próxima ativação: ${d.code?.let { "envia $it" } ?: "não envia"} — ${d.reason}"
    }

    private fun runCatchingShow(block: suspend () -> String) {
        append("…")
        lifecycleScope.launch {
            val text = try { block() } catch (t: Throwable) { "ERRO: ${t.javaClass.simpleName}: ${t.message}" }
            append(text)
        }
    }

    private fun append(text: String) {
        out.append("\n\n" + text)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
