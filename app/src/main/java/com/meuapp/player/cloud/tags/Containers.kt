package com.meuapp.player.cloud.tags

import com.meuapp.player.cloud.CloudConfig

/** Comentários Vorbis (FLAC, Ogg Vorbis, Opus) e bloco de imagem FLAC. */
internal object VorbisComments {
    class Result(val fields: Map<String, String>, val pictures: List<String>)

    /** [start] aponta para o tamanho do "vendor string". */
    fun parse(b: ByteArray, start: Int): Result {
        val fields = HashMap<String, String>()
        val pics = ArrayList<String>()
        var p = start
        val vendor = Bytes.le32(b, p).toInt(); p += 4 + vendor
        val n = Bytes.le32(b, p).toInt(); p += 4
        var i = 0
        while (i < n && p + 4 <= b.size) {
            val len = Bytes.le32(b, p).toInt(); p += 4
            if (len < 0 || p + len > b.size) break
            val s = String(b, p, len, Charsets.UTF_8); p += len
            val eq = s.indexOf('=')
            if (eq > 0) {
                val k = s.substring(0, eq).uppercase()
                val v = s.substring(eq + 1)
                if (k == "METADATA_BLOCK_PICTURE") pics += v else if (k !in fields) fields[k] = v
            }
            i++
        }
        return Result(fields, pics)
    }

    fun apply(r: Result, into: TrackTags) {
        fun f(k: String) = r.fields[k]?.trim()?.ifBlank { null }
        if (into.title == null) into.title = f("TITLE")
        if (into.artist == null) into.artist = f("ARTIST")
        if (into.album == null) into.album = f("ALBUM")
        if (into.albumArtist == null) into.albumArtist = f("ALBUMARTIST")
        if (into.genre == null) into.genre = f("GENRE")
    }

    /** Bloco PICTURE do FLAC (também usado em base64 pelo Ogg). Devolve (tipo, bytes). */
    fun parsePicture(b: ByteArray): Pair<Int, ByteArray>? {
        var p = 0
        val type = Bytes.be32(b, p).toInt(); p += 4
        val mimeLen = Bytes.be32(b, p).toInt(); p += 4 + mimeLen
        val descLen = Bytes.be32(b, p).toInt(); p += 4 + descLen
        p += 16                                           // largura, altura, profundidade, cores
        val len = Bytes.be32(b, p).toInt(); p += 4
        if (len <= 0 || len > CloudConfig.MAX_COVER_BYTES || p + len > b.size) return null
        return type to b.copyOfRange(p, p + len)
    }
}

internal object Flac {
    fun read(src: RangeSource, into: TrackTags) {
        val magic = src.read(0, 4)
        if (!Bytes.startsWith(magic, 0, "fLaC")) return
        var pos = 4L
        var bestPic = -1
        var guard = 0
        while (pos + 4 <= src.size && guard++ < 64) {
            val h = src.read(pos, 4)
            if (h.size < 4) break
            val last = (Bytes.u8(h, 0) and 0x80) != 0
            val type = Bytes.u8(h, 0) and 0x7F
            val len = Bytes.be24(h, 1)
            val body = pos + 4
            when (type) {
                0 -> {
                    val d = src.read(body, minOf(len, 34))
                    if (d.size >= 18) {
                        val sr = (Bytes.u8(d, 10) shl 12) or (Bytes.u8(d, 11) shl 4) or (Bytes.u8(d, 12) shr 4)
                        val total = ((Bytes.u8(d, 13) and 0x0F).toLong() shl 32) or Bytes.be32(d, 14)
                        if (sr > 0 && total > 0) into.durationMs = total * 1000L / sr
                    }
                }
                4 -> if (len <= CloudConfig.MAX_TAG_BYTES) {
                    val d = src.read(body, len)
                    VorbisComments.apply(VorbisComments.parse(d, 0), into)
                }
                6 -> if (len <= CloudConfig.MAX_COVER_BYTES + 4096) {
                    val d = src.read(body, len)
                    val pic = VorbisComments.parsePicture(d)
                    if (pic != null) {
                        val rank = if (pic.first == 3) 2 else 1
                        if (rank > bestPic) { bestPic = rank; into.cover = pic.second }
                    }
                }
            }
            if (last) break
            pos = body + len
        }
    }
}

internal object Ogg {
    private class Page(val granule: Long, val segments: IntArray, val bodyStart: Int, val end: Int)

    fun read(src: RangeSource, into: TrackTags) {
        var packets: List<ByteArray> = emptyList()
        for (window in intArrayOf(128 * 1024, CloudConfig.MAX_TAG_BYTES)) {
            val buf = src.read(0, window)
            packets = firstPackets(buf, 2)
            if (packets.size >= 2 || buf.size < window) break
        }
        if (packets.size < 2) return
        val id = packets[0]; val com = packets[1]
        var sampleRate = 0L
        var preSkip = 0L
        var opus = false
        if (Bytes.startsWith(id, 0, "OpusHead") && Bytes.startsWith(com, 0, "OpusTags")) {
            opus = true
            preSkip = Bytes.le16(id, 10).toLong()
            VorbisComments.parse(com, 8).also { applyAll(it, into) }
        } else if (id.size > 16 && Bytes.u8(id, 0) == 1 && Bytes.startsWith(id, 1, "vorbis") &&
            Bytes.u8(com, 0) == 3 && Bytes.startsWith(com, 1, "vorbis")) {
            sampleRate = Bytes.le32(id, 12)
            VorbisComments.parse(com, 7).also { applyAll(it, into) }
        } else return
        // Duração: granule position da última página do arquivo (leitura do final).
        val tailLen = minOf(src.size, 64L * 1024).toInt()
        val tail = src.read(src.size - tailLen, tailLen)
        val idx = Bytes.lastIndexOf(tail, "OggS")
        if (idx >= 0 && idx + 14 <= tail.size) {
            val g = Bytes.le64(tail, idx + 6)
            if (g > 0) {
                into.durationMs = if (opus) ((g - preSkip).coerceAtLeast(0) * 1000L / 48000L)
                else if (sampleRate > 0) g * 1000L / sampleRate else 0L
            }
        }
    }

    private fun applyAll(r: VorbisComments.Result, into: TrackTags) {
        VorbisComments.apply(r, into)
        // Capa embutida em base64 (padrão METADATA_BLOCK_PICTURE)
        var best = -1
        for (b64 in r.pictures) {
            if (b64.length > CloudConfig.MAX_COVER_BYTES * 2) continue
            val raw = try { java.util.Base64.getMimeDecoder().decode(b64) } catch (e: Exception) { continue }
            val pic = try { VorbisComments.parsePicture(raw) } catch (e: Exception) { null } ?: continue
            val rank = if (pic.first == 3) 2 else 1
            if (rank > best) { best = rank; into.cover = pic.second }
        }
    }

    /** Remonta os [count] primeiros pacotes lógicos a partir das páginas Ogg do buffer. */
    private fun firstPackets(buf: ByteArray, count: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var cur = java.io.ByteArrayOutputStream()
        var p = 0
        var serial = Long.MIN_VALUE
        while (p + 27 <= buf.size && out.size < count) {
            if (!Bytes.startsWith(buf, p, "OggS")) return out
            val s = Bytes.le32(buf, p + 14)
            val nseg = Bytes.u8(buf, p + 26)
            if (p + 27 + nseg > buf.size) return out
            if (serial == Long.MIN_VALUE) serial = s
            var bodyLen = 0
            for (i in 0 until nseg) bodyLen += Bytes.u8(buf, p + 27 + i)
            val bodyStart = p + 27 + nseg
            if (s != serial) { p = bodyStart + bodyLen; continue }
            var off = bodyStart
            for (i in 0 until nseg) {
                val lace = Bytes.u8(buf, p + 27 + i)
                if (off + lace > buf.size) return out        // página cortada pelo buffer
                cur.write(buf, off, lace); off += lace
                if (lace < 255) { out += cur.toByteArray(); cur = java.io.ByteArrayOutputStream(); if (out.size >= count) return out }
            }
            p = bodyStart + bodyLen
        }
        return out
    }
}

internal object Mp4 {
    private class Atom(val type: String, val bodyStart: Int, val end: Int)

    fun read(src: RangeSource, into: TrackTags) {
        var pos = 0L
        var guard = 0
        while (pos + 8 <= src.size && guard++ < 64) {
            val h = src.read(pos, 16)
            if (h.size < 8) return
            var size = Bytes.be32(h, 0)
            val type = Bytes.ascii(h, 4, 4)
            var hdr = 8
            if (size == 1L && h.size >= 16) { size = Bytes.be64(h, 8); hdr = 16 }
            if (size == 0L) size = src.size - pos
            if (size < hdr) return
            if (type == "moov") {
                val bodyLen = size - hdr
                if (bodyLen > CloudConfig.MAX_TAG_BYTES * 2L) return
                parseMoov(src.read(pos + hdr, bodyLen.toInt()), into)
                return
            }
            pos += size
        }
    }

    private fun children(b: ByteArray, start: Int, end: Int): List<Atom> {
        val list = ArrayList<Atom>()
        var p = start
        while (p + 8 <= end) {
            val size = Bytes.be32(b, p).toInt()
            if (size < 8 || p + size > end) break
            list += Atom(Bytes.ascii(b, p + 4, 4), p + 8, p + size)
            p += size
        }
        return list
    }

    private fun parseMoov(b: ByteArray, into: TrackTags) {
        val top = children(b, 0, b.size)
        top.firstOrNull { it.type == "mvhd" }?.let { a ->
            val v = Bytes.u8(b, a.bodyStart)
            val ts: Long; val dur: Long
            if (v == 1) { ts = Bytes.be32(b, a.bodyStart + 20); dur = Bytes.be64(b, a.bodyStart + 24) }
            else { ts = Bytes.be32(b, a.bodyStart + 12); dur = Bytes.be32(b, a.bodyStart + 16) }
            if (ts > 0 && dur > 0) into.durationMs = dur * 1000L / ts
        }
        val udta = top.firstOrNull { it.type == "udta" } ?: return
        val meta = children(b, udta.bodyStart, udta.end).firstOrNull { it.type == "meta" } ?: return
        val ilst = children(b, meta.bodyStart + 4, meta.end).firstOrNull { it.type == "ilst" } ?: return
        for (item in children(b, ilst.bodyStart, ilst.end)) {
            val data = children(b, item.bodyStart, item.end).firstOrNull { it.type == "data" } ?: continue
            val flags = Bytes.be24(b, data.bodyStart + 1)
            val payloadStart = data.bodyStart + 8
            if (payloadStart > data.end) continue
            when (item.type) {
                "\u00A9nam" -> if (into.title == null) into.title = str(b, payloadStart, data.end)
                "\u00A9ART" -> if (into.artist == null) into.artist = str(b, payloadStart, data.end)
                "\u00A9alb" -> if (into.album == null) into.album = str(b, payloadStart, data.end)
                "aART" -> if (into.albumArtist == null) into.albumArtist = str(b, payloadStart, data.end)
                "\u00A9gen" -> if (into.genre == null) into.genre = str(b, payloadStart, data.end)
                "gnre" -> if (into.genre == null && data.end - payloadStart >= 2)
                    into.genre = Genres.byIndex(Bytes.be16(b, payloadStart) - 1).ifBlank { null }
                "covr" -> if ((flags == 13 || flags == 14) && data.end - payloadStart in 1..CloudConfig.MAX_COVER_BYTES && into.cover == null)
                    into.cover = b.copyOfRange(payloadStart, data.end)
            }
        }
    }

    private fun str(b: ByteArray, from: Int, to: Int) = String(b, from, to - from, Charsets.UTF_8).trim().ifBlank { null }
}

internal object Wav {
    fun read(src: RangeSource, into: TrackTags) {
        val b = src.read(0, CloudConfig.HEAD_CHUNK)
        if (b.size < 12 || !Bytes.startsWith(b, 0, "RIFF") || !Bytes.startsWith(b, 8, "WAVE")) return
        var p = 12
        var byteRate = 0L
        var dataLen = -1L
        while (p + 8 <= b.size) {
            val id = Bytes.ascii(b, p, 4)
            val len = Bytes.le32(b, p + 4)
            val body = p + 8
            when (id) {
                "fmt " -> if (body + 12 <= b.size) byteRate = Bytes.le32(b, body + 8)
                "data" -> {
                    val avail = src.size - body
                    dataLen = if (len == 0L || len > avail) avail else len
                }
                "LIST" -> if (body + 4 <= b.size && Bytes.startsWith(b, body, "INFO"))
                    info(b, body + 4, minOf(b.size.toLong(), body + len).toInt(), into)
            }
            if (byteRate > 0 && dataLen >= 0) break
            if (id == "data") break                                  // não dá para pular o áudio com o buffer atual
            val next = body + len + (len and 1L)
            if (next > b.size) break
            p = next.toInt()
        }
        if (byteRate > 0 && dataLen >= 0) into.durationMs = dataLen * 1000L / byteRate
    }

    private fun info(b: ByteArray, start: Int, end: Int, into: TrackTags) {
        var p = start
        while (p + 8 <= end) {
            val id = Bytes.ascii(b, p, 4)
            val len = Bytes.le32(b, p + 4).toInt()
            val s = p + 8
            if (len < 0 || s + len > end) break
            val v = String(b, s, len, Charsets.UTF_8).substringBefore('\u0000').trim().ifBlank { null }
            when (id) {
                "INAM" -> if (into.title == null) into.title = v
                "IART" -> if (into.artist == null) into.artist = v
                "IPRD" -> if (into.album == null) into.album = v
                "IGNR" -> if (into.genre == null) into.genre = v
            }
            p = s + len + (len and 1)
        }
    }
}
