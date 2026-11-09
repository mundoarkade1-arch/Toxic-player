package com.meuapp.player.cloud

import org.junit.Assert.*
import org.junit.Test

class CloudLogicTest {
    private fun df(id: String, name: String = "$id.mp3", size: Long = 100, md5: String? = "aa", mod: String = "t1") =
        DriveFile(id, name, "audio/mpeg", size, mod, md5, CloudFormat.MP3)
    private fun ct(id: String, size: Long = 100, md5: String? = "aa", mod: String = "t1", title: String = id, artist: String = "A", album: String = "B") =
        CloudTrack(id, "$id.mp3", title, artist, album, 0, size, CloudFormat.MP3, mod, md5, false, MetaState.PENDING)

    // ---- formatos ----
    @Test fun detectsAllFormatsByExtension() {
        mapOf("a.mp3" to CloudFormat.MP3, "a.FLAC" to CloudFormat.FLAC, "a.wav" to CloudFormat.WAV, "a.m4a" to CloudFormat.M4A,
            "a.aac" to CloudFormat.AAC, "a.ogg" to CloudFormat.OGG, "a.opus" to CloudFormat.OPUS).forEach { (n, f) ->
            assertEquals(n, f, CloudFormat.detect(n, "application/octet-stream"))
        }
    }
    @Test fun fallsBackToMimeAndRejectsOthers() {
        assertEquals(CloudFormat.MP3, CloudFormat.detect("semextensao", "audio/mpeg"))
        assertNull(CloudFormat.detect("foto.jpg", "image/jpeg"))
        assertNull(CloudFormat.detect("doc.pdf", "application/octet-stream"))
    }

    // ---- JSON do Drive ----
    @Test fun parsesFilesPageAndIgnoresInvalidEntries() {
        val json = """{"nextPageToken":"n1","files":[
          {"id":"abc_1","name":"01 - Song.mp3","mimeType":"audio/mpeg","size":"1234","modifiedTime":"2024","md5Checksum":"ff"},
          {"id":"x2","name":"pasta","mimeType":"application/vnd.google-apps.folder"},
          {"id":"x3","name":"foto.jpg","mimeType":"image/jpeg","size":"10"},
          {"id":"x4","name":"lixo.mp3","mimeType":"audio/mpeg","size":"10","trashed":true},
          {"id":"bad id!","name":"a.mp3","mimeType":"audio/mpeg","size":"10"},
          {"id":"x5","name":"semtamanho.flac","mimeType":"audio/flac"},
          {"id":"x6","name":"b.flac","mimeType":"application/octet-stream","size":"99"}]}"""
        val p = DriveJson.parseFilesPage(json)
        assertEquals("n1", p.nextPageToken)
        assertEquals(listOf("abc_1", "x6"), p.files.map { it.id })
        assertEquals(CloudFormat.FLAC, p.files[1].format)
    }
    @Test fun parsesChanges() {
        val json = """{"newStartPageToken":"t9","changes":[
          {"fileId":"a1","removed":true},
          {"fileId":"a2","removed":false,"file":{"id":"a2","name":"n.opus","mimeType":"audio/ogg","size":"5"}},
          {"fileId":"a3","removed":false,"file":{"id":"a3","name":"x.txt","mimeType":"text/plain","size":"5"}}]}"""
        val p = DriveJson.parseChangesPage(json)
        assertNull(p.nextPageToken); assertEquals("t9", p.newStartPageToken)
        assertTrue(p.changes[0].removed)
        assertEquals(CloudFormat.OPUS, p.changes[1].file?.format)
        assertNull(p.changes[2].file)
    }
    @Test fun parsesAboutAndErrorReason() {
        val a = DriveJson.parseAbout("""{"user":{"displayName":"Ana","emailAddress":"a@x.com"},"storageQuota":{"limit":"100","usage":"40"}}""")
        assertEquals("Ana", a.displayName); assertEquals(40L, a.usageBytes); assertEquals(100L, a.limitBytes)
        assertEquals("rateLimitExceeded", DriveJson.parseErrorReason("""{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}"""))
        assertNull(DriveJson.parseErrorReason("não é json"))
    }

    // ---- sincronização ----
    @Test fun fullPlanFindsAddedModifiedRemoved() {
        val local = mapOf("a" to ct("a"), "b" to ct("b"), "c" to ct("c"))
        val remote = listOf(df("a"), df("b", md5 = "zz"), df("d"))
        val p = SyncPlanner.planFull(local, remote)
        assertEquals(listOf("d"), p.added.map { it.id })
        assertEquals(listOf("b"), p.modified.map { it.id })
        assertEquals(listOf("c"), p.removedIds)
        assertEquals(1, p.unchanged)
    }
    @Test fun incrementalUsesLastChangeAndHandlesRemoval() {
        val local = mapOf("a" to ct("a"), "b" to ct("b"))
        val ch = listOf(
            DriveChange("a", false, df("a", size = 999)), DriveChange("a", true, null),
            DriveChange("b", false, df("b", mod = "t2")), DriveChange("n", false, df("n")), DriveChange("ghost", true, null)
        )
        val p = SyncPlanner.planChanges(local, ch)
        assertEquals(listOf("a"), p.removedIds)
        assertEquals(listOf("b"), p.modified.map { it.id })
        assertEquals(listOf("n"), p.added.map { it.id })
    }
    @Test fun fileTurnedNonAudioIsRemoved() {
        val p = SyncPlanner.planChanges(mapOf("a" to ct("a")), listOf(DriveChange("a", false, null)))
        assertEquals(listOf("a"), p.removedIds)
    }

    // ---- biblioteca ----
    @Test fun searchIgnoresAccentsAndCase() {
        val l = listOf(ct("1", title = "Açaí", artist = "Zé"), ct("2", title = "Outra"))
        assertEquals(listOf("1"), CloudLibrary.filter(l, "acai").map { it.id })
        assertEquals(listOf("1"), CloudLibrary.filter(l, "ZE").map { it.id })
    }
    @Test fun filtersAndSorts() {
        val l = listOf(ct("1", title = "b", artist = "X", album = "Al1"), ct("2", title = "a", artist = "Y", album = "Al2").copy(format = CloudFormat.FLAC, size = 500))
        assertEquals(listOf("2"), CloudLibrary.filter(l, format = CloudFormat.FLAC).map { it.id })
        assertEquals(listOf("2"), CloudLibrary.filter(l, offlineOnly = true, offlineIds = setOf("2")).map { it.id })
        assertEquals(listOf("1"), CloudLibrary.filter(l, group = "A:X").map { it.id })
        assertEquals(listOf("2", "1"), CloudLibrary.sort(l, CloudLibrary.SORT_TITLE).map { it.id })
        assertEquals(listOf("2", "1"), CloudLibrary.sort(l, CloudLibrary.SORT_SIZE).map { it.id })
        assertEquals(2, CloudLibrary.artists(l).size)
    }
    @Test fun formatsSizes() {
        assertEquals("512 B", CloudLibrary.formatSize(512)); assertEquals("1.5 MB", CloudLibrary.formatSize(1_572_864))
    }

    // ---- índice ----
    @Test fun numericIdIsNegativeStableAndRoundTrips() {
        val t = ct("abc")
        assertTrue(t.numericId < 0); assertEquals(t.numericId, CloudTrack.numericIdFor("abc"))
        assertNotEquals(CloudTrack.numericIdFor("abc"), CloudTrack.numericIdFor("abd"))
        val back = CloudTrack.listFromJson(CloudTrack.listToJson(listOf(t, ct("z", md5 = null))))
        assertEquals(listOf(t, ct("z", md5 = null)), back)
    }
    @Test fun nameParserDerivesTitleAndArtist() {
        val p = com.meuapp.player.cloud.tags.NameParser.parse("03 - Artista - Música_Nova.mp3")
        assertEquals("Artista", p.artist); assertEquals("Música Nova", p.title)
        assertNull(com.meuapp.player.cloud.tags.NameParser.parse("so_titulo.flac").artist)
    }

    // ---- erros ----
    @Test fun classifiesErrors() {
        assertEquals(CloudErrorKind.NO_INTERNET, CloudErrors.classify(java.net.UnknownHostException()))
        assertEquals(CloudErrorKind.NO_SPACE, CloudErrors.classify(java.io.IOException("write failed: ENOSPC (No space left on device)")))
        assertEquals(CloudErrorKind.NO_INTERNET, CloudErrors.classify(RuntimeException(java.net.SocketTimeoutException())))
        assertEquals(CloudErrorKind.TOKEN_EXPIRED, CloudErrors.fromHttp(401, null).kind)
        assertEquals(CloudErrorKind.RATE_LIMITED, CloudErrors.fromHttp(403, "userRateLimitExceeded").kind)
        assertEquals(CloudErrorKind.RATE_LIMITED, CloudErrors.fromHttp(429, null).kind)
        assertEquals(CloudErrorKind.ACCESS_DENIED, CloudErrors.fromHttp(403, "forbidden").kind)
        assertEquals(CloudErrorKind.FILE_GONE, CloudErrors.fromHttp(404, null).kind)
    }
    @Test fun backoffGrowsAndHonorsRetryAfter() {
        assertTrue(CloudErrors.backoffMs(2, 0, 0.0) > CloudErrors.backoffMs(0, 0, 0.0))
        assertEquals(7000L, CloudErrors.backoffMs(0, 7, 0.5))
        assertTrue(CloudErrors.backoffMs(5, 0, 1.0) <= 60_000L)
    }
    @Test fun errorMessagesNeverContainSecrets() {
        CloudErrorKind.values().forEach { assertFalse(it.message.contains("Bearer", true)); assertFalse(it.message.contains("http")) }
    }

    // ---- API do Drive: reautenticação e repetição (sem rede real) ----
    @Test fun apiWithoutTokenFailsAsNotConnected() {
        val api = DriveApi(object : DriveApi.TokenProvider {
            override fun get(): String = throw CloudException(CloudErrorKind.NOT_CONNECTED)
            override fun refresh(): String = throw CloudException(CloudErrorKind.NOT_CONNECTED)
        })
        try { api.about(); fail() } catch (e: CloudException) { assertEquals(CloudErrorKind.NOT_CONNECTED, e.kind) }
    }
}
