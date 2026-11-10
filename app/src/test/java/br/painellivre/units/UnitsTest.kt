package br.painellivre.units

import br.painellivre.nav.native.RouteTracker
import br.painellivre.ui.aura.AuraFormat
import br.painellivre.units.Units.Choice
import br.painellivre.units.Units.Distance
import br.painellivre.units.Units.Electric
import br.painellivre.units.Units.Fuel
import br.painellivre.units.Units.Pressure
import br.painellivre.units.Units.ShortDistance
import br.painellivre.units.Units.Speed
import br.painellivre.units.Units.Temperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #25. A suíte anterior (274 testes, 0 falhas, na main da 0.5.7) continua válida:
 * estes casos travam a conversão e o texto que clima, energia, painel e navegação compartilham.
 */
class UnitsTest {

    private val imperial = Choice(
        distance = Distance.MI,
        shortDistance = ShortDistance.FT,
        speed = Speed.MPH,
        pressure = Pressure.BAR,
        temperature = Temperature.F,
        electric = Electric.MI_PER_KWH,
        fuel = Fuel.MPG,
    )

    @Test fun factoryDefaultIsMetricExceptTirePressure() {
        val d = Choice.DEFAULT
        assertEquals(Distance.KM, d.distance)
        assertEquals(ShortDistance.M, d.shortDistance)
        assertEquals(Speed.KMH, d.speed)
        assertEquals(Pressure.PSI, d.pressure)
        assertEquals(Temperature.C, d.temperature)
        assertEquals(Electric.KWH_100, d.electric)
        assertEquals(Fuel.L_100, d.fuel)
    }

    @Test fun conversionsUseInternationalFactors() {
        assertEquals(1.0, Units.miles(Units.KM_PER_MILE), 1e-12)
        assertEquals(1.0, Units.feet(Units.M_PER_FOOT), 1e-12)
        assertEquals(1.0, Units.psi(Units.KPA_PER_PSI), 1e-12)
        assertEquals(32.0, Units.fahrenheit(0.0), 1e-12)
        assertEquals(71.6, Units.fahrenheit(22.0), 1e-9)
        assertEquals(1.0, Units.mph(Units.KM_PER_MILE), 1e-12)
        assertEquals(Units.MILES_PER_100_KM, Units.milesPerKwh(1.0), 1e-9)
        assertEquals(Units.MPG_PER_L_100, Units.mpgUs(1.0), 1e-9)
        // Ida e volta não perde o kPa de origem.
        assertEquals(287.0, Units.psi(287.0) * Units.KPA_PER_PSI, 1e-9)
    }

    @Test fun climateKeepsCelsiusByDefaultAndConvertsSetpointAndAmbient() {
        assertEquals("22,0°C", Units.temperature(22.0))
        assertEquals("23,5°C", Units.temperature(23.5))
        assertEquals("24°C", Units.temperature(23.6, whole = true))
        assertEquals("71,6°F", Units.temperature(22.0, imperial))
        assertEquals("74,5°F", Units.temperature(23.6, imperial))
        assertEquals("74°F", Units.temperature(23.6, imperial, whole = true))
        assertEquals("—", Units.temperature(null))
        assertEquals("—", Units.temperature(Double.NaN, imperial, whole = true))
        // A Aura não formata por conta própria: o mesmo texto do conversor.
        assertEquals(Units.temperature(22.0, imperial), AuraFormat.temp(22.0, imperial))
        assertEquals(Units.temperature(31.0, imperial, whole = true), AuraFormat.tempWhole(31.0, imperial))
    }

    @Test fun energyRangeAndConsumption() {
        assertEquals("6,4 km", Units.distanceKm(6.4))
        assertEquals("0,4 km", Units.distanceKm(0.42))
        assertEquals("12 km", Units.distanceKm(12.3))
        assertEquals("10 km", Units.distanceKm(9.97))
        assertEquals("420 km", Units.distanceKm(420.0))
        assertEquals("261 mi", Units.distanceKm(420.0, imperial))
        assertEquals("6,4", Units.distanceSplit(6.4).number)
        assertEquals("km", Units.distanceSplit(6.4).unit)
        assertEquals("—", Units.distanceKm(null))
        assertEquals(Units.distanceKm(6.4, imperial), AuraFormat.km(6.4, imperial))

        assertEquals("15,0 kWh/100 km", Units.electric(15.0))
        assertEquals("4,1 mi/kWh", Units.electric(15.0, imperial))
        assertEquals("0,0 kWh/100 km", Units.electric(0.0))
        assertEquals("—", Units.electric(0.0, imperial))
        assertEquals("—", Units.electric(-1.0))

        assertEquals("8,0 L/100 km", Units.fuel(8.0))
        assertEquals("29,4 mpg", Units.fuel(8.0, imperial))
        assertEquals("—", Units.fuel(0.0, imperial))
        assertEquals("—", Units.fuel(null))
    }

    @Test fun tirePressureDefaultsToPsiAndKeepsBarAndKpa() {
        assertEquals("41,6 psi", Units.pressureKpa(287.0))
        assertEquals("2,9 bar", Units.pressureKpa(287.0, Choice(pressure = Pressure.BAR)))
        assertEquals("287 kPa", Units.pressureKpa(287.0, Choice(pressure = Pressure.KPA)))
        assertEquals("41,6", Units.pressureNumber(287.0))
        assertEquals("—", Units.pressureKpa(null))
    }

    @Test fun speedAndShortDistance() {
        assertEquals("12 km/h", Units.speedKmh(12.0))
        assertEquals("7,5 mph", Units.speedKmh(12.0, imperial))
        assertEquals("—", Units.speedKmh(null))
        assertEquals("400 m", Units.navDistance(400, "ignora"))
        assertEquals("1312 ft", Units.navDistance(400, "400 m", imperial))
    }

    @Test fun clusterAndNavigationShareTheSameDistanceText() {
        // ClusterActivity.render e LauncherActivity.renderNav chamam navDistance
        // para a manobra e para o restante. O número é o mesmo nos dois cartões.
        val maneuverOnCluster = Units.navDistance(400, "0.2 mi")
        val maneuverOnNav = Units.navDistance(400, "0.2 mi")
        assertEquals(maneuverOnCluster, maneuverOnNav)
        assertEquals("400 m", maneuverOnCluster)

        val remainingOnCluster = Units.navDistance(7400, "4.6 mi")
        val remainingOnNav = Units.navDistance(7400, "4.6 mi")
        assertEquals("7,4 km", remainingOnCluster)
        assertEquals(remainingOnCluster, remainingOnNav)

        assertEquals("1,2 km", Units.navDistance(1200, "1,2 km"))
        assertEquals("0,7 mi", Units.navDistance(1200, "1,2 km", imperial))
        assertEquals("4,6 mi", Units.navDistance(7400, "7,4 km", imperial))
        assertEquals("80 m", Units.navDistance(80, "80 m"))
        // Sem metros interpretados, o texto da notificação permanece.
        assertNull(Units.navDistance(null, null))
        assertEquals("0.2 mi", Units.navDistance(null, "0.2 mi"))
        assertEquals("300 m", Units.navDistance(-1, "300 m"))
        // A rota nativa grava o mesmo texto de reserva do padrão de fábrica.
        assertEquals(Units.navDistance(400, null), RouteTracker.meters(400))
        assertEquals(Units.navDistance(1200, null), RouteTracker.meters(1200))
        assertEquals(Units.navDistance(5000, null), RouteTracker.meters(5000))
    }
}
