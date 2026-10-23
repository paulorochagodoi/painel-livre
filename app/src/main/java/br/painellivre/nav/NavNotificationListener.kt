package br.painellivre.nav

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import br.painellivre.util.Prefs
import br.painellivre.debug.DebugLog
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import br.painellivre.util.AppLog

/**
 * Lê as notificações contínuas de navegação do Google Maps e do Waze e publica o estado em
 * [NavRepository]. É o mesmo princípio do DashCast (MapNotificationListenerService) e do
 * OpenBYD: esses apps não têm API pública de turn-by-turn, mas a notificação traz a seta
 * (largeIcon), a distância, a rua e o resumo da rota.
 */
class NavNotificationListener : NotificationListenerService() {

    private var drivingKey: String? = null
    private lateinit var preferences: SharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_EXTERNAL_NAVIGATION) {
            clearExternal()
            if (Prefs(this).externalNavigation) readActive()
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        clearExternal()
        super.onDestroy()
    }

    private fun clearExternal() {
        NavRepository.NAV_PACKAGES.forEach { NavRepository.end(it) }
        drivingKey = null
    }

    override fun onListenerConnected() {
        AppLog.i("Leitor de notificações conectado")
        readActive()
    }

    private fun readActive() {
        // Recupera também uma rota que já estava em andamento ao habilitar a opção.
        try {
            activeNotifications?.filter { it.packageName in NavRepository.NAV_PACKAGES }
                ?.forEach { onNotificationPosted(it) }
        } catch (t: Throwable) {
            AppLog.w("activeNotifications falhou", t)
        }
    }

    override fun onListenerDisconnected() {
        AppLog.w("Leitor de notificações desconectado")
        clearExternal()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NavRepository.NAV_PACKAGES) return
        val n = sbn.notification ?: return
        // O debug captura inclusive formatos ainda não reconhecidos, antes de qualquer filtro.
        if (Prefs(this).debugMode) {
            DebugLog.add("notificação", buildString {
                append("pacote=${sbn.packageName} id=${sbn.id} categoria=${n.category} flags=${n.flags}")
                n.extras?.keySet()?.sorted()?.forEach { key ->
                    val value = runCatching { n.extras.get(key) }.getOrNull()
                    append("\n$key=${when (value) {
                        is Bitmap -> "Bitmap(${value.width}x${value.height})"
                        is Array<*> -> value.contentDeepToString()
                        else -> value.toString()
                    }}")
                }
                append("\nlargeIcon=${n.getLargeIcon()} smallIcon=${n.smallIcon}")
            })
        }
        if (!Prefs(this).externalNavigation || !isNavigationNotification(n)) return
        val ex: Bundle = n.extras ?: return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" · ")
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val icon = n.getLargeIcon()
        val iconName = runCatching {
            if (icon?.type == Icon.TYPE_RESOURCE)
                packageManager.getResourcesForApplication(sbn.packageName).getResourceEntryName(icon.resId)
            else null
        }.getOrNull()
        val parsed = NavParser.parse(title, text, big, sub, NavParser.iconManeuver(iconName))
        if (parsed == null) {
            if (drivingKey == sbn.key) {
                NavRepository.end(sbn.packageName)
                drivingKey = null
            }
            AppLog.i("Notificação de ${sbn.packageName} sem orientação reconhecível (título=${title?.length ?: 0} car.)")
            return
        }
        drivingKey = sbn.key
        // Setas próprias para manobras reconhecidas; bitmap como alternativa sem adivinhar pixels.
        NavRepository.update(sbn.packageName, parsed,
            if (parsed.maneuver == Maneuver.UNKNOWN) loadLargeIcon(n) else null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NavRepository.NAV_PACKAGES) return
        // Fim de rota = sumiu a notificação que estava guiando (ou não sabemos qual era).
        if (drivingKey == null || drivingKey == sbn.key) {
            drivingKey = null
            NavRepository.end(sbn.packageName)
            AppLog.i("Navegação encerrada (${sbn.packageName})")
        }
    }

    private fun isNavigationNotification(n: Notification): Boolean =
        (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 || Notification.CATEGORY_NAVIGATION == n.category

    private fun loadLargeIcon(n: Notification): Bitmap? = try {
        val icon: Icon? = n.getLargeIcon()
        val d = icon?.loadDrawable(this)
        when {
            d == null -> (n.extras.get(Notification.EXTRA_LARGE_ICON) as? Bitmap)
            d is BitmapDrawable && d.bitmap != null -> d.bitmap
            d.intrinsicWidth > 0 && d.intrinsicHeight > 0 -> {
                val bmp = Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                d.setBounds(0, 0, c.width, c.height)
                d.draw(c)
                bmp
            }
            else -> null
        }
    } catch (t: Throwable) {
        AppLog.w("Falha ao ler o ícone da manobra", t)
        null
    }
}
