package com.meuapp.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import java.io.File

/** Carrega a capa de uma música: miniatura do sistema, imagem embutida no arquivo ou cover.jpg da pasta. */
object CoverLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<String>()

    fun load(ctx: Context, t: Track, size: Int): Bitmap? {
        if (t.cloudId != null) return loadCloud(t, size)
        val key = "${t.id}:$size"
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        val bmp = try { fetch(ctx, t, size) } catch (e: Exception) { null }
        if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing.add(key) }
        return bmp
    }

    /** Capa da nuvem: arquivo já extraído das tags (cache local). Sem cache negativo: a capa pode chegar depois. */
    private fun loadCloud(t: Track, size: Int): Bitmap? {
        val key = "c:${t.cloudId}:${t.coverStamp}:$size"
        cache.get(key)?.let { return it }
        val f = com.meuapp.player.cloud.CloudStorage.coverFile(t.cloudId ?: return null)
        if (!f.exists()) return null
        val bmp = try {
            val o = BitmapFactory.Options().apply { inSampleSize = sample(f.absolutePath, size) }
            BitmapFactory.decodeFile(f.absolutePath, o)
        } catch (e: Exception) { null }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun fetch(ctx: Context, t: Track, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try { return ctx.contentResolver.loadThumbnail(t.uri, Size(size, size), null) } catch (e: Exception) { }
        }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, t.uri)
            val bytes = r.embeddedPicture
            if (bytes != null) decode(bytes, size)?.let { return it }
        } catch (e: Exception) {
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
        for (n in listOf("cover.jpg", "folder.jpg", "front.jpg", "albumart.jpg", "cover.png", "folder.png")) {
            val f = File(t.folderPath, n)
            if (f.exists()) {
                val o = BitmapFactory.Options().apply { inSampleSize = sample(f.absolutePath, size) }
                BitmapFactory.decodeFile(f.absolutePath, o)?.let { return it }
            }
        }
        return null
    }

    private fun sample(path: String, size: Int): Int {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return calc(o.outWidth, o.outHeight, size)
    }

    private fun calc(w: Int, h: Int, size: Int): Int {
        var s = 1
        while (w / (s * 2) >= size && h / (s * 2) >= size) s *= 2
        return s
    }

    private fun decode(bytes: ByteArray, size: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val o2 = BitmapFactory.Options().apply { inSampleSize = calc(o.outWidth, o.outHeight, size) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }

    /** Cor média da capa, usada para tingir o fundo do player. */
    fun averageColor(b: Bitmap): Int = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
}
