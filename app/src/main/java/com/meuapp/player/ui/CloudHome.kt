package com.meuapp.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.Folder
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.cloud.CloudHomeLogic
import com.meuapp.player.cloud.CloudLibrary
import com.meuapp.player.cloud.CloudManager
import com.meuapp.player.cloud.CloudTrack
import com.meuapp.player.cloud.SyncProgress
import java.util.Calendar

private class Mix(val title: String, val subtitle: String, val tracks: List<CloudTrack>, val hue: Float)

private fun gradient(hue: Float) = Brush.linearGradient(listOf(Color.hsv(hue, 0.62f, 0.66f), Color.hsv((hue + 38f) % 360f, 0.78f, 0.22f)))

/**
 * Início: TUDO do Drive num lugar só. Busca no topo (acha músicas e playlists), atalhos, playlists das pastas,
 * navegar pelas músicas (todas, artistas, álbuns, gêneros, pastas, recentes) e mixes.
 * [onOpenPlaylist] abre a tela da playlist (tocar é escolha do usuário lá dentro). [onSeePlaylists] abre a lista de todas as playlists.
 * [onBrowse] abre a lista de músicas: view 0 todas, 1 artistas, 2 álbuns, 3 gêneros, 4 recentes, 5 pastas; key opcional ("A:nome", "G:nome").
 * Tocar uma música abre o Player ([goPlayer]).
 */
@Composable
fun CloudHome(
    vm: PlayerViewModel, cloud: CloudManager, goPlayer: () -> Unit, onDevice: () -> Unit,
    query: String, onQuery: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit, onSeePlaylists: () -> Unit, onBrowse: (Int, String?) -> Unit
) {
    // Sem internet, o Início mostra só o que está salvo no aparelho.
    val all = if (cloud.online) cloud.tracks else cloud.tracks.filter { it.id in cloud.offlineIds }
    val seed = remember { System.currentTimeMillis() / 86_400_000L }
    val recent = remember(all) { CloudHomeLogic.recent(all, 15) }
    val artists = remember(all) { CloudHomeLogic.topArtists(all, 12) }
    val genres = remember(all) { CloudHomeLogic.topGenres(all, 10) }
    val rediscover = remember(all) { CloudHomeLogic.shuffled(all, seed + 7, 15) }
    val mixes = remember(all) {
        val out = ArrayList<Mix>()
        if (all.isNotEmpty()) out += Mix("Mix do dia", "Músicas variadas da sua biblioteca", CloudHomeLogic.shuffled(all, seed, 40), 150f)
        genres.take(3).forEachIndexed { i, g ->
            out += Mix("Mix ${g.name}", "${g.count} músicas", CloudHomeLogic.shuffled(CloudHomeLogic.byGenre(all, g.name), seed + i + 1, 40), CloudHomeLogic.hueOf(g.name))
        }
        artists.take(3).forEachIndexed { i, a ->
            out += Mix("Só ${a.name}", "${a.count} músicas", CloudHomeLogic.shuffled(CloudHomeLogic.byArtist(all, a.name), seed + i + 11, 40), CloudHomeLogic.hueOf(a.name))
        }
        out
    }
    val favs = all.filter { vm.isFav(it.numericId) }
    val searching = query.isNotBlank()
    val found = remember(query, cloud.tracks, cloud.folderPaths, cloud.online, cloud.offlineIds) {
        if (query.isBlank()) emptyList()
        else CloudLibrary.sort(CloudLibrary.filter(cloud.tracks, query, paths = cloud.folderPaths), CloudLibrary.SORT_TITLE)
            .let { r -> if (cloud.online) r else r.filter { it.id in cloud.offlineIds } }   // sem internet: só o que está salvo
            .take(200)
    }
    val foundPlayer = remember(found, cloud.playerTracks) { found.mapNotNull { cloud.trackByNumericId(it.numericId) } }

    fun play(list: List<CloudTrack>, index: Int = 0) {
        val pl = list.mapNotNull { cloud.trackByNumericId(it.numericId) }
        if (pl.isNotEmpty()) { vm.playList(pl, index.coerceIn(0, pl.size - 1)); goPlayer() }
    }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val shortcuts = listOf<Triple<String, ImageVector, () -> Unit>>(
        Triple("Curtidas", Icons.Rounded.Favorite, { play(favs) }),
        Triple("Recentes", Icons.Rounded.Schedule, { play(CloudHomeLogic.recent(all, 100)) }),
        Triple("Aleatório", Icons.Rounded.Shuffle, { play(CloudHomeLogic.shuffled(all, System.nanoTime(), 100)) })
    )

    val myLists = vm.groups(3).filter { it.count > 0 }
        .sortedWith(compareBy<Folder>({ vm.playlistKind(it.name) }, { it.name.lowercase() }))
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Column {
                Text("TOXIC PLAYER", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
                Text(CloudHomeLogic.greeting(hour), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { SearchField(query, onQuery, "Buscar músicas, artistas e playlists") }
        if (cloud.syncing || cloud.progress !is SyncProgress.Idle) item { SyncBanner(cloud) }
        cloud.message?.let { m -> item { MessageCard(m) { cloud.dismissMessage() } } }
        if (searching) {
            val matchLists = myLists.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (matchLists.isNotEmpty()) {
                item { Section("Playlists") }
                item { PlaylistRow(vm, matchLists, onOpenPlaylist) }
            }
            if (found.isEmpty() && matchLists.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "Nada encontrado para \"${query.trim()}\".") }
            } else if (found.isNotEmpty()) {
                item { Section("Músicas · ${plural(found.size)}") }
                items(found.size, key = { found[it].id }) { i ->
                    CloudTrackRow(vm, cloud, found[i]) { vm.playList(foundPlayer, i); goPlayer() }
                }
            }
            return@LazyColumn
        }
        if (all.isEmpty()) {
            item {
                if (!cloud.online) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(Icons.Rounded.CloudOff, "Sem internet e nenhuma música baixada ainda. Quando a rede voltar, tudo aparece aqui.")
                        GhostButton("Ver músicas do aparelho", onClick = onDevice)
                    }
                } else {
                    EmptyState(
                        Icons.Rounded.CloudQueue,
                        if (cloud.syncing) "Carregando suas músicas..." else "Sua biblioteca está vazia. Envie músicas para o Drive; o app atualiza sozinho."
                    )
                }
            }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shortcuts.forEach { (label, icon, action) -> ActionTile(label, icon, Modifier.weight(1f), action) }
            }
        }
        if (myLists.isNotEmpty()) {
            item { SectionRow("Suas playlists", "Ver todas", onSeePlaylists) }
            item { PlaylistRow(vm, myLists, onOpenPlaylist) }
        }
        item { Section("Suas músicas") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BROWSE_CHIPS) { (label, mode) -> Chip(label) { onBrowse(mode, null) } }
            }
        }
        if (mixes.isNotEmpty()) {
            item { Section("Feito para você") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mixes) { m -> MixCard(m) { play(m.tracks) } }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Section("Adicionadas recentemente") }
            item { TrackRow(cloud, recent) { i -> play(recent, i) } }
        }
        if (artists.isNotEmpty()) {
            item { Section("Seus artistas") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artists) { a ->
                        val first = remember(all, a.name) { CloudHomeLogic.byArtist(all, a.name).firstOrNull { it.hasCover } ?: CloudHomeLogic.byArtist(all, a.name).first() }
                        ArtistCard(cloud, a.name, a.count, first) { onBrowse(1, "A:${a.name}") }
                    }
                }
            }
        }
        if (genres.isNotEmpty()) {
            item { Section("Navegue por gênero") }
            items(genres.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { g ->
                        Box(
                            Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(20.dp)).background(gradient(CloudHomeLogic.hueOf(g.name)))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                                .clickable { onBrowse(3, "G:${g.name}") }.padding(14.dp)
                        ) {
                            Text(g.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${g.count}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomStart))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (rediscover.isNotEmpty()) {
            item { Section("Redescubra") }
            item { TrackRow(cloud, rediscover) { i -> play(rediscover, i) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Section(text: String) = Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))

private val BROWSE_CHIPS = listOf("Todas" to 0, "Artistas" to 1, "Álbuns" to 2, "Gêneros" to 3, "Pastas" to 5, "Recentes" to 4)

/** Título de seção com um botão de texto à direita (ex.: "Ver todas"). */
@Composable
private fun SectionRow(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Neon.Green,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Atalho em vidro líquido: ícone em cima e nome embaixo (cabe em 3 colunas sem cortar). */
@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.glass(22.dp).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Neon.Green.copy(alpha = 0.34f), Neon.Lime.copy(alpha = 0.12f)))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Neon.Green) }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Carrossel de playlists: cartão de vidro com capa, nome (até 2 linhas) e quantidade de músicas, sem cortar nada. */
@Composable
private fun PlaylistRow(vm: PlayerViewModel, lists: List<Folder>, onOpen: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(lists, key = { it.path }) { g ->
            Column(Modifier.width(158.dp).glass(24.dp).clickable { onOpen(g.name) }.padding(8.dp)) {
                PlaylistCover(vm, g.name, Modifier.size(142.dp), 48.dp)
                Text(
                    g.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
                )
                Text(plural(g.count), fontSize = 12.sp, color = Neon.Dim, maxLines = 1, modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun MixCard(m: Mix, onClick: () -> Unit) {
    Column(Modifier.width(158.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(158.dp).clip(RoundedCornerShape(22.dp)).background(gradient(m.hue))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)).padding(14.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)).align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Text(m.title, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.align(Alignment.BottomStart), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(m.subtitle, fontSize = 11.sp, color = Neon.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TrackRow(cloud: CloudManager, list: List<CloudTrack>, onPlay: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list.size) { i ->
            val t = list[i]
            val pt = remember(t, cloud.playerTracks) { cloud.trackByNumericId(t.numericId) }
            val cover by rememberCover(pt, 320)
            Column(Modifier.width(140.dp).clickable { onPlay(i) }) {
                Box(
                    Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)).background(Neon.Glass)
                        .border(1.dp, Neon.Border, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val c = cover
                    if (c != null) Image(c.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = Neon.Dim, modifier = Modifier.size(40.dp))
                }
                Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(t.artist, fontSize = 11.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistCard(cloud: CloudManager, name: String, count: Int, sample: CloudTrack, onClick: () -> Unit) {
    val pt = remember(sample, cloud.playerTracks) { cloud.trackByNumericId(sample.numericId) }
    val cover by rememberCover(pt, 256)
    Column(Modifier.width(110.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(110.dp).clip(CircleShape).background(gradient(CloudHomeLogic.hueOf(name)))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val c = cover
            if (c != null) Image(c.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(1).uppercase(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(plural(count), fontSize = 11.sp, color = Neon.Dim)
    }
}
