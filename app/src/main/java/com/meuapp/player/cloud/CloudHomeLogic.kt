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

    /**
     * Gênero pela pasta do Drive, para músicas sem gênero nas tags.
     * "Músicas/Artistas/Sertanejo/Gusttavo Lima" vira "Sertanejo": é a pasta logo depois de "Artistas",
     * mas só quando ainda existe a pasta do cantor dentro dela. Música direto em "Artistas/Cantor" não tem gênero.
     */
    fun genreFromPath(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val parts = path.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val i = parts.indexOfFirst { CloudLibrary.normalize(it) == "artistas" }
        if (i < 0 || parts.size - i < 3) return ""
        return parts[i + 1]
    }

    private val FILLER_WORDS = setOf("e", "de", "da", "do", "das", "dos", "a", "o", "x", "the", "and")

    /** Letras da capa quando a playlist não tem imagem: iniciais das duas primeiras palavras ("Sertanejo Churrasco" vira "SC"). */
    fun initials(name: String): String {
        val all = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val words = all.filter { it.lowercase() !in FILLER_WORDS }.ifEmpty { all }
        return when {
            words.isEmpty() -> "\u266A"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    // Tipos de playlist, já na ordem em que aparecem nas telas.
    const val KIND_FAV = 0
    const val KIND_MINE = 1
    const val KIND_FOLDER = 2
    const val KIND_MOTOR = 3
    const val KIND_ARTIST = 4

    /** Tipo da playlist pela origem que o site/motor gravou no arquivo ("Pasta", "Cantor", "Motor"). Sem origem: criada no app. */
    fun playlistKind(isFavorites: Boolean, origin: String?): Int {
        if (isFavorites) return KIND_FAV
        return when (origin?.trim()?.lowercase()) {
            "pasta" -> KIND_FOLDER
            "cantor" -> KIND_ARTIST
            "motor" -> KIND_MOTOR
            else -> KIND_MINE
        }
    }
}
