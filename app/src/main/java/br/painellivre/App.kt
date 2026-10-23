package br.painellivre

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import br.painellivre.debug.DebugProbe
import br.painellivre.util.AppLog
import org.maplibre.android.MapLibre

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW),
            )
        }
        AppLog.i("Painel Livre ${BuildConfig.VERSION_NAME} iniciado")
        DebugProbe.sync(this) // só roda se o motorista ligou o modo de debug
    }

    companion object {
        const val CHANNEL_SERVICE = "painel"
    }
}
