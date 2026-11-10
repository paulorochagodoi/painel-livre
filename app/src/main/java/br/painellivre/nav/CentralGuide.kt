package br.painellivre.nav

import br.painellivre.nav.native.GuideCue
import br.painellivre.nav.native.RouteTracker

/**
 * O que a central mostra durante uma rota, sem Android.
 *
 * Com o painel projetando (modos `nav` e `always`, projeção ativa) o mapa de lá continua
 * sendo o guia. Sem essa projeção — modo `manual`, ou o painel não subiu — a rota nativa
 * é conduzida na central: mapa, manobra em destaque e atalhos da Home e do dock.
 * Maps e Waze guiam no próprio app; no modo manual eles abrem na central porque a rota
 * não pede o painel.
 */
object CentralGuide {
    /** Inclinação do mapa enquanto a central segue o carro. */
    const val TILT = 40.0

    /**
     * A central conduz a rota nativa quando o motorista não está olhando o mapa no painel.
     * No modo manual a central guia mesmo que o painel tenha sido ligado à mão: foi escolha
     * dele não usar o painel como navegação automática.
     */
    fun guidesOnCentral(clusterMode: String, panelActive: Boolean, nativeRoute: Boolean): Boolean =
        nativeRoute && (clusterMode == "manual" || !panelActive)

    /** Com o guia na central, o atalho da Home abre o mapa em vez da janela. */
    fun homeOpensMap(guiding: Boolean) = guiding

    /** O botão de rota do dock troca o ícone pela seta da próxima manobra. */
    fun dockShowsManeuver(guiding: Boolean) = guiding

    /** A câmera segue o carro, com o rumo para cima, até o motorista arrastar o mapa. */
    fun followCamera(guiding: Boolean, userPanned: Boolean) = guiding && !userPanned

    /** Mais perto parado, um pouco mais longe em velocidade — a mesma ideia do painel. */
    fun zoomForSpeed(speedMps: Double): Double {
        val kmh = (speedMps * 3.6).coerceAtLeast(0.0)
        return when {
            kmh <= 20 -> 16.8
            kmh >= 110 -> 15.2
            else -> 16.8 - (kmh - 20) / 90.0 * 1.6
        }
    }

    /** Uma linha com as manobras depois da atual, para o banner da central. */
    fun nextLine(cues: List<GuideCue>): String = cues.joinToString("  ·  ") { cue ->
        val where = cue.street.ifBlank { cue.instruction }
        "${RouteTracker.meters(cue.meters)} $where"
    }
}
