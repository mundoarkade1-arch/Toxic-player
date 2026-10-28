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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.meuapp.player.audio.AudioFx
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
    var speed by mutableFloatStateOf(1f); private set
    var pitch by mutableFloatStateOf(1f); private set

    /** Pasta aberta na biblioteca (null = lista de pastas). */
    var openFolder by mutableStateOf<String?>(null); private set

    var update by mutableStateOf<UpdateInfo?>(null); private set
    var busy by mutableStateOf(false); private set
    var updateMsg by mutableStateOf(""); private set
    var progress by mutableFloatStateOf(-1f); private set

    private var controller: MediaController? = null

    val folders: List<Folder>
        get() = tracks.groupBy { it.folderPath }
            .map { (p, l) -> Folder(p, l.first().folderName, l.size) }
            .sortedBy { it.name.lowercase() }

    fun tracksIn(path: String?): List<Track> =
        if (path == null || path == ALL_FOLDERS) tracks else tracks.filter { it.folderPath == path }

    fun enterFolder(path: String?) { openFolder = path }

    init {
        AudioFx.init(app)
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

    /** Lê as músicas do celular (MediaStore), guardando a pasta de cada uma. */
    fun loadTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Track>()
            val proj = arrayOf(Media._ID, Media.TITLE, Media.ARTIST, Media.DURATION, Media.DATA)
            getApplication<Application>().contentResolver.query(
                Media.EXTERNAL_CONTENT_URI, proj,
                "${Media.IS_MUSIC} != 0 AND ${Media.DURATION} > 30000", null,
                "${Media.TITLE} COLLATE NOCASE"
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(Media._ID)
                val t = c.getColumnIndexOrThrow(Media.TITLE)
                val a = c.getColumnIndexOrThrow(Media.ARTIST)
                val d = c.getColumnIndexOrThrow(Media.DURATION)
                val p = c.getColumnIndexOrThrow(Media.DATA)
                while (c.moveToNext()) {
                    val artistName = c.getString(a)?.takeUnless { it == "<unknown>" } ?: "Artista desconhecido"
                    val path = c.getString(p) ?: ""
                    val folderPath = path.substringBeforeLast('/', "")
                    val folderName = folderPath.substringAfterLast('/').ifEmpty { "Sem pasta" }
                    list += Track(c.getLong(id), c.getString(t) ?: "Sem título", artistName, c.getLong(d), folderPath, folderName)
                }
            }
            withContext(Dispatchers.Main) { tracks = list }
        }
    }

    /** Toca uma lista (por exemplo, só as músicas de uma pasta) a partir de [index]. */
    fun playList(list: List<Track>, index: Int) {
        val c = controller ?: return
        val items = list.map {
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

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            shuffle = it.shuffleModeEnabled
        }
    }

    fun toggleRepeat() {
        controller?.let {
            val on = it.repeatMode == Player.REPEAT_MODE_OFF
            it.repeatMode = if (on) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            repeat = on
        }
    }

    fun changeSpeed(v: Float) { speed = v; controller?.setPlaybackParameters(PlaybackParameters(speed, pitch)) }
    fun changePitch(v: Float) { pitch = v; controller?.setPlaybackParameters(PlaybackParameters(speed, pitch)) }

    // ---------- Atualização automática ----------
    /** [force] = true ignora o cache de 10 minutos (botão "Verificar agora"). */
    fun checkUpdate(force: Boolean = false) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            updateMsg = "Verificando atualizações..."
            when (val r = Updater.check(getApplication<Application>(), force)) {
                is Updater.Check.Available -> { update = r.info; updateMsg = "Nova versão ${r.info.versionName} disponível" }
                is Updater.Check.UpToDate -> { update = null; updateMsg = "Você está na versão mais recente (${r.latestName})" }
                is Updater.Check.Failed -> updateMsg = r.message
            }
            busy = false
        }
    }

    fun installUpdate() {
        val info = update ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val ctx = getApplication<Application>()
                progress = 0f
                updateMsg = "Baixando ${info.versionName}..."
                val file = Updater.download(ctx, info) { progress = it }
                progress = -1f
                val problem = Updater.verifyApk(ctx, file, info)
                if (problem != null) {
                    file.delete()
                    updateMsg = problem
                } else if (Updater.install(ctx, file)) {
                    updateMsg = "Confirme a instalação na tela do Android"
                } else {
                    updateMsg = "Permita instalar apps desconhecidos e toque de novo"
                }
            } catch (e: Exception) {
                progress = -1f
                updateMsg = "Falha no download: ${e.message}"
            }
            busy = false
        }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
