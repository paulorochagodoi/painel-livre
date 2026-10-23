package br.painellivre.adb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import br.painellivre.nav.NavNotificationListener
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs

/**
 * Ajustes que a central da BYD nem sempre deixa fazer pela interface (faltam telas de
 * "acesso a notificações", "launcher padrão" etc.). Tudo feito pelo ADB local, sempre a pedido
 * do usuário por um botão — nada disso roda sozinho.
 */
object SystemSetup {

    private val BYD_PERMISSIONS = listOf(
        "android.permission.BYDAUTO_STATISTIC_GET",
        "android.permission.BYDAUTO_BODYWORK_GET",
        "android.permission.BYDAUTO_CHARGING_GET",
        "android.permission.BYDAUTO_SPEED_GET",
        "android.permission.BYDAUTO_INSTRUMENT_GET",
    )

    private val FAIL_HINTS = listOf("Exception", "Error", "Unknown", "not found", "denied")

    fun listenerComponent(ctx: Context) = ComponentName(ctx, NavNotificationListener::class.java)

    fun hasNotificationAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it) == listenerComponent(ctx) }
    }

    /** Concede localização, permissões BYD (leitura) e acesso às notificações. */
    suspend fun grantAll(ctx: Context): String {
        val pkg = ctx.packageName
        val cmds = buildList {
            add("pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
            add("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")
            BYD_PERMISSIONS.forEach { add("pm grant $pkg $it") }
            // Discador (issue #15): ligar direto pelo celular e achar a conta do Bluetooth.
            add("pm grant $pkg android.permission.CALL_PHONE")
            add("pm grant $pkg android.permission.READ_PHONE_STATE")
            add("cmd notification allow_listener ${listenerComponent(ctx).flattenToString()}")
            add("dumpsys deviceidle whitelist +$pkg")
            // Rodar em segundo plano sem ser encerrado (ajuda a subir com o carro e a manter o painel).
            add("cmd appops set $pkg RUN_IN_BACKGROUND allow")
            add("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
            add("am set-standby-bucket $pkg active")
        }
        // Testa a conexão uma vez antes (é aqui que a central pede "Permitir depuração USB").
        val probe = SelfAdb.shell(ctx, "echo ok")
        if (probe is SelfAdb.Result.Error) return "ERRO: ${probe.message}"
        val out = StringBuilder()
        for (c in cmds) {
            val r = SelfAdb.shell(ctx, "$c 2>&1")
            val text = when (r) {
                is SelfAdb.Result.Ok -> r.output
                is SelfAdb.Result.Error -> "ERRO: ${r.message}"
            }
            // Só a linha que interessa (o pm despeja a stack trace inteira quando recusa).
            val brief = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && it != "Exception occurred while executing:" }
            val failed = r !is SelfAdb.Result.Ok || r.exitCode != 0 ||
                FAIL_HINTS.any { text.contains(it, ignoreCase = true) }
            out.append(if (failed) "✗ " else "✓ ").append(c)
            if (failed && brief != null) out.append("\n    ").append(brief.take(160))
            out.append('\n')
        }
        AppLog.i("Conceder permissões:\n$out")
        return out.toString()
    }

    // ── Launcher padrão ─────────────────────────────────────────────────────
    // v0.4.1: o filtro HOME (alias `HomeAlias`) vem desligado no manifesto, porque a central da BYD
    // recusou instalar a v0.4.0, que o trazia ligado ("Fail in installation of desktop apps").
    // "Usar como tela inicial" liga o alias em tempo de execução e escolhe o preferido; as decisões
    // ficam em `HomeChoice` (testadas na JVM). Nada liga o alias sozinho.

    private fun homeAlias(ctx: Context) = ComponentName(ctx.packageName, "${ctx.packageName}.ui.HomeAlias")

    private const val RESOLVE_HOME =
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

    suspend fun currentHome(ctx: Context): HomeChoice.Resolved =
        HomeChoice.parseResolved(SelfAdb.shellText(ctx, "$RESOLVE_HOME 2>&1"), ctx.packageName)

    private fun aliasOn(ctx: Context) = HomeChoice.aliasOn(ctx.packageManager.getComponentEnabledSetting(homeAlias(ctx)))

    // O próprio app pode ligar/desligar componentes seus, sem ADB nem permissão extra.
    private fun setAlias(ctx: Context, enabled: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            homeAlias(ctx),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Resultado de [becomeHome]: texto para o usuário e, sem ADB, a tela que a interface deve abrir. */
    data class HomeResult(val message: String, val fallback: HomeChoice.Step? = null)

    suspend fun becomeHome(ctx: Context): HomeResult {
        val prefs = Prefs(ctx)
        val wasOn = aliasOn(ctx)
        val adbOk = SelfAdb.shell(ctx, "echo ok") is SelfAdb.Result.Ok
        // Lido antes de ligar o alias: com ele desligado a BYD é a única tela inicial e sai como
        // "original"; depois de ligado o Android poderia responder com o seletor.
        val cur = if (adbOk) currentHome(ctx) else HomeChoice.Resolved.Unknown
        HomeChoice.originalToSave(cur)?.let { prefs.originalHome = it }
        val hasSettings = Intent(Settings.ACTION_HOME_SETTINGS).resolveActivity(ctx.packageManager) != null
        val step = HomeChoice.nextStep(cur, adbOk, hasSettings, wasOn)
        if (HomeChoice.mustEnableAlias(step, wasOn)) {
            setAlias(ctx, true)
            AppLog.i("Alias HOME ligado a pedido do motorista")
        }
        return when (step) {
            HomeChoice.Step.DONE -> HomeResult("O Painel Livre já é a tela inicial.")
            HomeChoice.Step.ADB -> {
                val r = SelfAdb.shellText(ctx, "cmd package set-home-activity ${homeAlias(ctx).flattenToString()} 2>&1")
                val after = currentHome(ctx)
                AppLog.i("Usar como tela inicial (antes: $cur, depois: $after): $r")
                when {
                    after is HomeChoice.Resolved.Ours ->
                        HomeResult("Pronto: o botão Início abre o Painel Livre.\nLauncher anterior: ${prefs.originalHome ?: "?"}")
                    HomeChoice.setHomeSucceeded(r) ->
                        HomeResult("A central aceitou o pedido, mas ainda responde com outro launcher ($after). Toque em Início para conferir.\n$r")
                    else -> HomeResult(
                        "A central recusou pelo ADB. Escolha o Painel Livre e toque em \"Sempre\".\n$r",
                        if (hasSettings) HomeChoice.Step.HOME_SETTINGS else HomeChoice.Step.CHOOSER,
                    )
                }
            }
            else -> {
                AppLog.i("Usar como tela inicial sem ADB: $step")
                HomeResult("Sem ADB local. Escolha o Painel Livre na lista e toque em \"Sempre\".", step)
            }
        }
    }

    suspend fun restoreHome(ctx: Context): String {
        val orig = HomeChoice.restoreTarget(Prefs(ctx).originalHome, ctx.packageName)
        val r = if (orig != null) SelfAdb.shellText(ctx, "cmd package set-home-activity $orig 2>&1") else "launcher original desconhecido"
        // Sem o original (ou se a central recusou), some com o nosso filtro HOME: a BYD volta a ser a única.
        val hide = orig == null || !HomeChoice.setHomeSucceeded(r)
        if (hide) setAlias(ctx, false)
        AppLog.i("Voltar ao launcher BYD ($orig, alias desligado=$hide): $r")
        return "Voltando para: ${orig ?: "launcher da BYD"}\n$r" +
            if (hide) "\nO Painel Livre deixou de aparecer como tela inicial (\"Usar como tela inicial\" religa)." else ""
    }
}
