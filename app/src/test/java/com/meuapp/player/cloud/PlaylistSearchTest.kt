package com.meuapp.player.cloud

import org.junit.Assert.*
import org.junit.Test

class PlaylistSearchTest {
    private val none = { emptyList<String>() }

    @Test fun nameMatchIgnoresAccentCaseAndPunctuation() {
        assertEquals(PlaylistSearch.BY_NAME, PlaylistSearch.score("charlie brown jr.", "Charlie Brown Jr", none, 30))
        assertEquals(PlaylistSearch.BY_NAME, PlaylistSearch.score("SERTANEJO", "Sertanejo Universitário", none, 30))
        assertEquals(PlaylistSearch.BY_NAME, PlaylistSearch.score("universitario", "Sertanejo Universitário", none, 30))
    }

    @Test fun contentMatchWhenMostTracksAreFromTheArtist() {
        val names = { List(12) { "Charlie Brown Jr." } + List(18) { "Outro" } }   // 12 de 30 = 40%
        assertEquals(PlaylistSearch.BY_CONTENT, PlaylistSearch.score("Charlie Brown Jr", "Rock nacional", names, 30))
    }

    @Test fun fewTracksFromTheArtistDoNotMatch() {
        val names = { List(3) { "Charlie Brown Jr." } + List(27) { "Outro" } }    // 10%
        assertEquals(PlaylistSearch.NO_MATCH, PlaylistSearch.score("Charlie Brown Jr", "Rock nacional", names, 30))
    }

    @Test fun emptyQueryAndEmptyPlaylistNeverMatch() {
        assertEquals(PlaylistSearch.NO_MATCH, PlaylistSearch.score("  ", "Rock", none, 30))
        assertEquals(PlaylistSearch.NO_MATCH, PlaylistSearch.score("...", "Rock", none, 30))
        assertEquals(PlaylistSearch.NO_MATCH, PlaylistSearch.score("rock", "Pop", { listOf("Rock") }, 0))
    }

    @Test fun namesAreOnlyReadWhenNameDidNotMatch() {
        var called = false
        PlaylistSearch.score("rock", "Rock", { called = true; emptyList() }, 30)
        assertFalse(called)
    }
}
