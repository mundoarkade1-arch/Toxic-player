package com.meuapp.player.taste

import org.junit.Assert.*
import org.junit.Test

class ContextualPlaylistGeneratorTest {
    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val now = 1_000 * day
    private val gen = ContextualPlaylistGenerator(MusicRecommendationEngine(1L))
    private val noHistory = emptyMap<String, HistoryEntry>()
    private val emptyProfile = UserTasteProfile.build(emptyList(), { "" }, now)

    private fun cand(key: String, artist: String, genre: String = "") = Candidate(key, key, artist, genre)
    private val lib = listOf(
        cand("r1", "Banda Rock", "Rock"), cand("f1", "MC Funk", "Funk"), cand("s1", "Dupla", "Sertanejo"),
        cand("m1", "Cantora", "MPB"), cand("a1", "Anitta", "Pop"), cand("a2", "Anitta", "Pop"), cand("x1", "Sem Genero", ""))

    @Test fun trainingPrefersFunkAndRock() {
        val r = gen.generate("Treinar", lib, emptyProfile, noHistory, now, limit = 3)
        assertTrue(r.understood)
        assertTrue(r.items.take(2).all { it.key == "f1" || it.key == "r1" })   // funk e rock estão na lista de treino
    }

    @Test fun relaxPrefersMpb() {
        val r = gen.generate("Relaxar", lib, emptyProfile, noHistory, now, limit = 2)
        assertTrue(r.understood); assertEquals("m1", r.items[0].key)
    }

    @Test fun accentsAndCaseDoNotMatter() {
        val r = gen.generate("DIRIGINDO À NOITE", lib, emptyProfile, noHistory, now, limit = 3)
        assertTrue(r.understood)
        assertTrue(r.items.take(3).any { it.key == "r1" || it.key == "a1" })
    }

    @Test fun artistNamedInIntentComesFirstAndIsNotCappedAtThree() {
        val many = (1..6).map { cand("an$it", "Anitta", "Pop") } + cand("o1", "Outro", "Rock")
        val r = gen.generate("só Anitta", many, emptyProfile, noHistory, now, limit = 6)
        assertTrue(r.understood)
        assertEquals(6, r.items.count { it.key.startsWith("an") })
    }

    @Test fun genreNamedInIntentIsUnderstood() {
        val r = gen.generate("meu rock", lib, emptyProfile, noHistory, now, limit = 2)
        assertTrue(r.understood); assertEquals("r1", r.items[0].key)
    }

    @Test fun nostalgiaPrefersOldFavoritesOverRecentOnes() {
        val old = HistoryEntry("o", "o", "Velho", plays = 1, completes = 1, lastPlayedAt = now - 90 * day)
        val rec = HistoryEntry("r", "r", "Recente", plays = 1, completes = 1, lastPlayedAt = now - 2 * hour)
        val cands = listOf(cand("o", "Velho"), cand("r", "Recente"), cand("n", "Novo"))
        val profile = UserTasteProfile.build(listOf(old, rec), { "" }, now)
        val r = gen.generate("Nostalgia", cands, profile, listOf(old, rec).associateBy { it.key }, now, limit = 3)
        assertTrue(r.understood); assertEquals("o", r.items[0].key)
    }

    @Test fun unknownIntentStillGivesSongsAndSaysSo() {
        val r = gen.generate("xyzzy", lib, emptyProfile, noHistory, now, limit = 3)
        assertFalse(r.understood); assertEquals(3, r.items.size); assertTrue(r.note.startsWith("Não entendi"))
    }

    @Test fun emptyIntentSaysSo() {
        val r = gen.generate("   ", lib, emptyProfile, noHistory, now, limit = 3)
        assertFalse(r.understood); assertTrue(r.note.startsWith("Escreva"))
    }

    @Test fun styleWithoutMatchingTagsIsHonest() {
        val onlyRock = listOf(cand("r1", "A", "Rock"), cand("r2", "B", "Rock"))
        val r = gen.generate("Relaxar", onlyRock, emptyProfile, noHistory, now, limit = 2)
        assertFalse(r.understood); assertTrue(r.note.contains("tags")); assertEquals(2, r.items.size)
    }

    @Test fun excludedSongsNeverReturn() {
        val r = gen.generate("Treinar", lib, emptyProfile, noHistory, now, limit = 10, exclude = setOf("f1"))
        assertFalse(r.items.any { it.key == "f1" })
    }
}
