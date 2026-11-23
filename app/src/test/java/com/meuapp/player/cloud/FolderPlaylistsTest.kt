package com.meuapp.player.cloud

import org.junit.Assert.*
import org.junit.Test

class FolderPlaylistsTest {
    private fun ct(id: String, fileName: String, parent: String = "") =
        CloudTrack(id, fileName, fileName.substringBeforeLast('.'), CloudTrack.UNKNOWN_ARTIST, CloudTrack.UNKNOWN_ALBUM, 0, 100,
            CloudFormat.MP3, "t", null, false, MetaState.PENDING, parentId = parent)

    /** [n] músicas dentro da pasta [parent] (nomes de arquivo "<parent>-01.mp3" ...). */
    private fun many(parent: String, n: Int = FolderPlaylists.MIN_TRACKS) =
        (1..n).map { ct("$parent-$it", "%s-%02d.mp3".format(parent, it), parent) }

    @Test fun folderNameBecomesPlaylistName() {
        val lib = many("f1") + many("f2")
        val paths = mapOf("f1" to "Música/Sertanejo", "f2" to "Música/Rock")
        val f = FolderPlaylists.build(lib, paths)!!
        assertEquals(listOf("Rock", "Sertanejo"), f.playlists.map { it.name }.sorted())
        assertTrue(f.playlists.all { it.origin == "Pasta" && it.rule == null })
        // Faixas em ordem do nome do arquivo.
        assertEquals(many("f1").map { it.id }, f.playlists.first { it.name == "Sertanejo" }.tracks.map { it.id })
    }

    @Test fun smallFoldersDoNotBecomePlaylists() {
        val lib = many("f1", FolderPlaylists.MIN_TRACKS - 1) + many("f2", 40)
        val paths = mapOf("f1" to "Música/Poucas", "f2" to "Música/Muitas")
        val f = FolderPlaylists.build(lib, paths)!!
        assertEquals(listOf("Muitas"), f.playlists.map { it.name })
        assertNull(FolderPlaylists.build(many("f1", 2), mapOf("f1" to "Música/Poucas")))
    }

    @Test fun rootAndLooseTracksAreIgnored() {
        val lib = many("", 10) + many("root", 10) + many("unknown", 10)
        val paths = mapOf("root" to CloudFolders.ROOT, "x" to "Música/Rock")
        assertNull(FolderPlaylists.build(lib, paths))
        assertNull(FolderPlaylists.build(emptyList(), paths))
    }

    @Test fun parentFolderDoesNotCollectSubfolders() {
        val lib = many("sub")
        val paths = mapOf("top" to "Música", "sub" to "Música/Rock")
        val f = FolderPlaylists.build(lib, paths)!!
        assertEquals(listOf("Rock"), f.playlists.map { it.name })
    }

    @Test fun sameFolderNameInDifferentPlacesKeepsThePath() {
        val lib = many("f1") + many("f2")
        val paths = mapOf("f1" to "Música/Rock", "f2" to "Download/rock")
        val names = FolderPlaylists.build(lib, paths)!!.playlists.map { it.name }
        assertEquals(2, names.size)
        assertEquals(2, names.map { SmartMatcher.key(it) }.toSet().size)
        assertTrue(names.contains("Música / Rock"))
    }

    @Test fun generatedChangesWhenAFolderIsRenamed() {
        val lib = many("f1")
        val g1 = FolderPlaylists.build(lib, mapOf("f1" to "Música/Rock"))!!.generated
        val g2 = FolderPlaylists.build(lib, mapOf("f1" to "Música/Rock antigo"))!!.generated
        assertNotEquals(g1, g2)
    }

    @Test fun appliesAsNewPlaylistsAndNeverRemovesUserTracks() {
        val lib = many("f1", FolderPlaylists.MIN_TRACKS + 1)
        val paths = mapOf("f1" to "Música/Rock")
        val file = FolderPlaylists.build(lib, paths)!!
        // Primeira vez: cria a playlist com as faixas todas.
        val first = SmartPlaylists.apply(file, lib, paths, emptyMap())
        assertTrue(first.results.single().created)
        assertEquals(lib.map { it.numericId }.toSet(), first.results.single().finalIds.toSet())
        // O usuário tirou faixas e pôs uma outra à mão: nada disso é desfeito.
        val mine = listOf(lib[0].numericId, 777L)
        val second = SmartPlaylists.apply(file, lib, paths, mapOf("Rock" to mine), mapOf("Rock" to first.results.single().syncedIds))
        assertTrue(second.results.single().addedIds.isEmpty())
        assertEquals(mine, second.results.single().finalIds)
    }
}
