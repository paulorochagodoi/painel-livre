package br.painellivre.ui.home

/**
 * Estado da tela inicial. Cada campo é anulável de propósito: `null` significa "ainda não sei",
 * nunca zero. Quem preenche isto é uma [VehicleUiSource]. Esta classe não lê o carro e não
 * envia comando nenhum.
 *
 * Porta `true` = aberta. Vidro `true` = aberto. Níveis de banco vão de 0 (desligado) a 3.
 */
data class VehicleUiState(
    /** Exemplo de layout, não é leitura do carro. A tela mostra um aviso enquanto isto for verdadeiro. */
    val sample: Boolean = false,
    val socPercent: Int? = null,
    val evRangeKm: Int? = null,
    val fuelRangeKm: Int? = null,
    val charging: Boolean? = null,
    val chargePowerKw: Double? = null,
    val chargeRemainingMin: Int? = null,
    val chargePortOpen: Boolean? = null,
    val cabinTempC: Double? = null,
    val acOn: Boolean? = null,
    val driverSeat: SeatComfort = SeatComfort(),
    val passengerSeat: SeatComfort = SeatComfort(),
    val doors: Doors = Doors(),
    val locked: Boolean? = null,
    val windows: Windows = Windows(),
    val sunroof: Sunroof? = null,
    val tires: Tires = Tires(),
) {
    companion object {
        /** Números verossímeis de um DM-i parado na tomada, só para desenhar a tela. */
        fun sample() = VehicleUiState(
            sample = true,
            socPercent = 72,
            evRangeKm = 78,
            fuelRangeKm = 420,
            charging = true,
            chargePowerKw = 6.6,
            chargeRemainingMin = 48,
            chargePortOpen = true,
            cabinTempC = 22.0,
            acOn = true,
            driverSeat = SeatComfort(ventilation = 2, heat = 0),
            passengerSeat = SeatComfort(ventilation = 0, heat = 1),
            doors = Doors(
                frontLeft = true,
                frontRight = false,
                rearLeft = false,
                rearRight = false,
                trunk = false,
            ),
            locked = false,
            windows = Windows(
                frontLeft = false,
                frontRight = false,
                rearLeft = false,
                rearRight = false,
            ),
            sunroof = Sunroof.CLOSED,
            tires = Tires(
                frontLeftBar = 2.4,
                frontRightBar = 2.3,
                rearLeftBar = 2.4,
                rearRightBar = 2.4,
            ),
        )
    }
}

data class SeatComfort(
    val ventilation: Int? = null,
    val heat: Int? = null,
)

data class Doors(
    val frontLeft: Boolean? = null,
    val frontRight: Boolean? = null,
    val rearLeft: Boolean? = null,
    val rearRight: Boolean? = null,
    val trunk: Boolean? = null,
)

data class Windows(
    val frontLeft: Boolean? = null,
    val frontRight: Boolean? = null,
    val rearLeft: Boolean? = null,
    val rearRight: Boolean? = null,
)

enum class Sunroof { CLOSED, VENT, OPEN }

data class Tires(
    val frontLeftBar: Double? = null,
    val frontRightBar: Double? = null,
    val rearLeftBar: Double? = null,
    val rearRightBar: Double? = null,
)
