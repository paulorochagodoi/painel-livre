package br.painellivre.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import br.painellivre.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** GPS da própria central (não depende do celular). Alimenta o mapa do painel. */
object LocationRepository {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private var lm: LocationManager? = null
    private var lastBearing = 0f

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            // Parado, o GPS dá rumos aleatórios: mantém o último rumo válido.
            if (loc.hasBearing() && loc.speed > 1.5f) lastBearing = loc.bearing else loc.bearing = lastBearing
            _location.value = loc
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (lm != null) return
        if (!hasPermission(ctx)) {
            AppLog.w("Sem permissão de localização — o mapa do painel não vai acompanhar o carro")
            return
        }
        val m = ctx.applicationContext.getSystemService(LocationManager::class.java) ?: return
        lm = m
        try {
            val last = listOfNotNull(
                runCatching { m.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                runCatching { m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
            ).maxByOrNull { it.time }
            if (last != null && _location.value == null) _location.value = last
            if (m.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            }
            if (m.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, listener, Looper.getMainLooper())
            }
            AppLog.i("GPS iniciado (provedores: ${m.allProviders})")
        } catch (t: Throwable) {
            AppLog.e("Falha ao iniciar GPS", t)
        }
    }

    /** Só para a simulação em builds de debug (emulador sem GPS). */
    fun injectForTest(loc: Location) {
        if (br.painellivre.BuildConfig.DEBUG) _location.value = loc
    }

    fun stop() {
        lm?.removeUpdates(listener)
        lm = null
    }
}
