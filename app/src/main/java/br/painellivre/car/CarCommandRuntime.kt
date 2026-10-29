package br.painellivre.car

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import br.painellivre.adb.SelfAdb
import br.painellivre.info.MediaRepository
import br.painellivre.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Adaptador Android. Uma fila só para o envio (`send`); nenhuma repetição automática.
 * A releitura (`confirm`) roda em segundo plano, fora da fila: o carro sentia ~1 s entre toques
 * porque o `execute` antigo esperava a releitura inteira antes de devolver o controle à UI
 * (achado do Paulo, 29/10 à tarde). `scope` não é cancelado pela Activity — uma releitura em
 * voo sobrevive a uma troca de tela, só não bloqueia mais o próximo toque.
 */
object CarCommandRuntime {
    const val ENABLED = "car_commands_enabled"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: CarCommands? = null
    suspend fun execute(ctx: Context, key: String, value: Double): String {
        val app = ctx.applicationContext
        lateinit var commands: CarCommands
        val outcome = try {
            mutex.withLock {
                val settings = br.painellivre.util.Prefs(app)
                commands = engine ?: buildEngine(app, settings).also { engine = it }
                commands.send(key, value)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLog.w("Comando $key = $value: interrompido; resultado desconhecido, não repetir automaticamente")
            throw e
        } catch (e: Exception) {
            val message = "Comando $key = $value: falha ${e.javaClass.simpleName}; não repetido"
            AppLog.w(message)
            return message
        }
        return when (outcome) {
            is CarCommands.Outcome.Done -> outcome.message
            is CarCommands.Outcome.Sent -> {
                scope.launch {
                    try { commands.confirm(outcome.spec, key, value) }
                    catch (e: Exception) { AppLog.w("Comando $key = $value: confirmação falhou ${e.javaClass.simpleName}") }
                }
                "Enviado"
            }
        }
    }
    private fun buildEngine(app: Context, settings: br.painellivre.util.Prefs) =
        CarCommands(object : CarCommands.Port {
            override fun enabled() = settings.carCommandsEnabled
            override fun allowed(key: String) = settings.carCommandAllowed(key)
            override fun now() = SystemClock.elapsedRealtime()
            override suspend fun gear(): CarCommands.Gear {
                if (VehicleTelemetry.simulated != null) return CarCommands.Gear(null, false, false)
                val s = VehicleTelemetry.sweep(app, listOf(TelemetryCatalog.byKey("gear")))
                return CarCommands.Gear(s.value("gear")?.toInt(),
                    s.status == TelemetrySnapshot.Status.OK && System.currentTimeMillis() - s.atMillis in 0..2000, true)
            }
            override suspend fun environmentOk(): Boolean {
                if (Build.VERSION.SDK_INT != 29 || VehicleTelemetry.simulated != null) return false
                val r = SelfAdb.shell(app, Autoservice.ENVIRONMENT_COMMAND) as? SelfAdb.Result.Ok ?: return false
                // Descriptor sozinho não prova o contrato da firmware: nenhum setter está liberado ainda.
                return r.exitCode == 0 && Autoservice.Environment.parse(r.output).descriptorOk
            }
            override suspend fun sendOnce(command: String): Boolean {
                val r = SelfAdb.shellOnce(app, command) as? SelfAdb.Result.Ok ?: return false
                return r.exitCode == 0 && (Autoservice.parse(r.output) as? Autoservice.Reply.Value)?.word == 0
            }
            override suspend fun read(key: String): Double? {
                delay(100) // breve assentamento; confirm roda em background e não bloqueia a UI
                return VehicleTelemetry.refresh(app, listOf(TelemetryCatalog.byKey(key))).value(key)
            }
            override suspend fun media(key: String, value: Double): String {
                return try {
                    when (key) {
                        "media_play" -> if (MediaRepository.now.value == null) MediaRepository.resumeLast(app) else MediaRepository.playPause()
                        "media_next" -> MediaRepository.next()
                        "media_prev" -> MediaRepository.previous()
                        "media_volume" -> {
                            val am = app.getSystemService(AudioManager::class.java) ?: return "Áudio indisponível"
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, value.toInt(), 0)
                            return "Volume relido: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}"
                        }
                    }
                    delay(500)
                    val state = MediaRepository.refreshState()
                    when {
                        state == null -> "Sem confirmação de sessão Android"
                        key == "media_play" -> "Estado relido da sessão: " + if (state.playing) "tocando" else "pausado"
                        else -> "Sessão relida: ${state.title ?: "sem título"}; troca de faixa não confirmada"
                    }
                } catch (e: RuntimeException) { "Recusado pelo Android: ${e.javaClass.simpleName}" }
            }
            override fun log(at: Long, key: String, value: Double, result: String) {
                AppLog.i("Comando $key = $value: $result")
            }
        })
}
