package br.painellivre.adb

import android.content.Context
import br.painellivre.util.AppLog
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * "ADB de si mesmo": conecta no adbd da própria central (127.0.0.1:5555) e roda comandos de
 * shell como uid 2000. É assim que DashCast e SealNav ativam a projeção no painel — o serviço
 * `AutoContainer` e o `am start --display` exigem privilégios que um app comum não tem.
 *
 * Uma única conexão fica aberta e é compartilhada (docs/SEALNAV_RE.md, achado A): se um comando falha
 * porque a conexão caiu, reconecta uma vez e repete. No boot o adbd demora a subir, então
 * [ensureReady] tenta de novo a cada 2 s.
 *
 * Na primeira conexão a central mostra "Permitir depuração USB?" — marque "Sempre permitir"
 * e toque em OK. A chave fica em files/adbkey(.pub) deste app.
 */
object SelfAdb {
    private const val HOST = "127.0.0.1"
    private const val PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val SOCKET_TIMEOUT_MS = 20_000

    private val mutex = Mutex()
    @Volatile private var keyPair: AdbKeyPair? = null
    @Volatile private var dadb: Dadb? = null

    /** Último comando deu certo (para as telas mostrarem o estado sem abrir conexão). */
    @Volatile var connected = false
        private set

    private val _ready = MutableStateFlow<Boolean?>(null)
    /** null = ainda não testado; true/false = resultado do último comando. */
    val ready: StateFlow<Boolean?> = _ready.asStateFlow()

    sealed class Result {
        data class Ok(val output: String, val exitCode: Int) : Result()
        data class Error(val message: String, val kind: Kind = Kind.OTHER) : Result()
    }

    enum class Kind { PORT_CLOSED, AUTH, TIMEOUT, OTHER }

    /** Porta 5555 aberta? (ADB por TCP ligado na central.) */
    suspend fun isPortOpen(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(HOST, PORT), 1500); true }
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Espera o ADB local responder: até [attempts] tentativas com [intervalMs] entre elas.
     * Para na hora se a central recusar a chave (o motorista precisa aceitar o aviso na tela).
     */
    suspend fun ensureReady(context: Context, attempts: Int = 15, intervalMs: Long = 2_000): Result {
        var last: Result = Result.Error("ADB não testado")
        for (i in 1..attempts) {
            last = shell(context, "echo ok")
            if (last is Result.Ok) {
                if (i > 1) AppLog.i("ADB local pronto na tentativa $i")
                return last
            }
            val kind = (last as Result.Error).kind
            if (kind == Kind.AUTH || kind == Kind.TIMEOUT) return last
            if (i < attempts) delay(intervalMs)
        }
        AppLog.w("ADB local não respondeu em $attempts tentativas")
        return last
    }

    suspend fun shell(context: Context, command: String): Result =
        withContext(Dispatchers.IO) { mutex.withLock { runLocked(context, command, retry = true) } }

    /** Escritas nunca são repetidas: uma queda pode acontecer depois de o carro executar. */
    suspend fun shellOnce(context: Context, command: String): Result =
        withContext(Dispatchers.IO) { mutex.withLock { runLocked(context, command, retry = false) } }

    /** Atalho: devolve a saída ou "ERRO: ..." (para telas de diagnóstico). */
    suspend fun shellText(context: Context, command: String): String =
        when (val r = shell(context, command)) {
            is Result.Ok -> r.output
            is Result.Error -> "ERRO: ${r.message}"
        }

    private fun runLocked(context: Context, command: String, retry: Boolean): Result {
        var attempt = 0
        while (true) {
            try {
                val d = dadb ?: Dadb.create(HOST, PORT, keys(context), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS, true)
                    .also { dadb = it }
                val r = d.shell(command)
                connected = true
                _ready.value = true
                return Result.Ok(r.allOutput.trim(), r.exitCode)
            } catch (t: Throwable) {
                drop()
                val kind = classify(t)
                // Conexão antiga que caiu (central dormiu, adbd reiniciou): uma nova tentativa.
                if (retry && attempt == 0 && kind == Kind.OTHER && t is IOException) {
                    attempt++
                    AppLog.w("Conexão ADB caiu (${t.javaClass.simpleName}); reconectando")
                    continue
                }
                val msg = message(kind, t)
                _ready.value = false
                AppLog.w("shell falhou: ${command.take(80)} → $msg")
                return Result.Error(msg, kind)
            }
        }
    }

    private fun drop() {
        connected = false
        runCatching { dadb?.close() }
        dadb = null
    }

    private fun classify(t: Throwable): Kind {
        val name = t.javaClass.simpleName
        return when {
            name.contains("Auth", ignoreCase = true) -> Kind.AUTH
            t is ConnectException || name.contains("Connect", ignoreCase = true) &&
                !name.contains("Closed", ignoreCase = true) -> Kind.PORT_CLOSED
            t is SocketTimeoutException -> Kind.TIMEOUT
            else -> Kind.OTHER
        }
    }

    private fun message(kind: Kind, t: Throwable) = when (kind) {
        Kind.AUTH -> "ADB recusou a chave. Aceite \"Permitir depuração USB\" na tela da central (marque Sempre permitir)."
        Kind.PORT_CLOSED -> "ADB por TCP desligado (porta 5555 fechada). Ative a depuração/ADB na central."
        Kind.TIMEOUT -> "ADB não respondeu a tempo. Se apareceu \"Permitir depuração USB\" na central, aceite e tente de novo."
        Kind.OTHER -> "${t.javaClass.simpleName}: ${t.message}"
    }

    private fun keys(context: Context): AdbKeyPair {
        keyPair?.let { return it }
        val dir = context.applicationContext.filesDir
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) {
            AppLog.i("Gerando chave ADB do Painel Livre")
            AdbKeyPair.generate(priv, pub)
        }
        return AdbKeyPair.read(priv, pub).also { keyPair = it }
    }
}
