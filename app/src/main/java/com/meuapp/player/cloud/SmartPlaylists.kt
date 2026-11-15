package com.meuapp.player.cloud

import org.json.JSONArray
import org.json.JSONObject

/*
 * Playlists inteligentes: lê o arquivo "playlists-inteligentes.json" (formato "toxic-playlists", versão 2),
 * casa cada faixa com a biblioteca da nuvem e aplica as regras. Lógica PURA (sem Android além de org.json),
 * por isso é testável na JVM. Nada aqui escreve no Drive: o app só LÊ.
 */

/** Item de uma regra "lista" (título + artista). */
data class SmartItem(val title: String, val artist: String)

/** Regra de uma playlist. [type] é "artista", "pasta" ou "lista". */
data class SmartRule(val type: String, val name: String = "", val items: List<SmartItem> = emptyList()) {
    companion object { const val ARTISTA = "artista"; const val PASTA = "pasta"; const val LISTA = "lista" }
}

/** Faixa citada no JSON. Qualquer campo pode estar vazio. */
data class SmartTrackRef(val id: String, val fileName: String, val folder: String, val title: String, val artist: String) {
    /** Texto para mostrar ao usuário quando a faixa não foi encontrada. */
    fun label(): String = when {
        title.isNotBlank() && artist.isNotBlank() -> "$artist - $title"
        title.isNotBlank() -> title
        fileName.isNotBlank() -> fileName
        else -> id
    }
}

data class SmartPlaylist(val name: String, val origin: String, val rule: SmartRule?, val tracks: List<SmartTrackRef>)

data class SmartFile(val version: Int, val generated: String, val playlists: List<SmartPlaylist>)

sealed class SmartParse {
    /** [skipped] = playlists ignoradas por estarem sem nome ou repetidas. */
    data class Ok(val file: SmartFile, val skipped: Int) : SmartParse()
    data class Invalid(val reason: String) : SmartParse()
}

/** Leitor tolerante: campos faltando, "id" ausente, versão desconhecida ou arquivo inválido nunca derrubam o app. */
object SmartPlaylistJson {
    const val FORMAT = "toxic-playlists"
    private const val MAX_PLAYLISTS = 5000
    private const val MAX_NAME = 120

    private fun str(o: JSONObject, key: String): String {
        val v = o.opt(key)
        return if (v == null || v == JSONObject.NULL) "" else v.toString().trim()
    }

    fun parse(text: String?): SmartParse {
        val clean = text?.removePrefix("﻿")?.trim()
        if (clean.isNullOrEmpty()) return SmartParse.Invalid("O arquivo está vazio.")
        val root = try { JSONObject(clean) } catch (e: Exception) {
            return SmartParse.Invalid("O arquivo não é um JSON válido.")
        }
        val fmt = str(root, "formato")
        if (fmt.isNotEmpty() && fmt != FORMAT) return SmartParse.Invalid("Este arquivo não é uma lista de playlists do Toxic.")
        val arr = root.optJSONArray("playlists") ?: return SmartParse.Invalid("O arquivo não tem a lista \"playlists\".")
        val out = ArrayList<SmartPlaylist>()
        val seen = HashSet<String>()
        var skipped = 0
        for (i in 0 until minOf(arr.length(), MAX_PLAYLISTS)) {
            val o = arr.optJSONObject(i)
            if (o == null) { skipped++; continue }
            val name = str(o, "nome").take(MAX_NAME)
            if (name.isEmpty() || !seen.add(CloudLibrary.normalize(name))) { skipped++; continue }
            out += SmartPlaylist(name, str(o, "origem"), parseRule(o.optJSONObject("regra")), parseTracks(o.optJSONArray("faixas")))
        }
        return SmartParse.Ok(SmartFile(root.optInt("v", 0), str(root, "gerado"), out), skipped)
    }

    private fun parseRule(o: JSONObject?): SmartRule? {
        if (o == null) return null
        return when (val type = str(o, "tipo").lowercase()) {
            SmartRule.ARTISTA, SmartRule.PASTA -> str(o, "nome").takeIf { it.isNotEmpty() }?.let { SmartRule(type, name = it) }
            SmartRule.LISTA -> {
                val a = o.optJSONArray("itens")
                val items = ArrayList<SmartItem>()
                if (a != null) for (i in 0 until a.length()) {
                    val io = a.optJSONObject(i) ?: continue
                    val t = str(io, "t")
                    if (t.isNotEmpty()) items += SmartItem(t, str(io, "a"))
                }
                SmartRule(type, items = items)
            }
            else -> null        // tipo desconhecido (versão futura): ignora a regra, mantém as faixas
        }
    }

    private fun parseTracks(a: JSONArray?): List<SmartTrackRef> {
        if (a == null) return emptyList()
        val out = ArrayList<SmartTrackRef>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val r = SmartTrackRef(str(o, "id"), str(o, "fn"), str(o, "p"), str(o, "t"), str(o, "a"))
            if (r.id.isNotEmpty() || r.fileName.isNotEmpty() || r.title.isNotEmpty()) out += r
        }
        return out
    }

    /** Escreve no mesmo formato lido (usado para guardar o arquivo no aparelho). */
    fun toJson(f: SmartFile): String {
        val pls = JSONArray()
        for (p in f.playlists) {
            val o = JSONObject().put("nome", p.name).put("origem", p.origin)
            p.rule?.let { r ->
                val ro = JSONObject().put("tipo", r.type)
                if (r.type == SmartRule.LISTA) {
                    val items = JSONArray()
                    r.items.forEach { items.put(JSONObject().put("t", it.title).put("a", it.artist)) }
                    ro.put("itens", items)
                } else ro.put("nome", r.name)
                o.put("regra", ro)
            }
            val fa = JSONArray()
            p.tracks.forEach { fa.put(JSONObject().put("id", it.id).put("fn", it.fileName).put("p", it.folder).put("t", it.title).put("a", it.artist)) }
            o.put("faixas", fa)
            pls.put(o)
        }
        return JSONObject().put("formato", FORMAT).put("v", f.version).put("gerado", f.generated).put("playlists", pls).toString()
    }
}

/** Casamento de faixas do JSON com a biblioteca da nuvem. */
class SmartMatcher(private val library: List<CloudTrack>) {
    private val byId = HashMap<String, CloudTrack>()
    private val byFn = HashMap<String, CloudTrack>()
    private val byTitleArtist = HashMap<String, CloudTrack>()
    private val byTitle = HashMap<String, MutableList<CloudTrack>>()
    /** Possíveis "fontes de artista" de cada música: artista das tags + artistas deduzidos do nome do arquivo. */
    private val artistSources = HashMap<String, List<String>>()

    init {
        for (t in library) {
            byId.putIfAbsent(t.id, t)
            if (t.fileName.isNotBlank()) byFn.putIfAbsent(t.fileName.trim().lowercase(), t)
            val sources = ArrayList<String>()
            if (t.artist.isNotBlank() && t.artist != CloudTrack.UNKNOWN_ARTIST) sources += t.artist
            val pairs = ArrayList<Pair<String, String>>()    // (título, artista) possíveis
            pairs += t.title to (sources.firstOrNull() ?: "")
            for ((title, artist) in namePairs(t.fileName)) { pairs += title to artist; if (artist.isNotEmpty()) sources += artist }
            artistSources[t.id] = sources
            for ((title, artist) in pairs) {
                val tk = key(title)
                if (tk.isEmpty()) continue
                byTitleArtist.putIfAbsent(tk + "|" + key(artist), t)
                val l = byTitle.getOrPut(tk) { ArrayList() }
                if (l.none { it.id == t.id }) l += t
            }
        }
    }

    fun byId(id: String): CloudTrack? = if (id.isBlank()) null else byId[id]

    /** Ordem: 1º id do Drive; 2º nome do arquivo (sem diferenciar maiúsculas); 3º título + artista normalizados. */
    fun match(ref: SmartTrackRef): CloudTrack? {
        byId(ref.id)?.let { return it }
        if (ref.fileName.isNotBlank()) byFn[ref.fileName.trim().lowercase()]?.let { return it }
        return byTitleArtist(ref.title, ref.artist)
    }

    fun byTitleArtist(title: String, artist: String): CloudTrack? {
        val tk = key(title)
        if (tk.isEmpty()) return null
        byTitleArtist[tk + "|" + key(artist)]?.let { return it }
        val candidates = byTitle[tk] ?: return null
        val want = artistWords(artist)
        if (want.isEmpty()) return candidates.singleOrNull()     // sem artista no JSON: só aceita se não houver dúvida
        return candidates.firstOrNull { c ->
            artistSources[c.id].orEmpty().any { src ->
                val have = artistWords(src)
                have.isNotEmpty() && (have.containsAll(want) || want.containsAll(have))
            }
        }
    }

    /** Regra "artista": TODAS as palavras do nome aparecem no artista (ignorando "e", "&", "x", "and"). */
    fun byArtistRule(name: String): List<CloudTrack> {
        val want = artistWords(name)
        if (want.isEmpty()) return emptyList()
        return library.filter { t -> artistSources[t.id].orEmpty().any { artistWords(it).containsAll(want) } }
    }

    /** Regra "pasta": pasta do Drive cujo último trecho do caminho tem esse nome. */
    fun byFolderRule(name: String, paths: Map<String, String>): List<CloudTrack> {
        val want = key(name.substringAfterLast('/'))
        if (want.isEmpty()) return emptyList()
        return library.filter { t -> paths[t.parentId]?.let { key(it.substringAfterLast('/')) == want } == true }
    }

    /** Regra "lista": casa cada item por título + artista. */
    fun byListRule(items: List<SmartItem>): List<CloudTrack> = items.mapNotNull { byTitleArtist(it.title, it.artist) }

    companion object {
        private val STOP = setOf("e", "x", "and")

        /** Minúsculas, sem acentos (CloudLibrary.normalize), sem apóstrofos e sem pontuação. */
        fun key(s: String): String =
            CloudLibrary.normalize(s).replace(Regex("['’`]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        /** Palavras do nome de um artista, sem "e", "&", "x" e "and". */
        fun artistWords(s: String): Set<String> = key(s).split(' ').filter { it.isNotEmpty() && it !in STOP }.toSet()

        /** "Cantor - Música" ou "Música - Cantor": as duas ordens possíveis, como (título, artista). */
        fun namePairs(fileName: String): List<Pair<String, String>> {
            var base = fileName.substringBeforeLast('.', fileName).trim().replace('_', ' ')
            base = base.replace(Regex("^\\(?\\d{1,3}\\)?\\s*[-.)]*\\s+"), "").trim()
            val parts = base.split(" - ")
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
                listOf(parts[1].trim() to parts[0].trim(), parts[0].trim() to parts[1].trim())
            else if (base.isNotBlank()) listOf(base to "") else emptyList()
        }
    }
}

/** Resultado de uma playlist. [syncedIds] = tudo o que a sincronização já ofereceu a esta playlist (para respeitar remoções do usuário). */
data class SmartResult(
    val name: String, val origin: String, val created: Boolean,
    val finalIds: List<Long>, val addedIds: List<Long>, val syncedIds: Set<Long>,
    val matched: Int, val unmatched: List<String>
)

data class SmartOutcome(val results: List<SmartResult>) {
    val updated: Int get() = results.count { it.created || it.addedIds.isNotEmpty() }
    val added: Int get() = results.sumOf { it.addedIds.size }
    val notFound: Int get() = results.sumOf { it.unmatched.size }
}

object SmartPlaylists {
    /**
     * Aplica o arquivo à biblioteca. NUNCA remove faixa: a lista final é "o que já estava" + "o que é novo".
     * - [current]: playlists atuais do app (nome -> ids numéricos). Faixas colocadas à mão ficam.
     * - [synced]: ids que cada playlist já recebeu em sincronizações anteriores. Se o usuário removeu uma delas, não volta.
     * - [skip]: nomes (normalizados) de playlists que o usuário apagou: não são recriadas.
     */
    fun apply(
        file: SmartFile, library: List<CloudTrack>, paths: Map<String, String>,
        current: Map<String, List<Long>>, synced: Map<String, Set<Long>> = emptyMap(), skip: Set<String> = emptySet()
    ): SmartOutcome {
        val matcher = SmartMatcher(library)
        val currentByName = current.entries.associate { SmartMatcher.key(it.key) to it }
        val results = ArrayList<SmartResult>()
        for (p in file.playlists) {
            val nk = SmartMatcher.key(p.name)
            if (nk in skip) continue
            val existingEntry = currentByName[nk]
            val name = existingEntry?.key ?: p.name
            val existing = existingEntry?.value.orEmpty()
            val created = existingEntry == null

            val matched = LinkedHashMap<String, CloudTrack>()
            val unmatched = LinkedHashSet<String>()
            for (ref in p.tracks) {
                val t = matcher.match(ref)
                if (t != null) matched.putIfAbsent(t.id, t) else unmatched += ref.label()
            }
            val rule = p.rule
            val ruled: List<CloudTrack> = when {
                rule == null -> emptyList()
                rule.type == SmartRule.ARTISTA -> matcher.byArtistRule(rule.name)
                rule.type == SmartRule.PASTA -> matcher.byFolderRule(rule.name, paths)
                rule.type == SmartRule.LISTA -> matcher.byListRule(rule.items)
                else -> emptyList()
            }
            ruled.forEach { matched.putIfAbsent(it.id, it) }

            val wanted = matched.values.map { it.numericId }
            val have = existing.toHashSet()
            val before = if (created) emptySet() else synced[name].orEmpty()
            val toAdd = wanted.filter { it !in have && it !in before }.distinct()
            results += SmartResult(
                name, p.origin, created, existing + toAdd, toAdd, before + toAdd,
                matched.size, unmatched.toList()
            )
        }
        return SmartOutcome(results)
    }

    /** Texto simples para o usuário, ex.: "3 playlists atualizadas, +27 faixas, 4 não encontradas." */
    fun describe(o: SmartOutcome): String {
        if (o.results.isEmpty()) return "Nenhuma playlist para atualizar."
        if (o.updated == 0 && o.notFound == 0) return "Playlists do Drive já estão em dia (${o.results.size})."
        val pl = if (o.updated == 1) "1 playlist atualizada" else "${o.updated} playlists atualizadas"
        val fx = "+${o.added} ${if (o.added == 1) "faixa" else "faixas"}"
        val nf = if (o.notFound == 1) "1 não encontrada" else "${o.notFound} não encontradas"
        return "$pl, $fx, $nf."
    }

    /** Lista das faixas que não casaram, agrupadas por playlist (para a tela mostrar depois). */
    fun unmatchedReport(o: SmartOutcome, maxPerPlaylist: Int = 20): String =
        o.results.filter { it.unmatched.isNotEmpty() }.joinToString("\n") { r ->
            val shown = r.unmatched.take(maxPerPlaylist).joinToString("; ")
            val more = if (r.unmatched.size > maxPerPlaylist) " (e mais ${r.unmatched.size - maxPerPlaylist})" else ""
            "${r.name}: $shown$more"
        }
}

/** Guarda/lê o estado auxiliar (ids já oferecidos e playlists apagadas) como JSON de texto. */
object SmartState {
    fun addedToJson(m: Map<String, Set<Long>>): String {
        val o = JSONObject()
        m.forEach { (k, v) -> val a = JSONArray(); v.forEach { a.put(it) }; o.put(k, a) }
        return o.toString()
    }

    fun addedFromJson(text: String?): Map<String, Set<Long>> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(text)
            val out = HashMap<String, Set<Long>>()
            val ks = o.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val a = o.optJSONArray(k) ?: continue
                out[k] = HashSet<Long>().also { s -> for (i in 0 until a.length()) s += a.optLong(i) }
            }
            out
        } catch (e: Exception) { emptyMap() }
    }

    fun namesToJson(s: Set<String>): String { val a = JSONArray(); s.forEach { a.put(it) }; return a.toString() }

    fun namesFromJson(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return try {
            val a = JSONArray(text)
            HashSet<String>().also { s -> for (i in 0 until a.length()) a.optString(i, "").takeIf { it.isNotEmpty() }?.let { s += it } }
        } catch (e: Exception) { emptySet() }
    }
}
