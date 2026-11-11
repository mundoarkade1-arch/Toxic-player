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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.cloud.CloudLibrary
import com.meuapp.player.cloud.CloudManager
import com.meuapp.player.cloud.CloudTrack
import com.meuapp.player.cloud.MetaState
import com.meuapp.player.cloud.SyncProgress

/** Seção TOXIC CLOUD: conta Google, biblioteca do Drive, sincronização, cache e downloads offline. */
@Composable
fun CloudScreen(vm: PlayerViewModel, goPlayer: () -> Unit) {
    val cloud = vm.cloud
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) cloud.onAuthResult(r.data) else cloud.onAuthCancelled()
    }
    LaunchedEffect(cloud.pendingAuth) {
        val pi = cloud.pendingAuth ?: return@LaunchedEffect
        cloud.authLaunched()
        try { launcher.launch(IntentSenderRequest.Builder(pi).build()) } catch (e: Exception) { cloud.onAuthCancelled() }
    }
    BackHandler(enabled = cloud.openGroup != null) { cloud.enterGroup(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            if (!cloud.connected) ConnectCard(cloud) else ConnectedContent(vm, cloud, goPlayer)
        }
    }
}

@Composable
private fun ConnectCard(cloud: CloudManager) {
    Text("TOXIC CLOUD", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
    Text("Sua música no Google Drive", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
    Column(Modifier.fillMaxWidth().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Cloud, null, tint = Neon.Green, modifier = Modifier.size(40.dp))
        Text("Conecte sua conta Google para tocar pela internet as músicas que já estão no seu Drive, sem baixar tudo para o celular.", color = Neon.Dim)
        Text("O app pede acesso somente de leitura ao Drive, não guarda sua senha e você pode desconectar quando quiser.", color = Neon.Dim, fontSize = 12.sp)
        PrimaryButton(if (cloud.connecting) "Conectando..." else "Conectar conta Google") { cloud.connect() }
    }
    cloud.message?.let { MessageCard(it) { cloud.dismissMessage() } }
}

@Composable
private fun MessageCard(text: String, onClose: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().glass(16.dp).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 13.sp)
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fechar aviso", tint = Neon.Dim) }
    }
}

@Composable
private fun ConnectedContent(vm: PlayerViewModel, cloud: CloudManager, goPlayer: () -> Unit) {
    var home by remember { mutableStateOf(true) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf<List<CloudTrack>?>(null) }
    val open = cloud.openGroup

    val base = remember(cloud.tracks) { cloud.tracks }
    // Listas de gênero e a visão "Recentes" mostram sempre as músicas mais novas do Drive no topo.
    val effSort = if (open?.startsWith("G:") == true || cloud.viewMode == 4) CloudLibrary.SORT_RECENT else cloud.sortMode
    val filtered = remember(base, cloud.query, cloud.formatFilter, cloud.offlineOnly, cloud.offlineIds, open, effSort, cloud.folderPaths) {
        CloudLibrary.sort(
            CloudLibrary.filter(base, cloud.query, cloud.formatFilter, cloud.offlineOnly, cloud.offlineIds, open, cloud.folderPaths),
            effSort
        )
    }
    val searching = cloud.query.isNotBlank()
    val showGroups = open == null && !searching && (cloud.viewMode in 1..3 || cloud.viewMode == 5)
    val groups = remember(base, cloud.viewMode, cloud.formatFilter, cloud.offlineOnly, cloud.offlineIds, cloud.folderPaths) {
        val pool = CloudLibrary.filter(base, "", cloud.formatFilter, cloud.offlineOnly, cloud.offlineIds, null, cloud.folderPaths)
        when (cloud.viewMode) { 1 -> CloudLibrary.artists(pool); 2 -> CloudLibrary.albums(pool); 5 -> CloudLibrary.folders(pool, cloud.folderPaths); else -> CloudLibrary.genres(pool) }
    }
    val playerList = remember(filtered, cloud.playerTracks) { filtered.mapNotNull { cloud.trackByNumericId(it.numericId) } }

    if (home && open == null) {
        CloudHome(vm, cloud, goPlayer, onLibrary = { home = false }) { view, key -> cloud.changeView(view); cloud.enterGroup(key); home = false }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (open == null) {
            item { Row { Chip("← Início", false) { home = true } } }
            item { AccountCard(cloud) }
            item { ActionButtons(cloud) { confirmDisconnect = true } }
            if (cloud.syncing) item { SyncStatus(cloud) }
            cloud.message?.let { m -> item { MessageCard(m) { cloud.dismissMessage() } } }
            item {
                OutlinedTextField(
                    value = cloud.query, onValueChange = cloud::changeQuery, singleLine = true,
                    label = { Text("Buscar na nuvem") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Filters(cloud, base) }
        } else {
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
                Text(
                    if (cloud.syncing) "Procurando músicas no seu Drive..." else "Nenhuma música de áudio encontrada no seu Drive. Envie arquivos MP3, FLAC, WAV, M4A, AAC, OGG ou OPUS e toque em Atualizar biblioteca.",
                    color = Neon.Dim, modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else if (showGroups) {
            items(groups, key = { it.key }) { g ->
                GroupCard(g.name, g.count, when (cloud.viewMode) { 1 -> Icons.Rounded.Person; 2 -> Icons.Rounded.Album; 5 -> Icons.Rounded.Folder; else -> Icons.Rounded.LibraryMusic }) { cloud.enterGroup(g.key) }
            }
        } else if (filtered.isEmpty()) {
            item { Text("Nada encontrado com estes filtros.", color = Neon.Dim, modifier = Modifier.padding(vertical = 8.dp)) }
        } else {
            items(filtered.size, key = { filtered[it].id }) { i ->
                CloudTrackRow(vm, cloud, filtered[i]) { vm.playList(playerList, i); goPlayer() }
            }
        }
        if (open == null) item { StorageCard(cloud) }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar conta Google?") },
            text = { Text("O acesso do Toxic Player ao seu Drive será revogado. Neste aparelho serão apagados o índice da biblioteca, o cache e os downloads offline. Nada é apagado do seu Drive.") },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; cloud.disconnect() }) { Text("Desconectar", color = Neon.Green) } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } }
        )
    }
    confirmDownload?.let { list ->
        val bytes = list.sumOf { it.size }
        AlertDialog(
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
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TOXIC CLOUD", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDone, null, tint = Neon.Green, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(cloud.accountName.ifBlank { "Conta Google" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cloud.accountEmail.isNotBlank()) Text(cloud.accountEmail, fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val state = if (cloud.syncing) "Sincronizando" else "Conectado"
        Text("$state · ${cloud.tracks.size} músicas" + if (pending > 0 && !cloud.syncing) " · $pending sem metadados" else "", fontSize = 13.sp)
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
private fun ActionButtons(cloud: CloudManager, onDisconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Sincronizar", Modifier.weight(1f)) { cloud.syncNow() }
            GhostButton("Atualizar biblioteca", Modifier.weight(1f)) { cloud.refreshLibrary() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Trocar conta", Modifier.weight(1f)) { cloud.switchAccount() }
            GhostButton("Desconectar", Modifier.weight(1f), onDisconnect)
        }
    }
}

@Composable
private fun SyncStatus(cloud: CloudManager) {
    Column(Modifier.fillMaxWidth().glass(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val p = cloud.progress) {
            is SyncProgress.Listing -> {
                Text("Procurando músicas no Drive... ${p.found} arquivos", fontSize = 13.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Green)
            }
            is SyncProgress.Enriching -> {
                Text("Lendo informações das músicas: ${p.done} de ${p.total}", fontSize = 13.sp)
                LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth(), color = Neon.Green)
            }
            else -> {
                Text("Sincronizando...", fontSize = 13.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Green)
            }
        }
    }
}

@Composable
private fun Filters(cloud: CloudManager, all: List<CloudTrack>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Músicas", cloud.viewMode == 0) { cloud.changeView(0) }
            Chip("Artistas", cloud.viewMode == 1) { cloud.changeView(1) }
            Chip("Álbuns", cloud.viewMode == 2) { cloud.changeView(2) }
            Chip("Gêneros", cloud.viewMode == 3) { cloud.changeView(3) }
            Chip("Pastas", cloud.viewMode == 5) { cloud.changeView(5) }
            Chip("Recentes", cloud.viewMode == 4) { cloud.changeView(4) }
            Chip("Offline", cloud.offlineOnly) { cloud.toggleOfflineOnly() }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Título", cloud.sortMode == CloudLibrary.SORT_TITLE) { cloud.changeSort(CloudLibrary.SORT_TITLE) }
            Chip("Artista", cloud.sortMode == CloudLibrary.SORT_ARTIST) { cloud.changeSort(CloudLibrary.SORT_ARTIST) }
            Chip("Álbum", cloud.sortMode == CloudLibrary.SORT_ALBUM) { cloud.changeSort(CloudLibrary.SORT_ALBUM) }
            Chip("Duração", cloud.sortMode == CloudLibrary.SORT_DURATION) { cloud.changeSort(CloudLibrary.SORT_DURATION) }
            Chip("Tamanho", cloud.sortMode == CloudLibrary.SORT_SIZE) { cloud.changeSort(CloudLibrary.SORT_SIZE) }
            Chip("Recentes", cloud.sortMode == CloudLibrary.SORT_RECENT) { cloud.changeSort(CloudLibrary.SORT_RECENT) }
        }
        val formats = remember(all) { CloudLibrary.formatsPresent(all) }
        if (formats.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Todos", cloud.formatFilter == null) { cloud.changeFormat(null) }
                formats.forEach { f -> Chip(f.label, cloud.formatFilter == f) { cloud.changeFormat(f) } }
            }
        }
    }
}

@Composable
private fun GroupCard(name: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(18.dp).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Neon.Green, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count músicas", fontSize = 12.sp, color = Neon.Dim)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Neon.Dim)
    }
}

@Composable
private fun CloudTrackRow(vm: PlayerViewModel, cloud: CloudManager, t: CloudTrack, onPlay: () -> Unit) {
    val pt = remember(t, cloud.playerTracks) { cloud.trackByNumericId(t.numericId) }
    val now = vm.currentTrack?.cloudId == t.id
    val offline = t.id in cloud.offlineIds
    val dl = cloud.downloads[t.id]
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    val fav = vm.isFav(t.numericId)
    Row(
        Modifier.fillMaxWidth().glass(18.dp).clickable(onClick = onPlay).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumb by rememberCover(pt, 128)
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Neon.Glass), contentAlignment = Alignment.Center) {
            val tb = thumb
            if (tb != null) Image(tb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, tint = if (now) Neon.Green else Neon.Dim)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (now) Neon.Green else Color.White)
            Text("${t.artist} · ${t.album}", fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    t.format.label, fontSize = 10.sp, color = Neon.Green,
                    modifier = Modifier.border(1.dp, Neon.Green.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                )
                val dur = if (t.durationMs > 0) fmt(t.durationMs) + " · " else ""
                val status = when {
                    now && offline -> "tocando offline"
                    now -> "streaming"
                    offline -> "offline"
                    cloud.isCached(t) -> "em cache"
                    else -> "nuvem"
                }
                Text("$dur${CloudLibrary.formatSize(t.size)} · $status", fontSize = 11.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            dl != null -> Box(Modifier.size(40.dp).clickable { cloud.cancelDownload(t.id) }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { dl }, modifier = Modifier.size(22.dp), color = Neon.Green, strokeWidth = 2.dp)
            }
            offline -> Icon(Icons.Rounded.DownloadDone, "Disponível offline", tint = Neon.Green, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp))
            else -> Icon(Icons.Rounded.CloudQueue, "Streaming", tint = Neon.Dim, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp))
        }
        IconButton(onClick = { vm.toggleFav(t.numericId) }) {
            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favoritar", tint = if (fav) Neon.Green else Neon.Dim)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Neon.Dim) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Adicionar à lista") },
        text = {
            Column {
                vm.playlists.keys.sorted().forEach { n ->
                    Text(n, Modifier.fillMaxWidth().clickable { vm.addToPlaylist(n, t.numericId); onClose() }.padding(12.dp))
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
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar cache?") },
            text = { Text("Remove o áudio em cache e as capas. Os downloads offline são mantidos e as capas voltam a ser lidas aos poucos.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; cloud.clearCache() }) { Text("Limpar", color = Neon.Green) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
    if (confirmOffline) {
        AlertDialog(
            onDismissRequest = { confirmOffline = false },
            title = { Text("Remover downloads?") },
            text = { Text("As músicas baixadas serão apagadas do aparelho. Elas continuam no seu Drive.") },
            confirmButton = { TextButton(onClick = { confirmOffline = false; cloud.clearOffline() }) { Text("Remover", color = Neon.Green) } },
            dismissButton = { TextButton(onClick = { confirmOffline = false }) { Text("Cancelar") } }
        )
    }
}
