package com.meuapp.player.cloud

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Aviso para a tela: [text] é a frase principal; [report] lista as faixas que não casaram (pode ser vazio). */
data class SmartInfo(val text: String, val report: String = "")

/**
 * Liga os arquivos "playlists-inteligentes.json" (site) e "playlists-motor.json" (motor do PC) do Drive às playlists do app.
 * Os dois são juntados por [SmartMerge] num arquivo só. SEM TELA: só lógica.
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
    /** Junta as playlists vindas das pastas do Drive ao arquivo [stored] (que pode não existir). Só conta, sem tela. Pode rodar em segundo plano. */
    private fun withFolders(stored: SmartFile?, lib: List<CloudTrack>, pathsNow: Map<String, String>): SmartFile? {
        val folderFile = FolderPlaylists.build(lib, pathsNow)
        folderKeys = folderFile?.playlists?.map { SmartMatcher.key(it.name) }?.toSet() ?: emptySet()
        return SmartMerge.merge(stored, folderFile)
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
    private fun applyFile(fileIn: SmartFile, ignoreDismissed: Boolean): SmartOutcome? {
        val lib = library()
        if (lib.isEmpty()) return null
        val pathsNow = paths()
        val file = withFolders(fileIn, lib, pathsNow) ?: fileIn
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
    private suspend fun applyAsync(fileIn: SmartFile?, ignoreDismissed: Boolean, force: Boolean): SmartOutcome? {
        val lib = library()
        if (lib.isEmpty()) return null
        val pathsNow = paths()
        // Junta o arquivo do Drive (se existir) com as pastas do Drive; a conta roda em segundo plano.
        val file = withContext(Dispatchers.Default) { withFolders(fileIn, lib, pathsNow) } ?: return null
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
                val f = withContext(Dispatchers.Default) { storedFile() }     // pode ser null: as pastas valem mesmo sem arquivo
                val out = applyAsync(f, ignoreDismissed = false, force = announce) ?: return@launch
                if (announce || out.updated > 0) say(SmartPlaylists.describe(out))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { /* nunca derruba o app */ }
        }
    }

    /** Importa um arquivo .json escolhido pelo usuário. Devolve uma frase simples. */
    fun importText(text: String?): String = try {
        when (val p = SmartPlaylistJson.parse(text)) {
            is SmartParse.Invalid -> { lastReport = ""; "Não consegui ler este arquivo de playlists. ${p.reason}" }
            is SmartParse.Ok -> {
                saveFile(p.file, driveModified = "")
                val out = applyFile(p.file, ignoreDismissed = true)
                if (out == null) "Playlists guardadas. Elas entram quando a biblioteca da nuvem estiver sincronizada."
                else SmartPlaylists.describe(out)
            }
        }
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { "Não consegui aplicar as playlists. Tente de novo." }

    // ---------- leitura direta do Drive ----------
    /**
     * Depois de cada sincronização: baixa o JSON do Drive (se mudou), guarda e aplica; sempre reaplica as regras guardadas.
     * Chamado automaticamente; [manual] = o usuário pediu, então avisa também quando não há novidade ou dá erro.
     */
    fun syncFromDrive(manual: Boolean = false) {
        if (fetching) return
        fetching = true
        scope.launch(Dispatchers.IO) {
            var parsed: SmartParse.Ok? = null
            var modified = ""
            var problem: String? = null
            var loud = false        // erro que vale avisar mesmo no modo automático
            var noFile = false      // não existe arquivo de playlists no Drive (as pastas ainda valem)
            try {
                val meta = api.findPlaylistsFile()
                // O arquivo do motor é opcional: se der erro ao procurar, segue só com o do site.
                val motorMeta = try { api.findMotorPlaylistsFile() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                val stamp = "${meta?.modifiedTime.orEmpty()}|${motorMeta?.modifiedTime.orEmpty()}"
                if (meta == null && motorMeta == null) {
                    problem = "Não achei o arquivo de playlists no Drive (pasta ${CloudConfig.PLAYLISTS_FOLDER}). Abra o site e toque em Atualizar tudo."
                    noFile = true
                } else if (!manual && stamp != "|" && stamp == prefs.getString("modified", "")) {
                    // Arquivos iguais aos que já foram lidos: não baixa de novo.
                } else {
                    modified = stamp
                    var site: SmartFile? = null
                    var siteBad = false
                    if (meta != null) {
                        when (val p = SmartPlaylistJson.parse(api.readTextFile(meta.id))) {
                            is SmartParse.Ok -> site = p.file
                            is SmartParse.Invalid -> siteBad = true
                        }
                    }
                    var motor: SmartFile? = null
                    if (motorMeta != null) {
                        try {
                            val pm = SmartPlaylistJson.parse(api.readTextFile(motorMeta.id))
                            if (pm is SmartParse.Ok) motor = pm.file
                            else modified = ""      // arquivo do motor com problema: tenta de novo na próxima vez
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { modified = "" }
                    }
                    if (siteBad) {
                        problem = "O arquivo de playlists no Drive está com problema. Abra o site e toque em Atualizar tudo de novo."
                        loud = true
                    } else {
                        val merged = SmartMerge.merge(site, motor)
                        if (merged != null) parsed = SmartParse.Ok(merged, 0)
                        else problem = "O arquivo de playlists do motor está com problema. Rode o motor no PC de novo."
                    }
                }
            } catch (e: CancellationException) { fetching = false; throw e
            } catch (e: Exception) {
                val kind = CloudErrors.classify(e)
                problem = if (kind == CloudErrorKind.UNKNOWN) "Não consegui ler as playlists do Drive." else kind.message
                if (kind == CloudErrorKind.NO_INTERNET && !manual) problem = null     // sem rede no automático: silêncio, usa o que já tem
            }
            val msg = problem
            val loudNow = loud
            val noFileNow = noFile
            val newFile = parsed?.file
            val driveModified = modified
            withContext(Dispatchers.Main) {
                try {
                    if (newFile != null) saveFile(newFile, driveModified)
                    val file = newFile ?: storedFile()
                    val out = applyAsync(file, ignoreDismissed = false, force = manual)
                    // Sem arquivo no Drive mas com playlists de pasta: não precisa reclamar do arquivo.
                    val showProblem = msg != null && (loudNow || (manual && !(noFileNow && folderKeys.isNotEmpty())))
                    if (showProblem && msg != null) { lastReport = ""; say(msg) }
                    else if (out != null && (manual || out.updated > 0)) say(SmartPlaylists.describe(out))
                    else if (out == null && manual) { lastReport = ""; say("A biblioteca da nuvem ainda está vazia. Sincronize a Cloud e tente de novo.") }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { /* nunca derruba o app */
                } finally { fetching = false }
            }
        }
    }

    /** Chamado pelo CloudManager (thread principal) depois de sincronizar a biblioteca. */
    fun onLibrarySynced() = syncFromDrive(manual = false)
}
