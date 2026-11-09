package com.meuapp.player.cloud

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.TreeSet

/**
 * Armazenamento local do Toxic Cloud:
 *  - índice e downloads offline em filesDir (não são apagados pelo sistema);
 *  - capas e cache de streaming em cacheDir (o usuário pode limpar; o sistema pode reclamar espaço).
 */
@OptIn(UnstableApi::class)
object CloudStorage {
    private lateinit var app: Context
    @Volatile private var cacheRef: SimpleCache? = null
    private val evictor = DynamicLruEvictor(CloudConfig.DEFAULT_CACHE_MB * 1024L * 1024L)

    fun init(ctx: Context) {
        if (!::app.isInitialized) app = ctx.applicationContext
        evictor.maxBytes = cacheLimitMb() * 1024L * 1024L
    }

    private val prefs get() = app.getSharedPreferences("toxic_cloud", Context.MODE_PRIVATE)

    // ---------- diretórios ----------
    val dataDir: File get() = File(app.filesDir, "toxic_cloud").apply { mkdirs() }
    val offlineDir: File get() = File(dataDir, "offline").apply { mkdirs() }
    private val coversDir: File get() = File(app.cacheDir, "toxic_cloud/covers").apply { mkdirs() }
    private val audioCacheDir: File get() = File(app.cacheDir, "toxic_cloud/audio")

    // ---------- configurações ----------
    fun cacheLimitMb(): Int = prefs.getInt("cache_limit_mb", CloudConfig.DEFAULT_CACHE_MB)
        .let { if (it in CloudConfig.CACHE_LIMIT_OPTIONS_MB) it else CloudConfig.DEFAULT_CACHE_MB }

    /** Vale imediatamente: o evictor consulta o novo limite e remove o excedente na próxima gravação. */
    fun setCacheLimitMb(mb: Int) {
        prefs.edit().putInt("cache_limit_mb", mb).apply()
        evictor.maxBytes = mb * 1024L * 1024L
        trimNow()
    }

    // ---------- cache de streaming (SimpleCache) ----------
    @Synchronized
    fun audioCache(): SimpleCache {
        cacheRef?.let { return it }
        val c = SimpleCache(audioCacheDir.apply { mkdirs() }, evictor, StandaloneDatabaseProvider(app))
        cacheRef = c
        return c
    }

    /** A chave do cache é a própria URI interna (sem token). */
    fun cacheKey(fileId: String) = CloudConfig.playbackUri(fileId)

    fun isCached(fileId: String, size: Long): Boolean = try {
        size > 0 && audioCache().isCached(cacheKey(fileId), 0, size)
    } catch (e: Exception) { false }

    fun audioCacheBytes(): Long = try { audioCache().cacheSpace } catch (e: Exception) { 0L }

    fun clearAudioCache() {
        try {
            val c = audioCache()
            for (k in HashSet(c.keys)) c.removeResource(k)
        } catch (e: Exception) { /* cache indisponível: nada a limpar */ }
    }

    /** Remove do cache de streaming os bytes de um arquivo (usado quando o arquivo muda ou some no Drive). */
    fun evictAudio(fileId: String) {
        try { audioCache().removeResource(cacheKey(fileId)) } catch (e: Exception) { }
    }

    private fun trimNow() {
        try {
            val c = audioCache()
            // Remove recursos inteiros (os de chave mais antiga primeiro não é garantido pela API; o evictor cuida do LRU fino).
            if (c.cacheSpace > evictor.maxBytes) {
                for (k in HashSet(c.keys)) {
                    if (c.cacheSpace <= evictor.maxBytes) break
                    c.removeResource(k)
                }
            }
        } catch (e: Exception) { }
    }

    // ---------- capas ----------
    fun coverFile(fileId: String): File = File(coversDir, safe(fileId) + ".img")
    fun saveCover(fileId: String, bytes: ByteArray): Boolean = try {
        val f = coverFile(fileId); val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes); tmp.renameTo(f)
    } catch (e: Exception) { false }
    fun coverStamp(fileId: String): Long = coverFile(fileId).let { if (it.exists()) it.lastModified() else 0L }
    fun coverUri(fileId: String): Uri? = coverFile(fileId).takeIf { it.exists() }?.let { Uri.fromFile(it) }
    fun deleteCover(fileId: String) { coverFile(fileId).delete() }
    fun coversBytes(): Long = dirSize(coversDir)
    fun clearCovers() { coversDir.listFiles()?.forEach { it.delete() } }

    // ---------- offline ----------
    fun offlineFile(fileId: String, fmt: CloudFormat): File = File(offlineDir, safe(fileId) + "." + fmt.extensions.first())
    fun offlinePartFile(fileId: String): File = File(offlineDir, safe(fileId) + ".part")

    /** ids com download concluído (lidos do disco: a pasta é a fonte da verdade). */
    fun offlineIds(): Set<String> =
        offlineDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.map { it.name.substringBeforeLast('.') }?.toSet()
            ?: emptySet()

    fun offlineBytes(): Long = dirSize(offlineDir)
    fun deleteOffline(fileId: String) {
        offlineDir.listFiles()?.filter { it.name.substringBeforeLast('.') == safe(fileId) }?.forEach { it.delete() }
    }
    fun clearOffline() { offlineDir.listFiles()?.forEach { it.delete() } }

    fun freeSpaceBytes(): Long = try { offlineDir.usableSpace } catch (e: Exception) { Long.MAX_VALUE }

    private fun dirSize(d: File): Long = d.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}

/** LRU com limite que pode mudar em tempo de execução (o LeastRecentlyUsedCacheEvictor do Media3 tem limite fixo). */
@OptIn(UnstableApi::class)
class DynamicLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {
    private val lru = TreeSet<CacheSpan> { a, b ->
        val d = a.lastTouchTimestamp - b.lastTouchTimestamp
        if (d == 0L) a.compareTo(b) else if (d < 0) -1 else 1
    }
    private var current = 0L

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() {}
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) { lru.add(span); current += span.length; evict(cache, 0) }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) { lru.remove(span); current -= span.length }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan); onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, required: Long) {
        while (current + required > maxBytes && lru.isNotEmpty()) cache.removeSpan(lru.first())
    }
}
