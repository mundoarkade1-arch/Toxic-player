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

    fun clear() { file.delete(); File(dir, "index.json.tmp").delete() }
}
