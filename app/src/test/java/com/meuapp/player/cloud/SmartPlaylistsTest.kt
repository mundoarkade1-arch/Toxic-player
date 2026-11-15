package com.meuapp.player.cloud

import org.junit.Assert.*
import org.junit.Test

class SmartPlaylistsTest {
    private fun ct(id: String, fileName: String, title: String = "", artist: String = CloudTrack.UNKNOWN_ARTIST, parent: String = "") =
        CloudTrack(id, fileName, title.ifBlank { fileName.substringBeforeLast('.') }, artist, CloudTrack.UNKNOWN_ALBUM, 0, 100,
            CloudFormat.MP3, "t", null, false, MetaState.PENDING, parentId = parent)

    private fun ref(id: String = "", fn: String = "", t: String = "", a: String = "", p: String = "") = SmartTrackRef(id, fn, p, t, a)
    private fun pl(name: String, rule: SmartRule? = null, vararg tracks: SmartTrackRef) = SmartPlaylist(name, "Teste", rule, tracks.toList())
    private fun file(vararg p: SmartPlaylist) = SmartFile(2, "2026-11-15T02:00:00.000Z", p.toList())

    private val a = ct("idA", "Henrique e Juliano - Até Você Voltar.mp3", "Até Você Voltar", "Henrique & Juliano", "f1")
    private val b = ct("idB", "Anitta - Envolver.mp3", "Envolver", "Anitta", "f2")
    private val c = ct("idC", "Pontos Cardeais - Alceu Valença.mp3", "Pontos Cardeais", "Alceu Valença", "f2")
    private val lib = listOf(a, b, c)
    private val paths = mapOf("f1" to "Músicas do Site/Meu PC/Sertanejo", "f2" to "Músicas do Site/Meu PC/Top 50 - Brazil - Spotify")

    // ---------- leitor ----------
    @Test fun parsesFullFile() {
        val json = """{"formato":"toxic-playlists","v":2,"gerado":"G1","playlists":[
          {"nome":"Top 50 Mundo","origem":"Deezer","regra":{"tipo":"lista","itens":[{"t":"Envolver","a":"Anitta"}]},
           "faixas":[{"id":"idB","fn":"Anitta - Envolver.mp3","p":"x","t":"Envolver","a":"Anitta"}]},
          {"nome":"Henrique & Juliano","regra":{"tipo":"artista","nome":"Henrique & Juliano"},"faixas":[]},
          {"nome":"Sertanejo","regra":{"tipo":"pasta","nome":"Sertanejo"}}]}"""
        val ok = SmartPlaylistJson.parse(json) as SmartParse.Ok
        assertEquals("G1", ok.file.generated); assertEquals(2, ok.file.version)
        assertEquals(3, ok.file.playlists.size)
        assertEquals(SmartRule.LISTA, ok.file.playlists[0].rule?.type)
        assertEquals(SmartItem("Envolver", "Anitta"), ok.file.playlists[0].rule?.items?.first())
        assertEquals("idB", ok.file.playlists[0].tracks[0].id)
        assertEquals(0, ok.file.playlists[2].tracks.size)
    }
    @Test fun invalidFilesNeverCrash() {
        listOf(null, "", "   ", "não é json", "[1,2]", "{}", """{"formato":"outra-coisa","playlists":[]}""", "{\"playlists\":5}").forEach {
            assertTrue("deveria ser inválido: $it", SmartPlaylistJson.parse(it) is SmartParse.Invalid)
        }
    }
    @Test fun toleratesMissingFieldsUnknownVersionAndUnknownRule() {
        val json = """{"v":99,"playlists":[
          {"nome":"A","regra":{"tipo":"genero","nome":"Funk"},"faixas":[{"fn":"x.mp3"},{"id":null},5,"texto",{"t":"Só título"}]},
          {"origem":"sem nome"}, {"nome":"A"}, 7,
          {"nome":"B","regra":{"tipo":"artista"},"faixas":null}]}"""
        val ok = SmartPlaylistJson.parse(json) as SmartParse.Ok
        assertEquals(99, ok.file.version)
        assertEquals(listOf("A", "B"), ok.file.playlists.map { it.name })
        assertNull(ok.file.playlists[0].rule)                  // tipo desconhecido
        assertEquals(2, ok.file.playlists[0].tracks.size)      // só as faixas com id/fn/título
        assertNull(ok.file.playlists[1].rule)                  // artista sem nome
        assertEquals(3, ok.skipped)                            // sem nome, repetida e item inválido
    }
    @Test fun acceptsBomAndRoundTrips() {
        val src = file(pl("P", SmartRule(SmartRule.PASTA, "Sertanejo"), ref("idA", "a.mp3", "T", "A", "pc")),
            pl("L", SmartRule(SmartRule.LISTA, items = listOf(SmartItem("X", "Y")))))
        val text = "﻿" + SmartPlaylistJson.toJson(src)
        assertEquals(src, (SmartPlaylistJson.parse(text) as SmartParse.Ok).file)
    }

    // ---------- casamento (cada etapa) ----------
    @Test fun matchesById() {
        assertEquals("idB", SmartMatcher(lib).match(ref(id = "idB", fn = "outro.mp3", t = "outro"))?.id)
    }
    @Test fun matchesByFileNameIgnoringCase() {
        assertEquals("idB", SmartMatcher(lib).match(ref(id = "naoExiste", fn = "ANITTA - envolver.MP3"))?.id)
        assertEquals("idB", SmartMatcher(lib).match(ref(fn = "anitta - envolver.mp3"))?.id)
    }
    @Test fun matchesByTitleAndArtistWithAccentsAndCase() {
        assertEquals("idA", SmartMatcher(lib).match(ref(t = "ATE VOCE VOLTAR", a = "henrique & juliano"))?.id)
        assertEquals("idC", SmartMatcher(lib).match(ref(t = "pontos cardeais", a = "ALCEU VALENCA"))?.id)
    }
    @Test fun matchesWhenFileNameHasArtistAndTitleSwapped() {
        // o arquivo é "Música - Cantor" e o JSON trouxe o contrário (ou vice-versa)
        assertEquals("idC", SmartMatcher(lib).match(ref(t = "Alceu Valença", a = "Pontos Cardeais"))?.id)
    }
    @Test fun artistSeparatorsAndFeat() {
        val m = SmartMatcher(listOf(ct("z", "Zé Neto e Cristiano - Largado.mp3", "Largado", "Zé Neto & Cristiano")))
        assertEquals("z", m.byTitleArtist("Largado", "Ze Neto e Cristiano")?.id)
        assertEquals("z", m.byTitleArtist("Largado", "Zé Neto x Cristiano")?.id)
        val f = SmartMatcher(listOf(ct("f", "Anitta feat Cardi B - Girl.mp3", "Girl", "Anitta")))
        assertEquals("f", f.byTitleArtist("Girl", "Anitta")?.id)
        assertEquals("f", f.byTitleArtist("Girl", "Anitta, Cardi B")?.id)
    }
    @Test fun apostrophesAndPunctuationIgnored() {
        val m = SmartMatcher(listOf(ct("d", "Queen - Don't Stop Me Now.mp3", "Don't Stop Me Now", "Queen")))
        assertEquals("d", m.byTitleArtist("Dont Stop Me Now!", "Queen")?.id)
    }
    @Test fun noMatchReturnsNull() {
        val m = SmartMatcher(lib)
        assertNull(m.match(ref(id = "x", fn = "x.mp3", t = "Inexistente", a = "Ninguém")))
        assertNull(m.match(ref()))
        assertNull(m.byTitleArtist("Envolver", "Outro Cantor"))
    }

    // ---------- regras ----------
    @Test fun artistRuleIgnoresAndXAmpersand() {
        val m = SmartMatcher(lib)
        assertEquals(listOf("idA"), m.byArtistRule("Henrique e Juliano").map { it.id })
        assertEquals(listOf("idA"), m.byArtistRule("Henrique x Juliano").map { it.id })
        assertEquals(listOf("idA"), m.byArtistRule("HENRIQUE & JULIANO").map { it.id })
        assertTrue(m.byArtistRule("Ana").isEmpty())                  // palavra inteira, não pedaço
        assertTrue(m.byArtistRule("  &  ").isEmpty())
    }
    @Test fun artistRuleDoesNotMatchPartOfWord() {
        val m = SmartMatcher(listOf(ct("m", "Mariana - Teste.mp3", "Teste", "Mariana")))
        assertTrue(m.byArtistRule("Ana").isEmpty())
    }
    @Test fun folderRuleUsesLastSegmentOfDrivePath() {
        val m = SmartMatcher(lib)
        assertEquals(listOf("idB", "idC"), m.byFolderRule("Top 50 - Brazil - Spotify", paths).map { it.id })
        assertEquals(listOf("idA"), m.byFolderRule("sertanejo", paths).map { it.id })
        assertTrue(m.byFolderRule("Meu PC", paths).isEmpty())        // só o último trecho
        assertTrue(m.byFolderRule("Pasta que não existe", paths).isEmpty())
    }
    @Test fun listRuleMatchesEachItem() {
        val items = listOf(SmartItem("Envolver", "Anitta"), SmartItem("Música Que Não Tenho", "Alguém"), SmartItem("Até Você Voltar", "Henrique e Juliano"))
        assertEquals(listOf("idB", "idA"), SmartMatcher(lib).byListRule(items).map { it.id })
    }

    // ---------- aplicar / mesclar ----------
    @Test fun createsPlaylistFromFaixasAndCountsUnmatched() {
        val f = file(pl("Top", null, ref("idA"), ref(t = "Envolver", a = "Anitta"), ref(t = "Sumiu", a = "Fulano")))
        val out = SmartPlaylists.apply(f, lib, paths, emptyMap())
        val r = out.results.single()
        assertTrue(r.created)
        assertEquals(listOf(a.numericId, b.numericId), r.finalIds)
        assertEquals(listOf("Fulano - Sumiu"), r.unmatched)
        assertEquals(1, out.updated); assertEquals(2, out.added); assertEquals(1, out.notFound)
        assertEquals("1 playlist atualizada, +2 faixas, 1 não encontrada.", SmartPlaylists.describe(out))
    }
    @Test fun ruleComplementsFaixasWithoutDuplicates() {
        val f = file(pl("Pasta", SmartRule(SmartRule.PASTA, "Top 50 - Brazil - Spotify"), ref("idB")))
        val r = SmartPlaylists.apply(f, lib, paths, emptyMap()).results.single()
        assertEquals(listOf(b.numericId, c.numericId), r.finalIds)   // idB da faixa + idC da regra, sem repetir idB
    }
    @Test fun applyingTwiceDoesNotDuplicate() {
        val f = file(pl("Top", SmartRule(SmartRule.ARTISTA, "Anitta"), ref("idA")))
        val first = SmartPlaylists.apply(f, lib, paths, emptyMap()).results.single()
        val second = SmartPlaylists.apply(f, lib, paths, mapOf("Top" to first.finalIds), mapOf("Top" to first.syncedIds))
        val r = second.results.single()
        assertEquals(first.finalIds, r.finalIds)
        assertTrue(r.addedIds.isEmpty()); assertFalse(r.created)
        assertEquals(0, second.updated)
        assertEquals("Playlists do Drive já estão em dia (1).", SmartPlaylists.describe(second))
        assertEquals(first.finalIds.distinct(), r.finalIds)
    }
    @Test fun manualTrackIsPreservedAndOrderKept() {
        val manual = c.numericId
        val f = file(pl("Top", null, ref("idA")))
        val r = SmartPlaylists.apply(f, lib, paths, mapOf("Top" to listOf(manual))).results.single()
        assertEquals(listOf(manual, a.numericId), r.finalIds)
        assertFalse(r.created)
    }
    @Test fun trackRemovedByUserIsNotAddedBack() {
        val f = file(pl("Top", null, ref("idA"), ref("idB")))
        val first = SmartPlaylists.apply(f, lib, paths, emptyMap()).results.single()
        val afterUserRemoval = first.finalIds - a.numericId
        val r = SmartPlaylists.apply(f, lib, paths, mapOf("Top" to afterUserRemoval), mapOf("Top" to first.syncedIds)).results.single()
        assertEquals(listOf(b.numericId), r.finalIds)
    }
    @Test fun newDriveTrackEntersOnNextApply() {
        val f = file(pl("Anitta", SmartRule(SmartRule.ARTISTA, "Anitta")))
        val first = SmartPlaylists.apply(f, lib, paths, emptyMap()).results.single()
        assertEquals(listOf(b.numericId), first.finalIds)
        val novo = ct("idN", "Anitta - Downtown.mp3", "Downtown", "Anitta")
        val second = SmartPlaylists.apply(f, lib + novo, paths, mapOf("Anitta" to first.finalIds), mapOf("Anitta" to first.syncedIds)).results.single()
        assertEquals(listOf(b.numericId, novo.numericId), second.finalIds)
        assertEquals(listOf(novo.numericId), second.addedIds)
    }
    @Test fun existingPlaylistWithSameNameIsUpdatedIgnoringCaseAndAccents() {
        val f = file(pl("Sertanejo Útil", null, ref("idA")))
        val r = SmartPlaylists.apply(f, lib, paths, mapOf("sertanejo util" to listOf(b.numericId))).results.single()
        assertEquals("sertanejo util", r.name)
        assertEquals(listOf(b.numericId, a.numericId), r.finalIds)
    }
    @Test fun emptyPlaylistIsCreatedWithZeroTracks() {
        val f = file(pl("Vazia", null), pl("SoNaoAchadas", null, ref(t = "Nada", a = "Ninguém")))
        val out = SmartPlaylists.apply(f, lib, paths, emptyMap())
        assertEquals(2, out.results.size)
        assertTrue(out.results.all { it.created && it.finalIds.isEmpty() })
        assertEquals(1, out.notFound)
    }
    @Test fun dismissedPlaylistIsNotRecreated() {
        val f = file(pl("Apagada", null, ref("idA")), pl("Outra", null, ref("idB")))
        val out = SmartPlaylists.apply(f, lib, paths, emptyMap(), skip = setOf(SmartMatcher.key("Apagada")))
        assertEquals(listOf("Outra"), out.results.map { it.name })
    }
    @Test fun emptyFileOrLibraryGivesFriendlyText() {
        assertEquals("Nenhuma playlist para atualizar.", SmartPlaylists.describe(SmartPlaylists.apply(file(), lib, paths, emptyMap())))
        assertEquals(0, SmartPlaylists.apply(file(pl("X", null, ref("idA"))), emptyList(), paths, emptyMap()).results.single().finalIds.size)
    }
    @Test fun describePluralsAndReport() {
        val f = file(pl("A", null, ref("idA"), ref(t = "x1"), ref(t = "x2")), pl("B", null, ref("idB")))
        val out = SmartPlaylists.apply(f, lib, paths, emptyMap())
        assertEquals("2 playlists atualizadas, +2 faixas, 2 não encontradas.", SmartPlaylists.describe(out))
        assertEquals("A: x1; x2", SmartPlaylists.unmatchedReport(out))
    }

    // ---------- estado guardado ----------
    @Test fun stateRoundTripsAndToleratesGarbage() {
        val m = mapOf("A" to setOf(1L, -5L, Long.MIN_VALUE + 7), "B" to emptySet<Long>())
        assertEquals(m, SmartState.addedFromJson(SmartState.addedToJson(m)))
        assertEquals(setOf("a", "b"), SmartState.namesFromJson(SmartState.namesToJson(setOf("a", "b"))))
        assertTrue(SmartState.addedFromJson("lixo").isEmpty()); assertTrue(SmartState.addedFromJson(null).isEmpty())
        assertTrue(SmartState.namesFromJson("lixo").isEmpty())
    }
    @Test fun parsesNamedFilesFromDrive() {
        val json = """{"files":[{"id":"abc_1","name":"playlists-inteligentes.json","modifiedTime":"2026-11-15T02:00:00Z","size":"1234"},
          {"id":"bad id!","name":"x"},{"id":"t1","name":"y","trashed":true},5]}"""
        val l = DriveJson.parseNamedFiles(json)
        assertEquals(1, l.size); assertEquals("abc_1", l[0].id); assertEquals(1234L, l[0].size)
    }
}
