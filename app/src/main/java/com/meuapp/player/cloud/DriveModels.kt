package com.meuapp.player.cloud

import org.json.JSONObject

/** Arquivo de áudio como listado pelo Drive (sem tags: o Drive não expõe metadados de áudio). */
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedTime: String,
    val md5: String?,
    val format: CloudFormat,
    val createdTime: String = ""
)

/** Uma entrada do feed de mudanças (changes.list). [file] é null quando o arquivo foi removido ou não é áudio. */
data class DriveChange(val fileId: String, val removed: Boolean, val file: DriveFile?)

data class ChangesPage(val changes: List<DriveChange>, val nextPageToken: String?, val newStartPageToken: String?)
data class FilesPage(val files: List<DriveFile>, val nextPageToken: String?)
data class DriveAbout(val displayName: String, val email: String, val usageBytes: Long, val limitBytes: Long)

/** Interpretação defensiva das respostas JSON. Entradas inválidas são ignoradas, nunca derrubam o app. */
object DriveJson {
    fun parseFile(o: JSONObject?): DriveFile? {
        if (o == null) return null
        val id = o.optString("id").takeIf { it.isNotBlank() && it.length <= 200 && ID_REGEX.matches(it) } ?: return null
        if (o.optBoolean("trashed", false)) return null
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
        val mime = o.optString("mimeType")
        if (mime == "application/vnd.google-apps.folder") return null
        val fmt = CloudFormat.detect(name, mime) ?: return null
        // O Drive devolve "size" como texto; ausente em arquivos nativos do Google (que não são áudio).
        val size = o.optString("size").toLongOrNull() ?: return null
        if (size <= 0L) return null
        return DriveFile(id, name, mime, size, o.optString("modifiedTime"), o.optString("md5Checksum").ifBlank { null }, fmt, o.optString("createdTime"))
    }

    fun parseFilesPage(text: String): FilesPage {
        val j = JSONObject(text)
        val arr = j.optJSONArray("files")
        val out = ArrayList<DriveFile>()
        if (arr != null) for (i in 0 until arr.length()) parseFile(arr.optJSONObject(i))?.let { out += it }
        return FilesPage(out, j.optString("nextPageToken").ifBlank { null })
    }

    fun parseChangesPage(text: String): ChangesPage {
        val j = JSONObject(text)
        val arr = j.optJSONArray("changes")
        val out = ArrayList<DriveChange>()
        if (arr != null) for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val id = c.optString("fileId").takeIf { it.isNotBlank() && ID_REGEX.matches(it) } ?: continue
            val removed = c.optBoolean("removed", false)
            // Arquivo na lixeira conta como removido; parseFile devolve null para trashed e para não-áudio.
            out += DriveChange(id, removed, if (removed) null else parseFile(c.optJSONObject("file")))
        }
        return ChangesPage(out, j.optString("nextPageToken").ifBlank { null }, j.optString("newStartPageToken").ifBlank { null })
    }

    fun parseAbout(text: String): DriveAbout {
        val j = JSONObject(text)
        val u = j.optJSONObject("user")
        val q = j.optJSONObject("storageQuota")
        return DriveAbout(
            u?.optString("displayName").orEmpty(), u?.optString("emailAddress").orEmpty(),
            q?.optString("usage")?.toLongOrNull() ?: 0L,
            q?.optString("limit")?.toLongOrNull() ?: 0L   // 0 = ilimitado/desconhecido
        )
    }

    /** Extrai apenas o campo "reason" do erro JSON do Google. */
    fun parseErrorReason(text: String): String? = try {
        JSONObject(text).optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
            ?: JSONObject(text).optString("error").ifBlank { null }
    } catch (e: Exception) { null }

    private val ID_REGEX = Regex("^[A-Za-z0-9_-]+$")
}
