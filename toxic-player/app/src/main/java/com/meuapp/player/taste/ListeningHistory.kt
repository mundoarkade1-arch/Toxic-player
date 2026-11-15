package com.meuapp.player.taste

import org.json.JSONArray
import org.json.JSONObject

/** Como terminou uma escuta. Pular NUNCA é rejeição definitiva: só muda um peso pequeno. */
enum class PlayOutcome { COMPLETE, PARTIAL, SKIP_EARLY, SKIP }

/** Decide se a música foi ouvida até o fim, pela metade ou pulada. Lógica pura. */
object PlayClassifier {
    const val EARLY_MS = 10_000L

    fun classify(listenedMs: Long, durationMs: Long, endedNaturally: Boolean): PlayOutcome {
        if (endedNaturally) return PlayOutcome.COMPLETE
        if (durationMs > 0 && listenedMs >= durationMs * 9 / 10) return PlayOutcome.COMPLETE
        if (listenedMs < EARLY_MS) return PlayOutcome.SKIP_EARLY
        if (durationMs > 0 && listenedMs < durationMs / 2) return PlayOutcome.SKIP
        return PlayOutcome.PARTIAL
    }
}

/** Tudo que o app sabe sobre as escutas de UMA música. [key] é estável: "c:<idDoDrive>" ou "l:<idLocal>". */
data class HistoryEntry(
    val key: String,
    val title: String,
    val artist: String,
    val plays: Int = 0,
    val completes: Int = 0,
    val partials: Int = 0,
    val skips: Int = 0,
    val earlySkips: Int = 0,
    val repeats: Int = 0,
    val listenedMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val favorite: Boolean = false
) {
    /** Peso de gosto desta música. Pulos pesam pouco e o resultado nunca fica abaixo de zero. */
    fun score(): Double {
        val s = completes * 2.0 + partials * 1.0 + repeats * 3.0 + (if (favorite) 5.0 else 0.0) -
            skips * 0.5 - earlySkips * 0.25
        return if (s < 0.0) 0.0 else s
    }
}

/**
 * Histórico de escuta guardado SÓ no aparelho. Lógica pura (só org.json), sem Android.
 * Não apaga favoritas ao limpar o excesso. Não envia nada para fora.
 */
class ListeningHistory(initial: Collection<HistoryEntry> = emptyList(), private val maxEntries: Int = 1500) {
    private val map = LinkedHashMap<String, HistoryEntry>()

    init { initial.forEach { map[it.key] = it } }

    val size: Int get() = map.size
    fun entries(): List<HistoryEntry> = map.values.toList()
    fun get(key: String): HistoryEntry? = map[key]

    fun record(key: String, title: String, artist: String, outcome: PlayOutcome, listenedMs: Long,
               now: Long, repeated: Boolean = false) {
        if (key.isBlank()) return
        val e = map[key] ?: HistoryEntry(key, title, artist)
        val updated = e.copy(
            title = title.ifBlank { e.title },
            artist = artist.ifBlank { e.artist },
            plays = e.plays + 1,
            completes = e.completes + if (outcome == PlayOutcome.COMPLETE) 1 else 0,
            partials = e.partials + if (outcome == PlayOutcome.PARTIAL) 1 else 0,
            skips = e.skips + if (outcome == PlayOutcome.SKIP) 1 else 0,
            earlySkips = e.earlySkips + if (outcome == PlayOutcome.SKIP_EARLY) 1 else 0,
            repeats = e.repeats + if (repeated) 1 else 0,
            listenedMs = e.listenedMs + (if (listenedMs > 0) listenedMs else 0L),
            lastPlayedAt = now
        )
        map.remove(key); map[key] = updated      // vai para o fim = mais recente
        trim()
    }

    fun setFavorite(key: String, title: String, artist: String, fav: Boolean, now: Long) {
        if (key.isBlank()) return
        val e = map[key] ?: HistoryEntry(key, title, artist, lastPlayedAt = now)
        map[key] = e.copy(favorite = fav)
        trim()
    }

    /** Artistas por peso (maior primeiro). Ignora artista vazio ou desconhecido. */
    fun topArtists(limit: Int): List<Pair<String, Double>> {
        val sum = HashMap<String, Double>()
        for (e in map.values) {
            val a = e.artist.trim()
            if (a.isEmpty() || a.equals("Artista desconhecido", true) || a.equals("<unknown>", true)) continue
            sum[a] = (sum[a] ?: 0.0) + e.score()
        }
        return sum.entries.filter { it.value > 0.0 }.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }

    /** Remove as mais antigas que NÃO são favoritas quando passa do limite. */
    private fun trim() {
        if (map.size <= maxEntries) return
        val victims = map.values.filter { !it.favorite }.sortedBy { it.lastPlayedAt }
        var over = map.size - maxEntries
        for (v in victims) { if (over <= 0) break; map.remove(v.key); over-- }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (e in map.values) arr.put(JSONObject()
            .put("k", e.key).put("t", e.title).put("a", e.artist)
            .put("p", e.plays).put("c", e.completes).put("pa", e.partials)
            .put("s", e.skips).put("es", e.earlySkips).put("r", e.repeats)
            .put("ms", e.listenedMs).put("lp", e.lastPlayedAt).put("f", e.favorite))
        return JSONObject().put("formato", FORMAT).put("v", 1).put("entradas", arr).toString()
    }

    companion object {
        const val FORMAT = "toxic-history"

        /** Leitor tolerante: texto inválido ou campo faltando nunca derruba o app (volta histórico vazio). */
        fun parse(text: String?, maxEntries: Int = 1500): ListeningHistory {
            if (text.isNullOrBlank()) return ListeningHistory(emptyList(), maxEntries)
            return try {
                val root = JSONObject(text)
                if (root.optString("formato") != FORMAT) return ListeningHistory(emptyList(), maxEntries)
                val arr = root.optJSONArray("entradas") ?: return ListeningHistory(emptyList(), maxEntries)
                val list = ArrayList<HistoryEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val k = o.optString("k")
                    if (k.isBlank()) continue
                    list.add(HistoryEntry(k, o.optString("t"), o.optString("a"),
                        o.optInt("p"), o.optInt("c"), o.optInt("pa"), o.optInt("s"), o.optInt("es"), o.optInt("r"),
                        o.optLong("ms"), o.optLong("lp"), o.optBoolean("f")))
                }
                ListeningHistory(list, maxEntries)
            } catch (e: Exception) {
                ListeningHistory(emptyList(), maxEntries)
            }
        }
    }
}
