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

    /** Regressão da causa da issue: o filtro HOME/DEFAULT precisa vir ligado no manifesto. */
    @Test fun manifestDeclaresEnabledHomeFilter() {
        val f = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }
        val xml = f.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val alias = Regex("<activity-alias[^>]*HomeAlias[^>]*>.*?</activity-alias>", RegexOption.DOT_MATCHES_ALL).find(xml)?.value
            ?: error("HomeAlias sumiu do manifesto")
        assertTrue(alias.contains("android:enabled=\"true\""))
        assertTrue(alias.contains("android.intent.category.HOME"))
        assertTrue(alias.contains("android.intent.category.DEFAULT"))
        assertTrue(alias.contains("android:targetActivity=\".ui.LauncherActivity\""))
        val launcher = Regex("<activity\\s[^>]*\\.ui\\.LauncherActivity[^>]*>", RegexOption.DOT_MATCHES_ALL).find(xml)?.value ?: error("sem LauncherActivity")
        assertTrue("o Início precisa reaproveitar a mesma tela", launcher.contains("singleTask"))
    }
}
