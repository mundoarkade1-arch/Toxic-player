package com.meuapp.player

import com.meuapp.player.audio.Fft
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FftTest {
    @Test fun senoideApareceNoBinCerto() {
        val n = 2048
        val sr = 44100.0
        val x = FloatArray(n) { (sin(2 * PI * 1000.0 * it / sr) * (0.5 - 0.5 * cos(2 * PI * it / n))).toFloat() }
        val mag = Fft.magnitudes(x)
        var best = 0
        for (i in mag.indices) if (mag[i] > mag[best]) best = i
        val expected = 1000.0 * n / sr
        assertTrue("bin $best, esperado perto de $expected", kotlin.math.abs(best - expected) <= 1.5)
    }
}
