package com.meuapp.player.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.pow

/**
 * Estado e persistência de todos os ajustes de áudio.
 * Processamento próprio (EQ, compressor, limitador, estéreo, medidores): [DspCore].
 * Efeitos do sistema (dependem do aparelho): Loudness, Bass Boost, Surround e compressor multibanda.
 */
object AudioFx {
    val EQ_FREQS = DspCore.GRAPHIC_FREQS.toList()

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

    private const val TOXIC_BASS = """{
  "toxic": "preset",
  "eq": [
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0
  ],
  "filters": [
    {
      "t": "HIGH_PASS",
      "f": 22,
      "g": 0,
      "q": 0.707,
      "on": true
    },
    {
      "t": "LOW_SHELF",
      "f": 90,
      "g": 4.5,
      "q": 0.65,
      "on": true
    },
    {
      "t": "PEAK",
      "f": 48,
      "g": 2.0,
      "q": 1.1,
      "on": true
    },
    {
      "t": "PEAK",
      "f": 250,
      "g": -1.8,
      "q": 1.2,
      "on": true
    },
    {
      "t": "PEAK",
      "f": 3500,
      "g": 1.2,
      "q": 0.9,
      "on": true
    },
    {
      "t": "HIGH_SHELF",
      "f": 9000,
      "g": 1.5,
      "q": 0.7,
      "on": true
    }
  ],
  "eqOn": true,
  "paramOn": true,
  "pre": -3.0,
  "auto": false,
  "bal": 0.0,
  "width": 1.1,
  "cross": 0.0,
  "loud": 0.0,
  "compOn": true,
  "thr": -22.0,
  "ratio": 2.2,
  "att": 25.0,
  "rel": 220.0,
  "knee": 10.0,
  "mk": 1.5,
  "rms": true,
  "limOn": true,
  "limThr": -1.0,
  "limRel": 80.0,
  "mbcOn": false,
  "mbcThr": -24.0,
  "mbcRatio": 3.0,
  "bass": 0.0,
  "virt": 0.0
}"""

    private var boost: LoudnessEnhancer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var prefs: SharedPreferences? = null

    var ready by mutableStateOf(false)
    val bands = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    val filters = mutableStateListOf<EqFilter>()
    var enabled by mutableStateOf(true)
    var eqOn by mutableStateOf(true)
    var paramOn by mutableStateOf(true)
    var preamp by mutableFloatStateOf(0f)
    var autoHeadroom by mutableStateOf(false)
    var balance by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var crossfeed by mutableFloatStateOf(0f)
    var loudness by mutableFloatStateOf(0f)
    var compOn by mutableStateOf(false)
    var thr by mutableFloatStateOf(-20f)
    var ratio by mutableFloatStateOf(3f)
    var attack by mutableFloatStateOf(10f)
    var release by mutableFloatStateOf(120f)
    var knee by mutableFloatStateOf(6f)
    var makeup by mutableFloatStateOf(0f)
    var compRms by mutableStateOf(true)
    var limOn by mutableStateOf(true)
    var limThr by mutableFloatStateOf(-1f)
    var limRelease by mutableFloatStateOf(60f)
    var mbcOn by mutableStateOf(false)
    var mbThrL by mutableFloatStateOf(-24f)
    var mbThrM by mutableFloatStateOf(-24f)
    var mbThrH by mutableFloatStateOf(-24f)
    var mbAttack by mutableFloatStateOf(15f)
    var mbRelease by mutableFloatStateOf(150f)
    var mbKnee by mutableFloatStateOf(6f)
    var mbMakeup by mutableFloatStateOf(0f)
    var mbX1 by mutableFloatStateOf(250f)
    var mbX2 by mutableFloatStateOf(3000f)
    var compScHpf by mutableFloatStateOf(0f)
    var fadeSec by mutableFloatStateOf(0f)
    var mbcRatio by mutableFloatStateOf(3f)
    var truePeak by mutableStateOf(true)
    var levelerOn by mutableStateOf(false)
    var levelerTarget by mutableFloatStateOf(-16f)
    var ditherOn by mutableStateOf(true)
    var activeName by mutableStateOf("Personalizado")
    var currentRoute by mutableStateOf("speaker")
    val routeProfiles = mutableStateMapOf<String, String>()
    var bassPct by mutableFloatStateOf(0f)
    var virtPct by mutableFloatStateOf(0f)
    val customNames = mutableStateListOf<String>()
    private var abA: JSONObject? = null
    private var abB: JSONObject? = null

    // ---------- Persistência ----------
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("toxic_fx", Context.MODE_PRIVATE)
        prefs = p
        p.getString("current", null)?.let { s -> try { restore(JSONObject(s)) } catch (e: Exception) { } }
        if (!p.contains("custom")) {
            // Primeira execução: já deixa o perfil de graves suaves disponível em "Meus perfis"
            try { writeCustom(JSONObject().put("Toxic · Graves Suaves", JSONObject(TOXIC_BASS))) } catch (e: Exception) { }
        }
        fadeSec = p.getFloat("fade", 0f)
        refreshCustom()
        try {
            val rj = JSONObject(p.getString("routes", "{}") ?: "{}")
            val ks = rj.keys()
            while (ks.hasNext()) { val k = ks.next(); routeProfiles[k] = rj.optString(k, "") }
        } catch (e: Exception) { }
        DspEngine.core.params = buildParams()
    }

    private fun JSONObject.f(k: String, d: Float) = optDouble(k, d.toDouble()).toFloat()

    private fun snapshot(): JSONObject = JSONObject().apply {
        put("eq", JSONArray(bands.map { it.toDouble() }))
        val fa = JSONArray()
        filters.forEach { f ->
            fa.put(JSONObject().put("t", f.type.name).put("f", f.freq.toDouble()).put("g", f.gainDb.toDouble()).put("q", f.q.toDouble()).put("on", f.enabled))
        }
        put("filters", fa)
        put("eqOn", eqOn); put("paramOn", paramOn)
        put("pre", preamp.toDouble()); put("auto", autoHeadroom); put("bal", balance.toDouble())
        put("width", width.toDouble()); put("cross", crossfeed.toDouble()); put("loud", loudness.toDouble())
        put("compOn", compOn); put("thr", thr.toDouble()); put("ratio", ratio.toDouble())
        put("att", attack.toDouble()); put("rel", release.toDouble()); put("knee", knee.toDouble())
        put("mk", makeup.toDouble()); put("rms", compRms)
        put("limOn", limOn); put("limThr", limThr.toDouble()); put("limRel", limRelease.toDouble())
        put("mbcOn", mbcOn); put("mbcRatio", mbcRatio.toDouble())
        put("mbL", mbThrL.toDouble()); put("mbM", mbThrM.toDouble()); put("mbH", mbThrH.toDouble())
        put("mbAtt", mbAttack.toDouble()); put("mbRel", mbRelease.toDouble()); put("mbKnee", mbKnee.toDouble())
        put("mbMk", mbMakeup.toDouble()); put("mbX1", mbX1.toDouble()); put("mbX2", mbX2.toDouble()); put("scHpf", compScHpf.toDouble())
        put("bass", bassPct.toDouble()); put("virt", virtPct.toDouble())
        put("tp", truePeak); put("lev", levelerOn); put("levT", levelerTarget.toDouble()); put("dith", ditherOn)
    }

    private fun restore(j: JSONObject) {
        j.optJSONArray("eq")?.let { a -> for (i in 0 until minOf(10, a.length())) bands[i] = a.optDouble(i, 0.0).toFloat() }
        filters.clear()
        j.optJSONArray("filters")?.let { a ->
            for (i in 0 until minOf(DspCore.MAX_PARAM, a.length())) {
                val o = a.optJSONObject(i) ?: continue
                val t = try { FilterType.valueOf(o.optString("t", "PEAK")) } catch (e: Exception) { FilterType.PEAK }
                filters.add(sanitize(EqFilter(t, o.f("f", 1000f), o.f("g", 0f), o.f("q", 1f), o.optBoolean("on", true))))
            }
        }
        eqOn = j.optBoolean("eqOn", true); paramOn = j.optBoolean("paramOn", true)
        preamp = j.f("pre", 0f); autoHeadroom = j.optBoolean("auto", false); balance = j.f("bal", 0f)
        width = j.f("width", 1f); crossfeed = j.f("cross", 0f); loudness = j.f("loud", 0f)
        compOn = j.optBoolean("compOn", false); thr = j.f("thr", -20f); ratio = j.f("ratio", 3f)
        attack = j.f("att", 10f); release = j.f("rel", 120f); knee = j.f("knee", 6f)
        makeup = j.f("mk", 0f); compRms = j.optBoolean("rms", true)
        limOn = j.optBoolean("limOn", true); limThr = j.f("limThr", -1f); limRelease = j.f("limRel", 60f)
        mbcOn = j.optBoolean("mbcOn", false); mbcRatio = j.f("mbcRatio", 3f)
        val oldThr = j.f("mbcThr", -24f)   // perfis antigos tinham um só threshold
        mbThrL = j.f("mbL", oldThr); mbThrM = j.f("mbM", oldThr); mbThrH = j.f("mbH", oldThr)
        mbAttack = j.f("mbAtt", 15f); mbRelease = j.f("mbRel", 150f); mbKnee = j.f("mbKnee", 6f)
        mbMakeup = j.f("mbMk", 0f); mbX1 = j.f("mbX1", 250f); mbX2 = j.f("mbX2", 3000f); compScHpf = j.f("scHpf", 0f)
        bassPct = j.f("bass", 0f); virtPct = j.f("virt", 0f)
        truePeak = j.optBoolean("tp", true); levelerOn = j.optBoolean("lev", false)
        levelerTarget = j.f("levT", -16f); ditherOn = j.optBoolean("dith", true)
    }

    private fun sanitize(f: EqFilter) = f.copy(
        freq = f.freq.coerceIn(20f, 20000f), gainDb = f.gainDb.coerceIn(-18f, 18f), q = f.q.coerceIn(0.1f, 18f)
    )

    private fun save() { prefs?.edit()?.putString("current", snapshot().toString())?.apply() }

    private fun customJson(): JSONObject =
        try { JSONObject(prefs?.getString("custom", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun writeCustom(j: JSONObject) { prefs?.edit()?.putString("custom", j.toString())?.apply(); refreshCustom() }

    private fun refreshCustom() {
        customNames.clear()
        val keys = customJson().keys()
        while (keys.hasNext()) customNames.add(keys.next())
    }

    // ---------- Presets, perfis, backup ----------
    fun saveCustom(name: String) { if (name.isBlank()) return; val j = customJson(); j.put(name.trim(), snapshot()); writeCustom(j) }
    fun loadCustom(name: String) { customJson().optJSONObject(name)?.let { restore(it); apply(); activeName = name } }
    fun deleteCustom(name: String) { val j = customJson(); j.remove(name); writeCustom(j) }
    fun duplicateCustom(name: String) {
        val j = customJson(); val o = j.optJSONObject(name) ?: return
        j.put("$name (cópia)", JSONObject(o.toString())); writeCustom(j)
    }
    fun renameCustom(old: String, new: String) {
        if (new.isBlank() || old == new.trim()) return
        val j = customJson(); val o = j.optJSONObject(old) ?: return
        j.remove(old); j.put(new.trim(), o); writeCustom(j)
    }
    /** Liga/desliga todos os efeitos sem perder o nome do preset ativo. */
    fun setFade(v: Float) { fadeSec = v; prefs?.edit()?.putFloat("fade", v)?.apply() }

    fun setEnabled(v: Boolean) { val n = activeName; enabled = v; apply(); activeName = n }

    fun applyPreset(name: String) {
        val v = PRESETS[name] ?: return
        for (i in 0 until 10) bands[i] = v[i]
        apply()
        activeName = name
    }
    fun resetAll() {
        restore(JSONObject()); apply()
    }
    fun exportCurrent(): String = snapshot().put("toxic", "preset").toString(2)
    fun exportAll(): String = JSONObject().put("toxic", "backup").put("custom", customJson()).put("current", snapshot()).toString(2)

    /** Importa preset ou backup em JSON. Retorna uma mensagem para o usuário. */
    fun importJson(text: String): String = try {
        val j = JSONObject(text)
        val c = j.optJSONObject("custom")
        if (c != null) {
            val mine = customJson(); val keys = c.keys(); var n = 0
            while (keys.hasNext()) { val k = keys.next(); mine.put(k, c.get(k)); n++ }
            writeCustom(mine)
            j.optJSONObject("current")?.let { restore(it); apply() }
            "Backup restaurado: $n presets"
        } else { restore(j); apply(); activeName = "Importado"; "Preset carregado" }
    } catch (e: Exception) { "Arquivo JSON inválido" }

    fun storeAB(slot: Int) { if (slot == 0) abA = snapshot() else abB = snapshot() }
    fun recallAB(slot: Int): Boolean {
        val s = (if (slot == 0) abA else abB) ?: return false
        restore(s); apply(); return true
    }

    // ---------- Perfil automático por saída (alto-falante, fones com fio, Bluetooth) ----------
    fun setRouteProfile(route: String, name: String) {
        routeProfiles[route] = name
        val j = JSONObject(); routeProfiles.forEach { (k, v) -> j.put(k, v) }
        prefs?.edit()?.putString("routes", j.toString())?.apply()
        if (route == currentRoute && name.isNotBlank()) loadCustom(name)
    }

    /** Chamado quando a saída de áudio muda. Só troca de perfil se a saída realmente mudou. */
    fun routeChanged(route: String) {
        if (route == currentRoute) return
        currentRoute = route
        val n = routeProfiles[route]
        if (!n.isNullOrBlank() && customNames.contains(n)) loadCustom(n)
    }

    // ---------- Filtros paramétricos ----------
    fun addFilter() { if (filters.size < DspCore.MAX_PARAM) { filters.add(EqFilter()); apply() } }
    fun removeFilter(i: Int) { if (i in filters.indices) { filters.removeAt(i); apply() } }
    fun updateFilter(i: Int, f: EqFilter) { if (i in filters.indices) { filters[i] = sanitize(f); apply() } }

    /** Resposta combinada do EQ (gráfico + paramétrico) em 20 Hz..20 kHz, [points] pontos, em dB. */
    fun responseCurve(points: Int): FloatArray {
        val sr = 48000.0
        val list = ArrayList<BiquadCoef>()
        if (eqOn) for (i in 0 until 10) if (abs(bands[i]) > 0.01f) {
            list.add(BiquadCoef.design(EqFilter(FilterType.PEAK, EQ_FREQS[i].toFloat(), bands[i], DspCore.GRAPHIC_Q), sr))
        }
        if (paramOn) filters.forEach { f ->
            if (f.enabled && (!f.type.usesGain || abs(f.gainDb) > 0.01f)) list.add(BiquadCoef.design(f, sr))
        }
        return FloatArray(points) { i ->
            val f = 20.0 * 1000.0.pow(i / (points - 1.0))
            var db = 0.0
            for (c in list) db += c.responseDb(f, sr)
            db.toFloat()
        }
    }

    // ---------- Aplicação ----------
    private fun buildParams() = DspParams(
        enabled = enabled, preampDb = preamp, autoHeadroom = autoHeadroom, balance = balance,
        graphicOn = eqOn, graphic = bands.toList(), paramOn = paramOn, filters = filters.toList(),
        compOn = compOn, compThr = thr, compRatio = ratio, compAttackMs = attack, compReleaseMs = release,
        compKneeDb = knee, compMakeupDb = makeup, compRms = compRms,
        limOn = limOn, limCeilingDb = limThr, limReleaseMs = limRelease,
        width = width, crossfeed = crossfeed,
        truePeak = truePeak, levelerOn = levelerOn, levelerTargetDb = levelerTarget, ditherOn = ditherOn,
        compScHpf = compScHpf, mbOn = mbcOn, mbThrL = mbThrL, mbThrM = mbThrM, mbThrH = mbThrH,
        mbRatio = mbcRatio, mbAttackMs = mbAttack, mbReleaseMs = mbRelease, mbKneeDb = mbKnee, mbMakeupDb = mbMakeup,
        mbX1 = mbX1, mbX2 = mbX2
    )

    /** Chamado pelo player quando o id da sessão de áudio muda (efeitos do sistema). */
    fun attach(session: Int) {
        if (session == 0) return
        release()
        try { boost = LoudnessEnhancer(session) } catch (e: Exception) { }
        try { bass = BassBoost(0, session) } catch (e: Exception) { }
        try { virt = Virtualizer(0, session) } catch (e: Exception) { }
        ready = true
        apply()
    }

    fun apply() {
        activeName = "Personalizado"
        save()
        DspEngine.core.params = buildParams()
        try { boost?.let { it.setEnabled(enabled && loudness > 0f); it.setTargetGain((loudness * 100).toInt()) } } catch (e: Exception) { }
        try { bass?.let { it.setEnabled(enabled && bassPct > 0f); it.setStrength((bassPct * 10).toInt().toShort()) } } catch (e: Exception) { }
        try { virt?.let { it.setEnabled(enabled && virtPct > 0f); it.setStrength((virtPct * 10).toInt().toShort()) } } catch (e: Exception) { }
    }

    var importNote by mutableStateOf("")

    /**
     * Importa Equalizer APO / AutoEQ como filtros PARAMÉTRICOS (frequência, ganho e Q originais).
     * Tipos: PK/PEQ -> Peak, LS/LSC -> Low Shelf, HS/HSC -> High Shelf, LP/HP -> passa-baixas/altas, NO -> Notch.
     * Aceita até 16 filtros; o restante é descartado e informado em [importNote].
     */
    fun importApo(text: String): Int {
        val re = Regex("""Filter\s*\d*:\s*ON\s+(\w+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""", RegexOption.IGNORE_CASE)
        val list = ArrayList<EqFilter>(); var skipped = 0
        re.findAll(text).forEach { m ->
            val type = when (m.groupValues[1].uppercase()) {
                "PK", "PEQ", "MODAL" -> FilterType.PEAK
                "LS", "LSC" -> FilterType.LOW_SHELF
                "HS", "HSC" -> FilterType.HIGH_SHELF
                "LP", "LPQ" -> FilterType.LOW_PASS
                "HP", "HPQ" -> FilterType.HIGH_PASS
                "NO", "NOTCH" -> FilterType.NOTCH
                else -> null
            }
            val fc = m.groupValues[2].toFloatOrNull()
            if (type == null || fc == null) { skipped++; return@forEach }
            val g = m.groupValues[3].toFloatOrNull() ?: 0f
            val q = m.groupValues[4].toFloatOrNull() ?: if (type == FilterType.PEAK) 1f else 0.707f
            list.add(EqFilter(type, fc, g, q, true))
        }
        val kept = list.take(DspCore.MAX_PARAM)
        skipped += list.size - kept.size
        Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE).find(text)?.let {
            preamp = (it.groupValues[1].toFloatOrNull() ?: 0f).coerceIn(-12f, 12f)
        }
        if (kept.isNotEmpty()) {
            filters.clear(); kept.forEach { filters.add(sanitize(it)) }
            for (i in 0 until 10) bands[i] = 0f
            paramOn = true
        }
        importNote = if (skipped > 0) "$skipped filtros não puderam ser representados (tipo desconhecido ou acima de 16)." else ""
        apply()
        return kept.size
    }

    fun release() {
        try { boost?.release() } catch (e: Exception) { }
        try { bass?.release() } catch (e: Exception) { }
        try { virt?.release() } catch (e: Exception) { }
        boost = null; bass = null; virt = null
        ready = false
    }
}
