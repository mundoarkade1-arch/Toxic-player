package com.meuapp.player.taste

import org.junit.Assert.*
import org.junit.Test

class UserTasteProfileTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun e(key: String, artist: String, completes: Int = 1, skips: Int = 0, ageDays: Long = 0, fav: Boolean = false) =
        HistoryEntry(key, key, artist, plays = completes + skips, completes = completes, skips = skips,
            lastPlayedAt = now - ageDays * day, favorite = fav)

    private val genres = mapOf("1" to "Sertanejo", "2" to "Sertanejo", "3" to "Funk")
    private fun build(vararg es: HistoryEntry) = UserTasteProfile.build(es.toList(), { genres[it] ?: "" }, now)

    @Test fun emptyHistoryGivesEmptyProfile() {
        val p = build()
        assertTrue(p.isEmpty); assertEquals(0.0, p.artistAffinity("Anitta"), 0.0); assertEquals("", p.explain("Anitta", "Pop"))
    }

    @Test fun mostListenedArtistIsOne() {
        val p = build(e("1", "Anitta", completes = 4), e("3", "Alceu", completes = 1))
        assertEquals(1.0, p.artistAffinity("Anitta"), 0.0001)
        assertEquals(0.25, p.artistAffinity("Alceu"), 0.0001)
        assertEquals("Anitta", p.topArtists(1)[0].name)
    }

    @Test fun matchingIgnoresCaseAndAccents() {
        val p = build(e("1", "Alceu Valença", completes = 2))
        assertTrue(p.isKnownArtist("ALCEU VALENCA")); assertTrue(p.artistAffinity("alceu valença") > 0.0)
    }

    @Test fun oldListeningCountsLess() {
        val p = build(e("1", "Novo", ageDays = 0), e("2", "Velho", ageDays = 60))
        assertEquals(0.5, p.artistAffinity("Velho"), 0.0001)
        assertEquals(1.0, p.artistAffinity("Novo"), 0.0001)
    }

    @Test fun skipsOnlyDoNotCreateTaste() {
        val p = build(e("1", "Chato", completes = 0, skips = 5))
        assertTrue(p.isEmpty); assertFalse(p.isKnownArtist("Chato"))
    }

    @Test fun unknownArtistIsIgnoredButGenreStillCounts() {
        val p = build(e("1", "Artista desconhecido", completes = 3))
        assertEquals(0.0, p.artistAffinity("Artista desconhecido"), 0.0)
        assertEquals(1.0, p.genreAffinity("sertanejo"), 0.0001)
    }

    @Test fun genresAreSumAcrossTracks() {
        val p = build(e("1", "A", completes = 1), e("2", "B", completes = 1), e("3", "C", completes = 1))
        assertEquals(1.0, p.genreAffinity("Sertanejo"), 0.0001)
        assertEquals(0.5, p.genreAffinity("Funk"), 0.0001)
        assertEquals("Sertanejo", p.topGenres(1)[0].name)
    }

    @Test fun explainPicksSimpleReasons() {
        val p = build(e("1", "Anitta", completes = 4), e("3", "Alceu", completes = 1))
        assertEquals("Você ouve bastante Anitta.", p.explain("Anitta", "Pop"))
        assertEquals("Você já ouviu Alceu.", p.explain("Alceu", ""))
        assertEquals("", p.explain("Desconhecido Total", "Jazz"))
    }

    @Test fun favoriteBoostsAffinity() {
        val p = build(e("1", "Fã", completes = 0, fav = true), e("3", "Outro", completes = 1))
        assertTrue(p.artistAffinity("Fã") > p.artistAffinity("Outro"))
    }
}
