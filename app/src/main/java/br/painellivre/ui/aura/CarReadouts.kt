package br.painellivre.ui.aura

import br.painellivre.car.CarCommands
import br.painellivre.car.TelemetrySnapshot
import br.painellivre.car.VehicleData
import br.painellivre.ui.cockpit.CarOverlay

/**
 * **Kotlin puro.** O que a tela Aura mostra do carro, calculado da telemetria **somente leitura**
 * (issue #16) e do SDK BYD. Regra de ouro: sem dado não vira "fechado", "travado" nem zero — o chip
 * some ou diz "Sem dado". Estado nunca só por cor: cada chip tem ícone e palavra (design-system.md).
 */
object CarReadouts {

    /** Nível visual do chip: neutro (fechado), warn (aberto), accent (carregando), sem dado. */
    enum class Level { NEUTRAL, WARN, ACCENT, UNKNOWN }

    /** Ícone do chip: o nome do ícone Aura (res/drawable/au_*). */
    enum class Icon { DOOR, WINDOW, HOOD, TRUNK, PLUG, GAUGE }

    data class Chip(val id: String, val icon: Icon, val label: String, val value: String, val level: Level)

    private val DOOR_KEYS = listOf("door_fl", "door_fr", "door_rl", "door_rr")
    private val WINDOW_KEYS = listOf("win_fl", "win_fr", "win_rl", "win_rr")
    private val SIDE = mapOf("fl" to "Dianteira esq.", "fr" to "Dianteira dir.", "rl" to "Traseira esq.", "rr" to "Traseira dir.")
    private val SIDE_SHORT = mapOf("fl" to "Diant. esq.", "fr" to "Diant. dir.", "rl" to "Tras. esq.", "rr" to "Tras. dir.")

    /** Só vale a leitura que chegou bem. */
    private fun usable(s: TelemetrySnapshot?) = s?.takeIf { it.status == TelemetrySnapshot.Status.OK && it.hasData }

    /** Chips ao redor do carro, na ordem: Portas, Vidros, Capô, Porta-malas, Carregamento, Pneus. */
    fun chips(snapshot: TelemetrySnapshot?): List<Chip> {
        val s = usable(snapshot) ?: return emptyList()
        val out = mutableListOf<Chip>()
        door(s)?.let(out::add)
        windows(s)?.let(out::add)
        s.isOpen("hood")?.let { out += Chip("hood", Icon.HOOD, "Capô", if (it) "Aberto" else "Fechado", if (it) Level.WARN else Level.NEUTRAL) }
        s.isOpen("trunk")?.let { out += Chip("trunk", Icon.TRUNK, "Porta-malas", if (it) "Aberto" else "Fechado", if (it) Level.WARN else Level.NEUTRAL) }
        charge(s)?.let(out::add)
        tyres(s)?.let(out::add)
        return out
    }

    private fun door(s: TelemetrySnapshot): Chip? {
        val known = DOOR_KEYS.mapNotNull { k -> s.isOpen(k)?.let { k to it } }
        if (known.isEmpty()) return null
        val open = known.filter { it.second }.map { it.first.removePrefix("door_") }
        return when {
            open.size == 1 -> Chip("doors", Icon.DOOR, "Portas", "${SIDE.getValue(open[0])} aberta", Level.WARN)
            open.size > 1 -> Chip("doors", Icon.DOOR, "Portas", "${open.size} abertas", Level.WARN)
            known.size == DOOR_KEYS.size -> Chip("doors", Icon.DOOR, "Portas", "Fechadas", Level.NEUTRAL)
            else -> Chip("doors", Icon.DOOR, "Portas", "Sem dado", Level.UNKNOWN)
        }
    }

    private fun windows(s: TelemetrySnapshot): Chip? {
        val known = WINDOW_KEYS.mapNotNull { k -> s.value(k)?.let { k to it } }
        if (known.isEmpty()) return null
        val open = known.filter { it.second > 0 }
        return when {
            open.size == 1 -> Chip("windows", Icon.WINDOW, "Vidros",
                "${SIDE_SHORT.getValue(open[0].first.removePrefix("win_"))} ${AuraFormat.percent(open[0].second)}", Level.WARN)
            open.size > 1 -> Chip("windows", Icon.WINDOW, "Vidros", "${open.size} abertos", Level.WARN)
            known.size == WINDOW_KEYS.size -> Chip("windows", Icon.WINDOW, "Vidros", "Fechados", Level.NEUTRAL)
            else -> Chip("windows", Icon.WINDOW, "Vidros", "Sem dado", Level.UNKNOWN)
        }
    }

    private fun charge(s: TelemetrySnapshot): Chip? = when {
        s.charging == true -> Chip("charge", Icon.PLUG, "Carregamento",
            listOfNotNull("Carregando", s.socPercent?.let(AuraFormat::percent)).joinToString(" · "), Level.ACCENT)
        s.chargeLidOpen == true -> Chip("charge", Icon.PLUG, "Carregamento", "Tampa aberta", Level.WARN)
        s.plugged == true -> Chip("charge", Icon.PLUG, "Carregamento", "Cabo conectado", Level.NEUTRAL)
        s.chargeLidOpen == false -> Chip("charge", Icon.PLUG, "Carregamento", "Tampa fechada", Level.NEUTRAL)
        else -> null
    }

    private fun tyres(s: TelemetrySnapshot): Chip? {
        val state = CarOverlay.state(s)
        if (state.pressures.isEmpty()) return null
        val alerts = state.tyres.values.count { it == CarOverlay.Tyre.ALERT }
        return when {
            alerts == 1 -> Chip("tyres", Icon.GAUGE, "Pneus", "1 fora da faixa", Level.WARN)
            alerts > 1 -> Chip("tyres", Icon.GAUGE, "Pneus", "$alerts fora da faixa", Level.WARN)
            state.pressures.size == 4 -> Chip("tyres", Icon.GAUGE, "Pneus", "Na faixa", Level.NEUTRAL)
            else -> Chip("tyres", Icon.GAUGE, "Pneus", "Sem dado", Level.UNKNOWN)
        }
    }

    /** Linha de resumo do herói: "Tudo fechado" (ok), "2 aberturas" (warn) ou null (sem dado). */
    data class Summary(val text: String, val warn: Boolean)

    fun summary(snapshot: TelemetrySnapshot?): Summary? {
        val s = usable(snapshot) ?: return null
        val open = DOOR_KEYS.count { s.isOpen(it) == true } + WINDOW_KEYS.count { (s.value(it) ?: 0.0) > 0 } +
            listOf("hood", "trunk").count { s.isOpen(it) == true } +
            // Tampa aberta com o cabo carregando é o normal (o chip mostra "Carregando"), não abertura.
            (if (s.chargeLidOpen == true && s.charging != true) 1 else 0)
        if (open > 0) return Summary(if (open == 1) "1 abertura" else "$open aberturas", warn = true)
        val allKnown = TelemetrySnapshot.OPENING_KEYS.all { s.isOpen(it) == false }
        return if (allKnown) Summary("Tudo fechado", warn = false) else null
    }

    /** Trava: "Travado"/"Destravado" ou null (sem dado: o botão some). */
    fun lock(snapshot: TelemetrySnapshot?): String? = when (usable(snapshot)?.locked) {
        true -> "Travado"
        false -> "Destravado"
        null -> null
    }

    // ── Climatização (só mostra) ────────────────────────────────────────────

    /** Nível 0 desligado, 1… ligado (o carro manda 1 = desligado, 2 = nível 1; hipótese em TELEMETRIA.md). */
    data class Seat(val heat: Int?, val vent: Int?)

    data class Climate(
        val driverC: Double?,
        val passengerC: Double?,
        val fan: Int?,
        val insideC: Double?,
        val outsideC: Double?,
        /** Modos com leitura 0/1 (experimental). Null = sem leitura confiável: o botão some. */
        val auto: Boolean?,
        val ac: Boolean?,
        val recirc: Boolean?,
        val defrost: Boolean?,
        /** Energia geral do ar (issue #20: toggle sem diálogo). */
        val on: Boolean?,
        val driverSeat: Seat,
        val passengerSeat: Seat,
    ) {
        val hasAny get() = listOf(driverC, passengerC, fan, insideC, outsideC, auto, ac, recirc, defrost, on,
            driverSeat.heat, driverSeat.vent, passengerSeat.heat, passengerSeat.vent).any { it != null }

        /** Linha do atalho: "Automático · Ventilação 3 · A/C", só com o que foi lido. */
        val tileLine: String? get() = listOfNotNull(
            auto?.let { if (it) "Automático" else "Manual" },
            fan?.takeIf { it > 0 }?.let { "Ventilação $it" },
            ac?.takeIf { it }?.let { "A/C" },
        ).joinToString(" · ").ifEmpty { null }
    }

    fun climate(snapshot: TelemetrySnapshot?): Climate? {
        val s = usable(snapshot) ?: return null
        // O carro responde o valor cru do fio (ex.: AUTO manda 1=desliga/2=liga); decodificar com o
        // mesmo onWire de CarCommands antes de interpretar como ligado/desligado (bug visto no carro em 29/10).
        fun flag(key: String): Boolean? {
            val raw = s.value(key) ?: return null
            val spec = CarCommands.CATALOG.firstOrNull { it.key == key }
            val decoded = if (spec != null) CarCommands.fromWire(spec, raw) else raw
            return if (decoded == 1.0) true else if (decoded == 0.0) false else null
        }
        fun seat(key: String): Int? {
            val raw = s.value(key) ?: return null
            val spec = CarCommands.CATALOG.firstOrNull { it.key == key }
            // Fio 1=off, 2=nível1, 3=nível2 → lógico 0/1/2 via onWire (confirmado no carro 29/10).
            return if (spec != null && spec.onWire.isNotEmpty()) CarCommands.fromWire(spec, raw).toInt()
            else raw.toInt().let { (it - 1).coerceAtLeast(0) }
        }
        val c = Climate(
            driverC = s.value("ac_temp_driver"),
            passengerC = s.value("ac_temp_pass"),
            fan = s.value("ac_fan")?.toInt(),
            insideC = s.value("temp_in"),
            outsideC = s.value("temp_out"),
            auto = flag("ac_auto"),
            ac = flag("ac_compressor"),
            recirc = flag("ac_recirc"),
            defrost = flag("ac_defrost"),
            on = flag("ac_on"),
            driverSeat = Seat(seat("seat_heat_driver"), seat("seat_vent_driver")),
            passengerSeat = Seat(seat("seat_heat_pass"), seat("seat_vent_pass")),
        )
        return c.takeIf { it.hasAny }
    }

    // ── Bateria e autonomia (só mostra) ─────────────────────────────────────

    data class Battery(
        val soc: Double?,
        val evKm: Int?,
        val fuelKm: Int?,
        val fuelPercent: Int?,
        val volt12: Double?,
        val charge: String?,
        val charging: Boolean,
        val plugged: Boolean?,
        val remainingMin: Int?,
    ) {
        val totalKm: Int? get() = listOfNotNull(evKm, fuelKm).takeIf { it.isNotEmpty() }?.sum()
        val hasAny get() = soc != null || totalKm != null || fuelPercent != null
    }

    /** Linha da carga no formato Aura: "Carregando · 6,6 kW · 1h20" (o painel segue com TelemetryText.charge). */
    fun chargeLine(s: TelemetrySnapshot): String? = when {
        s.charging == true -> listOfNotNull(
            "Carregando",
            s.chargeKw?.let { String.format(java.util.Locale("pt", "BR"), "%.1f kW", it) },
            s.chargeRemainingMin?.let(AuraFormat::duration),
        ).joinToString(" · ")
        s.plugged == true -> "Cabo conectado, sem carregar"
        else -> null
    }

    /**
     * A leitura pelo ADB local (autoservice) vence. O SDK do uid do app costuma falhar nesta DiLink;
     * um zero só do SDK não esconde o valor do ADB nem vira "0%" sozinho.
     */
    fun battery(sdk: VehicleData.Snapshot?, snapshot: TelemetrySnapshot?): Battery {
        val t = usable(snapshot)
        return Battery(
            soc = prefer(t?.socPercent, sdk?.batteryPercent),
            evKm = prefer(t?.evRangeKm?.toDouble(), sdk?.evRangeKm?.toDouble())?.toInt(),
            fuelKm = prefer(t?.fuelRangeKm?.toDouble(), sdk?.fuelRangeKm?.toDouble())?.toInt(),
            fuelPercent = prefer(t?.value("fuel_pct"), sdk?.fuelPercent?.toDouble())?.toInt(),
            volt12 = t?.value("volt12"),
            charge = t?.let(::chargeLine),
            charging = t?.charging == true,
            plugged = t?.plugged,
            remainingMin = t?.chargeRemainingMin,
        )
    }

    /** Valor do ADB se houver; senão o do SDK, desde que seja maior que zero. */
    fun prefer(adb: Double?, sdk: Double?): Double? = when {
        adb != null -> adb
        sdk != null && sdk > 0.0 -> sdk
        else -> null
    }
}
