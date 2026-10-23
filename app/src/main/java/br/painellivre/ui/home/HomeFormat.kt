package br.painellivre.ui.home

import br.painellivre.ui.cockpit.CarVisual
import java.util.Locale
import kotlin.math.round

/**
 * Converte [VehicleUiState] em textos curtos para a tela. Kotlin puro, sem Android:
 * `null` e número inválido viram traço, nunca "indisponível" e nunca zero inventado.
 */
object HomeFormat {
    const val DASH = "—"

    fun present(state: VehicleUiState): HomePresentation {
        val soc = soc(state.socPercent)
        val rangeKm = totalRangeKm(state.evRangeKm, state.fuelRangeKm)
        val driver = seatLine(state.driverSeat)
        val passenger = seatLine(state.passengerSeat)
        val doors = doorsLabel(state.doors)
        val windows = windowsLabel(state.windows)
        val sunroof = sunroofLabel(state.sunroof)
        val lock = lockLabel(state.locked)
        val charge = chargeLine(state.charging, state.chargePowerKw, state.chargeRemainingMin)
        val temp = tempText(state.cabinTempC)
        val presentation = HomePresentation(
            sample = state.sample,
            socText = soc?.toString() ?: DASH,
            socPercent = soc,
            socTone = socTone(soc),
            showPercentSign = soc != null,
            rangeText = rangeKm?.let { "$it km" } ?: DASH,
            rangeDetail = rangeDetail(state.evRangeKm, state.fuelRangeKm),
            rangeKnown = rangeKm != null,
            chargeText = charge,
            charging = state.charging == true,
            cabinTemp = temp,
            cabinKnown = temp != DASH,
            acText = acText(state.acOn),
            acOn = state.acOn,
            driverSeat = driver,
            driverTone = seatTone(state.driverSeat),
            passengerSeat = passenger,
            passengerTone = seatTone(state.passengerSeat),
            lockText = lock,
            locked = state.locked,
            doorsText = doors,
            doorsTone = openableTone(doors),
            windowsText = windows,
            windowsTone = openableTone(windows),
            sunroofText = sunroof,
            sunroofTone = when (state.sunroof) {
                null -> Tone.MUTED
                Sunroof.CLOSED -> Tone.NORMAL
                Sunroof.VENT, Sunroof.OPEN -> Tone.ACCENT
            },
            tires = TireReadout(
                frontLeft = tireText(state.tires.frontLeftBar),
                frontRight = tireText(state.tires.frontRightBar),
                rearLeft = tireText(state.tires.rearLeftBar),
                rearRight = tireText(state.tires.rearRightBar),
                frontLeftOpen = state.doors.frontLeft,
                frontRightOpen = state.doors.frontRight,
                rearLeftOpen = state.doors.rearLeft,
                rearRightOpen = state.doors.rearRight,
                trunkOpen = state.doors.trunk,
            ),
            car = carVisual(state),
        )
        return presentation.copy(summary = summary(presentation))
    }

    /** Lateral desenhada = lado do motorista. O outro lado vira um aviso, sem sumir. */
    fun carVisual(state: VehicleUiState): CarVisual = CarVisual(
        frontDoorOpen = state.doors.frontLeft,
        rearDoorOpen = state.doors.rearLeft,
        farDoorOpen = farSide(state.doors.frontRight, state.doors.rearRight),
        trunkOpen = state.doors.trunk,
        locked = state.locked,
        chargePortOpen = state.chargePortOpen,
        charging = state.charging,
    )

    fun soc(percent: Int?): Int? = percent?.takeIf { it in 0..100 }

    fun socTone(percent: Int?): Tone = when (percent) {
        null -> Tone.MUTED
        in 0..10 -> Tone.DANGER
        in 11..20 -> Tone.WARN
        else -> Tone.OK
    }

    fun totalRangeKm(ev: Int?, fuel: Int?): Int? {
        val parts = listOfNotNull(ev?.takeIf { it >= 0 }, fuel?.takeIf { it >= 0 })
        if (parts.isEmpty()) return null
        return parts.sum()
    }

    fun rangeDetail(ev: Int?, fuel: Int?): String {
        val e = ev?.takeIf { it >= 0 }
        val f = fuel?.takeIf { it >= 0 }
        return when {
            e != null && f != null -> "Elétrica $e · Combustão $f"
            e != null -> "Autonomia elétrica"
            f != null -> "Autonomia a combustão"
            else -> ""
        }
    }

    fun chargeLine(charging: Boolean?, powerKw: Double?, remainingMin: Int?): String {
        val power = powerKw?.takeIf { it.isFinite() && it >= 0 }?.let { formatKw(it) }
        val time = remainingMin?.takeIf { it >= 0 }?.let { formatDuration(it) }
        val detail = listOfNotNull(power, time).joinToString(" · ")
        return when {
            charging == null && detail.isEmpty() -> ""
            charging == true && detail.isEmpty() -> "Carregando"
            charging == true -> detail
            charging == false && detail.isEmpty() -> "Sem carga"
            else -> detail
        }
    }

    fun formatKw(kw: Double): String {
        val rounded = round(kw * 10.0) / 10.0
        val num = if (rounded % 1.0 == 0.0) rounded.toInt().toString()
        else String.format(Locale.US, "%.1f", rounded).replace('.', ',')
        return "$num kW"
    }

    fun formatDuration(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    fun tempText(c: Double?): String {
        if (c == null || !c.isFinite() || c < -40.0 || c > 60.0) return DASH
        val rounded = round(c * 10.0) / 10.0
        val body = if (rounded % 1.0 == 0.0) rounded.toInt().toString()
        else String.format(Locale.US, "%.1f", rounded).replace('.', ',')
        return "$body°"
    }

    fun acText(on: Boolean?): String = when (on) {
        null -> DASH
        true -> "A/C ligado"
        false -> "A/C desligado"
    }

    fun seatLine(seat: SeatComfort): String {
        val vent = seat.ventilation?.takeIf { it in 0..3 }
        val heat = seat.heat?.takeIf { it in 0..3 }
        if (vent == null && heat == null) return DASH
        val parts = buildList {
            if (vent != null && vent > 0) add("Ventilação $vent")
            if (heat != null && heat > 0) add("Aquecimento $heat")
        }
        return parts.joinToString(" · ").ifEmpty { "Desligado" }
    }

    fun seatTone(seat: SeatComfort): Tone {
        val vent = seat.ventilation?.takeIf { it in 0..3 }
        val heat = seat.heat?.takeIf { it in 0..3 }
        return when {
            heat != null && heat > 0 -> Tone.WARN
            vent != null && vent > 0 -> Tone.ACCENT
            vent == null && heat == null -> Tone.MUTED
            else -> Tone.NORMAL
        }
    }

    fun doorsLabel(doors: Doors): String {
        val named = listOf(
            doors.frontLeft to "Motorista",
            doors.frontRight to "Passageiro",
            doors.rearLeft to "Traseira esq.",
            doors.rearRight to "Traseira dir.",
            doors.trunk to "Porta-malas",
        )
        val known = named.filter { it.first != null }
        if (known.isEmpty()) return DASH
        val open = known.filter { it.first == true }.map { it.second }
        return when {
            open.isEmpty() -> "Fechadas"
            open.size == 1 -> open[0]
            else -> "${open.size} abertas"
        }
    }

    fun windowsLabel(windows: Windows): String {
        val known = listOfNotNull(windows.frontLeft, windows.frontRight, windows.rearLeft, windows.rearRight)
        if (known.isEmpty()) return DASH
        val open = known.count { it }
        return when {
            open == 0 -> "Fechados"
            open == 1 -> "1 aberto"
            else -> "$open abertos"
        }
    }

    fun sunroofLabel(sunroof: Sunroof?): String = when (sunroof) {
        null -> DASH
        Sunroof.CLOSED -> "Fechado"
        Sunroof.VENT -> "Entreaberto"
        Sunroof.OPEN -> "Aberto"
    }

    fun lockLabel(locked: Boolean?): String = when (locked) {
        null -> DASH
        true -> "Travado"
        false -> "Destravado"
    }

    fun tireText(bar: Double?): String {
        if (bar == null || !bar.isFinite() || bar < 0.0 || bar > 6.0) return DASH
        return String.format(Locale.US, "%.1f", bar).replace('.', ',')
    }

    private fun farSide(a: Boolean?, b: Boolean?): Boolean? {
        val known = listOfNotNull(a, b)
        if (known.isEmpty()) return null
        return known.any { it }
    }

    /** Tom de um texto que já é "Fechadas"/"Fechados"/"Fechado", um aviso de aberto, ou traço. */
    private fun openableTone(label: String): Tone = when {
        label == DASH -> Tone.MUTED
        label == "Fechadas" || label == "Fechados" || label == "Fechado" -> Tone.NORMAL
        else -> Tone.WARN
    }

    private fun summary(p: HomePresentation): String = listOfNotNull(
        "Carro",
        "bateria ${p.socText}" + if (p.showPercentSign) "%" else "",
        "autonomia ${p.rangeText}",
        p.chargeText.takeIf { it.isNotBlank() },
        "clima ${p.cabinTemp}",
        "portas ${p.doorsText}",
        "trava ${p.lockText}",
    ).joinToString(". ")
}

enum class Tone { OK, WARN, DANGER, ACCENT, MUTED, NORMAL }

data class HomePresentation(
    val sample: Boolean,
    val socText: String,
    val socPercent: Int?,
    val socTone: Tone,
    val showPercentSign: Boolean,
    val rangeText: String,
    val rangeDetail: String,
    val rangeKnown: Boolean,
    val chargeText: String,
    val charging: Boolean,
    val cabinTemp: String,
    val cabinKnown: Boolean,
    val acText: String,
    val acOn: Boolean?,
    val driverSeat: String,
    val driverTone: Tone,
    val passengerSeat: String,
    val passengerTone: Tone,
    val lockText: String,
    val locked: Boolean?,
    val doorsText: String,
    val doorsTone: Tone,
    val windowsText: String,
    val windowsTone: Tone,
    val sunroofText: String,
    val sunroofTone: Tone,
    val tires: TireReadout,
    val car: CarVisual,
    val summary: String = "",
)

data class TireReadout(
    val frontLeft: String,
    val frontRight: String,
    val rearLeft: String,
    val rearRight: String,
    val frontLeftOpen: Boolean?,
    val frontRightOpen: Boolean?,
    val rearLeftOpen: Boolean?,
    val rearRightOpen: Boolean?,
    val trunkOpen: Boolean?,
)
