package com.meuapp.player.youtube

import org.json.JSONException
import org.json.JSONObject

/** Vídeo e/ou playlist reconhecidos em um link ou código colado. */
data class YtTarget(val videoId: String?, val playlistId: String?)

/** Um resultado de vídeo ou playlist da busca pública. */
data class YtResult(val target: YtTarget, val title: String, val channel: String, val thumb: String)

/** Link salvo pelo usuário. */
data class YtSaved(val title: String, val input: String)

object YouTubeLinks {
    private val VIDEO = Regex("""(?:v=|youtu\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})""")
    private val LIST = Regex("""[?&]list=([A-Za-z0-9_-]{10,})""")
    private val BARE_VIDEO = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val BARE_LIST = Regex("""^(?:PL|UU|OL|FL|RD)[A-Za-z0-9_-]{10,}$""")

    /** Lê links de vídeo, playlists, youtu.be, shorts ou códigos soltos. Devolve null se não reconhecer. */
    fun parse(text: String): YtTarget? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val v = VIDEO.find(t)?.groupValues?.get(1) ?: if (BARE_VIDEO.matches(t)) t else null
        val l = LIST.find(t)?.groupValues?.get(1) ?: if (BARE_LIST.matches(t)) t else null
        return if (v == null && l == null) null else YtTarget(v, l)
    }

    /** Link HTTPS normal; o Android encaminha para o app oficial do YouTube quando instalado. */
    fun externalUrl(t: YtTarget): String? = when {
        t.videoId != null -> "https://www.youtube.com/watch?v=${t.videoId}" + (t.playlistId?.let { "&list=$it" } ?: "")
        t.playlistId != null -> "https://www.youtube.com/playlist?list=${t.playlistId}"
        else -> null
    }

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    /** Lê resultados de vídeo e playlist da YouTube Data API v3. */
    fun parseSearch(json: String): List<YtResult> {
        try {
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            val out = ArrayList<YtResult>()
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val id = o.optJSONObject("id") ?: continue
                val videoId = id.optString("videoId", "").takeIf { it.isNotBlank() }
                val playlistId = id.optString("playlistId", "").takeIf { it.isNotBlank() }
                if (videoId == null && playlistId == null) continue
                val sn = o.optJSONObject("snippet")
                val thumb = sn?.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url", "") ?: ""
                out.add(YtResult(YtTarget(videoId, playlistId), decode(sn?.optString("title", "") ?: ""), decode(sn?.optString("channelTitle", "") ?: ""), thumb))
            }
            return out
        } catch (e: JSONException) {
            return emptyList()
        }
    }
}

