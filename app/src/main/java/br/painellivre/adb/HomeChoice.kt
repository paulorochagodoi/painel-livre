package br.painellivre.adb

/**
 * **Kotlin puro.** Decisões sobre a tela inicial (launcher), separadas do Android para teste na JVM.
 *
 * Issue #7 ("não é reconhecido como um launcher"): até a v0.3.0 o filtro HOME ficava num alias
 * desligado no manifesto, então nem o Android nem a BYD listavam o Painel Livre como tela inicial; e o
 * botão "Usar como tela inicial" podia guardar o seletor do Android (`ResolverActivity`) como "launcher
 * original", que depois não dá para restaurar.
 */
object HomeChoice {

    /** O que `cmd package resolve-activity --brief … HOME` respondeu. */
    sealed class Resolved {
        /** O Painel Livre já abre no botão Início. */
        object Ours : Resolved()
        /** Outro launcher é o preferido (ex.: o da BYD). */
        data class Other(val component: String) : Resolved()
        /** Mais de um launcher e nenhum preferido: o Android mostra o seletor ao tocar em Início. */
        object Chooser : Resolved()
        /** Saída vazia, erro do ADB ou formato desconhecido. */
        object Unknown : Resolved()
    }

    private val CHOOSERS = listOf("ResolverActivity", "ChooserActivity", "/com.android.internal.app.")

    /** Último `pacote/Classe` da saída (as linhas de cima são prioridade e filtro). */
    fun componentFrom(output: String?): String? = output.orEmpty().lines().map { it.trim() }
        .lastOrNull { line ->
            line.contains('/') && !line.startsWith("ERRO") && !line.contains(' ') &&
                !line.startsWith("No activity", ignoreCase = true)
        }

    fun parseResolved(output: String?, ourPackage: String): Resolved {
        val c = componentFrom(output) ?: return Resolved.Unknown
        return when {
            CHOOSERS.any { c.contains(it) } -> Resolved.Chooser
            c.substringBefore('/') == ourPackage -> Resolved.Ours
            else -> Resolved.Other(c)
        }
    }

    /** Só vale guardar como "launcher original" um launcher de verdade que não seja o nosso. */
    fun originalToSave(resolved: Resolved): String? = (resolved as? Resolved.Other)?.component

    /** `cmd package set-home-activity` responde "Success"; erro vem como "Error:"/"Exception"/"Unknown command". */
    fun setHomeSucceeded(output: String?): Boolean {
        val t = output.orEmpty()
        if (listOf("Error", "Exception", "Unknown", "ERRO", "denied").any { t.contains(it, ignoreCase = true) }) return false
        return t.contains("Success", ignoreCase = true)
    }

    /** Próximo passo para virar a tela inicial. */
    enum class Step {
        /** Já é a tela inicial: nada a fazer. */
        DONE,
        /** Pelo ADB local (`set-home-activity`), sem perguntar nada ao motorista. */
        ADB,
        /** Sem ADB: abrir a tela "Tela inicial" do Android, se a BYD não a tiver removido. */
        HOME_SETTINGS,
        /** Último recurso: tocar em Início pelo app; o Android mostra o seletor com "Sempre". */
        CHOOSER,
    }

    fun nextStep(current: Resolved, adbOk: Boolean, hasHomeSettings: Boolean): Step = when {
        current is Resolved.Ours -> Step.DONE
        adbOk -> Step.ADB
        hasHomeSettings -> Step.HOME_SETTINGS
        else -> Step.CHOOSER
    }

    /** Ao voltar ao launcher da BYD: com o original conhecido, define-o; sem ele, esconde o nosso filtro HOME. */
    fun restoreTarget(original: String?, ourPackage: String): String? =
        original?.takeIf { it.contains('/') && it.substringBefore('/') != ourPackage && CHOOSERS.none { c -> it.contains(c) } }
}
