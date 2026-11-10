package br.painellivre.ui.aura

/** A mídia já mostra seu estado nos controles; releituras não precisam cobrir a tela (#21). */
object CommandFeedback {
    fun isMedia(key: String) = key.startsWith("media_")

    fun showMessage(key: String, message: String): Boolean = !isMedia(key) || !(
        message.startsWith("Volume relido: ") ||
        message.startsWith("Estado relido da sessão: ") ||
        message.startsWith("Sessão relida: ") ||
        message == "Sem confirmação de sessão Android"
    )
}
