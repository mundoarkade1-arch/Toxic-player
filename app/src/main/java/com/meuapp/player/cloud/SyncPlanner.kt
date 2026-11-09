package com.meuapp.player.cloud

/** Resultado da comparação entre o índice local e o que o Drive informa. */
data class SyncPlan(val added: List<DriveFile>, val modified: List<DriveFile>, val removedIds: List<String>, val unchanged: Int)

/** Lógica pura de sincronização (sem Android), coberta por testes unitários. */
object SyncPlanner {
    /** O arquivo mudou se o conteúdo (md5), a data de modificação ou o tamanho forem diferentes. */
    fun isModified(local: CloudTrack, remote: DriveFile): Boolean =
        local.size != remote.size ||
            (remote.md5 != null && local.md5 != null && !remote.md5.equals(local.md5, true)) ||
            (remote.modifiedTime.isNotBlank() && local.modifiedTime.isNotBlank() && remote.modifiedTime != local.modifiedTime)

    /** Varredura completa: compara tudo. Arquivos que sumiram da listagem são considerados removidos. */
    fun planFull(local: Map<String, CloudTrack>, remote: List<DriveFile>): SyncPlan {
        val seen = HashSet<String>(remote.size)
        val added = ArrayList<DriveFile>(); val modified = ArrayList<DriveFile>()
        var same = 0
        for (r in remote) {
            if (!seen.add(r.id)) continue
            val l = local[r.id]
            when {
                l == null -> added += r
                isModified(l, r) -> modified += r
                else -> same++
            }
        }
        val removed = local.keys.filter { it !in seen }
        return SyncPlan(added, modified, removed, same)
    }

    /** Sincronização incremental a partir do feed de mudanças. */
    fun planChanges(local: Map<String, CloudTrack>, changes: List<DriveChange>): SyncPlan {
        // Só vale a última mudança de cada arquivo.
        val last = LinkedHashMap<String, DriveChange>()
        for (c in changes) last[c.fileId] = c
        val added = ArrayList<DriveFile>(); val modified = ArrayList<DriveFile>(); val removed = ArrayList<String>()
        var same = 0
        for (c in last.values) {
            val l = local[c.fileId]
            val f = c.file
            when {
                f == null -> if (l != null) removed += c.fileId      // removido, na lixeira ou deixou de ser áudio
                l == null -> added += f
                isModified(l, f) -> modified += f
                else -> same++
            }
        }
        return SyncPlan(added, modified, removed, same)
    }
}
