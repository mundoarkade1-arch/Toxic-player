package com.meuapp.player.cloud

import org.json.JSONArray
import org.json.JSONObject

/** Estado da leitura de metadados de uma música. */
object MetaState { const val PENDING = 0; const val DONE = 1; const val FAILED = 2 }

/** Música da nuvem como guardada no índice local. */
data class CloudTrack(
    val id: String,              // fileId do Drive
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val format: CloudFormat,
    val modifiedTime: String,
    val md5: String?,
    val hasCover: Boolean,
    val metaState: Int,
    val genre: String = "",          // "" = sem gênero nas tags
    val addedMs: Long = 0L            // quando o arquivo entrou no Drive (0 = desconhecido)
) {
    /** Id numérico estável e negativo: nunca colide com os ids positivos do MediaStore e serve para playlists/favoritas. */
    val numericId: Long get() = numericIdFor(id)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("fn", fileName).put("t", title).put("a", artist).put("al", album)
        .put("d", durationMs).put("s", size).put("f", format.name).put("m", modifiedTime)
        .put("h", md5 ?: JSONObject.NULL).put("c", hasCover).put("ms", metaState)
        .put("g", genre).put("ad", addedMs)

    companion object {
        const val UNKNOWN_ARTIST = "Artista desconhecido"
        const val UNKNOWN_ALBUM = "Álbum desconhecido"

        fun numericIdFor(fileId: String): Long {
            var h = -0x340d631b7bdddcdbL            // FNV-1a 64 bits
            for (ch in fileId) { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
            return h or Long.MIN_VALUE               // bit de sinal sempre ligado => negativo
        }

        fun fromJson(o: JSONObject): CloudTrack? {
            val id = o.optString("id").ifBlank { return null }
            val fmt = CloudFormat.fromName(o.optString("f")) ?: return null
            return CloudTrack(
                id, o.optString("fn"), o.optString("t"), o.optString("a", UNKNOWN_ARTIST), o.optString("al", UNKNOWN_ALBUM),
                o.optLong("d"), o.optLong("s"), fmt, o.optString("m"),
                if (o.isNull("h")) null else o.optString("h").ifBlank { null }, o.optBoolean("c"),
                // Índice antigo (sem gênero): relê as tags uma vez para descobrir o gênero.
                if (o.has("g")) o.optInt("ms") else MetaState.PENDING,
                o.optString("g"), o.optLong("ad")
            )
        }

        /** Registro novo a partir da listagem do Drive, com título/artista deduzidos do nome do arquivo. */
        fun fromDrive(f: DriveFile): CloudTrack {
            val p = com.meuapp.player.cloud.tags.NameParser.parse(f.name)
            return CloudTrack(f.id, f.name, p.title, p.artist ?: UNKNOWN_ARTIST, UNKNOWN_ALBUM, 0L, f.size, f.format,
                f.modifiedTime, f.md5, false, MetaState.PENDING, "", addedOf(f))
        }

        /** Data em que o arquivo entrou no Drive (createdTime; se faltar, modifiedTime). 0 se não der para ler. */
        fun addedOf(f: DriveFile): Long {
            fun parse(s: String) = try { java.time.Instant.parse(s).toEpochMilli() } catch (e: Exception) { 0L }
            return parse(f.createdTime).takeIf { it > 0 } ?: parse(f.modifiedTime)
        }

        fun listToJson(list: Collection<CloudTrack>): String {
            val a = JSONArray(); list.forEach { a.put(it.toJson()) }
            return JSONObject().put("v", 1).put("tracks", a).toString()
        }

        fun listFromJson(text: String): List<CloudTrack> {
            val arr = JSONObject(text).optJSONArray("tracks") ?: return emptyList()
            val out = ArrayList<CloudTrack>(arr.length())
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { fromJson(it) }?.let { out += it }
            return out
        }
    }
}
