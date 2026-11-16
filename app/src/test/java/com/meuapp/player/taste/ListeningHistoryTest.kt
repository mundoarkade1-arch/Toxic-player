package com.meuapp.player.taste

import org.junit.Assert.*
import org.junit.Test

class ListeningHistoryTest {
    @Test fun naturalEndIsComplete() = assertEquals(PlayOutcome.COMPLETE, PlayClassifier.classify(1000, 200_000, true))
    @Test fun ninetyPercentIsComplete() = assertEquals(PlayOutcome.COMPLETE, PlayClassifier.classify(180_000, 200_000, false))
    @Test fun underTenSecondsIsEarlySkip() = assertEquals(PlayOutcome.SKIP_EARLY, PlayClassifier.classify(5_000, 200_000, false))
    @Test fun underHalfIsSkip() = assertEquals(PlayOutcome.SKIP, PlayClassifier.classify(60_000, 200_000, false))
    @Test fun overHalfIsPartial() = assertEquals(PlayOutcome.PARTIAL, PlayClassifier.classify(120_000, 200_000, false))
    @Test fun unknownDurationAfterTenSecondsIsPartial() = assertEquals(PlayOutcome.PARTIAL, PlayClassifier.classify(30_000, 0, false))

    @Test fun recordCountsEachOutcome() {
        val h = ListeningHistory()
        h.record("c:1", "A", "X", PlayOutcome.COMPLETE, 200_000, 1)
        h.record("c:1", "A", "X", PlayOutcome.SKIP, 20_000, 2)
        h.record("c:1", "A", "X", PlayOutcome.COMPLETE, 200_000, 3, repeated = true)
        val e = h.get("c:1")!!
        assertEquals(3, e.plays); assertEquals(2, e.completes); assertEquals(1, e.skips); assertEquals(1, e.repeats)
        assertEquals(420_000L, e.listenedMs); assertEquals(3L, e.lastPlayedAt)
    }

    @Test fun skipsAreSoftAndNeverNegative() {
        val h = ListeningHistory()
        repeat(10) { h.record("l:1", "A", "X", PlayOutcome.SKIP, 20_000, it.toLong()) }
        assertEquals(0.0, h.get("l:1")!!.score(), 0.0)
        h.record("l:2", "B", "X", PlayOutcome.COMPLETE, 1, 1)
        h.record("l:2", "B", "X", PlayOutcome.SKIP, 1, 2)
        assertEquals(1.5, h.get("l:2")!!.score(), 0.0001)
    }

    @Test fun blankKeyIsIgnored() {
        val h = ListeningHistory(); h.record("", "A", "X", PlayOutcome.COMPLETE, 1, 1); assertEquals(0, h.size)
    }

    @Test fun favoriteWorksAndCountsInScore() {
        val h = ListeningHistory(); h.setFavorite("c:9", "T", "Y", true, 5)
        assertTrue(h.get("c:9")!!.favorite); assertEquals(5.0, h.get("c:9")!!.score(), 0.0)
        h.setFavorite("c:9", "T", "Y", false, 6); assertFalse(h.get("c:9")!!.favorite)
    }

    @Test fun topArtistsSortedAndIgnoresUnknown() {
        val h = ListeningHistory()
        h.record("1", "a", "Anitta", PlayOutcome.COMPLETE, 1, 1)
        h.record("2", "b", "Anitta", PlayOutcome.COMPLETE, 1, 2)
        h.record("3", "c", "Alceu", PlayOutcome.COMPLETE, 1, 3)
        h.record("4", "d", "Artista desconhecido", PlayOutcome.COMPLETE, 1, 4)
        h.record("5", "e", "", PlayOutcome.COMPLETE, 1, 5)
        val top = h.topArtists(5)
        assertEquals(listOf("Anitta", "Alceu"), top.map { it.first })
        assertEquals(1, h.topArtists(1).size)
    }

    @Test fun trimKeepsFavoritesAndRemovesOldest() {
        val h = ListeningHistory(maxEntries = 3)
        h.setFavorite("fav", "F", "Z", true, 0)
        h.record("a", "a", "X", PlayOutcome.COMPLETE, 1, 1)
        h.record("b", "b", "X", PlayOutcome.COMPLETE, 1, 2)
        h.record("c", "c", "X", PlayOutcome.COMPLETE, 1, 3)
        assertEquals(3, h.size)
        assertNotNull(h.get("fav")); assertNull(h.get("a")); assertNotNull(h.get("c"))
    }

    @Test fun jsonRoundTrip() {
        val h = ListeningHistory()
        h.record("c:1", "Envolver", "Anitta", PlayOutcome.COMPLETE, 190_000, 10, repeated = true)
        h.setFavorite("c:1", "Envolver", "Anitta", true, 11)
        val back = ListeningHistory.parse(h.toJson())
        assertEquals(h.get("c:1"), back.get("c:1"))
    }

    @Test fun parseToleratesGarbage() {
        assertEquals(0, ListeningHistory.parse(null).size)
        assertEquals(0, ListeningHistory.parse("isso não é json").size)
        assertEquals(0, ListeningHistory.parse("""{"formato":"outro","entradas":[]}""").size)
        val ok = ListeningHistory.parse("""{"formato":"toxic-history","v":1,"entradas":[{"k":""},{"k":"x","p":2},5]}""")
        assertEquals(1, ok.size); assertEquals(2, ok.get("x")!!.plays)
    }
}
