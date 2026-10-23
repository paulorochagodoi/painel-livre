package br.painellivre.cluster

/** Mesma tela do painel, mas aberta na central para conferir o visual (toque para fechar). */
class ClusterPreviewActivity : ClusterActivity() {
    override val isPreview: Boolean = true
}
