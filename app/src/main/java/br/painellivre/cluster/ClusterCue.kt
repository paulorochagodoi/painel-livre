package br.painellivre.cluster

import br.painellivre.nav.Maneuver
import br.painellivre.nav.NavState
import br.painellivre.nav.ParsedNav
import java.util.Locale

/**
 * **Kotlin puro.** O que o cartão do [br.painellivre.cluster.ClusterActivity] desenha a partir da
 * notificação do Maps ou do Waze: seta, distância e próxima manobra. O app de navegação fica na
 * central (issue #24).
 */
object ClusterCue {

    data class Card(
        val visible: Boolean,
        val source: String,
        val distance: String,
        val instruction: String,
        val street: String,
        val showDistance: Boolean,
        val showInstruction: Boolean,
        val etaClock: String,
        val etaTime: String,
        val etaDistance: String,
        val showEta: Boolean,
        val maneuver: Maneuver,
        val roundaboutExit: Int?,
        val simulated: Boolean,
    )

    fun from(state: NavState): Card {
        val nav = state.nav
        if (!state.active || nav == null) return HIDDEN
        val street = nav.street
        val instruction = if (street != null) shortInstruction(nav) else ""
        val showEta = nav.etaClock != null || nav.remainingSeconds != null || nav.remainingDistanceText != null
        return Card(
            visible = true,
            source = state.sourceLabel,
            distance = nav.distanceText ?: "",
            instruction = instruction,
            street = street ?: nav.instruction,
            showDistance = !nav.distanceText.isNullOrEmpty(),
            showInstruction = instruction.isNotEmpty(),
            etaClock = nav.etaClock ?: "—",
            etaTime = nav.remainingSeconds?.let(::formatDuration) ?: "—",
            etaDistance = nav.remainingDistanceText ?: "—",
            showEta = showEta,
            maneuver = nav.maneuver,
            roundaboutExit = nav.roundaboutExit,
            simulated = state.simulated,
        )
    }

    /** "Waze · Vire à direita" — a fonte deixa claro que a manobra veio da notificação. */
    fun instructionLine(card: Card): String =
        listOfNotNull(
            card.source.takeIf { it.isNotEmpty() },
            card.instruction.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")

    fun formatDuration(sec: Int): String {
        val hh = sec / 3600
        val mm = (sec % 3600) / 60
        return if (hh > 0) String.format(Locale.ROOT, "%d h %02d min", hh, mm) else "$mm min"
    }

    /** "Vire à direita", "Pegue a 2ª saída"… — o verbo da instrução, sem a rua. */
    fun shortInstruction(nav: ParsedNav): String {
        val instr = nav.instruction
        val street = nav.street ?: return instr
        if (instr == street) return ""
        val cut = instr.indexOf(street)
        val head = if (cut > 0) instr.substring(0, cut) else instr
        return head.trim().removeSuffix(" na").removeSuffix(" no").removeSuffix(" para").removeSuffix(" em").trim().trimEnd(',')
    }

    private val HIDDEN = Card(
        false, "", "", "", "", false, false, "—", "—", "—", false, Maneuver.UNKNOWN, null, false,
    )
}
