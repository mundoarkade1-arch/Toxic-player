package com.meuapp.player.cloud

/** Lógica pura da tela inicial (mixes, artistas e gêneros em destaque). Tudo vem da biblioteca do usuário. */
object CloudHomeLogic {
    data class Entry(val name: String, val count: Int)

    fun greeting(hour: Int) = when (hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }

    /** Artistas com mais músicas (ignora "Artista desconhecido"). */
    fun topArtists(list: List<CloudTrack>, n: Int): List<Entry> =
        list.filter { it.artist.isNotBlank() && it.artist != CloudTrack.UNKNOWN_ARTIST }
            .groupBy { it.artist }.map { (k, l) -> Entry(k, l.size) }
            .sortedWith(compareByDescending<Entry> { it.count }.thenBy { CloudLibrary.normalize(it.name) }).take(n)

    /** Gêneros com mais músicas (ignora faixas sem gênero; junta maiúsculas/minúsculas). */
    fun topGenres(list: List<CloudTrack>, n: Int): List<Entry> =
        list.filter { it.genre.isNotBlank() }.groupBy { CloudLibrary.normalize(it.genre) }.map { (_, l) ->
            val names = l.map { it.genre }.distinct()
            Entry(names.firstOrNull { it != it.lowercase() && it != it.uppercase() } ?: names.first(), l.size)
        }.sortedWith(compareByDescending<Entry> { it.count }.thenBy { CloudLibrary.normalize(it.name) }).take(n)

    fun byArtist(list: List<CloudTrack>, name: String) = list.filter { it.artist == name }
    fun byGenre(list: List<CloudTrack>, name: String) =
        list.filter { CloudLibrary.normalize(it.genre) == CloudLibrary.normalize(name) }

    fun recent(list: List<CloudTrack>, n: Int) = CloudLibrary.sort(list, CloudLibrary.SORT_RECENT).take(n)

    /** Mistura estável para a mesma [seed] (muda a cada dia). */
    fun shuffled(list: List<CloudTrack>, seed: Long, n: Int): List<CloudTrack> =
        list.shuffled(java.util.Random(seed)).take(n)

    /** Matiz (0..360) estável para um nome, para colorir cartões. */
    fun hueOf(name: String): Float = ((name.hashCode().toLong() and 0x7FFFFFFF) % 360).toFloat()
}
