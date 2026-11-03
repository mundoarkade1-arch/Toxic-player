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

/** Busca na YouTube Data API v3 usando a chave do próprio usuário. Só retorna vídeos que podem ser embutidos. */
object YouTubeApi {
    suspend fun search(key: String, query: String): YtSearch = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val k = URLEncoder.encode(key.trim(), "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=15&q=$q&key=$k"
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            val code = c.responseCode
            if (code == 200) {
                YtSearch.Ok(YouTubeLinks.parseSearch(c.inputStream.bufferedReader().use { it.readText() }))
            } else {
                YtSearch.Error(
                    when (code) {
                        400 -> "Chave inválida ou pedido recusado pelo YouTube"
                        403 -> "Cota diária esgotada ou a YouTube Data API v3 não está ativada para esta chave"
                        else -> "O YouTube respondeu com erro $code"
                    }
                )
            }
        } catch (e: IOException) {
            YtSearch.Error("Sem conexão com a internet")
        }
    }
}
