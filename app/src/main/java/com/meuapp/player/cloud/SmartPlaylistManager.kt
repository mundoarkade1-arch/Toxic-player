package com.meuapp.player.cloud

import android.content.Context
import com.meuapp.player.FAVORITES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Aviso para a tela: [text] é a frase principal; [report] lista as faixas que não casaram (pode ser vazio). */
data class SmartInfo(val text: String, val report: String = "")

/**
 * Playlists do app que vêm do Drive. DESDE 2026-11-22 só as PASTAS do Drive viram playlist (ver [FolderPlaylists]).
 * Os arquivos "playlists-inteligentes.json" (site) e "playlists-motor.json" (motor) NÃO são mais lidos: eles criavam
 * playlists de 1 ou 2 músicas. As playlists que eles já tinham criado são apagadas uma vez ([cleanupOldSmart]).
 * SEM TELA: só lógica.
 * Todas as funções públicas devem ser chamadas na thread principal (as playlists do app são estado do Compose).
 * O app só LÊ o Drive. Nada aqui apaga ou escreve no Drive.
 *
 * [library] e [paths] dão a biblioteca da nuvem; [playlists] dá as playlists atuais (nome -> ids);
 * [commit] grava as playlists novas/atualizadas; [notify] mostra uma frase ao usuário.
 */
class SmartPlaylistManager(
    ctx: Context,
    private val api: DriveApi,
    private val scope: CoroutineScope,
    private val library: () -> List<CloudTrack>,
    private val paths: () -> Map<String, String>,
    private val playlists: () -> Map<String, List<Long>>,
    private val commit: (Map<String, List<Long>>) -> Unit,
    private val remove: (Set<String>) -> Unit,
    private val notify: (SmartInfo) -> Unit
) {
    /** Entrega o aviso à tela junto com a lista das faixas não encontradas. */
    private fun say(text: String) = notify(SmartInfo(text, lastReport))

    private val prefs = ctx.applicationContext.getSharedPreferences("toxic_smart", Context.MODE_PRIVATE)
    @Volatile private var fetching = false

    /** Último resultado, para a tela mostrar depois (lista das faixas que não casaram). */
    @Volatile var lastReport: String = ""; private set

    // ---------- estado guardado ----------
    private fun storedFile(): SmartFile? =
        (SmartPlaylistJson.parse(prefs.getString("file", null)) as? SmartParse.Ok)?.file

    private fun saveFile(f: SmartFile, driveModified: String) {
        prefs.edit().putString("file", SmartPlaylistJson.toJson(f)).putString("gerado", f.generated)
            .putString("modified", driveModified).apply()
    }

    private var originsStamp = 0
    private var originsCache: Map<String, String> = emptyMap()

    /** Nomes (pela chave de SmartMatcher) das playlists que vêm das pastas do Drive. Atualizado a cada vez que as pastas são lidas. */
    @Volatile private var folderKeys: Set<String> = emptySet()

    /** Nome da playlist (pela chave de SmartMatcher) -> origem gravada no arquivo ("Pasta", "Cantor", "Motor"...). Só leitura. */
    fun origins(): Map<String, String> {
        val raw = prefs.getString("file", null)
        val folders = folderKeys
        if (raw == null) return folders.associateWith { FolderPlaylists.ORIGIN }
        val h = raw.hashCode()
        if (h != originsStamp) {
            originsStamp = h
            originsCache = storedFile()?.playlists?.associate { SmartMatcher.key(it.name) to it.origin } ?: emptyMap()
        }
        if (folders.isEmpty()) return originsCache
        val out = HashMap<String, String>(originsCache)
        for (k in folders) out.putIfAbsent(k, FolderPlaylists.ORIGIN)
        return out
    }

    private fun added() = SmartState.addedFromJson(prefs.getString("added", null))
    private fun dismissed() = SmartState.namesFromJson(prefs.getString("dismissed", null))

    /** O usuário apagou esta playlist: não recriar sozinha. */
    fun forget(name: String) {
        val key = SmartMatcher.key(name)
        val isSmart = key in folderKeys || storedFile()?.playlists?.any { SmartMatcher.key(it.name) == key } == true
        if (!isSmart) return
        prefs.edit().putString("dismissed", SmartState.namesToJson(dismissed() + key)).apply()
    }

    // ---------- pastas do Drive ----------
    /** Monta as playlists a partir das pastas do Drive (só elas). Só conta, sem tela. Pode rodar em segundo plano. */
    private fun withFolders(lib: List<CloudTrack>, pathsNow: Map<String, String>): SmartFile? {
        val folderFile = FolderPlaylists.build(lib, pathsNow)
        folderKeys = folderFile?.playlists?.map { SmartMatcher.key(it.name) }?.toSet() ?: emptySet()
        return folderFile
    }

    /**
     * Limpeza automática: apaga do app as playlists que o PRÓPRIO app criou antes e que hoje não valem mais
     * (as do arquivo do site/motor e as de pastas pequenas demais, com menos de [FolderPlaylists.MIN_TRACKS] músicas).
     * Só apaga se TUDO que está na playlist foi posto pelo app (se o usuário pôs uma música à mão, ela fica).
     * Playlist de pasta que vale hoje nunca é apagada. Nada é apagado no Drive. Na thread principal.
     */
    private fun cleanupOldSmart(pathsNow: Map<String, String>) {
        if (pathsNow.isEmpty()) return            // pastas ainda não carregadas: espera, para não apagar à toa
        val oldKeys = HashSet<String>()
        storedFile()?.playlists?.forEach { oldKeys += SmartMatcher.key(it.name) }
        oldKeys += SmartState.namesFromJson(prefs.getString("made", null))
        oldKeys -= folderKeys
        if (oldKeys.isNotEmpty()) {
            val offered = added()
            val drop = HashSet<String>()
            for ((name, ids) in playlists()) {
                if (name == FAVORITES || SmartMatcher.key(name) !in oldKeys) continue
                val mine = offered[name].orEmpty()
                if (ids.all { it in mine }) drop += name
            }
            if (drop.isNotEmpty()) remove(drop)
        }
        prefs.edit().remove("file").remove("gerado").remove("modified").remove("sig")
            .putString("made", SmartState.namesToJson(folderKeys)).apply()
    }

    // ---------- aplicar ----------
    /** Assinatura barata da biblioteca + arquivo: se não mudou desde a última vez, não precisa refazer a conta. */
    private fun signature(lib: List<CloudTrack>, file: SmartFile, pathsNow: Map<String, String>): String {
        var h = 1L
        for (t in lib) h = h * 31 + t.id.hashCode() + t.parentId.hashCode() * 7L + t.title.hashCode() * 13L + t.artist.hashCode() * 17L
        return "${lib.size}:$h:${pathsNow.hashCode()}:${file.generated}:${file.playlists.size}"
    }

    /** Grava o resultado (thread principal). Junta com as playlists de AGORA, para nunca pisar numa edição feita no meio do caminho. */
    private fun finish(out: SmartOutcome, synced: Map<String, Set<Long>>, ignoreDismissed: Boolean) {
        val nowAll = playlists()
        val changed = LinkedHashMap<String, List<Long>>()
        val newSynced = HashMap(synced)
        for (r in out.results) {
            if (r.created || r.addedIds.isNotEmpty()) {
                val now = nowAll[r.name].orEmpty()
                val nowSet = now.toHashSet()
                changed[r.name] = now + r.addedIds.filter { it !in nowSet }
            }
            newSynced[r.name] = r.syncedIds
        }
        if (changed.isNotEmpty()) commit(changed)
        val ed = prefs.edit().putString("added", SmartState.addedToJson(newSynced))
        if (ignoreDismissed) ed.putString("dismissed", SmartState.namesToJson(emptySet()))
        ed.apply()
        lastReport = SmartPlaylists.unmatchedReport(out)
    }

    /** Aplica [file] na thread principal (usado só ao importar um arquivo à mão). Devolve null se a biblioteca ainda está vazia. */
    private fun applyFile(ignoreDismissed: Boolean): SmartOutcome? {
        val lib = library()
        if (lib.isEmpty()) return null
        val pathsNow = paths()
        val file = withFolders(lib, pathsNow)
        cleanupOldSmart(pathsNow)
        if (file == null) return SmartOutcome(emptyList())
        val skip = if (ignoreDismissed) emptySet() else dismissed()
        val synced = added()
        val out = SmartPlaylists.apply(file, lib, pathsNow, playlists(), synced, skip)
        finish(out, synced, ignoreDismissed)
        prefs.edit().putString("sig", signature(lib, file, pathsNow)).apply()
        return out
    }

    /**
     * Aplica sem travar a tela: a conta pesada roda em segundo plano e só o resultado volta à thread principal.
     * Se nada mudou desde a última vez (e não é [force]), pula tudo e devolve um resultado vazio.
     */
    private suspend fun applyAsync(ignoreDismissed: Boolean, force: Boolean): SmartOutcome? {
        val lib = library()
        if (lib.isEmpty()) return null
        val pathsNow = paths()
        // Só as pastas do Drive viram playlist; a conta roda em segundo plano.
        val file = withContext(Dispatchers.Default) { withFolders(lib, pathsNow) }
        cleanupOldSmart(pathsNow)
        if (file == null) return SmartOutcome(emptyList())
        val sig = signature(lib, file, pathsNow)
        if (!force && sig == prefs.getString("sig", null)) return SmartOutcome(emptyList())
        val currentNow = playlists()
        val skip = if (ignoreDismissed) emptySet() else dismissed()
        val synced = added()
        val out = withContext(Dispatchers.Default) { SmartPlaylists.apply(file, lib, pathsNow, currentNow, synced, skip) }
        finish(out, synced, ignoreDismissed)
        prefs.edit().putString("sig", sig).apply()
        return out
    }

    /** Reaplica as regras guardadas (sem internet), em segundo plano. Ao abrir o app e depois de cada sincronização. */
    fun reapplyStored(announce: Boolean = false) {
        scope.launch(Dispatchers.Main) {
            try {
                val out = applyAsync(ignoreDismissed = false, force = announce) ?: return@launch
                if (announce || out.updated > 0) say(SmartPlaylists.describe(out))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { /* nunca derruba o app */ }
        }
    }

    /** Importar arquivo de playlists foi desligado: agora só as pastas do Drive viram playlist. */
    @Suppress("UNUSED_PARAMETER")
    fun importText(text: String?): String =
        "As playlists agora vêm só das pastas do Drive. Esse arquivo não é mais usado."

    // ---------- depois de sincronizar ----------
    /** Não lê mais o Drive: só refaz as playlists das pastas. [manual] = o usuário pediu, então avisa o resultado. */
    fun syncFromDrive(manual: Boolean = false) { reapplyStored(announce = manual) }

    /** Chamado pelo CloudManager (thread principal) depois de sincronizar a biblioteca. */
    fun onLibrarySynced() = syncFromDrive(manual = false)
}
