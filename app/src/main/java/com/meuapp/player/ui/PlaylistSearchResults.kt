package com.meuapp.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.Folder
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.cloud.PlaylistSearch

/**
 * Playlists (pastas do Drive e as suas) que combinam com a busca: pelo nome ou porque a maior parte das músicas
 * é do artista/álbum buscado. As que combinam pelo nome vêm primeiro. Ver [PlaylistSearch].
 */
@Composable
internal fun rememberPlaylistMatches(vm: PlayerViewModel, query: String): List<Folder> {
    val q = query.trim()
    // Chave barata para refazer quando alguma playlist ganha/perde música (a leitura abaixo também é observada).
    val stamp = vm.playlists.size * 100_000 + vm.playlists.values.sumOf { it.size }
    return remember(q, stamp, vm.cloud.playerTracks) {
        if (q.isEmpty()) emptyList()
        else {
            val hits = ArrayList<Pair<Folder, Int>>()
            for (g in vm.groups(3)) {
                if (g.count <= 0) continue
                // Uma frase por música (artista + álbum): a playlist conta se boa parte das músicas combina.
                val s = PlaylistSearch.score(q, g.name, { vm.tracksIn("P:${g.name}").map { it.artist + " " + it.album } }, g.count)
                if (s > 0) hits += g to s
            }
            hits.sortedWith(compareByDescending<Pair<Folder, Int>> { it.second }.thenBy { it.first.name.lowercase() }).map { it.first }
        }
    }
}

/** Linha de playlist nos resultados da busca: capa (mosaico de 4), nome e quantidade de músicas. Toca para abrir a playlist. */
@Composable
internal fun PlaylistSearchRow(vm: PlayerViewModel, g: Folder, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen(g.name) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlaylistCover(vm, g.name, Modifier.size(64.dp), 24.dp, RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist · " + plural(g.count), fontSize = 12.sp, color = Neon.Dim, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Neon.Dim)
    }
}
