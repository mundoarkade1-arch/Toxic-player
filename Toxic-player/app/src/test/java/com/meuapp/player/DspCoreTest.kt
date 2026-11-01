package com.meuapp.player

import com.meuapp.player.audio.DspCore
import com.meuapp.player.audio.DspParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DspCoreTest {
    private val sr = 48000

    private fun newCore(p: DspParams): DspCore = DspCore().also { it.configure(sr, 2); it.params = p }

    /** Gera senoide estéreo intercalada (canal R pode ter fase/amplitude diferente). */
    private fun sine(frames: Int, freq: Double, ampL: Double, ampR: Double = ampL, phaseR: Double = 0.0): FloatArray {
        val b = FloatArray(frames * 2)
        for (n in 0 until frames) {
            b[2 * n] = (ampL * sin(2 * PI * freq * n / sr)).toFloat()
            b[2 * n + 1] = (ampR * sin(2 * PI * freq * n / sr + phaseR)).toFloat()
        }
        return b
    }

    private fun run(core: DspCore, input: FloatArray): FloatArray {
        val out = input.copyOf()
        val frames = out.size / 2
        var pos = 0
        while (pos < frames) {
            val n = minOf(1024, frames - pos)
            val chunk = out.copyOfRange(pos * 2, (pos + n) * 2)
            core.process(chunk, n)
            chunk.copyInto(out, pos * 2)
            pos += n
        }
        return out
    }

    private fun peakTail(b: FloatArray, tailFrames: Int, channel: Int): Double {
        var m = 0.0
        for (n in (b.size / 2 - tailFrames) until b.size / 2) m = max(m, abs(b[2 * n + channel].toDouble()))
        return m
    }

    private fun rmsTail(b: FloatArray, tailFrames: Int): Double {
        var s = 0.0
        for (n in (b.size / 2 - tailFrames) until b.size / 2) s += b[2 * n].toDouble().pow(2)
        return sqrt(s / tailFrames)
    }

    @Test fun parametrosPadraoNaoAlteramOSom() {
        val core = newCore(DspParams())
        val input = sine(sr / 2, 440.0, 0.5)
        val out = run(core, input)
        val d = core.latencyFrames
        for (n in 0 until 2000) {
            assertEquals(input[2 * n].toDouble(), out[2 * (n + d)].toDouble(), 1e-5)
            assertEquals(input[2 * n + 1].toDouble(), out[2 * (n + d) + 1].toDouble(), 1e-5)
        }
    }

    @Test fun preampAplicaOGanho() {
        val core = newCore(DspParams(preampDb = 6f, limOn = false))
        val out = run(core, sine(sr / 2, 1000.0, 0.1))
        assertEquals(0.1 * 10.0.pow(6.0 / 20.0), peakTail(out, 2000, 0), 0.003)
    }

    @Test fun eqGraficoReforcaA1kHz() {
        val g = MutableList(10) { 0f }; g[5] = 12f
        val core = newCore(DspParams(graphic = g, limOn = false))
        val out = run(core, sine(sr / 2, 1000.0, 0.1))
        assertEquals(0.398, peakTail(out, 2000, 0), 0.02)
    }

    @Test fun limitadorNuncaPassaDoTeto() {
        val core = newCore(DspParams(limOn = true, limCeilingDb = -6f))
        val out = run(core, sine(sr, 300.0, 1.5))
        val ceiling = 10.0.pow(-6.0 / 20.0)
        for (v in out) assertTrue("amostra $v acima do teto", abs(v.toDouble()) <= ceiling + 1e-4)
        assertTrue(core.meters.limGrDb > 0f)
    }

    @Test fun compressorReduzSinalForteMasNaoOFraco() {
        val forte = newCore(DspParams(compOn = true, compThr = -30f, compRatio = 8f, compKneeDb = 0f, compAttackMs = 1f, compReleaseMs = 50f, compRms = false, limOn = false))
        val inF = sine(sr, 1000.0, 0.9)
        val outF = run(forte, inF)
        assertTrue(rmsTail(outF, 9600) < rmsTail(inF, 9600) * 0.5)
        assertTrue(forte.meters.compGrDb > 10f)

        val fraco = newCore(DspParams(compOn = true, compThr = -20f, compRatio = 8f, compKneeDb = 0f, compRms = false, limOn = false))
        val inW = sine(sr / 2, 1000.0, 0.01)
        val outW = run(fraco, inW)
        assertEquals(rmsTail(inW, 4800), rmsTail(outW, 4800), 1e-4)
    }

    @Test fun larguraZeroViraMono() {
        val core = newCore(DspParams(width = 0f, limOn = false))
        val out = run(core, sine(sr / 4, 500.0, 0.4, 0.2, 1.0))
        for (n in 3000 until sr / 4) assertEquals(out[2 * n].toDouble(), out[2 * n + 1].toDouble(), 1e-5)
    }

    @Test fun balancoTotalSilenciaOLadoOposto() {
        val core = newCore(DspParams(balance = 1f, limOn = false))
        val out = run(core, sine(sr / 4, 500.0, 0.4))
        assertEquals(0.0, peakTail(out, 2000, 0), 1e-6)
        assertTrue(peakTail(out, 2000, 1) > 0.3)
    }

    @Test fun bypassGeralNaoProcessa() {
        val g = MutableList(10) { 12f }
        val core = newCore(DspParams(enabled = false, graphic = g, preampDb = 12f))
        val input = sine(sr / 4, 440.0, 0.3)
        val out = run(core, input)
        val d = core.latencyFrames
        for (n in 0 until 1000) assertEquals(input[2 * n].toDouble(), out[2 * (n + d)].toDouble(), 1e-5)
    }

    @Test fun medidorDetectaClipping() {
        val core = newCore(DspParams(preampDb = 12f))
        run(core, sine(sr / 10, 440.0, 0.9))
        assertTrue(core.meters.clip)
    }
}
