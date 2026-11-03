package com.meuapp.player.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class YtSearch {
    data class Ok(val items: List<YtResult>) : YtSearch()
    data class Error(val message: String) : YtSearch()
}

enum class YtSearchType(val apiValue: String) {
    VIDEOS("video"), PLAYLISTS("playlist")
}

/** Busca conteúdo público na YouTube Data API v3 com a chave fornecida pelo usuário. */
object YouTubeApi {
    suspend fun search(key: String, query: String, type: YtSearchType): YtSearch = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val k = URLEncoder.encode(key.trim(), "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=${type.apiValue}&maxResults=15&regionCode=BR&relevanceLanguage=pt-BR&q=$q&key=$k"
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            try {
                val code = c.responseCode
                if (code == 200) {
                    YtSearch.Ok(YouTubeLinks.parseSearch(c.inputStream.bufferedReader().use { it.readText() }))
                } else {
                    val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val reason = runCatching {
                        org.json.JSONObject(body).getJSONObject("error").getJSONArray("errors")
                            .optJSONObject(0)?.optString("reason").orEmpty()
                    }.getOrDefault("")
                    YtSearch.Error(when {
                        reason == "quotaExceeded" -> "A cota diária de buscas da API acabou. Tente novamente amanhã."
                        reason == "keyInvalid" || code == 400 -> "Chave inválida. Confira a chave e a ativação da YouTube Data API v3."
                        code == 403 -> "A API recusou a busca. Confira as restrições da chave e a cota diária."
                        else -> "O YouTube respondeu com erro $code. Tente novamente."
                    })
                }
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            YtSearch.Error(if (e is IOException) "Sem conexão com a internet" else "Não foi possível concluir a busca. Verifique a conexão e a chave da API.")
        }
    }
}

