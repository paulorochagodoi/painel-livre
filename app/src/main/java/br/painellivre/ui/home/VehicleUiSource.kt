package br.painellivre.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * De onde a tela inicial lê o carro. A implementação de hoje é um exemplo fixo.
 * A telemetria real (outro branch) entra trocando só [VehicleUiSources.forHome]:
 * a Activity observa [state] e não sabe quem mediu.
 */
interface VehicleUiSource {
    val state: StateFlow<VehicleUiState>
}

/** Fonte de exemplo. Não consulta SDK, ADB nem CAN. */
class SampleVehicleUiSource(
    initial: VehicleUiState = VehicleUiState.sample(),
) : VehicleUiSource {
    override val state: StateFlow<VehicleUiState> = MutableStateFlow(initial)
}

object VehicleUiSources {
    /**
     * Ponto único de troca. Quando a leitura real existir, devolva essa fonte aqui
     * (com `sample = false`). Não ligue comando de carro neste método.
     */
    fun forHome(): VehicleUiSource = SampleVehicleUiSource()
}
