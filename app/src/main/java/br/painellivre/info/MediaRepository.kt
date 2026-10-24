package br.painellivre.info

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import br.painellivre.nav.NavNotificationListener
import br.painellivre.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * O que está tocando (qualquer app de música/rádio), para o cartão de mídia da tela central.
 * Usa o mesmo acesso às notificações que já lê o Maps/Waze — nada a mais para autorizar.
 */
object MediaRepository {

    data class NowPlaying(
        val title: String?,
        val artist: String?,
        val appPackage: String,
        val art: Bitmap?,
        val playing: Boolean,
    )

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val main = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { controller = null; _now.value = null }
    }

    fun start(ctx: Context) {
        if (manager != null) return
        val m = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val listener = ComponentName(ctx, NavNotificationListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            pick(m.getActiveSessions(listener))
            manager = m
        } catch (e: SecurityException) {
            // Sem acesso às notificações ainda: tenta de novo na próxima vez que a tela abrir.
            AppLog.w("Mídia: sem acesso às notificações")
        }
    }

    private fun pick(list: List<MediaController>?) {
        val best = list.orEmpty().sortedByDescending { it.playbackState?.state == PlaybackState.STATE_PLAYING }.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, main)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { _now.value = null; return }
        val md = c.metadata
        _now.value = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            appPackage = c.packageName,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    /**
     * Tocar sem sessão ativa: manda a tecla "play" de mídia ao Android, que retoma o último app
     * de música em segundo plano. A tela continua no Painel Livre (nada de abrir outro app); se
     * nenhum app responder, segue "Nada tocando".
     */
    fun resumeLast(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }.onFailure { AppLog.w("Mídia: tecla play recusada: ${it.message}") }
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
