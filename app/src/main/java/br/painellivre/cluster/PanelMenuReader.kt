package br.painellivre.cluster

import br.painellivre.adb.SelfAdb
import br.painellivre.car.InstrumentSdk

/**
 * **Kotlin puro.** Lê `INSTRUMENT_NAVI_TYPE` (issue #9): primeiro pelo SDK BYD no processo do app; se o
 * SDK recusar a permissão, pelo ADB local (uid 2000, o mesmo canal do `sendInfo`) com
 * [PanelMenu.ADB_READ_COMMAND] — confirmado no carro (DiLink 3.0, 2026-10-23).
 *
 * Só leitura: o único comando possível é a constante de leitura (nunca a transação 6, nunca
 * `INSTRUMENT_NAVI_TYPE_SET`). O ADB só entra quando o SDK existe e recusou por permissão (então é
 * uma central BYD) e no Android em que a transação foi conferida ([adbAllowed]). Depois da primeira
 * recusa o SDK não é tentado de novo: a permissão não muda com o app aberto.
 *
 * [sdk] e [adb] vêm de fora para os testes da JVM.
 */
class PanelMenuReader(
    private val sdk: () -> InstrumentSdk.Reading,
    private val adb: suspend (String) -> SelfAdb.Result,
    private val adbAllowed: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryMs: Long = 5_000,
) {
    private var sdkDenied = false
    private var adbPauseUntil = 0L
    private var lastAdbFailure: InstrumentSdk.Reading? = null

    suspend fun read(): InstrumentSdk.Reading {
        if (!sdkDenied) {
            val r = sdk()
            if (!r.denied) return r
            if (!adbAllowed) return r
            sdkDenied = true
        }
        // ADB fora do ar (chave recusada, porta fechada): não insiste a cada segundo.
        lastAdbFailure?.let { if (clock() < adbPauseUntil) return it }
        val r = when (val out = adb(PanelMenu.ADB_READ_COMMAND)) {
            is SelfAdb.Result.Error -> InstrumentSdk.Reading(null, "ADB: ${out.message}", "", source = "ADB")
                .also { lastAdbFailure = it; adbPauseUntil = clock() + retryMs }
            is SelfAdb.Result.Ok -> {
                lastAdbFailure = null
                val p = PanelMenu.parseParcel(out.output)
                InstrumentSdk.Reading(p.raw, p.error, "ADB uid 2000, transação ${PanelMenu.GET_TRANSACTION}", source = "ADB")
            }
        }
        return r
    }
}
