package com.meuapp.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType(val label: String) {
    PEAK("Peak"), LOW_SHELF("Low Shelf"), HIGH_SHELF("High Shelf"),
    HIGH_PASS("Passa-altas"), LOW_PASS("Passa-baixas"), NOTCH("Notch");

    /** Passa-altas, passa-baixas e notch não usam ganho. */
    val usesGain: Boolean get() = this == PEAK || this == LOW_SHELF || this == HIGH_SHELF
}

/** Um filtro do equalizador paramétrico. */
data class EqFilter(
    val type: FilterType = FilterType.PEAK,
    val freq: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1.0f,
    val enabled: Boolean = true
)

/** Filtro biquad (coeficientes normalizados, a0 = 1). Fórmulas do "Audio EQ Cookbook" (RBJ). */
class BiquadCoef(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Resposta em frequência (dB) deste filtro na frequência [f] para a taxa [sr]. */
    fun responseDb(f: Double, sr: Double): Double {
        val w = 2.0 * PI * f / sr
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2
        val ni = -(b1 * sw + b2 * s2)
        val dr = 1.0 + a1 * cw + a2 * c2
        val di = -(a1 * sw + a2 * s2)
        return 10.0 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    companion object {
        val IDENTITY = BiquadCoef(1.0, 0.0, 0.0, 0.0, 0.0)

        fun design(f: EqFilter, sr: Double): BiquadCoef {
            val freq = f.freq.toDouble().coerceIn(20.0, 0.45 * sr)
            val q = f.q.toDouble().coerceIn(0.1, 18.0)
            val w0 = 2.0 * PI * freq / sr
            val cw = cos(w0)
            val alpha = sin(w0) / (2.0 * q)
            val a = 10.0.pow(f.gainDb.toDouble() / 40.0)
            val sa = sqrt(a)
            val b0: Double; val b1: Double; val b2: Double
            val a0: Double; val a1: Double; val a2: Double
            when (f.type) {
                FilterType.PEAK -> {
                    b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                    a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
                }
                FilterType.LOW_SHELF -> {
                    b0 = a * ((a + 1) - (a - 1) * cw + 2 * sa * alpha)
                    b1 = 2 * a * ((a - 1) - (a + 1) * cw)
                    b2 = a * ((a + 1) - (a - 1) * cw - 2 * sa * alpha)
                    a0 = (a + 1) + (a - 1) * cw + 2 * sa * alpha
                    a1 = -2 * ((a - 1) + (a + 1) * cw)
                    a2 = (a + 1) + (a - 1) * cw - 2 * sa * alpha
                }
                FilterType.HIGH_SHELF -> {
                    b0 = a * ((a + 1) + (a - 1) * cw + 2 * sa * alpha)
                    b1 = -2 * a * ((a - 1) + (a + 1) * cw)
                    b2 = a * ((a + 1) + (a - 1) * cw - 2 * sa * alpha)
                    a0 = (a + 1) - (a - 1) * cw + 2 * sa * alpha
                    a1 = 2 * ((a - 1) - (a + 1) * cw)
                    a2 = (a + 1) - (a - 1) * cw - 2 * sa * alpha
                }
                FilterType.LOW_PASS -> {
                    b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2
                    a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
                }
                FilterType.HIGH_PASS -> {
                    b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2
                    a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
                }
                FilterType.NOTCH -> {
                    b0 = 1.0; b1 = -2 * cw; b2 = 1.0
                    a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
                }
            }
            return BiquadCoef(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }
}
