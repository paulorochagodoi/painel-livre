package br.painellivre

import br.painellivre.adb.HomeChoice
import br.painellivre.adb.HomeChoice.Resolved
import br.painellivre.adb.HomeChoice.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Issue #7: o Painel Livre precisa ser reconhecido como tela inicial. */
class HomeChoiceTest {

    private val us = "br.painellivre"

    @Test fun parsesBriefOutputOfResolveActivity() {
        // Formato do Android 10: linha de prioridade e depois pacote/classe.
        val out = "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\ncom.byd.launcher/.Launcher\n"
        assertEquals(Resolved.Other("com.byd.launcher/.Launcher"), HomeChoice.parseResolved(out, us))
        assertEquals(Resolved.Ours, HomeChoice.parseResolved("priority=0\nbr.painellivre/.ui.HomeAlias", us))
    }

    @Test fun chooserIsNotALauncher() {
        val out = "priority=0 preferredOrder=0\nandroid/com.android.internal.app.ResolverActivity"
        assertEquals(Resolved.Chooser, HomeChoice.parseResolved(out, us))
        // Bug antigo: o seletor era guardado como "launcher original" e não dava para voltar.
        assertNull(HomeChoice.originalToSave(Resolved.Chooser))
        assertNull(HomeChoice.originalToSave(Resolved.Ours))
        assertEquals("com.byd.launcher/.Launcher", HomeChoice.originalToSave(Resolved.Other("com.byd.launcher/.Launcher")))
    }

    @Test fun unknownOutputs() {
        assertEquals(Resolved.Unknown, HomeChoice.parseResolved(null, us))
        assertEquals(Resolved.Unknown, HomeChoice.parseResolved("", us))
        assertEquals(Resolved.Unknown, HomeChoice.parseResolved("ERRO: porta 5555 fechada", us))
        assertEquals(Resolved.Unknown, HomeChoice.parseResolved("No activity found", us))
    }

    @Test fun setHomeResult() {
        assertTrue(HomeChoice.setHomeSucceeded("Success\n"))
        assertFalse(HomeChoice.setHomeSucceeded("Error: component br.painellivre/.ui.HomeAlias is disabled"))
        assertFalse(HomeChoice.setHomeSucceeded("Unknown command: set-home-activity"))
        assertFalse(HomeChoice.setHomeSucceeded("ERRO: tempo esgotado"))
        assertFalse(HomeChoice.setHomeSucceeded(""))
        assertFalse(HomeChoice.setHomeSucceeded(null))
    }

    @Test fun nextStepPrefersAdbThenSettingsThenChooser() {
        assertEquals(Step.DONE, HomeChoice.nextStep(Resolved.Ours, adbOk = false, hasHomeSettings = false))
        assertEquals(Step.ADB, HomeChoice.nextStep(Resolved.Other("x/.Y"), adbOk = true, hasHomeSettings = true))
        assertEquals(Step.ADB, HomeChoice.nextStep(Resolved.Chooser, adbOk = true, hasHomeSettings = false))
        assertEquals(Step.HOME_SETTINGS, HomeChoice.nextStep(Resolved.Unknown, adbOk = false, hasHomeSettings = true))
        assertEquals(Step.CHOOSER, HomeChoice.nextStep(Resolved.Unknown, adbOk = false, hasHomeSettings = false))
    }

    @Test fun restoreOnlyToARealOtherLauncher() {
        assertEquals("com.byd.launcher/.Launcher", HomeChoice.restoreTarget("com.byd.launcher/.Launcher", us))
        assertNull(HomeChoice.restoreTarget(null, us))
        assertNull(HomeChoice.restoreTarget("android/com.android.internal.app.ResolverActivity", us))
        assertNull(HomeChoice.restoreTarget("br.painellivre/.ui.HomeAlias", us))
        assertNull(HomeChoice.restoreTarget("lixo", us))
    }

    @Test fun aliasStateFromPackageManager() {
        // Manifesto desligado (v0.4.1): "padrão" = desligado; só o estado ligado pelo app conta.
        assertFalse(HomeChoice.aliasOn(HomeChoice.STATE_DEFAULT))
        assertTrue(HomeChoice.aliasOn(HomeChoice.STATE_ENABLED))
        assertFalse(HomeChoice.aliasOn(HomeChoice.STATE_DISABLED))
        assertFalse(HomeChoice.aliasOn(3)) // DISABLED_USER
        assertTrue(HomeChoice.aliasOn(HomeChoice.STATE_DEFAULT, manifestEnabled = true))
        // Mesmos números do Android (PackageManager.COMPONENT_ENABLED_STATE_*).
        assertEquals(0, HomeChoice.STATE_DEFAULT)
        assertEquals(1, HomeChoice.STATE_ENABLED)
        assertEquals(2, HomeChoice.STATE_DISABLED)
    }

    @Test fun aliasIsEnabledBeforeChoosingHome() {
        // Instalação nova: alias desligado, BYD é o launcher → liga antes do set-home-activity.
        val fresh = HomeChoice.nextStep(Resolved.Other("com.byd.launcher/.Launcher"), adbOk = true, hasHomeSettings = true, aliasOn = false)
        assertEquals(Step.ADB, fresh)
        assertTrue(HomeChoice.mustEnableAlias(fresh, aliasOn = false))
        // Sem ADB também: a tela "Tela inicial" e o seletor só listam o app com o filtro ativo.
        assertTrue(HomeChoice.mustEnableAlias(Step.HOME_SETTINGS, aliasOn = false))
        assertTrue(HomeChoice.mustEnableAlias(Step.CHOOSER, aliasOn = false))
        // Já ligado ou já é a tela inicial: não mexe.
        assertFalse(HomeChoice.mustEnableAlias(Step.ADB, aliasOn = true))
        assertFalse(HomeChoice.mustEnableAlias(Step.DONE, aliasOn = true))
    }

    @Test fun staleOursWithAliasOffIsNotDone() {
        // Preferido antigo apontando para o alias desligado: o Início não abriria o app.
        assertEquals(Step.ADB, HomeChoice.nextStep(Resolved.Ours, adbOk = true, hasHomeSettings = false, aliasOn = false))
        assertEquals(Step.CHOOSER, HomeChoice.nextStep(Resolved.Ours, adbOk = false, hasHomeSettings = false, aliasOn = false))
        assertEquals(Step.DONE, HomeChoice.nextStep(Resolved.Ours, adbOk = true, hasHomeSettings = false, aliasOn = true))
    }

    private fun manifest(): String {
        val f = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }
        return f.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    }

    /**
     * Regressão da v0.4.0: a central da BYD recusou instalar o APK com o alias HOME ligado ("Fail in
     * installation of desktop apps"). O filtro HOME/DEFAULT existe, mas vem desligado; o app liga depois.
     */
    @Test fun manifestDeclaresDisabledHomeFilter() {
        val xml = manifest()
        val alias = Regex("<activity-alias[^>]*HomeAlias[^>]*>.*?</activity-alias>", RegexOption.DOT_MATCHES_ALL).find(xml)?.value
            ?: error("HomeAlias sumiu do manifesto")
        assertTrue("o alias HOME precisa vir desligado", alias.contains("android:enabled=\"false\""))
        assertEquals(HomeChoice.ALIAS_ENABLED_IN_MANIFEST, alias.contains("android:enabled=\"true\""))
        assertTrue(alias.contains("android.intent.category.HOME"))
        assertTrue(alias.contains("android.intent.category.DEFAULT"))
        assertTrue(alias.contains("android:targetActivity=\".ui.LauncherActivity\""))
        val launcher = Regex("<activity\\s[^>]*\\.ui\\.LauncherActivity[^>]*>", RegexOption.DOT_MATCHES_ALL).find(xml)?.value ?: error("sem LauncherActivity")
        assertTrue("o Início precisa reaproveitar a mesma tela", launcher.contains("singleTask"))
    }

    /** Nenhum outro componente pode declarar HOME: o APK chegaria à BYD como launcher ativo. */
    @Test fun onlyTheDisabledAliasDeclaresHome() {
        val xml = manifest()
        val components = Regex("<(activity|activity-alias)\\s[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }
        val withHome = components.filter { it.contains("android.intent.category.HOME") }.toList()
        assertEquals(1, withHome.size)
        assertTrue(withHome.single().contains(".ui.HomeAlias"))
        assertTrue(withHome.single().contains("android:enabled=\"false\""))
    }
}
