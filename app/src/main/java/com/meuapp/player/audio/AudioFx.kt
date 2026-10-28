package com.meuapp.player.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.ln

/**
 * Cadeia de áudio: Pré-amplificador (LoudnessEnhancer) -> EQ (Equalizer) -> Compressor/Limitador (DynamicsProcessing).
 * Os valores ficam em estados do Compose, então a interface atualiza sozinha.
 */
object AudioFx {
    private var eq: Equalizer? = null
    private var boost: LoudnessEnhancer? = null
    private var dp: DynamicsProcessing? = null

    val bands = mutableStateListOf<Float>()          // ganho de cada banda em dB
    var freqs by mutableStateOf<List<Int>>(emptyList()) // frequência central (Hz)
    var minDb by mutableFloatStateOf(-15f)
    var maxDb by mutableFloatStateOf(15f)
    var enabled by mutableStateOf(true)
    var preamp by mutableFloatStateOf(0f)            // 0..10 dB
    var compOn by mutableStateOf(false)
    var threshold by mutableFloatStateOf(-12f)       // dB
    var ratio by mutableFloatStateOf(4f)             // x:1

    /** Chamado pelo player quando o id da sessão de áudio muda. */
    fun attach(session: Int) {
        if (session == 0) return
        release()
        try {
            val e = Equalizer(0, session)
            eq = e
            val n = e.numberOfBands.toInt()
            freqs = List(n) { e.getCenterFreq(it.toShort()) / 1000 }
            minDb = e.bandLevelRange[0] / 100f
            maxDb = e.bandLevelRange[1] / 100f
            if (bands.size != n) { bands.clear(); repeat(n) { bands.add(0f) } }
        } catch (e: Exception) { }
        try { boost = LoudnessEnhancer(session) } catch (e: Exception) { }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, false, 0, false, 0, true
                ).build()
                dp = DynamicsProcessing(0, session, cfg)
            } catch (e: Exception) { }
        }
        apply()
    }

    /** Aplica todos os valores atuais nos efeitos. */
    fun apply() {
        try {
            eq?.let { e ->
                e.setEnabled(enabled)
                val lo = e.bandLevelRange[0].toInt(); val hi = e.bandLevelRange[1].toInt()
                for (i in bands.indices) e.setBandLevel(i.toShort(), (bands[i] * 100).toInt().coerceIn(lo, hi).toShort())
            }
        } catch (e: Exception) { }
        try {
            boost?.let { it.setEnabled(enabled); it.setTargetGain((preamp * 100).toInt()) }
        } catch (e: Exception) { }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                dp?.let { d ->
                    d.setEnabled(enabled && compOn)
                    val lim = DynamicsProcessing.Limiter(true, compOn, 0, 5f, 80f, ratio, threshold, 0f)
                    for (ch in 0 until 2) d.setLimiterByChannelIndex(ch, lim)
                }
            } catch (e: Exception) { }
        }
    }

    /** Presets rápidos: função que devolve o ganho (dB) para cada frequência. */
    fun preset(f: (Int) -> Float) {
        for (i in bands.indices) bands[i] = f(freqs[i]).coerceIn(minDb, maxDb)
        apply()
    }

    /** Importa texto no formato Equalizer APO / AutoEQ. Retorna quantos filtros foram lidos. */
    fun importApo(text: String): Int {
        if (bands.isEmpty()) return 0
        val re = Regex("""Filter\s*\d*:\s*ON\s+\w+\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
        val acc = FloatArray(bands.size); var n = 0
        re.findAll(text).forEach { m ->
            val fc = m.groupValues[1].toFloatOrNull() ?: return@forEach
            val g = m.groupValues[2].toFloatOrNull() ?: return@forEach
            val idx = freqs.indices.minByOrNull { abs(ln(freqs[it].toFloat() / fc)) } ?: return@forEach
            acc[idx] += g; n++
        }
        Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE).find(text)?.let {
            preamp = (it.groupValues[1].toFloatOrNull() ?: 0f).coerceIn(0f, 10f)
        }
        for (i in bands.indices) bands[i] = acc[i].coerceIn(minDb, maxDb)
        apply()
        return n
    }

    fun release() {
        try { eq?.release() } catch (e: Exception) { }
        try { boost?.release() } catch (e: Exception) { }
        try { dp?.release() } catch (e: Exception) { }
        eq = null; boost = null; dp = null
    }
}
