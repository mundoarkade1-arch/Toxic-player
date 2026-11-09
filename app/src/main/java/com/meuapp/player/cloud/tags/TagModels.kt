package com.meuapp.player.cloud.tags

/** Fonte de bytes com leitura parcial. No app lê do Drive por HTTP Range; nos testes lê de um ByteArray. */
interface RangeSource {
    val size: Long
    /** Lê até [length] bytes a partir de [offset] (menos, se passar do fim). */
    fun read(offset: Long, length: Int): ByteArray
}

class ByteArraySource(private val data: ByteArray) : RangeSource {
    override val size: Long get() = data.size.toLong()
    var reads = 0; private set
    override fun read(offset: Long, length: Int): ByteArray {
        reads++
        if (offset < 0 || offset >= data.size || length <= 0) return ByteArray(0)
        val end = minOf(data.size.toLong(), offset + length).toInt()
        return data.copyOfRange(offset.toInt(), end)
    }
}

/** Metadados extraídos. Campos nulos/zero = não encontrado. */
class TrackTags(
    var title: String? = null,
    var artist: String? = null,
    var album: String? = null,
    var albumArtist: String? = null,
    var durationMs: Long = 0L,
    var cover: ByteArray? = null
) {
    fun isEmpty() = title == null && artist == null && album == null && durationMs == 0L && cover == null
}

/** Leitura de inteiros com verificação de limites (IndexOutOfBounds é tratado pelo TagReader como "sem tags"). */
internal object Bytes {
    fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
    fun be16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    fun be24(b: ByteArray, i: Int) = (u8(b, i) shl 16) or (u8(b, i + 1) shl 8) or u8(b, i + 2)
    fun be32(b: ByteArray, i: Int): Long =
        (u8(b, i).toLong() shl 24) or (u8(b, i + 1).toLong() shl 16) or (u8(b, i + 2).toLong() shl 8) or u8(b, i + 3).toLong()
    fun le16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
    fun le32(b: ByteArray, i: Int): Long =
        u8(b, i).toLong() or (u8(b, i + 1).toLong() shl 8) or (u8(b, i + 2).toLong() shl 16) or (u8(b, i + 3).toLong() shl 24)
    fun le64(b: ByteArray, i: Int): Long = le32(b, i) or (le32(b, i + 4) shl 32)
    fun be64(b: ByteArray, i: Int): Long = (be32(b, i) shl 32) or be32(b, i + 4)
    fun ascii(b: ByteArray, i: Int, n: Int): String = String(b, i, n, Charsets.ISO_8859_1)
    fun startsWith(b: ByteArray, i: Int, s: String): Boolean {
        if (i < 0 || i + s.length > b.size) return false
        for (k in s.indices) if ((b[i + k].toInt() and 0xFF) != s[k].code) return false
        return true
    }
    fun indexOf(b: ByteArray, s: String, from: Int = 0): Int {
        var i = from
        while (i + s.length <= b.size) { if (startsWith(b, i, s)) return i; i++ }
        return -1
    }
    fun lastIndexOf(b: ByteArray, s: String): Int {
        var i = b.size - s.length
        while (i >= 0) { if (startsWith(b, i, s)) return i; i-- }
        return -1
    }
}

/** Heurística de nome de arquivo quando não há tags: remove extensão e número de faixa; "Artista - Título" vira dois campos. */
object NameParser {
    data class Parsed(val artist: String?, val title: String)

    fun parse(fileName: String): Parsed {
        var base = fileName.substringBeforeLast('.', fileName).trim()
        base = base.replace('_', ' ')
        base = base.replace(Regex("^\\(?\\d{1,3}\\)?\\s*[-.)]*\\s+"), "").trim()
        val parts = base.split(" - ")
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
            Parsed(parts[0].trim(), parts[1].trim())
        else Parsed(null, base.ifBlank { fileName })
    }
}
