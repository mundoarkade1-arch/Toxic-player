package com.meuapp.player.cloud

import java.text.Normalizer

/** Busca, filtros, ordenação e agrupamento da biblioteca da nuvem (lógica pura, testável). */
object CloudLibrary {
    const val SORT_TITLE = 0
    const val SORT_ARTIST = 1
    const val SORT_ALBUM = 2
    const val SORT_DURATION = 3
    const val SORT_SIZE = 4
    const val SORT_RECENT = 5
    const val NO_GENRE = "Sem gênero"

    private fun genreOf(t: CloudTrack) = if (t.genre.isBlank()) NO_GENRE else t.genre

    data class Group(val key: String, val name: String, val count: Int)

    /** Minúsculas e sem acentos: "Açaí" encontra "acai". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /**
     * [group] é "A:artista" ou "B:álbum" (ou null). [offlineIds] só é usado com [offlineOnly].
     */
    fun filter(
        list: List<CloudTrack>, query: String = "", format: CloudFormat? = null,
        offlineOnly: Boolean = false, offlineIds: Set<String> = emptySet(), group: String? = null,
        paths: Map<String, String> = emptyMap()
    ): List<CloudTrack> {
        fun pathOf(t: CloudTrack) = paths[t.parentId] ?: if (t.parentId.isBlank()) CloudFolders.OTHER else CloudFolders.ROOT
        val q = normalize(query.trim())
        return list.filter { t ->
            (format == null || t.format == format) &&
                (!offlineOnly || t.id in offlineIds) &&
                when {
                    group == null -> true
                    group.startsWith("A:") -> t.artist == group.drop(2)
                    group.startsWith("B:") -> t.album == group.drop(2)
                    group.startsWith("F:") -> pathOf(t).let { p -> val g = group.drop(2); p == g || p.startsWith("$g/") }
                    group.startsWith("G:") -> normalize(genreOf(t)) == normalize(group.drop(2))
                    else -> true
                } &&
                (q.isEmpty() || normalize(t.title).contains(q) || normalize(t.artist).contains(q) ||
                    normalize(t.album).contains(q) || normalize(t.fileName).contains(q) ||
                    (paths.isNotEmpty() && normalize(pathOf(t)).contains(q)))
        }
    }

    fun sort(list: List<CloudTrack>, mode: Int): List<CloudTrack> = when (mode) {
        SORT_ARTIST -> list.sortedWith(compareBy({ normalize(it.artist) }, { normalize(it.album) }, { normalize(it.title) }))
        SORT_ALBUM -> list.sortedWith(compareBy({ normalize(it.album) }, { normalize(it.title) }))
        SORT_DURATION -> list.sortedByDescending { it.durationMs }
        SORT_SIZE -> list.sortedByDescending { it.size }
        SORT_RECENT -> list.sortedWith(compareByDescending<CloudTrack> { it.addedMs }.thenBy { normalize(it.title) })
        else -> list.sortedBy { normalize(it.title) }
    }

    fun artists(list: List<CloudTrack>): List<Group> =
        list.groupBy { it.artist }.map { (k, l) -> Group("A:$k", k, l.size) }.sortedBy { normalize(it.name) }

    fun albums(list: List<CloudTrack>): List<Group> =
        list.groupBy { it.album }.map { (k, l) -> Group("B:$k", k, l.size) }.sortedBy { normalize(it.name) }

    /** Gêneros presentes (sem diferenciar maiúsculas/acentos); "Sem gênero" fica por último. */
    fun genres(list: List<CloudTrack>): List<Group> =
        list.groupBy { normalize(genreOf(it)) }.map { (_, l) ->
            val names = l.map { genreOf(it) }.distinct()
            val name = names.firstOrNull { it != it.lowercase() && it != it.uppercase() } ?: names.first()
            Group("G:$name", name, l.size)
        }.sortedWith(compareBy({ it.name == NO_GENRE }, { normalize(it.name) }))

    /** Pastas com músicas; cada pasta conta também as músicas das subpastas. Ordenadas por caminho. */
    fun folders(list: List<CloudTrack>, paths: Map<String, String>): List<Group> {
        val counts = HashMap<String, Int>()
        for (t in list) {
            val p = paths[t.parentId] ?: if (t.parentId.isBlank()) CloudFolders.OTHER else CloudFolders.ROOT
            var acc = ""
            for (seg in p.split('/')) { acc = if (acc.isEmpty()) seg else "$acc/$seg"; counts[acc] = (counts[acc] ?: 0) + 1 }
        }
        return counts.map { (p, n) -> Group("F:$p", p.replace("/", " / "), n) }.sortedBy { normalize(it.key) }
    }

    fun formatsPresent(list: List<CloudTrack>): List<CloudFormat> =
        list.map { it.format }.distinct().sortedBy { it.ordinal }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble(); var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(java.util.Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }
}
