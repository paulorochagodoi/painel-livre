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
        val isEmpty get() = listOf(batteryPercent, evRangeKm, fuelPercent, fuelRangeKm, odometerKm).all { it == null }
    }

    private var statistic: Any? = null
    private var lastError: String? = null

    fun read(ctx: Context): Snapshot {
        val dev = statistic ?: device(ctx, "android.hardware.bydauto.statistic.BYDAutoStatisticDevice")?.also { statistic = it }
            ?: return Snapshot(null, null, null, null, null, lastError ?: "SDK BYD indisponível")
        return Snapshot(
            batteryPercent = (call(dev, "getElecPercentageValue") as? Number)?.toDouble()?.takeIf { it in 0.0..100.0 },
            evRangeKm = (call(dev, "getElecDrivingRangeValue") as? Number)?.toInt()?.takeIf { it in 0..2000 },
            fuelPercent = (call(dev, "getFuelPercentageValue") as? Number)?.toInt()?.takeIf { it in 0..100 },
            fuelRangeKm = (call(dev, "getFuelDrivingRangeValue") as? Number)?.toInt()?.takeIf { it in 0..3000 },
            odometerKm = (call(dev, "getTotalMileageValue") as? Number)?.toInt()?.takeIf { it in 0..3_000_000 },
        )
    }

    private fun device(ctx: Context, cls: String): Any? = try {
        Class.forName(cls).getMethod("getInstance", Context::class.java).invoke(null, ctx.applicationContext)
    } catch (t: Throwable) {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        lastError = when (cause) {
            is ClassNotFoundException -> "SDK BYD não encontrado (não é uma central BYD?)"
            is SecurityException -> "Sem permissão BYDAUTO_STATISTIC_GET — use Diagnóstico › Conceder permissões"
            else -> "${cause.javaClass.simpleName}: ${cause.message}"
        }
        AppLog.w("VehicleData: $lastError")
        null
    }

    private fun call(target: Any, method: String): Any? = try {
        target.javaClass.getMethod(method).invoke(target)
    } catch (t: Throwable) {
        null
    }
}
