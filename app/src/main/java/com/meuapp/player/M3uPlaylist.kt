package com.meuapp.player

/** Leitor simples de playlists M3U/M3U8; ignora metadados e comentários. */
object M3uPlaylist {
    fun parse(text: String): List<String> = text.removePrefix("\uFEFF")
        .lineSequence()
        .map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

    fun match(entries: List<String>, tracks: List<Track>): List<Track> {
        val byPath = tracks.filter { it.filePath.isNotBlank() }
            .associateBy { normalize(it.filePath) }
        val byName = tracks.filter { it.filePath.isNotBlank() }
            .groupBy { normalize(it.filePath.substringAfterLast('/')) }
        val result = LinkedHashMap<Long, Track>()
        entries.forEach { entry ->
            val clean = normalize(entry.removePrefix("file://"))
            val exact = byPath[clean]
            val matches = exact?.let(::listOf) ?: byName[clean.substringAfterLast('/')].orEmpty().takeIf { it.size == 1 }.orEmpty()
            matches.forEach { result[it.id] = it }
        }
        return result.values.toList()
    }

    private fun normalize(value: String): String = value.replace('\\', '/').trim().trimStart('/').lowercase()
}

