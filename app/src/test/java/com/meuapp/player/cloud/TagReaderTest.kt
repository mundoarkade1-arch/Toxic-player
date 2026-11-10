package com.meuapp.player.cloud

import com.meuapp.player.cloud.tags.ByteArraySource
import com.meuapp.player.cloud.tags.TagReader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class TagReaderTest {
    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun le32(v: Int) = be32(v).reversedArray()
    private fun synch(v: Int) = byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())
    private fun bytes(vararg parts: ByteArray): ByteArray { val o = ByteArrayOutputStream(); parts.forEach { o.write(it) }; return o.toByteArray() }
    private fun s(x: String) = x.toByteArray(Charsets.ISO_8859_1)

    private fun id3Frame(id: String, body: ByteArray) = bytes(s(id), be32(body.size), byteArrayOf(0, 0), body)
    private fun id3Text(id: String, t: String) = id3Frame(id, bytes(byteArrayOf(3), t.toByteArray(Charsets.UTF_8)))

    /** MP3 CBR 128 kbps, 44,1 kHz, estéreo: quadro de 417 bytes. */
    private fun mp3Frames(n: Int): ByteArray {
        val f = ByteArray(417); f[0] = 0xFF.toByte(); f[1] = 0xFB.toByte(); f[2] = 0x90.toByte(); f[3] = 0x00
        val o = ByteArrayOutputStream(); repeat(n) { o.write(f) }; return o.toByteArray()
    }

    @Test fun mp3WithId3v2TagsCoverAndCbrDuration() {
        val jpeg = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val apic = id3Frame("APIC", bytes(byteArrayOf(0), s("image/jpeg"), byteArrayOf(0, 3), s("capa"), byteArrayOf(0), jpeg))
        val frames = bytes(id3Text("TIT2", "Minha Música"), id3Text("TPE1", "Artista X"), id3Text("TALB", "Álbum Y"), apic)
        val tag = bytes(s("ID3"), byteArrayOf(3, 0, 0), synch(frames.size), frames)
        val audio = mp3Frames(100)
        val t = TagReader.read(CloudFormat.MP3, ByteArraySource(bytes(tag, audio)))
        assertEquals("Minha Música", t.title); assertEquals("Artista X", t.artist); assertEquals("Álbum Y", t.album)
        assertArrayEquals(jpeg, t.cover)
        // 100 quadros * 417 bytes * 8 / 128 kbps = 2606 ms
        assertEquals(2606L, t.durationMs)
    }

    @Test fun mp3XingHeaderGivesVbrDuration() {
        val f = ByteArray(417); f[0] = 0xFF.toByte(); f[1] = 0xFB.toByte(); f[2] = 0x90.toByte()
        val off = 4 + 32                                   // MPEG1 estéreo
        System.arraycopy(s("Xing"), 0, f, off, 4); System.arraycopy(be32(1), 0, f, off + 4, 4); System.arraycopy(be32(1000), 0, f, off + 8, 4)
        val t = TagReader.read(CloudFormat.MP3, ByteArraySource(bytes(f, mp3Frames(3))))
        assertEquals(1000L * 1152 * 1000 / 44100, t.durationMs)
    }

    @Test fun mp3WithoutTagsFallsBackToId3v1() {
        val v1 = ByteArray(128); System.arraycopy(s("TAG"), 0, v1, 0, 3)
        System.arraycopy(s("Titulo V1"), 0, v1, 3, 9); System.arraycopy(s("Artista V1"), 0, v1, 33, 10)
        val t = TagReader.read(CloudFormat.MP3, ByteArraySource(bytes(mp3Frames(10), v1)))
        assertEquals("Titulo V1", t.title); assertEquals("Artista V1", t.artist)
    }

    @Test fun flacStreamInfoAndVorbisComments() {
        val info = ByteArray(34)
        // 44100 Hz (20 bits), 44100*10 amostras
        val sr = 44100; val total = 441000L
        info[10] = (sr shr 12).toByte(); info[11] = (sr shr 4).toByte(); info[12] = ((sr and 0xF) shl 4).toByte()
        info[13] = ((total shr 32) and 0xF).toByte(); System.arraycopy(be32((total and 0xFFFFFFFFL).toInt()), 0, info, 14, 4)
        fun c(k: String) = bytes(le32(k.toByteArray().size), k.toByteArray())
        val vc = bytes(le32(3), s("abc"), le32(3), c("TITLE=Faixa"), c("ARTIST=Banda"), c("ALBUM=Disco"))
        fun block(type: Int, last: Boolean, body: ByteArray) =
            bytes(byteArrayOf((type or if (last) 0x80 else 0).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()), body)
        val file = bytes(s("fLaC"), block(0, false, info), block(4, true, vc), ByteArray(50))
        val t = TagReader.read(CloudFormat.FLAC, ByteArraySource(file))
        assertEquals("Faixa", t.title); assertEquals("Banda", t.artist); assertEquals("Disco", t.album)
        assertEquals(10_000L, t.durationMs)
    }

    @Test fun mp4DurationAndItunesTags() {
        fun atom(type: String, body: ByteArray) = bytes(be32(8 + body.size), s(type), body)
        fun item(type: ByteArray, text: String) =
            bytes(be32(8 + 8 + 8 + text.toByteArray().size), type, be32(16 + text.toByteArray().size), s("data"), byteArrayOf(0, 0, 0, 1), be32(0), text.toByteArray())
        val nam = item(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()), "Titulo M4A")
        val art = item(byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte()), "Cantor")
        val ilst = atom("ilst", bytes(nam, art))
        val meta = atom("meta", bytes(be32(0), ilst))
        val udta = atom("udta", meta)
        val mvhd = atom("mvhd", bytes(be32(0), be32(0), be32(0), be32(1000), be32(180_000), ByteArray(80)))
        val moov = atom("moov", bytes(mvhd, udta))
        val file = bytes(atom("ftyp", s("M4A \u0000\u0000\u0000\u0000")), atom("mdat", ByteArray(1000)), moov)   // moov no fim
        val t = TagReader.read(CloudFormat.M4A, ByteArraySource(file))
        assertEquals("Titulo M4A", t.title); assertEquals("Cantor", t.artist); assertEquals(180_000L, t.durationMs)
    }

    @Test fun wavDurationFromByteRate() {
        val fmt = bytes(s("fmt "), le32(16), byteArrayOf(1, 0, 2, 0), le32(44100), le32(176400), byteArrayOf(4, 0, 16, 0))
        val data = bytes(s("data"), le32(176400 * 3), ByteArray(176400 * 3))
        val file = bytes(s("RIFF"), le32(4 + fmt.size + data.size), s("WAVE"), fmt, data)
        assertEquals(3000L, TagReader.read(CloudFormat.WAV, ByteArraySource(file)).durationMs)
    }

    @Test fun opusCommentsAndDuration() {
        fun page(seq: Int, granule: Long, packet: ByteArray): ByteArray {
            val h = ByteArray(27); System.arraycopy(s("OggS"), 0, h, 0, 4)
            for (i in 0 until 8) h[6 + i] = (granule shr (8 * i)).toByte()
            System.arraycopy(le32(1), 0, h, 14, 4); System.arraycopy(le32(seq), 0, h, 18, 4)
            val segs = ArrayList<Int>(); var left = packet.size
            while (left >= 255) { segs += 255; left -= 255 }; segs += left
            h[26] = segs.size.toByte()
            return bytes(h, ByteArray(segs.size) { segs[it].toByte() }, packet)
        }
        val head = bytes(s("OpusHead"), byteArrayOf(1, 2), byteArrayOf(0x38, 0x01), le32(48000), byteArrayOf(0, 0, 0))   // pre-skip 312
        fun c(k: String) = bytes(le32(k.toByteArray().size), k.toByteArray())
        val tags = bytes(s("OpusTags"), le32(2), s("ab"), le32(2), c("TITLE=Opus T"), c("ARTIST=Opus A"))
        val file = bytes(page(0, 0, head), page(1, 0, tags), page(2, 48000L * 5 + 312, ByteArray(10)))
        val t = TagReader.read(CloudFormat.OPUS, ByteArraySource(file))
        assertEquals("Opus T", t.title); assertEquals("Opus A", t.artist); assertEquals(5000L, t.durationMs)
    }

    @Test fun corruptedFileYieldsEmptyTagsWithoutCrashing() {
        for (f in CloudFormat.values()) {
            val t = TagReader.read(f, ByteArraySource(ByteArray(64) { (it * 7).toByte() }))
            assertNull(f.name, t.title)
        }
    }

    @Test fun readsOnlyPartsNotWholeFile() {
        val streamInfo = bytes(byteArrayOf(0x80.toByte(), 0, 0, 34), ByteArray(34))   // bloco STREAMINFO, último
        val big = bytes(s("fLaC"), streamInfo, ByteArray(5 * 1024 * 1024))
        val src = ByteArraySource(big)
        TagReader.read(CloudFormat.FLAC, src)
        assertTrue("leituras: ${src.reads}", src.reads <= 4)
    }
}

class GenreTagTest {
    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    @Test fun id3v2GenreTcon() {
        val body = byteArrayOf(3) + "(5)".toByteArray()
        val frame = "TCON".toByteArray() + be32(body.size) + byteArrayOf(0, 0) + body
        val size = frame.size
        val tag = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + byteArrayOf(0, 0, ((size shr 7) and 0x7F).toByte(), (size and 0x7F).toByte()) + frame
        val t = TagReader.read(CloudFormat.MP3, ByteArraySource(tag + ByteArray(600)))
        assertEquals("Funk", com.meuapp.player.cloud.tags.Genres.clean(t.genre))
    }
}
