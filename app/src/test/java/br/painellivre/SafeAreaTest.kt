package br.painellivre

import br.painellivre.cluster.PanelMenu
import br.painellivre.cluster.SafeArea
import br.painellivre.cluster.SafeArea.Insets
import br.painellivre.cluster.SafeArea.Rect
import org.junit.Assert.*
import org.junit.Test

/** Área segura do mapa no painel (foto do painel do Song Plus, 1920×720). */
class SafeAreaTest {

    private val full = Rect(0, 0, 1920, 720)
    private fun songPlus(adjust: Insets = SafeArea.NONE) = SafeArea.resolve(1920, 720, SafeArea.SONG_PLUS, adjust)

    @Test fun songPlusDefault() {
        assertEquals(Insets(230, 90, 230, 230), songPlus())
        val p = SafeArea.plan(1920, 720, songPlus(), full)
        assertEquals(Rect(230, 90, 1690, 490), p.safe)
        // Carro no meio da largura e acima da faixa de baixo (y ~360).
        assertEquals(960, p.carX)
        assertEquals(360, p.carY)
    }

    @Test fun safeAreaAvoidsEveryByDOverlay() {
        val safe = SafeArea.plan(1920, 720, songPlus(), full).safe
        for (o in SafeArea.SONG_PLUS_OVERLAYS) {
            // O assistente de faixa (y 420–520) entra um pouco na área: só o carro e os cartões
            // precisam fugir dele (testado abaixo).
            if (o == Rect(1290, 420, 1450, 520)) continue
            assertFalse("área segura invade $o", safe.intersects(o))
        }
    }

    @Test fun cardsAndCarStayClearOfOverlays() {
        val p = SafeArea.plan(1920, 720, songPlus(), full)
        val cardsH = (p.unit * SafeArea.CARDS_HEIGHT_UNITS).toInt()
        val cards = Rect(p.safe.left, p.safe.top, p.safe.left + p.cardWidth, p.safe.top + cardsH)
        val half = (p.unit * SafeArea.CAR_SIZE / 2).toInt()
        val car = Rect(p.carX - half, p.carY - half, p.carX + half, p.carY + half)
        assertTrue("cartões fora da área segura", p.safe.contains(cards))
        assertTrue("carro fora da área segura", p.safe.contains(car))
        assertFalse("cartão encosta no carro", cards.intersects(car))
        for (o in SafeArea.SONG_PLUS_OVERLAYS) {
            assertFalse("cartões sob $o", cards.intersects(o))
            assertFalse("carro sob $o", car.intersects(o))
        }
        // Tamanho de sempre no Song Plus (os cartões não encolheram).
        assertEquals(720f, p.unit, 0f)
        assertEquals(576, p.cardWidth)
    }

    @Test fun cameraFocusIsTheCar() {
        val p = SafeArea.plan(1920, 720, songPlus(), full)
        val pad = p.cameraPadding
        // Centro da área sem padding = carro.
        assertEquals(p.carX, (pad.left + 1920 - pad.right) / 2)
        assertEquals(p.carY, (pad.top + 720 - pad.bottom) / 2)
        assertEquals(Insets(0, 0, 0, 0), pad) // no Song Plus o carro cai no meio do display
    }

    @Test fun focusPaddingIsMinimal() {
        assertEquals(Insets(0, 200, 0, 0), SafeArea.focusPadding(1000, 600, 500, 400))
        assertEquals(Insets(0, 0, 400, 300), SafeArea.focusPadding(1000, 600, 300, 150))
        assertEquals(Insets(1000, 600, 0, 0), SafeArea.focusPadding(1000, 600, 5000, 5000)) // preso à vista
    }

    @Test fun fineAdjustAddsToDefaultAndAcceptsNegative() {
        assertEquals(Insets(250, 80, 230, 260), songPlus(Insets(20, -10, 0, 30)))
        assertEquals(Insets(0, 0, 0, 0), SafeArea.resolve(1920, 720, SafeArea.SONG_PLUS, Insets(-400, -400, -400, -400)))
        // Ajuste além do limite é cortado em ±400.
        assertEquals(songPlus(Insets(400, 0, 0, 0)), songPlus(Insets(9999, 0, 0, 0)))
    }

    @Test fun hugeMarginsNeverEatTheWholeMap() {
        val i = SafeArea.resolve(1920, 720, Insets(1500, 1500, 1500, 1500), Insets(400, 400, 400, 400))
        assertTrue(1920 - i.left - i.right >= (1920 * SafeArea.MIN_WIDTH_FRACTION).toInt())
        assertTrue(720 - i.top - i.bottom >= (720 * SafeArea.MIN_HEIGHT_FRACTION).toInt())
        val p = SafeArea.plan(1920, 720, i, full)
        assertTrue(p.safe.width > 0 && p.safe.height > 0)
        assertTrue(p.cardWidth <= p.safe.width)
        assertTrue(p.unit * SafeArea.CARDS_HEIGHT_UNITS <= p.safe.height + 1)
    }

    @Test fun scalesWithOtherPanelSizes() {
        // Painel de outra resolução: as medidas do Song Plus escalam com o display.
        assertEquals(Insets(115, 45, 115, 115), SafeArea.resolve(960, 360, SafeArea.SONG_PLUS, SafeArea.NONE))
        assertEquals(Insets(0, 0, 0, 0), SafeArea.resolve(0, 0, SafeArea.SONG_PLUS, SafeArea.NONE))
    }

    @Test fun presetNoneUsesWholeScreen() {
        assertEquals(SafeArea.NONE, SafeArea.preset("none"))
        assertEquals(SafeArea.SONG_PLUS, SafeArea.preset("songplus"))
        assertEquals(SafeArea.SONG_PLUS, SafeArea.preset(null))
        assertEquals(SafeArea.SONG_PLUS, SafeArea.preset("lixo"))
        val p = SafeArea.plan(1920, 720, SafeArea.resolve(1920, 720, SafeArea.NONE, SafeArea.NONE), full)
        assertEquals(full, p.safe)
    }

    @Test fun shrunkContentFromPanelMenuIsClipped() {
        // "Pequeno" (issue #9): o conteúdo vai para o meio; a área segura é recortada por ele.
        val l = PanelMenu.SMALL_LAYOUT
        val content = Rect((1920 * l.left).toInt(), (720 * l.top).toInt(),
            1920 - (1920 * l.right).toInt(), 720 - (720 * l.bottom).toInt())
        val p = SafeArea.plan(1920, 720, songPlus(), content)
        // Em coordenadas do conteúdo: os lados já estão longe dos avisos, só topo e base cortam.
        assertEquals(0, p.safe.left)
        assertEquals(content.width, p.safe.right)
        assertEquals(90 - content.top, p.safe.top)
        assertEquals(490 - content.top, p.safe.bottom)
        assertTrue(p.cardWidth <= p.safe.width)
    }

    @Test fun narrowSafeAreasKeepCardsBeforeTheCar() {
        val small = Rect(537, 86, 1383, 634)
        for (content in listOf(small, full)) {
            for (adjust in listOf(SafeArea.NONE, Insets(400, -400, 400, -400))) {
                val p = SafeArea.plan(1920, 720, songPlus(adjust), content)
                val carLeft = p.carX - (p.unit * SafeArea.CAR_SIZE / 2).toInt()
                assertTrue("cartão invade carro: $p", p.safe.left + p.cardWidth < carLeft)
                assertTrue(p.cardWidth <= p.safe.width)
            }
        }
    }

    @Test fun extremeInsetsCannotOverflowOrConsumeTinyDisplays() {
        for (size in listOf(1, 2, 720, 1920)) {
            val i = SafeArea.resolve(size, size,
                Insets(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
                Insets(Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
            assertTrue(i.left >= 0 && i.top >= 0 && i.right >= 0 && i.bottom >= 0)
            assertTrue(i.left.toLong() + i.right < size)
            assertTrue(i.top.toLong() + i.bottom < size)
        }
    }

    @Test fun contentOutsideSafeAreaFallsBackToContent() {
        val p = SafeArea.plan(1920, 720, songPlus(), Rect(0, 600, 1920, 720))
        assertEquals(Rect(0, 0, 1920, 120), p.safe)
    }
    @Test fun telemetryStaysInsideSafeAreaWithMenuAndFineAdjust() {
        val l = PanelMenu.SMALL_LAYOUT
        val small = Rect((1920 * l.left).toInt(), (720 * l.top).toInt(),
            1920 - (1920 * l.right).toInt(), 720 - (720 * l.bottom).toInt())
        for (content in listOf(full, small)) {
            for (adjust in listOf(SafeArea.NONE, Insets(400, 400, 400, 400),
                Insets(-400, -400, -400, -400), Insets(400, -400, 400, -400))) {
                val p = SafeArea.plan(1920, 720, songPlus(adjust), content)
                assertTrue("telemetria fora da área: $p", p.safe.contains(p.telemetryBounds))
                val half = (p.unit * SafeArea.CAR_SIZE / 2).toInt()
                assertFalse(p.telemetryBounds.intersects(Rect(p.carX - half, p.carY - half,
                    p.carX + half, p.carY + half)))
            }
        }
        val p = SafeArea.plan(1920, 720, songPlus(), full)
        for (o in SafeArea.SONG_PLUS_OVERLAYS) assertFalse(p.telemetryBounds.intersects(o))
    }

}
