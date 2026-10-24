package br.painellivre

import br.painellivre.adb.SelfAdb
import br.painellivre.car.InstrumentSdk
import br.painellivre.cluster.PanelMenu
import br.painellivre.cluster.PanelMenu.Mode
import br.painellivre.cluster.PanelMenuReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #9 no carro: leitura de INSTRUMENT_NAVI_TYPE pelo ADB local quando o SDK recusa. */
class PanelMenuReaderTest {

    private fun parcel(value: String) = "Result: Parcel(00000000 $value   '........')"

    @Test fun parsesTheParcelSeenInTheCar() {
        for ((word, mode) in listOf("00000001" to Mode.OFF, "00000002" to Mode.SIMPLE, "00000003" to Mode.SMALL, "00000004" to Mode.FULL)) {
            val p = PanelMenu.parseParcel(parcel(word))
            assertNull(p.error)
            assertEquals(mode, PanelMenu.modeOf(p.raw))
        }
    }

    @Test fun parsesMultiLineParcelWithOffsets() {
        val out = "Result: Parcel(\n  0x00000000: 00000000 00000003 00000000          '............')\n"
        assertEquals(PanelMenu.ParcelValue(3, null), PanelMenu.parseParcel(out))
    }

    @Test fun asciiPreviewIsIgnored() {
        // A prévia entre aspas nunca vira valor, mesmo parecendo hexadecimal.
        assertEquals(PanelMenu.ParcelValue(2, null), PanelMenu.parseParcel("Result: Parcel(00000000 00000002 'deadbeef')"))
    }

    @Test fun unavailableValueIsAnErrorAndKeepsTheRaw() {
        val p = PanelMenu.parseParcel(parcel("ffffd8e5"))
        assertEquals(-10011, p.raw)
        assertEquals(PanelMenu.UNAVAILABLE, p.raw)
        assertTrue(p.error!!.contains("0xffffd8e5"))
        val r = InstrumentSdk.Reading(p.raw, p.error, "", source = "ADB")
        assertEquals(Mode.UNKNOWN, r.mode)
    }

    @Test fun badAnswersAreErrors() {
        for (bad in listOf(
            "", "service: command not found", "Service autoservice does not exist",
            "Result: Parcel(00000000 00000001", // truncado
            "Result: Parcel(00000000)\n00000001", // valor fora do Parcel
            "Result: Parcel(lixo 00000000 00000001)",
            "Result: Parcel()", "Result: Parcel(00000000)",
            "Result: Parcel(ffffffff 00000004 '....')", // exceção do Binder: não usa o valor
            "Result: Parcel(fffffffc 0000001c 00740053 '....S.t.')",
        )) {
            val p = PanelMenu.parseParcel(bad)
            assertTrue("[$bad]", p.error != null)
            assertEquals("[$bad]", Mode.UNKNOWN, InstrumentSdk.Reading(p.raw, p.error, "").mode)
        }
    }

    @Test fun adbCommandIsTheReadOnlyOneConfirmedInTheCar() {
        assertEquals("service call autoservice 5 i32 1007 i32 1086337074", PanelMenu.ADB_READ_COMMAND)
        assertEquals(5, PanelMenu.GET_TRANSACTION)
        assertFalse(PanelMenu.ADB_READ_COMMAND.contains(" 6 "))
        assertFalse(PanelMenu.ADB_READ_COMMAND.contains("SET", ignoreCase = true))
        assertEquals(29, PanelMenu.ADB_SDK_INT)
    }

    private val denied = InstrumentSdk.Reading(null, "permissão BYD recusada", "", denied = true)

    private class FakeAdb(var answer: SelfAdb.Result) {
        val commands = mutableListOf<String>()
        suspend fun run(cmd: String): SelfAdb.Result { commands += cmd; return answer }
    }

    @Test fun sdkFirstWithoutAdbWhenItWorks() = runTest {
        val adb = FakeAdb(SelfAdb.Result.Ok(parcel("00000001"), 0))
        val reader = PanelMenuReader({ InstrumentSdk.Reading(4, null, "") }, adb::run, adbAllowed = true)
        val r = reader.read()
        assertEquals(Mode.FULL, r.mode)
        assertEquals("SDK", r.source)
        assertTrue(adb.commands.isEmpty())
    }

    @Test fun fallsBackToAdbOnSecurityExceptionAndStopsAskingTheSdk() = runTest {
        var sdkCalls = 0
        val adb = FakeAdb(SelfAdb.Result.Ok(parcel("00000003"), 0))
        val reader = PanelMenuReader({ sdkCalls++; denied }, adb::run, adbAllowed = true)
        assertEquals(Mode.SMALL, reader.read().mode)
        adb.answer = SelfAdb.Result.Ok(parcel("00000001"), 0)
        val r = reader.read()
        assertEquals(Mode.OFF, r.mode)
        assertEquals("ADB", r.source)
        assertEquals(1, sdkCalls)
        assertEquals(listOf(PanelMenu.ADB_READ_COMMAND, PanelMenu.ADB_READ_COMMAND), adb.commands)
    }

    @Test fun otherSdkFailuresDoNotUseAdb() = runTest {
        // Sem SDK (emulador, outra central): não é BYD com permissão recusada, então nada de ADB.
        val adb = FakeAdb(SelfAdb.Result.Ok(parcel("00000002"), 0))
        val reader = PanelMenuReader({ InstrumentSdk.Reading(null, "SDK BYD não encontrado", "") }, adb::run, adbAllowed = true)
        assertEquals(Mode.UNKNOWN, reader.read().mode)
        assertTrue(adb.commands.isEmpty())
    }

    @Test fun adbOnlyOnTheConfirmedAndroid() = runTest {
        val adb = FakeAdb(SelfAdb.Result.Ok(parcel("00000002"), 0))
        val reader = PanelMenuReader({ denied }, adb::run, adbAllowed = false)
        val r = reader.read()
        assertEquals(Mode.UNKNOWN, r.mode)
        assertTrue(r.denied)
        assertTrue(adb.commands.isEmpty())
    }

    @Test fun adbFailureBacksOffAndRecovers() = runTest {
        var now = 0L
        val adb = FakeAdb(SelfAdb.Result.Error("porta fechada", SelfAdb.Kind.PORT_CLOSED))
        val reader = PanelMenuReader({ denied }, adb::run, adbAllowed = true, clock = { now }, retryMs = 5_000)
        val first = reader.read()
        assertEquals(Mode.UNKNOWN, first.mode)
        assertTrue(first.error!!.contains("ADB: porta fechada"))
        assertTrue(first.error!!.contains("SDK:"))
        now = 1_000
        reader.read()
        assertEquals(1, adb.commands.size) // não insiste a cada segundo
        adb.answer = SelfAdb.Result.Ok(parcel("00000002"), 0)
        now = 5_000
        assertEquals(Mode.SIMPLE, reader.read().mode)
        assertEquals(2, adb.commands.size)
    }

    @Test fun shellAndParcelFailuresBackOffAndUnknownValuesNeverSelectAMode() = runTest {
        for (answer in listOf(
            SelfAdb.Result.Ok(parcel("00000001"), 1),
            SelfAdb.Result.Ok("Service autoservice does not exist", 0),
            SelfAdb.Result.Ok("Result: Parcel(00000000 00000001", 0),
        )) {
            var now = 0L
            val adb = FakeAdb(answer)
            val reader = PanelMenuReader({ denied }, adb::run, true, clock = { now })
            assertEquals(Mode.UNKNOWN, reader.read().mode)
            now = 1000
            reader.read()
            assertEquals(1, adb.commands.size)
            now = 5000
            adb.answer = SelfAdb.Result.Ok(parcel("00000004"), 0)
            assertEquals(Mode.FULL, reader.read().mode)
        }
        for (word in listOf("00000000", "00000005", "ffffffff", "7fffffff")) {
            val adb = FakeAdb(SelfAdb.Result.Ok(parcel(word), 0))
            assertEquals(Mode.UNKNOWN, PanelMenuReader({ denied }, adb::run, true).read().mode)
        }
    }

    @Test fun followerReactsToAdbReadings() = runTest {
        // Leitura a cada 1 s: a troca no menu vale na segunda leitura igual.
        val adb = FakeAdb(SelfAdb.Result.Ok(parcel("00000004"), 0))
        val reader = PanelMenuReader({ denied }, adb::run, adbAllowed = true)
        val f = PanelMenu.Follower()
        repeat(2) { f.update(reader.read().mode) }
        assertEquals(Mode.FULL, f.stable)
        adb.answer = SelfAdb.Result.Ok(parcel("00000001"), 0)
        assertEquals(Mode.FULL, f.update(reader.read().mode))
        assertEquals(Mode.OFF, f.update(reader.read().mode))
        assertEquals(PanelMenu.layoutFor(Mode.OFF), PanelMenu.Layout(showMap = false, showCards = false, showCar = false))
    }
}
