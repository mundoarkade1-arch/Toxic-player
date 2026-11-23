package com.meuapp.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.Track

/**
 * Biblioteca: SÓ o que está salvo no aparelho, para ouvir sem internet.
 * "Baixadas" = músicas do Drive que o usuário baixou. "No celular" = músicas que já estavam no celular.
 * Nada do Drive que não esteja baixado aparece aqui (tudo do Drive fica no Início).
 */
@Composable
fun OfflineLibrary(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit, goPlayer: () -> Unit) {
    val cloud = vm.cloud
    var section by remember { mutableIntStateOf(0) }          // 0 baixadas, 1 no celular
    var q by remember { mutableStateOf("") }
    var pickFor by remember { mutableStateOf<Track?>(null) }

    val downloaded = remember(cloud.tracks, cloud.offlineIds, cloud.playerTracks) {
        cloud.tracks.filter { it.id in cloud.offlineIds }.mapNotNull { cloud.trackByNumericId(it.numericId) }
    }
    val onPhone = remember(vm.tracks) { vm.tracks.filter { it.cloudId == null } }
    val base = if (section == 0) downloaded else onPhone
    val shown = remember(base, q) {
        val k = q.trim()
        if (k.isEmpty()) base
        else base.filter { it.title.contains(k, ignoreCase = true) || it.artist.contains(k, ignoreCase = true) || it.album.contains(k, ignoreCase = true) }
    }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 24.dp)) {
        PageHeader("BIBLIOTECA", "Salvas no aparelho")
        SearchField(q, { q = it }, "Buscar música ou artista", Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        SegmentedTabs(listOf("Baixadas · ${downloaded.size}", "No celular · ${onPhone.size}"), section) { section = it }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
        if (section == 1 && !granted) {
            PermissionCard("Permita o acesso às músicas do celular para ver e tocar as que estão salvas aqui.", "Permitir acesso", askPerm)
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (shown.isNotEmpty() && q.isBlank()) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        GhostButton("Tocar tudo", Modifier.weight(1f)) { vm.playList(shown, 0); goPlayer() }
                        GhostButton("Aleatório", Modifier.weight(1f)) { vm.playList(shown.shuffled(), 0); goPlayer() }
                    }
                }
                if (shown.isEmpty()) item {
                    when {
                        q.isNotBlank() -> EmptyState(Icons.Rounded.Search, "Nada encontrado para \"${q.trim()}\".")
                        section == 0 -> EmptyState(Icons.Rounded.CloudDownload, "Você ainda não baixou músicas. No Início, abra uma playlist e toque em Baixar para ouvir sem internet.")
                        else -> EmptyState(Icons.Rounded.LibraryMusic, "Nenhuma música encontrada no celular.")
                    }
                }
                items(shown.size) { i ->
                    val t = shown[i]
                    SongRow(vm, t, null, onPlay = { vm.playList(shown, i); goPlayer() }, onAdd = { pickFor = t })
                }
            }
        }
    }
    pickFor?.let { tr -> AddToListDialog(vm, tr) { pickFor = null } }
}
