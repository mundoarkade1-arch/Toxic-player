package com.meuapp.player.cloud

import java.io.File

/** Persistência do índice da biblioteca (um JSON, gravado de forma atômica: arquivo temporário + renomear). */
class CloudStore(private val dir: File) {
    private val file get() = File(dir, "index.json")

    fun load(): List<CloudTrack> = try {
        if (!file.exists()) emptyList() else CloudTrack.listFromJson(file.readText())
    } catch (e: Exception) {
        emptyList()      // índice corrompido: será reconstruído na próxima sincronização
    }

    fun save(list: Collection<CloudTrack>) {
        dir.mkdirs()
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(CloudTrack.listToJson(list))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private val foldersFile get() = File(dir, "folders.json")

    fun loadFolders(): List<DriveFolder> = try {
        if (!foldersFile.exists()) emptyList() else {
            val a = org.json.JSONArray(foldersFile.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .map { DriveFolder(it.optString("i"), it.optString("n"), it.optString("p")) }
        }
    } catch (e: Exception) { emptyList() }

    fun saveFolders(list: Collection<DriveFolder>) {
        dir.mkdirs()
        val a = org.json.JSONArray()
        list.forEach { a.put(org.json.JSONObject().put("i", it.id).put("n", it.name).put("p", it.parentId)) }
        val tmp = File(dir, "folders.json.tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(foldersFile)) { foldersFile.delete(); tmp.renameTo(foldersFile) }
    }

    fun clear() { file.delete(); foldersFile.delete(); File(dir, "index.json.tmp").delete() }
}
