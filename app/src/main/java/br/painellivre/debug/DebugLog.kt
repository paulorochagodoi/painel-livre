package br.painellivre.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * **Kotlin puro.** Registro do modo de debug (issue #9): cada linha tem horário, origem
 * (`sdk`, `settings`, `prop`, `broadcast`, `logcat`, `telefone`, `marca`…) e o texto com os valores.
 * Fica separado do `AppLog` para não misturar o que o sistema mandou com o que o app fez, e é
 * maior (o motorista troca as opções do menu várias vezes seguidas).
 *
 * Só grava com [enabled] ligado: desligado (padrão), nada é guardado.
 */
class DebugBuffer(
    private val max: Int = 3000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Entry(val atMs: Long, val source: String, val text: String)

    @Volatile var enabled = false

    private val entries = ArrayDeque<Entry>()
    private val _version = MutableStateFlow(0L)
    /** Muda a cada linha nova (a tela redesenha quando muda). */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun add(source: String, text: String) {
        if (!enabled) return
        synchronized(this) {
            entries.addLast(Entry(clock(), source, text))
            while (entries.size > max) entries.removeFirst()
        }
        _version.value = _version.value + 1
    }

    fun entries(): List<Entry> = synchronized(this) { entries.toList() }

    fun clear() {
        synchronized(this) { entries.clear() }
        _version.value = _version.value + 1
    }

    /** Texto para tela, copiar e exportar: "HH:mm:ss.SSS [origem] texto". */
    fun format(tz: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply { timeZone = tz }
        return entries().joinToString("\n") { "${fmt.format(Date(it.atMs))} [${it.source}] ${it.text}" }
    }
}

/** Registro único do app. */
object DebugLog {
    val buffer = DebugBuffer()
    val enabled get() = buffer.enabled
    fun add(source: String, text: String) = buffer.add(source, text)
}
