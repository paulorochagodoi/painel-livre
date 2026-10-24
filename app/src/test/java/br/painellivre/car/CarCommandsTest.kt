package br.painellivre.car

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CarCommandsTest {
    private class Fake : CarCommands.Port {
        var enabled = true
        var allowed = true
        var environment = true
        var clock = 10000L
        var gear = CarCommands.Gear(1, true, true)
        var actual: Double? = 22.0
        var sends = mutableListOf<String>()
        var logs = mutableListOf<String>()
        var sent = true
        var withdrawOnEnvironment = false
        var reads = 0
        override fun enabled() = enabled
        override fun allowed(key: String) = allowed
        override fun now() = clock
        override suspend fun gear() = gear
        override suspend fun environmentOk(): Boolean { if (withdrawOnEnvironment) enabled = false; return environment }
        override suspend fun sendOnce(command: String): Boolean { sends += command; return sent }
        override suspend fun read(key: String): Double? { reads++; return actual }
        override suspend fun media(key: String, value: Double) = "Android relido"
        override fun log(at: Long, key: String, value: Double, result: String) { logs += "$at $key $value $result" }
    }
    @Test fun disabledAndIndividualOffNeverSendAndLog() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        p.enabled = false
        assertTrue(c.execute("ac_temp_driver", 22.0).contains("desligados"))
        p.enabled = true; p.allowed = false
        assertTrue(c.execute("ac_temp_driver", 22.0).contains("Diagnóstico"))
        assertTrue(p.sends.isEmpty()); assertEquals(2, p.logs.size)
        assertTrue(p.logs.first().contains("10000 ac_temp_driver 22.0"))
    }
    @Test fun limitsRejectNanInfinityFractionsAndUnknown() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        for (v in listOf(15.5, 30.5, 22.5, Double.NaN, Double.POSITIVE_INFINITY)) c.execute("ac_temp_driver", v)
        for (v in listOf(-1.0, 8.0, 1.5)) c.execute("ac_fan", v)
        c.execute("arbitrary; reboot", 1.0)
        assertTrue(p.sends.isEmpty()); assertEquals(9, p.logs.size)
        val temp = CarCommands.CATALOG.first { it.key == "ac_temp_driver" }
        assertTrue(CarCommands.valid(temp, 16.0)); assertTrue(CarCommands.valid(temp, 30.0))
    }
    @Test fun sendsOnlyWhitelistedAddressReadsBackAndRateLimits() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        assertEquals("Confirmado pelo estado relido", c.execute("ac_temp_driver", 22.0))
        assertEquals(listOf("service call autoservice 6 i32 1000 i32 501219368 i32 22"), p.sends)
        assertEquals(1, p.reads)
        assertTrue(c.execute("ac_temp_pass", 23.0).contains("aguarde"))
        p.clock += 1000; p.actual = null
        assertTrue(c.execute("ac_temp_pass", 23.0).contains("Não confirmado"))
        assertEquals(2, p.sends.size)
    }
    @Test fun rejectsUnknownEnvironmentAndHypotheses() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        p.environment = false; c.execute("ac_temp_driver", 22.0)
        p.environment = true; c.execute("seat_heat_driver", 2.0)
        assertTrue(p.sends.isEmpty())
    }
    @Test fun movementNeedsFreshRealParkAndStillBlocksUnprovenSetter() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        for (g in listOf(CarCommands.Gear(null,true,true), CarCommands.Gear(2,true,true),
            CarCommands.Gear(3,true,true), CarCommands.Gear(4,true,true),
            CarCommands.Gear(1,false,true), CarCommands.Gear(1,true,false))) {
            p.gear = g
            assertFalse(CarCommands.parked(g))
            assertTrue(c.execute("trunk", 1.0).contains("P não confirmado"))
        }
        p.gear = CarCommands.Gear(1,true,true)
        assertTrue(CarCommands.parked(p.gear))
        assertTrue(c.execute("trunk", 1.0).contains("ainda não confirmado"))
        assertTrue(p.sends.isEmpty())
    }
    @Test fun ambiguousSendIsNotRetriedAndReadbackStillRuns() = runBlocking {
        val p = Fake(); p.sent = false
        CarCommands(p).execute("ac_temp_driver",22.0)
        assertEquals(1,p.sends.size); assertEquals(1,p.reads)
        assertTrue(p.logs.any { it.contains("inconclusiva") })
    }
    @Test fun autoEncodingAndFeedbackUseWireState() = runBlocking {
        val p = Fake(); p.actual = 2.0
        assertTrue(CarCommands(p).execute("ac_auto",1.0).startsWith("Não confirmado"))
        assertTrue(p.sends.single().endsWith("501219352 i32 2"))
    }
    @Test fun withdrawnPermissionWhileReadingEnvironmentBlocksSend() = runBlocking {
        val p = Fake(); p.withdrawOnEnvironment = true
        assertTrue(CarCommands(p).execute("ac_temp_driver",22.0).contains("retirada"))
        assertTrue(p.sends.isEmpty())
    }
    @Test fun mediaAlsoNeedsBothSwitchesAndBounds() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        p.enabled = false
        assertTrue(c.execute("media_play",1.0).contains("desligados"))
        p.enabled = true; p.allowed = false
        assertTrue(c.execute("media_play",1.0).contains("Diagnóstico"))
        p.allowed = true
        assertTrue(c.execute("media_volume",2.0).contains("limites"))
        assertEquals("Android relido",c.execute("media_play",1.0))
        assertTrue(p.sends.isEmpty())
    }
    @Test fun toqueDiretoPassaPeloRuntimeSemConfirmacaoExtra() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val ui = File(root, "br/painellivre/ui/LauncherActivity.kt").readText()
        assertFalse(ui.contains("Enviar ao carro?"))
        assertTrue(ui.contains("command(key, values[index])"))
        assertTrue(ui.contains("CarCommandRuntime.execute"))
        val runtime = File(root, "br/painellivre/car/CarCommandRuntime.kt").readText()
        assertTrue(runtime.contains("commands.execute(key, value)"))
        assertTrue(runtime.contains("AppLog.i(\"Comando"))
    }
    @Test fun todasAsHipotesesContinuamBloqueadasMesmoComChavesLigadas() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        for (spec in CarCommands.CATALOG.filter { !it.confirmed }) {
            assertTrue(c.execute(spec.key, spec.min).contains("ainda não confirmado"))
        }
        assertTrue(p.sends.isEmpty())
        assertEquals(CarCommands.CATALOG.count { !it.confirmed }, p.logs.size)
    }
    @Test fun transactionSixExistsOnlyAtCommandBoundaryAndReadBatchStillRejectsIt() {
        val root = listOf(File("src/main/java"),File("app/src/main/java")).first { it.exists() }
        val sources = root.walkTopDown().filter { it.extension == "kt" && it.readText().contains("service call autoservice 6 ") }.toList()
        assertEquals(listOf("CarCommands.kt"),sources.map { it.name })
        assertFalse(Autoservice.isReadOnly("service call autoservice 6 i32 1000 i32 501219368 i32 22"))
    }
}
