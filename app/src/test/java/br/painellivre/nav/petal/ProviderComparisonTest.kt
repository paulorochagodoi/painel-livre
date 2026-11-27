package br.painellivre.nav.petal

import br.painellivre.nav.Place
import org.junit.Assert.*
import org.junit.Test

class ProviderComparisonTest {
    @Test fun formatsSideBySide() {
        val text = ProviderComparison.format("padaria",
            listOf(
                ProviderComparison.Search("Photon", 320, listOf(Place("Padaria A", "Centro", 0.0, 0.0))),
                ProviderComparison.Search("Petal", 210, listOf(Place("Padaria B", "", 0.0, 0.0), Place("Padaria C", "Vila", 0.0, 0.0))),
            ),
            listOf(
                ProviderComparison.Route("OSRM → Padaria A", 400, 2500.0, 300.0, null),
                ProviderComparison.Route("Petal → Padaria B", 380, 2400.0, 280.0, 4000.0),
            ))
        assertTrue(text.contains("■ Photon — 320 ms — 1 resultado\n  1. Padaria A · Centro"))
        assertTrue(text.contains("■ Petal — 210 ms — 2 resultados\n  1. Padaria B\n  2. Padaria C · Vila"))
        assertTrue(text.contains("distância: 2,5 km · sem trânsito: 5 min · com trânsito: não informado"))
        assertTrue(text.contains("distância: 2,4 km · sem trânsito: 5 min · com trânsito: 1 h 07 min"))
    }

    @Test fun showsErrorsWithoutBreaking() {
        val text = ProviderComparison.format("x",
            listOf(ProviderComparison.Search("Petal", 0, emptyList(), "sem chave")),
            listOf(ProviderComparison.Route("Rotas", 0, null, null, null, "sem GPS")))
        assertTrue(text.contains("■ Petal — 0 ms\n  erro: sem chave"))
        assertTrue(text.contains("erro: sem GPS"))
        assertEquals("—", ProviderComparison.minutes(Double.NaN))
        assertEquals("—", ProviderComparison.km(null))
    }
}
