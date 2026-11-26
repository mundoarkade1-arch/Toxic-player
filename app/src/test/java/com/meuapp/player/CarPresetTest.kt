package com.meuapp.player

import com.meuapp.player.audio.AudioFx
import com.meuapp.player.audio.CarPresets
import com.meuapp.player.audio.DspCore
import com.meuapp.player.audio.DspEngine
import com.meuapp.player.audio.DspParams
import com.meuapp.player.audio.XoverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Os presets de carro prontos carregam, ficam marcados como ativos e, renderizados de verdade, fazem o que prometem. */
class CarPresetTest {
    private val sr = 48000

    private fun render(p: DspParams, x: FloatArray): FloatArray {
        val core = DspCore().also { it.configure(sr, 2); it.params = p }
        val out = x.copyOf(); var pos = 0; val frames = out.size / 2
        while (pos < frames) {
            val n = minOf(1024, frames - pos)
            val chunk = out.copyOfRange(pos * 2, (pos + n) * 2)
            core.process(chunk, n); chunk.copyInto(out, pos * 2); pos += n
        }
        return out
    }

    private fun tone(sec: Int, f: Double, a: Double) = FloatArray(sr * sec * 2) { i -> (a * sin(2 * PI * f * (i / 2) / sr)).toFloat() }

    /** Amplitude de [f] nos últimos 0,5 s (projeção com janela de Hann), canal esquerdo. */
    private fun amp(b: FloatArray, f: Double): Double {
        val len = sr / 2; val total = b.size / 2
        var re = 0.0; var im = 0.0; var ws = 0.0
        for (k in 0 until len) {
            val n = total - len + k; val w = 0.5 - 0.5 * cos(2 * PI * k / len); val v = b[2 * n] * w
            re += v * cos(2 * PI * f * n / sr); im += v * sin(2 * PI * f * n / sr); ws += w
        }
        return 2 * sqrt(re * re + im * im) / ws
    }

    private fun db(x: Double) = 20 * log10(max(x, 1e-12))

    private fun load(name: String): DspParams {
        AudioFx.resetAll()
        assertTrue("preset $name deve existir", AudioFx.applyCarPreset(name))
        return DspEngine.core.params
    }

    @Test fun todosOsPresetsDeCarroCarregam() {
        assertTrue(CarPresets.ALL.containsKey(CarPresets.MAIN))
        for ((name, json) in CarPresets.ALL) {
            AudioFx.resetAll()
            assertEquals(name, "Preset carregado com sucesso.", AudioFx.importJson(json))
            assertTrue("$name: chave geral ligada", AudioFx.enabled)
            assertTrue("$name: tem filtros", AudioFx.filters.isNotEmpty())
            assertTrue("$name: corte nos graves ligado", AudioFx.hpfOn)
        }
    }

    @Test fun presetAtivoFicaMarcadoEPerdeAMarcaAoMexer() {
        AudioFx.resetAll()
        assertTrue(AudioFx.applyCarPreset(CarPresets.MAIN))
        assertTrue(AudioFx.isActive(CarPresets.MAIN))
        AudioFx.setMasterEnabled(false)
        assertFalse("com tudo desligado o preset não está valendo", AudioFx.isActive(CarPresets.MAIN))
        AudioFx.setMasterEnabled(true)
        assertTrue("religar a chave geral volta a marcar o preset", AudioFx.isActive(CarPresets.MAIN))
        AudioFx.vbAmount = 0.9f; AudioFx.apply()
        assertFalse("mexer em qualquer controle vira Personalizado", AudioFx.isActive(CarPresets.MAIN))
    }

    @Test fun presetProntoZeraORestoELigaOEq() {
        AudioFx.resetAll()
        assertTrue(AudioFx.applyCarPreset(CarPresets.MAIN))
        AudioFx.eqOn = false; AudioFx.enabled = false
        AudioFx.applyPreset("Bass")
        assertTrue(AudioFx.enabled); assertTrue(AudioFx.eqOn)
        assertFalse("preset de EQ não pode herdar o corte do preset de carro", AudioFx.hpfOn)
        assertFalse(AudioFx.vbOn); assertFalse(AudioFx.compOn)
        assertTrue(AudioFx.filters.isEmpty())
        assertEquals(6f, AudioFx.bands[0], 0.001f)
        assertTrue(AudioFx.isActive("Bass"))
    }

    @Test fun arquivosDaPastaPresetsSaoIguaisAosEmbutidos() {
        val files = mapOf(
            "Toxic Carro · Grave Forte + Voz" to "Toxic-Carro-Grave-Forte-Voz.json",
            "Toxic Carro · Equilibrado" to "Toxic-Carro-Equilibrado.json",
            "Toxic Carro · Com Subwoofer" to "Toxic-Carro-Com-Subwoofer.json",
            "Toxic Carro · Volume Baixo" to "Toxic-Carro-Volume-Baixo.json",
            "Toxic Carro · Vibração Máxima" to "Toxic-Carro-Vibracao-Maxima.json",
            "Toxic Carro · Kit 2 Vias" to "Toxic-Carro-Kit-2-Vias.json"
        )
        for ((name, f) in files) {
            val file = listOf(File("../presets/$f"), File("presets/$f")).firstOrNull { it.exists() } ?: continue   // sem a pasta, só pula
            AudioFx.resetAll(); AudioFx.importJson(CarPresets.ALL.getValue(name)); val a = AudioFx.exportCurrent()
            AudioFx.resetAll(); AudioFx.importJson(file.readText()); val b = AudioFx.exportCurrent()
            assertEquals("$name: arquivo e preset embutido devem ser iguais", a, b)
        }
    }

    @Test fun presetPrincipalCortaOSubsonicoMasRealcaGraveEVoz() {
        val p = load(CarPresets.MAIN)
        val g30 = db(amp(render(p, tone(2, 30.0, 0.2)), 30.0) / 0.2)
        val g70 = db(amp(render(p, tone(2, 70.0, 0.2)), 70.0) / 0.2)
        val g1k = db(amp(render(p, tone(2, 1000.0, 0.2)), 1000.0) / 0.2)
        val g3k = db(amp(render(p, tone(2, 3000.0, 0.2)), 3000.0) / 0.2)
        val g250 = db(amp(render(p, tone(2, 250.0, 0.2)), 250.0) / 0.2)
        assertTrue("30 Hz (o cone da porta não segura) deve cair bem abaixo do grave útil: $g30 x $g70", g30 < g70 - 8.0)
        assertTrue("grave de 70 Hz mais forte que os médios: $g70 x $g1k", g70 > g1k + 4.0)
        assertTrue("região da voz (3 kHz) acima de 1 kHz: $g3k x $g1k", g3k > g1k + 1.5)
        assertTrue("lama de 250 Hz abaixo dos médios: $g250 x $g1k", g250 < g1k - 1.0)
    }

    @Test fun presetPrincipalNaoEstouraEOMedidorMostraOGraveSubindo() {
        val p = load(CarPresets.MAIN)
        val rnd = java.util.Random(5)
        // "música" quente: ruído rosado grosseiro + bumbo de 55 Hz forte
        var lp = 0.0
        val x = FloatArray(sr * 6 * 2)
        for (n in 0 until sr * 6) {
            lp += (rnd.nextGaussian() * 0.25 - lp) * 0.05
            val kick = 0.55 * sin(2 * PI * 55.0 * n / sr) * (if ((n % (sr / 2)) < sr / 5) 1.0 else 0.2)
            val v = (lp * 1.6 + kick + rnd.nextGaussian() * 0.02).coerceIn(-1.0, 1.0).toFloat()
            x[2 * n] = v; x[2 * n + 1] = v
        }
        val core = DspCore().also { it.configure(sr, 2); it.params = p }
        val out = x.copyOf(); var pos = 0
        while (pos < sr * 6) {
            val n = minOf(1024, sr * 6 - pos)
            val c = out.copyOfRange(pos * 2, (pos + n) * 2); core.process(c, n); c.copyInto(out, pos * 2); pos += n
        }
        val ceil = Math.pow(10.0, p.limCeilingDb / 20.0) + 1e-4
        for (v in out) { assertTrue(v.isFinite()); assertTrue("estourou o teto: $v", abs(v) <= ceil) }
        assertTrue("o medidor deve estar medindo", core.meters.measured)
    }

    /** "Música" quente de 4 s: ruído rosado grosseiro + bumbo de 50 Hz forte, igual nos dois lados. */
    private fun musicaQuente(): FloatArray {
        val rnd = java.util.Random(11); var lp = 0.0
        val x = FloatArray(sr * 4 * 2)
        for (n in 0 until sr * 4) {
            lp += (rnd.nextGaussian() * 0.25 - lp) * 0.05
            val kick = 0.6 * sin(2 * PI * 50.0 * n / sr) * (if ((n % (sr / 2)) < sr / 5) 1.0 else 0.2)
            val v = (lp * 1.6 + kick + rnd.nextGaussian() * 0.03).coerceIn(-1.0, 1.0).toFloat()
            x[2 * n] = v; x[2 * n + 1] = v
        }
        return x
    }

    @Test fun todosOsPresetsDeCarroNaoEstouram() {
        val x = musicaQuente()
        for (name in CarPresets.ALL.keys) {
            val p = load(name)
            val ceil = Math.pow(10.0, p.limCeilingDb / 20.0) + 1e-4
            for (v in render(p, x)) {
                assertTrue("$name: valor inválido", v.isFinite())
                assertTrue("$name: estourou o teto: $v", abs(v) <= ceil)
            }
        }
    }

    @Test fun todosOsPresetsDeCarroMudamOSomDeFormaMedivel() {
        // Ruído branco: com qualquer preset de carro os graves saem mais fortes que os médios (em relação à entrada) e o medidor entrada x saída percebe.
        // Compara graves com médios porque o preamp negativo dos presets baixa tudo junto.
        val rnd = java.util.Random(9)
        val x = FloatArray(sr * 4 * 2) { (rnd.nextGaussian() * 0.08).toFloat() }
        for (name in CarPresets.ALL.keys) {
            val core = DspCore().also { it.configure(sr, 2); it.params = load(name) }
            val out = x.copyOf(); var pos = 0
            while (pos < sr * 4) {
                val n = minOf(1024, sr * 4 - pos)
                val c = out.copyOfRange(pos * 2, (pos + n) * 2); core.process(c, n); c.copyInto(out, pos * 2); pos += n
            }
            val m = core.meters
            assertTrue("$name: medidor deve estar medindo", m.measured)
            println("DIFF $name: graves ${m.diffLowDb} médios ${m.diffMidDb} agudos ${m.diffHighDb}")
            assertTrue("$name: graves devem subir em relação aos médios: ${m.diffLowDb} x ${m.diffMidDb}", m.diffLowDb - m.diffMidDb > 1.5f)
        }
    }

    @Test fun presetsPrincipaisReforcamMuitoMaisOGraveQueOsMedios() {
        for (name in listOf(CarPresets.MAIN, "Toxic Carro · Vibração Máxima")) {
            val p = load(name)
            val g60 = db(amp(render(p, tone(2, 60.0, 0.1)), 60.0) / 0.1)
            val g1k = db(amp(render(p, tone(2, 1000.0, 0.1)), 1000.0) / 0.1)
            assertTrue("$name: 60 Hz precisa passar bem dos médios (sentir a vibração): $g60 x $g1k", g60 > g1k + 5.0)
        }
    }

    @Test fun ajustesDeCarroSobemNoJsonEVoltam() {
        AudioFx.resetAll()
        AudioFx.hpfOn = true; AudioFx.hpfFreq = 55f; AudioFx.hpfSlope = 36; AudioFx.hpfType = XoverType.BUTTERWORTH
        AudioFx.vbOn = true; AudioFx.vbAmount = 0.7f; AudioFx.delayL = 3.5f; AudioFx.trimR = -4f
        AudioFx.invertR = true; AudioFx.contour = 0.4f; AudioFx.monoBassOn = true; AudioFx.blOn = true; AudioFx.blCeil = -5f
        AudioFx.apply()
        val json = AudioFx.exportCurrent()
        AudioFx.resetAll()
        assertFalse(AudioFx.hpfOn)
        assertEquals("Preset carregado com sucesso.", AudioFx.importJson(json))
        val p = DspEngine.core.params
        assertTrue(p.hpfOn); assertEquals(55f, p.hpfFreq, 0.01f); assertEquals(36, p.hpfSlope); assertEquals(XoverType.BUTTERWORTH, p.hpfType)
        assertTrue(p.vbOn); assertEquals(0.7f, p.vbAmount, 0.001f); assertEquals(3.5f, p.delayLms, 0.001f)
        assertEquals(-4f, p.trimRdb, 0.001f); assertTrue(p.invertR); assertEquals(0.4f, p.contour, 0.001f)
        assertTrue(p.monoBassOn); assertTrue(p.blOn); assertEquals(-5f, p.blCeilDb, 0.001f)
    }

    @Test fun valoresDeCarroForaDaFaixaSaoLimitados() {
        AudioFx.resetAll()
        val j = """{"toxic":"preset","eq":[0,0,0,0,0,0,0,0,0,0],"hpfOn":true,"hpfS":17,"hpfF":99999,"hpfT":"NAO_EXISTE","dL":500,"vbA":9,"tR":-99}"""
        assertEquals("Preset carregado com sucesso.", AudioFx.importJson(j))
        assertEquals(18, AudioFx.hpfSlope)                       // 17 vira a inclinação válida mais próxima
        assertEquals(250f, AudioFx.hpfFreq, 0.001f)
        assertEquals(XoverType.LINKWITZ_RILEY, AudioFx.hpfType)  // tipo desconhecido volta ao padrão
        assertEquals(DspCore.MAX_DELAY_MS, AudioFx.delayL, 0.001f)
        assertEquals(1f, AudioFx.vbAmount, 0.001f)
        assertEquals(-24f, AudioFx.trimR, 0.001f)
    }

    @Test fun zerarCarroNaoMexeNoEq() {
        AudioFx.resetAll()
        assertTrue(AudioFx.applyCarPreset(CarPresets.MAIN))
        AudioFx.bands[0] = 3f; AudioFx.apply()
        AudioFx.resetCar()
        assertFalse(AudioFx.hpfOn); assertFalse(AudioFx.vbOn); assertFalse(AudioFx.blOn); assertEquals(0f, AudioFx.air, 0f)
        assertEquals(3f, AudioFx.bands[0], 0.001f)
        assertTrue(AudioFx.filters.isNotEmpty())
    }

    @Test fun efeitosTocamPeloMotorEOContadorDeQuadrosSobe() {
        val core = DspCore().also { it.configure(sr, 2); it.params = load(CarPresets.MAIN) }
        assertEquals(0L, core.meters.frames)
        core.process(FloatArray(2048 * 2), 2048)
        assertEquals(2048L, core.meters.frames)
        core.process(FloatArray(1024 * 2), 1024)
        assertEquals(3072L, core.meters.frames)
    }
}
