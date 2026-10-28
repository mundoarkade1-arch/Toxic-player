package com.meuapp.player.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln

/**
 * Cadeia de áudio do Toxic Player:
 * Pré-amp + Balanço -> EQ de 10 bandas -> Compressor multibanda (3 bandas) -> Limitador
 * (tudo no DynamicsProcessing) + Loudness + Bass Boost + Virtualizer (surround).
 * Os valores ficam em estados do Compose e são salvos automaticamente no aparelho.
 */
object AudioFx {
    val EQ_FREQS = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val PRESETS: Map<String, List<Float>> = linkedMapOf(
        "Flat" to List(10) { 0f },
        "Bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Vocal" to listOf(-2f, -2f, -1f, 0f, 2f, 3f, 4f, 2f, 0f, -1f),
        "Rock" to listOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4f),
        "Eletrônica" to listOf(5f, 4f, 2f, 0f, -2f, 2f, 1f, 3f, 4f, 5f),
        "Pop" to listOf(-1f, 0f, 2f, 3f, 3f, 2f, 0f, -1f, -1f, -2f),
        "Studio" to listOf(1f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 3f),
        "Graves pesados" to listOf(9f, 8f, 6f, 3f, 0f, 0f, 0f, 0f, 0f, 0f)
    )

    private var dp: DynamicsProcessing? = null
    private var boost: LoudnessEnhancer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var prefs: SharedPreferences? = null

    var ready by mutableStateOf(false)          // true quando há uma música tocando/preparada
    val bands = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    var enabled by mutableStateOf(true)         // liga/desliga tudo
    var eqOn by mutableStateOf(true)
    var preamp by mutableFloatStateOf(0f)       // -12..+12 dB
    var loudness by mutableFloatStateOf(0f)     // 0..10 dB
    var balance by mutableFloatStateOf(0f)      // -1 (esq) .. +1 (dir)
    var compOn by mutableStateOf(false)
    var thr by mutableFloatStateOf(-20f)        // dB
    var ratio by mutableFloatStateOf(3f)        // x:1
    var attack by mutableFloatStateOf(10f)      // ms
    var release by mutableFloatStateOf(120f)    // ms
    var makeup by mutableFloatStateOf(0f)       // dB
    var limOn by mutableStateOf(true)
    var limThr by mutableFloatStateOf(-1f)      // dB
    var bassPct by mutableFloatStateOf(0f)      // 0..100
    var virtPct by mutableFloatStateOf(0f)      // 0..100
    val customNames = mutableStateListOf<String>()

    // ---------- Inicialização e persistência ----------
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("toxic_fx", Context.MODE_PRIVATE)
        prefs = p
        p.getString("current", null)?.let { s -> try { restore(JSONObject(s)) } catch (e: Exception) { } }
        refreshCustom()
    }

    private fun JSONObject.f(k: String, d: Float) = optDouble(k, d.toDouble()).toFloat()

    private fun snapshot(): JSONObject = JSONObject().apply {
        put("eq", JSONArray(bands.map { it.toDouble() }))
        put("eqOn", eqOn); put("pre", preamp.toDouble()); put("loud", loudness.toDouble()); put("bal", balance.toDouble())
        put("compOn", compOn); put("thr", thr.toDouble()); put("ratio", ratio.toDouble())
        put("att", attack.toDouble()); put("rel", release.toDouble()); put("mk", makeup.toDouble())
        put("limOn", limOn); put("limThr", limThr.toDouble())
        put("bass", bassPct.toDouble()); put("virt", virtPct.toDouble())
    }

    private fun restore(j: JSONObject) {
        j.optJSONArray("eq")?.let { a -> for (i in 0 until minOf(10, a.length())) bands[i] = a.optDouble(i, 0.0).toFloat() }
        eqOn = j.optBoolean("eqOn", true)
        preamp = j.f("pre", 0f); loudness = j.f("loud", 0f); balance = j.f("bal", 0f)
        compOn = j.optBoolean("compOn", false); thr = j.f("thr", -20f); ratio = j.f("ratio", 3f)
        attack = j.f("att", 10f); release = j.f("rel", 120f); makeup = j.f("mk", 0f)
        limOn = j.optBoolean("limOn", true); limThr = j.f("limThr", -1f)
        bassPct = j.f("bass", 0f); virtPct = j.f("virt", 0f)
    }

    private fun save() { prefs?.edit()?.putString("current", snapshot().toString())?.apply() }

    private fun customJson(): JSONObject =
        try { JSONObject(prefs?.getString("custom", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun refreshCustom() {
        customNames.clear()
        val keys = customJson().keys()
        while (keys.hasNext()) customNames.add(keys.next())
    }

    fun saveCustom(name: String) {
        if (name.isBlank()) return
        val j = customJson(); j.put(name.trim(), snapshot())
        prefs?.edit()?.putString("custom", j.toString())?.apply()
        refreshCustom()
    }

    fun loadCustom(name: String) { customJson().optJSONObject(name)?.let { restore(it); apply() } }

    fun deleteCustom(name: String) {
        val j = customJson(); j.remove(name)
        prefs?.edit()?.putString("custom", j.toString())?.apply()
        refreshCustom()
    }

    fun applyPreset(name: String) {
        val v = PRESETS[name] ?: return
        for (i in 0 until 10) bands[i] = v[i]
        apply()
    }

    // ---------- Efeitos ----------
    /** Chamado pelo player quando o id da sessão de áudio muda. */
    fun attach(session: Int) {
        if (session == 0) return
        release()
        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                true, 10,   // EQ de 10 bandas
                true, 3,    // compressor multibanda de 3 bandas
                false, 0,
                true        // limitador
            ).build()
            dp = DynamicsProcessing(0, session, cfg)
        } catch (e: Exception) { }
        try { boost = LoudnessEnhancer(session) } catch (e: Exception) { }
        try { bass = BassBoost(0, session) } catch (e: Exception) { }
        try { virt = Virtualizer(0, session) } catch (e: Exception) { }
        ready = true
        apply()
    }

    /** Aplica todos os valores atuais nos efeitos (e salva). */
    fun apply() {
        save()
        try {
            dp?.let { d ->
                d.setEnabled(enabled)
                val left = preamp + if (balance > 0f) -24f * balance else 0f
                val right = preamp + if (balance < 0f) 24f * balance else 0f
                d.setInputGainbyChannel(0, left)
                d.setInputGainbyChannel(1, right)
                for (i in 0 until 10) {
                    d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(eqOn, EQ_FREQS[i].toFloat(), bands[i]))
                }
                val cuts = floatArrayOf(250f, 3000f, 20000f)
                for (i in 0 until 3) {
                    d.setMbcBandAllChannelsTo(
                        i, DynamicsProcessing.MbcBand(compOn, cuts[i], attack, release, ratio, thr, 6f, -90f, 1f, 0f, makeup)
                    )
                }
                val lim = DynamicsProcessing.Limiter(true, limOn, 0, 1f, 60f, 10f, limThr, 0f)
                for (ch in 0 until 2) d.setLimiterByChannelIndex(ch, lim)
            }
        } catch (e: Exception) { }
        try { boost?.let { it.setEnabled(enabled && loudness > 0f); it.setTargetGain((loudness * 100).toInt()) } } catch (e: Exception) { }
        try { bass?.let { it.setEnabled(enabled && bassPct > 0f); it.setStrength((bassPct * 10).toInt().toShort()) } } catch (e: Exception) { }
        try { virt?.let { it.setEnabled(enabled && virtPct > 0f); it.setStrength((virtPct * 10).toInt().toShort()) } } catch (e: Exception) { }
    }

    /** Importa texto no formato Equalizer APO / AutoEQ. Retorna quantos filtros foram lidos. */
    fun importApo(text: String): Int {
        val re = Regex("""Filter\s*\d*:\s*ON\s+\w+\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
        val acc = FloatArray(10); var n = 0
        re.findAll(text).forEach { m ->
            val fc = m.groupValues[1].toFloatOrNull() ?: return@forEach
            val g = m.groupValues[2].toFloatOrNull() ?: return@forEach
            val idx = EQ_FREQS.indices.minByOrNull { abs(ln(EQ_FREQS[it].toFloat() / fc)) } ?: return@forEach
            acc[idx] += g; n++
        }
        Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE).find(text)?.let {
            preamp = (it.groupValues[1].toFloatOrNull() ?: 0f).coerceIn(-12f, 12f)
        }
        if (n > 0) for (i in 0 until 10) bands[i] = acc[i].coerceIn(-12f, 12f)
        apply()
        return n
    }

    fun release() {
        try { dp?.release() } catch (e: Exception) { }
        try { boost?.release() } catch (e: Exception) { }
        try { bass?.release() } catch (e: Exception) { }
        try { virt?.release() } catch (e: Exception) { }
        dp = null; boost = null; bass = null; virt = null
        ready = false
    }
}
