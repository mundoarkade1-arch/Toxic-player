package com.meuapp.player

import com.meuapp.player.audio.Quantizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class QuantizerTest {
    @Test fun semDitherNaoAdicionaRuido() {
        val q = Quantizer()
        for (i in 0 until 1000) assertEquals(0, q.toShort(0f, false).toInt())
        assertEquals(16384, q.toShort(0.5f, false).toInt())
        assertEquals(-16384, q.toShort(-0.5f, false).toInt())
    }

    @Test fun ditherFicaDentroDeUmBitEComMediaZero() {
        val q = Quantizer()
        var soma = 0L
        for (i in 0 until 100000) {
            val v = q.toShort(0f, true).toInt()
            assertTrue(abs(v) <= 1)
            soma += v
        }
        assertTrue(abs(soma / 100000.0) < 0.02)
    }

    @Test fun limitesDe16BitsSaoRespeitados() {
        val q = Quantizer()
        assertEquals(32767, q.toShort(2f, false).toInt())
        assertEquals(-32768, q.toShort(-2f, false).toInt())
    }
}
