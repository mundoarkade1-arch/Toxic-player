package com.meuapp.player.taste

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Uma música da biblioteca que pode ser sugerida. [key] é a mesma chave do histórico ("c:<idDoDrive>"). */
data class Candidate(val key: String, val title: String, val artist: String, val genre: String = "", val addedMs: Long = 0L)

/** Sugestão pronta: [reason] é uma frase simples; [isDiscovery] = artista novo para o usuário. */
data class Recommendation(val key: String, val score: Double, val reason: String, val isDiscovery: Boolean)

/**
 * Motor de recomendação híbrido. Lógica pura, sem Android, sem internet.
 * Mistura: gosto por artista, gosto por gênero, histórico (recente, favorita, pulos), novidade na biblioteca,
 * contexto (boost opcional) e uma parte reservada para DESCOBERTA (artistas novos).
 * Regras: nunca sugere o que está em [exclude]; sem duplicata (mesma chave ou mesma música com outro arquivo);
 * limite por artista; evita o mesmo artista duas vezes seguidas; pulo só reduz um pouco, nunca bane.
 * Ainda NÃO usa popularidade (não há dado de popularidade no app).
 */
class MusicRecommendationEngine(private val seed: Long = 7L) {

    private class Scored(val c: Candidate, val score: Double, val discovery: Boolean) {
        val artistKey: String = UserTasteProfile.norm(c.artist)
    }

    fun recommend(
        candidates: List<Candidate>,
        profile: UserTasteProfile,
        history: Map<String, HistoryEntry>,
        now: Long,
        limit: Int,
        exclude: Set<String> = emptySet(),
        discoveryShare: Double = 0.2,
        maxPerArtist: Int = 3,
        contextBoost: (Candidate) -> Double = { 0.0 }
    ): List<Recommendation> {
        if (limit <= 0) return emptyList()
        val rnd = Random(seed)

        // 1) Sem duplicata e sem o que já está excluído (inclui a mesma música em outro arquivo).
        val seenKeys = HashSet<String>()
        val seenSongs = HashSet<String>()
        for (c in candidates) if (c.key in exclude) seenSongs.add(songId(c))
        val scored = ArrayList<Scored>()
        for (c in candidates) {
            if (c.key.isBlank() || c.key in exclude || !seenKeys.add(c.key)) continue
            if (!seenSongs.add(songId(c))) continue
            scored.add(score(c, profile, history[c.key], now, rnd, contextBoost))
        }

        // 2) Separa conforto (já conhecido) e descoberta (artista novo no estilo que o usuário gosta).
        val wantDisc = if (profile.isEmpty) 0 else (limit * discoveryShare).roundToInt().coerceIn(0, limit)
        val comfort = scored.filter { !it.discovery }.sortedByDescending { it.score }
        val disc = scored.filter { it.discovery }.sortedByDescending { it.score }

        val counts = HashMap<String, Int>()
        val chosen = HashSet<String>()
        fun pick(from: List<Scored>, n: Int): List<Scored> {
            val out = ArrayList<Scored>()
            for (s in from) {
                if (out.size >= n) break
                if (s.c.key in chosen) continue
                val used = counts[s.artistKey] ?: 0
                if (used >= maxPerArtist) continue
                counts[s.artistKey] = used + 1; chosen.add(s.c.key); out.add(s)
            }
            return out
        }
        val d = ArrayList(pick(disc, wantDisc))
        val c = ArrayList(pick(comfort, limit - d.size))
        if (d.size + c.size < limit) d.addAll(pick(disc, limit - d.size - c.size))   // sobrou vaga: mais descobertas

        // 3) Descobertas espalhadas pela lista, não todas juntas.
        val total = d.size + c.size
        val step = if (d.isEmpty()) 0 else max(2, (total.toDouble() / d.size).roundToInt())
        val ordered = ArrayList<Scored>(total)
        var ci = 0; var di = 0
        while (ordered.size < total) {
            val discTurn = di < d.size && step > 0 && (ordered.size + 1) % step == 0
            if (discTurn) ordered.add(d[di++])
            else if (ci < c.size) ordered.add(c[ci++])
            else ordered.add(d[di++])
        }

        // 4) Evita o mesmo artista duas vezes seguidas, quando dá.
        for (i in 1 until ordered.size) {
            if (ordered[i].artistKey != ordered[i - 1].artistKey) continue
            var j = i + 1
            while (j < ordered.size && ordered[j].artistKey == ordered[i - 1].artistKey) j++
            if (j < ordered.size) { val t = ordered[i]; ordered[i] = ordered[j]; ordered[j] = t }
        }

        return ordered.map { s ->
            val reason = if (s.discovery) {
                if (s.c.genre.isNotBlank()) "Descoberta: artista novo no estilo ${s.c.genre}." else "Descoberta: artista novo para você."
            } else {
                profile.explain(s.c.artist, s.c.genre).ifBlank { "Da sua biblioteca." }
            }
            Recommendation(s.c.key, s.score, reason, s.discovery)
        }
    }

    private fun songId(c: Candidate) = UserTasteProfile.norm(c.title) + "|" + UserTasteProfile.norm(c.artist)

    private fun score(c: Candidate, p: UserTasteProfile, h: HistoryEntry?, now: Long, rnd: Random,
                      boost: (Candidate) -> Double): Scored {
        val a = p.artistAffinity(c.artist)
        val g = if (c.genre.isBlank()) 0.0 else p.genreAffinity(c.genre)
        var s = 0.55 * a + 0.30 * g + boost(c)
        if (h != null) {
            val ageH = if (h.lastPlayedAt > 0 && now > h.lastPlayedAt) (now - h.lastPlayedAt) / 3_600_000.0 else 1e9
            if (ageH < 6.0) s -= 0.5 else if (ageH < 24.0) s -= 0.25      // evita repetir o que acabou de tocar
            if (h.completes == 0 && h.skips >= 3) s -= 0.3                 // muito pulada: desce, mas não some
            if (h.favorite) s += 0.15
            if (h.completes > 0) s += 0.05
        } else {
            s += 0.05                                                       // nunca tocada: pequena chance extra
        }
        if (c.addedMs > 0 && now > c.addedMs && (now - c.addedMs) < 14L * 86_400_000L) s += 0.1   // chegou há pouco
        val discovery = !p.isEmpty && h == null && !p.isKnownArtist(c.artist) && g > 0.0
        return Scored(c, s + rnd.nextDouble() * 0.001, discovery)
    }
}
