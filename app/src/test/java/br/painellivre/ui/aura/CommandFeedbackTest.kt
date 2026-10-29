package br.painellivre.ui.aura

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CommandFeedbackTest {
    private val keys = listOf("media_play", "media_next", "media_prev", "media_volume")

    @Test fun routineMediaReadbacksStaySilent() {
        val messages = listOf("Volume relido: 8", "Estado relido da sessão: tocando",
            "Estado relido da sessão: pausado", "Sessão relida: Rádio; troca de faixa não confirmada",
            "Sem confirmação de sessão Android")
        keys.forEach { key -> messages.forEach { assertFalse("$key: $it", CommandFeedback.showMessage(key, it)) } }
    }

    @Test fun mediaFailuresAndRejectionsRemainVisible() {
        val messages = listOf("Áudio indisponível", "Recusado pelo Android: SecurityException",
            "Rejeitado: comandos desligados em Ajustes", "Rejeitado: comando desligado no Diagnóstico",
            "Rejeitado: aguarde um segundo", "Comando media_play = 1.0: falha IllegalStateException; não repetido")
        keys.forEach { key -> messages.forEach { assertTrue("$key: $it", CommandFeedback.showMessage(key, it)) } }
    }

    @Test fun climateFeedbackIsUnchanged() {
        assertFalse(CommandFeedback.isMedia("ac_on"))
        assertTrue(CommandFeedback.showMessage("ac_on", "Confirmado pelo estado relido"))
        assertTrue(CommandFeedback.showMessage("ac_on", "Não confirmado pelo carro (sem dado)"))
    }

    @Test fun launcherUsesFeedbackPolicyAndKeepsMediaOutOfClimateStatus() {
        val path = "src/main/java/br/painellivre/ui/LauncherActivity.kt"
        val src = listOf(File(path), File("app/$path")).first { it.exists() }.readText()
        val command = src.substringAfter("private fun command(key:").substringBefore("private fun chooseCommand")
        assertTrue(command.contains("CommandFeedback.showMessage(key, message)"))
        assertTrue(command.contains("val media = br.painellivre.ui.aura.CommandFeedback.isMedia(key)"))
        val climateWrites = command.lines().filter { it.contains("popClimateState") }
        assertEquals(2, climateWrites.size)
        assertTrue(climateWrites.all { it.trim().startsWith("if (!media)") })
        assertTrue(command.contains("renderVolume()"))
    }
}
