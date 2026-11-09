package com.meuapp.player.cloud.tags

import com.meuapp.player.cloud.CloudConfig

/** ID3v2 (2.2/2.3/2.4) no início do arquivo e ID3v1 no fim. Usado por MP3 e, às vezes, AAC. */
internal object Id3 {
    /** Tamanho total do tag ID3v2 (cabeçalho+corpo+rodapé) no início do arquivo, ou 0 se não houver. */
    fun tagLength(header: ByteArray): Int {
        if (header.size < 10 || !Bytes.startsWith(header, 0, "ID3")) return 0
        val size = synchsafe(header, 6)
        val footer = if (Bytes.u8(header, 3) >= 4 && (Bytes.u8(header, 5) and 0x10) != 0) 10 else 0
        return 10 + size + footer
    }

    private fun synchsafe(b: ByteArray, i: Int) =
        (Bytes.u8(b, i) shl 21) or (Bytes.u8(b, i + 1) shl 14) or (Bytes.u8(b, i + 2) shl 7) or Bytes.u8(b, i + 3)

    fun readV2(src: RangeSource, into: TrackTags): Int {
        val head = src.read(0, 10)
        val total = tagLength(head)
        if (total == 0) return 0
        val toRead = minOf(total.toLong(), CloudConfig.MAX_TAG_BYTES.toLong()).toInt()
        val raw = src.read(0, toRead)
        parseV2(raw, into)
        return total
    }

    fun parseV2(raw: ByteArray, into: TrackTags) {
        if (raw.size < 10) return
        val ver = Bytes.u8(raw, 3)
        if (ver !in 2..4) return
        val flags = Bytes.u8(raw, 5)
        val bodyEnd = minOf(raw.size, 10 + synchsafe(raw, 6))
        var body = raw.copyOfRange(10, bodyEnd)
        if ((flags and 0x80) != 0 && ver < 4) body = unsync(body)       // dessincronização global (2.2/2.3)
        var pos = 0
        if ((flags and 0x40) != 0 && ver >= 3) {                        // cabeçalho estendido
            val extSize = if (ver == 4) synchsafe(body, 0) else (Bytes.be32(body, 0).toInt() + 4)
            pos = extSize.coerceAtLeast(0)
        }
        val idLen = if (ver == 2) 3 else 4
        val hdrLen = if (ver == 2) 6 else 10
        var bestCoverType = -1
        while (pos + hdrLen <= body.size) {
            if (body[pos].toInt() == 0) break                           // padding
            val id = Bytes.ascii(body, pos, idLen)
            val size = when (ver) {
                2 -> Bytes.be24(body, pos + 3)
                4 -> synchsafe(body, pos + 4)
                else -> Bytes.be32(body, pos + 4).toInt()
            }
            val fl = if (ver == 2) 0 else Bytes.be16(body, pos + 8)
            val start = pos + hdrLen
            if (size < 0) break
            val end = start + size
            pos = end
            if (end > body.size) {
                // Quadro truncado (capa maior que o limite lido): só interessa se for texto pequeno; ignora.
                continue
            }
            var fs = start
            if (ver == 4 && (fl and 0x0001) != 0) fs += 4                // data length indicator
            if (ver == 3 && (fl and 0x00C0) != 0) continue              // comprimido/criptografado
            if (ver == 4 && (fl and 0x000C) != 0) continue
            var data = body.copyOfRange(fs.coerceAtMost(end), end)
            if (ver == 4 && (fl and 0x0002) != 0) data = unsync(data)
            when (id) {
                "TIT2", "TT2" -> if (into.title == null) into.title = text(data)
                "TPE1", "TP1" -> if (into.artist == null) into.artist = text(data)
                "TALB", "TAL" -> if (into.album == null) into.album = text(data)
                "TPE2", "TP2" -> if (into.albumArtist == null) into.albumArtist = text(data)
                "APIC", "PIC" -> {
                    val pic = parsePicture(data, ver == 2)
                    if (pic != null && pic.second.size <= CloudConfig.MAX_COVER_BYTES) {
                        // Prefere a capa frontal (tipo 3); senão a primeira imagem encontrada.
                        val rank = if (pic.first == 3) 2 else 1
                        if (rank > bestCoverType) { bestCoverType = rank; into.cover = pic.second }
                    }
                }
            }
        }
    }

    /** Remove o byte 0x00 inserido depois de 0xFF (esquema de dessincronização). */
    fun unsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if ((b[i].toInt() and 0xFF) == 0xFF && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    fun text(d: ByteArray): String? {
        if (d.isEmpty()) return null
        val enc = Bytes.u8(d, 0)
        val s = decode(d, 1, d.size, enc).substringBefore('\u0000').trim()
        return s.ifBlank { null }
    }

    private fun decode(d: ByteArray, from: Int, to: Int, enc: Int): String {
        if (from >= to) return ""
        return when (enc) {
            0 -> String(d, from, to - from, Charsets.ISO_8859_1)
            1 -> {
                // UTF-16 com BOM
                if (to - from >= 2 && Bytes.u8(d, from) == 0xFF && Bytes.u8(d, from + 1) == 0xFE)
                    String(d, from + 2, to - from - 2, Charsets.UTF_16LE)
                else if (to - from >= 2 && Bytes.u8(d, from) == 0xFE && Bytes.u8(d, from + 1) == 0xFF)
                    String(d, from + 2, to - from - 2, Charsets.UTF_16BE)
                else String(d, from, to - from, Charsets.UTF_16LE)
            }
            2 -> String(d, from, to - from, Charsets.UTF_16BE)
            else -> String(d, from, to - from, Charsets.UTF_8)
        }
    }

    /** Posição do terminador de string conforme a codificação (1 ou 2 bytes nulos), a partir de [from]. */
    private fun terminator(d: ByteArray, from: Int, enc: Int): Int {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < d.size) { if (d[i].toInt() == 0 && d[i + 1].toInt() == 0) return i; i += 2 }
            return -1
        }
        for (i in from until d.size) if (d[i].toInt() == 0) return i
        return -1
    }

    /** Devolve (tipo da imagem, bytes) de um quadro APIC/PIC. */
    fun parsePicture(d: ByteArray, v22: Boolean): Pair<Int, ByteArray>? {
        if (d.size < 8) return null
        val enc = Bytes.u8(d, 0)
        var p = 1
        if (v22) p += 3                                   // formato de 3 letras ("JPG"/"PNG")
        else {
            val z = (1 until d.size).firstOrNull { d[it].toInt() == 0 } ?: return null   // MIME em Latin-1
            p = z + 1
        }
        if (p >= d.size) return null
        val type = Bytes.u8(d, p); p++
        val z = terminator(d, p, enc)
        if (z < 0) return null
        p = z + if (enc == 1 || enc == 2) 2 else 1
        if (p >= d.size) return null
        return type to d.copyOfRange(p, d.size)
    }

    /** ID3v1: 128 bytes no fim ("TAG"). Preenche somente campos ainda vazios. */
    fun readV1(src: RangeSource, into: TrackTags) {
        if (src.size < 128) return
        val b = src.read(src.size - 128, 128)
        if (b.size < 128 || !Bytes.startsWith(b, 0, "TAG")) return
        fun field(o: Int, n: Int) = String(b, o, n, Charsets.ISO_8859_1).substringBefore('\u0000').trim().ifBlank { null }
        if (into.title == null) into.title = field(3, 30)
        if (into.artist == null) into.artist = field(33, 30)
        if (into.album == null) into.album = field(63, 30)
    }
}

/** Duração de MP3 a partir do primeiro quadro MPEG (Xing/Info/VBRI para VBR; tamanho/bitrate para CBR). */
internal object Mp3Info {
    private val BR_V1 = arrayOf(
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    )
    private val BR_V2 = arrayOf(
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    )
    private val SR = arrayOf(intArrayOf(44100, 48000, 32000), intArrayOf(22050, 24000, 16000), intArrayOf(11025, 12000, 8000))

    /** [audioStart] = tamanho do ID3v2. Devolve a duração em ms, ou 0 se não for possível estimar. */
    fun durationMs(src: RangeSource, audioStart: Long): Long {
        val win = src.read(audioStart, 64 * 1024)
        var i = 0
        while (i + 4 <= win.size) {
            if (Bytes.u8(win, i) == 0xFF && (Bytes.u8(win, i + 1) and 0xE0) == 0xE0) {
                val d = frame(win, i, audioStart, src.size)
                if (d != null) return d
            }
            i++
        }
        return 0L
    }

    private fun frame(w: ByteArray, o: Int, audioStart: Long, fileSize: Long): Long? {
        val b1 = Bytes.u8(w, o + 1); val b2 = Bytes.u8(w, o + 2); val b3 = Bytes.u8(w, o + 3)
        val vBits = (b1 shr 3) and 3
        val lBits = (b1 shr 1) and 3
        if (vBits == 1 || lBits == 0) return null
        val brIdx = (b2 shr 4) and 0xF
        val srIdx = (b2 shr 2) and 3
        if (brIdx == 0 || brIdx == 15 || srIdx == 3) return null
        val mpeg1 = vBits == 3
        val layer = 4 - lBits                                   // 1, 2 ou 3
        val kbps = if (mpeg1) BR_V1[layer - 1][brIdx] else BR_V2[if (layer == 1) 0 else 1][brIdx]
        val sr = SR[if (mpeg1) 0 else if (vBits == 2) 1 else 2][srIdx]
        val spf = when { layer == 1 -> 384; layer == 2 -> 1152; mpeg1 -> 1152; else -> 576 }
        val mono = ((b3 shr 6) and 3) == 3
        if (layer == 3) {
            val side = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
            val crc = if ((b1 and 1) == 0) 2 else 0
            val x = o + 4 + crc + side
            if (Bytes.startsWith(w, x, "Xing") || Bytes.startsWith(w, x, "Info")) {
                val fl = Bytes.be32(w, x + 4)
                if ((fl and 1L) != 0L) {
                    val frames = Bytes.be32(w, x + 8)
                    if (frames > 0) return frames * spf * 1000L / sr
                }
            }
            val v = o + 4 + 32
            if (Bytes.startsWith(w, v, "VBRI")) {
                val frames = Bytes.be32(w, v + 14)
                if (frames > 0) return frames * spf * 1000L / sr
            }
        }
        val audioBytes = fileSize - audioStart - o
        return if (audioBytes > 0) audioBytes * 8L / kbps else null   // bits / (kbit/s) = ms
    }
}
