package br.painellivre.car

/**
 * **Kotlin puro.** Endereços digitados no modo de debug, no formato
 * `soc=1014:0x4A505038:7` (chave do catálogo, dispositivo, fid, transação).
 *
 * Só transação 5 ou 7. Chave desconhecida, transação 6 e dispositivo fora da faixa são ignorados:
 * o texto nunca cria uma leitura nova nem uma escrita.
 */
object TelemetryOverrides {

    private val ITEM = Regex("""([a-z0-9_]+)\s*=\s*(10\d{2})\s*:\s*(0x[0-9a-fA-F]{1,8}|-?\d+)\s*:\s*([57])""")

    fun parse(text: String?): Map<String, TelemetryPlan.Override> {
        if (text.isNullOrBlank()) return emptyMap()
        val known = TelemetryCatalog.SIGNALS.map { it.key }.toSet()
        val out = LinkedHashMap<String, TelemetryPlan.Override>()
        for (m in ITEM.findAll(text)) {
            val key = m.groupValues[1]
            if (key !in known) continue
            val dev = m.groupValues[2].toInt()
            if (dev !in Autoservice.DEVICE_RANGE) continue
            val raw = m.groupValues[3]
            val fid = if (raw.startsWith("0x")) raw.substring(2).toLong(16).toInt() else raw.toIntOrNull() ?: continue
            val tx = m.groupValues[4].toInt()
            if (tx !in Autoservice.READ_TRANSACTIONS) continue
            out[key] = TelemetryPlan.Override(dev, fid, tx)
        }
        return out
    }
}
