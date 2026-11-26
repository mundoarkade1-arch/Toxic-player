package com.meuapp.player

import com.meuapp.player.audio.PcmCodec
import com.meuapp.player.audio.PcmFormat
import com.meuapp.player.audio.Quantizer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmCodecTest {
    private val src = floatArrayOf(0f, 0.5f, -0.5f, 0.999f, -1f, 0.123456f)

    private fun roundTrip(f: PcmFormat, order: ByteOrder, tol: Float) {
        val b = ByteBuffer.allocate(src.size * f.bytes).order(order)
        PcmCodec.write(b, f, src, src.size, Quantizer(), false); b.flip()
        assertEquals(src.size * f.bytes, b.remaining())
        val out = FloatArray(src.size); PcmCodec.read(b, f, out, src.size)
        for (i in src.indices) assertEquals("$f $order #$i", src[i], out[i], tol)
    }

    @Test fun idaEVoltaEmTodosOsFormatos() {
        for (o in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            roundTrip(PcmFormat.INT16, o, 1f / 16000)
            roundTrip(PcmFormat.INT24, o, 1f / 4_000_000)
            roundTrip(PcmFormat.INT32, o, 1e-6f)
            roundTrip(PcmFormat.FLOAT, o, 0f)
        }
    }

    @Test fun valoresForaDaFaixaSaoLimitados() {
        for (f in PcmFormat.values()) {
            if (f == PcmFormat.FLOAT) continue
            val b = ByteBuffer.allocate(2 * f.bytes).order(ByteOrder.LITTLE_ENDIAN)
            PcmCodec.write(b, f, floatArrayOf(3f, -3f), 2, Quantizer(), false); b.flip()
            val out = FloatArray(2); PcmCodec.read(b, f, out, 2)
            assertEquals(1f, out[0], 0.001f); assertEquals(-1f, out[1], 0.001f)
        }
    }
}
