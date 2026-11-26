package com.meuapp.player

import com.meuapp.player.audio.Crossover
import com.meuapp.player.audio.DspCore
import com.meuapp.player.audio.DspParams
import com.meuapp.player.audio.XoverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Mede de verdade o que cada recurso de áudio de carro faz com o som (motor em Kotlin puro, sem Android). */
class CarAudioTest {
    private val sr = 48000

    private fun newCore(p: DspParams) = DspCore().also { it.configure(sr, 2); it.params = p }

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

    private fun tone(frames: Int, f: Double, a: Double, aR: Double = a, phaseR: Double = 0.0) =
        FloatArray(frames * 2) { i ->
            val n = i / 2
            if (i % 2 == 0) (a * sin(2 * PI * f * n / sr)).toFloat() else (aR * sin(2 * PI * f * n / sr + phaseR)).toFloat()
        }

    /** Amplitude de uma frequência no canal [ch], nos últimos [len] quadros (projeção com janela de Hann). */
    private fun amp(b: FloatArray, f: Double, ch: Int = 0, len: Int = sr / 2): Double {
        val total = b.size / 2
        var re = 0.0; var im = 0.0; var wsum = 0.0
        for (k in 0 until len) {
            val n = total - len + k
            val w = 0.5 - 0.5 * cos(2 * PI * k / len)
            val x = b[2 * n + ch].toDouble() * w
            re += x * cos(2 * PI * f * n / sr); im += x * sin(2 * PI * f * n / sr); wsum += w
        }
        return 2.0 * sqrt(re * re + im * im) / wsum
    }

    private fun db(x: Double) = 20.0 * log10(max(x, 1e-12))

    /** Ganho (dB) do núcleo para uma senoide de [f] Hz com parâmetros [p]. */
    private fun gainDb(p: DspParams, f: Double, a: Double = 0.1): Double {
        val out = run(newCore(p), tone(sr, f, a))
        return db(amp(out, f) / a)
    }

    private val flat = DspParams(limOn = false, ditherOn = false)

    @Test fun passaAltasLinkwitzRiley24() {
        val p = flat.copy(hpfOn = true, hpfFreq = 40f, hpfSlope = 24, hpfType = XoverType.LINKWITZ_RILEY)
        assertEquals(-6.0, gainDb(p, 40.0), 0.6)      // LR: -6 dB na frequência de corte
        assertTrue("20 Hz deve cair uns 24+ dB", gainDb(p, 20.0) < -22.0)
        assertEquals(0.0, gainDb(p, 400.0), 0.3)
    }

    @Test fun passaAltasButterworthInclinacoes() {
        val p6 = flat.copy(hpfOn = true, hpfFreq = 80f, hpfSlope = 6, hpfType = XoverType.BUTTERWORTH)
        val p12 = p6.copy(hpfSlope = 12)
        val p18 = p6.copy(hpfSlope = 18)
        val p48 = p6.copy(hpfSlope = 48)
        for (p in listOf(p6, p12, p18, p48)) assertEquals("no corte, -3 dB", -3.0, gainDb(p, 80.0), 0.5)
        assertEquals(-7.0, gainDb(p6, 40.0), 1.0)        // 1 oitava abaixo: ~-7 dB
        assertEquals(-12.3, gainDb(p12, 40.0), 1.2)
        assertEquals(-18.1, gainDb(p18, 40.0), 1.5)
        assertTrue(gainDb(p48, 40.0) < -44.0)
    }

    @Test fun passaBaixas() {
        val p = flat.copy(lpfOn = true, lpfFreq = 8000f, lpfSlope = 12, lpfType = XoverType.BUTTERWORTH)
        assertEquals(0.0, gainDb(p, 1000.0), 0.3)
        assertEquals(-3.0, gainDb(p, 8000.0), 0.6)
        assertTrue(gainDb(p, 16000.0) < -10.0)
    }

    @Test fun inclinacaoEfetivaDoLinkwitzRiley() {
        assertEquals(24, Crossover.effectiveSlope(XoverType.LINKWITZ_RILEY, 18))
        assertEquals(12, Crossover.effectiveSlope(XoverType.LINKWITZ_RILEY, 6))
        assertEquals(18, Crossover.effectiveSlope(XoverType.BUTTERWORTH, 18))
    }

    @Test fun graveVirtualRepoeOGraveCortado() {
        // Fundamental de 40 Hz (abaixo da capacidade do alto-falante). O corte tira ela; o grave virtual devolve harmônicos em 80/120 Hz.
        val x = tone(sr * 2, 40.0, 0.3)
        val semVb = run(newCore(flat.copy(hpfOn = true, hpfFreq = 50f, hpfSlope = 24)), x)
        val comVb = run(newCore(flat.copy(hpfOn = true, hpfFreq = 50f, hpfSlope = 24, vbOn = true, vbFreq = 60f, vbAmount = 1f)), x)
        val h2sem = amp(semVb, 80.0) + amp(semVb, 120.0)
        val h2com = amp(comVb, 80.0) + amp(comVb, 120.0)
        assertTrue("harmônicos acima de 80 Hz devem aparecer: sem=$h2sem com=$h2com", h2com > 0.04 && h2com > h2sem * 8)
        assertTrue("a fundamental continua cortada", amp(comVb, 40.0) < 0.3 * 0.5)
        for (v in comVb) assertTrue(v.isFinite() && abs(v) < 1.0f)
    }

    @Test fun graveVirtualNaoMexeNosMedios() {
        val x = tone(sr, 1000.0, 0.3)
        val a = run(newCore(flat), x); val b = run(newCore(flat.copy(vbOn = true, vbAmount = 1f)), x)
        assertEquals(amp(a, 1000.0), amp(b, 1000.0), 0.01)
        assertTrue(amp(b, 2000.0) < 0.005)
    }

    @Test fun subHarmonicoGeraUmaOitavaAbaixo() {
        val x = tone(sr * 2, 80.0, 0.3)
        val out = run(newCore(flat.copy(subOn = true, subFreq = 100f, subAmount = 1f)), x)
        assertTrue("deve haver 40 Hz: ${amp(out, 40.0)}", amp(out, 40.0) > 0.05)
        assertTrue(amp(out, 80.0) > 0.25)
        val off = run(newCore(flat), x)
        assertTrue(amp(off, 40.0) < 0.005)
    }

    @Test fun limitadorDeGravesSegueOTetoSoNosGraves() {
        val p = flat.copy(blOn = true, blFreq = 120f, blCeilDb = -6f)
        val grave = run(newCore(p), tone(sr, 60.0, 0.9))
        val ampGrave = amp(grave, 60.0)
        assertTrue("60 Hz limitado perto de 0,5: $ampGrave", ampGrave < 0.56 && ampGrave > 0.40)
        val medio = run(newCore(p), tone(sr, 1000.0, 0.9))
        assertEquals(0.9, amp(medio, 1000.0), 0.03)
        // abaixo do teto não mexe
        val baixo = run(newCore(p), tone(sr, 60.0, 0.2))
        assertEquals(0.2, amp(baixo, 60.0), 0.01)
    }

    @Test fun alinhamentoDeTempoGanhoEPolaridade() {
        val frames = 4800
        val imp = FloatArray(frames * 2); imp[0] = 0.5f; imp[1] = 0.5f
        val core = newCore(flat.copy(delayLms = 2f))
        val out = run(core, imp)
        var iL = -1; var iR = -1
        for (n in 0 until frames) {
            if (iL < 0 && abs(out[2 * n]) > 0.1f) iL = n
            if (iR < 0 && abs(out[2 * n + 1]) > 0.1f) iR = n
        }
        assertEquals("L atrasado 2 ms = 96 amostras em relação ao R", 96, iL - iR)

        val trim = run(newCore(flat.copy(trimLdb = -6f, trimRdb = 0f)), tone(sr, 1000.0, 0.4))
        assertEquals(0.2, amp(trim, 1000.0, 0), 0.01)
        assertEquals(0.4, amp(trim, 1000.0, 1), 0.01)

        val inv = run(newCore(flat.copy(invertR = true)), imp)
        val a = inv[2 * core.latencyFrames]; val b = inv[2 * core.latencyFrames + 1]
        assertTrue(a > 0.4f && b < -0.4f)
    }

    @Test fun graveMonoSomenteAbaixoDoCorte() {
        val x = tone(sr, 60.0, 0.3, 0.3, PI)      // graves em oposição de fase entre L e R
        val off = run(newCore(flat), x)
        val on = run(newCore(flat.copy(monoBassOn = true, monoBassFreq = 150f)), x)
        assertTrue(amp(off, 60.0) > 0.29)
        assertTrue("graves devem virar mono: ${amp(on, 60.0)}", amp(on, 60.0) < 0.06)
        val hi = tone(sr, 2000.0, 0.3, 0.3, PI)
        assertTrue("agudos continuam estéreo", amp(run(newCore(flat.copy(monoBassOn = true, monoBassFreq = 150f)), hi), 2000.0) > 0.28)
    }

    @Test fun contornoDeLoudness() {
        val p = flat.copy(contour = 1f)
        assertTrue(gainDb(p, 60.0) > 6.0)
        assertEquals(0.0, gainDb(p, 1000.0), 0.8)
        assertTrue(gainDb(p, 14000.0) > 2.0)
        assertEquals(0.0, gainDb(flat, 60.0), 0.1)
    }

    @Test fun focoNaVozSoNoCentro() {
        val p = flat.copy(vocalFocus = 1f)
        val centro = run(newCore(p), tone(sr, 2800.0, 0.1))
        assertEquals(5.0, db(amp(centro, 2800.0) / 0.1), 0.7)
        val lados = run(newCore(p), tone(sr, 2800.0, 0.1, 0.1, PI))
        assertEquals(0.0, db(amp(lados, 2800.0) / 0.1), 0.3)
    }

    @Test fun arAdicionaAgudosSemEstourar() {
        val x = tone(sr, 5500.0, 0.3)
        val off = run(newCore(flat), x)
        val on = run(newCore(flat.copy(air = 1f)), x)
        val h = amp(on, 11000.0) + amp(on, 16500.0)
        assertTrue("harmônicos acima de 8 kHz: $h", h > 0.01 && h > (amp(off, 11000.0) + amp(off, 16500.0)) * 5)
        for (v in on) assertTrue(v.isFinite() && abs(v) < 1.2f)
    }

    @Test fun medidorEntradaSaidaMostraADiferencaReal() {
        // ruído com muita energia nos graves, médios e agudos
        val rnd = java.util.Random(7)
        val b = FloatArray(sr * 4 * 2) { (rnd.nextGaussian() * 0.08).toFloat() }
        val flatCore = newCore(flat); run(flatCore, b)
        val m0 = flatCore.meters
        assertTrue(m0.measured)
        assertEquals(0f, m0.diffLowDb, 0.6f); assertEquals(0f, m0.diffMidDb, 0.6f); assertEquals(0f, m0.diffHighDb, 0.6f)
        val boost = newCore(flat.copy(graphic = listOf(8f, 8f, 8f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)))
        run(boost, b)
        val m = boost.meters
        assertTrue("graves devem subir: ${m.diffLowDb}", m.diffLowDb > 5f)
        assertEquals(0f, m.diffHighDb, 1.0f)
        assertTrue(m.frames >= sr * 4L)
    }

    @Test fun tudoLigadoComSinalQuenteContinuaSeguro() {
        val rnd = java.util.Random(3)
        val x = FloatArray(sr * 3 * 2) { i -> ((rnd.nextGaussian() * 0.35) + 0.5 * sin(2 * PI * 55.0 * (i / 2) / sr)).toFloat().coerceIn(-1f, 1f) }
        val p = DspParams(
            graphic = listOf(9f, 9f, 6f, 0f, 0f, 0f, 0f, 2f, 3f, 3f), autoHeadroom = false,
            hpfOn = true, lpfOn = true, vbOn = true, subOn = true, blOn = true, monoBassOn = true,
            delayLms = 3f, delayRms = 1f, trimLdb = -2f, invertR = true, contour = 1f, vocalFocus = 1f, air = 1f,
            compOn = true, mbOn = true, limOn = true, limCeilingDb = -1f
        )
        val out = run(newCore(p), x)
        val ceil = Math.pow(10.0, -1.0 / 20.0)
        for (v in out) { assertTrue(v.isFinite()); assertTrue("estourou o teto: $v", abs(v) <= ceil + 1e-4) }
    }

    @Test fun cortesDesligadosNaoMudamNada() {
        val x = tone(sr, 1000.0, 0.5)
        val a = run(newCore(flat), x)
        val b = run(newCore(flat.copy(hpfOn = false, lpfOn = false, vbOn = false, subOn = false, blOn = false, air = 0f, contour = 0f)), x)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-6f)
        assertFalse(a.any { it.isNaN() })
    }
}
