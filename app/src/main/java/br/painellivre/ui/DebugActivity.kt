package br.painellivre.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.painellivre.BuildConfig
import br.painellivre.R
import br.painellivre.adb.SelfAdb
import br.painellivre.car.VehicleTelemetry
import br.painellivre.cluster.PanelMenu
import br.painellivre.cluster.PanelMenuWatcher
import br.painellivre.debug.DebugLog
import br.painellivre.debug.DebugProbe
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela do modo de debug (issues #9 e #15). O motorista deixa aberta, troca a opção do menu de
 * navegação do painel, toca no botão com a opção que escolheu (marca no registro) e depois copia,
 * compartilha ou salva o registro. Também testa o desenho de cada opção sem depender do carro.
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var out: TextView
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var pauseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        out = findViewById(R.id.debugOutput)
        scroll = findViewById(R.id.debugScroll)
        status = findViewById(R.id.debugStatus)

        val marks = findViewById<LinearLayout>(R.id.debugMarks)
        button(marks, "Escolhi:", enabled = false) {}
        for (m in MENU) button(marks, m.label, primary = true) { mark("escolhi ${m.label} no menu do painel") }
        button(marks, "Outra marca…") { askText("Marca no registro", "") { mark(it) } }

        val tests = findViewById<LinearLayout>(R.id.debugTests)
        button(tests, "Testar desenho:", enabled = false) {}
        button(tests, "Normal") { test(null) }
        for (m in MENU) button(tests, m.label) { test(m) }

        val actions = findViewById<LinearLayout>(R.id.debugActions)
        pauseButton = button(actions, "Pausar") {
            if (DebugProbe.adbRunning.value) DebugProbe.pause() else DebugProbe.restart(this)
        }
        button(actions, "Fotografia completa") { snapshot() }
        button(actions, "Ler dados do carro") { readCar() }
        button(actions, "Escutar ação…") {
            askText("Ação de broadcast para escutar", "com.exemplo.ACAO") { DebugProbe.addAction(this, it) }
        }
        button(actions, "Copiar") { copy() }
        button(actions, "Compartilhar") { share() }
        button(actions, "Salvar arquivo") { save() }
        button(actions, "Limpar") { DebugLog.buffer.clear() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var shown = -1L
                    while (true) {
                        val v = DebugLog.buffer.version.value
                        if (v != shown) { shown = v; render() }
                        renderStatus()
                        delay(700)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun button(bar: LinearLayout, label: String, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit): Button {
        val b = Button(this, null, 0, if (primary) R.style.BigButton_Primary else R.style.BigButton).apply {
            text = label
            isEnabled = enabled
            setOnClickListener { action() }
        }
        bar.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 12 })
        return b
    }

    private fun render() {
        val atBottom = !scroll.canScrollVertically(1)
        out.text = if (DebugLog.enabled || DebugLog.buffer.entries().isNotEmpty()) DebugLog.buffer.format()
        else "O modo de debug está desligado. Ligue em Ajustes → Testes e ajuda → Modo de debug."
        if (atBottom) scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun renderStatus() {
        val r = PanelMenuWatcher.last.value
        val follow = Prefs(this).panelMenuFollow
        val forced = PanelMenuWatcher.override.value
        status.text = listOf(
            "Debug: ${if (DebugLog.enabled) "ligado" else "desligado"}",
            "ADB: ${if (DebugProbe.adbRunning.value) "coletando" else "parado"}${if (SelfAdb.connected) "" else " (sem conexão)"}",
            "Seguir menu: ${if (follow) "sim" else "não"}",
            "Leitura do menu: ${r?.let { it.error ?: "${it.raw} = ${it.mode.label}" } ?: "—"}",
            "Teste: ${forced?.label ?: "nenhum"}",
        ).joinToString("  ·  ")
        pauseButton.text = if (DebugProbe.adbRunning.value) "Pausar" else "Retomar"
    }

    private fun mark(text: String) {
        if (!DebugLog.enabled) { toast("Ligue o modo de debug em Ajustes"); return }
        DebugLog.add("marca", "──── $text ────")
    }

    private fun test(mode: PanelMenu.Mode?) {
        PanelMenuWatcher.override.value = mode
        DebugLog.add("teste", "desenho forçado no painel: ${mode?.label ?: "normal (segue a preferência)"}")
        toast("Aplicado no painel em até 2 s")
    }

    /** Issue #16: lê uma vez todos os ids mapeados (só leitura) e registra para conferir no carro. */
    private fun readCar() {
        if (!DebugLog.enabled) { toast("Ligue o modo de debug em Ajustes"); return }
        // A varredura não lança: falha de ADB/serviço vira uma linha no registro.
        lifecycleScope.launch { VehicleTelemetry.debugReadAll(this@DebugActivity) }
    }

    private fun snapshot() {
        if (!DebugLog.enabled) { toast("Ligue o modo de debug em Ajustes"); return }
        DebugLog.add("foto", "tirando a fotografia completa…")
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try { DebugProbe.snapshot(this@DebugActivity) } catch (t: Throwable) { "ERRO: ${t.javaClass.simpleName}" }
            }
            DebugLog.add("foto", "\n$text")
        }
    }

    private fun exportText(): String =
        "Painel Livre ${BuildConfig.VERSION_NAME} — registro do modo de debug\n" + DebugLog.buffer.format()

    private fun copy() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Painel Livre debug", exportText().takeLast(MAX_SHARE)))
        toast("Copiado")
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Painel Livre — debug")
            .putExtra(Intent.EXTRA_TEXT, exportText().takeLast(MAX_SHARE))
        try {
            startActivity(Intent.createChooser(send, "Compartilhar registro"))
        } catch (t: Throwable) {
            toast("Nenhum app para compartilhar; use Copiar ou Salvar arquivo")
        }
    }

    private fun save() {
        val dir = getExternalFilesDir(null) ?: filesDir
        val name = "painel-livre-debug-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val f = File(dir, name)
        try {
            f.writeText(exportText())
            AlertDialog.Builder(this).setMessage("Salvo em:\n${f.absolutePath}\n\nPelo ADB: adb pull ${f.absolutePath}")
                .setPositiveButton(android.R.string.ok, null).show()
        } catch (t: Throwable) {
            toast("Não consegui salvar: ${t.javaClass.simpleName}")
        }
    }

    private fun askText(title: String, hint: String, done: (String) -> Unit) {
        val input = EditText(this).apply { this.hint = hint; setSingleLine() }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(done) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private val MENU = listOf(PanelMenu.Mode.SIMPLE, PanelMenu.Mode.SMALL, PanelMenu.Mode.FULL, PanelMenu.Mode.OFF)
        /** O Binder do Android não aguenta textos enormes no compartilhar/copiar. */
        private const val MAX_SHARE = 200_000
    }
}
