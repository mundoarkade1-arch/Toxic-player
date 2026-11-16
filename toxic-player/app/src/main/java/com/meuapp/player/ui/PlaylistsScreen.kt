package com.meuapp.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.FAVORITES
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.cloud.CloudHomeLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Capa de playlist: se as músicas têm capa, monta uma automaticamente (mosaico de 4 capas, ou a capa da primeira);
 * se não tem nenhuma, cria uma genérica: degradê numa cor própria de cada nome, com um símbolo (coração nas curtidas).
 */
@Composable
internal fun PlaylistCover(vm: PlayerViewModel, name: String, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 44.dp) {
    val ids = vm.playlists[name]
    val withCover = remember(ids) { vm.tracksIn("P:$name").take(60).filter { it.cloudId == null || it.coverStamp != 0L }.take(4) }
    val c0 = rememberCover(withCover.getOrNull(0), 200).value
    val c1 = rememberCover(withCover.getOrNull(1), 200).value
    val c2 = rememberCover(withCover.getOrNull(2), 200).value
    val c3 = rememberCover(withCover.getOrNull(3), 200).value
    val bitmaps = listOfNotNull(c0, c1, c2, c3)
    val shape = RoundedCornerShape(20.dp)
    if (bitmaps.isNotEmpty()) {
        Box(modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.16f), shape)) {
            if (bitmaps.size >= 4) {
                Column(Modifier.fillMaxSize()) {
                    for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0..1) Image(
                            bitmaps[row * 2 + col].asImageBitmap(), null,
                            Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                Image(bitmaps[0].asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        return
    }
    val hue = CloudHomeLogic.hueOf(name)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.62f, 0.66f), Color.hsv((hue + 38f) % 360f, 0.78f, 0.22f))))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (name == FAVORITES) Icons.Rounded.Favorite else Icons.Rounded.QueueMusic, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(iconSize))
    }
}

/** Biblioteca: suas playlists e todas as músicas do Drive, separadas. */
@Composable
fun LibraryHub(vm: PlayerViewModel, goPlayer: () -> Unit) {
    val cloud = vm.cloud
    var section by remember { mutableIntStateOf(if (cloud.openGroup != null) 1 else 0) }
    BackHandler(enabled = section == 1 && cloud.openGroup != null) { cloud.enterGroup(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 24.dp)) {
        PageHeader("BIBLIOTECA", if (section == 0) "Suas playlists" else "Músicas do Drive")
        SegmentedTabs(listOf("Playlists", "Músicas do Drive"), section) { section = it }
        Spacer(Modifier.height(10.dp))
        if (section == 0) PlaylistsSection(vm, goPlayer)
        else if (!cloud.connected) ConnectCard(cloud, header = false)
        else DriveSongs(vm, cloud, goPlayer)
    }
}

@Composable
private fun PlaylistsSection(vm: PlayerViewModel, goPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }
    var newDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var intentDialog by remember { mutableStateOf(false) }
    var intentText by remember { mutableStateOf("") }
    var intentResult by remember { mutableStateOf<String?>(null) }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { com.meuapp.player.SafeRead.readText(ctx, uri, 8 * 1024 * 1024) }
            vm.importSmartPlaylistsJson(text)
        }
    }
    BackHandler(enabled = open != null) { open = null }

    val current = open
    if (current != null && vm.playlists.containsKey(current)) {
        TrackList(vm, vm.tracksIn("P:$current"), current, true, goPlayer, playlist = current, onBack = { open = null })
        return
    }

    val groups = vm.groups(3)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        vm.smartInfo?.let { info -> item { SmartInfoCard(info) { vm.dismissSmartInfo() } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Criar playlist", Modifier.weight(1f)) { newDialog = true }
                GhostButton("Sincronizar", Modifier.weight(1f)) { vm.syncSmartPlaylistsNow() }
            }
        }
        item { GhostButton("Importar playlists (.json)", Modifier.fillMaxWidth()) { jsonPicker.launch(arrayOf("application/json", "text/plain", "*/*")) } }
        item { GhostButton("Criar por intenção (inteligente)", Modifier.fillMaxWidth()) { intentDialog = true } }
        if (groups.isEmpty()) item { EmptyState(Icons.Rounded.QueueMusic, "Nenhuma playlist ainda.") }
        items(groups.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { g ->
                    val name = g.name
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { open = name }) {
                        PlaylistCover(vm, name, Modifier.fillMaxWidth().aspectRatio(1f), 52.dp)
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(plural(g.count), fontSize = 11.sp, color = Neon.Dim)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (newDialog) {
        GlassDialog(
            onDismissRequest = { newDialog = false },
            title = { Text("Nova playlist", fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Nome da playlist") }) },
            confirmButton = { TextButton(onClick = { vm.createPlaylist(newName); newName = ""; newDialog = false }) { Text("Criar", color = Neon.Green, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { newDialog = false }) { Text("Cancelar") } }
        )
    }
    if (intentDialog) {
        GlassDialog(
            onDismissRequest = { intentDialog = false },
            title = { Text("Playlist inteligente", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O que você escrever vira o nome da playlist. O app escolhe as músicas pelo seu gosto.", fontSize = 13.sp, color = Neon.Dim)
                    OutlinedTextField(value = intentText, onValueChange = { intentText = it }, singleLine = true, label = { Text("Para que é a playlist?") })
                }
            },
            confirmButton = {
                TextButton(onClick = { intentResult = vm.createIntentPlaylist(intentText); intentText = ""; intentDialog = false }) {
                    Text("Montar", color = Neon.Green, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { intentDialog = false }) { Text("Cancelar") } }
        )
    }
    val resultMsg = intentResult
    if (resultMsg != null) {
        GlassDialog(
            onDismissRequest = { intentResult = null },
            title = { Text("Pronto", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(resultMsg, fontSize = 15.sp)
                    if (vm.intentReport.isNotBlank()) {
                        Text("Por que cada música:", fontSize = 12.sp, color = Neon.Dim)
                        Text(vm.intentReport, fontSize = 12.sp, color = Neon.Dim)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { intentResult = null }) { Text("Fechar", color = Neon.Green, fontWeight = FontWeight.Bold) } }
        )
    }
}
