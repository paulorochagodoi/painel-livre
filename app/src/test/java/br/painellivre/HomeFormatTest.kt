package br.painellivre

import br.painellivre.ui.cockpit.Palette
import br.painellivre.ui.home.Doors
import br.painellivre.ui.home.HomeFormat
import br.painellivre.ui.home.SampleVehicleUiSource
import br.painellivre.ui.home.SeatComfort
import br.painellivre.ui.home.Sunroof
import br.painellivre.ui.home.Tone
import br.painellivre.ui.home.VehicleUiState
import br.painellivre.ui.home.VehicleUiSources
import br.painellivre.ui.home.Windows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos da tela inicial. Número inválido ou ausente vira traço, nunca zero inventado. */
class HomeFormatTest {

    @Test fun sampleReadsAsAParkedCharge() {
        val p = HomeFormat.present(VehicleUiState.sample())
        assertTrue(p.sample)
        assertEquals("72", p.socText)
        assertEquals(72, p.socPercent)
        assertEquals(Tone.OK, p.socTone)
        assertTrue(p.showPercentSign)
        assertEquals("498 km", p.rangeText)
        assertEquals("Elétrica 78 · Combustão 420", p.rangeDetail)
        assertEquals("6,6 kW · 48 min", p.chargeText)
        assertTrue(p.charging)
        assertEquals("22°", p.cabinTemp)
        assertEquals("A/C ligado", p.acText)
        assertEquals("Ventilação 2", p.driverSeat)
        assertEquals(Tone.ACCENT, p.driverTone)
        assertEquals("Aquecimento 1", p.passengerSeat)
        assertEquals(Tone.WARN, p.passengerTone)
        assertEquals("Destravado", p.lockText)
        assertEquals("Motorista", p.doorsText)
        assertEquals(Tone.WARN, p.doorsTone)
        assertEquals("Fechados", p.windowsText)
        assertEquals("Fechado", p.sunroofText)
        assertEquals("2,4", p.tires.frontLeft)
        assertEquals("2,3", p.tires.frontRight)
        assertTrue(p.car.frontDoorOpen == true)
        assertFalse(p.car.farDoorOpen == true)
        assertTrue(p.car.chargePortOpen == true)
        assertTrue(p.car.charging == true)
        assertEquals(false, p.car.locked)
        assertTrue(p.summary.contains("bateria 72%"))
        assertTrue(p.summary.contains("Motorista"))
    }

    @Test fun unknownBecomesDashNotZero() {
        val p = HomeFormat.present(VehicleUiState())
        assertFalse(p.sample)
        assertEquals(HomeFormat.DASH, p.socText)
        assertNull(p.socPercent)
        assertEquals(Tone.MUTED, p.socTone)
        assertFalse(p.showPercentSign)
        assertEquals(HomeFormat.DASH, p.rangeText)
        assertEquals("", p.rangeDetail)
        assertEquals("", p.chargeText)
        assertFalse(p.charging)
        assertEquals(HomeFormat.DASH, p.cabinTemp)
        assertEquals(HomeFormat.DASH, p.acText)
        assertEquals(HomeFormat.DASH, p.driverSeat)
        assertEquals(HomeFormat.DASH, p.passengerSeat)
        assertEquals(HomeFormat.DASH, p.lockText)
        assertEquals(HomeFormat.DASH, p.doorsText)
        assertEquals(HomeFormat.DASH, p.windowsText)
        assertEquals(HomeFormat.DASH, p.sunroofText)
        assertEquals(HomeFormat.DASH, p.tires.frontLeft)
        assertEquals(HomeFormat.DASH, p.tires.rearRight)
        assertNull(p.car.frontDoorOpen)
        assertNull(p.car.rearDoorOpen)
        assertNull(p.car.farDoorOpen)
        assertNull(p.car.trunkOpen)
        assertNull(p.car.locked)
        assertNull(p.car.chargePortOpen)
        assertNull(p.car.charging)
        assertFalse(p.summary.contains("0"))
        assertFalse(p.summary.contains("indispon"))
    }

    @Test fun outOfRangeNumbersAreUnknown() {
        val p = HomeFormat.present(
            VehicleUiState(
                socPercent = 140,
                evRangeKm = -3,
                fuelRangeKm = -1,
                cabinTempC = 80.0,
                chargePowerKw = Double.NaN,
                chargeRemainingMin = -5,
                driverSeat = SeatComfort(ventilation = 9, heat = -1),
                tires = br.painellivre.ui.home.Tires(frontLeftBar = 9.0),
            ),
        )
        assertEquals(HomeFormat.DASH, p.socText)
        assertEquals(HomeFormat.DASH, p.rangeText)
        assertEquals("", p.chargeText)
        assertEquals(HomeFormat.DASH, p.cabinTemp)
        assertEquals(HomeFormat.DASH, p.driverSeat)
        assertEquals(HomeFormat.DASH, p.tires.frontLeft)
    }

    @Test fun partialRangeAndCharge() {
        assertEquals(80, HomeFormat.totalRangeKm(80, null))
        assertEquals(120, HomeFormat.totalRangeKm(null, 120))
        assertEquals("Autonomia elétrica", HomeFormat.rangeDetail(80, null))
        assertEquals("Autonomia a combustão", HomeFormat.rangeDetail(null, 40))
        assertEquals("Carregando", HomeFormat.chargeLine(true, null, null))
        assertEquals("Sem carga", HomeFormat.chargeLine(false, null, null))
        assertEquals("7 kW", HomeFormat.chargeLine(true, 7.0, null))
        assertEquals("1 h 30 min", HomeFormat.chargeLine(true, null, 90))
        assertEquals("", HomeFormat.chargeLine(null, null, null))
    }

    @Test fun durationsPowersAndTemperatures() {
        assertEquals("0 min", HomeFormat.formatDuration(0))
        assertEquals("48 min", HomeFormat.formatDuration(48))
        assertEquals("1 h", HomeFormat.formatDuration(60))
        assertEquals("2 h 5 min", HomeFormat.formatDuration(125))
        assertEquals("6 kW", HomeFormat.formatKw(6.0))
        assertEquals("6,6 kW", HomeFormat.formatKw(6.64))
        assertEquals("11 kW", HomeFormat.formatKw(11.0))
        assertEquals("22°", HomeFormat.tempText(22.0))
        assertEquals("22,5°", HomeFormat.tempText(22.5))
        assertEquals(HomeFormat.DASH, HomeFormat.tempText(null))
        assertEquals(HomeFormat.DASH, HomeFormat.tempText(Double.NaN))
    }

    @Test fun socToneSteps() {
        assertEquals(Tone.DANGER, HomeFormat.socTone(0))
        assertEquals(Tone.DANGER, HomeFormat.socTone(10))
        assertEquals(Tone.WARN, HomeFormat.socTone(11))
        assertEquals(Tone.WARN, HomeFormat.socTone(20))
        assertEquals(Tone.OK, HomeFormat.socTone(21))
        assertEquals(Tone.MUTED, HomeFormat.socTone(null))
        assertEquals("0", HomeFormat.present(VehicleUiState(socPercent = 0)).socText)
    }

    @Test fun doorsWindowsSunroofAndSeats() {
        assertEquals("Fechadas", HomeFormat.doorsLabel(Doors(false, false, false, false, false)))
        assertEquals(
            "2 abertas",
            HomeFormat.doorsLabel(Doors(frontLeft = true, trunk = true)),
        )
        assertEquals("Porta-malas", HomeFormat.doorsLabel(Doors(trunk = true)))
        assertEquals(HomeFormat.DASH, HomeFormat.doorsLabel(Doors()))
        assertEquals("Fechados", HomeFormat.windowsLabel(Windows(false, false, false, false)))
        assertEquals("1 aberto", HomeFormat.windowsLabel(Windows(frontRight = true)))
        assertEquals("2 abertos", HomeFormat.windowsLabel(Windows(frontLeft = true, rearRight = true)))
        assertEquals(HomeFormat.DASH, HomeFormat.windowsLabel(Windows()))
        assertEquals("Entreaberto", HomeFormat.sunroofLabel(Sunroof.VENT))
        assertEquals("Aberto", HomeFormat.sunroofLabel(Sunroof.OPEN))
        assertEquals(HomeFormat.DASH, HomeFormat.sunroofLabel(null))
        assertEquals("Desligado", HomeFormat.seatLine(SeatComfort(0, 0)))
        assertEquals("Ventilação 1 · Aquecimento 2", HomeFormat.seatLine(SeatComfort(1, 2)))
        assertEquals(Tone.WARN, HomeFormat.seatTone(SeatComfort(1, 2)))
        assertEquals(Tone.NORMAL, HomeFormat.seatTone(SeatComfort(0, null)))
    }

    @Test fun farSideDoorDoesNotHide() {
        val bothUnknown = HomeFormat.carVisual(VehicleUiState())
        assertNull(bothUnknown.farDoorOpen)
        val hiddenOpen = HomeFormat.carVisual(VehicleUiState(doors = Doors(frontRight = true)))
        assertEquals(true, hiddenOpen.farDoorOpen)
        assertNull(hiddenOpen.frontDoorOpen)
        val bothClosed = HomeFormat.carVisual(
            VehicleUiState(doors = Doors(frontRight = false, rearRight = false)),
        )
        assertEquals(false, bothClosed.farDoorOpen)
    }

    @Test fun homeSourceIsTheSampleUntilARealOneIsPlugged() {
        val source = VehicleUiSources.forHome()
        assertTrue(source.state.value.sample)
        assertEquals(72, source.state.value.socPercent)
        assertEquals(72, SampleVehicleUiSource().state.value.socPercent)
    }

    @Test fun nightCardsStayOpaqueAndReadable() {
        val night = Palette.NIGHT
        assertEquals(255, night.card ushr 24)
        assertEquals(255, Palette.DAY.card ushr 24)
        assertTrue(lum(night.text1) - lum(night.card) > 180)
        assertTrue(lum(night.text2) - lum(night.card) > 120)
        val accent = lum(night.accent)
        assertTrue(accent - lum(night.card) > 60)
        // Azul claro lavado (o da tela antiga) passa de ~130. O acento novo fica abaixo disso.
        assertTrue(accent < 130)
        assertTrue(lum(Palette.DAY.text1) < 40)
        assertTrue(lum(Palette.DAY.card) > 240)
    }

    private fun lum(color: Int): Double {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
