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
    @Test fun sendsOnlyWhitelistedAddressReadsBackWithoutRateLimit() = runBlocking {
        // 0.5.7: sem limite de 1 envio/s — dois sends seguidos devem ir (confirm separado).
        val p = Fake(); val c = CarCommands(p)
        assertEquals("Confirmado pelo estado relido", c.execute("ac_temp_driver", 22.0))
        assertEquals(listOf("service call autoservice 6 i32 1000 i32 501219368 i32 22"), p.sends)
        assertEquals(1, p.reads)
        p.actual = null
        assertTrue(c.execute("ac_temp_pass", 23.0).contains("Não confirmado"))
        assertEquals(2, p.sends.size)
        assertFalse(p.logs.any { it.contains("aguarde") })
    }
    @Test fun rejectsUnknownEnvironmentAndHypotheses() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        p.environment = false; c.execute("ac_temp_driver", 22.0)
        p.environment = true; c.execute("win_fl", 10.0) // vidros: ainda sem escrita confirmada
        assertTrue(p.sends.isEmpty())
    }
    @Test fun movementNeedsFreshRealParkThenTrunkOpens() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        for (g in listOf(CarCommands.Gear(null,true,true), CarCommands.Gear(2,true,true),
            CarCommands.Gear(3,true,true), CarCommands.Gear(4,true,true),
            CarCommands.Gear(1,false,true), CarCommands.Gear(1,true,false))) {
            p.gear = g
            assertFalse(CarCommands.parked(g))
            assertTrue(c.execute("trunk", 1.0).contains("P não confirmado"))
        }
        assertTrue(p.sends.isEmpty())
        p.gear = CarCommands.Gear(1,true,true); p.actual = 1.0
        assertTrue(CarCommands.parked(p.gear))
        assertEquals("Confirmado pelo estado relido", c.execute("trunk", 1.0))
        assertEquals(listOf("service call autoservice 6 i32 1001 i32 1125122080 i32 1"), p.sends)
    }
    @Test fun ambiguousSendIsNotRetriedAndSkipsConfirm() = runBlocking {
        // 0.5.7: se o send falhou, não há confirm/readback — não inventar segundo envio.
        val p = Fake(); p.sent = false
        val msg = CarCommands(p).execute("ac_temp_driver",22.0)
        assertEquals(1,p.sends.size); assertEquals(0,p.reads)
        assertTrue(msg.contains("inconclusiva"))
    }
    @Test fun autoEncodingSendsWireValueAndDecodesReadbackBeforeConfirming() = runBlocking {
        // Bug visto no carro em 29/10: o readback é o valor cru do fio (1=desliga, 2=liga para AUTO);
        // decodificar antes de comparar ao alvo lógico, senão nunca confirma.
        val p = Fake(); p.actual = 2.0
        assertEquals("Confirmado pelo estado relido", CarCommands(p).execute("ac_auto",1.0))
        assertTrue(p.sends.single().endsWith("501219352 i32 2"))
        val p2 = Fake(); p2.actual = 1.0
        assertTrue(CarCommands(p2).execute("ac_auto",1.0).startsWith("Não confirmado"))
    }
    @Test fun recircReadbackNowConfirmsLikeSeenOnTheCar() = runBlocking {
        val p = Fake(); p.actual = 1.0
        assertEquals("Confirmado pelo estado relido", CarCommands(p).execute("ac_recirc",1.0))
        p.clock += 1000; p.actual = 0.0
        assertEquals("Confirmado pelo estado relido", CarCommands(p).execute("ac_recirc",0.0))
    }
    @Test fun toWireAndFromWireAreInversesAndIdentityWithoutOnWire() {
        val auto = CarCommands.CATALOG.first { it.key == "ac_auto" }
        assertEquals(1, CarCommands.toWire(auto, 0.0)); assertEquals(2, CarCommands.toWire(auto, 1.0))
        assertEquals(0.0, CarCommands.fromWire(auto, 1.0), 0.0); assertEquals(1.0, CarCommands.fromWire(auto, 2.0), 0.0)
        val recirc = CarCommands.CATALOG.first { it.key == "ac_recirc" }
        assertEquals(1, CarCommands.toWire(recirc, 1.0)); assertEquals(1.0, CarCommands.fromWire(recirc, 1.0), 0.0)
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
        assertTrue(ui.contains("CarCommandRuntime.execute"))
        val runtime = File(root, "br/painellivre/car/CarCommandRuntime.kt").readText()
        assertTrue(runtime.contains("commands.send(key, value)"))
        assertTrue(runtime.contains("commands.confirm("))
        assertTrue(runtime.contains("AppLog.i(\"Comando"))
    }
    @Test fun toggleAndFanStepArePureAndClamped() {
        assertEquals(1.0, CarCommands.toggleValue(null), 0.0)
        assertEquals(1.0, CarCommands.toggleValue(false), 0.0)
        assertEquals(0.0, CarCommands.toggleValue(true), 0.0)
        assertEquals(1.0, CarCommands.fanStep(null, up = true), 0.0)
        assertEquals(1.0, CarCommands.fanStep(0, up = true), 0.0)
        assertEquals(3.0, CarCommands.fanStep(4, up = false), 0.0)
        assertEquals(7.0, CarCommands.fanStep(7, up = true), 0.0) // não passa do máximo
        assertEquals(0.0, CarCommands.fanStep(0, up = false), 0.0) // não passa do mínimo
    }
    @Test fun stepValueUsaOsLimitesEOPassoDoProprioSpec() {
        // Achado extra do teste no carro de 29/10: temperatura e bancos também viram toque direto.
        val temp = CarCommands.CATALOG.first { it.key == "ac_temp_driver" }
        assertEquals(23.0, CarCommands.stepValue(temp, 22.0, up = true), 0.0)
        assertEquals(21.0, CarCommands.stepValue(temp, 22.0, up = false), 0.0)
        assertEquals(30.0, CarCommands.stepValue(temp, 30.0, up = true), 0.0) // não passa do máximo
        assertEquals(16.0, CarCommands.stepValue(temp, 16.0, up = false), 0.0) // não passa do mínimo
        assertEquals(16.0, CarCommands.stepValue(temp, null, up = false), 0.0) // sem leitura: parte do mínimo
        val seat = CarCommands.CATALOG.first { it.key == "seat_heat_driver" }
        assertEquals(1.0, CarCommands.stepValue(seat, 0.0, up = true), 0.0)
        assertEquals(2.0, CarCommands.stepValue(seat, 2.0, up = true), 0.0) // max=2 níveis no carro
    }
    @Test fun modosDeArEVentilacaoViramToqueUnicoSemDialogoDeEscolha() {
        // issue #20: AUTO/A-C/recirculação/desembaçador/energia viram toggle; ventilação vira +/-.
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val ui = File(root, "br/painellivre/ui/LauncherActivity.kt").readText()
        assertTrue(ui.contains("mapOf(R.id.modeAuto to \"ac_auto\", R.id.modeAc to \"ac_compressor\", R.id.modeRecirc to \"ac_recirc\", R.id.modeDefrost to \"ac_defrost\")"))
        assertTrue(ui.contains("setOnClickListener { toggleClimate(key) } }"))
        assertTrue(ui.contains("R.id.fanDown).setOnClickListener { stepFan(up = false) }"))
        assertTrue(ui.contains("R.id.fanUp).setOnClickListener { stepFan(up = true) }"))
        assertTrue(ui.contains("R.id.popClimateState).setOnClickListener { toggleClimate(\"ac_on\") }"))
    }
    @Test fun temperaturaEBancosTambemViramToqueDiretoSemListaNemDialogo() {
        // Achado extra do teste no carro de 29/10: a lista 16..30° e o diálogo dos bancos ainda abriam.
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val ui = File(root, "br/painellivre/ui/LauncherActivity.kt").readText()
        assertTrue(ui.contains("card.findViewById<View>(R.id.tempDown).setOnClickListener { stepTemp(key, up = false) }"))
        assertTrue(ui.contains("card.findViewById<View>(R.id.tempUp).setOnClickListener { stepTemp(key, up = true) }"))
        assertTrue(ui.contains("setOnClickListener { toggleSeat(\"seat_heat_\$side\") }"))
        assertTrue(ui.contains("setOnClickListener { toggleSeat(\"seat_vent_\$side\") }"))
        assertTrue(ui.contains("if (chip.id == \"trunk\") v.setOnClickListener { toggleTrunk() }"))
        // Os atalhos do dock de temperatura só mostram leitura; tocar abre a Climatização (sem lista),
        // por cima da tela atual e com a seta no próprio botão (0.5.9).
        assertTrue(ui.contains("click(R.id.dockTempDriver) { togglePopover(PopoverState.Id.CLIMATE, CentralScreen.Anchor.DOCK_TEMP_DRIVER) }"))
        assertTrue(ui.contains("click(R.id.dockTempPassenger) { togglePopover(PopoverState.Id.CLIMATE, CentralScreen.Anchor.DOCK_TEMP_PASSENGER) }"))
    }
    @Test fun nenhumDialogoOuListaDeEscolhaSobrouParaComandosDoCarro() {
        // Extra 3 do teste no carro de 29/10: varredura total. Os únicos AlertDialog que sobram no
        // arquivo são de navegação/diagnóstico (route_end_question, setup_title), não de comando.
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val ui = File(root, "br/painellivre/ui/LauncherActivity.kt").readText()
        assertFalse(ui.contains("chooseCommand"))
        assertFalse(ui.contains(".setItems("))
        assertFalse(ui.contains("Comando ainda não confirmado; controle bloqueado"))
    }
    @Test fun toquesRapidosNuncaMostramRejeicaoNemDebounceAntesDoEnvio() {
        // 0.5.7: envio na hora; fila só se send in-flight; sem delay(350); UI otimista.
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val ui = File(root, "br/painellivre/ui/LauncherActivity.kt").readText()
        val runtime = File(root, "br/painellivre/car/CarCommandRuntime.kt").readText()
        assertFalse(ui.contains("Aguarde o comando anterior"))
        assertFalse(ui.contains("rejeitado, outro comando em andamento"))
        assertFalse(ui.contains("delay(350)"))
        assertFalse(ui.contains("pendingStepTarget"))
        assertTrue(ui.contains("private var pendingCommand: Pair<String, Double>? = null"))
        assertTrue(ui.contains("optimisticClimate"))
        assertTrue(runtime.contains("commands.send(key, value)"))
        assertTrue(runtime.contains("commands.confirm("))
        assertFalse(File(root, "br/painellivre/car/CarCommands.kt").readText().contains("aguarde um segundo"))
    }

    @Test fun seatOnWireMapsLogicalToWireAndPassengerHeatPrepsSix() = runBlocking {
        // Confirmado no carro 29/10 ~16:00: fio 1=off, 2=nível1, 3=nível2; pass heat manda 6 antes.
        val heat = CarCommands.CATALOG.first { it.key == "seat_heat_driver" }
        assertEquals(1, CarCommands.toWire(heat, 0.0))
        assertEquals(2, CarCommands.toWire(heat, 1.0))
        assertEquals(3, CarCommands.toWire(heat, 2.0))
        assertEquals(0.0, CarCommands.fromWire(heat, 1.0), 0.0)
        assertEquals(1.0, CarCommands.fromWire(heat, 2.0), 0.0)
        assertEquals(2.0, CarCommands.fromWire(heat, 3.0), 0.0)
        assertEquals(2.0, heat.max, 0.0)
        assertTrue(heat.confirmed && heat.readbackKnown)
        val p = Fake(); p.actual = 2.0
        val c = CarCommands(p)
        assertEquals("Confirmado pelo estado relido", c.execute("seat_heat_driver", 1.0))
        assertEquals(listOf("service call autoservice 6 i32 1001 i32 1125122068 i32 2"), p.sends)
        p.sends.clear(); p.actual = 2.0
        assertEquals("Confirmado pelo estado relido", c.execute("seat_heat_pass", 1.0))
        assertEquals(
            listOf(
                "service call autoservice 6 i32 1001 i32 1125122076 i32 6",
                "service call autoservice 6 i32 1001 i32 1125122076 i32 2",
            ),
            p.sends,
        )
    }

    @Test fun trunkOpenOnlyInParkSendsOneAndWindowsStayBlocked() = runBlocking {
        val p = Fake(); val c = CarCommands(p)
        p.gear = CarCommands.Gear(4, true, true) // D
        assertTrue(c.execute("trunk", 1.0).contains("câmbio P"))
        assertTrue(p.sends.isEmpty())
        p.gear = CarCommands.Gear(1, true, true); p.actual = 1.0
        assertEquals("Confirmado pelo estado relido", c.execute("trunk", 1.0))
        assertEquals(listOf("service call autoservice 6 i32 1001 i32 1125122080 i32 1"), p.sends)
        p.sends.clear()
        assertTrue(c.execute("trunk", 0.0).contains("fora dos limites")) // fechar sem contrato
        assertTrue(c.execute("win_fl", 10.0).contains("ainda não confirmado"))
        assertTrue(p.sends.isEmpty())
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
