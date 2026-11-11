package com.meuapp.player.cloud

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.meuapp.player.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado e ações do Toxic Cloud para a interface. Pertence ao PlayerViewModel (mesmo escopo de vida).
 * Todas as operações de rede/disco rodam em Dispatchers.IO; os campos observáveis são estados do Compose.
 */
class CloudManager(private val app: Application, private val scope: CoroutineScope) {
    private val api = DriveApi()
    private val repo: CloudRepository
    private val prefs = app.getSharedPreferences("toxic_cloud", Context.MODE_PRIVATE)

    // ---------- conexão ----------
    var connected by mutableStateOf(false); private set
    var accountName by mutableStateOf(""); private set
    var accountEmail by mutableStateOf(""); private set
    var usageBytes by mutableStateOf(0L); private set
    var limitBytes by mutableStateOf(0L); private set
    var pendingAuth by mutableStateOf<PendingIntent?>(null); private set
    var connecting by mutableStateOf(false); private set

    // ---------- biblioteca ----------
    var tracks by mutableStateOf<List<CloudTrack>>(emptyList()); private set
    var playerTracks by mutableStateOf<List<Track>>(emptyList()); private set
    private var byNumeric: Map<Long, Track> = emptyMap()
    private var byId: Map<String, CloudTrack> = emptyMap()
    var offlineIds by mutableStateOf<Set<String>>(emptySet()); private set
    var folderPaths by mutableStateOf<Map<String, String>>(emptyMap()); private set
    val downloads = mutableStateMapOf<String, Float>()
    private val downloadJobs = HashMap<String, Job>()

    // ---------- sincronização ----------
    var syncing by mutableStateOf(false); private set
    var progress by mutableStateOf<SyncProgress>(SyncProgress.Idle); private set
    var lastSyncMs by mutableStateOf(0L); private set
    var message by mutableStateOf<String?>(null); private set
    var diagnostic by mutableStateOf<String?>(null); private set
    private var syncJob: Job? = null

    // ---------- armazenamento ----------
    var cacheBytes by mutableStateOf(0L); private set
    var coversBytes by mutableStateOf(0L); private set
    var offlineBytes by mutableStateOf(0L); private set
    var cacheLimitMb by mutableIntStateOf(CloudConfig.DEFAULT_CACHE_MB); private set

    // ---------- filtros da interface ----------
    var query by mutableStateOf(""); private set
    var sortMode by mutableIntStateOf(CloudLibrary.SORT_TITLE); private set
    var formatFilter by mutableStateOf<CloudFormat?>(null); private set
    var offlineOnly by mutableStateOf(false); private set
    var viewMode by mutableIntStateOf(0); private set        // 0 músicas, 1 artistas, 2 álbuns
    var openGroup by mutableStateOf<String?>(null); private set

    fun changeQuery(q: String) { query = q }
    fun changeSort(m: Int) { sortMode = m }
    fun changeFormat(f: CloudFormat?) { formatFilter = f }
    fun toggleOfflineOnly() { offlineOnly = !offlineOnly }
    fun changeView(m: Int) { viewMode = m; openGroup = null }
    fun enterGroup(k: String?) { openGroup = k }
    fun dismissMessage() { message = null }

    /** O player só restaura a fila depois de conhecer as músicas da nuvem. */
    var indexLoaded = false; private set
    var onIndexLoaded: (() -> Unit)? = null

    init {
        CloudAuth.init(app); CloudStorage.init(app)
        repo = CloudRepository(app, api)
        connected = CloudAuth.connected
        accountName = prefs.getString("acc_name", "") ?: ""
        accountEmail = prefs.getString("acc_email", "") ?: ""
        usageBytes = prefs.getLong("acc_usage", 0L); limitBytes = prefs.getLong("acc_limit", 0L)
        cacheLimitMb = CloudStorage.cacheLimitMb()
        lastSyncMs = repo.lastSyncMs
        scope.launch(Dispatchers.IO) {
            val l = repo.loadIndex()
            val off = CloudStorage.offlineIds()
            withContext(Dispatchers.Main) { applyTracks(l); offlineIds = off; indexLoaded = true; onIndexLoaded?.invoke() }
            refreshUsage()
            // Índice de versão antiga: uma varredura completa traz a data de adição e releitura traz o gênero.
            if (CloudAuth.connected && repo.needsMigration()) doSync(full = true)
        }
    }

    private fun applyTracks(l: List<CloudTrack>) {
        tracks = l
        folderPaths = repo.pathsFor(l)
        byId = l.associateBy { it.id }
        val pt = l.map(::toTrack)
        playerTracks = pt
        byNumeric = pt.associateBy { it.id }
    }

    private fun toTrack(c: CloudTrack) = Track(
        id = c.numericId, title = c.title, artist = c.artist, album = c.album, albumId = 0L, durationMs = c.durationMs,
        folderPath = "toxic-cloud", folderName = "Toxic Cloud", filePath = "", cloudId = c.id,
        coverStamp = if (c.hasCover) 1L else 0L
    )

    fun trackByNumericId(id: Long): Track? = byNumeric[id]
    fun cloudTrack(id: String): CloudTrack? = byId[id]

    /** URI de reprodução: arquivo offline se existir; senão a URI interna de streaming. */
    fun playbackUri(t: Track): Uri {
        val id = t.cloudId ?: return t.uri
        val ct = cloudTrack(id)
        if (ct != null && id in offlineIds) {
            val f = CloudStorage.offlineFile(id, ct.format)
            if (f.exists()) return Uri.fromFile(f)
        }
        return Uri.parse(CloudConfig.playbackUri(id))
    }

    fun artworkUri(t: Track): Uri? = t.cloudId?.let { CloudStorage.coverUri(it) }
    fun isOffline(id: String) = id in offlineIds
    fun isCached(c: CloudTrack): Boolean = CloudStorage.isCached(c.id, c.size)

    // ---------- conectar / desconectar ----------
    fun connect() {
        if (connecting) return
        connecting = true; message = null
        scope.launch(Dispatchers.IO) {
            try {
                when (val o = CloudAuth.authorizeBlocking()) {
                    is CloudAuth.Outcome.Granted -> afterConnected()
                    is CloudAuth.Outcome.NeedsUi -> withContext(Dispatchers.Main) { pendingAuth = o.pendingIntent }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail(e) }
            finally { withContext(Dispatchers.Main) { connecting = pendingAuth != null } }
        }
    }

    fun authLaunched() { pendingAuth = null }

    /** O usuário fechou a tela de consentimento do Google sem autorizar. */
    fun onAuthCancelled() { connecting = false; message = CloudErrorKind.CANCELLED.message }

    /** Resultado da tela de consentimento do Google. */
    fun onAuthResult(data: Intent?) {
        scope.launch(Dispatchers.IO) {
            try {
                when (val o = CloudAuth.handleResult(data)) {
                    is CloudAuth.Outcome.Granted -> afterConnected()
                    is CloudAuth.Outcome.NeedsUi -> withContext(Dispatchers.Main) { pendingAuth = o.pendingIntent }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { fail(e) }
            finally { withContext(Dispatchers.Main) { connecting = false } }
        }
    }

    private suspend fun afterConnected() {
        withContext(Dispatchers.Main) { connected = true; connecting = false }
        loadAccount()
        doSync(full = true)
    }

    private suspend fun loadAccount() {
        try {
            val a = api.about()
            prefs.edit().putString("acc_name", a.displayName).putString("acc_email", a.email)
                .putLong("acc_usage", a.usageBytes).putLong("acc_limit", a.limitBytes).apply()
            withContext(Dispatchers.Main) {
                accountName = a.displayName; accountEmail = a.email; usageBytes = a.usageBytes; limitBytes = a.limitBytes
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { /* informação opcional: a falha aparece na sincronização, se persistir */ }
    }

    /** Revoga o acesso no Google e apaga índice, capas, cache e downloads desta conta. */
    fun disconnect(thenReconnect: Boolean = false) {
        syncJob?.cancel(); downloadJobs.values.forEach { it.cancel() }; downloadJobs.clear()
        scope.launch(Dispatchers.IO) {
            val revoked = CloudAuth.disconnectBlocking()
            repo.clearAll()
            CloudStorage.clearAudioCache(); CloudStorage.clearCovers(); CloudStorage.clearOffline()
            prefs.edit().remove("acc_name").remove("acc_email").remove("acc_usage").remove("acc_limit").apply()
            withContext(Dispatchers.Main) {
                connected = false; accountName = ""; accountEmail = ""; usageBytes = 0; limitBytes = 0
                applyTracks(emptyList()); offlineIds = emptySet(); downloads.clear()
                syncing = false; progress = SyncProgress.Idle; lastSyncMs = 0L; openGroup = null
                message = if (revoked) "Conta desconectada e acesso revogado no Google."
                else "Conta desconectada neste aparelho. Não consegui confirmar a revogação no Google (sem internet?); você pode removê-la em myaccount.google.com/permissions."
            }
            refreshUsage()
            if (thenReconnect) connect()
        }
    }

    /** Trocar de conta = desconectar e conectar de novo (o Google mostra a escolha de conta). */
    fun switchAccount() = disconnect(thenReconnect = true)

    // ---------- sincronização ----------
    /** Sincronização automática ao abrir o app: só se conectado e a última tiver mais de [CloudConfig.AUTO_SYNC_INTERVAL_MS]. */
    fun maybeAutoSync() {
        if (!connected || syncing) return
        if (System.currentTimeMillis() - repo.lastSyncMs > CloudConfig.AUTO_SYNC_INTERVAL_MS) sync(full = false)
    }

    fun syncNow() = sync(full = false)
    fun refreshLibrary() = sync(full = true)

    private fun sync(full: Boolean) {
        if (syncing) return
        syncJob = scope.launch(Dispatchers.IO) { doSync(full) }
    }

    private suspend fun doSync(full: Boolean) {
        withContext(Dispatchers.Main) { syncing = true; message = null }
        try {
            repo.requeueFailed()
            val r = repo.sync(full, { p -> progress = p }, { publish() })
            loadAccount()
            withContext(Dispatchers.Main) {
                lastSyncMs = repo.lastSyncMs
                message = r.enrichError?.message ?: if (r.full) "Biblioteca atualizada: ${tracks.size} músicas."
                else if (r.added + r.modified + r.removed == 0) "Biblioteca já está em dia."
                else "Sincronizado: +${r.added} novas, ${r.modified} alteradas, ${r.removed} removidas."
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val kind = CloudErrors.classify(e)
            if (kind == CloudErrorKind.ACCESS_REVOKED || kind == CloudErrorKind.NOT_CONNECTED) CloudAuth.markDisconnected()
            fail(e, fallback = CloudErrorKind.SYNC_ERROR)
        } finally {
            withContext(Dispatchers.Main) { syncing = false; progress = SyncProgress.Idle; connected = CloudAuth.connected }
            refreshUsage()
        }
    }

    private fun publish() {
        val l = repo.snapshot()
        val off = CloudStorage.offlineIds()
        scope.launch(Dispatchers.Main) { applyTracks(l); offlineIds = off }
    }

    private suspend fun fail(e: Throwable, fallback: CloudErrorKind? = null) {
        var k = CloudErrors.classify(e)
        if (k == CloudErrorKind.UNKNOWN && fallback != null) k = fallback
        withContext(Dispatchers.Main) {
            message = k.message
            if (k == CloudErrorKind.TOKEN_EXPIRED || k == CloudErrorKind.ACCESS_REVOKED) connected = CloudAuth.connected
        }
    }

    // ---------- offline ----------
    fun download(c: CloudTrack) {
        if (c.id in offlineIds || downloadJobs.containsKey(c.id)) return
        if (CloudStorage.freeSpaceBytes() < c.size + CloudConfig.FREE_SPACE_RESERVE) {
            message = CloudErrorKind.NO_SPACE.message; return
        }
        downloads[c.id] = 0f
        val job = scope.launch(Dispatchers.IO) {
            val part = CloudStorage.offlinePartFile(c.id)
            try {
                val f = DriveFile(c.id, c.fileName, "", c.size, c.modifiedTime, c.md5, c.format)
                api.download(f, part, { n -> downloads[c.id] = (n.toFloat() / c.size).coerceIn(0f, 1f) }, { !isActive })
                val dest = CloudStorage.offlineFile(c.id, c.format)
                if (!part.renameTo(dest)) throw CloudException(CloudErrorKind.NO_SPACE)
                withContext(Dispatchers.Main) { offlineIds = offlineIds + c.id }
            } catch (e: CancellationException) { part.delete(); throw e
            } catch (e: Exception) {
                part.delete()
                val k = CloudErrors.classify(e)
                if (k != CloudErrorKind.CANCELLED) withContext(Dispatchers.Main) { message = "\"${c.title}\": ${k.message}" }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { downloads.remove(c.id); downloadJobs.remove(c.id) }
                refreshUsage()
            }
        }
        downloadJobs[c.id] = job
    }

    fun downloadAll(list: List<CloudTrack>) { list.filter { it.id !in offlineIds }.forEach { download(it) } }
    fun cancelDownload(id: String) { downloadJobs[id]?.cancel() }

    fun removeOffline(id: String) {
        scope.launch(Dispatchers.IO) {
            CloudStorage.deleteOffline(id)
            withContext(Dispatchers.Main) { offlineIds = offlineIds - id }
            refreshUsage()
        }
    }

    fun clearOffline() {
        downloadJobs.values.forEach { it.cancel() }
        scope.launch(Dispatchers.IO) {
            CloudStorage.clearOffline()
            withContext(Dispatchers.Main) { offlineIds = emptySet() }
            refreshUsage()
        }
    }

    // ---------- cache ----------
    fun setCacheLimit(mb: Int) {
        cacheLimitMb = mb
        scope.launch(Dispatchers.IO) { CloudStorage.setCacheLimitMb(mb); refreshUsage() }
    }

    /** Limpa cache de streaming e capas (não toca nos downloads offline). As capas voltam a ser lidas aos poucos. */
    fun clearCache() {
        scope.launch(Dispatchers.IO) {
            CloudStorage.clearAudioCache(); CloudStorage.clearCovers()
            repo.requeueCovers()
            publish()
            refreshUsage()
            if (connected) repo.enrichPending({ p -> progress = p }, { publish() })
            progress = SyncProgress.Idle
        }
    }

    fun refreshUsage() {
        scope.launch(Dispatchers.IO) {
            val a = CloudStorage.audioCacheBytes(); val c = CloudStorage.coversBytes(); val o = CloudStorage.offlineBytes()
            withContext(Dispatchers.Main) { cacheBytes = a; coversBytes = c; offlineBytes = o }
        }
    }

    // ---------- diagnóstico de streaming ----------
    /** Pede 1 KB do primeiro arquivo e mostra se o Drive respondeu com leitura parcial (HTTP 206). */
    fun testStreaming() {
        val c = tracks.firstOrNull()
        if (c == null) { diagnostic = "Sincronize a biblioteca antes de testar."; return }
        diagnostic = "Testando..."
        scope.launch(Dispatchers.IO) {
            val text = try {
                val p = api.probeRange(c.id)
                if (p.status == 206) "Streaming OK: o Drive aceitou leitura parcial (HTTP 206, ${p.bytes} bytes${p.contentRange?.let { ", $it" } ?: ""})."
                else "Atenção: o Drive respondeu HTTP ${p.status} sem leitura parcial. O seek pode ser lento."
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { "Falha no teste: " + CloudErrors.message(e) }
            withContext(Dispatchers.Main) { diagnostic = text }
        }
    }
}
