package br.painellivre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.painellivre.cluster.ClusterService
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        when (intent.action) {
            // Registrado para o Diagnóstico mostrar se a BYD entregou o boot a este app (ela pode
            // segurar o BOOT_COMPLETED de apps fora da lista de "autoiniciar").
            Intent.ACTION_BOOT_COMPLETED -> prefs.lastBootAt = System.currentTimeMillis()
            Intent.ACTION_MY_PACKAGE_REPLACED -> {} // app atualizado: volta a rodar sem esperar o boot
            else -> return
        }
        if (!prefs.startOnBoot) return
        AppLog.i("${intent.action?.substringAfterLast('.')}: iniciando serviço do painel")
        try {
            ClusterService.start(context)
        } catch (t: Throwable) {
            AppLog.e("Não consegui iniciar o serviço no boot", t)
        }
    }
}
