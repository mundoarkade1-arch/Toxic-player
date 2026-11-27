package com.meuapp.player.cloud

/**
 * Busca de playlists (lógica PURA, testável na JVM).
 * Uma playlist aparece na busca quando:
 *  - o NOME dela (a pasta do Drive) tem o texto buscado: sem diferenciar acento, maiúscula nem pontuação
 *    (\"Charlie Brown Jr.\" acha a pasta \"Charlie Brown Jr\"); ou
 *  - boa parte das músicas dela é do artista/álbum buscado (pasta chamada \"CBJ\" cheia de Charlie Brown Jr).
 */
object PlaylistSearch {
    const val NO_MATCH = 0
    const val BY_CONTENT = 1
    const val BY_NAME = 2

    /** Parte mínima das músicas da playlist que precisa ser do artista/álbum buscado (40%). */
    private const val CONTENT_PERCENT = 40

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    /** Texto comparável: sem acento, minúsculo, pontuação vira espaço único. */
    fun key(s: String): String = CloudLibrary.normalize(s).replace(NON_ALNUM, " ").trim()

    /**
     * [names]: nomes (artista ou álbum) de cada música da playlist, um por música; só é chamado se o nome não bateu.
     * Devolve [BY_NAME], [BY_CONTENT] ou [NO_MATCH].
     */
    fun score(query: String, playlistName: String, names: () -> Collection<String>, total: Int): Int {
        val q = key(query)
        if (q.isEmpty()) return NO_MATCH
        if (key(playlistName).contains(q)) return BY_NAME
        if (total <= 0) return NO_MATCH
        val matched = names().count { key(it).contains(q) }
        return if (matched > 0 && matched * 100 >= total * CONTENT_PERCENT) BY_CONTENT else NO_MATCH
    }
}
