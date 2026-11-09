package com.meuapp.player

import com.meuapp.player.audio.BiquadCoef
import com.meuapp.player.audio.EqFilter
import com.meuapp.player.audio.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadTest {
    private val sr = 48000.0
    private fun design(t: FilterType, f: Float, g: Float, q: Float) = BiquadCoef.design(EqFilter(t, f, g, q), sr)

    @Test fun peakNoCentroTemOGanhoPedido() {
        assertEquals(6.0, design(FilterType.PEAK, 1000f, 6f, 1f).responseDb(1000.0, sr), 0.01)
        assertEquals(-9.0, design(FilterType.PEAK, 1000f, -9f, 2f).responseDb(1000.0, sr), 0.01)
    }

    @Test fun ganhoZeroEhNeutro() {
        val c = design(FilterType.PEAK, 1000f, 0f, 1f)
        for (f in listOf(100.0, 1000.0, 10000.0)) assertEquals(0.0, c.responseDb(f, sr), 1e-6)
    }

    @Test fun passaBaixasEPassaAltasCaem3dBNoCorte() {
        assertEquals(-3.01, design(FilterType.LOW_PASS, 1000f, 0f, 0.7071f).responseDb(1000.0, sr), 0.05)
        assertEquals(-3.01, design(FilterType.HIGH_PASS, 1000f, 0f, 0.7071f).responseDb(1000.0, sr), 0.05)
    }

    @Test fun notchCavaNaFrequencia() {
        assertTrue(design(FilterType.NOTCH, 1000f, 0f, 1f).responseDb(1000.0, sr) < -60.0)
    }

    @Test fun shelvesFuncionamNasPontas() {
        val ls = design(FilterType.LOW_SHELF, 200f, 6f, 0.707f)
        assertEquals(6.0, ls.responseDb(20.0, sr), 0.2)
        assertEquals(0.0, ls.responseDb(10000.0, sr), 0.2)
        val hs = design(FilterType.HIGH_SHELF, 4000f, 6f, 0.707f)
        assertEquals(6.0, hs.responseDb(20000.0, sr), 0.2)
        assertEquals(0.0, hs.responseDb(100.0, sr), 0.2)
    }

    @Test fun parametrosForaDoLimiteSaoCorrigidos() {
        val c = BiquadCoef.design(EqFilter(FilterType.PEAK, 99999f, 6f, 500f), sr)
        assertTrue(c.b0.isFinite() && c.a1.isFinite() && c.a2.isFinite())
    }
}
