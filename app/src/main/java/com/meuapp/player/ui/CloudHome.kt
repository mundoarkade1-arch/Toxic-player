package com.meuapp.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.cloud.CloudHomeLogic
import com.meuapp.player.cloud.CloudManager
import com.meuapp.player.cloud.CloudTrack
import java.util.Calendar

private class Mix(val title: String, val subtitle: String, val tracks: List<CloudTrack>, val hue: Float)

private fun gradient(hue: Float) = Brush.linearGradient(listOf(Color.hsv(hue, 0.65f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.7f, 0.25f)))

/**
 * Tela inicial do Toxic Cloud: saudação, atalhos, carrosséis e mixes gerados da biblioteca do próprio usuário.
 * [onOpen] abre uma lista na aba Biblioteca (view: 1 artistas, 3 gêneros; key: "A:nome" / "G:nome").
 */
@Composable
fun CloudHome(vm: PlayerViewModel, cloud: CloudManager, goPlayer: () -> Unit, onLibrary: () -> Unit, onOpen: (Int, String) -> Unit) {
    val all = cloud.tracks
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
    val lists = vm.playlists.keys.sorted()

    fun play(list: List<CloudTrack>, index: Int = 0) {
        val pl = list.mapNotNull { cloud.trackByNumericId(it.numericId) }
        if (pl.isNotEmpty()) { vm.playList(pl, index.coerceIn(0, pl.size - 1)); goPlayer() }
    }

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val shortcuts = ArrayList<Triple<String, ImageVector, () -> Unit>>()
    shortcuts += Triple("Músicas curtidas", Icons.Rounded.Favorite, { play(favs) })
    shortcuts += Triple("Adicionadas recentemente", Icons.Rounded.Schedule, { play(CloudHomeLogic.recent(all, 100)) })
    shortcuts += Triple("Aleatório", Icons.Rounded.Shuffle, { play(CloudHomeLogic.shuffled(all, System.nanoTime(), 100)) })
    lists.take(3).forEach { n ->
        shortcuts += Triple(n, Icons.Rounded.LibraryMusic, {
            play((vm.playlists[n] ?: emptyList()).mapNotNull { id -> cloud.trackByNumericId(id) }.mapNotNull { cloud.cloudTrack(it.cloudId ?: "") })
        })
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TOXIC CLOUD", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
                    Text(CloudHomeLogic.greeting(hour), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                GhostButton("Biblioteca", onClick = onLibrary)
            }
        }
        if (cloud.syncing) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sincronizando sua biblioteca...", fontSize = 12.sp, color = Neon.Dim)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Neon.Green)
            }
        }
        cloud.message?.let { m -> item { Text(m, fontSize = 12.sp, color = Neon.Dim) } }
        if (all.isEmpty()) {
            item {
                Text(
                    if (cloud.syncing) "Procurando músicas no seu Drive..." else "Sua biblioteca está vazia. Envie músicas para o Drive e toque em Biblioteca > Atualizar biblioteca.",
                    color = Neon.Dim, modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            return@LazyColumn
        }
        items(shortcuts.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, action) -> Shortcut(label, icon, Modifier.weight(1f), action) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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
                        ArtistCard(cloud, a.name, a.count, first) { onOpen(1, "A:${a.name}") }
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
                            Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(14.dp)).background(gradient(CloudHomeLogic.hueOf(g.name)))
                                .clickable { onOpen(3, "G:${g.name}") }.padding(12.dp)
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
private fun Section(text: String) = Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun Shortcut(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(56.dp).glass(12.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).background(Neon.Green.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Neon.Green)
        }
        Text(label, Modifier.padding(horizontal = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MixCard(m: Mix, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)).background(gradient(m.hue)).padding(12.dp)) {
            Icon(Icons.Rounded.Shuffle, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopEnd))
            Text(m.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomStart), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(m.subtitle, fontSize = 11.sp, color = Neon.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
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
                Box(Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)).background(Neon.Glass), contentAlignment = Alignment.Center) {
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
        Box(Modifier.size(110.dp).clip(CircleShape).background(gradient(CloudHomeLogic.hueOf(name))), contentAlignment = Alignment.Center) {
            val c = cover
            if (c != null) Image(c.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.take(1).uppercase(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("$count músicas", fontSize = 11.sp, color = Neon.Dim)
    }
}
