package com.meuapp.player

import android.app.Application
import android.content.ComponentName
import android.provider.MediaStore.Audio.Media
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.meuapp.player.audio.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var isPlaying by mutableStateOf(false); private set
    var title by mutableStateOf("Nenhuma faixa"); private set
    var artist by mutableStateOf("Escolha na biblioteca"); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableStateOf(false); private set

    var update by mutableStateOf<Updater.Info?>(null); private set
    var updateMsg by mutableStateOf(""); private set
    var progress by mutableFloatStateOf(-1f); private set

    private var controller: MediaController? = null

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onMediaMetadataChanged(m: MediaMetadata) {
                    title = m.title?.toString() ?: "Faixa"
                    artist = m.artist?.toString() ?: "Artista desconhecido"
                }
            })
            isPlaying = c.isPlaying
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let { position = it.currentPosition; duration = it.duration.coerceAtLeast(0L) }
            }
        }
        checkUpdate()
    }

    /** Lê as músicas do celular (MediaStore). */
    fun loadTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Track>()
            val media = MediaStore.Audio.Media
            val proj = arrayOf(media._ID, media.TITLE, media.ARTIST, media.DURATION)
            getApplication<Application>().contentResolver.query(
                media.EXTERNAL_CONTENT_URI, proj,
                "${media.IS_MUSIC} != 0 AND ${media.DURATION} > 30000", null,
                "${media.TITLE} COLLATE NOCASE"
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(media._ID)
                val t = c.getColumnIndexOrThrow(media.TITLE)
                val a = c.getColumnIndexOrThrow(media.ARTIST)
                val d = c.getColumnIndexOrThrow(media.DURATION)
                while (c.moveToNext()) {
                    val artistName = c.getString(a)?.takeUnless { it == "<unknown>" } ?: "Artista desconhecido"
                    list += Track(c.getLong(id), c.getString(t) ?: "Sem título", artistName, c.getLong(d))
                }
            }
            withContext(Dispatchers.Main) { tracks = list }
        }
    }

    fun play(index: Int) {
        val c = controller ?: return
        val items = tracks.map {
            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build())
                .build()
        }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seek(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; shuffle = it.shuffleModeEnabled } }
    fun toggleRepeat() {
        controller?.let {
            val on = it.repeatMode == Player.REPEAT_MODE_OFF
            it.repeatMode = if (on) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            repeat = on
        }
    }

    // ---------- Atualização automática ----------
    fun checkUpdate() {
        viewModelScope.launch {
            updateMsg = "Verificando atualizações..."
            val info = Updater.latest()
            if (info == null) {
                updateMsg = "Não foi possível verificar (confira GH_OWNER e GH_REPO)"
            } else if (Updater.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                update = info; updateMsg = "Nova versão ${info.tag} disponível"
            } else {
                update = null; updateMsg = "Você está na versão mais recente"
            }
        }
    }

    fun installUpdate() {
        val info = update ?: return
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                progress = 0f
                val file = Updater.download(ctx, info.url) { progress = it }
                progress = -1f
                if (!Updater.install(ctx, file)) updateMsg = "Permita instalar apps desconhecidos e toque de novo"
            } catch (e: Exception) {
                progress = -1f; updateMsg = "Falha no download: ${e.message}"
            }
        }
    }

    override fun onCleared() { controller?.release(); super.onCleared() }
}
