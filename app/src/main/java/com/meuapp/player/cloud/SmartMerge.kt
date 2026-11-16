package com.meuapp.player.cloud

/**
 * Junta as playlists do site ("playlists-inteligentes.json") com as do motor ("playlists-motor.json") num arquivo só.
 * Lógica PURA, testável na JVM. NUNCA remove nada: playlists e faixas do site sempre ficam.
 * - Nome novo (comparado sem acento e sem maiúscula): a playlist do motor entra como está.
 * - Mesmo nome: vale a regra e a origem do site; as faixas do motor que o site ainda não tem são acrescentadas.
 */
object SmartMerge {
    fun merge(site: SmartFile?, motor: SmartFile?): SmartFile? {
        if (site == null) return motor
        if (motor == null) return site
        val out = ArrayList<SmartPlaylist>(site.playlists)
        val index = HashMap<String, Int>()
        for (i in out.indices) index[SmartMatcher.key(out[i].name)] = i
        for (m in motor.playlists) {
            val k = SmartMatcher.key(m.name)
            val i = index[k]
            if (i == null) {
                index[k] = out.size
                out.add(m)
            } else {
                val s = out[i]
                val seen = HashSet<String>()
                s.tracks.forEach { seen.addAll(keys(it)) }
                val extra = ArrayList<SmartTrackRef>()
                for (t in m.tracks) {
                    val ks = keys(t)
                    if (ks.none { it in seen }) {
                        seen.addAll(ks)
                        extra.add(t)
                    }
                }
                if (extra.isNotEmpty()) out[i] = s.copy(tracks = s.tracks + extra)
            }
        }
        // "gerado" junta os dois: se qualquer um mudar, o app refaz a conta.
        return SmartFile(maxOf(site.version, motor.version), site.generated + "|" + motor.generated, out)
    }

    /** Identificadores de uma faixa: id do Drive e/ou nome do arquivo; sem os dois, título + artista. */
    private fun keys(t: SmartTrackRef): List<String> {
        val ks = ArrayList<String>()
        if (t.id.isNotEmpty()) ks.add("i:" + t.id)
        if (t.fileName.isNotBlank()) ks.add("f:" + t.fileName.trim().lowercase())
        if (ks.isEmpty()) ks.add("t:" + t.title.trim().lowercase() + "|" + t.artist.trim().lowercase())
        return ks
    }
}
