package br.painellivre.cluster

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Decisão automática separada do Android para testar o fim da rota sem esperar tempo real. */
class PanelAutomation(
    private val scope: CoroutineScope,
    private val mode: () -> String,
    private val restoreDelaySec: () -> Int,
    private val isActive: () -> Boolean,
    private val prepareNavigation: suspend () -> Unit,
    private val show: suspend () -> Unit,
    private val restore: suspend () -> Unit,
) {
    private var restoreJob: Job? = null

    suspend fun onStart() {
        if (mode() == "always") show()
    }

    suspend fun onNavigation(active: Boolean) {
        if (mode() != "nav") return
        if (active) {
            cancelRestore()
            prepareNavigation()
            if (!isActive()) show()
        } else if (isActive()) {
            cancelRestore()
            restoreJob = scope.launch {
                delay(restoreDelaySec() * 1000L)
                // O prazo venceu: restore() pode cancelar pendências sem cancelar a si próprio.
                restoreJob = null
                restore()
            }
        }
    }

    fun cancelRestore() { restoreJob?.cancel() }
}
