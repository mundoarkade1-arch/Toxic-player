package com.meuapp.player.ui

import android.app.Activity
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.cloud.CloudLibrary
import com.meuapp.player.cloud.CloudManager
import com.meuapp.player.cloud.CloudTrack
import com.meuapp.player.cloud.MetaState
import com.meuapp.player.cloud.SyncProgress

/** Login do Google (tela de consentimento). Fica no App, uma vez só, para funcionar em qualquer aba. */
@Composable
internal fun CloudAuthEffect(cloud: CloudManager) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) cloud.onAuthResult(r.data) else cloud.onAuthCancelled()
    }
    LaunchedEffect(cloud.pendingAuth) {
        val pi = cloud.pendingAuth ?: return@LaunchedEffect
        cloud.authLaunched()
        try { launcher.launch(IntentSenderRequest.Builder(pi).build()) } catch (e: Exception) { cloud.onAuthCancelled() }
    }
}

/**
 * Início: tudo do Drive. Sem conta conectada mostra o convite para entrar. [onDevice] abre a Biblioteca (músicas salvas no aparelho).
 * Dentro dele ficam: a tela inicial, a lista de todas as músicas (com filtros), a lista de todas as playlists e a tela de cada playlist.
 */
@Composable
fun CloudScreen(vm: PlayerViewModel, goPlayer: () -> Unit, onDevice: () -> Unit = {}) {
    val cloud = vm.cloud
    if (!cloud.connected) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) { ConnectCard(cloud) }
    } else {
        DriveHub(vm, goPlayer, onDevice)
    }
}

@Composable
private fun DriveHub(vm: PlayerViewModel, goPlayer: () -> Unit, onDevice: () -> Unit) {
    val cloud = vm.cloud
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }          // 0 início, 1 todas as músicas, 2 todas as playlists
    var query by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }

    val detail = openPlaylist?.takeIf { vm.playlists.containsKey(it) }
    if (detail != null) {
        // Playlist aberta: tela cheia no estilo Spotify. O voltar leva de volta para onde o usuário estava.
        BackHandler { openPlaylist = null }
        PlaylistDetail(vm, detail, goPlayer) { openPlaylist = null }
        return
    }
    BackHandler(enabled = page == 1 && cloud.openGroup != null) { cloud.enterGroup(null) }
    BackHandler(enabled = page != 0 && !(page == 1 && cloud.openGroup != null)) { page = 0 }
    BackHandler(enabled = page == 0 && query.isNotBlank()) { query = "" }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 24.dp)) {
        when (page) {
            0 -> CloudHome(
                vm, cloud, goPlayer, onDevice, query, { query = it },
                onOpenPlaylist = { openPlaylist = it },
                onSeePlaylists = { page = 2 },
                onBrowse = { view, key -> cloud.changeView(view); if (key != null) cloud.enterGroup(key); page = 1 }
            )
            1 -> {
                val title = when (cloud.viewMode) { 1 -> "Artistas"; 2 -> "Álbuns"; 3 -> "Gêneros"; 4 -> "Recentes"; 5 -> "Pastas"; else -> "Todas as músicas" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cloud.enterGroup(null); page = 0 }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Neon.Green) }
                    Column(Modifier.weight(1f)) {
                        Text("SEU DRIVE", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Rounded.Tune, "Filtros e ordem", tint = if (filtersActive(cloud)) Neon.Green else Neon.Dim)
                    }
                }
                Spacer(Modifier.height(6.dp))
                SearchField(cloud.query, cloud::changeQuery, "Buscar música, artista, álbum ou playlist")
                Spacer(Modifier.height(10.dp))
                DriveSongs(vm, cloud, goPlayer, onOpenPlaylist = { openPlaylist = it })
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = 0 }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Neon.Green) }
                    Column(Modifier.weight(1f)) {
                        Text("SEU DRIVE", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                        Text("Playlists", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                PlaylistsSection(vm, goPlayer, "", null) { openPlaylist = it }
            }
        }
    }
    if (showFilters) FiltersDialog(cloud) { showFilters = false }
}

@Composable
internal fun ConnectCard(cloud: CloudManager, header: Boolean = true) {
    if (header) PageHeader("TOXIC PLAYER", "Sua música no Drive")
    Column(
        Modifier.fillMaxWidth().glass(28.dp).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon.Green.copy(alpha = 0.32f), Neon.Lime.copy(alpha = 0.10f)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Cloud, null, tint = Neon.Green, modifier = Modifier.size(38.dp)) }
        Text("Entre com o Google para ouvir as músicas do seu Drive.", color = Neon.Dim, textAlign = TextAlign.Center)
        PrimaryButton(if (cloud.connecting) "Entrando..." else "Entrar com o Google") { cloud.connect() }
        Text("Só leitura. Você sai quando quiser.", color = Neon.Faint, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
    cloud.message?.let { MessageCard(it) { cloud.dismissMessage() } }
}

@Composable
internal fun MessageCard(text: String, onClose: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().glass(16.dp).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 13.sp)
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fechar aviso", tint = Neon.Dim) }
    }
}

/** Biblioteca > Músicas: todas as músicas (ou pastas, artistas, álbuns e gêneros, pelo botão de filtros). A busca fica no topo da Biblioteca. */
@Composable
internal fun DriveSongs(vm: PlayerViewModel, cloud: CloudManager, goPlayer: () -> Unit, onOpenPlaylist: (String) -> Unit = {}) {
    var confirmDownload by remember { mutableStateOf<List<CloudTrack>?>(null) }
    val open = cloud.openGroup

    val base = remember(cloud.tracks) { cloud.tracks }
    // Listas de gênero e a visão "Recentes" mostram sempre as músicas mais novas do Drive no topo.
    val effSort = if (open?.startsWith("G:") == true || cloud.viewMode == 4) CloudLibrary.SORT_RECENT else cloud.sortMode
    // Sem internet, só entram as músicas baixadas no aparelho.
    val offOnly = cloud.offlineOnly || !cloud.online
    val filtered = remember(base, cloud.query, cloud.formatFilter, offOnly, cloud.offlineIds, open, effSort, cloud.folderPaths) {
        CloudLibrary.sort(
            CloudLibrary.filter(base, cloud.query, cloud.formatFilter, offOnly, cloud.offlineIds, open, cloud.folderPaths),
            effSort
        )
    }
    val searching = cloud.query.isNotBlank()
    // Playlists (pastas) que combinam com a busca aparecem no topo, antes das músicas.
    val matchLists = rememberPlaylistMatches(vm, if (open == null) cloud.query else "")
    val showGroups = open == null && !searching && (cloud.viewMode in 1..3 || cloud.viewMode == 5)
    val groups = remember(base, cloud.viewMode, cloud.formatFilter, offOnly, cloud.offlineIds, cloud.folderPaths) {
        val pool = CloudLibrary.filter(base, "", cloud.formatFilter, offOnly, cloud.offlineIds, null, cloud.folderPaths)
        when (cloud.viewMode) { 1 -> CloudLibrary.artists(pool); 2 -> CloudLibrary.albums(pool); 5 -> CloudLibrary.folders(pool, cloud.folderPaths); else -> CloudLibrary.genres(pool) }
    }
    val playerList = remember(filtered, cloud.playerTracks) { filtered.mapNotNull { cloud.trackByNumericId(it.numericId) } }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (open != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cloud.enterGroup(null) }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Neon.Green) }
                    Column(Modifier.weight(1f)) {
                        Text(open.drop(2).replace("/", " / "), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${filtered.size} músicas", fontSize = 12.sp, color = Neon.Dim)
                    }
                }
            }
        }
        if (searching && matchLists.isNotEmpty()) {
            item { Text("Playlists · ${matchLists.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
            items(matchLists, key = { "pl:" + it.path }) { g -> PlaylistSearchRow(vm, g, onOpenPlaylist) }
            if (filtered.isNotEmpty()) item { Text("Músicas · ${filtered.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (!showGroups && filtered.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Tocar tudo", Modifier.weight(1f)) { vm.playList(playerList, 0); goPlayer() }
                    GhostButton("Baixar tudo", Modifier.weight(1f)) { confirmDownload = filtered.filter { it.id !in cloud.offlineIds } }
                }
            }
        }
        if (base.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.CloudQueue,
                    if (cloud.syncing) "Carregando suas músicas..." else "Nenhuma música de áudio encontrada no seu Drive. Envie arquivos MP3, FLAC, WAV, M4A, AAC, OGG ou OPUS; o app atualiza sozinho."
                )
            }
        } else if (showGroups) {
            items(groups, key = { it.key }) { g ->
                GroupCard(g.name, g.count, when (cloud.viewMode) { 1 -> Icons.Rounded.Person; 2 -> Icons.Rounded.Album; 5 -> Icons.Rounded.Folder; else -> Icons.Rounded.LibraryMusic }) { cloud.enterGroup(g.key) }
            }
        } else if (filtered.isEmpty()) {
            if (matchLists.isEmpty()) item {
                EmptyState(
                    if (!cloud.online) Icons.Rounded.CloudOff else Icons.Rounded.Search,
                    if (!cloud.online) "Sem internet e nenhuma música baixada ainda. Baixe músicas pelo menu (três pontinhos) para ouvir offline."
                    else "Nada encontrado."
                )
            }
        } else {
            items(filtered.size, key = { filtered[it].id }) { i ->
                CloudTrackRow(vm, cloud, filtered[i]) { vm.playList(playerList, i); goPlayer() }
            }
        }
    }

    confirmDownload?.let { list ->
        val bytes = list.sumOf { it.size }
        GlassDialog(
            onDismissRequest = { confirmDownload = null },
            title = { Text("Baixar para ouvir offline?") },
            text = { Text(if (list.isEmpty()) "Todas as músicas desta lista já estão offline." else "${list.size} músicas, ${CloudLibrary.formatSize(bytes)} de espaço no aparelho.") },
            confirmButton = {
                TextButton(onClick = { cloud.downloadAll(list); confirmDownload = null }, enabled = list.isNotEmpty()) { Text("Baixar", color = Neon.Green) }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AccountCard(cloud: CloudManager) {
    val pending = cloud.tracks.count { it.metaState == MetaState.PENDING }
    val who = cloud.accountName.ifBlank { "Conta Google" }
    Column(Modifier.fillMaxWidth().glass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Neon.Brand), contentAlignment = Alignment.Center) {
                Text(who.take(1).uppercase(), color = Neon.OnGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(who, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cloud.accountEmail.isNotBlank()) Text(cloud.accountEmail, fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.CloudDone, null, tint = Neon.Green, modifier = Modifier.size(26.dp))
        }
        val state = if (cloud.syncing) "Sincronizando" else "Conectado"
        Text("$state · ${cloud.tracks.size} músicas" + if (pending > 0 && !cloud.syncing) " · $pending sem metadados" else "", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        val last = if (cloud.lastSyncMs > 0) "Última sincronização " + DateUtils.getRelativeTimeSpanString(
            cloud.lastSyncMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase() else "Ainda não sincronizado"
        Text(last, fontSize = 12.sp, color = Neon.Dim)
        if (cloud.usageBytes > 0 || cloud.limitBytes > 0) {
            val q = if (cloud.limitBytes > 0) "${CloudLibrary.formatSize(cloud.usageBytes)} de ${CloudLibrary.formatSize(cloud.limitBytes)} usados no Drive"
            else "${CloudLibrary.formatSize(cloud.usageBytes)} usados no Drive"
            Text(q, fontSize = 12.sp, color = Neon.Dim)
        }
    }
}

@Composable
private fun AccountButtons(cloud: CloudManager, onDisconnect: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Trocar conta", Modifier.weight(1f)) { cloud.switchAccount() }
        GhostButton("Desconectar", Modifier.weight(1f), onDisconnect)
    }
}

/** Botões de bastidor (tudo isso já acontece sozinho; ficam aqui só para quem quiser forçar). */
@Composable
private fun MaintenanceButtons(vm: PlayerViewModel, cloud: CloudManager) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("O app já sincroniza sozinho. Use estes botões só se algo parecer desatualizado.", fontSize = 12.sp, color = Neon.Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Sincronizar", Modifier.weight(1f)) { cloud.syncNow() }
            GhostButton("Atualizar biblioteca", Modifier.weight(1f)) { cloud.refreshLibrary() }
        }
    }
}

@Composable
internal fun SyncStatus(cloud: CloudManager) {
    Column(Modifier.fillMaxWidth().glass(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val p = cloud.progress) {
            is SyncProgress.Listing -> {
                Text("Atualizando suas músicas", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Procurando no Drive: ${p.found} arquivos", fontSize = 12.sp, color = Neon.Dim)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Green)
            }
            is SyncProgress.Enriching -> {
                val pct = if (p.total == 0) 0 else (p.done * 100L / p.total).toInt().coerceIn(0, 100)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Atualizando suas músicas", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("$pct%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Neon.Green)
                }
                Text("Lendo as informações: ${p.done} de ${p.total}", fontSize = 12.sp, color = Neon.Dim)
                LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth(), color = Neon.Green)
            }
            else -> {
                Text("Atualizando suas músicas...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Green)
            }
        }
    }
}

/**
 * Faixa de progresso da atualização automática (Início e Biblioteca). Só aparece se a atualização demorar
 * mais de 1 segundo, para não piscar toda vez que o app confere o Drive e não há novidade.
 */
@Composable
internal fun SyncBanner(cloud: CloudManager) {
    val active = cloud.connected && (cloud.syncing || cloud.progress !is SyncProgress.Idle)
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) { kotlinx.coroutines.delay(1000); show = true } else show = false
    }
    if (active && show) {
        Spacer(Modifier.height(8.dp))
        SyncStatus(cloud)
        Spacer(Modifier.height(8.dp))
    }
}

/** Há algum filtro ou ordem diferente do padrão? (para acender o botão de filtros) */
internal fun filtersActive(cloud: CloudManager) =
    cloud.viewMode != 0 || cloud.offlineOnly || cloud.formatFilter != null || cloud.sortMode != CloudLibrary.SORT_TITLE

/** Filtros, visões e ordem da lista de músicas, atrás de um botão pequeno. */
@Composable
internal fun FiltersDialog(cloud: CloudManager, onClose: () -> Unit) {
    val formats = remember(cloud.tracks) { CloudLibrary.formatsPresent(cloud.tracks) }
    GlassDialog(
        onDismissRequest = onClose,
        title = { Text("Filtros e ordem", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ver por", fontSize = 12.sp, color = Neon.Dim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Músicas", cloud.viewMode == 0) { cloud.changeView(0) }
                    Chip("Artistas", cloud.viewMode == 1) { cloud.changeView(1) }
                    Chip("Álbuns", cloud.viewMode == 2) { cloud.changeView(2) }
                    Chip("Gêneros", cloud.viewMode == 3) { cloud.changeView(3) }
                    Chip("Pastas", cloud.viewMode == 5) { cloud.changeView(5) }
                    Chip("Recentes", cloud.viewMode == 4) { cloud.changeView(4) }
                }
                Text("Ordem", fontSize = 12.sp, color = Neon.Dim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Título", cloud.sortMode == CloudLibrary.SORT_TITLE) { cloud.changeSort(CloudLibrary.SORT_TITLE) }
                    Chip("Artista", cloud.sortMode == CloudLibrary.SORT_ARTIST) { cloud.changeSort(CloudLibrary.SORT_ARTIST) }
                    Chip("Álbum", cloud.sortMode == CloudLibrary.SORT_ALBUM) { cloud.changeSort(CloudLibrary.SORT_ALBUM) }
                    Chip("Duração", cloud.sortMode == CloudLibrary.SORT_DURATION) { cloud.changeSort(CloudLibrary.SORT_DURATION) }
                    Chip("Tamanho", cloud.sortMode == CloudLibrary.SORT_SIZE) { cloud.changeSort(CloudLibrary.SORT_SIZE) }
                    Chip("Recentes", cloud.sortMode == CloudLibrary.SORT_RECENT) { cloud.changeSort(CloudLibrary.SORT_RECENT) }
                }
                Text("Mostrar", fontSize = 12.sp, color = Neon.Dim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Tudo", !cloud.offlineOnly) { if (cloud.offlineOnly) cloud.toggleOfflineOnly() }
                    Chip("Só baixadas", cloud.offlineOnly) { if (!cloud.offlineOnly) cloud.toggleOfflineOnly() }
                }
                if (formats.size > 1) {
                    Text("Formato", fontSize = 12.sp, color = Neon.Dim)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Todos", cloud.formatFilter == null) { cloud.changeFormat(null) }
                        formats.forEach { f -> Chip(f.label, cloud.formatFilter == f) { cloud.changeFormat(f) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Pronto", color = Neon.Green, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun GroupCard(name: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) =
    GroupTile(name, count, icon, onClick)

@Composable
internal fun CloudTrackRow(vm: PlayerViewModel, cloud: CloudManager, t: CloudTrack, onPlay: () -> Unit) {
    val pt = remember(t, cloud.playerTracks) { cloud.trackByNumericId(t.numericId) }
    val now = vm.currentTrack?.cloudId == t.id
    val offline = t.id in cloud.offlineIds
    val dl = cloud.downloads[t.id]
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    val fav = vm.isFav(t.numericId)
    val rowShape = RoundedCornerShape(18.dp)
    // Linha simples: capa, título, artista, coração e menu.
    Row(
        Modifier.fillMaxWidth().clip(rowShape)
            .then(
                if (now) Modifier.background(Neon.Green.copy(alpha = 0.10f)).border(1.dp, Neon.Green.copy(alpha = 0.35f), rowShape)
                else Modifier.background(Neon.Glass)
            )
            .clickable(onClick = onPlay).padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackThumb(pt, now, vm.isPlaying)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (now) Neon.Green else Color.White, fontWeight = FontWeight.Medium)
            Text(t.artist, fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (dl != null) {
            Box(Modifier.size(40.dp).clickable { cloud.cancelDownload(t.id) }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { dl }, modifier = Modifier.size(22.dp), color = Neon.Green, strokeWidth = 2.dp)
            }
        }
        IconButton(onClick = { vm.toggleFav(t.numericId) }) {
            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favoritar", tint = if (fav) Neon.Green else Neon.Dim)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Neon.Dim) }
            GlassMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (pt != null) {
                    DropdownMenuItem(text = { Text("Tocar a seguir") }, onClick = { vm.playNext(pt); menu = false })
                    DropdownMenuItem(text = { Text("Adicionar à fila") }, onClick = { vm.addToQueue(pt); menu = false })
                }
                DropdownMenuItem(text = { Text("Adicionar à lista...") }, onClick = { addDialog = true; menu = false })
                if (offline) DropdownMenuItem(text = { Text("Remover download") }, onClick = { cloud.removeOffline(t.id); menu = false })
                else if (dl == null) DropdownMenuItem(text = { Text("Baixar para offline") }, onClick = { cloud.download(t); menu = false })
            }
        }
    }
    if (addDialog) AddToListDialog(vm, t) { addDialog = false }
}

@Composable
private fun AddToListDialog(vm: PlayerViewModel, t: CloudTrack, onClose: () -> Unit) {
    GlassDialog(
        onDismissRequest = onClose,
        title = { Text("Adicionar à lista") },
        text = {
            Column {
                vm.playlists.keys.sorted().forEach { n ->
                    Text(n, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.addToPlaylist(n, t.numericId); onClose() }.padding(12.dp), color = Color.White)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } }
    )
}

@Composable
private fun StorageCard(cloud: CloudManager) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmOffline by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().glass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title("ARMAZENAMENTO NO APARELHO")
        Text("Cache de streaming: ${CloudLibrary.formatSize(cloud.cacheBytes)} · Capas: ${CloudLibrary.formatSize(cloud.coversBytes)}", fontSize = 13.sp)
        Text("Limite do cache de streaming", fontSize = 12.sp, color = Neon.Dim)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.meuapp.player.cloud.CloudConfig.CACHE_LIMIT_OPTIONS_MB.forEach { mb ->
                Chip(if (mb >= 1024) "${mb / 1024} GB" else "$mb MB", cloud.cacheLimitMb == mb) { cloud.setCacheLimit(mb) }
            }
        }
        GhostButton("Limpar cache", Modifier.fillMaxWidth()) { confirmClear = true }
        Text("Downloads offline: ${CloudLibrary.formatSize(cloud.offlineBytes)} (${cloud.offlineIds.size} músicas)", fontSize = 13.sp)
        GhostButton("Remover todos os downloads", Modifier.fillMaxWidth()) { confirmOffline = true }
        GhostButton("Testar streaming", Modifier.fillMaxWidth()) { cloud.testStreaming() }
        cloud.diagnostic?.let { Text(it, fontSize = 12.sp, color = Neon.Dim) }
    }
    if (confirmClear) {
        GlassDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar cache?") },
            text = { Text("Remove o áudio em cache e as capas. Os downloads offline são mantidos e as capas voltam a ser lidas aos poucos.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; cloud.clearCache() }) { Text("Limpar", color = Neon.Green) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
    if (confirmOffline) {
        GlassDialog(
            onDismissRequest = { confirmOffline = false },
            title = { Text("Remover downloads?") },
            text = { Text("As músicas baixadas serão apagadas do aparelho. Elas continuam no seu Drive.") },
            confirmButton = { TextButton(onClick = { confirmOffline = false; cloud.clearOffline() }) { Text("Remover", color = Neon.Green) } },
            dismissButton = { TextButton(onClick = { confirmOffline = false }) { Text("Cancelar") } }
        )
    }
}


/**
 * Ajustes > Conta do Google ([advanced] = false) ou Ajustes > Avançado ([advanced] = true: sincronização, importação, cache e downloads).
 */
@Composable
internal fun CloudSettings(vm: PlayerViewModel, advanced: Boolean = false) {
    val cloud = vm.cloud
    var confirmDisconnect by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!cloud.connected) {
            ConnectCard(cloud, header = false)
        } else if (!advanced) {
            AccountCard(cloud)
            AccountButtons(cloud) { confirmDisconnect = true }
            cloud.message?.let { MessageCard(it) { cloud.dismissMessage() } }
            Spacer(Modifier.height(16.dp))
        } else {
            if (cloud.syncing) SyncStatus(cloud)
            MaintenanceButtons(vm, cloud)
            cloud.message?.let { MessageCard(it) { cloud.dismissMessage() } }
            vm.smartInfo?.let { info -> SmartInfoCard(info) { vm.dismissSmartInfo() } }
            StorageCard(cloud)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDisconnect) {
        GlassDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar conta Google?") },
            text = { Text("O acesso do Toxic Player ao seu Drive será revogado. Neste aparelho serão apagados o índice da biblioteca, o cache e os downloads offline. Nada é apagado do seu Drive.") },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; cloud.disconnect() }) { Text("Desconectar", color = Neon.Green) } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } }
        )
    }
}
