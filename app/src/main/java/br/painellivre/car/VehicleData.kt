package br.painellivre.car

import android.content.Context
import br.painellivre.util.AppLog

/**
 * Leitura (somente leitura!) de dados do carro pelo SDK BYD que vem na firmware
 * (`android.hardware.bydauto.*`). Os nomes dos métodos foram tirados dos stubs do SDK embutidos
 * no BYD Navigator (Telenav) — o próprio Navigator usa getElecDrivingRangeValue() etc.
 *
 * Tudo por reflexão: compila sem o SDK e, num aparelho que não é BYD, só devolve nulo.
 */
object VehicleData {

    data class Snapshot(
        val batteryPercent: Double?,
        val evRangeKm: Int?,
        val fuelPercent: Int?,
        val fuelRangeKm: Int?,
        val odometerKm: Int?,
        val error: String? = null,
    ) {
        fun diagnosticText(): String = listOf(
            "Bateria: ${batteryPercent?.let { "$it %" } ?: "indisponível"}",
            "Autonomia elétrica: ${evRangeKm?.let { "$it km" } ?: "indisponível"}",
            "Combustível: ${fuelPercent?.let { "$it %" } ?: "indisponível"}",
            "Autonomia a combustível: ${fuelRangeKm?.let { "$it km" } ?: "indisponível"}",
            "Hodômetro: ${odometerKm?.let { "$it km" } ?: "indisponível"}",
            "Portas/travas: não lidas pelo SDK (ver a leitura pelo ADB local, issue #16)",
            error ?: "Leituras estatísticas disponíveis",
        ).joinToString("\n")
        val isEmpty get() = listOf(batteryPercent, evRangeKm, fuelPercent, fuelRangeKm, odometerKm).all { it == null }
    }

    private var statistic: Any? = null
    private var lastError: String? = null

    @Synchronized
    fun read(ctx: Context): Snapshot {
        val dev = statistic ?: device(ctx, "android.hardware.bydauto.statistic.BYDAutoStatisticDevice")?.also { statistic = it }
            ?: return Snapshot(null, null, null, null, null, lastError ?: "SDK BYD indisponível")
        val reader = VehicleReadings { method -> dev.javaClass.getMethod(method).invoke(dev) }
        val battery = reader.number("getElecPercentageValue", 100.0)
        val ev = reader.number("getElecDrivingRangeValue", 2000.0)?.toInt()
        val fuel = reader.number("getFuelPercentageValue", 100.0)?.toInt()
        val range = reader.number("getFuelDrivingRangeValue", 3000.0)?.toInt()
        val odo = reader.number("getTotalMileageValue", 3_000_000.0)?.toInt()
        val error = reader.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
        report(error)
        return Snapshot(battery, ev, fuel, range, odo, error)
    }

    private fun report(error: String?) {
        // A tela consulta periodicamente: só registra mudanças para não inundar o diagnóstico.
        if (error != lastError) {
            if (error != null) AppLog.w("Dados do carro indisponíveis/parciais: $error")
            else if (lastError != null) AppLog.i("Leitura dos dados do carro recuperada")
        }
        lastError = error
    }

    private fun device(ctx: Context, cls: String): Any? = try {
        Class.forName(cls).getMethod("getInstance", Context::class.java).invoke(null, ctx.applicationContext)
            .also { if (it == null) report("SDK BYD retornou dispositivo indisponível") }
    } catch (t: Exception) {
        report(VehicleReadings.failure(t))
        null
    } catch (t: LinkageError) {
        report("SDK BYD incompatível (${t.javaClass.simpleName})")
        null
    }
}
