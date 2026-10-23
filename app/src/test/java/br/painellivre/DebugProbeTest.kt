package br.painellivre

import br.painellivre.debug.DebugBuffer
import br.painellivre.debug.ProbeParse
import br.painellivre.debug.ProbeTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Issue #9: modo de debug — registro, leitura das saídas de shell e o que mudou entre coletas. */
class DebugProbeTest {

    @Test fun bufferIsOffByDefaultAndKeepsOnlyTheNewest() {
        var now = 0L
        val b = DebugBuffer(max = 3, clock = { now })
        b.add("sdk", "ignorado")
        assertTrue(b.entries().isEmpty()) // desligado: nada é guardado
        b.enabled = true
        for (i in 1..5) { now = i * 1000L; b.add("prop", "linha $i") }
        assertEquals(listOf("linha 3", "linha 4", "linha 5"), b.entries().map { it.text })
        assertEquals("00:00:05.000 [prop] linha 5", b.format(TimeZone.getTimeZone("UTC")).lines().last())
        val v = b.version.value
        b.clear()
        assertTrue(b.entries().isEmpty())
        assertTrue(b.version.value > v)
    }

    @Test fun collectCommandIsReadOnly() {
        val c = ProbeParse.COLLECT_COMMAND
        for (bad in listOf("settings put", "setprop", "am broadcast", "service call", "sendInfo", "pm grant", "rm "))
            assertFalse(bad, c.contains(bad))
        for (s in ProbeParse.SECTIONS) assertTrue(s, c.contains("echo @@$s"))
    }

    @Test fun splitsSections() {
        val out = "@@SYSTEM\na=1\n@@GLOBAL\n@@SECURE\nb=2\nc=3\n@@PROPS\n[x]: [y]\n@@BCAST\n@@LOGCAT\nlinha"
        val s = ProbeParse.sections(out)
        assertEquals("a=1", s["SYSTEM"])
        assertEquals("", s["GLOBAL"])
        assertEquals("b=2\nc=3", s["SECURE"])
        assertEquals("linha", s["LOGCAT"])
    }

    @Test fun parsesSettingsAndProps() {
        assertEquals(mapOf("screen_brightness" to "120", "url" to "a=b", "vazio" to ""),
            ProbeParse.settings("screen_brightness=120\nurl=a=b\nvazio=\nlixo\n=semchave"))
        assertEquals(mapOf("sys.screen.current.mode" to "2", "ro.vazia" to ""),
            ProbeParse.getprop("[sys.screen.current.mode]: [2]\n[ro.vazia]: []\nlixo"))
    }

    @Test fun diffReportsChangedNewAndRemoved() {
        val d = ProbeParse.diff(mapOf("a" to "1", "b" to "2", "c" to "3"), mapOf("a" to "1", "b" to "5", "d" to "4"))
        assertEquals(listOf("b: 2 → 5", "c removida (era 3)", "d = 4 (nova)"), d.map { it.describe() })
    }

    private val history = """
          #0: act=com.byd.action.INSTRUMENT_NAVI_TYPE_CHANGED flg=0x10 (has extras)
            +1ms dispatch +3ms finish
            enq=2026-10-23 10:15:31.200 disp=2026-10-23 10:15:31.201 fin=2026-10-23 10:15:31.204
          #1: act=android.intent.action.TIME_TICK flg=0x50200014 (has extras)
            +0ms dispatch +2ms finish
            enq=2026-10-23 10:15:00.000 disp=2026-10-23 10:15:00.001 fin=2026-10-23 10:15:00.003
    """.trimIndent()

    @Test fun parsesBroadcastHistorySummary() {
        val b = ProbeParse.broadcastHistory(history)
        assertEquals(listOf("com.byd.action.INSTRUMENT_NAVI_TYPE_CHANGED", "android.intent.action.TIME_TICK"), b.map { it.action })
        assertEquals("2026-10-23 10:15:31.200", b[0].enqueued)
        // Linha de entrada sem "enq=" (saída cortada) não vira broadcast.
        assertEquals(1, ProbeParse.broadcastHistory("#0: act=x\n#1: act=y\nenq=2026-10-23 10:00:00.000 disp=…").size)
    }

    @Test fun candidatesAreMarked() {
        assertTrue(ProbeParse.isCandidate("sys.screen.current.mode"))
        assertTrue(ProbeParse.isCandidate("act=AUTONAVI_STANDARD_BROADCAST_SEND"))
        assertTrue(ProbeParse.isCandidate("instrument_navi_type"))
        assertFalse(ProbeParse.isCandidate("screen_brightness"))
        assertFalse(ProbeParse.isCandidate("android.intent.action.TIME_TICK"))
    }

    private fun collect(system: String, props: String, bcast: String = "", logcat: String = "") =
        mapOf("SYSTEM" to system, "GLOBAL" to "", "SECURE" to "", "PROPS" to props, "BCAST" to bcast, "LOGCAT" to logcat)

    @Test fun trackerShowsOnlyWhatChangedAfterTheBaseline() {
        val t = ProbeTracker()
        val first = t.update(collect("navi_mode=1\nscreen_brightness=100", "[sys.screen.current.mode]: [1]", history, "linha velha"))
        assertEquals(listOf("base"), first.map { it.source }) // a base não despeja tudo
        assertTrue(first[0].text.contains("settings system: 2"))

        val newer = "  #0: act=com.byd.X flg=0x10\n    enq=2026-10-23 10:16:00.000 disp=…\n$history"
        val second = t.update(collect("navi_mode=4\nscreen_brightness=100", "[sys.screen.current.mode]: [3]", newer, "linha velha\nnavi linha nova"))
        assertEquals(
            listOf(
                "settings system" to "★ navi_mode: 1 → 4",
                "prop" to "★ sys.screen.current.mode: 1 → 3",
                "broadcast" to "2026-10-23 10:16:00.000 act=com.byd.X flg=0x10",
                "logcat" to "navi linha nova",
            ),
            second.map { it.source to it.text },
        )
        // Nada mudou: nada aparece.
        assertTrue(t.update(collect("navi_mode=4\nscreen_brightness=100", "[sys.screen.current.mode]: [3]", newer, "navi linha nova")).isEmpty())
    }

    @Test fun trackerIgnoresMissingSectionsInsteadOfReportingEverythingRemoved() {
        val t = ProbeTracker()
        t.update(collect("a=1", "[p]: [1]"))
        assertTrue(t.update(mapOf("SYSTEM" to "a=1")).isEmpty())
    }

    @Test fun redactsPhoneNumbersAndBluetoothAddresses() {
        val s = ProbeParse.redactPersonal("tel:+55 11 98765-4321 conta=00:1A:7D:DA:71:13 id=42 hora=10:15")
        assertFalse(s.contains("98765"))
        assertFalse(s.contains("00:1A:7D"))
        assertTrue(s.contains("id=42"))
        assertTrue(s.contains("hora=10:15"))
    }
}
