package com.meuapp.player

import com.meuapp.player.youtube.YouTubeLinks
import com.meuapp.player.youtube.YtTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinksTest {
    private val id = "dQw4w9WgXcQ"
    private val pl = "PLabcdefghijklmnop"

    @Test fun lePadroesDeLinkDeVideo() {
        assertEquals(YtTarget(id, null), YouTubeLinks.parse("https://www.youtube.com/watch?v=$id"))
        assertEquals(YtTarget(id, null), YouTubeLinks.parse("https://youtu.be/$id?si=abc"))
        assertEquals(YtTarget(id, null), YouTubeLinks.parse("https://www.youtube.com/shorts/$id"))
        assertEquals(YtTarget(id, null), YouTubeLinks.parse("  https://youtu.be/$id  "))
    }

    @Test fun lePlaylistsEMisturas() {
        assertEquals(YtTarget(null, pl), YouTubeLinks.parse("https://www.youtube.com/playlist?list=$pl"))
        assertEquals(YtTarget(id, pl), YouTubeLinks.parse("https://www.youtube.com/watch?v=$id&list=$pl"))
    }

    @Test fun leCodigosSoltos() {
        assertEquals(YtTarget(id, null), YouTubeLinks.parse(id))
        assertEquals(YtTarget(null, pl), YouTubeLinks.parse(pl))
    }

    @Test fun rejeitaTextoQueNaoELink() {
        assertNull(YouTubeLinks.parse("texto qualquer"))
        assertNull(YouTubeLinks.parse("ab"))
        assertNull(YouTubeLinks.parse(""))
    }

    @Test fun criaLinksNormaisParaAbrirNoYoutubeOficial() {
        assertEquals("https://www.youtube.com/watch?v=$id", YouTubeLinks.externalUrl(YtTarget(id, null)))
        assertEquals("https://www.youtube.com/playlist?list=$pl", YouTubeLinks.externalUrl(YtTarget(null, pl)))
        assertEquals("https://www.youtube.com/watch?v=$id&list=$pl", YouTubeLinks.externalUrl(YtTarget(id, pl)))
    }

    @Test fun leResultadosDaBuscaEDecodificaTexto() {
        val json = """{"items":[
            {"id":{"videoId":"AAAAAAAAAAA"},"snippet":{"title":"Rock &amp; Roll","channelTitle":"Canal","thumbnails":{"default":{"url":"https://img/a.jpg"}}}},
            {"id":{"videoId":""},"snippet":{"title":"sem id"}},
            {"id":{"videoId":"BBBBBBBBBBB"},"snippet":{"title":"Outro","channelTitle":"C2","thumbnails":{"default":{"url":"https://img/b.jpg"}}}},
            {"id":{"playlistId":"PLabcdefghijklmnop"},"snippet":{"title":"Mix","channelTitle":"Canal 3","thumbnails":{"default":{"url":"https://img/c.jpg"}}}}
        ]}"""
        val r = YouTubeLinks.parseSearch(json)
        assertEquals(3, r.size)
        assertEquals("Rock & Roll", r[0].title)
        assertEquals("BBBBBBBBBBB", r[1].target.videoId)
        assertEquals("PLabcdefghijklmnop", r[2].target.playlistId)
        assertTrue(YouTubeLinks.parseSearch("lixo").isEmpty())
    }
}

