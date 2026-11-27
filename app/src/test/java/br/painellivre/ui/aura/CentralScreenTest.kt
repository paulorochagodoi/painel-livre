package br.painellivre.ui.aura

import br.painellivre.ui.aura.CentralScreen.Anchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 0.5.9: tocar num controle (ar-condicionado, rádio/mídia, bancos, bateria, navegação, painel) abre o
 * balão por cima da tela atual e não volta ao Início; fechar o balão deixa a tela como estava.
 */
class CentralScreenTest {

    private val pages = listOf(CentralScreen.HOME, CentralScreen.NAV, CentralScreen.APPS)

    @Test fun acNoMapaAbreBalaoSemTrocarDeTela() {
        val s = CentralScreen(CentralScreen.NAV)
        assertEquals(PopoverState.Id.CLIMATE, s.tapControl(PopoverState.Id.CLIMATE, Anchor.DOCK_CLIMATE))
        assertEquals(CentralScreen.NAV, s.page)
        assertEquals(Anchor.DOCK_CLIMATE, s.visibleAnchor())
    }

    @Test fun radioNoMapaAbreBalaoSemTrocarDeTela() {
        val s = CentralScreen(CentralScreen.NAV)
        assertEquals(PopoverState.Id.MEDIA, s.tapControl(PopoverState.Id.MEDIA, Anchor.DOCK_MEDIA))
        assertEquals(CentralScreen.NAV, s.page)
        assertEquals(Anchor.DOCK_MEDIA, s.visibleAnchor())
    }

    @Test fun fecharBalaoNoMapaContinuaNoMapa() {
        val s = CentralScreen(CentralScreen.NAV)
        s.tapControl(PopoverState.Id.CLIMATE, Anchor.DOCK_CLIMATE)
        assertNull(s.tapControl(PopoverState.Id.CLIMATE, Anchor.DOCK_CLIMATE)) // mesmo botão fecha
        assertEquals(CentralScreen.NAV, s.page)
        s.tapControl(PopoverState.Id.MEDIA, Anchor.DOCK_MEDIA)
        assertTrue(s.close()) // X ou toque fora
        assertEquals(CentralScreen.NAV, s.page)
        assertNull(s.open)
        assertNull(s.visibleAnchor())
        assertFalse(s.close())
    }

    @Test fun trocarDeControleNoMapaNaoTrocaDeTela() {
        val s = CentralScreen(CentralScreen.NAV)
        s.tapControl(PopoverState.Id.CLIMATE, Anchor.DOCK_CLIMATE)
        assertEquals(PopoverState.Id.MEDIA, s.tapControl(PopoverState.Id.MEDIA, Anchor.DOCK_MEDIA)) // só um por vez
        assertEquals(CentralScreen.NAV, s.page)
        assertEquals(Anchor.DOCK_MEDIA, s.visibleAnchor())
    }

    @Test fun temperaturaDoDockAbreClimaENaoTrocaDeTela() {
        for (a in listOf(Anchor.DOCK_TEMP_DRIVER, Anchor.DOCK_TEMP_PASSENGER)) {
            val s = CentralScreen(CentralScreen.NAV)
            assertEquals(PopoverState.Id.CLIMATE, s.tapControl(PopoverState.Id.CLIMATE, a))
            assertEquals(CentralScreen.NAV, s.page)
            assertEquals(a, s.visibleAnchor())
        }
    }

    @Test fun nenhumControleTrocaDeTelaEmNenhumaTela() {
        for (p in pages) for (id in PopoverState.Id.values()) for (a in Anchor.values()) {
            val s = CentralScreen(p)
            s.tapControl(id, a)
            assertEquals("$id por $a na tela $p", p, s.page)
            s.tapControl(id, a)
            assertEquals("fechar $id na tela $p", p, s.page)
        }
    }

    @Test fun foraDoInicioOBalaoApontaParaOBotaoDoDock() {
        // Atalhos grandes só existem no Início (ex.: balão restaurado depois de girar a tela em outra página).
        val s = CentralScreen(CentralScreen.NAV)
        s.showControl(PopoverState.Id.CLIMATE, Anchor.TILE_CLIMATE)
        assertEquals(Anchor.DOCK_CLIMATE, s.visibleAnchor())
        s.showControl(PopoverState.Id.MEDIA, Anchor.TILE_MEDIA)
        assertEquals(Anchor.DOCK_MEDIA, s.visibleAnchor())
        s.showControl(PopoverState.Id.NAV, Anchor.TILE_NAV)
        assertEquals(Anchor.DOCK_NAV_APP, s.visibleAnchor())
        for (a in Anchor.values()) assertFalse("$a", CentralScreen.dockFor(a).tile)
        val home = CentralScreen(CentralScreen.HOME)
        home.showControl(PopoverState.Id.CLIMATE, Anchor.TILE_CLIMATE)
        assertEquals(Anchor.TILE_CLIMATE, home.visibleAnchor()) // no Início continua no atalho
    }

    @Test fun emPeSemTemperaturaNoDockASetaVaiParaOBotaoDoClima() {
        // Dock em retrato (#32) não tem os botões de temperatura: o balão do clima aponta para o ar.
        assertEquals(Anchor.DOCK_CLIMATE, CentralScreen.fallbackFor(Anchor.DOCK_TEMP_DRIVER))
        assertEquals(Anchor.DOCK_CLIMATE, CentralScreen.fallbackFor(Anchor.DOCK_TEMP_PASSENGER))
        assertEquals(Anchor.DOCK_MEDIA, CentralScreen.fallbackFor(Anchor.TILE_MEDIA))
        for (a in Anchor.values()) assertFalse("$a", CentralScreen.fallbackFor(a).tile)
    }

    @Test fun trocaDeTelaDePropositoNaoMexeNoBalao() {
        val s = CentralScreen()
        s.tapControl(PopoverState.Id.NAV, Anchor.TILE_NAV)
        s.goTo(CentralScreen.NAV) // "Buscar destino"
        assertEquals(CentralScreen.NAV, s.page)
        assertEquals(PopoverState.Id.NAV, s.open)
    }

    /** Guarda no código da Activity: abrir balão não pode voltar a chamar showPage (causa do bug da 0.5.8). */
    @Test fun activityNaoTrocaDeTelaAoAbrirBalao() {
        val src = listOf(
            File("src/main/java/br/painellivre/ui/LauncherActivity.kt"),
            File("app/src/main/java/br/painellivre/ui/LauncherActivity.kt"),
        ).first { it.exists() }.readText()
        fun body(name: String): String {
            val start = src.indexOf("private fun $name(")
            assertTrue("função $name não achada", start >= 0)
            val open = src.indexOf('{', start)
            var depth = 0
            for (i in open until src.length) {
                if (src[i] == '{') depth++
                if (src[i] == '}') { depth--; if (depth == 0) return src.substring(open, i + 1) }
            }
            error("fim de $name não achado")
        }
        for (f in listOf("togglePopover", "showPopover", "placePopover", "closePopover", "restorePopover")) {
            assertFalse("$f não pode trocar de tela", body(f).contains("showPage("))
        }
        // Todo toque que abre balão diz de qual botão veio (a seta aponta para ele, em qualquer tela).
        val calls = Regex("""togglePopover\(([^)]*)\)""").findAll(src).map { it.groupValues[1] }
            .filterNot { it.startsWith("id:") }.toList()
        assertTrue(calls.size >= 9)
        for (c in calls) assertTrue("togglePopover($c) sem âncora", c.contains("CentralScreen.Anchor."))
    }
}
