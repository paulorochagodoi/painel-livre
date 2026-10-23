package br.painellivre

import br.painellivre.cluster.PanelSize
import br.painellivre.cluster.PanelSize.Geometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelSizeTest {
    private val songPlus = "BYD Song Plus DM-i di3_sp di3 BYD/di3/di3:10/QKQ1/13.1.33:user/release-keys"
    private val unknown = "sdk_gphone_x86_64 sdk_gphone_x86_64 generic_x86_64 google/sdk/generic:10"
    private val panel12 = Geometry(1920, 720)
    private val panel88 = Geometry(1280, 480)

    @Test fun autoSongPlusMeasured12() {
        val d = PanelSize.decide("auto", songPlus, panel12)
        assertEquals(PanelSize.CODE_12_3, d.code)
    }

    @Test fun autoNeverWithoutMeasuredPanel() {
        // Primeira ativação: o painel ainda não foi medido → age como o DashCast (não envia).
        assertNull(PanelSize.decide("auto", songPlus, null).code)
    }

    @Test fun autoBlocksSmallPanelEvenForSongPlus() {
        val d = PanelSize.decide("auto", songPlus, panel88)
        assertNull(d.code)
        assertTrue(d.reason.startsWith("BLOQUEADO"))
    }

    @Test fun autoUnknownModelSendsNothing() {
        assertNull(PanelSize.decide("auto", unknown, panel12).code)
    }

    @Test fun autoIgnoresOtherBydNames() {
        // "seal" sozinho (10,25") e "dm-i" genérico não liberam o 30 no automático.
        assertNull(PanelSize.decide("auto", "BYD Seal EV", panel12).code)
        assertNull(PanelSize.decide("auto", "BYD King DM-i", panel12).code)
        assertEquals(PanelSize.CODE_12_3, PanelSize.decide("auto", "BYD SEAL U DM-i", panel12).code)
    }

    @Test fun manualChoiceRespectsMeasuredPanel() {
        assertEquals(30, PanelSize.decide("30", unknown, panel12).code)
        assertNull(PanelSize.decide("30", unknown, panel88).code)
        assertEquals(29, PanelSize.decide("29", unknown, panel88).code)
        assertNull(PanelSize.decide("29", unknown, panel12).code)
        assertEquals(31, PanelSize.decide("31", unknown, panel12).code)
        assertEquals(30, PanelSize.decide("30", unknown, null).code) // escolhido e ainda não medido
    }

    @Test fun offAndInvalidNeverSend() {
        assertNull(PanelSize.decide("off", songPlus, panel12).code)
        assertNull(PanelSize.decide("1", songPlus, panel12).code)
        assertNull(PanelSize.decide("16", songPlus, panel12).code)
        assertNull(PanelSize.decide("lixo", songPlus, panel12).code)
    }

    @Test fun geometryParse() {
        assertEquals(panel12, Geometry.parse("1920x720"))
        assertNull(Geometry.parse("0x720"))
        assertNull(Geometry.parse("abc"))
        assertNull(Geometry.parse(null))
    }
}
