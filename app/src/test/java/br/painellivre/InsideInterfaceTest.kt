package br.painellivre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decisão do Paulo (rodada 2 do Aura): tudo dentro da interface. Clima, energia e mídia aparecem
 * nas janelas do Painel Livre; nenhuma janela abre app da BYD nem app externo. As únicas aberturas
 * de outro app que sobram são funções antigas, contadas aqui para nenhuma nova entrar sem querer:
 *  - rota ativa do Maps/Waze e o botão do app de navegação no dock (o Maps/Waze é quem calcula a rota);
 *  - "Abrir app" da janela Mídia (era o toque no cartão Mídia do cockpit);
 *  - a página Apps (é a tela inicial) e o atalho para liberar o acesso às notificações;
 *  - "O que mostrar no painel → Navegação" (#2/#5/#6): `ClusterController.launchNavigation` põe o
 *    Maps/Waze no display do painel, nunca na central.
 */
class InsideInterfaceTest {

    private fun src(path: String): File =
        listOf(File(path), File("app/$path")).firstOrNull { it.exists() } ?: error("não achei $path")

    private val launcher get() = src("src/main/java/br/painellivre/ui/LauncherActivity.kt").readText()

    private fun count(text: String, what: String) = Regex(Regex.escape(what)).findAll(text).count()

    @Test fun launcherOpensOtherAppsOnlyInTheOldPlaces() {
        val t = launcher
        assertEquals("rota do Maps/Waze + Abrir app da mídia", 2, count(t, "getLaunchIntentForPackage"))
        assertEquals("seletor de música só no Abrir app", 1, count(t, "makeMainSelectorActivity"))
        assertEquals("dock e rota ativa", 2, count(t, "Places.openApp("))
        assertEquals("só o botão Abrir app chama openMedia", 1, count(t, "openMedia()") - 1)
        assertFalse("nenhum intent para pacote fixo", t.contains("setPackage(") || t.contains("setClassName("))
    }

    @Test fun mediaPlayStaysInsideTheInterface() {
        val t = launcher
        val play = t.substringAfter("private fun playOrResume()").substringBefore("\n    }")
        assertTrue(play.contains("MediaRepository.resumeLast"))
        assertFalse(play.contains("startActivity") || play.contains("openMedia") || play.contains("startSafely"))
    }

    @Test fun auraWindowsAndPanelNeverOpenAnotherApp() {
        val dirs = listOf("src/main/java/br/painellivre/ui/aura", "src/main/java/br/painellivre/cluster")
        dirs.flatMap { src(it).walk().filter { f -> f.extension == "kt" }.toList() }.forEach { f ->
            var t = f.readText()
            if (f.name == "ClusterController.kt") {
                assertEquals("só o launchNavigation (Maps/Waze no painel)", 1, count(t, "getLaunchIntentForPackage"))
                t = t.replace("getLaunchIntentForPackage", "")
            }
            listOf("getLaunchIntentForPackage", "makeMainSelectorActivity", "setPackage(", "Places.openApp", "ACTION_VIEW")
                .forEach { assertFalse("${f.name} usa $it", t.contains(it)) }
        }
    }

    @Test fun noBydAppIsCalledByName() {
        val ui = src("src/main/java/br/painellivre/ui").walk().filter { it.extension == "kt" }
        val layouts = src("src/main/res/layout").walk().filter { it.extension == "xml" }
        (ui + layouts).forEach { f ->
            val t = f.readText()
            assertFalse("${f.name} cita pacote da BYD", Regex("com\\.byd").containsMatchIn(t))
            assertFalse("${f.name} usa onClick no XML", t.contains("android:onClick"))
        }
    }
}
