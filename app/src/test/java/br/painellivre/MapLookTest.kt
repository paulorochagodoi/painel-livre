package br.painellivre

import br.painellivre.cluster.ClusterLook
import br.painellivre.cluster.SafeArea
import br.painellivre.nav.MapLook
import br.painellivre.ui.aura.AuraColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rodada 2 da interface Aura: ruas mais largas, rota em accent com contorno, seta do Waze, painel. */
class MapLookTest {

    // ── Ruas ────────────────────────────────────────────────────────────────

    @Test fun mainAndSecondaryRoadsOfEveryOpenFreeMapStyleGetWider() {
        // Nomes reais das camadas (positron/dark/fiord e liberty).
        val major = listOf("highway_major_inner", "highway_major_casing", "highway_motorway_inner", "highway_motorway_casing",
            "tunnel_motorway_inner", "highway_motorway_bridge_casing", "road_trunk_primary", "road_secondary_tertiary_casing",
            "bridge_motorway", "road_motorway_link", "tunnel_link")
        major.forEach { assertEquals(it, MapLook.MAJOR_FACTOR, MapLook.roadFactor(it, "transportation")!!, 0.0) }
        val minor = listOf("highway_minor", "road_minor", "road_minor_casing", "tunnel_street_casing", "bridge_street", "road_service_track")
        minor.forEach { assertEquals(it, MapLook.MINOR_FACTOR, MapLook.roadFactor(it, "transportation")!!, 0.0) }
        assertTrue(MapLook.MAJOR_FACTOR > MapLook.MINOR_FACTOR && MapLook.MINOR_FACTOR > 1.0)
    }

    @Test fun railPathsAndOtherLayersStayAsTheyAre() {
        listOf("railway", "railway_dashline", "railway_transit", "road_major_rail", "road_major_rail_hatching",
            "highway_path", "road_path_pedestrian", "road_pier", "highway_major_subtle", "highway_motorway_subtle")
            .forEach { assertNull(it, MapLook.roadFactor(it, "transportation")) }
        // Fora da fonte de vias (rios, limites, nomes), nada muda mesmo com nome parecido.
        assertNull(MapLook.roadFactor("waterway", "waterway"))
        assertNull(MapLook.roadFactor("highway_major_inner", "transportation_name"))
        assertNull(MapLook.roadFactor("highway_major_inner", null))
    }

    @Test fun widthStaysProportionalToZoom() {
        // highway_major_inner do positron: ["interpolate", ["exponential", 1.3], ["zoom"], 10, 2, 20, 20]
        val raw = arrayOf<Any?>("interpolate", arrayOf<Any?>("exponential", 1.3), arrayOf<Any?>("zoom"), 10.0, 2.0, 20.0, 20.0)
        val scaled = MapLook.scaleWidth(raw, 1.6) as List<*>
        assertEquals(listOf("interpolate", listOf("exponential", 1.3), listOf("zoom"), 10.0, 3.2, 20.0, 32.0), scaled.map { round(it) })
        // O zoom continua no topo (exigência do MapLibre) e as paradas de zoom não mudam.
        assertEquals("""["interpolate",["exponential",1.3],["zoom"],10,3.2,20,32]""", MapLook.toJson(scaled.map { round(it) }))
    }

    @Test fun constantsStepsAndDataExpressionsScaleToo() {
        assertEquals(3.2, MapLook.scaleWidth(2, 1.6) as Double, 1e-9)
        val step = MapLook.scaleWidth(listOf("step", listOf("zoom"), 1.0, 14.0, 2.0), 2.0)
        assertEquals(listOf("step", listOf("zoom"), 2.0, 14.0, 4.0), step)
        // Parada com expressão de dado: multiplica dentro, o zoom continua no topo.
        val data = listOf("match", listOf("get", "class"), "primary", 3.0, 2.0)
        val inter = MapLook.scaleWidth(listOf("interpolate", listOf("linear"), listOf("zoom"), 10.0, data), 1.5) as List<*>
        assertEquals(listOf("*", 1.5, data), inter[4])
        assertEquals(listOf("*", 1.5, data), MapLook.scaleWidth(data, 1.5))
    }

    @Test fun expressionsThatCannotBeScaledSafelyAreLeftAlone() {
        // Zoom fora do topo: multiplicar quebraria o estilo.
        assertNull(MapLook.scaleWidth(listOf("coalesce", listOf("interpolate", listOf("linear"), listOf("zoom"), 10, 1, 20, 2)), 2.0))
        assertNull(MapLook.scaleWidth(listOf("interpolate", listOf("linear"), listOf("zoom"), 10.0), 2.0))
        assertNull(MapLook.scaleWidth(null, 2.0))
        assertNull(MapLook.scaleWidth("texto", 2.0))
    }

    // ── Rota ────────────────────────────────────────────────────────────────

    @Test fun routeIsAccentWithOutlineInBothThemes() {
        for (c in listOf(AuraColors.LIGHT, AuraColors.DARK)) {
            val r = MapLook.routeColors(c)
            assertEquals(c.accent, r.line)
            assertTrue("contorno distinto da linha", r.casing != r.line)
            assertTrue("contorno separa a rota (${c.label})", AuraColors.contrast(r.line, r.casing) >= 3.0)
        }
        val casing = MapLook.routeCasingStops()
        MapLook.ROUTE_STOPS.zip(casing).forEach { (l, k) -> assertEquals(l.first, k.first, 0.0); assertTrue(k.second > l.second) }
        // A rota cresce com o zoom e fica mais larga que a via principal alargada do positron.
        MapLook.ROUTE_STOPS.zipWithNext().forEach { (a, b) -> assertTrue(b.second > a.second) }
        assertTrue(MapLook.ROUTE_STOPS.last().second > 20.0 * MapLook.MAJOR_FACTOR)
    }

    // ── Seta do carro ───────────────────────────────────────────────────────

    @Test fun carArrowIsWideFilledAccentWithLightOutline() {
        val xs = MapLook.ARROW.map { it.first }
        val ys = MapLook.ARROW.map { it.second }
        assertTrue("larga como a do Waze", xs.max() - xs.min() >= 0.85)
        assertEquals("ponta para cima, no meio", 0.5, MapLook.ARROW.first().first, 0.0)
        assertEquals(ys.min(), MapLook.ARROW.first().second, 0.0)
        assertTrue("base recortada", MapLook.ARROW[2].second < MapLook.ARROW[1].second)
        assertTrue(MapLook.ARROW_OUTLINE >= 0.06 && MapLook.ARROW_SHADOW_DY > 0)
        for (c in listOf(AuraColors.LIGHT, AuraColors.DARK)) {
            val a = MapLook.arrowColors(c)
            assertEquals(c.accent, a.fill)
            assertEquals(0xFFFFFFFF.toInt(), a.outline)
            assertTrue("sombra leve", (a.shadow ushr 24) in 0x20..0x80)
        }
        assertTrue("seta maior na busca", MapLook.CENTRAL_ARROW_DP > 44)
        assertTrue("seta maior no painel", SafeArea.CAR_SIZE > 0.095f)
    }

    // ── Painel ──────────────────────────────────────────────────────────────

    @Test fun panelUsesAuraColorsWithReadableContrastOverAnyMap() {
        for (c in listOf(AuraColors.LIGHT, AuraColors.DARK)) {
            val l = ClusterLook.of(c)
            assertEquals(c.dark, l.dark)
            assertEquals(c.surface and 0xFFFFFF, l.card and 0xFFFFFF)
            assertEquals(c.accent, l.accent)
            // Cartão quase opaco: o texto lê bem com mapa claro ou escuro atrás.
            for (behind in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF7F7F7F.toInt())) {
                val bg = ClusterLook.over(l.card, behind)
                for ((name, ink) in listOf("ink" to l.ink, "muted" to l.inkMuted, "accent" to l.accent, "warn" to l.warn)) {
                    val k = AuraColors.contrast(ink, bg)
                    assertTrue("$name ${c.label} sobre ${Integer.toHexString(behind)}: $k", k >= 4.5)
                }
            }
            assertTrue(AuraColors.contrast(l.onAccent, l.accent) >= 4.5)
            assertTrue(AuraColors.contrast(l.statusInk, l.statusBg) >= 4.5)
        }
    }

    @Test fun panelTextIsReadableAtAGlance() {
        ClusterLook.READING_TEXT.forEach { assertTrue("$it", it >= ClusterLook.MIN_TEXT) }
        // No Song Plus (unidade 720 px): a distância passa de 60 px e nenhum texto de leitura fica abaixo de 18 px.
        assertTrue(ClusterLook.DISTANCE * 720 >= 60)
        assertTrue(ClusterLook.MIN_TEXT * 720 >= 18)
        assertTrue("números maiores que os rótulos", ClusterLook.DISTANCE > ClusterLook.STREET && ClusterLook.ETA_VALUE > ClusterLook.ETA_LABEL)
    }

    @Test fun panelCardsStillFitTheSafeAreaWithTheBiggerArrow() {
        // Song Plus 1920×720, tela inteira e "Pequeno" (#9): cartões e seta dentro da área segura.
        val insets = SafeArea.resolve(1920, 720, SafeArea.SONG_PLUS, SafeArea.NONE)
        for (content in listOf(SafeArea.Rect(0, 0, 1920, 720), SafeArea.Rect(537, 86, 1383, 634))) {
            val p = SafeArea.plan(1920, 720, insets, content)
            val half = (p.unit * SafeArea.CAR_SIZE / 2).toInt()
            assertTrue(p.safe.contains(SafeArea.Rect(p.carX - half, p.carY - half, p.carX + half, p.carY + half)))
            assertTrue(p.safe.contains(p.telemetryBounds))
            val stack = (p.unit * (ClusterLook.ICON_BOX + 2 * ClusterLook.PAD + ClusterLook.ETA_VALUE + ClusterLook.ETA_LABEL + 3 * ClusterLook.PAD)).toInt()
            assertTrue("pilha manobra + chegada cabe na altura ($stack de ${p.safe.height})", stack <= p.safe.height)
        }
    }

    @Test fun smallModeReservesRoomForDistanceWithoutTouchingTheArrow() {
        val insets = SafeArea.resolve(1920, 720, SafeArea.SONG_PLUS, SafeArea.NONE)
        for (frame in listOf(SafeArea.Rect(0, 0, 1920, 720), SafeArea.Rect(537, 86, 1383, 634))) {
            val p = SafeArea.plan(1920, 720, insets, frame)
            val width = ClusterLook.maneuverWidth(p)
            val textWidth = width - p.unit * (ClusterLook.ICON_BOX + 3.6f * ClusterLook.PAD)
            // Reserva para cinco caracteres Sora grandes, incluindo unidade e vírgula.
            assertTrue(textWidth >= p.unit * ClusterLook.DISTANCE * 3.4f)
            assertTrue(p.safe.left + width <= p.carX - p.unit * SafeArea.CAR_SIZE / 2)
        }
    }

    private fun round(v: Any?): Any? = when (v) {
        is Double -> Math.round(v * 1000) / 1000.0
        is Array<*> -> v.map(::round)
        is List<*> -> v.map(::round)
        else -> v
    }
}
