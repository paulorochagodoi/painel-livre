package br.painellivre

import br.painellivre.car.Autoservice
import br.painellivre.car.CarCommands
import br.painellivre.cluster.ClusterController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regressão da 0.5.7 e do painel: comandos instantâneos (clima, bancos, porta-malas),
 * sendInfo e o cartão do ClusterActivity. A telemetria nova não pode reabrir escrita
 * nem voltar a mover o Waze para o display sem toque.
 */
class Release057RegressionTest {

    private class Port : CarCommands.Port {
        var reads = 0
        val sends = mutableListOf<String>()
        override fun enabled() = true
        override fun allowed(key: String) = true
        override fun now() = 1L
        override suspend fun gear() = CarCommands.Gear(1, true, true)
        override suspend fun environmentOk() = true
        override suspend fun sendOnce(command: String): Boolean { sends += command; return true }
        override suspend fun read(key: String): Double? { reads++; return 22.0 }
        override suspend fun media(key: String, value: Double) = "ok"
        override fun log(at: Long, key: String, value: Double, result: String) {}
    }

    @Test fun sendReturnsBeforeReadbackForClimateSeatsAndTrunk() = runBlocking {
        val port = Port()
        val commands = CarCommands(port)
        val climate = commands.send("ac_temp_driver", 22.0)
        assertTrue(climate is CarCommands.Outcome.Sent)
        assertEquals(0, port.reads)
        assertEquals(listOf("service call autoservice 6 i32 1000 i32 501219368 i32 22"), port.sends)

        port.sends.clear()
        assertTrue(commands.send("seat_heat_driver", 2.0) is CarCommands.Outcome.Sent)
        assertEquals(listOf("service call autoservice 6 i32 1001 i32 1125122068 i32 3"), port.sends)
        assertEquals(0, port.reads)

        port.sends.clear()
        assertTrue(commands.send("seat_heat_pass", 1.0) is CarCommands.Outcome.Sent)
        assertEquals(2, port.sends.size)
        assertTrue(port.sends[0].endsWith("i32 6"))
        assertTrue(port.sends[1].endsWith("i32 2"))

        port.sends.clear()
        assertTrue(commands.send("trunk", 1.0) is CarCommands.Outcome.Sent)
        assertEquals(listOf("service call autoservice 6 i32 1001 i32 1125122080 i32 1"), port.sends)
        assertEquals(0, port.reads)
        val confirmed = commands.confirm((climate as CarCommands.Outcome.Sent).spec, "ac_temp_driver", 22.0)
        assertEquals("Confirmado pelo estado relido", confirmed)
        assertEquals(1, port.reads)
    }

    @Test fun windowsAndRoofStayUnconfirmedAndSendInfoStaysClosed() {
        for (key in listOf("win_fl", "win_fr", "win_rl", "win_rr")) {
            val spec = CarCommands.CATALOG.first { it.key == key }
            assertFalse(spec.confirmed)
            assertNull(spec.dev)
            assertNull(spec.fid)
        }
        assertFalse(CarCommands.CATALOG.any { it.key == "sunroof" || it.key.contains("teto") })
        assertEquals(setOf(0, 16, 18, 35), ClusterController.SAFE_COMMANDS)
        assertFalse(ClusterController.CMD_DESTROY_DISPLAY in ClusterController.SAFE_COMMANDS)
        assertTrue(ClusterController.isKnownClusterName("fission_bg_xdjaVirtualSurface"))
        assertFalse(ClusterController.isKnownClusterName("Tela integrada"))
        try {
            Autoservice.command(6, 1000, 1)
            throw AssertionError("transação 6 não pode sair do leitor")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun clusterActivityStillDrawsCueAndDoesNotLaunchTheNavApp() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val cluster = File(root, "br/painellivre/cluster/ClusterActivity.kt").readText()
        assertTrue(cluster.contains("ClusterCue.from"))
        assertTrue(cluster.contains("renderTelemetry"))
        assertFalse(cluster.contains("launchNavigation"))
        assertFalse(cluster.contains("service call autoservice 6"))
        val runtime = File(root, "br/painellivre/car/CarCommandRuntime.kt").readText()
        assertTrue(runtime.contains("commands.send(key, value)"))
        assertTrue(runtime.contains("commands.confirm("))
        assertFalse(runtime.contains("commands.execute("))
    }
}
