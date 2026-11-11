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

class GenreAndRecentTest {
    private fun ct(id: String, genre: String, added: Long, title: String = id) =
        CloudTrack(id, "$id.mp3", title, "A", "B", 0, 100, CloudFormat.MP3, "t", "m", false, MetaState.DONE, genre, added)

    @Test fun cleansGenres() {
        val g = com.meuapp.player.cloud.tags.Genres
        assertEquals("Rock", g.clean("(17)"))
        assertEquals("Funk", g.clean("5"))
        assertEquals("Rock", g.clean("(17)Rock"))
        assertEquals("Funk", g.clean("funk"))
        assertEquals("Funk", g.clean("Funk;Rap"))
        assertEquals("R&B", g.clean("R&B"))
        assertEquals("", g.clean("  "))
        assertEquals("", g.clean(null))
    }
    @Test fun groupsGenresIgnoringCaseAndSortsNoGenreLast() {
        val l = listOf(ct("1", "Funk", 1), ct("2", "funk", 2), ct("3", "Rock", 3), ct("4", "", 4))
        val gs = CloudLibrary.genres(l)
        assertEquals(listOf("Funk", "Rock", "Sem gênero"), gs.map { it.name })
        assertEquals(2, gs[0].count)
        assertEquals(setOf("1", "2"), CloudLibrary.filter(l, group = "G:Funk").map { it.id }.toSet())
        assertEquals(listOf("4"), CloudLibrary.filter(l, group = "G:Sem gênero").map { it.id })
    }
    @Test fun recentFirst() {
        val l = listOf(ct("old", "Rock", 100), ct("new", "Rock", 300), ct("mid", "Rock", 200))
        assertEquals(listOf("new", "mid", "old"), CloudLibrary.sort(l, CloudLibrary.SORT_RECENT).map { it.id })
    }
    @Test fun addedDateFromDrive() {
        val f = DriveFile("a", "a.mp3", "audio/mpeg", 10, "2024-01-02T00:00:00.000Z", null, CloudFormat.MP3, "2024-01-01T00:00:00.000Z")
        assertEquals(java.time.Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(), CloudTrack.addedOf(f))
        assertEquals(0L, CloudTrack.addedOf(f.copy(createdTime = "", modifiedTime = "")))
    }
    @Test fun oldIndexWithoutGenreIsRequeued() {
        val old = org.json.JSONObject("""{"v":1,"tracks":[{"id":"x","fn":"x.mp3","t":"x","a":"A","al":"B","d":1,"s":5,"f":"MP3","m":"t","c":false,"ms":1}]}""")
        val t = CloudTrack.listFromJson(old.toString()).single()
        assertEquals(MetaState.PENDING, t.metaState); assertEquals(0L, t.addedMs)
    }
}

class FolderTest {
    private fun fo(id: String, name: String, parent: String) = DriveFolder(id, name, parent)
    private fun ct(id: String, parent: String, title: String = id) =
        CloudTrack(id, "$id.mp3", title, "A", "B", 0, 100, CloudFormat.MP3, "t", "m", false, MetaState.DONE, "", 1, parent)

    private val folders = mapOf("m" to fo("m", "Música", "root"), "r" to fo("r", "Rock", "m"), "f" to fo("f", "Funk", "m"))

    @Test fun buildsPaths() {
        assertEquals("Música/Rock", CloudFolders.pathOf("r", folders))
        assertEquals("Música", CloudFolders.pathOf("m", folders))
        assertEquals("Meu Drive", CloudFolders.pathOf("root", folders))
        assertEquals("Outros", CloudFolders.pathOf("", folders))
    }
    @Test fun survivesFolderCycles() {
        val loop = mapOf("a" to fo("a", "A", "b"), "b" to fo("b", "B", "a"))
        assertTrue(CloudFolders.pathOf("a", loop).isNotEmpty())
    }
    @Test fun foldersIncludeSubfoldersInCountsAndFilter() {
        val l = listOf(ct("1", "r"), ct("2", "f"), ct("3", "m"))
        val paths = l.map { it.parentId }.distinct().associateWith { CloudFolders.pathOf(it, folders) }
        val g = CloudLibrary.folders(l, paths)
        assertEquals(listOf("Música", "Música/Funk", "Música/Rock"), g.map { it.key.drop(2) })
        assertEquals(3, g[0].count)
        assertEquals(setOf("1", "2", "3"), CloudLibrary.filter(l, group = "F:Música", paths = paths).map { it.id }.toSet())
        assertEquals(listOf("1"), CloudLibrary.filter(l, group = "F:Música/Rock", paths = paths).map { it.id })
        assertEquals(listOf("2"), CloudLibrary.filter(l, query = "funk", paths = paths).map { it.id })
    }
    @Test fun parsesParentsAndFolders() {
        val f = DriveJson.parseFilesPage("""{"files":[{"id":"a","name":"a.mp3","mimeType":"audio/mpeg","size":"5","parents":["p1"]}]}""").files.single()
        assertEquals("p1", f.parentId)
        val (list, next) = DriveJson.parseFoldersPage("""{"nextPageToken":"n","files":[{"id":"p1","name":"Rock","parents":["m"]},{"id":"bad id","name":"x"}]}""")
        assertEquals(listOf(DriveFolder("p1", "Rock", "m")), list); assertEquals("n", next)
    }
}

class HomeLogicTest {
    private fun ct(id: String, artist: String, genre: String = "") =
        CloudTrack(id, "$id.mp3", id, artist, "B", 0, 1, CloudFormat.MP3, "t", "m", false, MetaState.DONE, genre, 1, "")
    private val l = listOf(ct("1", "Pitty", "Rock"), ct("2", "Pitty", "rock"), ct("3", "Henrique e Juliano", "Sertanejo"),
        ct("4", CloudTrack.UNKNOWN_ARTIST, ""))

    @Test fun greetingByHour() {
        assertEquals("Bom dia", CloudHomeLogic.greeting(8)); assertEquals("Boa tarde", CloudHomeLogic.greeting(15)); assertEquals("Boa noite", CloudHomeLogic.greeting(23))
        assertEquals("Boa noite", CloudHomeLogic.greeting(2))
    }
    @Test fun topArtistsAndGenres() {
        assertEquals(listOf("Pitty", "Henrique e Juliano"), CloudHomeLogic.topArtists(l, 5).map { it.name })
        val g = CloudHomeLogic.topGenres(l, 5)
        assertEquals("Rock", g[0].name); assertEquals(2, g[0].count); assertEquals(2, g.size)
        assertEquals(2, CloudHomeLogic.byGenre(l, "Rock").size); assertEquals(2, CloudHomeLogic.byArtist(l, "Pitty").size)
    }
    @Test fun shuffleIsStablePerSeedAndLimited() {
        assertEquals(CloudHomeLogic.shuffled(l, 5, 3).map { it.id }, CloudHomeLogic.shuffled(l, 5, 3).map { it.id })
        assertEquals(2, CloudHomeLogic.shuffled(l, 1, 2).size)
        val h = CloudHomeLogic.hueOf("Pitty"); assertTrue(h >= 0f && h < 360f)
    }
}
