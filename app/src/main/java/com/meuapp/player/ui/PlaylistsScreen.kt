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

/** Capa genérica de playlist: degradê numa cor própria de cada nome, com um símbolo (coração nas curtidas). */
@Composable
internal fun PlaylistCover(name: String, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 44.dp) {
    val hue = CloudHomeLogic.hueOf(name)
    val shape = RoundedCornerShape(20.dp)
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
        if (groups.isEmpty()) item { EmptyState(Icons.Rounded.QueueMusic, "Nenhuma playlist ainda.") }
        items(groups.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { g ->
                    val name = g.name
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { open = name }) {
                        PlaylistCover(name, Modifier.fillMaxWidth().aspectRatio(1f), 52.dp)
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
}
