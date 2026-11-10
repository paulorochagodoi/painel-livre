package br.painellivre.car

/** Porta única de escrita. Nenhum endereço vindo da interface ou de preferências chega ao shell.
 * Evidências e pendências em docs/COMANDOS.md. Não repete envio após erro de transporte.
 *
 * `send`/`confirm` são separados desde 29/10 (tarde): o carro sentia ~1 s entre toques porque o
 * antigo `execute` único esperava a releitura (`port.read`, com `delay` do lado Android) antes de
 * devolver o controle. `send` valida e manda; quem chama decide se espera `confirm` (JVM/testes,
 * via `execute`, que faz os dois em sequência) ou dispara `confirm` à parte em segundo plano sem
 * bloquear o próximo toque (`CarCommandRuntime`, adaptador Android). Não há mais limite de 1
 * envio/s aqui: cada toque manda na hora; a única serialização que resta é a do adaptador
 * (não dois `sendOnce` ao mesmo tempo).
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
    /** Resultado de `send`: `Done` já é a mensagem final (rejeição ou retorno de mídia, sem
     * releitura); `Sent` mandou de verdade e precisa de `confirm` depois para saber se bateu. */
    sealed class Outcome {
        data class Done(val message: String) : Outcome()
        data class Sent(val spec: Spec) : Outcome()
    }
    suspend fun send(key: String, value: Double): Outcome {
        fun result(s: String): Outcome.Done { port.log(port.now(), key.take(64), value, s); return Outcome.Done(s) }
        val spec = CATALOG.firstOrNull { it.key == key } ?: return result("Rejeitado: comando fora da lista")
        if (!port.enabled()) return result("Rejeitado: comandos desligados em Ajustes")
        if (!port.allowed(key)) return result("Rejeitado: comando desligado no Diagnóstico")
        if (!valid(spec, value)) return result("Rejeitado: valor fora dos limites")
        if (spec.moves) {
            val gear = port.gear()
            if (!parked(gear)) return result("Rejeitado: câmbio P não confirmado")
        }
        if (!spec.confirmed) return result("Rejeitado: comando ainda não confirmado")
        if (key.startsWith("media_")) {
            result("Enviando ao Android")
            return result(port.media(key, value))
        }
        if (!port.environmentOk()) return result("Rejeitado: interface ou firmware não confirmada")
        // Só valores inteiros com escala comprovada podem chegar a setInt.
        val dev = spec.dev ?: return result("Rejeitado: dispositivo não confirmado")
        val fid = spec.fid ?: return result("Rejeitado: endereço de escrita não confirmado")
        if (!port.enabled() || !port.allowed(key)) return result("Rejeitado: liberação retirada antes do envio")
        if (spec.moves && !parked(port.gear())) return result("Rejeitado: câmbio P não confirmado")
        result("Enviando")
        // Passageiro aquecer (carro 29/10): a referência E4 e o teste mandam 6 antes do nível.
        if (key == "seat_heat_pass" && value > 0.0) {
            val prep = port.sendOnce("service call autoservice 6 i32 $dev i32 $fid i32 6")
            if (!prep) return result("Resposta do envio inconclusiva; não repetido")
        }
        val sent = port.sendOnce("service call autoservice 6 i32 $dev i32 $fid i32 ${toWire(spec, value)}")
        if (!sent) return result("Resposta do envio inconclusiva; não repetido")
        return Outcome.Sent(spec)
    }
    /** Releitura depois de um `send` que mandou de verdade (`Outcome.Sent`). Pode rodar bem depois
     * do `send` ter devolvido o controle para o próximo toque — ver `CarCommandRuntime`. */
    suspend fun confirm(spec: Spec, key: String, value: Double): String {
        val actual = port.read(key)
        val actualDecoded = actual?.let { fromWire(spec, it) }
        val message = if (spec.readbackKnown && actualDecoded == value) "Confirmado pelo estado relido" else "Não confirmado pelo carro (${actualDecoded ?: "sem dado"})"
        port.log(port.now(), key.take(64), value, message)
        return message
    }
    /** `send` + `confirm` em sequência, para quem não precisa (ou não pode, como os testes
     * síncronos) separar os dois passos. `CarCommandRuntime` não usa este atalho. */
    suspend fun execute(key: String, value: Double): String = when (val outcome = send(key, value)) {
        is Outcome.Done -> outcome.message
        is Outcome.Sent -> confirm(outcome.spec, key, value)
    }
    companion object {
        fun parked(g: Gear) = g.real && g.fresh && g.value == 1
        fun valid(s: Spec, v: Double): Boolean = v.isFinite() && v >= s.min && v <= s.max &&
            kotlin.math.abs((v - s.min) / s.step - kotlin.math.round((v - s.min) / s.step)) < 0.000001
        /** Valor lógico → valor no fio: aplica `onWire` quando definido (ex.: AUTO 0/1 → 1/2), senão identidade. */
        fun toWire(spec: Spec, value: Double): Int = spec.onWire[value.toInt()] ?: value.toInt()
        /** Valor no fio (lido de volta) → valor lógico: inverso de `onWire`, senão identidade. */
        fun fromWire(spec: Spec, raw: Double): Double =
            spec.onWire.entries.firstOrNull { it.value == raw.toInt() }?.key?.toDouble() ?: raw
        /** AUTO/A-C/recirculação/desembaçador/energia: um toque alterna, sem diálogo de escolha (issue #20). */
        fun toggleValue(current: Boolean?): Double = if (current == true) 0.0 else 1.0
        /** Ventilação sobe/desce um nível por toque, sem lista modal (issue #20). */
        fun fanStep(current: Int?, up: Boolean): Double = ((current ?: 0) + if (up) 1 else -1).coerceIn(0, 7).toDouble()
        /** Qualquer controle numérico com passo e limites do próprio Spec (temperatura, bancos):
         * sobe/desce um passo por toque, sem lista modal (issue #20, achado extra de 29/10). */
        fun stepValue(spec: Spec, current: Double?, up: Boolean): Double =
            ((current ?: spec.min) + if (up) spec.step else -spec.step).coerceIn(spec.min, spec.max)
        // IDs de leitura NÃO são reutilizados como setters. Pendências ficam sem endereço e bloqueadas.
        val CATALOG: List<Spec> = listOf(
            Spec("ac_temp_driver", "Temperatura motorista", 1000, 501219368, min=16.0, max=30.0, confirmed=true, readbackKnown=true),
            Spec("ac_temp_pass", "Temperatura passageiro", 1000, 501219376, min=16.0, max=30.0, confirmed=true, readbackKnown=true),
            Spec("ac_on", "Ar ligado", 1000, 501219364, confirmed=true), Spec("ac_fan", "Ventilação", 1000, 501219340, max=7.0, confirmed=true, readbackKnown=true),
            Spec("ac_auto", "AUTO", 1000, 501219352, confirmed=true, onWire=mapOf(0 to 1, 1 to 2), readbackKnown=true), Spec("ac_compressor", "A/C", 1000, 501219344, confirmed=true),
            Spec("ac_recirc", "Recirculação", 1000, 501219355, confirmed=true, readbackKnown=true), Spec("ac_defrost", "Desembaçador", 1000, 501219362, confirmed=true),
            // Bancos: confirmados no carro 29/10 ~16:00 (P). Fio 1=off, 2=nível1, 3=nível2
            // (só 2 níveis; SET 0 → erro ffffd8e2). GET Setting 1023 / 0x4FA0000B|08|13|10.
            // onWire lógico 0/1/2 → fio 1/2/3. Passageiro aquecer: prep 6 antes do nível (E4+carro).
            Spec("seat_heat_driver", "Aquecer motorista", 1001, 1125122068, max=2.0, confirmed=true, readbackKnown=true, onWire=mapOf(0 to 1, 1 to 2, 2 to 3)),
            Spec("seat_vent_driver", "Ventilar motorista", 1001, 1125122064, max=2.0, confirmed=true, readbackKnown=true, onWire=mapOf(0 to 1, 1 to 2, 2 to 3)),
            Spec("seat_heat_pass", "Aquecer passageiro", 1001, 1125122076, max=2.0, confirmed=true, readbackKnown=true, onWire=mapOf(0 to 1, 1 to 2, 2 to 3)),
            Spec("seat_vent_pass", "Ventilar passageiro", 1001, 1125122072, max=2.0, confirmed=true, readbackKnown=true, onWire=mapOf(0 to 1, 1 to 2, 2 to 3)),
            // Porta-malas: OPEN confirmado SET 1001/1125122080=1 (GET 1001/0x2940001A 0→1), só em P.
            // Fechar: desconhecido — Spec só aceita 1 (abrir). Vidros: sem escrita, ficam bloqueados.
            Spec("trunk", "Porta-malas", 1001, 1125122080, min=1.0, max=1.0, confirmed=true, readbackKnown=true, moves=true),
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
