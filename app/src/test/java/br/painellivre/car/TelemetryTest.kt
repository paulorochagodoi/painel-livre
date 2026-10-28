package br.painellivre.car

import br.painellivre.car.Autoservice.Reply
import br.painellivre.car.TelemetryCatalog.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Issue #16: telemetria só de leitura pelo `autoservice`. Respostas no formato do `service call` do
 * Android 10 (as do diagnóstico da issue e as publicadas por outros projetos BYD, docs/TELEMETRIA.md).
 */
class TelemetryTest {

    // Respostas gravadas.
    private val NO_DATA = "Result: Parcel(00000000 ffffd8e5   '........')"          // 1001/0x43100008 na issue
    private val ONE = "Result: Parcel(00000000 00000001   '........')"              // 1011/0x21200038 na issue (P)
    private val FFFF = "Result: Parcel(00000000 0000ffff   '........')"             // 1023/0x4290000e na issue
    private val ZERO = "Result: Parcel(00000000 00000000   '........')"
    private val SOC_43 = "Result: Parcel(00000000 422c0000   '......,B')"           // 43,0 % (float)
    private val PADDED = "Result: Parcel(    00000000 00000064   '....d...')"       // Android 14
    private val EXCEPTION = """Result: Parcel(
  0x00000000: fffffffc 0000001f 00610050 00630072 '....\...P.a.r.c.'
  0x00000010: 006c0065 00620020 00000000          'e.l. .b.....    ')"""
    private val NO_SERVICE = "Service autoservice does not exist"

    @Test fun parsesRecordedReplies() {
        assertEquals(Reply.Value(-10011), Autoservice.parse(NO_DATA))
        assertEquals(Reply.Value(1), Autoservice.parse(ONE))
        assertEquals(Reply.Value(0xFFFF), Autoservice.parse(FFFF))
        assertEquals(Reply.Value(0x64), Autoservice.parse(PADDED))
        assertEquals(Reply.Failure(0xfffffffc.toInt()), Autoservice.parse(EXCEPTION))
        assertEquals(Reply.NoService, Autoservice.parse(NO_SERVICE))
        assertTrue(Autoservice.parse("") is Reply.Garbled)
        assertTrue(Autoservice.parse(null) is Reply.Garbled)
        assertTrue(Autoservice.parse("/system/bin/sh: service: not found") is Reply.Garbled)
        assertTrue(Autoservice.parse("Result: Parcel(00000000 '....')") is Reply.Garbled)
    }

    @Test fun parcelHexIgnoresAsciiAndKeepsMultilinePayload() {
        assertTrue(Autoservice.parse("Result: Parcel(00000000 'deadbeef')") is Reply.Garbled)
        assertEquals(Reply.Value(-10011), Autoservice.parse("""
            Result: Parcel(
              0x00000000: 00000000 'deadbeef'
              0x00000004: FFFFD8E5 '........')
        """.trimIndent()))
        for (signal in TelemetryCatalog.SIGNALS) {
            val reading = TelemetrySnapshot.reading(signal, signal.fid, Autoservice.parse(NO_DATA))
            assertNull(signal.key, reading.value)
        }
    }

    @Test fun telemetryStillRejectsSettersForEveryCatalogAddressAndProtocol() {
        assertEquals(setOf(5, 7), Autoservice.READ_TRANSACTIONS)
        for (signal in TelemetryCatalog.SIGNALS) {
            for (protocol in Autoservice.Protocol.values()) {
                val fid = signal.fidFor(protocol)
                try {
                    Autoservice.command(6, signal.dev, fid)
                    fail("Setter montado para ${signal.key}")
                } catch (_: IllegalArgumentException) {}
                val setter = "service call autoservice 6 i32 ${signal.dev} i32 $fid"
                assertFalse(Autoservice.isReadOnly(setter))
                try {
                    Autoservice.batch(listOf(signal.command(fid), setter))
                    fail("Lote aceitou setter para ${signal.key}")
                } catch (_: IllegalArgumentException) {}
            }
        }
        assertFalse(Autoservice.isReadOnly("service call autoservice 5 i32 1999 i32 1"))
    }

    @Test fun sentinelsAreUnavailableNotZero() {
        for (w in listOf(-10011, -10013, 0xFFFF, 0xFFFFF)) assertNull(Autoservice.intValue(w))
        assertEquals(0, Autoservice.intValue(0))
        assertNull(Autoservice.floatValue(0xBF800000.toInt()))      // -1.0f
        assertNull(Autoservice.floatValue(0x7FC00000))              // NaN
        assertNull(Autoservice.floatValue(-10011))
        assertEquals(43.0f, Autoservice.floatValue(0x422c0000)!!, 0f)
        // Porta com 0xffff (ligação CAN ausente) é "sem dado", não "fechada".
        assertNull(TelemetrySnapshot.decode(Kind.OPEN_FLAG, 0xFFFF))
        assertNull(TelemetrySnapshot.decode(Kind.OPEN_FLAG, -10011))
        assertEquals(0.0, TelemetrySnapshot.decode(Kind.OPEN_FLAG, 0)!!, 0.0)
    }

    @Test fun plausibilityPerKind() {
        assertEquals(43.0, TelemetrySnapshot.decode(Kind.PERCENT_FLOAT, 0x422c0000)!!, 0.0)
        assertNull(TelemetrySnapshot.decode(Kind.PERCENT_FLOAT, 0x43480000))   // 200 %
        assertEquals(287.0, TelemetrySnapshot.decode(Kind.KPA, 287)!!, 0.0)
        assertNull(TelemetrySnapshot.decode(Kind.KPA, 4095))                    // marcador de faixa máxima
        assertNull(TelemetrySnapshot.decode(Kind.TEMP_SET, 255))
        assertNull(TelemetrySnapshot.decode(Kind.LOCK, 0))
        assertNull(TelemetrySnapshot.decode(Kind.OPEN_FLAG, 2))
        assertNull(TelemetrySnapshot.decode(Kind.GEAR, 0))
        assertEquals(1.0, TelemetrySnapshot.decode(Kind.GEAR, 1)!!, 0.0)
        assertEquals(13.8, TelemetrySnapshot.decode(Kind.VOLT_FLOAT, 0x415ccccd)!!, 0.01)
    }

    @Test fun onlyReadTransactionsEverLeaveTheApp() {
        assertEquals("service call autoservice 5 i32 1001 i32 692060168", Autoservice.command(5, 1001, 0x29400008))
        // fid com bit alto vai em decimal com sinal.
        assertEquals("service call autoservice 5 i32 1016 i32 -1728052956", Autoservice.command(5, 1016, 0x99000124.toInt()))
        for (tx in listOf(6, 0, 1, 2, 8, 10, 12, 40, 42)) {
            try { Autoservice.command(tx, 1001, 1); fail("transação $tx deveria ser recusada") } catch (_: IllegalArgumentException) {}
        }
        assertFalse(Autoservice.isReadOnly("service call autoservice 6 i32 1001 i32 1 i32 1"))
        assertFalse(Autoservice.isReadOnly("service call autoservice 5 i32 1001 i32 1; reboot"))
        assertFalse(Autoservice.isReadOnly("service call AutoContainer 2 i32 1000 i32 1 s16 \"\""))
        try { Autoservice.batch(listOf("service call autoservice 6 i32 1001 i32 1 i32 0")); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun catalogIsClosedReadOnlyAndConsistent() {
        val keys = TelemetryCatalog.SIGNALS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (s in TelemetryCatalog.SIGNALS) {
            assertTrue(s.key, s.tx in Autoservice.READ_TRANSACTIONS)
            assertTrue(s.key, s.kind.float == (s.tx == Autoservice.TX_GET_FLOAT))
            assertFalse(s.key, s.symbol?.endsWith("_SET") == true)
            assertTrue(s.key, Autoservice.isReadOnly(s.command()))
            s.canFid?.let { assertTrue(s.key, Autoservice.isReadOnly(s.command(it))) }
        }
        // Batch completo do debug só tem leituras.
        val batch = Autoservice.batch(TelemetryCatalog.SIGNALS.map { it.command() })
        assertFalse(batch.contains("autoservice 6 "))
        assertFalse(batch.contains("AutoContainer"))
        // A trava e a tampa do carregador ainda são hipóteses: o teste no carro decide.
        assertEquals(TelemetryCatalog.Confidence.HYPOTHESIS, TelemetryCatalog.byKey("lock").confidence)
        assertEquals(TelemetryCatalog.Confidence.HYPOTHESIS, TelemetryCatalog.byKey("charge_lid").confidence)
        assertTrue(TelemetryCatalog.POLLED.none { it.group == TelemetryCatalog.Group.PROBE })
    }

    @Test fun mappingOfKnownIds() {
        // Portas (catálogo BYD, Bodywork): ordem e endereços.
        assertEquals(0x29400008, TelemetryCatalog.byKey("door_fl").fid)
        assertEquals(0x2940000A, TelemetryCatalog.byKey("door_fr").fid)
        assertEquals(0x2940000C, TelemetryCatalog.byKey("door_rl").fid)
        assertEquals(0x2940000E, TelemetryCatalog.byKey("door_rr").fid)
        // O SealNav lê a bateria com a transação 7 neste endereço (1246777400).
        val soc = TelemetryCatalog.byKey("soc")
        assertEquals("service call autoservice 7 i32 1014 i32 1246777400", soc.command())
        // Gear lido na issue: 1011/0x21200038.
        assertEquals("service call autoservice 5 i32 1011 i32 555745336", TelemetryCatalog.byKey("gear").command())
    }

    @Test fun protocolAndNameResolution() {
        val soc = TelemetryCatalog.byKey("soc")
        assertEquals(0x4A505038, TelemetryCatalog.resolveFid(soc, Autoservice.Protocol.CANFD, null))
        assertEquals(0x4A505038, TelemetryCatalog.resolveFid(soc, Autoservice.Protocol.UNKNOWN, null))
        assertEquals(1033543720, TelemetryCatalog.resolveFid(soc, Autoservice.Protocol.CAN, null))
        // O nome no catálogo da central vence o padrão; zero não é id.
        assertEquals(0x12345678, TelemetryCatalog.resolveFid(soc, Autoservice.Protocol.CAN, 0x12345678))
        assertEquals(1033543720, TelemetryCatalog.resolveFid(soc, Autoservice.Protocol.CAN, 0))
    }

    @Test fun environmentRequiresTheKnownInterface() {
        val car = Autoservice.Environment.parse("57\tautoservice: [android.gui.BYDAutoServer]\n@@PLPROTO\nCANFD\n\n")
        assertTrue(car.descriptorOk)
        assertEquals(Autoservice.Protocol.CANFD, car.protocol)
        val di5 = Autoservice.Environment.parse("57\tautoservice: [android.gui.BYDAutoServer]\n@@PLPROTO\n\nCAN\n")
        assertEquals(Autoservice.Protocol.CAN, di5.protocol)
        val other = Autoservice.Environment.parse("57\tautoservice: [com.example.IOther]\n@@PLPROTO\n")
        assertTrue(other.hasService)
        assertFalse(other.descriptorOk)
        val emulator = Autoservice.Environment.parse("@@PLPROTO\n\n")
        assertFalse(emulator.hasService)
        assertEquals(Autoservice.Protocol.UNKNOWN, emulator.protocol)
        // "xautoservice" não é o serviço.
        assertFalse(Autoservice.Environment.parse("3\tmyautoservice: [android.gui.BYDAutoServer]\n@@PLPROTO\n").hasService)
    }

    @Test fun batchMarkersKeepAnswersAligned() {
        val cmds = listOf(Autoservice.command(5, 1001, 1), Autoservice.command(5, 1001, 2), Autoservice.command(7, 1014, 3))
        val b = Autoservice.batch(cmds)
        assertTrue(b.startsWith("echo @@PL0; service call autoservice 5"))
        assertTrue(b.endsWith("echo @@PLFIM"))
        // A segunda leitura não imprimiu nada: a terceira continua na terceira posição.
        val out = "@@PL0\n$ONE\n@@PL1\n@@PL2\n$SOC_43\n@@PLFIM\n"
        val parts = Autoservice.splitBatch(out, 3)
        assertEquals(Reply.Value(1), Autoservice.parse(parts[0]))
        assertTrue(Autoservice.parse(parts[1]) is Reply.Garbled)
        assertEquals(Reply.Value(0x422c0000), Autoservice.parse(parts[2]))
        // Saída cortada: o que faltou é null.
        val cut = Autoservice.splitBatch("@@PL0\n$ONE\n", 3)
        assertNull(cut[1]); assertNull(cut[2])
        // Exceção em várias linhas fica inteira no seu pedaço.
        val multi = Autoservice.splitBatch("@@PL0\n$EXCEPTION\n@@PL1\n$ZERO\n@@PLFIM", 2)
        assertEquals(Reply.Failure(0xfffffffc.toInt()), Autoservice.parse(multi[0]))
        assertEquals(Reply.Value(0), Autoservice.parse(multi[1]))
    }

    /** Varredura gravada: carro parado, porta do motorista aberta, trancado? não; carregando AC. */
    private fun snapshot(vararg replies: Pair<String, String>): TelemetrySnapshot {
        val map = replies.associate { (key, text) ->
            val s = TelemetryCatalog.byKey(key)
            key to TelemetrySnapshot.reading(s, s.fid, Autoservice.parse(text))
        }
        return TelemetrySnapshot(TelemetrySnapshot.Status.OK, readings = map)
    }

    private fun word(v: Int) = "Result: Parcel(00000000 %08x   '........')".format(v)
    private fun float(f: Float) = word(java.lang.Float.floatToIntBits(f))

    @Test fun statesForAnimations() {
        val s = snapshot(
            "door_fl" to ONE, "door_fr" to ZERO, "door_rl" to ZERO, "door_rr" to NO_DATA, "hood" to ZERO, "trunk" to ZERO,
            "lock" to word(1), "charge_lid" to ONE, "gun" to word(2), "bms" to word(1),
            "charge_kw" to float(6.6f), "charge_h" to word(1), "charge_min" to word(20), "soc" to SOC_43,
        )
        assertEquals(true, s.isOpen("door_fl"))
        assertEquals(false, s.isOpen("door_fr"))
        assertNull(s.isOpen("door_rr"))           // sem dado não é "fechada"
        assertEquals(listOf("Porta dianteira esquerda"), s.openings)
        assertEquals(false, s.locked)
        assertEquals(true, s.chargeLidOpen)
        assertEquals(true, s.plugged)
        assertEquals(true, s.charging)
        assertEquals(80, s.chargeRemainingMin)
        assertEquals(43.0, s.socPercent!!, 0.0)
        assertTrue(s.notable)
        assertEquals("Porta dianteira esquerda aberta", TelemetryText.panel(s))
        assertEquals("Carregando · 6,6 kW · 1 h 20 min", TelemetryText.charge(s))
    }

    @Test fun closedAndLockedCarIsQuiet() {
        val s = snapshot(
            "door_fl" to ZERO, "door_fr" to ZERO, "door_rl" to ZERO, "door_rr" to ZERO, "hood" to ZERO, "trunk" to ZERO,
            "lock" to word(2), "charge_lid" to ZERO, "gun" to word(1), "bms" to word(2),
        )
        assertEquals(true, s.locked)
        assertEquals(false, s.charging)
        assertEquals(false, s.plugged)
        assertFalse(s.notable)
        assertNull(TelemetryText.panel(s))
        assertEquals("Tudo fechado e trancado", TelemetryText.headline(s))
    }

    @Test fun partialReadingsNeverClaimEverythingIsClosed() {
        assertEquals("Trancado", TelemetryText.headline(snapshot("lock" to word(2), "door_fl" to ZERO)))
        assertEquals("Carro", TelemetryText.headline(snapshot("door_fl" to ZERO, "door_rr" to NO_DATA)))
        assertEquals("Destrancado", TelemetryText.headline(snapshot("lock" to word(1), "door_fl" to ZERO)))
    }

    @Test fun hoodAndTrunkTextAndSeveralOpenings() {
        assertEquals("Capô aberto", TelemetryText.panel(snapshot("hood" to ONE)))
        assertEquals("Porta-malas aberto", TelemetryText.panel(snapshot("trunk" to ONE)))
        assertEquals("2 aberturas: porta traseira direita, porta-malas", TelemetryText.panel(snapshot("door_rr" to ONE, "trunk" to ONE)))
        assertEquals("Tampa do carregador aberta", TelemetryText.panel(snapshot("charge_lid" to ONE, "bms" to word(2))))
    }

    @Test fun chargingFallsBackToPowerWithoutBms() {
        val s = snapshot("gun" to word(2), "charge_kw" to float(3.2f), "bms" to NO_DATA)
        assertEquals(true, s.charging)
        assertEquals("Carregando · 3,2 kW", TelemetryText.charge(s))
        assertEquals("Cabo conectado, sem carregar", TelemetryText.charge(snapshot("gun" to word(3), "bms" to word(2))))
        assertNull(TelemetryText.charge(snapshot("gun" to word(1))))
        assertNull(snapshot().charging)
    }

    @Test fun detailLinesOnlyWithData() {
        val s = snapshot(
            "ac_temp_driver" to word(22), "ac_temp_pass" to word(24), "temp_out" to word(31),
            "seat_heat_driver" to word(1), "seat_vent_driver" to word(3),
            "tyre_p_fl" to word(287), "tyre_p_fr" to word(284), "tyre_p_rl" to NO_DATA, "tyre_p_rr" to word(290),
            "win_fl" to word(0), "win_fr" to word(40),
        )
        val lines = TelemetryText.details(s)
        assertEquals(listOf(
            "Clima: 22° / 24° · fora 31°",
            "Bancos: vent. motorista 2",
            "Pneus (bar): 2,9 2,8 — 2,9",
            "Abertos: vidro dianteiro direito 40 %",
        ), lines)
        assertTrue(TelemetryText.details(snapshot("tyre_p_fl" to NO_DATA)).isEmpty())
        assertTrue(TelemetryText.details(snapshot("win_fl" to ZERO)).isEmpty())
        assertEquals(listOf("Vidros fechados"), TelemetryText.details(snapshot(
            "win_fl" to ZERO, "win_fr" to ZERO, "win_rl" to ZERO, "win_rr" to ZERO)))
    }

    @Test fun debugLineShowsAddressRawAndConfidence() {
        val s = TelemetryCatalog.byKey("probe_roof_b")
        val line = TelemetrySnapshot.reading(s, s.fid, Autoservice.parse(NO_DATA)).debugLine()
        assertEquals("probe_roof_b [1001/0x43100008 tx5] Teto (alternativo): 0xffffd8e5 (-10011) → indisponível (hipótese)", line)
        val gear = TelemetryCatalog.byKey("gear")
        assertTrue(TelemetrySnapshot.reading(gear, gear.fid, Autoservice.parse(ONE)).debugLine().endsWith("→ P (confirmado no carro)"))
    }
}
