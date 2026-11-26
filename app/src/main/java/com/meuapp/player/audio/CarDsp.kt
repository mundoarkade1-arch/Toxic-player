package com.meuapp.player.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Blocos de processamento "de carro" (Kotlin puro, sem Android): cortes (crossover), grave virtual,
 * sub-harmônico, limitador de graves, alinhamento de tempo e medidor entrada x saída.
 * Todos trabalham em Double, amostra a amostra, e são usados pelo [DspCore].
 */

enum class XoverType(val label: String) {
    BUTTERWORTH("Butterworth"), LINKWITZ_RILEY("Linkwitz-Riley")
}

object Crossover {
    /** Inclinações oferecidas (dB por oitava). */
    val SLOPES = intArrayOf(6, 12, 18, 24, 36, 48)

    /** Linkwitz-Riley só existe em múltiplos de 12 dB/oitava (duas Butterworth em série): 18 vira 24. */
    fun effectiveSlope(type: XoverType, slopeDb: Int): Int =
        if (type == XoverType.LINKWITZ_RILEY) 12 * ceil(slopeDb.coerceIn(6, 48) / 12.0).toInt() else slopeDb.coerceIn(6, 48)

    /** Ordem do filtro Butterworth para uma inclinação (6 dB = 1ª ordem). */
    private fun butterOrder(slopeDb: Int) = (slopeDb / 6).coerceIn(1, 8)

    private fun butterSections(highPass: Boolean, order: Int, freq: Double, sr: Double): List<BiquadCoef> {
        val out = ArrayList<BiquadCoef>()
        val type = if (highPass) FilterType.HIGH_PASS else FilterType.LOW_PASS
        for (k in 1..order / 2) {
            val q = 1.0 / (2.0 * sin((2 * k - 1) * PI / (2.0 * order)))
            out.add(BiquadCoef.design(EqFilter(type, freq.toFloat(), 0f, q.toFloat()), sr))
        }
        if (order % 2 == 1) out.add(BiquadCoef.firstOrder(!highPass, freq, sr))
        return out
    }

    /** Seções biquad do filtro de corte. Passa-altas ([highPass] = true) ou passa-baixas. */
    fun sections(highPass: Boolean, type: XoverType, slopeDb: Int, freq: Double, sr: Double): List<BiquadCoef> {
        val f = freq.coerceIn(20.0, 0.45 * sr)
        return if (type == XoverType.BUTTERWORTH) {
            butterSections(highPass, butterOrder(slopeDb), f, sr)
        } else {
            val n = ceil(slopeDb.coerceIn(6, 48) / 12.0).toInt()   // LR = Butterworth de ordem n em série consigo mesma
            val one = butterSections(highPass, n, f, sr)
            one + one
        }
    }

    fun responseDb(list: List<BiquadCoef>, f: Double, sr: Double): Double {
        var db = 0.0
        for (c in list) db += c.responseDb(f, sr)
        return db
    }
}

/** Cascata de biquads com estado para 2 canais. Trocar os coeficientes não zera o estado (sem estalos ao mexer no controle). */
internal class Cascade(private val max: Int) {
    private val c = Array(max) { BiquadCoef.IDENTITY }
    private val z1 = Array(2) { DoubleArray(max) }
    private val z2 = Array(2) { DoubleArray(max) }
    var n = 0; private set

    fun set(list: List<BiquadCoef>) {
        val k = min(max, list.size)
        for (i in 0 until k) {
            if (i >= n) { z1[0][i] = 0.0; z1[1][i] = 0.0; z2[0][i] = 0.0; z2[1][i] = 0.0 }
            c[i] = list[i]
        }
        n = k
    }

    fun run(ch: Int, x: Double): Double {
        var y = x
        for (i in 0 until n) {
            val q = c[i]
            val o = q.b0 * y + z1[ch][i]
            z1[ch][i] = q.b1 * y - q.a1 * o + z2[ch][i]
            z2[ch][i] = q.b2 * y - q.a2 * o
            y = o
        }
        return y
    }

    fun reset() { for (a in 0 until 2) { z1[a].fill(0.0); z2[a].fill(0.0) } }
}

private fun lowPass2(f: Double, sr: Double) =
    BiquadCoef.design(EqFilter(FilterType.LOW_PASS, f.toFloat(), 0f, 0.7071f), sr)
private fun highPass2(f: Double, sr: Double) =
    BiquadCoef.design(EqFilter(FilterType.HIGH_PASS, f.toFloat(), 0f, 0.7071f), sr)

/**
 * Grave virtual (harmônicos psicoacústicos). Pega os graves ABAIXO de [freq] (onde alto-falante de porta quase
 * não entrega nada), gera harmônicos deles (2ª, 3ª...) acima de [freq] e soma. O ouvido "completa" a
 * fundamental que falta e percebe grave mais fundo, sem o cone se mexer tanto.
 */
internal class VirtualBass {
    private val src = Cascade(2)
    private val hp = Cascade(2)
    private val lp = Cascade(1)
    private var env = 0.0
    private var envA = 0.0; private var envR = 0.0
    var gain = 0.0

    fun configure(freq: Double, amount: Double, sr: Double) {
        src.set(listOf(lowPass2(freq, sr), lowPass2(freq, sr)))
        hp.set(listOf(highPass2(freq * 1.25, sr), highPass2(freq * 1.25, sr)))   // só harmônicos: a própria fundamental não volta (evita soma/cancelamento com o original)
        lp.set(listOf(lowPass2(min(freq * 3.5, 0.4 * sr), sr)))
        envA = 1.0 - exp(-1.0 / (0.003 * sr)); envR = 1.0 - exp(-1.0 / (0.06 * sr))
        gain = amount.coerceIn(0.0, 1.0) * 1.6
    }

    fun process(x: Double): Double {
        val lo = src.run(0, x)
        val a = abs(lo)
        env += (a - env) * (if (a > env) envA else envR)
        val g = max(env, 0.003)
        val nrm = (lo / g).coerceIn(-2.0, 2.0)
        val h = 0.7 * tanh(2.2 * nrm) + 0.3 * (abs(nrm) - 0.7)
        return lp.run(0, hp.run(0, h)) * env * gain
    }

    fun reset() { src.reset(); hp.reset(); lp.reset(); env = 0.0 }
}

/**
 * Sub-harmônico: gera uma oitava ABAIXO dos graves entre ~50 e [freq] (divisor de frequência por 2).
 * Só faz sentido com subwoofer; em alto-falante de porta fica desligado.
 */
internal class SubSynth {
    private val src = Cascade(2)
    private val sm = Cascade(2)
    private var env = 0.0
    private var envA = 0.0; private var envR = 0.0
    private var pos = false
    private var flip = 1.0
    private var gate = 0.0
    var gain = 0.0

    fun configure(freq: Double, amount: Double, sr: Double) {
        src.set(listOf(lowPass2(freq, sr), lowPass2(freq, sr)))
        val fo = max(freq * 0.5, 20.0)
        sm.set(listOf(lowPass2(fo, sr), lowPass2(fo, sr)))
        envA = 1.0 - exp(-1.0 / (0.004 * sr)); envR = 1.0 - exp(-1.0 / (0.08 * sr))
        gain = amount.coerceIn(0.0, 1.0) * 1.3
    }

    fun process(x: Double): Double {
        val lo = src.run(0, x)
        val a = abs(lo)
        env += (a - env) * (if (a > env) envA else envR)
        val hys = 0.15 * env + 1e-5
        if (pos) { if (lo < -hys) pos = false } else if (lo > hys) { pos = true; flip = -flip }
        gate += ((if (env > 0.01) 1.0 else 0.0) - gate) * 0.002
        return sm.run(0, flip) * env * gate * gain
    }

    fun reset() { src.reset(); sm.reset(); env = 0.0; pos = false; flip = 1.0; gate = 0.0 }
}

/** Realce de agudos ("ar"): gera harmônicos acima de 8 kHz a partir da região de presença (4,5 a 7 kHz). */
internal class AirExciter {
    private val inp = Cascade(3)
    private val outHp = Cascade(2)
    private val env = DoubleArray(2)
    private var envA = 0.0; private var envR = 0.0
    var gain = 0.0

    fun configure(amount: Double, sr: Double) {
        inp.set(listOf(highPass2(4500.0, sr), highPass2(4500.0, sr), lowPass2(7000.0, sr)))
        outHp.set(listOf(highPass2(8000.0, sr), highPass2(8000.0, sr)))
        envA = 1.0 - exp(-1.0 / (0.001 * sr)); envR = 1.0 - exp(-1.0 / (0.03 * sr))
        gain = amount.coerceIn(0.0, 1.0) * 0.9
    }

    fun process(ch: Int, x: Double): Double {
        val b = inp.run(ch, x)
        val a = abs(b)
        env[ch] += (a - env[ch]) * (if (a > env[ch]) envA else envR)
        val g = max(env[ch], 0.001)
        val nrm = (b / g).coerceIn(-2.0, 2.0)
        return outHp.run(ch, tanh(2.5 * nrm) - 0.4 * nrm) * env[ch] * gain
    }

    fun reset() { inp.reset(); outHp.reset(); env.fill(0.0) }
}

/**
 * Limitador de graves (proteção do alto-falante): separa em duas faixas com um cruzamento Linkwitz-Riley de 48 dB
 * (passa-baixas + passa-altas explícitos: somados voltam ao sinal original, sem buraco nem bolha na região do corte),
 * limita só a faixa abaixo de [freq] no teto e soma de volta. Médios e agudos não são tocados, então a voz continua
 * inteira mesmo com grave forte.
 */
internal class BassLimiter {
    private val lp = Cascade(4)   // Linkwitz-Riley 48 dB/oitava: quase nada da faixa de cima vaza para a faixa limitada
    private val hp = Cascade(4)
    private var ceil = 1.0
    private var att = 0.0; private var rel = 0.0
    private var g = 1.0
    var outL = 0.0; var outR = 0.0
    var maxGrDb = 0.0

    fun configure(freq: Double, ceilDb: Double, sr: Double) {
        lp.set(Crossover.sections(false, XoverType.LINKWITZ_RILEY, 48, freq, sr))
        hp.set(Crossover.sections(true, XoverType.LINKWITZ_RILEY, 48, freq, sr))
        ceil = Math.pow(10.0, ceilDb / 20.0)
        att = 1.0 - exp(-1.0 / (0.0005 * sr)); rel = 1.0 - exp(-1.0 / (0.08 * sr))
    }

    fun process(l: Double, r: Double) {
        val lowL = lp.run(0, l); val lowR = lp.run(1, r)
        val peak = max(abs(lowL), abs(lowR))
        val target = if (peak > ceil) ceil / peak else 1.0
        g += (target - g) * (if (target < g) att else rel)
        if (g < 0.9999) { val db = -20.0 * log10(g); if (db > maxGrDb) maxGrDb = db }
        outL = hp.run(0, l) + lowL * g
        outR = hp.run(1, r) + lowR * g
    }

    fun reset() { lp.reset(); hp.reset(); g = 1.0; maxGrDb = 0.0 }
}

/** Linha de atraso por canal (alinhamento de tempo). 1 amostra a 48 kHz ≈ 7 mm de caminho do som. */
internal class DelayLine(size: Int) {
    private val buf = DoubleArray(max(size, 2))
    private var pos = 0
    val capacity get() = buf.size - 1

    fun process(x: Double, delaySamples: Int): Double {
        buf[pos] = x
        val d = delaySamples.coerceIn(0, buf.size - 1)
        var idx = pos - d
        if (idx < 0) idx += buf.size
        pos++; if (pos == buf.size) pos = 0
        return buf[idx]
    }

    fun reset() { buf.fill(0.0); pos = 0 }
}

/**
 * Medidor de bandas (graves de 40 a 160 Hz = o que o alto-falante de porta entrega, médios de 160 Hz a 4 kHz,
 * agudos acima de 4 kHz) para comparar ENTRADA x SAÍDA e mostrar, em dB, o quanto o processamento realmente
 * mudou o som. Média móvel de ~0,4 s.
 */
internal class BandMeter {
    private val lo = Cascade(3); private val midHp = Cascade(1); private val midLp = Cascade(1); private val hi = Cascade(2)
    private val e = DoubleArray(3)
    private var coef = 0.0

    fun configure(sr: Double) {
        lo.set(listOf(highPass2(40.0, sr), lowPass2(160.0, sr), lowPass2(160.0, sr)))
        midHp.set(listOf(highPass2(160.0, sr)))
        midLp.set(listOf(lowPass2(4000.0, sr)))
        hi.set(listOf(highPass2(4000.0, sr), highPass2(4000.0, sr)))
        coef = 1.0 - exp(-1.0 / (0.4 * sr))
    }

    fun push(x: Double) {
        val a = lo.run(0, x)
        val b = midLp.run(0, midHp.run(0, x))
        val c = hi.run(0, x)
        e[0] += (a * a - e[0]) * coef
        e[1] += (b * b - e[1]) * coef
        e[2] += (c * c - e[2]) * coef
    }

    fun power(i: Int) = e[i]
    fun reset() { lo.reset(); midHp.reset(); midLp.reset(); hi.reset(); e.fill(0.0) }
}
