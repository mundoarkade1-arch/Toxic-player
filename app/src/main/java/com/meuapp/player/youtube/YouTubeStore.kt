package com.meuapp.player.youtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** O que está tocando no player embutido do YouTube. Quando [url] é nulo, o YouTube está parado. */
object YouTubeState {
    var url by mutableStateOf<String?>(null); private set
    var referer by mutableStateOf("https://com.meuapp.player"); private set
    var title by mutableStateOf(""); private set

    fun play(target: YtTarget, title: String, referer: String) {
        this.referer = referer
        this.title = title
        url = YouTubeLinks.embedUrl(target, referer)
    }

    fun stop() { url = null; title = "" }
}

/** Links salvos e chave da API (ficam só neste aparelho). */
object YouTubeStore {
    private var prefs: SharedPreferences? = null
    val links = mutableStateListOf<YtSaved>()
    var apiKey by mutableStateOf(""); private set

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("youtube", Context.MODE_PRIVATE)
        prefs = p
        apiKey = p.getString("key", "") ?: ""
        try {
            val a = JSONArray(p.getString("links", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                links.add(YtSaved(o.optString("t"), o.optString("i")))
            }
        } catch (e: Exception) { }
    }

    private fun persist() {
        val a = JSONArray()
        links.forEach { a.put(JSONObject().put("t", it.title).put("i", it.input)) }
        prefs?.edit()?.putString("links", a.toString())?.apply()
    }

    fun setKey(k: String) { apiKey = k.trim(); prefs?.edit()?.putString("key", apiKey)?.apply() }
    fun addLink(title: String, input: String) { links.add(YtSaved(title.ifBlank { input }, input)); persist() }
    fun removeLink(s: YtSaved) { links.remove(s); persist() }
}
