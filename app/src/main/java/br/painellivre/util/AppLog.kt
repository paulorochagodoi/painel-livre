package br.painellivre.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log em memória (últimas linhas) + logcat. A tela de Diagnóstico mostra este buffer, para o
 * motorista conseguir copiar o que aconteceu no carro sem precisar de um notebook com adb.
 */
object AppLog {
    private const val TAG = "PainelLivre"
    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun i(msg: String) = add("I", msg).also { Log.i(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) =
        add("W", msg + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: "")).also { Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) =
        add("E", msg + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: "")).also { Log.e(TAG, msg, t) }

    @Synchronized
    private fun add(level: String, msg: String) {
        lines.addLast("${fmt.format(Date())} $level $msg")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}
