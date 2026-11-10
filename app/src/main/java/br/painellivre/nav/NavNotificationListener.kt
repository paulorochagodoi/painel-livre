package br.painellivre.nav

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
    private var lastSignature: String? = null

    override fun onListenerConnected() {
        AppLog.i("Leitor de notificações conectado")
        // Se uma rota já estava em andamento quando o app (re)iniciou, recupera o estado.
        try {
            activeNotifications?.filter { it.packageName in NavRepository.NAV_PACKAGES }
                ?.forEach { onNotificationPosted(it) }
        } catch (t: Throwable) {
            AppLog.w("activeNotifications falhou", t)
        }
    }

    override fun onListenerDisconnected() {
        AppLog.w("Leitor de notificações desconectado")
        NavRepository.end(null)
        drivingKey = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NavRepository.NAV_PACKAGES) return
        val n = sbn.notification ?: return
        if (!isNavigationNotification(n)) return
        val ex: Bundle = n.extras ?: return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() } ?: emptyList()

        val signature = "${sbn.key}|$title|$text|$big|$sub|${lines.joinToString("|")}"
        if (signature == lastSignature) return
        lastSignature = signature

        val parsed = NavParser.parse(title, text, big, sub, lines)
        if (parsed == null) {
            AppLog.i("Notificação de ${sbn.packageName} sem orientação reconhecível (título=${title?.length ?: 0} car.)")
            return
        }
        drivingKey = sbn.key
        val who = when (sbn.packageName) {
            NavRepository.PKG_WAZE -> "Waze"
            NavRepository.PKG_MAPS, NavRepository.PKG_MAPS_REVANCED -> "Google Maps"
            else -> sbn.packageName
        }
        AppLog.i("Manobra de $who: ${parsed.distanceText ?: "sem distância"} · ${parsed.instruction.take(80)}")
        NavRepository.update(sbn.packageName, parsed, loadLargeIcon(n))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in NavRepository.NAV_PACKAGES) return
        // Fim de rota = sumiu a notificação que estava guiando (ou não sabemos qual era).
        if (drivingKey == null || drivingKey == sbn.key) {
            drivingKey = null
            lastSignature = null
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
