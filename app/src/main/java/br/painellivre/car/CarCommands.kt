package br.painellivre.car

/** Porta única de escrita. Nenhum endereço vindo da interface ou de preferências chega ao shell.
 * Evidências e pendências em docs/COMANDOS.md. Não repete envio após erro de transporte.
 */
class CarCommands(private val port: Port) {
    data class Spec(val key: String, val label: String, val dev: Int? = null, val fid: Int? = null,
                    val min: Double = 0.0, val max: Double = 1.0, val step: Double = 1.0,
                    val confirmed: Boolean = false, val moves: Boolean = false,
                    val onWire: Map<Int, Int> = emptyMap(), val readbackKnown: Boolean = false)
    data class Gear(val value: Int?, val fresh: Boolean, val real: Boolean)
    interface Port {
        fun enabled(): Boolean
        fun allowed(key: String): Boolean
        fun now(): Long
        suspend fun gear(): Gear
        suspend fun environmentOk(): Boolean
        suspend fun sendOnce(command: String): Boolean
        suspend fun read(key: String): Double?
        suspend fun media(key: String, value: Double): String
        fun log(at: Long, key: String, value: Double, result: String)
    }
    private var lastSent: Long? = null
    suspend fun execute(key: String, value: Double): String {
        fun result(s: String): String { port.log(port.now(), key.take(64), value, s); return s }
        val spec = CATALOG.firstOrNull { it.key == key } ?: return result("Rejeitado: comando fora da lista")
        if (!port.enabled()) return result("Rejeitado: comandos desligados em Ajustes")
        if (!port.allowed(key)) return result("Rejeitado: comando desligado no Diagnóstico")
        if (!valid(spec, value)) return result("Rejeitado: valor fora dos limites")
        val now = port.now()
        if (lastSent?.let { now - it < 1000 } == true) return result("Rejeitado: aguarde um segundo")
        if (spec.moves) {
            val gear = port.gear()
            if (!parked(gear)) return result("Rejeitado: câmbio P não confirmado")
        }
        if (!spec.confirmed) return result("Rejeitado: comando ainda não confirmado")
        if (key.startsWith("media_")) {
            lastSent = now
            result("Enviando ao Android")
            return result(port.media(key, value))
        }
        if (!port.environmentOk()) return result("Rejeitado: interface ou firmware não confirmada")
        // Só valores inteiros com escala comprovada podem chegar a setInt.
        val dev = spec.dev ?: return result("Rejeitado: dispositivo não confirmado")
        val fid = spec.fid ?: return result("Rejeitado: endereço de escrita não confirmado")
        if (!port.enabled() || !port.allowed(key)) return result("Rejeitado: liberação retirada antes do envio")
        if (spec.moves && !parked(port.gear())) return result("Rejeitado: câmbio P não confirmado")
        lastSent = port.now()
        result("Enviando")
        val sent = port.sendOnce("service call autoservice 6 i32 $dev i32 $fid i32 ${spec.onWire[value.toInt()] ?: value.toInt()}")
        if (!sent) result("Resposta do envio inconclusiva; não repetido")
        val actual = port.read(key)
        return result(if (spec.readbackKnown && actual == value) "Confirmado pelo estado relido" else "Não confirmado pelo carro (${actual ?: "sem dado"})")
    }
    companion object {
        fun parked(g: Gear) = g.real && g.fresh && g.value == 1
        fun valid(s: Spec, v: Double): Boolean = v.isFinite() && v >= s.min && v <= s.max &&
            kotlin.math.abs((v - s.min) / s.step - kotlin.math.round((v - s.min) / s.step)) < 0.000001
        // IDs de leitura NÃO são reutilizados como setters. Pendências ficam sem endereço e bloqueadas.
        val CATALOG: List<Spec> = listOf(
            Spec("ac_temp_driver", "Temperatura motorista", 1000, 501219368, min=16.0, max=30.0, confirmed=true, readbackKnown=true),
            Spec("ac_temp_pass", "Temperatura passageiro", 1000, 501219376, min=16.0, max=30.0, confirmed=true, readbackKnown=true),
            Spec("ac_on", "Ar ligado", 1000, 501219364, confirmed=true), Spec("ac_fan", "Ventilação", 1000, 501219340, max=7.0, confirmed=true, readbackKnown=true),
            Spec("ac_auto", "AUTO", 1000, 501219352, confirmed=true, onWire=mapOf(0 to 1, 1 to 2)), Spec("ac_compressor", "A/C", 1000, 501219344, confirmed=true),
            Spec("ac_recirc", "Recirculação", 1000, 501219355, confirmed=true), Spec("ac_defrost", "Desembaçador", 1000, 501219362, confirmed=true),
            Spec("seat_heat_driver", "Aquecer motorista", max=3.0),
            Spec("seat_vent_driver", "Ventilar motorista", max=3.0),
            Spec("seat_heat_pass", "Aquecer passageiro", max=3.0),
            Spec("seat_vent_pass", "Ventilar passageiro", max=3.0),
            Spec("trunk", "Porta-malas", moves=true),
            Spec("win_fl", "Vidro motorista", max=100.0, moves=true),
            Spec("win_fr", "Vidro passageiro", max=100.0, moves=true),
            Spec("win_rl", "Vidro traseiro esquerdo", max=100.0, moves=true),
            Spec("win_rr", "Vidro traseiro direito", max=100.0, moves=true),
            Spec("media_play", "Tocar/pausar", min=1.0, confirmed=true),
            Spec("media_next", "Próxima", min=1.0, confirmed=true), Spec("media_prev", "Anterior", min=1.0, confirmed=true),
            Spec("media_volume", "Volume Android", min=-1.0, max=1.0, confirmed=true),
        )
    }
}
