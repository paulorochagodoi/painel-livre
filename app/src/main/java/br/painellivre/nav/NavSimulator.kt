package br.painellivre.nav

import br.painellivre.location.LocationRepository
import br.painellivre.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Rota de mentira para testar o painel com o carro parado: percorre as manobras mais comuns,
 * passando pelo MESMO parser usado com o Google Maps (os textos imitam as notificações reais).
 */
object NavSimulator {
    private var job: Job? = null
    const val PKG = "br.painellivre.simulacao"

    private val steps = listOf(
        Triple("400 m", "Vire à direita na Rua Augusta", "18 min · 7,4 km · 14:35"),
        Triple("250 m", "Na rotatória, pegue a 2ª saída para Av. Paulista", "16 min · 6,9 km · 14:35"),
        Triple("900 m", "Mantenha-se à esquerda para Av. 23 de Maio", "14 min · 6,2 km · 14:36"),
        Triple("1,2 km", "Pegue a saída à direita para Rodovia dos Imigrantes", "11 min · 5,0 km · 14:36"),
        Triple("300 m", "Vire ligeiramente à esquerda na Rua Vergueiro", "6 min · 2,1 km · 14:37"),
        Triple("150 m", "Faça o retorno na Av. Domingos de Morais", "4 min · 1,2 km · 14:37"),
        Triple("80 m", "O destino está à direita", "1 min · 80 m · 14:38"),
    )

    val running get() = job?.isActive == true

    fun toggle(scope: CoroutineScope) {
        if (running) stop() else start(scope)
    }

    fun start(scope: CoroutineScope) {
        stop()
        AppLog.i("Simulação de rota iniciada")
        job = scope.launch {
            // Em debug (emulador sem GPS) também anda com um GPS falso pela Av. Paulista.
            var lat = -23.5614
            var lon = -46.6565
            var bearing = 50.0
            while (isActive) {
                for ((dist, instr, summary) in steps) {
                    bearing = (bearing + 70) % 360
                    // Distância decrescendo até a manobra, como o Maps faz.
                    val meters = NavParser.toMeters(dist.substringBefore(' '), dist.substringAfter(' ')) ?: 300
                    var d = meters
                    while (d > 0 && isActive) {
                        val txt = if (d >= 1000) String.format(java.util.Locale("pt", "BR"), "%.1f km", d / 1000.0) else "$d m"
                        NavParser.parse(txt, instr, null, summary)?.let { NavRepository.update(PKG, it, null, simulated = true) }
                        if (br.painellivre.BuildConfig.DEBUG) {
                            val step = 12.0 // m/s ≈ 43 km/h
                            lat += step * Math.cos(Math.toRadians(bearing)) / 111_320.0
                            lon += step * Math.sin(Math.toRadians(bearing)) / (111_320.0 * Math.cos(Math.toRadians(lat)))
                            LocationRepository.injectForTest(android.location.Location("sim").apply {
                                latitude = lat; longitude = lon; this.bearing = bearing.toFloat(); speed = step.toFloat()
                                accuracy = 5f; time = System.currentTimeMillis()
                            })
                        }
                        delay(1000)
                        d -= (meters / 6).coerceAtLeast(20)
                    }
                }
            }
        }
    }

    fun stop() {
        if (job != null) AppLog.i("Simulação de rota encerrada")
        job?.cancel()
        job = null
        NavRepository.end(PKG)
    }
}
