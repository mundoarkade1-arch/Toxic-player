package com.meuapp.player.cloud

import android.content.Context
import com.meuapp.player.cloud.tags.TagReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Progresso exibido na interface. */
sealed class SyncProgress {
    object Idle : SyncProgress()
    data class Listing(val found: Int) : SyncProgress()
    data class Enriching(val done: Int, val total: Int) : SyncProgress()
}

/** Versão do esquema do índice (3 = gênero, data de adição e pasta). */
private const val SCHEMA = 3

/** Resultado de uma sincronização. */
data class SyncResult(val added: Int, val modified: Int, val removed: Int, val full: Boolean, val enrichError: CloudErrorKind?)

/**
 * Índice local da biblioteca + sincronização (completa e incremental via Changes API) + leitura de metadados.
 * Tudo roda no Android, sem servidor. A interface é thread-safe: o mapa é protegido por [lock].
 */
class CloudRepository(ctx: Context, private val api: DriveApi = DriveApi()) {
    private val store = CloudStore(CloudStorage.dataDir)
    private val prefs = ctx.applicationContext.getSharedPreferences("toxic_cloud", Context.MODE_PRIVATE)
    private val lock = Any()
    private val map = LinkedHashMap<String, CloudTrack>()
    private val folders = HashMap<String, DriveFolder>()

    // ---------- estado persistido ----------
    var lastSyncMs: Long
        get() = prefs.getLong("last_sync", 0L)
        private set(v) { prefs.edit().putLong("last_sync", v).apply() }
    private var changesToken: String?
        get() = prefs.getString("changes_token", null)
        set(v) { prefs.edit().putString("changes_token", v).apply() }

    fun loadIndex(): List<CloudTrack> {
        val l = store.load()
        val f = store.loadFolders()
        synchronized(lock) { map.clear(); l.forEach { map[it.id] = it }; folders.clear(); f.forEach { folders[it.id] = it } }
        if (l.isEmpty()) prefs.edit().putInt("schema", SCHEMA).apply()
        return l
    }

    fun snapshot(): List<CloudTrack> = synchronized(lock) { map.values.toList() }

    /** Índice de versão antiga (sem data de adição): pede uma varredura completa para completar os dados. */
    fun needsMigration(): Boolean = prefs.getInt("schema", 0) < SCHEMA && synchronized(lock) { map.isNotEmpty() }

    /** Caminho de pasta de cada pasta-pai usada pelas músicas. */
    fun pathsFor(list: List<CloudTrack>): Map<String, String> = synchronized(lock) {
        list.map { it.parentId }.distinct().associateWith { CloudFolders.pathOf(it, folders) }
    }

    private fun refreshFolders() {
        val all = ArrayList<DriveFolder>()
        var page: String? = null
        do { val p = api.listFolders(page); all += p.first; page = p.second } while (page != null)
        synchronized(lock) { folders.clear(); all.forEach { folders[it.id] = it } }
        store.saveFolders(all)
    }

    /** Atualiza data de adição e pasta das músicas existentes (inclui arquivos movidos de pasta). */
    private fun applyParents(files: List<DriveFile>) {
        synchronized(lock) {
            for (f in files) {
                val l = map[f.id] ?: continue
                var n = l
                if (l.addedMs == 0L) { val a = CloudTrack.addedOf(f); if (a > 0) n = n.copy(addedMs = a) }
                if (l.parentId != f.parentId) n = n.copy(parentId = f.parentId)
                if (n !== l) map[f.id] = n
            }
        }
    }

    private fun persist() { val s = snapshot(); store.save(s) }

    /** Esquece a biblioteca local (desconexão / troca de conta). Não apaga nada no Drive. */
    fun clearAll() {
        synchronized(lock) { map.clear() }
        synchronized(lock) { folders.clear() }
        store.clear()
        changesToken = null
        lastSyncMs = 0L
    }

    // ---------- sincronização ----------
    /**
     * [forceFull] = varredura completa ("Atualizar biblioteca"). Caso contrário usa o feed de mudanças quando há token.
     * Depois de aplicar as mudanças, lê os metadados (tags) das músicas pendentes.
     */
    suspend fun sync(forceFull: Boolean, onProgress: (SyncProgress) -> Unit, onChanged: () -> Unit): SyncResult =
        withContext(Dispatchers.IO) {
            val token = changesToken
            var full = forceFull || token == null || synchronized(lock) { map.isEmpty() }
            var plan: SyncPlan? = null
            var seen: List<DriveFile> = emptyList()
            var newToken: String? = null
            if (!full) {
                try {
                    val acc = ArrayList<DriveChange>()
                    var page: String? = token
                    while (page != null) {
                        ensureActive()
                        val p = api.changes(page)
                        acc += p.changes
                        onProgress(SyncProgress.Listing(acc.size))
                        if (p.nextPageToken != null) page = p.nextPageToken
                        else { newToken = p.newStartPageToken; page = null }
                    }
                    plan = SyncPlanner.planChanges(synchronized(lock) { HashMap(map) }, acc)
                    seen = acc.mapNotNull { it.file }
                } catch (e: CloudException) {
                    // Token de mudanças inválido/expirado: recomeça com varredura completa. Outros erros sobem.
                    if (e.httpCode == 400 || e.httpCode == 404 || e.httpCode == 410) full = true else throw e
                }
            }
            if (full) {
                val startToken = api.startPageToken()          // obtido ANTES de listar: nada se perde durante a varredura
                val all = ArrayList<DriveFile>()
                var page: String? = null
                do {
                    ensureActive()
                    val p = api.listAudio(page)
                    all += p.files
                    onProgress(SyncProgress.Listing(all.size))
                    page = p.nextPageToken
                } while (page != null)
                seen = all
                plan = SyncPlanner.planFull(synchronized(lock) { HashMap(map) }, all)
                newToken = startToken
            }
            val pl = plan!!
            apply(pl)
            applyParents(seen)
            // Pastas: falha aqui não impede a sincronização das músicas (o caminho só fica desatualizado).
            try { if (synchronized(lock) { map.isNotEmpty() }) refreshFolders() } catch (e: CancellationException) { throw e } catch (e: CloudException) { }
            if (full) prefs.edit().putInt("schema", SCHEMA).apply()
            persist()
            changesToken = newToken ?: changesToken
            lastSyncMs = System.currentTimeMillis()
            onChanged()
            val err = enrichPending(onProgress, onChanged)
            onProgress(SyncProgress.Idle)
            SyncResult(pl.added.size, pl.modified.size, pl.removedIds.size, full, err)
        }

    private fun apply(plan: SyncPlan) {
        synchronized(lock) {
            for (f in plan.added) map[f.id] = CloudTrack.fromDrive(f)
            for (f in plan.modified) {
                val old = map[f.id]
                // Mantém os metadados atuais até a releitura; marca como pendente.
                map[f.id] = (old ?: CloudTrack.fromDrive(f)).copy(
                    fileName = f.name, size = f.size, md5 = f.md5, modifiedTime = f.modifiedTime, format = f.format,
                    metaState = MetaState.PENDING
                )
            }
            for (id in plan.removedIds) map.remove(id)
        }
        // Arquivos alterados ou removidos: descarta bytes antigos para nunca tocar conteúdo defasado.
        for (f in plan.modified) { CloudStorage.evictAudio(f.id); CloudStorage.deleteOffline(f.id) }
        for (id in plan.removedIds) { CloudStorage.evictAudio(id); CloudStorage.deleteOffline(id); CloudStorage.deleteCover(id) }
    }

    // ---------- metadados ----------
    /** Lê tags das músicas pendentes, poucas por vez. Devolve o tipo de erro que interrompeu o processo, se houve. */
    suspend fun enrichPending(onProgress: (SyncProgress) -> Unit, onChanged: () -> Unit): CloudErrorKind? =
        withContext(Dispatchers.IO) {
            val pending = synchronized(lock) { map.values.filter { it.metaState == MetaState.PENDING } }
            if (pending.isEmpty()) return@withContext null
            val done = AtomicInteger(0)
            var abort: CloudErrorKind? = null
            var lastFlush = System.currentTimeMillis()
            val gate = Semaphore(CloudConfig.ENRICH_PARALLELISM)
            onProgress(SyncProgress.Enriching(0, pending.size))
            try {
                coroutineScope {
                    pending.map { t ->
                        async {
                            gate.withPermit {
                                ensureActive()
                                if (abort != null) return@withPermit
                                try {
                                    enrichOne(t)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: CloudException) {
                                    when (e.kind) {
                                        CloudErrorKind.NO_INTERNET, CloudErrorKind.TOKEN_EXPIRED, CloudErrorKind.RATE_LIMITED,
                                        CloudErrorKind.NOT_CONNECTED, CloudErrorKind.ACCESS_REVOKED -> abort = e.kind
                                        else -> markFailed(t.id)
                                    }
                                } catch (e: Exception) {
                                    markFailed(t.id)
                                }
                                val n = done.incrementAndGet()
                                onProgress(SyncProgress.Enriching(n, pending.size))
                                val now = System.currentTimeMillis()
                                if (n % 25 == 0 || now - lastFlush > 3000) {
                                    lastFlush = now; persist(); onChanged()
                                }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                persist(); onChanged()
            }
            abort
        }

    private fun markFailed(id: String) {
        synchronized(lock) { map[id]?.let { map[id] = it.copy(metaState = MetaState.FAILED) } }
    }

    private fun enrichOne(t: CloudTrack) {
        val src = DriveRangeSource(api, t.id, t.size)
        val tags = TagReader.read(t.format, src)
        var hasCover = false
        tags.cover?.let { hasCover = CloudStorage.saveCover(t.id, it) }
        synchronized(lock) {
            val cur = map[t.id] ?: return
            map[t.id] = cur.copy(
                title = tags.title ?: cur.title,
                artist = tags.artist ?: tags.albumArtist ?: cur.artist,
                album = tags.album ?: cur.album,
                genre = com.meuapp.player.cloud.tags.Genres.clean(tags.genre),
                durationMs = if (tags.durationMs > 0) tags.durationMs else cur.durationMs,
                hasCover = hasCover || (cur.hasCover && CloudStorage.coverFile(t.id).exists()),
                metaState = MetaState.DONE
            )
        }
    }

    /** Depois de limpar capas do cache: marca as músicas que tinham capa para serem relidas. */
    fun requeueCovers() {
        synchronized(lock) {
            for ((id, t) in map.entries.toList()) if (t.hasCover) map[id] = t.copy(hasCover = false, metaState = MetaState.PENDING)
        }
        persist()
    }

    /** Tenta de novo as músicas cuja leitura falhou. */
    fun requeueFailed() {
        synchronized(lock) {
            for ((id, t) in map.entries.toList()) if (t.metaState == MetaState.FAILED) map[id] = t.copy(metaState = MetaState.PENDING)
        }
    }
}
