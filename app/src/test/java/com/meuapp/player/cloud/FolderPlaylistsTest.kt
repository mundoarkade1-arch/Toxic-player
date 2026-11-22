package com.meuapp.player.cloud

import org.junit.Assert.*
import org.junit.Test

class FolderPlaylistsTest {
    private fun ct(id: String, fileName: String, parent: String = "") =
        CloudTrack(id, fileName, fileName.substringBeforeLast('.'), CloudTrack.UNKNOWN_ARTIST, CloudTrack.UNKNOWN_ALBUM, 0, 100,
            CloudFormat.MP3, "t", null, false, MetaState.PENDING, parentId = parent)

    @Test fun folderNameBecomesPlaylistName() {
        val lib = listOf(ct("1", "b.mp3", "f1"), ct("2", "a.mp3", "f1"), ct("3", "c.mp3", "f2"))
        val paths = mapOf("f1" to "Música/Sertanejo", "f2" to "Música/Rock")
        val f = FolderPlaylists.build(lib, paths)!!
        assertEquals(listOf("Rock", "Sertanejo"), f.playlists.map { it.name }.sorted())
        assertTrue(f.playlists.all { it.origin == "Pasta" && it.rule == null })
        // Faixas em ordem do nome do arquivo.
        assertEquals(listOf("2", "1"), f.playlists.first { it.name == "Sertanejo" }.tracks.map { it.id })
    }

    @Test fun rootAndLooseTracksAreIgnored() {
        val lib = listOf(ct("1", "a.mp3", ""), ct("2", "b.mp3", "root"), ct("3", "c.mp3", "unknown"))
        val paths = mapOf("root" to CloudFolders.ROOT, "x" to "Música/Rock")
        assertNull(FolderPlaylists.build(lib, paths))
        assertNull(FolderPlaylists.build(emptyList(), paths))
    }

    @Test fun parentFolderDoesNotCollectSubfolders() {
        val lib = listOf(ct("1", "a.mp3", "sub"))
        val paths = mapOf("top" to "Música", "sub" to "Música/Rock")
        val f = FolderPlaylists.build(lib, paths)!!
        assertEquals(listOf("Rock"), f.playlists.map { it.name })
    }

    @Test fun sameFolderNameInDifferentPlacesKeepsThePath() {
        val lib = listOf(ct("1", "a.mp3", "f1"), ct("2", "b.mp3", "f2"))
        val paths = mapOf("f1" to "Música/Rock", "f2" to "Download/rock")
        val names = FolderPlaylists.build(lib, paths)!!.playlists.map { it.name }
        assertEquals(2, names.size)
        assertEquals(2, names.map { SmartMatcher.key(it) }.toSet().size)
        assertTrue(names.contains("Música / Rock"))
    }

    @Test fun generatedChangesWhenAFolderIsRenamed() {
        val lib = listOf(ct("1", "a.mp3", "f1"))
        val g1 = FolderPlaylists.build(lib, mapOf("f1" to "Música/Rock"))!!.generated
        val g2 = FolderPlaylists.build(lib, mapOf("f1" to "Música/Rock antigo"))!!.generated
        assertNotEquals(g1, g2)
    }

    @Test fun appliesAsNewPlaylistsAndNeverRemovesUserTracks() {
        val lib = listOf(ct("1", "a.mp3", "f1"), ct("2", "b.mp3", "f1"))
        val paths = mapOf("f1" to "Música/Rock")
        val file = FolderPlaylists.build(lib, paths)!!
        // Primeira vez: cria a playlist com as duas faixas.
        val first = SmartPlaylists.apply(file, lib, paths, emptyMap())
        assertTrue(first.results.single().created)
        assertEquals(setOf(lib[0].numericId, lib[1].numericId), first.results.single().finalIds.toSet())
        // O usuário tirou a faixa 2 e pôs uma outra à mão: nada disso é desfeito.
        val mine = listOf(lib[0].numericId, 777L)
        val second = SmartPlaylists.apply(file, lib, paths, mapOf("Rock" to mine), mapOf("Rock" to first.results.single().syncedIds))
        assertTrue(second.results.single().addedIds.isEmpty())
        assertEquals(mine, second.results.single().finalIds)
    }

    @Test fun mergesWithSiteFileWithoutRemovingAnything() {
        val lib = listOf(ct("1", "a.mp3", "f1"))
        val paths = mapOf("f1" to "Música/Rock")
        val folders = FolderPlaylists.build(lib, paths)
        val site = SmartFile(2, "S", listOf(SmartPlaylist("Anitta", "Cantor", null, emptyList())))
        val out = SmartMerge.merge(site, folders)!!
        assertEquals(listOf("Anitta", "Rock"), out.playlists.map { it.name })
    }
}
