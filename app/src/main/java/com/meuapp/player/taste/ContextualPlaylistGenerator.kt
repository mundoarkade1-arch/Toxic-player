package com.meuapp.player.taste

import kotlin.math.max

/** Resultado: [items] já vêm na ordem final; [understood] = o nome da playlist foi entendido; [note] = frase simples para o usuário. */
data class GeneratedPlaylist(val items: List<Recommendation>, val understood: Boolean, val note: String)

/**
 * Transforma o nome ou a intenção de uma playlist ("playlist pra viajar", "relaxar", "só Anitta") em sugestões.
 * Lógica pura, sem Android. Usa uma tabela SIMPLES de palavras e gêneros (palpites razoáveis, não ciência):
 * só ajuda músicas que tenham gênero nas tags. Também reconhece o nome de um artista ou gênero da biblioteca.
 * A escolha final das músicas é do [MusicRecommendationEngine] (gosto, histórico, descoberta).
 */
class ContextualPlaylistGenerator(private val engine: MusicRecommendationEngine = MusicRecommendationEngine()) {

    private class Rule(val triggers: List<String>, val genres: List<String>, val share: Double = 0.2, val nostalgia: Boolean = false)

    private val rules = listOf(
        Rule(listOf("viaj", "estrada", "road"), listOf("rock", "sertanejo", "pop", "country", "mpb")),
        Rule(listOf("noite", "dirig"), listOf("pop", "rock", "r&b", "eletron", "soul")),
        Rule(listOf("relax", "calma", "calmo", "dormir", "descans"), listOf("mpb", "bossa", "jazz", "soul", "acust", "lofi", "classic")),
        Rule(listOf("trein", "academia", "malhar", "corrida", "correr"), listOf("funk", "rap", "hip", "trap", "eletron", "rock", "dance")),
        Rule(listOf("trabalh", "estud", "foco", "concentr"), listOf("lofi", "instrumental", "jazz", "classic", "ambient")),
        Rule(listOf("anim", "festa", "churrasco", "danc"), listOf("funk", "pop", "pagode", "forro", "dance", "axe", "samba")),
        Rule(listOf("romant", "amor", "namor"), listOf("romant", "sertanejo", "mpb", "pop", "soul", "balada")),
        Rule(listOf("nostalg", "saudade", "antigas", "retro"), emptyList(), nostalgia = true),
        Rule(listOf("descobert", "novidade", "novas"), emptyList(), share = 0.6)
    )

    private val day = 86_400_000L

    fun generate(
        intent: String,
        candidates: List<Candidate>,
        profile: UserTasteProfile,
        history: Map<String, HistoryEntry>,
        now: Long,
        limit: Int = 30,
        exclude: Set<String> = emptySet()
    ): GeneratedPlaylist {
        val text = UserTasteProfile.norm(intent)
        val words = LinkedHashSet<String>()
        var share = 0.2
        var nostalgia = false
        var matched = false
        for (r in rules) {
            if (r.triggers.any { text.contains(it) }) {
                matched = true; words.addAll(r.genres); share = max(share, r.share); nostalgia = nostalgia || r.nostalgia
            }
        }
        // Gênero da biblioteca citado no nome (ex.: "meu rock").
        for (g in candidates.map { it.genre }.filter { it.isNotBlank() }.distinct()) {
            val n = UserTasteProfile.norm(g)
            if (n.length >= 3 && text.contains(n)) { words.add(n); matched = true }
        }
        // Artista da biblioteca citado no nome (ex.: "só Anitta").
        val artistsNamed = HashSet<String>()
        for (a in candidates.map { it.artist }.distinct()) {
            val n = UserTasteProfile.norm(a)
            if (n.length >= 3 && n != "artista desconhecido" && n != "<unknown>" && text.contains(n)) { artistsNamed.add(n); matched = true }
        }
        val genreWords = words.toList()

        val boost: (Candidate) -> Double = { c ->
            var b = 0.0
            if (c.genre.isNotBlank() && genreWords.isNotEmpty()) {
                val g = UserTasteProfile.norm(c.genre)
                if (genreWords.any { g.contains(it) }) b += 0.5
            }
            if (artistsNamed.isNotEmpty() && UserTasteProfile.norm(c.artist) in artistsNamed) b += 0.6
            if (nostalgia) {
                val h = history[c.key]
                if (h != null && h.completes > 0 && h.lastPlayedAt > 0 && now - h.lastPlayedAt > 30 * day) b += 0.4
            }
            b
        }

        val hits = candidates.count { it.key !in exclude && boost(it) > 0.0 }
        val cap = if (artistsNamed.isNotEmpty()) max(limit, 1) else 3
        val items = engine.recommend(candidates, profile, history, now, limit, exclude, share, cap, boost)

        val understood = matched && hits > 0
        val note = when {
            text.isBlank() -> "Escreva uma intenção, como relaxar ou treinar. Usei o seu gosto geral."
            !matched -> "Não entendi bem o nome. Usei o seu gosto geral."
            hits == 0 -> "Não achei músicas com esse estilo nas tags. Usei o seu gosto geral."
            else -> "Montei com base no nome da playlist e no seu gosto."
        }
        return GeneratedPlaylist(items, understood, note)
    }
}
