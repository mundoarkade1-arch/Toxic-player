package com.meuapp.player.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Formatos de PCM que o motor sabe processar (Kotlin puro, sem Android). */
enum class PcmFormat(val bytes: Int, val label: String) {
    INT16(2, "16 bits"), INT24(3, "24 bits"), INT32(4, "32 bits"), FLOAT(4, "float 32 bits")
}

/** Leitura e escrita de amostras PCM em float (-1..1) para os formatos de [PcmFormat]. Respeita a ordem de bytes do buffer. */
object PcmCodec {
    /** Lê [count] amostras do buffer (avança a posição) para [out]. */
    fun read(b: ByteBuffer, f: PcmFormat, out: FloatArray, count: Int) {
        when (f) {
            PcmFormat.INT16 -> for (i in 0 until count) out[i] = b.getShort() / 32768f
            PcmFormat.INT32 -> for (i in 0 until count) out[i] = (b.getInt() / 2147483648.0).toFloat()
            PcmFormat.FLOAT -> for (i in 0 until count) out[i] = b.getFloat().let { if (it.isNaN() || it.isInfinite()) 0f else it }
            PcmFormat.INT24 -> {
                val le = b.order() == ByteOrder.LITTLE_ENDIAN
                for (i in 0 until count) {
                    val a = b.get().toInt() and 0xFF; val m = b.get().toInt() and 0xFF; val c = b.get().toInt()
                    val v = if (le) (c shl 16) or (m shl 8) or a else (a.toByte().toInt() shl 16) or (m shl 8) or (c and 0xFF)
                    out[i] = v / 8388608f
                }
            }
        }
    }

    /** Escreve [count] amostras de [src] no buffer. Só o 16 bits usa dither (os outros já têm resolução de sobra). */
    fun write(b: ByteBuffer, f: PcmFormat, src: FloatArray, count: Int, q: Quantizer, dither: Boolean) {
        when (f) {
            PcmFormat.INT16 -> for (i in 0 until count) b.putShort(q.toShort(src[i], dither))
            PcmFormat.INT32 -> for (i in 0 until count) b.putInt(Math.round(src[i].toDouble().coerceIn(-1.0, 1.0) * 2147483647.0).toInt())
            PcmFormat.FLOAT -> for (i in 0 until count) b.putFloat(src[i])
            PcmFormat.INT24 -> {
                val le = b.order() == ByteOrder.LITTLE_ENDIAN
                for (i in 0 until count) {
                    val v = Math.round(src[i].toDouble().coerceIn(-1.0, 1.0) * 8388607.0).toInt()
                    if (le) { b.put(v.toByte()); b.put((v shr 8).toByte()); b.put((v shr 16).toByte()) }
                    else { b.put((v shr 16).toByte()); b.put((v shr 8).toByte()); b.put(v.toByte()) }
                }
            }
        }
    }
}
