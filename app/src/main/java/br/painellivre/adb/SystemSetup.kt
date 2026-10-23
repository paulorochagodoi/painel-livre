package br.painellivre.adb

import android.content.ComponentName
import android.content.Context
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

    private fun homeAlias(ctx: Context) = ComponentName(ctx.packageName, "${ctx.packageName}.ui.HomeAlias")

    suspend fun currentHome(ctx: Context): String? {
        val out = SelfAdb.shellText(
            ctx, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        )
        return out.lines().map { it.trim() }.lastOrNull { it.contains('/') && !it.startsWith("ERRO") }
    }

    suspend fun becomeHome(ctx: Context): String {
        val prefs = Prefs(ctx)
        val cur = currentHome(ctx)
        if (cur != null && !cur.startsWith(ctx.packageName)) prefs.originalHome = cur
        ctx.packageManager.setComponentEnabledSetting(
            homeAlias(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        val r = SelfAdb.shellText(ctx, "cmd package set-home-activity ${homeAlias(ctx).flattenToString()} 2>&1")
        AppLog.i("Usar como tela inicial (anterior: $cur): $r")
        return "Launcher anterior salvo: ${cur ?: "?"}\n$r"
    }

    suspend fun restoreHome(ctx: Context): String {
        val orig = Prefs(ctx).originalHome
        val r = if (orig != null) SelfAdb.shellText(ctx, "cmd package set-home-activity $orig 2>&1") else "launcher original desconhecido"
        ctx.packageManager.setComponentEnabledSetting(
            homeAlias(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
        AppLog.i("Voltar ao launcher BYD ($orig): $r")
        return "Voltando para: ${orig ?: "?"}\n$r"
    }
}
