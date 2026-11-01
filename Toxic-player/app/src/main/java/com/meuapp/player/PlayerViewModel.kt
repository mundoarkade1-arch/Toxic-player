package com.meuapp.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore.Audio.Media
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import org.json.JSONArray

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
    var playError by mutableStateOf<String?>(null); private set
    var currentTrack by mutableStateOf<Track?>(null); private set

    // Biblioteca
    var openFolder by mutableStateOf<String?>(null); private set
    var libMode by mutableIntStateOf(0); private set      // 0 pastas, 1 artistas, 2 álbuns
    var query by mutableStateOf(""); private set
    var sortMode by mutableIntStateOf(0); private set     // 0 título, 1 artista, 2 duração

    // Atualização
    var update by mutableStateOf<UpdateInfo?>(null); private set
    var busy by mutableStateOf(false); private set
    var updateMsg by mutableStateOf(""); private set
    var progress by mutableFloatStateOf(-1f); private set

    private var controller: MediaController? = null
    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)
    private var restored = false
    private var lastSave = 0L

    // ---------- Biblioteca ----------
    fun groups(mode: Int): List<Folder> = when (mode) {
        1 -> tracks.groupBy { it.artist }.map { (k, l) -> Folder("A:$k", k, l.size) }
        2 -> tracks.groupBy { it.album }.map { (k, l) -> Folder("B:$k", k, l.size) }
        else -> tracks.groupBy { it.folderPath }.map { (p, l) -> Folder("F:$p", l.first().folderName, l.size) }
    }.sortedBy { it.name.lowercase() }

    private fun sorted(l: List<Track>): List<Track> = when (sortMode) {
        1 -> l.sortedBy { it.artist.lowercase() }
        2 -> l.sortedByDescending { it.durationMs }
        else -> l.sortedBy { it.title.lowercase() }
    }

    fun tracksIn(key: String?): List<Track> = sorted(
        when {
            key == null || key == ALL_FOLDERS -> tracks
            key.startsWith("F:") -> tracks.filter { it.folderPath == key.drop(2) }
            key.startsWith("A:") -> tracks.filter { it.artist == key.drop(2) }
            key.startsWith("B:") -> tracks.filter { it.album == key.drop(2) }
            else -> emptyList()
        }
    )

    fun searchResults(): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return sorted(tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) || it.folderName.contains(q, true)
        })
    }

    fun enterFolder(key: String?) { openFolder = key }
    fun changeLibMode(m: Int) { libMode = m; openFolder = null }
    fun changeQuery(q: String) { query = q }
    fun changeSort(m: Int) { sortMode = m }

    // ---------- Conexão com o player ----------
    init {
        AudioFx.init(app)
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (!playing) savePos() }
                override fun onMediaMetadataChanged(m: MediaMetadata) {
                    title = m.title?.toString() ?: "Faixa"
                    artist = m.artist?.toString() ?: "Artista desconhecido"
                    refreshCurrent()
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { savePos(); refreshCurrent() }
                override fun onPlayerError(error: PlaybackException) {
                    // Arquivo removido ou ilegível: avisa e pula para a próxima faixa
                    playError = "Não foi possível tocar esta faixa"
                    if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.prepare(); c.play() }
                }
            })
            syncOrRestore()
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let {
                    position = it.currentPosition
                    duration = it.duration.coerceAtLeast(0L)
                    val now = System.currentTimeMillis()
                    if (it.isPlaying && now - lastSave > 5000) { lastSave = now; savePos() }
                }
            }
        }
        checkUpdate()
    }

    // ---------- Continuar de onde parou ----------
    private fun mediaItem(t: Track) = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album)
                .setArtworkUri(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), t.albumId))
                .build()
        )
        .build()

    private fun refreshCurrent() {
        val id = controller?.currentMediaItem?.mediaId
        currentTrack = if (id == null) null else tracks.firstOrNull { it.id.toString() == id }
    }

    private fun saveQueue(list: List<Track>) {
        val ids = JSONArray(); list.forEach { ids.put(it.id.toString()) }
        prefs.edit().putString("ids", ids.toString()).apply()
    }

    fun savePos() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        prefs.edit()
            .putInt("idx", c.currentMediaItemIndex).putLong("pos", c.currentPosition)
            .putBoolean("sh", c.shuffleModeEnabled).putInt("rp", c.repeatMode)
            .putFloat("sp", speed).putFloat("pt", pitch).apply()
    }

    private fun syncFromController(c: MediaController) {
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode != Player.REPEAT_MODE_OFF
        speed = c.playbackParameters.speed
        pitch = c.playbackParameters.pitch
        val m = c.mediaMetadata
        if (m.title != null) { title = m.title.toString(); artist = m.artist?.toString() ?: "Artista desconhecido" }
        refreshCurrent()
    }

    /**
     * Se o serviço ainda tem fila (app só foi reaberto), apenas sincroniza a tela.
     * Se o serviço morreu, recria a última fila PAUSADA, na faixa e no ponto onde parou.
     * Arquivos que não existem mais são descartados. Só roda depois que a biblioteca foi lida.
     */
    private fun syncOrRestore() {
        val c = controller ?: return
        if (c.mediaItemCount > 0) { syncFromController(c); restored = true; return }
        if (restored || tracks.isEmpty()) return
        restored = true
        val arr = try { JSONArray(prefs.getString("ids", null) ?: return) } catch (e: Exception) { return }
        val byId = tracks.associateBy { it.id.toString() }
        val savedIdx = prefs.getInt("idx", 0)
        val list = ArrayList<Track>()
        var newIdx = -1; var exact = false
        for (i in 0 until arr.length()) {
            val t = byId[arr.optString(i)] ?: continue
            if (newIdx < 0 && i >= savedIdx) { newIdx = list.size; exact = i == savedIdx }
            list.add(t)
        }
        if (list.isEmpty()) return
        if (newIdx < 0) newIdx = 0
        val sp = prefs.getFloat("sp", 1f); val pt = prefs.getFloat("pt", 1f)
        c.setMediaItems(list.map { mediaItem(it) }, newIdx, if (exact) prefs.getLong("pos", 0L) else 0L)
        c.shuffleModeEnabled = prefs.getBoolean("sh", false)
        c.repeatMode = prefs.getInt("rp", Player.REPEAT_MODE_OFF)
        c.setPlaybackParameters(PlaybackParameters(sp, pt))
        c.prepare()
        syncFromController(c)
        val cur = list[newIdx]
        title = cur.title; artist = cur.artist
        refreshCurrent()
    }

    /** Lê as músicas do celular (MediaStore), guardando pasta, artista e álbum. */
    fun loadTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Track>()
            val proj = arrayOf(Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM, Media.ALBUM_ID, Media.DURATION, Media.DATA)
            try {
                getApplication<Application>().contentResolver.query(
                    Media.EXTERNAL_CONTENT_URI, proj,
                    "${Media.IS_MUSIC} != 0 AND ${Media.DURATION} > 30000", null,
                    "${Media.TITLE} COLLATE NOCASE"
                )?.use { c ->
                    val id = c.getColumnIndexOrThrow(Media._ID)
                    val t = c.getColumnIndexOrThrow(Media.TITLE)
                    val a = c.getColumnIndexOrThrow(Media.ARTIST)
                    val al = c.getColumnIndexOrThrow(Media.ALBUM)
                    val alId = c.getColumnIndexOrThrow(Media.ALBUM_ID)
                    val d = c.getColumnIndexOrThrow(Media.DURATION)
                    val p = c.getColumnIndexOrThrow(Media.DATA)
                    while (c.moveToNext()) {
                        val artistName = c.getString(a)?.takeUnless { it == "<unknown>" } ?: "Artista desconhecido"
                        val albumName = c.getString(al)?.takeUnless { it == "<unknown>" } ?: "Álbum desconhecido"
                        val path = c.getString(p) ?: ""
                        val folderPath = path.substringBeforeLast('/', "")
                        val folderName = folderPath.substringAfterLast('/').ifEmpty { "Sem pasta" }
                        list += Track(c.getLong(id), c.getString(t) ?: "Sem título", artistName, albumName, c.getLong(alId), c.getLong(d), folderPath, folderName)
                    }
                }
            } catch (e: Exception) { /* permissão negada ou erro do MediaStore: lista vazia */ }
            withContext(Dispatchers.Main) { tracks = list; syncOrRestore(); refreshCurrent() }
        }
    }

    // ---------- Controles ----------
    /** Toca uma lista (por exemplo, só as músicas de uma pasta) a partir de [index]. */
    fun playList(list: List<Track>, index: Int) {
        val c = controller ?: return
        playError = null
        saveQueue(list)
        c.setMediaItems(list.map { mediaItem(it) }, index, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() { controller?.let { playError = null; if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seek(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; shuffle = it.shuffleModeEnabled; savePos() }
    }

    fun toggleRepeat() {
        controller?.let {
            val on = it.repeatMode == Player.REPEAT_MODE_OFF
            it.repeatMode = if (on) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            repeat = on; savePos()
        }
    }

    fun changeSpeed(v: Float) { speed = v; controller?.setPlaybackParameters(PlaybackParameters(speed, pitch)) }
    fun changePitch(v: Float) { pitch = v; controller?.setPlaybackParameters(PlaybackParameters(speed, pitch)) }

    // ---------- Atualização automática ----------
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
        savePos()
        controller?.release()
        super.onCleared()
    }
}
