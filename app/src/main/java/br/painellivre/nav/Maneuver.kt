package br.painellivre.nav

/**
 * Tipos de manobra que o painel sabe desenhar.
 *
 * [angle] é usado pelo ManeuverView para desenhar a seta (0 = em frente, negativo = esquerda).
 * [bydCanIcon] é o id de ícone que o MCU do painel/HUD da BYD entende nos registradores de
 * orientação via CAN (`INSTRUMENT_GUIDE_SIMPLE`, tabela levantada na engenharia reversa do
 * DashCast/AmapService). Ainda não é enviado ao CAN por este app — fica aqui para a fase 2 (HUD).
 */
enum class Maneuver(val angle: Float, val bydCanIcon: Int) {
    STRAIGHT(0f, 11),
    SLIGHT_LEFT(-45f, 3),
    SLIGHT_RIGHT(45f, 5),
    LEFT(-90f, 1),
    RIGHT(90f, 2),
    SHARP_LEFT(-135f, 7),
    SHARP_RIGHT(135f, 8),
    KEEP_LEFT(-30f, 4),
    KEEP_RIGHT(30f, 6),
    EXIT_LEFT(-35f, 14),
    EXIT_RIGHT(35f, 13),
    MERGE(25f, 5),
    UTURN_LEFT(-180f, 9),
    UTURN_RIGHT(180f, 10),
    ROUNDABOUT(0f, 35),
    DESTINATION(0f, 48),
    FERRY(0f, 11),
    TOLL(0f, 47),
    UNKNOWN(0f, -1);

    val isLeft get() = angle < 0
}
