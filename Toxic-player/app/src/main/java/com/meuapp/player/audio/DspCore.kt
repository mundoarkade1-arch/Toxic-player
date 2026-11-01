package com.meuapp.player.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Medidores publicados a cada bloco de áudio. Valores lineares (0..1) e dB. */
data class Meters(
    val peakL: Float = 0f, val peakR: Float = 0f,
    val rmsL: Float = 0f, val rmsR: Float = 0f,
    val compGrDb: Float = 0f, val limGrDb: Float = 0f,
    val clip: Boolean = false
)

/** Todos os parâmetros do processamento. Imutável: a interface troca o objeto inteiro. */
data class DspParams(
    val enabled: Boolean = true,
    val preampDb: Float = 0f,
    val autoHeadroom: Boolean = false,
    val balance: Float = 0f,                 // -1 (esquerda) .. +1 (direita)
    val graphicOn: Boolean = true,
    val graphic: List<Float> = List(10) { 0f },
    val paramOn: Boolean = true,
    val filters: List<EqFilter> = emptyList(),
    val compOn: Boolean = false,
    val compThr: Float = -20f,
    val compRatio: Float = 3f,
    val compAttackMs: Float = 10f,
    val compReleaseMs: Float = 120f,
    val compKneeDb: Float = 6f,
    val compMakeupDb: Float = 0f,
    val compRms: Boolean = true,
    val limOn: Boolean = true,
    val limCeilingDb: Float = -1f,
    val limReleaseMs: Float = 60f,
    val width: Float = 1f,                   // 0 = mono, 1 = original, 2 = bem largo
    val crossfeed: Float = 0f                // 0..1
)

/**
 * Motor de DSP próprio (Kotlin puro, sem Android). Cadeia por amostra:
 * pré-amp/balanço -> EQ gráfico (10 peaks) -> EQ paramétrico (até 16 filtros) ->
 * compressor (RMS/pico, joelho suave) -> largura estéreo (M/S) + crossfeed ->
 * limitador com look-ahead de 5 ms + proteção final no teto.
 * Latência fixa de 5 ms (a linha de atraso do look-ahead fica sempre ativa).
 * O limitador NÃO é true-peak: trabalha em amostras (sample peak).
 */
class DspCore {
    companion object {
        val GRAPHIC_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val GRAPHIC_Q = 1.41f
        const val MAX_PARAM = 16
        const val SLOTS = 10 + MAX_PARAM
        const val SPEC_N = 2048
    }

    @Volatile var params = DspParams()
    @Volatile var meters = Meters()
    var latencyFrames = 240; private set

    private var sr = 48000
    private var ch = 2
    private var applied: DspParams? = null

    private val coefs = Array(SLOTS) { BiquadCoef.IDENTITY }
    private val active = BooleanArray(SLOTS)
    private var z1 = Array(2) { DoubleArray(SLOTS) }
    private var z2 = Array(2) { DoubleArray(SLOTS) }

    private var preLin = 1.0; private var gL = 1.0; private var gR = 1.0
    private var cAtt = 0.0; private var cRel = 0.0; private var rmsCoef = 0.0
    private var lAtt = 0.0; private var lRel = 0.0; private var ceilLin = 1.0
    private var cfCoef = 0.0

    private var rmsState = 0.0; private var grState = 0.0
    private var gLim = 1.0
    private var cfL = 0.0; private var cfR = 0.0
    private var delay = Array(2) { FloatArray(240) }; private var dPos = 0

    private val specBuf = FloatArray(SPEC_N)
    @Volatile private var specPos = 0

    fun configure(sampleRate: Int, channels: Int) {
        sr = sampleRate
        ch = channels.coerceIn(1, 2)
        latencyFrames = max(1, (0.005 * sr).toInt())
        delay = Array(2) { FloatArray(latencyFrames) }
        dPos = 0
        z1 = Array(2) { DoubleArray(SLOTS) }
        z2 = Array(2) { DoubleArray(SLOTS) }
        rmsState = 0.0; grState = 0.0; gLim = 1.0; cfL = 0.0; cfR = 0.0
        applied = null
    }

    private fun setSlot(i: Int, on: Boolean, c: BiquadCoef) {
        if (on && !active[i]) { z1[0][i] = 0.0; z1[1][i] = 0.0; z2[0][i] = 0.0; z2[1][i] = 0.0 }
        active[i] = on
        coefs[i] = c
    }

    private fun coefTime(ms: Float) = exp(-1.0 / (max(ms, 0.01f) / 1000.0 * sr))

    private fun rebuild(p: DspParams) {
        val srd = sr.toDouble()
        for (i in 0 until 10) {
            val g = p.graphic.getOrElse(i) { 0f }
            val on = p.graphicOn && abs(g) > 0.01f
            setSlot(i, on, if (on) BiquadCoef.design(EqFilter(FilterType.PEAK, GRAPHIC_FREQS[i].toFloat(), g, GRAPHIC_Q), srd) else BiquadCoef.IDENTITY)
        }
        for (k in 0 until MAX_PARAM) {
            val f = p.filters.getOrNull(k)
            val on = p.paramOn && f != null && f.enabled && (!f.type.usesGain || abs(f.gainDb) > 0.01f)
            setSlot(10 + k, on, if (on && f != null) BiquadCoef.design(f, srd) else BiquadCoef.IDENTITY)
        }
        // Atenuação automática: compensa o maior reforço do EQ
        var maxBoost = 0.0
        if (p.autoHeadroom) {
            for (s in 0 until 96) {
                val f = 20.0 * (20000.0 / 20.0).pow(s / 95.0)
                var db = 0.0
                for (i in 0 until SLOTS) if (active[i]) db += coefs[i].responseDb(f, srd)
                if (db > maxBoost) maxBoost = db
            }
        }
        preLin = 10.0.pow((p.preampDb - maxBoost) / 20.0)
        gL = if (p.balance > 0f) (1.0 - p.balance) else 1.0
        gR = if (p.balance < 0f) (1.0 + p.balance) else 1.0
        cAtt = coefTime(p.compAttackMs); cRel = coefTime(p.compReleaseMs)
        rmsCoef = 1.0 - exp(-1.0 / (0.05 * sr))
        lAtt = coefTime(1.2f); lRel = coefTime(p.limReleaseMs)
        ceilLin = 10.0.pow(p.limCeilingDb / 20.0)
        cfCoef = 1.0 - exp(-2.0 * PI * 700.0 / sr)
        applied = p
    }

    /** Processa [frames] quadros de áudio intercalado (L R L R...) em float, no lugar. */
    fun process(buf: FloatArray, frames: Int) {
        val p = params
        if (p !== applied) rebuild(p)
        val c = ch
        val enabled = p.enabled
        val limOn = enabled && p.limOn
        val clampTo = if (limOn) ceilLin else 1.0
        val thr = p.compThr.toDouble()
        val ratio = max(p.compRatio.toDouble(), 1.0)
        val knee = max(p.compKneeDb.toDouble(), 0.01)
        val width = p.width.toDouble()
        val k = 0.5 * p.crossfeed
        var pkL = 0f; var pkR = 0f; var sumL = 0.0; var sumR = 0.0
        var clip = false; var maxComp = 0.0; var maxLim = 0.0

        for (n in 0 until frames) {
            val i0 = n * c
            var l = buf[i0].toDouble()
            var r = if (c == 2) buf[i0 + 1].toDouble() else l

            if (enabled) {
                l *= preLin * gL; r *= preLin * gR
                for (s in 0 until SLOTS) {
                    if (!active[s]) continue
                    val q = coefs[s]
                    var y = q.b0 * l + z1[0][s]
                    z1[0][s] = q.b1 * l - q.a1 * y + z2[0][s]
                    z2[0][s] = q.b2 * l - q.a2 * y
                    l = y
                    y = q.b0 * r + z1[1][s]
                    z1[1][s] = q.b1 * r - q.a1 * y + z2[1][s]
                    z2[1][s] = q.b2 * r - q.a2 * y
                    r = y
                }
                if (abs(l) >= 1.0 || abs(r) >= 1.0) clip = true

                if (p.compOn) {
                    val lvl = if (p.compRms) {
                        rmsState += (0.5 * (l * l + r * r) - rmsState) * rmsCoef
                        10.0 * log10(max(rmsState, 1e-12))
                    } else {
                        20.0 * log10(max(max(abs(l), abs(r)), 1e-6))
                    }
                    val over = lvl - thr
                    val out = when {
                        2.0 * over < -knee -> lvl
                        2.0 * abs(over) <= knee -> lvl + (1.0 / ratio - 1.0) * (over + knee / 2.0).pow(2) / (2.0 * knee)
                        else -> thr + over / ratio
                    }
                    val gr = out - lvl
                    val coef = if (gr < grState) cAtt else cRel
                    grState = gr + coef * (grState - gr)
                    val gain = 10.0.pow((grState + p.compMakeupDb) / 20.0)
                    l *= gain; r *= gain
                    if (-grState > maxComp) maxComp = -grState
                } else {
                    grState = 0.0
                }

                if (c == 2) {
                    val m = 0.5 * (l + r)
                    val s = 0.5 * (l - r) * width
                    var l2 = m + s; var r2 = m - s
                    if (k > 0.0) {
                        cfL += (l2 - cfL) * cfCoef
                        cfR += (r2 - cfR) * cfCoef
                        val oL = (l2 + k * cfR) / (1.0 + k)
                        val oR = (r2 + k * cfL) / (1.0 + k)
                        l2 = oL; r2 = oR
                    }
                    l = l2; r = r2
                }
            }

            // Limitador com look-ahead (atraso fixo) + proteção final
            val peak = max(abs(l), abs(r))
            val gt = if (limOn && peak > ceilLin) ceilLin / peak else 1.0
            gLim = gt + (if (gt < gLim) lAtt else lRel) * (gLim - gt)
            if (gLim < 0.9999) { val db = -20.0 * log10(gLim); if (db > maxLim) maxLim = db }

            val dl = delay[0][dPos]; val dr = delay[1][dPos]
            delay[0][dPos] = l.toFloat(); delay[1][dPos] = r.toFloat()
            dPos++; if (dPos == latencyFrames) dPos = 0
            val ol = (dl * gLim).coerceIn(-clampTo, clampTo)
            val orr = (dr * gLim).coerceIn(-clampTo, clampTo)

            buf[i0] = ol.toFloat()
            if (c == 2) buf[i0 + 1] = orr.toFloat()
            val al = abs(ol).toFloat(); val ar = abs(orr).toFloat()
            if (al > pkL) pkL = al
            if (ar > pkR) pkR = ar
            sumL += ol * ol; sumR += orr * orr
            specBuf[specPos and (SPEC_N - 1)] = (0.5 * (ol + orr)).toFloat()
            specPos++
        }
        if (frames > 0) {
            meters = Meters(pkL, pkR, sqrt(sumL / frames).toFloat(), sqrt(sumR / frames).toFloat(), maxComp.toFloat(), maxLim.toFloat(), clip)
        }
    }

    /** Espectro real do que está saindo: [bands] barras (0..1) entre 40 Hz e 16 kHz. */
    fun spectrum(bands: Int): FloatArray {
        val tmp = FloatArray(SPEC_N)
        val start = specPos
        for (i in 0 until SPEC_N) {
            val w = 0.5 - 0.5 * kotlin.math.cos(2.0 * PI * i / SPEC_N)
            tmp[i] = (specBuf[(start + i) and (SPEC_N - 1)] * w).toFloat()
        }
        val mag = Fft.magnitudes(tmp)
        val out = FloatArray(bands)
        for (b in 0 until bands) {
            val f0 = 40.0 * (16000.0 / 40.0).pow(b.toDouble() / bands)
            val f1 = 40.0 * (16000.0 / 40.0).pow((b + 1).toDouble() / bands)
            val i0 = (f0 * SPEC_N / sr).toInt().coerceIn(1, mag.size - 1)
            val i1 = max(i0 + 1, (f1 * SPEC_N / sr).toInt()).coerceAtMost(mag.size)
            var m = 0f
            for (i in i0 until i1) if (mag[i] > m) m = mag[i]
            val amp = m / (SPEC_N / 4f)
            val db = 20f * log10(max(amp, 1e-6f))
            out[b] = ((db + 80f) / 80f).coerceIn(0f, 1f)
        }
        return out
    }
}
