package com.meuapp.player.taste

import org.junit.Assert.*
import org.junit.Test

class MusicRecommendationEngineTest {
    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 1_000 * day
    private val engine = MusicRecommendationEngine(1L)

    private fun cand(key: String, artist: String, genre: String = "", title: String = key) = Candidate(key, title, artist, genre)
    private fun hist(key: String, artist: String, completes: Int = 1, skips: Int = 0, agoMs: Long = 10 * day, fav: Boolean = false) =
        HistoryEntry(key, key, artist, plays = completes + skips, completes = completes, skips = skips,
            lastPlayedAt = now - agoMs, favorite = fav)

    private fun profileOf(entries: List<HistoryEntry>, genres: Map<String, String> = emptyMap()) =
        UserTasteProfile.build(entries, { genres[it] ?: "" }, now)

    private fun go(cands: List<Candidate>, entries: List<HistoryEntry>, genres: Map<String, String> = emptyMap(),
                    limit: Int = 5, exclude: Set<String> = emptySet(), share: Double = 0.2, cap: Int = 3) =
        engine.recommend(cands, profileOf(entries, genres), entries.associateBy { it.key }, now, limit, exclude, share, cap)

    @Test fun zeroLimitGivesEmpty() = assertTrue(go(listOf(cand("a", "X")), emptyList(), limit = 0).isEmpty())

    @Test fun coldStartStillGivesSongsWithoutDiscovery() {
        val r = go((1..6).map { cand("k$it", "Art$it") }, emptyList(), limit = 4)
        assertEquals(4, r.size); assertTrue(r.none { it.isDiscovery }); assertEquals("Da sua biblioteca.", r[0].reason)
    }

    @Test fun excludedAndDuplicatesNeverAppear() {
        val c = listOf(cand("a", "X", title = "Envolver"), cand("b", "X", title = "Envolver"), cand("c", "Y"), cand("c", "Y"))
        val r = go(c, emptyList(), limit = 10, exclude = setOf("a"))
        assertFalse(r.any { it.key == "a" })
        assertFalse(r.any { it.key == "b" })              // mesma música de uma excluída, outro arquivo
        assertEquals(1, r.count { it.key == "c" })
    }

    @Test fun recentlyPlayedGoesDown() {
        val e = listOf(hist("old", "Anitta", agoMs = 10 * day), hist("new", "Anitta", agoMs = hour))
        val r = go(listOf(cand("new", "Anitta"), cand("old", "Anitta")), e, limit = 2)
        assertEquals(listOf("old", "new"), r.map { it.key })
    }

    @Test fun heavilySkippedGoesDownButStaysAvailable() {
        val e = listOf(hist("liked", "Anitta"), hist("skipped", "Outro", completes = 0, skips = 4))
        val r = go(listOf(cand("skipped", "Outro"), cand("fresh", "Novo")), e, limit = 2)
        assertEquals("fresh", r[0].key); assertTrue(r.any { it.key == "skipped" })
    }

    @Test fun favoriteIsPreferred() {
        val e = listOf(hist("x", "Anitta", completes = 1), hist("fav", "Anitta", completes = 1, fav = true))
        val r = go(listOf(cand("x", "Anitta"), cand("fav", "Anitta")), e, limit = 2)
        assertEquals("fav", r[0].key)
    }

    @Test fun artistCapAndDiscoverySlot() {
        val genres = mapOf("a1" to "Pop", "a2" to "Pop", "a3" to "Pop")
        val e = listOf(hist("a1", "Anitta", completes = 3), hist("a2", "Anitta", completes = 3), hist("a3", "Anitta", completes = 3))
        val cands = (1..8).map { cand("a$it", "Anitta", "Pop") } + listOf(
            cand("d1", "Dua Lipa", "Pop"), cand("d2", "Rihanna", "Pop"), cand("m1", "Metallica", "Rock"))
        val r = go(cands, e, genres, limit = 5)
        assertEquals(5, r.size)
        assertEquals(r.size, r.map { it.key }.toSet().size)
        assertTrue(r.count { it.key.startsWith("a") } <= 3)
        assertEquals(1, r.count { it.isDiscovery })
        val d = r.first { it.isDiscovery }
        assertTrue(d.key == "d1" || d.key == "d2")
        assertTrue(d.reason.startsWith("Descoberta"))
    }

    @Test fun neverSameArtistTwiceInARowWhenPossible() {
        val e = listOf(hist("a1", "Alfa", completes = 5), hist("b1", "Beta", completes = 4))
        val cands = listOf(cand("a1", "Alfa"), cand("a2", "Alfa"), cand("a3", "Alfa"), cand("b1", "Beta"), cand("b2", "Beta"), cand("b3", "Beta"))
        val r = go(cands, e, limit = 6)
        assertEquals(6, r.size)
        val artists = r.map { rec -> cands.first { it.key == rec.key }.artist }
        for (i in 1 until artists.size) assertNotEquals(artists[i - 1], artists[i])
    }

    @Test fun contextBoostChangesOrder() {
        val e = listOf(hist("x", "Alfa"))
        val cands = listOf(cand("a", "Beta", "Rock"), cand("b", "Gama", "Forró"))
        val r = engine.recommend(cands, profileOf(e), e.associateBy { it.key }, now, 2,
            contextBoost = { if (it.genre == "Forró") 0.5 else 0.0 })
        assertEquals("b", r[0].key)
    }

    @Test fun sameInputSameOutput() {
        val cands = (1..10).map { cand("k$it", "Art$it") }
        assertEquals(go(cands, emptyList(), limit = 5).map { it.key }, go(cands, emptyList(), limit = 5).map { it.key })
    }
}
