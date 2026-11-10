package br.painellivre.car

/**
 * **Kotlin puro.** Uma varredura da telemetria: o endereço principal de cada sinal e, se ele não
 * tiver valor, um endereço alternativo já documentado (outro protocolo, ou o SOC do BMS em
 * décimos). Um override de debug substitui só o principal e não mistura os alternativos, para o
 * Paulo ver exatamente o endereço que pediu.
 *
 * Porta, trava e tampa não têm alternativa: um 0/1 de outro registrador viraria "aberta".
 */
object TelemetryPlan {

    data class Probe(
        val key: String,
        val dev: Int,
        val fid: Int,
        val tx: Int,
        val kind: TelemetryCatalog.Kind,
        val primary: Boolean,
    ) {
        fun command() = Autoservice.command(tx, dev, fid)
    }

    data class Override(val dev: Int, val fid: Int, val tx: Int)

    fun probes(
        signals: List<TelemetryCatalog.Signal>,
        protocol: Autoservice.Protocol,
        resolved: Map<String, Int?>,
        overrides: Map<String, Override> = emptyMap(),
    ): List<Probe> {
        val out = ArrayList<Probe>()
        fun add(p: Probe) {
            if (out.none { it.key == p.key && it.dev == p.dev && it.fid == p.fid && it.tx == p.tx }) out += p
        }
        for (signal in signals) {
            val ov = overrides[signal.key]
            val fid = ov?.fid ?: TelemetryCatalog.resolveFid(signal, protocol, resolved[signal.key])
            val dev = ov?.dev ?: signal.dev
            val tx = ov?.tx ?: signal.tx
            add(Probe(signal.key, dev, fid, tx, signal.kind, primary = true))
            if (ov != null) continue
            val other = when (protocol) {
                Autoservice.Protocol.CAN -> signal.fid
                Autoservice.Protocol.CANFD, Autoservice.Protocol.UNKNOWN -> signal.canFid
            }
            if (other != null && other != fid) add(Probe(signal.key, signal.dev, other, signal.tx, signal.kind, primary = false))
            for (alt in signal.alternates) {
                add(Probe(signal.key, alt.dev, alt.fid, alt.tx, alt.kind, primary = false))
            }
        }
        return out
    }

    /** Primeiro valor útil; se nenhum, fica a resposta do endereço principal (sentinela visível no log). */
    fun assemble(
        signals: List<TelemetryCatalog.Signal>,
        probes: List<Probe>,
        replies: List<Autoservice.Reply>,
    ): Map<String, TelemetrySnapshot.Reading> {
        val grouped = probes.withIndex().groupBy { it.value.key }
        return signals.associate { signal ->
            var withValue: TelemetrySnapshot.Reading? = null
            var primary: TelemetrySnapshot.Reading? = null
            for (item in grouped[signal.key].orEmpty()) {
                val probe = item.value
                val reply = replies.getOrElse(item.index) { Autoservice.Reply.Garbled("") }
                val reading = TelemetrySnapshot.reading(
                    signal, probe.fid, reply, probe.dev, probe.tx, probe.kind, alternate = !probe.primary,
                )
                if (probe.primary) primary = reading
                if (withValue == null && reading.value != null) withValue = reading
            }
            signal.key to (withValue ?: primary ?: TelemetrySnapshot.reading(signal, signal.fid, Autoservice.Reply.Garbled("")))
        }
    }
}
