package com.meuapp.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.meuapp.player.Folder
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
internal fun PlaylistCover(
    vm: PlayerViewModel, name: String, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 44.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp), coverPx: Int = 200
) {
    val ids = vm.playlists[name]
    // Refaz quando a lista de músicas do player muda (as capas da nuvem chegam DEPOIS da primeira leitura) e olha a
    // playlist inteira. As 4 capas do mosaico são IMAGENS DIFERENTES: duas músicas com a mesma imagem (mesmo álbum,
    // ou playlist baixada em que todas têm o mesmo nome de álbum) contam como uma só. Tamanho do arquivo da capa
    // serve de impressão digital (barato, sem decodificar nada). Menos de 4 imagens diferentes = uma capa só.
    val withCover = remember(ids, vm.cloud.playerTracks) {
        vm.tracksIn("P:$name").asSequence()
            .filter { it.cloudId == null || it.coverStamp != 0L }
            .distinctBy { t ->
                val id = t.cloudId
                if (id != null) com.meuapp.player.cloud.CloudStorage.coverFile(id).length().let { if (it > 0L) "s$it" else "t${t.id}" }
                else t.album.ifBlank { t.id.toString() }
            }
            .take(4).toList()
    }
    val c0 = rememberCover(withCover.getOrNull(0), coverPx).value
    val c1 = rememberCover(withCover.getOrNull(1), coverPx).value
    val c2 = rememberCover(withCover.getOrNull(2), coverPx).value
    val c3 = rememberCover(withCover.getOrNull(3), coverPx).value
    val bitmaps = listOfNotNull(c0, c1, c2, c3)
    if (bitmaps.isNotEmpty()) {
        Box(modifier.clip(shape).background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.16f), shape)) {
            if (bitmaps.size >= 4) {
                // 4 capinhas dentro de uma capa grande, como no Spotify (fresta fina entre elas).
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
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
        if (name == FAVORITES) {
            Icon(Icons.Rounded.Favorite, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(iconSize))
        } else {
            // Sem imagem: as letras do nome, para achar a playlist de relance.
            Text(CloudHomeLogic.initials(name), fontSize = (iconSize.value * 1.1f).sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.92f))
        }
    }
}

/** Lista de todas as playlists (grade), com "Nova playlist" e filtros por tipo. Aberta pelo "Ver todas" do Início. */
@Composable
internal fun PlaylistsSection(vm: PlayerViewModel, goPlayer: () -> Unit, query: String, open: String?, onOpen: (String?) -> Unit) {
    var newDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var plusMenu by remember { mutableStateOf(false) }
    var intentDialog by remember { mutableStateOf(false) }
    var intentText by remember { mutableStateOf("") }
    var intentResult by remember { mutableStateOf<String?>(null) }
    var kindFilter by remember { mutableIntStateOf(-1) }
    BackHandler(enabled = open != null) { onOpen(null) }

    val current = open
    if (current != null && vm.playlists.containsKey(current)) {
        TrackList(vm, vm.tracksIn("P:$current"), current, true, goPlayer, playlist = current, onBack = { onOpen(null) })
        return
    }

    // Ordem: curtidas, minhas, pastas, motor, cantores (e por nome dentro de cada tipo). Filtros por tipo logo acima da lista.
    val all = vm.groups(3).sortedWith(compareBy<Folder>({ vm.playlistKind(it.name) }, { it.name.lowercase() }))
    val kindOf = all.associate { it.name to vm.playlistKind(it.name) }
    val chips = KIND_CHIPS.map { (k, label) ->
        Triple(k, label, kindOf.values.count { it == k || (k == CloudHomeLogic.KIND_MINE && it == CloudHomeLogic.KIND_FAV) })
    }.filter { it.third > 0 }
    val activeKind = if (chips.any { it.first == kindFilter }) kindFilter else -1
    val groups = all.filter { g ->
        val k = kindOf[g.name] ?: CloudHomeLogic.KIND_MINE
        val kindOk = activeKind < 0 || k == activeKind || (activeKind == CloudHomeLogic.KIND_MINE && k == CloudHomeLogic.KIND_FAV)
        kindOk && (query.isBlank() || g.name.contains(query.trim(), ignoreCase = true))
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Box {
                GhostButton("Nova playlist", Modifier.fillMaxWidth()) { plusMenu = true }
                GlassMenu(expanded = plusMenu, onDismissRequest = { plusMenu = false }) {
                    DropdownMenuItem(text = { Text("Playlist vazia") }, onClick = { plusMenu = false; newDialog = true })
                    DropdownMenuItem(text = { Text("Por intenção (inteligente)") }, onClick = { plusMenu = false; intentDialog = true })
                }
            }
        }
        if (chips.size > 1) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { KindChip("Todas · ${all.size}", activeKind < 0) { kindFilter = -1 } }
                items(chips) { (k, label, n) -> KindChip("$label · $n", activeKind == k) { kindFilter = if (activeKind == k) -1 else k } }
            }
        }
        if (groups.isEmpty()) item {
            if (query.isBlank()) EmptyState(Icons.Rounded.QueueMusic, "Nenhuma playlist ainda.")
            else EmptyState(Icons.Rounded.Search, "Nenhuma playlist com \"${query.trim()}\".")
        }
        items(groups.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { g ->
                    val name = g.name
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onOpen(name) }) {
                        PlaylistCover(vm, name, Modifier.fillMaxWidth().aspectRatio(1f), 52.dp)
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(plural(g.count) + kindSuffix(kindOf[name] ?: CloudHomeLogic.KIND_MINE), fontSize = 11.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private val KIND_CHIPS = listOf(
    CloudHomeLogic.KIND_MINE to "Minhas",
    CloudHomeLogic.KIND_FOLDER to "Pastas",
    CloudHomeLogic.KIND_MOTOR to "Motor",
    CloudHomeLogic.KIND_ARTIST to "Cantores"
)

/** Complemento do rótulo do cartão: de onde vem a playlist (as criadas por você ficam sem complemento). */
internal fun kindSuffix(kind: Int): String = when (kind) {
    CloudHomeLogic.KIND_FOLDER -> " \u00B7 pasta"
    CloudHomeLogic.KIND_ARTIST -> " \u00B7 cantor"
    CloudHomeLogic.KIND_MOTOR -> " \u00B7 motor"
    else -> ""
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) Neon.OnGreen else Color.White,
        modifier = Modifier.clip(shape)
            .background(if (selected) Neon.Green else Neon.Glass)
            .border(1.dp, if (selected) Neon.Green else Neon.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
