package br.painellivre.ui.cockpit

/**
 * O que a silhueta do carro sabe desenhar. `null` é desconhecido: o desenho não inventa
 * "fechado" nem "aberto". A lateral visível é a do motorista (frente para a direita).
 */
data class CarVisual(
    val frontDoorOpen: Boolean? = null,
    val rearDoorOpen: Boolean? = null,
    val farDoorOpen: Boolean? = null,
    val trunkOpen: Boolean? = null,
    val locked: Boolean? = null,
    val chargePortOpen: Boolean? = null,
    val charging: Boolean? = null,
)
