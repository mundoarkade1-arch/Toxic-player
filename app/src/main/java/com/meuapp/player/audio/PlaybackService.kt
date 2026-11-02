package com.meuapp.player.audio

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.meuapp.player.widget.ToxicWidget
import com.meuapp.player.widget.WidgetBridge
import com.meuapp.player.widget.WidgetState

/** Toca em segundo plano (notificação e tela de bloqueio pelo Media3) com o motor de DSP do Toxic Player no pipeline. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        AudioFx.init(this)
        RouteWatcher.start(this)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(DspEngine.processor))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { AudioFx.attach(audioSessionId) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { updateWidget(player) }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { updateWidget(player) }
        })
        AudioFx.attach(player.audioSessionId)
        SleepTimer.attach(player)
        VolumeManager.attach(player)
        WidgetBridge.player = player
        session = MediaSession.Builder(this, player).build()
    }

    private fun updateWidget(p: Player) {
        WidgetState.title = p.mediaMetadata.title?.toString() ?: "Toxic Player"
        WidgetState.artist = p.mediaMetadata.artist?.toString() ?: "Toque para abrir"
        WidgetState.playing = p.isPlaying
        ToxicWidget.refresh(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        SleepTimer.detach()
        VolumeManager.detach()
        WidgetBridge.player = null
        AudioFx.release()
        super.onDestroy()
    }
}
