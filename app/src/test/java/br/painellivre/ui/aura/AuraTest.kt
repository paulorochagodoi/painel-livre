package br.painellivre.ui.aura

import br.painellivre.car.Autoservice
import br.painellivre.car.TelemetryCatalog
import br.painellivre.car.TelemetrySnapshot
import br.painellivre.car.VehicleData
import br.painellivre.ui.aura.CarReadouts.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

/** Interface Aura (docs/INTERFACE-AURA.md): tokens, números BR, chips, janelas e escala. */
class AuraTest {

    private fun snapshot(vararg values: Pair<String, Int>, status: TelemetrySnapshot.Status = TelemetrySnapshot.Status.OK) =
        TelemetrySnapshot(status, readings = values.associate { (key, word) ->
            TelemetryCatalog.byKey(key).let { key to TelemetrySnapshot.reading(it, it.fid, Autoservice.Reply.Value(word)) }
        })

    private fun float(f: Float) = java.lang.Float.floatToIntBits(f)

    // ── Tokens e tema ───────────────────────────────────────────────────────

    @Test fun tokensMatchTheDesignerFile() {
        // tokens.json da designer é a fonte: confere todas as cores dos dois temas.
        val json = listOf(File("../docs/design/aura-launcher/docs/tokens.json"), File("docs/design/aura-launcher/docs/tokens.json"))
            .firstOrNull { it.exists() }?.readText()
        val names = mapOf(
            "bg" to AuraColors::bg, "horizon" to AuraColors::horizon, "platform-glow" to AuraColors::platformGlow,
            "surface" to AuraColors::surface, "surface-raised" to AuraColors::surfaceRaised,
            "surface-sunken" to AuraColors::surfaceSunken, "surface-dock" to AuraColors::surfaceDock,
            "line" to AuraColors::line, "ink" to AuraColors::ink, "ink-muted" to AuraColors::inkMuted,
            "accent" to AuraColors::accent, "accent-soft" to AuraColors::accentSoft, "on-accent" to AuraColors::onAccent,
            "warn" to AuraColors::warn, "warn-soft" to AuraColors::warnSoft, "alert" to AuraColors::alert, "ok" to AuraColors::ok,
            "heat" to AuraColors::heat, "heat-soft" to AuraColors::heatSoft, "cool" to AuraColors::cool,
            "cool-soft" to AuraColors::coolSoft, "scrim" to AuraColors::scrim,
        )
        if (json != null) {
            for ((name, prop) in names) {
                val m = Regex("\"name\": \"$name\",\\s*\"value\": \\{\\s*\"light\": \"(#[0-9a-f]+)\",\\s*\"dark\": \"(#[0-9a-f]+)\"").find(json)
                    ?: error("token $name não achado")
                assertEquals(name, AuraColors.hex(m.groupValues[1]), prop.get(AuraColors.LIGHT))
                assertEquals(name, AuraColors.hex(m.groupValues[2]), prop.get(AuraColors.DARK))
            }
        }
        // E alguns valores fixos, caso o arquivo da designer saia do repositório.
        assertEquals(0xFF0A6F82.toInt(), AuraColors.LIGHT.accent)
        assertEquals(0xFF4FD4E6.toInt(), AuraColors.DARK.accent)
        assertEquals(0x666FCAD8, AuraColors.LIGHT.platformGlow)
        assertEquals(0x80000000.toInt(), AuraColors.DARK.scrim)
    }

    @Test fun textPassesContrastOnEverySurface() {
        for (c in listOf(AuraColors.LIGHT, AuraColors.DARK)) {
            for (bg in listOf(c.bg, c.surface, c.surfaceRaised, c.surfaceSunken)) {
                assertTrue(c.label, AuraColors.contrast(c.ink, bg) >= 4.5)
                assertTrue(c.label, AuraColors.contrast(c.inkMuted, bg) >= 4.5)
            }
            assertTrue(AuraColors.contrast(c.onAccent, c.accent) >= 4.5)
            assertTrue(AuraColors.contrast(c.warn, c.warnSoft) >= 4.5)
            assertTrue(AuraColors.contrast(c.accent, c.accentSoft) >= 4.5)
        }
    }

    @Test fun themeSettingAndToggle() {
        fun at(h: Int) = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0) }
        assertSame(AuraColors.LIGHT, AuraColors.forSetting("auto", at(12)))
        assertSame(AuraColors.DARK, AuraColors.forSetting("auto", at(22)))
        assertSame(AuraColors.LIGHT, AuraColors.forSetting("day", at(23)))
        assertSame(AuraColors.DARK, AuraColors.forSetting("night", at(12)))
        assertEquals("night", AuraColors.toggled(AuraColors.LIGHT))
        assertEquals("day", AuraColors.toggled(AuraColors.DARK))
        assertEquals("Claro", AuraColors.LIGHT.label)
        assertEquals("Escuro", AuraColors.DARK.label)
    }

    @Test fun legacyScreensGetAuraColors() {
        val p = AuraColors.DARK.toPalette()
        assertTrue(p.dark)
        assertEquals(AuraColors.DARK.ink, p.text1)
        assertEquals(AuraColors.DARK.accent, p.accent)
        assertEquals(AuraColors.DARK.surface, p.card)
        assertFalse(AuraColors.LIGHT.toPalette().dark)
    }

    // ── Números no formato brasileiro ───────────────────────────────────────

    @Test fun brazilianNumbers() {
        assertEquals("22,0°", AuraFormat.temp(22.0))
        assertEquals("23,5°", AuraFormat.temp(23.5))
        assertEquals("—", AuraFormat.temp(null))
        assertEquals("24°", AuraFormat.tempWhole(23.6))
        assertEquals("6,4 km", AuraFormat.km(6.4))
        assertEquals("0,4 km", AuraFormat.km(0.42))
        assertEquals("12 km", AuraFormat.km(12.3))
        assertEquals("10 km", AuraFormat.km(9.97))
        assertEquals("—", AuraFormat.km(null))
        assertEquals("1h40", AuraFormat.duration(100))
        assertEquals("28 min", AuraFormat.duration(28))
        assertEquals("2h", AuraFormat.duration(120))
        assertEquals("1h05", AuraFormat.duration(65))
        assertEquals("—", AuraFormat.duration(null))
        assertEquals("62%", AuraFormat.percent(62.4))
        assertEquals("Quinta-feira", AuraFormat.capitalized("quinta-feira"))
    }

    // ── Chips de estado ao redor do carro ───────────────────────────────────

    @Test fun noDataMeansNoChipsNeverClosed() {
        assertTrue(CarReadouts.chips(null).isEmpty())
        assertTrue(CarReadouts.chips(snapshot("door_fl" to 1, status = TelemetrySnapshot.Status.NO_ADB)).isEmpty())
        assertTrue(CarReadouts.chips(snapshot("door_fl" to -10011)).isEmpty()) // sentinela = sem dado
        assertNull(CarReadouts.summary(null))
        assertNull(CarReadouts.lock(null))
        assertNull(CarReadouts.climate(null))
        // Só uma porta lida e fechada: não dá para dizer "Fechadas".
        val partial = CarReadouts.chips(snapshot("door_fl" to 0)).single()
        assertEquals("Sem dado", partial.value)
        assertEquals(Level.UNKNOWN, partial.level)
        assertNull(CarReadouts.summary(snapshot("door_fl" to 0)))
    }

    @Test fun closedCarIsNeutralWithWords() {
        val s = snapshot("door_fl" to 0, "door_fr" to 0, "door_rl" to 0, "door_rr" to 0, "hood" to 0, "trunk" to 0,
            "win_fl" to 0, "win_fr" to 0, "win_rl" to 0, "win_rr" to 0, "lock" to 2, "charge_lid" to 0)
        val chips = CarReadouts.chips(s).associateBy { it.id }
        assertEquals(listOf("doors", "windows", "hood", "trunk", "charge"), CarReadouts.chips(s).map { it.id })
        assertEquals("Fechadas", chips.getValue("doors").value)
        assertEquals("Fechados", chips.getValue("windows").value)
        assertEquals("Fechado", chips.getValue("hood").value)
        assertEquals("Tampa fechada", chips.getValue("charge").value)
        assertTrue(chips.values.all { it.level == Level.NEUTRAL })
        assertEquals(CarReadouts.Summary("Tudo fechado", warn = false), CarReadouts.summary(s))
        assertEquals("Travado", CarReadouts.lock(s))
    }

    @Test fun openPartsAreWarnWithWords() {
        val s = snapshot("door_fl" to 1, "door_fr" to 0, "door_rl" to 0, "door_rr" to 0, "win_rl" to 40, "win_fl" to 0,
            "trunk" to 1, "hood" to 0, "lock" to 1)
        val chips = CarReadouts.chips(s).associateBy { it.id }
        assertEquals("Dianteira esq. aberta", chips.getValue("doors").value)
        assertEquals(Level.WARN, chips.getValue("doors").level)
        assertEquals("Tras. esq. 40%", chips.getValue("windows").value)
        assertEquals(Level.WARN, chips.getValue("windows").level)
        assertEquals("Aberto", chips.getValue("trunk").value)
        assertEquals(Level.WARN, chips.getValue("trunk").level)
        assertEquals(Level.NEUTRAL, chips.getValue("hood").level)
        assertEquals(CarReadouts.Summary("3 aberturas", warn = true), CarReadouts.summary(s))
        assertEquals("Destravado", CarReadouts.lock(s))
        val two = CarReadouts.chips(snapshot("door_fl" to 1, "door_rr" to 1)).first()
        assertEquals("2 abertas", two.value)
        assertEquals(CarReadouts.Summary("1 abertura", warn = true), CarReadouts.summary(snapshot("hood" to 1)))
    }

    @Test fun chargingChipIsAccentAndTyres() {
        val s = snapshot("bms" to 1, "soc" to float(62f), "gun" to 2)
        val chip = CarReadouts.chips(s).single { it.id == "charge" }
        assertEquals("Carregando · 62%", chip.value)
        assertEquals(Level.ACCENT, chip.level)
        val charge = snapshot("bms" to 1, "gun" to 2, "charge_kw" to float(6.6f), "charge_h" to 1, "charge_min" to 40)
        assertEquals("Carregando · 6,6 kW · 1h40", CarReadouts.battery(null, charge).charge)
        assertEquals("Cabo conectado, sem carregar", CarReadouts.battery(null, snapshot("gun" to 2, "bms" to 2)).charge)
        assertEquals(Level.WARN, CarReadouts.chips(snapshot("charge_lid" to 1)).single().level)
        val tyres = CarReadouts.chips(snapshot("tyre_p_fl" to 250, "tyre_p_fr" to 250, "tyre_p_rl" to 250, "tyre_p_rr" to 180)).single()
        assertEquals("1 fora da faixa", tyres.value)
        assertEquals(Level.WARN, tyres.level)
        val ok = CarReadouts.chips(snapshot("tyre_p_fl" to 250, "tyre_p_fr" to 250, "tyre_p_rl" to 250, "tyre_p_rr" to 250)).single()
        assertEquals("Na faixa", ok.value)
        // Carregando com a tampa aberta: não conta como abertura.
        assertNull(CarReadouts.summary(snapshot("bms" to 1, "charge_lid" to 1)))
        assertEquals(CarReadouts.Summary("1 abertura", warn = true), CarReadouts.summary(snapshot("bms" to 2, "charge_lid" to 1)))
    }

    @Test fun climateIsReadOnlyAndOnlyWhatWasRead() {
        // ac_auto manda 2 no fio = ligado (CarCommands.onWire); 1 no fio = desligado (bug visto no carro em 29/10).
        val c = CarReadouts.climate(snapshot("ac_temp_driver" to 22, "ac_temp_pass" to 24, "ac_fan" to 3, "ac_auto" to 2,
            "ac_compressor" to 7, "seat_heat_driver" to 3, "seat_vent_pass" to 1))!!
        assertEquals("22,0°", AuraFormat.temp(c.driverC))
        assertEquals("24,0°", AuraFormat.temp(c.passengerC))
        assertEquals(true, c.auto)
        assertNull(c.ac) // valor fora de 0/1: sem leitura confiável, o modo some
        assertEquals(2, c.driverSeat.heat)
        assertEquals(0, c.passengerSeat.vent)
        assertNull(c.passengerSeat.heat)
        assertEquals("Automático · Ventilação 3", c.tileLine)
        assertEquals(false, CarReadouts.climate(snapshot("ac_auto" to 1))!!.auto)
    }

    @Test fun batteryPrefersSdkThenTelemetry() {
        val sdk = VehicleData.Snapshot(batteryPercent = 70.0, evRangeKm = null, fuelPercent = null, fuelRangeKm = 500, odometerKm = null)
        val b = CarReadouts.battery(sdk, snapshot("soc" to float(43f), "ev_range" to 60, "fuel_pct" to 55))
        assertEquals(70.0, b.soc!!, 0.0)
        assertEquals(60, b.evKm)
        assertEquals(560, b.totalKm)
        assertEquals(55, b.fuelPercent)
        val empty = CarReadouts.battery(null, null)
        assertFalse(empty.hasAny)
        assertNull(empty.totalKm)
    }

    // ── Janelas flutuantes ──────────────────────────────────────────────────

    @Test fun onlyOneWindowAtATime() {
        val p = PopoverState()
        assertNull(p.open)
        assertEquals(PopoverState.Id.NAV, p.toggle(PopoverState.Id.NAV))
        assertEquals(PopoverState.Id.MEDIA, p.toggle(PopoverState.Id.MEDIA)) // troca, nunca duas
        assertEquals(PopoverState.Id.MEDIA, p.open)
        assertNull(p.toggle(PopoverState.Id.MEDIA)) // mesmo atalho fecha
        p.show(PopoverState.Id.CLIMATE)
        assertEquals(PopoverState.Id.CLIMATE, p.show(PopoverState.Id.CLIMATE)) // mostrar não alterna
        assertTrue(p.close())
        assertFalse(p.close())
        assertNull(p.open)
    }

    @Test fun windowAnchorsOnItsTile() {
        // Tiles do desenho base: 4 colunas entre 32 e 1248 com 16 de vão.
        val tileW = (1216 - 48) / 4
        fun cx(i: Int) = 32 + i * (tileW + 16) + tileW / 2
        val nav = PopoverAnchor.place(cx(0), 470, 1280, 24, 40)
        assertEquals(24, nav.left) // encostaria na borda: para na margem
        assertEquals(cx(0) - 24, nav.caretX)
        val media = PopoverAnchor.place(cx(3), 540, 1280, 24, 40)
        assertEquals(1280 - 24 - 540, media.left)
        assertEquals(cx(3) - media.left, media.caretX)
        val mid = PopoverAnchor.place(640, 400, 1280, 24, 40)
        assertEquals(440, mid.left)
        assertEquals(200, mid.caretX)
        val wide = PopoverAnchor.place(100, 2000, 1280, 24, 40) // janela maior que a tela encolhe
        assertEquals(24, wide.left)
        assertEquals(76, wide.caretX)
        assertEquals(40, PopoverAnchor.place(30, 400, 1280, 24, 40).caretX) // seta longe do canto redondo
    }

    @Test fun homeThemeReclaimsTheStatusBarToFillTheScreen() {
        // issue #22: sem windowFullscreen o Android reserva a barra de status e a Home não
        // preenche a tela inteira; a barra nativa inferior do carro não é nossa, então não entra aqui.
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.exists() }
        val aura = File(root, "values/aura.xml").readText()
        val style = Regex("(?s)<style name=\"Theme\\.PainelLivre\\.Aura\"[^>]*>.*?</style>").find(aura)
            ?: error("Theme.PainelLivre.Aura não achado")
        assertTrue(style.value.contains("<item name=\"android:windowFullscreen\">true</item>"))
    }

    @Test fun screenScalesToTheBaseDrawing() {
        assertEquals(240, AuraScale.densityDpi(1920, 1080))
        assertEquals(160, AuraScale.densityDpi(1280, 720))
        assertEquals(240, AuraScale.densityDpi(1080, 1920)) // em pé: 720 × 1280
        assertEquals(228, AuraScale.densityDpi(1920, 1024)) // com barra do sistema: altura manda
        assertEquals(160, AuraScale.densityDpi(0, 0))
    }
}
