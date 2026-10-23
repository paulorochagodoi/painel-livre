package br.painellivre.car

/**
 * **Kotlin puro.** Lista fechada dos dados do carro que o Painel Livre lê pelo `autoservice`
 * (issue #16). Tabela completa, fontes e confiança em docs/TELEMETRIA.md — mudou aqui, mude lá.
 *
 * Cada sinal tem o endereço padrão (dispositivo + fid) e, quando se conhece, o nome no catálogo da
 * própria central (`BYDAutoFeatureIds$Grupo.NOME`). Na central, o nome resolvido por reflexão vence o
 * padrão: a BYD calcula alguns ids conforme o protocolo (CAN ou CAN-FD) na hora de carregar a classe.
 * Nomes terminados em `_SET` (escrita) nunca entram aqui.
 */
object TelemetryCatalog {

    /** De onde vem o significado do id. */
    enum class Confidence(val label: String) {
        /** Lido no carro do dono e bate com o que aparece na tela. */
        CAR("confirmado no carro"),
        /** Nome no catálogo BYD e medição pública em outro BYD (fontes em docs/TELEMETRIA.md). */
        PUBLIC("confirmado em outro BYD"),
        /** Só o rótulo do SealNav (referência de interoperabilidade); significado a confirmar. */
        HYPOTHESIS("hipótese"),
    }

    /** Como transformar a palavra da resposta no valor (e o que é plausível). */
    enum class Kind(val float: Boolean = false) {
        OPEN_FLAG,          // 0 fechado, 1 aberto
        LOCK,               // 1 destrancado, 2 trancado (hipótese)
        GUN,                // 1 nenhum, 2 AC, 3 DC, 4 AC+DC, 5 V2L
        BMS,                // 1 carregando, 2 terminou, 13 pausado
        PERCENT_FLOAT(true),
        PERCENT,
        KM,
        TEMP,               // °C
        TEMP_SET,           // °C ajustado no ar-condicionado
        KPA,
        KW_FLOAT(true),
        KW,
        HOURS,
        MINUTES,
        VOLT_FLOAT(true),
        GEAR,               // 1 P, 2 R, 3 N, 4 D
        SEAT_LEVEL,         // 1 desligado, 2… nível (hipótese)
        LEVEL,
        RAW,
    }

    enum class Group(val label: String) {
        BODY("Portas e trava"), CHARGE("Carga"), BATTERY("Bateria e autonomia"), CLIMATE("Ar-condicionado"),
        SEATS("Bancos"), WINDOWS("Vidros e teto"), TYRES("Pneus"), DRIVE("Condução"), PROBE("Só no debug"),
    }

    data class Signal(
        val key: String,
        val label: String,
        val group: Group,
        val dev: Int,
        val fid: Int,
        val tx: Int,
        val kind: Kind,
        val confidence: Confidence,
        /** `Grupo.NOME` em `BYDAutoFeatureIds`, se conhecido. */
        val symbol: String? = null,
        /** Endereço alternativo quando a central diz `ro.car.protocol=CAN` (o padrão é CAN-FD). */
        val canFid: Int? = null,
        /** false = só na leitura completa do modo de debug. */
        val poll: Boolean = true,
    ) {
        fun fidFor(protocol: Autoservice.Protocol) = if (protocol == Autoservice.Protocol.CAN && canFid != null) canFid else fid
        fun command(fid: Int = this.fid) = Autoservice.command(tx, dev, fid)
    }

    private const val AC = 1000
    private const val BODY = 1001
    private const val ENGINE = 1012
    private const val INSTRUMENT = 1007
    private const val CHARGING = 1009
    private const val GEARBOX = 1011
    private const val STATISTIC = 1014
    private const val TYRE = 1016
    private const val SETTING = 1023
    private const val INT = Autoservice.TX_GET_INT
    private const val FLOAT = Autoservice.TX_GET_FLOAT

    private val C = Confidence.PUBLIC
    private val H = Confidence.HYPOTHESIS

    val SIGNALS: List<Signal> = listOf(
        // Portas: 0 fechada, 1 aberta. Na mão esquerda (Brasil) "dianteira esquerda" = motorista.
        Signal("door_fl", "Porta dianteira esquerda", Group.BODY, BODY, 0x29400008, INT, Kind.OPEN_FLAG, C, "Bodywork.BODYWORK_LEFT_HAND_FRONT_DOOR"),
        Signal("door_fr", "Porta dianteira direita", Group.BODY, BODY, 0x2940000A, INT, Kind.OPEN_FLAG, C, "Bodywork.BODYWORK_RIGHT_HAND_FRONT_DOOR"),
        Signal("door_rl", "Porta traseira esquerda", Group.BODY, BODY, 0x2940000C, INT, Kind.OPEN_FLAG, C, "Bodywork.BODYWORK_LEFT_HAND_REAR_DOOR"),
        Signal("door_rr", "Porta traseira direita", Group.BODY, BODY, 0x2940000E, INT, Kind.OPEN_FLAG, C, "Bodywork.BODYWORK_RIGHT_HAND_REAR_DOOR"),
        Signal("hood", "Capô", Group.BODY, BODY, 0x2940001C, INT, Kind.OPEN_FLAG, C, "Bodywork.BODYWORK_HOOD"),
        Signal("trunk", "Porta-malas", Group.BODY, BODY, 0x2940001A, INT, Kind.OPEN_FLAG, C, "Bodywork.BODYWORK_LUGGAGE_DOOR"),
        Signal("lock", "Trava das portas", Group.BODY, BODY, 0x40700008, INT, Kind.LOCK, H),
        Signal("charge_lid", "Tampa do carregador", Group.BODY, BODY, 0x3D902022, INT, Kind.OPEN_FLAG, H),

        Signal("gun", "Cabo de carga", Group.CHARGE, CHARGING, 0x34400032, INT, Kind.GUN, C, "Charging.CHARGING_GUN_CONNECT_STATE"),
        Signal("bms", "Estado da carga", Group.CHARGE, CHARGING, 0x34400018, INT, Kind.BMS, C, "Charging.CHARGING_BATTERRY_DEVICE_STATE", canFid = 876611608),
        Signal("charge_kw", "Potência de carga", Group.CHARGE, CHARGING, 0x32300018, FLOAT, Kind.KW_FLOAT, C, "Instrument.CHARGING_CHARGE_POWER_DD"),
        Signal("charge_h", "Tempo restante (horas)", Group.CHARGE, CHARGING, 0x32300028, INT, Kind.HOURS, C, "Instrument.CHARGING_CHARGE_REST_HOUR_DD"),
        Signal("charge_min", "Tempo restante (minutos)", Group.CHARGE, CHARGING, 0x32300030, INT, Kind.MINUTES, C, "Instrument.CHARGING_CHARGE_REST_MINUTE_DD"),

        Signal("soc", "Bateria", Group.BATTERY, STATISTIC, 0x4A505038, FLOAT, Kind.PERCENT_FLOAT, C, "Statistic.STATISTIC_ELEC_PERCENTAGE", canFid = 1033543720),
        Signal("ev_range", "Autonomia elétrica", Group.BATTERY, STATISTIC, 0x4A50203E, INT, Kind.KM, C, "Statistic.STATISTIC_ELEC_DRIVING_RANGE", canFid = 1033203771),
        Signal("fuel_range", "Autonomia a combustível", Group.BATTERY, STATISTIC, 0x4A504038, INT, Kind.KM, C, "Statistic.STATISTIC_FUEL_DRIVING_RANGE"),
        Signal("fuel_pct", "Combustível", Group.BATTERY, STATISTIC, 0x4A507040, INT, Kind.PERCENT, C, "Statistic.STATISTIC_FUEL_PERCENTAGE"),
        Signal("volt12", "Bateria 12 V", Group.BATTERY, BODY, 0x43400028, FLOAT, Kind.VOLT_FLOAT, C),

        Signal("ac_temp_driver", "Temperatura do motorista", Group.CLIMATE, AC, 0x40400028, INT, Kind.TEMP_SET, C),
        Signal("ac_temp_pass", "Temperatura do passageiro", Group.CLIMATE, AC, 0x40400030, INT, Kind.TEMP_SET, C),
        Signal("temp_out", "Temperatura externa", Group.CLIMATE, AC, 0x40400038, INT, Kind.TEMP, C),
        Signal("temp_in", "Temperatura interna", Group.CLIMATE, AC, 0x3D800030, INT, Kind.TEMP, C, "Ac.AC_TEMP_INSIDE"),
        Signal("ac_on", "Ar-condicionado ligado", Group.CLIMATE, AC, 0x40400010, INT, Kind.RAW, H),
        Signal("ac_fan", "Ventilador", Group.CLIMATE, AC, 0x4040001C, INT, Kind.LEVEL, H),
        Signal("ac_recirc", "Recirculação", Group.CLIMATE, AC, 0x40400014, INT, Kind.RAW, H),
        Signal("ac_mode", "Saída do ar", Group.CLIMATE, AC, 0x40400018, INT, Kind.RAW, H),
        Signal("ac_compressor", "Compressor (A/C)", Group.CLIMATE, AC, 0x40400009, INT, Kind.RAW, H),
        Signal("ac_auto", "Modo automático", Group.CLIMATE, AC, 0x40400012, INT, Kind.RAW, H),
        Signal("ac_defrost", "Desembaçador dianteiro", Group.CLIMATE, AC, 0x40400016, INT, Kind.RAW, H),

        // O "SET_" destes nomes é do grupo Setting (estado lido), não um id de escrita.
        Signal("seat_heat_driver", "Aquecimento do banco do motorista", Group.SEATS, SETTING, 0x4FA0000B, INT, Kind.SEAT_LEVEL, C, "Setting.SET_DRIVER_SEAT_HEATING_STATE"),
        Signal("seat_vent_driver", "Ventilação do banco do motorista", Group.SEATS, SETTING, 0x4FA00008, INT, Kind.SEAT_LEVEL, C, "Setting.SET_DRIVER_SEAT_VENTILATING_STATE"),
        Signal("seat_heat_pass", "Aquecimento do banco do passageiro", Group.SEATS, SETTING, 0x4FA00013, INT, Kind.SEAT_LEVEL, C, "Setting.SET_PASSENGER_SEAT_HEATING_STATE"),
        Signal("seat_vent_pass", "Ventilação do banco do passageiro", Group.SEATS, SETTING, 0x4FA00010, INT, Kind.SEAT_LEVEL, C, "Setting.SET_PASSENGER_SEAT_VENTILATING_STATE"),

        Signal("win_fl", "Vidro dianteiro esquerdo", Group.WINDOWS, BODY, 0x38800018, INT, Kind.PERCENT, H),
        Signal("win_fr", "Vidro dianteiro direito", Group.WINDOWS, BODY, 0x4B900010, INT, Kind.PERCENT, H),
        Signal("win_rl", "Vidro traseiro esquerdo", Group.WINDOWS, BODY, 0x38800020, INT, Kind.PERCENT, H),
        Signal("win_rr", "Vidro traseiro direito", Group.WINDOWS, BODY, 0x38800030, INT, Kind.PERCENT, H),
        Signal("sunroof", "Teto solar", Group.WINDOWS, BODY, 0x41A00030, INT, Kind.PERCENT, H),
        Signal("sunshade", "Cortina do teto", Group.WINDOWS, BODY, 0x41A00010, INT, Kind.PERCENT, C, "Body.BODYWORK_SUNSHADE_PANEL_PERCENT"),

        Signal("tyre_p_fl", "Pneu dianteiro esquerdo", Group.TYRES, TYRE, 0x99000124.toInt(), INT, Kind.KPA, C),
        Signal("tyre_p_fr", "Pneu dianteiro direito", Group.TYRES, TYRE, 0x99000128.toInt(), INT, Kind.KPA, C),
        Signal("tyre_p_rl", "Pneu traseiro esquerdo", Group.TYRES, TYRE, 0x9900012C.toInt(), INT, Kind.KPA, C),
        Signal("tyre_p_rr", "Pneu traseiro direito", Group.TYRES, TYRE, 0x99000130.toInt(), INT, Kind.KPA, C),
        Signal("tyre_t_fl", "Temperatura do pneu dianteiro esquerdo", Group.TYRES, INSTRUMENT, 0x4A50A018, INT, Kind.TEMP, C),
        Signal("tyre_t_fr", "Temperatura do pneu dianteiro direito", Group.TYRES, INSTRUMENT, 0x4A50A024, INT, Kind.TEMP, C),
        Signal("tyre_t_rl", "Temperatura do pneu traseiro esquerdo", Group.TYRES, INSTRUMENT, 0x4A50A030, INT, Kind.TEMP, C),
        Signal("tyre_t_rr", "Temperatura do pneu traseiro direito", Group.TYRES, INSTRUMENT, 0x4A50A03C, INT, Kind.TEMP, C),

        Signal("gear", "Câmbio", Group.DRIVE, GEARBOX, 0x21200038, INT, Kind.GEAR, Confidence.CAR, "Gearbox.GEARBOX_AUTO_MODE_TYPE"),
        Signal("power_kw", "Potência da bateria", Group.DRIVE, ENGINE, 0x14400020, INT, Kind.KW, C, "Engine.ENGINE_POWER"),

        // Só na leitura completa do debug: candidatos sem significado claro, para comparar no carro.
        Signal("probe_roof_b", "Teto (alternativo)", Group.PROBE, BODY, 0x43100008, INT, Kind.RAW, H, poll = false),
        Signal("probe_shade_b", "Cortina (alternativo)", Group.PROBE, BODY, 0x4310000C, INT, Kind.RAW, H, poll = false),
        Signal("probe_win_a", "Vidro (alternativo A)", Group.PROBE, BODY, 0x38800010, INT, Kind.RAW, H, poll = false),
        Signal("probe_win_b", "Vidro (alternativo B)", Group.PROBE, BODY, 0x38800028, INT, Kind.RAW, H, poll = false),
        Signal("probe_win_c", "Vidro (alternativo C)", Group.PROBE, BODY, 0x4B900018, INT, Kind.RAW, H, poll = false),
        Signal("probe_power_state", "Estado de energia (1023)", Group.PROBE, SETTING, 0x12D00020, INT, Kind.RAW, H, poll = false),
        Signal("probe_setting_e", "Ajuste 0x4290000e", Group.PROBE, SETTING, 0x4290000E, INT, Kind.RAW, H, poll = false),
        Signal("probe_belt", "Cinto do passageiro", Group.PROBE, INSTRUMENT, 0x2610001B, INT, Kind.RAW, H, poll = false),
        Signal("probe_trunk_instr", "Porta-malas (painel)", Group.PROBE, INSTRUMENT, 0x26100018, INT, Kind.RAW, H, poll = false),
        Signal("probe_charge_lid_instr", "Aviso da tampa de carga (painel)", Group.PROBE, INSTRUMENT, 0x3D902022, INT, Kind.RAW, H, poll = false),
        Signal("probe_cap_dl3", "Tampa de carga (catálogo DL3)", Group.PROBE, BODY, 0x2400003A, INT, Kind.RAW, H, poll = false),
        Signal("probe_cap_ac", "Tampa de carga AC", Group.PROBE, BODY, 0x4FB00020, INT, Kind.RAW, H, poll = false),
        Signal("probe_cap_dc", "Tampa de carga DC", Group.PROBE, BODY, 0x4FB00018, INT, Kind.RAW, H, poll = false),
        Signal("probe_gun_songplus", "Cabo de carga (Song Plus)", Group.PROBE, CHARGING, 0x34400832, INT, Kind.RAW, H, poll = false),
        Signal("probe_soc_bms", "Carga do BMS (x10)", Group.PROBE, STATISTIC, 0x44700028, INT, Kind.RAW, H, poll = false),
    )

    val POLLED = SIGNALS.filter { it.poll }

    fun byKey(key: String) = SIGNALS.first { it.key == key }

    /**
     * Endereço a usar: o id resolvido pelo nome no catálogo da central, se houver e não for de
     * escrita; senão o padrão para o protocolo. Função pura para o teste.
     */
    fun resolveFid(signal: Signal, protocol: Autoservice.Protocol, resolved: Int?): Int =
        resolved?.takeIf { it != 0 && signal.symbol?.endsWith("_SET") != true } ?: signal.fidFor(protocol)
}
