package com.meuapp.player.taste

import java.text.Normalizer

/** Um gosto (artista ou gênero) com força de 0 a 1. [name] é o nome para mostrar. */
data class Affinity(val name: String, val value: Double)

/**
 * Perfil de gosto do usuário, calculado a partir do histórico. Lógica pura, sem Android.
 * Escuta recente pesa mais (meia-vida de 60 dias). Pulo pesa pouco. Nada sai do aparelho.
 * O perfil é RECALCULADO do histórico; não guarda nada próprio.
 */
class UserTasteProfile private constructor(
    private val artists: Map<String, Affinity>,
    private val genres: Map<String, Affinity>,
    val trackCount: Int
) {
    val isEmpty: Boolean get() = artists.isEmpty() && genres.isEmpty()

    fun artistAffinity(name: String): Double = artists[norm(name)]?.value ?: 0.0
    fun genreAffinity(name: String): Double = genres[norm(name)]?.value ?: 0.0
    fun isKnownArtist(name: String): Boolean = artists.containsKey(norm(name))

    fun topArtists(n: Int): List<Affinity> = artists.values.sortedByDescending { it.value }.take(n)
    fun topGenres(n: Int): List<Affinity> = genres.values.sortedByDescending { it.value }.take(n)

    /** Frase curta em português simples dizendo por que esta música combina (ou nada, se não há motivo). */
    fun explain(artist: String, genre: String): String {
        val a = artistAffinity(artist); val g = genreAffinity(genre)
        return when {
            a >= 0.5 -> "Você ouve bastante $artist."
            a > 0.0 && g >= 0.5 -> "Você já ouviu $artist e gosta de $genre."
            g >= 0.5 -> "Você gosta de $genre."
            a > 0.0 -> "Você já ouviu $artist."
            else -> ""
        }
    }

    companion object {
        const val HALF_LIFE_DAYS = 60.0
        private const val DAY_MS = 86_400_000.0

        fun norm(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        private fun isUnknown(a: String) = a.isBlank() || a.equals("Artista desconhecido", true) || a.equals("<unknown>", true)

        /** [genreOf] recebe a chave da música (ex.: "c:abc") e devolve o gênero ("" se não souber). */
        fun build(entries: Collection<HistoryEntry>, genreOf: (String) -> String, now: Long): UserTasteProfile {
            val aSum = HashMap<String, Double>(); val aName = HashMap<String, String>()
            val gSum = HashMap<String, Double>(); val gName = HashMap<String, String>()
            var count = 0
            for (e in entries) {
                val base = e.score()
                if (base <= 0.0) continue
                count++
                val ageDays = if (e.lastPlayedAt > 0 && now > e.lastPlayedAt) (now - e.lastPlayedAt) / DAY_MS else 0.0
                val w = base * Math.pow(0.5, ageDays / HALF_LIFE_DAYS)
                if (!isUnknown(e.artist)) {
                    val k = norm(e.artist); aSum[k] = (aSum[k] ?: 0.0) + w; aName.putIfAbsent(k, e.artist.trim())
                }
                val g = genreOf(e.key).trim()
                if (g.isNotEmpty()) {
                    val k = norm(g); gSum[k] = (gSum[k] ?: 0.0) + w; gName.putIfAbsent(k, g)
                }
            }
            return UserTasteProfile(scale(aSum, aName), scale(gSum, gName), count)
        }

        private fun scale(sum: Map<String, Double>, names: Map<String, String>): Map<String, Affinity> {
            val max = sum.values.maxOrNull() ?: return emptyMap()
            if (max <= 0.0) return emptyMap()
            val out = HashMap<String, Affinity>()
            for ((k, v) in sum) out[k] = Affinity(names[k] ?: k, v / max)
            return out
        }
    }
}
