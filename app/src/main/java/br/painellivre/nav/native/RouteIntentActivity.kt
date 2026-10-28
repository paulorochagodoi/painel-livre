package br.painellivre.nav.native

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import br.painellivre.nav.Places
import br.painellivre.util.Prefs

/** Confirma o destino recebido antes de iniciar navegação; nunca segue links encurtados. */
class RouteIntentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else intent.dataString
        val query = raw?.let(RouteDestination::query)
        if (query == null) {
            AlertDialog.Builder(this).setMessage("Destino não reconhecido. Compartilhe as coordenadas ou busque o endereço no Painel Livre.")
                .setPositiveButton("Fechar") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            return
        }
        AlertDialog.Builder(this).setTitle("Abrir rota para $query")
            .setItems(arrayOf("Mapa do Painel Livre", "Google Maps", "Waze")) { _, which ->
                val app = listOf("native", "maps", "waze")[which]
                Prefs(this).navApp = app
                val point = RouteDestination.point(query)
                if (point != null) Places.navigate(this, br.painellivre.nav.Place(query, "", point.lat, point.lon), app)
                else if (app == "native") {
                    // Busca visível para o motorista escolher o endereço correto.
                    startActivity(Intent(this, br.painellivre.ui.LauncherActivity::class.java).putExtra("route_search", query))
                } else Places.navigateText(this, query, app)
                finish()
            }.setNegativeButton("Cancelar") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }
}
