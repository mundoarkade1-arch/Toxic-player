package com.meuapp.player.cloud

/**
 * Pastas do Drive viram playlists. Lógica PURA (testável na JVM), sem Android.
 * - Cada pasta que tem música DIRETO dentro vira uma playlist, com o nome da pasta.
 * - Música dentro de subpasta fica só na playlist da subpasta (a pasta de cima não junta tudo).
 * - Duas pastas com o mesmo nome em lugares diferentes: o nome fica com o caminho ("Música / Rock").
 * - A raiz do Drive, músicas sem pasta e pastas com menos de [MIN_TRACKS] músicas não viram playlist.
 * O resultado usa o mesmo formato das playlists do site ([SmartFile]), com origem "Pasta",
 * então vale a regra de sempre: nunca remove faixa, respeita o que o usuário tirou e não recria playlist apagada.
 */
object FolderPlaylists {
    const val ORIGIN = "Pasta"
    private const val MAX_NAME = 120
    /** Pasta com menos músicas que isto NÃO vira playlist (evita playlist de 1 ou 2 músicas). */
    const val MIN_TRACKS = 5

    /** [paths]: id da pasta -> caminho ("Música/Rock"), como o CloudRepository entrega. Devolve null se não há pasta com música. */
    fun build(library: List<CloudTrack>, paths: Map<String, String>): SmartFile? {
        val byParent = LinkedHashMap<String, MutableList<CloudTrack>>()
        for (t in library) {
            if (t.parentId.isBlank()) continue
            val path = paths[t.parentId] ?: continue
            if (path == CloudFolders.ROOT || path == CloudFolders.OTHER || path.isBlank()) continue
            byParent.getOrPut(t.parentId) { ArrayList() } += t
        }
        byParent.entries.removeAll { it.value.size < MIN_TRACKS }
        if (byParent.isEmpty()) return null

        fun lastName(path: String) = path.substringAfterLast('/').trim()
        // Quantas pastas têm cada nome (sem acento e sem maiúscula), para saber quando precisa do caminho.
        val sameName = HashMap<String, Int>()
        for (id in byParent.keys) {
            val k = SmartMatcher.key(lastName(paths.getValue(id)))
            sameName[k] = (sameName[k] ?: 0) + 1
        }

        val used = HashSet<String>()
        val out = ArrayList<SmartPlaylist>()
        val ordered = byParent.keys.sortedBy { SmartMatcher.key(paths.getValue(it)) }
        for (id in ordered) {
            val path = paths.getValue(id)
            val base = lastName(path)
            if (base.isEmpty()) continue
            var name = if ((sameName[SmartMatcher.key(base)] ?: 0) > 1) path.split('/').joinToString(" / ") { it.trim() } else base
            name = name.take(MAX_NAME)
            var n = 2
            val first = name
            while (!used.add(SmartMatcher.key(name))) { name = "$first ($n)"; n++ }
            val refs = byParent.getValue(id)
                .sortedBy { it.fileName.lowercase() }
                .map { SmartTrackRef(it.id, it.fileName, base, it.title, it.artist) }
            out += SmartPlaylist(name, ORIGIN, null, refs)
        }
        if (out.isEmpty()) return null
        // "gerado" muda quando uma pasta muda de nome; o app usa isso para saber que precisa refazer a conta.
        return SmartFile(2, "pastas:" + out.joinToString("|") { it.name }.hashCode(), out)
    }
}
