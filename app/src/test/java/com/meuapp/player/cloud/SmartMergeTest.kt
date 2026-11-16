package com.meuapp.player.cloud

import org.junit.Assert.*
import org.junit.Test

class SmartMergeTest {
    private fun ref(id: String = "", fn: String = "", t: String = "", a: String = "") = SmartTrackRef(id, fn, "", t, a)
    private fun pl(name: String, origin: String, rule: SmartRule?, vararg tracks: SmartTrackRef) = SmartPlaylist(name, origin, rule, tracks.toList())
    private fun file(gen: String, vararg p: SmartPlaylist) = SmartFile(2, gen, p.toList())

    @Test fun nullsAreHandled() {
        val f = file("A", pl("X", "Site", null))
        assertNull(SmartMerge.merge(null, null))
        assertSame(f, SmartMerge.merge(f, null))
        assertSame(f, SmartMerge.merge(null, f))
    }

    @Test fun newMotorPlaylistsAreAppendedAndSiteOnesStay() {
        val site = file("S", pl("Rock", "Site", null, ref(id = "1")))
        val motor = file("M", pl("Anitta", "Motor", SmartRule(SmartRule.ARTISTA, "Anitta"), ref(id = "2")))
        val out = SmartMerge.merge(site, motor)!!
        assertEquals(listOf("Rock", "Anitta"), out.playlists.map { it.name })
        assertEquals("S|M", out.generated)
    }

    @Test fun sameNameKeepsSiteRuleAndAddsOnlyNewTracks() {
        val siteRule = SmartRule(SmartRule.PASTA, "Anitta")
        val site = file("S", pl("Anitta", "Site", siteRule, ref(id = "1"), ref(fn = "b.mp3")))
        val motor = file("M", pl("ANITTA", "Motor", SmartRule(SmartRule.ARTISTA, "Anitta"),
            ref(id = "1"), ref(fn = "B.MP3"), ref(id = "3", fn = "c.mp3")))
        val out = SmartMerge.merge(site, motor)!!
        assertEquals(1, out.playlists.size)
        val p = out.playlists[0]
        assertEquals("Site", p.origin)
        assertEquals(siteRule, p.rule)
        assertEquals(3, p.tracks.size)
        assertEquals("3", p.tracks.last().id)
    }

    @Test fun mergedFileSurvivesJsonRoundTrip() {
        val site = file("S", pl("Rock", "Site", null, ref(id = "1", t = "Um", a = "Dois")))
        val motor = file("M", pl("Pop", "Motor", null, ref(id = "2", t = "Tres", a = "Quatro")))
        val out = SmartMerge.merge(site, motor)!!
        val back = SmartPlaylistJson.parse(SmartPlaylistJson.toJson(out)) as SmartParse.Ok
        assertEquals(2, back.file.playlists.size)
    }
}
