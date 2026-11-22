package com.meuapp.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.FAVORITES
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.Track
import com.meuapp.player.cloud.CloudHomeLogic
import com.meuapp.player.cloud.CloudTrack

/** Texto do tempo total, no estilo "3h 10min". Vazio se não há duração conhecida. */
internal fun totalTimeText(ms: Long): String {
    val min = (ms / 60_000L).toInt()
    if (min <= 0) return ""
    return if (min >= 60) "${min / 60}h ${min % 60}min" else "${min}min"
}

/** Até 3 artistas que mais aparecem, no estilo "A, B e C". Ignora artista desconhecido. */
internal fun topArtistsText(list: List<Track>): String {
    val top = list.map { it.artist.trim() }
        .filter { it.isNotEmpty() && it != CloudTrack.UNKNOWN_ARTIST }
        .groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        .take(3).map { it.key }
    return when (top.size) {
        0 -> ""
        1 -> top[0]
        else -> top.dropLast(1).joinToString(", ") + " e " + top.last()
    }
}

private fun kindText(kind: Int) = when (kind) {
    CloudHomeLogic.KIND_FAV -> "Suas curtidas"
    CloudHomeLogic.KIND_FOLDER -> "Pasta do Drive"
    CloudHomeLogic.KIND_MOTOR -> "Feita pelo motor"
    CloudHomeLogic.KIND_ARTIST -> "Playlist de artista"
    else -> "Sua playlist"
}

/**
 * Tela de uma playlist no estilo Spotify: capa grande com o nome por cima, artistas, tempo total,
 * botões (baixar, aleatório, tocar) e a lista de músicas (capa, título, artista e menu de três pontos).
 * Tocar a playlist inteira é escolha do usuário aqui dentro.
 */
@Composable
internal fun PlaylistDetail(vm: PlayerViewModel, name: String, goPlayer: () -> Unit, onBack: () -> Unit) {
    val cloud = vm.cloud
    val ids = vm.playlists[name]
    val list = remember(ids, cloud.playerTracks) { vm.tracksIn("P:$name") }
    val coverTrack = remember(list) { list.firstOrNull { it.cloudId == null || it.coverStamp != 0L } }
    val cover by rememberCover(coverTrack, 700)
    val totalMs = remember(list) { list.sumOf { it.durationMs } }
    val artists = remember(list) { topArtistsText(list) }
    val kind = vm.playlistKind(name)
    val current = vm.currentTrack
    val playingHere = vm.isPlaying && current != null && list.any { it.id == current.id }

    // Baixar para ouvir sem internet: só as músicas da nuvem desta playlist.
    val cloudTracks = remember(list) { list.mapNotNull { it.cloudId }.mapNotNull { cloud.cloudTrack(it) } }
    val allOffline = cloudTracks.isNotEmpty() && cloudTracks.all { cloud.isOffline(it.id) }
    val downloading = cloudTracks.any { cloud.downloads.containsKey(it.id) }
    val toDownload = cloudTracks.count { !cloud.isOffline(it.id) }

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<Track?>(null) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        // ---------- capa grande com o nome por cima ----------
        item {
            Box(Modifier.fillMaxWidth().height(380.dp)) {
                val b = cover
                if (b != null) {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    val hue = CloudHomeLogic.hueOf(name)
                    Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.hsv(hue, 0.62f, 0.66f), Color.hsv((hue + 38f) % 360f, 0.78f, 0.22f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (name == FAVORITES) Icon(Icons.Rounded.Favorite, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(96.dp))
                        else Text(CloudHomeLogic.initials(name), fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                // Escurece embaixo para o nome ficar legível e a capa "derreter" no fundo do app.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0.35f to Color.Transparent, 1f to Neon.Bg))))
                Text(
                    name, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.40f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Color.White)
                    }
                    if (name != FAVORITES) Box {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.40f)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Color.White)
                        }
                        GlassMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Apagar esta lista", color = Neon.Danger) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            }
        }
        // ---------- artistas, origem e tempo ----------
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (artists.isNotEmpty()) Text(artists, fontSize = 15.sp, color = Neon.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Neon.Brand), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.LibraryMusic, null, tint = Neon.OnGreen, modifier = Modifier.size(14.dp))
                    }
                    Text(kindText(kind), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Neon.Dim, modifier = Modifier.padding(start = 8.dp))
                }
                val total = totalTimeText(totalMs)
                Text(plural(list.size) + if (total.isNotEmpty()) " · $total" else "", fontSize = 14.sp, color = Neon.Faint)
            }
        }
        // ---------- botões: baixar, aleatório e tocar ----------
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    when {
                        cloudTracks.isEmpty() -> {}
                        downloading -> CircularProgressIndicator(Modifier.size(26.dp), color = Neon.Green, strokeWidth = 2.5.dp)
                        allOffline -> Icon(Icons.Rounded.DownloadDone, "Baixada", tint = Neon.Green, modifier = Modifier.size(28.dp))
                        else -> IconButton(onClick = { confirmDownload = true }) { Icon(Icons.Rounded.Download, "Baixar", tint = Neon.Dim, modifier = Modifier.size(28.dp)) }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (list.isNotEmpty()) { vm.playList(list.shuffled(), 0); goPlayer() } }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Shuffle, "Aleatório", tint = Neon.Dim, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Neon.Brand).clickable {
                        if (playingHere) vm.toggle()
                        else if (list.isNotEmpty()) { vm.playList(list, 0); goPlayer() }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (playingHere) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playingHere) "Pausar" else "Tocar", tint = Neon.OnGreen, modifier = Modifier.size(36.dp))
                }
            }
        }
        if (list.isEmpty()) item { EmptyState(Icons.Rounded.MusicNote, "Nenhuma música aqui ainda.") }
        // ---------- músicas ----------
        items(list.size) { i ->
            val t = list[i]
            PlaylistRow(vm, t, name, onPlay = { vm.playList(list, i); goPlayer() }, onAdd = { pickFor = t })
        }
    }

    if (confirmDelete) {
        GlassDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"$name\"?", fontWeight = FontWeight.Bold) },
            text = { Text("Só a playlist some do app. As músicas continuam no Drive.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(name) }) { Text("Apagar", color = Neon.Danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
    if (confirmDownload) {
        GlassDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Baixar a playlist?", fontWeight = FontWeight.Bold) },
            text = { Text("${plural(toDownload)} ficam salvas no aparelho para ouvir sem internet. Isso usa espaço e dados.") },
            confirmButton = { TextButton(onClick = { confirmDownload = false; cloud.downloadAll(cloudTracks) }) { Text("Baixar", color = Neon.Green, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Cancelar") } }
        )
    }
    pickFor?.let { tr -> AddToListDialog(vm, tr) { pickFor = null } }
}

/** Linha de música como no Spotify: capa, título, artista e três pontinhos. A música que está tocando fica em verde. */
@Composable
private fun PlaylistRow(vm: PlayerViewModel, t: Track, playlist: String, onPlay: () -> Unit, onAdd: () -> Unit) {
    val now = vm.currentTrack?.id == t.id
    val fav = vm.isFav(t.id)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 20.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackThumb(t, now, vm.isPlaying, 56.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (now) Neon.Green else Color.White, fontWeight = FontWeight.Medium)
            Text(t.artist, fontSize = 13.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Neon.Dim) }
            GlassMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (fav) "Tirar das curtidas" else "Curtir") }, onClick = { vm.toggleFav(t.id); menu = false })
                DropdownMenuItem(text = { Text("Tocar a seguir") }, onClick = { vm.playNext(t); menu = false })
                DropdownMenuItem(text = { Text("Adicionar à fila") }, onClick = { vm.addToQueue(t); menu = false })
                DropdownMenuItem(text = { Text("Adicionar à lista...") }, onClick = { onAdd(); menu = false })
                DropdownMenuItem(text = { Text("Remover desta lista") }, onClick = { vm.removeFromPlaylist(playlist, t.id); menu = false })
            }
        }
    }
}
