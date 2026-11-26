package com.meuapp.player.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.meuapp.player.ALL_FOLDERS
import com.meuapp.player.BuildConfig
import com.meuapp.player.CoverLoader
import com.meuapp.player.FAVORITES
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.Track
import com.meuapp.player.audio.AudioFx
import com.meuapp.player.audio.CarPresets
import com.meuapp.player.audio.Crossover
import com.meuapp.player.audio.XoverType
import com.meuapp.player.audio.DspCore
import com.meuapp.player.audio.DspEngine
import com.meuapp.player.audio.EqFilter
import com.meuapp.player.audio.FilterType
import com.meuapp.player.audio.Meters
import com.meuapp.player.audio.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// ───────────── Estrutura principal + navegação ─────────────
/**
 * Abas: 0 Início (TUDO do Drive: busca, playlists das pastas e músicas), 1 Biblioteca (só o que está salvo no aparelho:
 * músicas baixadas e músicas do celular), 2 Ajustes. O Player abre por cima ao tocar numa música ou no mini player;
 * o voltar dele só minimiza (a música continua tocando no mini player).
 */
@Composable
fun App(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var updateLater by remember { mutableStateOf(false) }      // "Depois": não mostra de novo nesta abertura do app
    val openPlayer = { showPlayer = true }
    val openDevice = { tab = 1 }
    CloudAuthEffect(vm.cloud)
    val bg by rememberCover(vm.currentTrack, 48)
    val target = bg?.let { lerp(Neon.Glow, Color(CoverLoader.averageColor(it)), 0.5f) } ?: Neon.Glow
    val glowColor by animateColorAsState(target, tween(700), label = "glow")
    if (vm.showTips) {
        GlassDialog(
            onDismissRequest = { vm.dismissTips() },
            title = { Text("Bem-vindo ao Toxic Player", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("• Início: tudo do seu Google Drive. Busque músicas e abra suas playlists (cada pasta vira uma).")
                    Text("• Biblioteca: só o que está salvo no aparelho, para ouvir sem internet.")
                    Text("• Ajustes: conta, equalizador e atualização. O app se atualiza sozinho.")
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissTips() }) { Text("Entendi", color = Neon.Green, fontWeight = FontWeight.Bold) } }
        )
    } else if (vm.update != null && !updateLater) {
        // Atualização disponível: aparece logo ao abrir o app. Só dá para seguir escolhendo "Atualizar agora" ou "Depois".
        UpdatePrompt(vm) { updateLater = true }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && tab != 0) { tab = 0 }
    Scaffold(
        containerColor = Neon.Bg,
        bottomBar = {
            if (!showPlayer) Column {
                AnimatedVisibility(visible = vm.currentTrack != null) { MiniPlayer(vm) { showPlayer = true } }
                NavBar(tab, vm.update != null) { tab = it }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().clipToBounds().aurora(glowColor)) {
            Column(Modifier.fillMaxSize()) {
                if (!showPlayer && !vm.cloud.online) OfflineBanner(openDevice)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (showPlayer) {
                        PlayerScreen(vm) { showPlayer = false }
                    } else {
                        Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { t ->
                            when (t) {
                                0 -> CloudScreen(vm, openPlayer, onDevice = openDevice)
                                1 -> OfflineLibrary(vm, granted, askPerm, openPlayer)
                                else -> SettingsScreen(vm, granted, askPerm, 0)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Aviso de atualização ao abrir o app. Não fecha tocando fora nem no voltar: o usuário escolhe "Atualizar agora" ou "Depois". */
@Composable
private fun UpdatePrompt(vm: PlayerViewModel, onLater: () -> Unit) {
    val info = vm.update ?: return
    var tried by remember { mutableStateOf(false) }
    GlassDialog(
        onDismissRequest = {},
        title = { Text("Nova versão disponível", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A versão ${info.versionName} está pronta para instalar.", fontSize = 15.sp)
                info.notes.trim().takeIf { it.isNotEmpty() }?.let { Text(it.take(240), fontSize = 12.sp, color = Neon.Dim) }
                if (vm.progress >= 0f) {
                    LinearProgressIndicator(progress = { vm.progress }, color = Neon.Green, trackColor = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().clip(CircleShape))
                }
                if (tried && vm.updateMsg.isNotBlank()) Text(vm.updateMsg, fontSize = 12.sp, color = Neon.Dim)
            }
        },
        confirmButton = {
            TextButton(onClick = { tried = true; vm.installUpdate() }, enabled = !vm.busy) {
                Text(if (vm.busy) "Baixando..." else "Atualizar agora", color = if (vm.busy) Neon.Faint else Neon.Green, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onLater, enabled = !vm.busy) { Text("Depois") } }
    )
}

/** Aviso leve de "sem internet". O app continua funcionando com o que está salvo no aparelho. */
@Composable
private fun OfflineBanner(onDevice: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp).glass(16.dp).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = Neon.Dim, modifier = Modifier.size(18.dp))
        Text("Sem internet. Mostrando o que está salvo no aparelho.", Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 12.sp, color = Neon.Dim)
        MiniAction("No aparelho", Neon.Green, onDevice)
    }
}

@Composable
private fun NavBar(tab: Int, hasUpdate: Boolean, onTab: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val items = listOf(
        Triple("Início", Icons.Rounded.Home, 0),
        Triple("Biblioteca", Icons.Rounded.LibraryMusic, 1),
        Triple("Ajustes", Icons.Rounded.Settings, 2)
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).glass(30.dp, strong = true).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { (label, icon, i) ->
            val sel = tab == i
            val tint by animateColorAsState(if (sel) Neon.Green else Neon.Dim, tween(200), label = "navTint")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                    .then(
                        if (sel) Modifier.background(Brush.verticalGradient(listOf(Neon.Green.copy(alpha = 0.26f), Neon.Green.copy(alpha = 0.08f))))
                        else Modifier
                    )
                    .clickable { if (!sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTab(i) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                    if (i == 2 && hasUpdate) Box(Modifier.size(8.dp).clip(CircleShape).background(Neon.Lime).align(Alignment.TopEnd))
                }
                Text(label, fontSize = 10.sp, color = tint, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Mini player: aparece em todas as abas (menos com o Player aberto) enquanto houver uma música carregada. Tocar nele abre o Player. */
@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val cover by rememberCover(vm.currentTrack, 128)
    val frac = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 8.dp)
            .glass(24.dp, strong = true).clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Neon.Glass), contentAlignment = Alignment.Center) {
                val c = cover
                if (c != null) Image(c.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Neon.Green)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(vm.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.artist, fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = vm::toggle) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Tocar/Pausar", tint = Neon.Green, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Próxima", modifier = Modifier.size(26.dp)) }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Neon.Brand))
        }
    }
}

// ───────────── Componentes compartilhados ─────────────
@Composable
private fun neonSlider() = SliderDefaults.colors(
    thumbColor = Neon.Green, activeTrackColor = Neon.Green, inactiveTrackColor = Color.White.copy(alpha = 0.14f)
)

@Composable
internal fun neonSwitch() = SwitchDefaults.colors(
    checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen,
    uncheckedTrackColor = Color.White.copy(alpha = 0.10f), uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
    uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
)

/** Slider do app: trilho verde e bolinha com degradê da marca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeonSlider(
    value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Slider(
        value = value, onValueChange = onChange, modifier = modifier, valueRange = range, colors = neonSlider(),
        thumb = { Box(Modifier.size(20.dp).clip(CircleShape).background(Neon.Brand).border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)) }
    )
}

@Composable
fun rememberCover(track: Track?, size: Int): State<Bitmap?> {
    val ctx = LocalContext.current.applicationContext
    return produceState<Bitmap?>(null, track?.id, track?.coverStamp, size) {
        value = if (track == null) null else withContext(Dispatchers.IO) { CoverLoader.load(ctx, track, size) }
    }
}

internal fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
internal fun plural(n: Int) = if (n == 1) "1 música" else "$n músicas"
private fun toDb(v: Float) = 20f * log10(max(v, 1e-6f))

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Neon.Brand).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Neon.OnGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1) }
}

@Composable
internal fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(CircleShape)
            .background(Neon.Green.copy(alpha = 0.08f))
            .border(1.dp, Neon.Green.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Neon.Green, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
internal fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) =
    Text(
        text,
        Modifier.clip(CircleShape)
            .background(if (selected) Neon.Green.copy(alpha = 0.20f) else Neon.Glass)
            .border(1.dp, if (selected) Neon.Green.copy(alpha = 0.8f) else Neon.Border, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        fontSize = 13.sp, color = if (selected) Neon.Green else Color.White,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1
    )

@Composable
internal fun Title(text: String) = Text(text, fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim, fontWeight = FontWeight.SemiBold)

/** Cabeçalho de página: linha pequena colorida + título grande + espaço para um botão à direita. */
@Composable
internal fun PageHeader(overline: String, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(overline, color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = Neon.Faint, modifier = Modifier.size(46.dp))
        Text(text, color = Neon.Dim, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.10f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            focusedBorderColor = Neon.Green.copy(alpha = 0.75f), unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            cursorColor = Neon.Green
        ),
        placeholder = { Text(label, color = Neon.Faint, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Limpar busca", tint = Neon.Dim) }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/** Abas em formato de pílula de vidro (usada na Biblioteca e no Equalizador). */
@Composable
internal fun SegmentedTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().glass(22.dp).padding(4.dp)) {
        items.forEachIndexed { i, n ->
            val sel = i == selected
            Text(
                n,
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .then(
                        if (sel) Modifier.background(Brush.verticalGradient(listOf(Neon.Green.copy(alpha = 0.30f), Neon.Green.copy(alpha = 0.12f))))
                        else Modifier
                    )
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                textAlign = TextAlign.Center, fontSize = 13.sp, maxLines = 1,
                color = if (sel) Neon.Green else Neon.Dim,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/** Barrinhas animadas que indicam a música em reprodução. */
@Composable
internal fun NowBars(playing: Boolean) {
    val t = rememberInfiniteTransition()
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse))
    val b by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse))
    val c by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse))
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(a, b, c).forEach { v ->
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) v else 0.3f).clip(CircleShape).background(Neon.Green))
        }
    }
}

/** Miniatura da capa (com as barrinhas por cima quando é a música atual). */
@Composable
internal fun TrackThumb(track: Track?, now: Boolean, playing: Boolean, size: Dp = 52.dp) {
    val thumb by rememberCover(track, 128)
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Neon.Glass), contentAlignment = Alignment.Center) {
        val tb = thumb
        if (tb != null) Image(tb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = if (now) Neon.Green else Neon.Dim)
        if (now) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { NowBars(playing) }
    }
}

/** Linha de grupo (pasta, artista, álbum, lista, gênero). */
@Composable
internal fun GroupTile(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(20.dp).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Neon.Green.copy(alpha = 0.30f), Neon.Lime.copy(alpha = 0.10f)))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Neon.Green) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(plural(count), fontSize = 12.sp, color = Neon.Dim)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Neon.Faint)
    }
}

/** Botãozinho em pílula (ações secundárias em listas). */
@Composable
internal fun MiniAction(text: String, color: Color = Neon.Dim, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(CircleShape).background(Neon.Glass).border(1.dp, Neon.Border, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        color = color, fontSize = 12.sp, maxLines = 1
    )
}

// ───────────── Player ─────────────
@Composable
fun PlayerScreen(vm: PlayerViewModel, onClose: (() -> Unit)? = null) {
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    val track = vm.currentTrack
    val cover by rememberCover(track, 600)
    val tint = cover?.let { Color(CoverLoader.averageColor(it)) } ?: Neon.Green
    if (showQueue) QueueDialog(vm) { showQueue = false }
    if (showTimer) TimerDialog { showTimer = false }
    Box(Modifier.fillMaxSize()) {
        val bmp = cover
        // Fundo: a própria capa, bem desfocada (o desfoque só existe no Android 12+).
        if (bmp != null && Build.VERSION.SDK_INT >= 31) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize().blur(80.dp).alpha(0.32f), contentScale = ContentScale.Crop)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availH = maxHeight
            val landscape = maxWidth > availH
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerCover(bmp, tint, vm.isPlaying, (availH - 40.dp).coerceAtLeast(120.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PlayerPanel(vm, track, { showQueue = true }, { showTimer = true })
                    }
                }
            } else {
                val side = minOf(maxWidth - 56.dp, availH * 0.34f).coerceAtLeast(150.dp)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimizar o player", tint = Neon.Green, modifier = Modifier.size(30.dp)) }
                            Text("TOXIC PLAYER", color = Neon.Green, fontSize = 12.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                        }
                        if (vm.queue.isNotEmpty()) {
                            Text(
                                "${vm.queueIndex + 1} / ${vm.queue.size}", fontSize = 12.sp, color = Neon.Dim,
                                modifier = Modifier.glass(14.dp).padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    PlayerCover(bmp, tint, vm.isPlaying, side)
                    PlayerPanel(vm, track, { showQueue = true }, { showTimer = true })
                }
            }
        }
        val screen = androidx.compose.ui.platform.LocalConfiguration.current
        if (onClose != null && screen.screenWidthDp > screen.screenHeightDp) {
            IconButton(onClick = onClose, Modifier.align(Alignment.TopStart).padding(4.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Minimizar o player", tint = Neon.Green, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** Capa em destaque: cantos grandes, brilho na cor da capa e leve "respiro" ao tocar/pausar. */
@Composable
private fun PlayerCover(cover: Bitmap?, tint: Color, playing: Boolean, side: Dp) {
    val scale by animateFloatAsState(if (playing) 1f else 0.92f, spring(dampingRatio = 0.7f, stiffness = 200f), label = "coverScale")
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.size(side)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent), center = center, radius = size.minDimension * 0.85f),
                    radius = size.minDimension * 0.85f
                )
            }
            .clip(shape).background(Neon.Surface)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), shape),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Neon.Green.copy(alpha = 0.5f), modifier = Modifier.size(side / 2.5f))
        }
    }
}

@Composable
private fun PlayerPanel(vm: PlayerViewModel, track: Track?, openQueue: () -> Unit, openTimer: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vm.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.artist, fontSize = 15.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.playError?.let { Text(it, fontSize = 12.sp, color = Neon.Danger) }
            }
            if (track != null) {
                val fav = vm.isFav(track.id)
                IconButton(onClick = { vm.toggleFav(track.id) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favoritar",
                        tint = if (fav) Neon.Green else Neon.Dim, modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        LiveSpectrum(vm.isPlaying)
        Column {
            NeonSlider(
                value = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f,
                onChange = { vm.seek((it * vm.duration).toLong()) }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(vm.position), fontSize = 12.sp, color = Neon.Dim)
                Text(fmt(vm.duration), fontSize = 12.sp, color = Neon.Dim)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Aleatório", tint = if (vm.shuffle) Neon.Green else Neon.Dim) }
            IconButton(onClick = vm::prev) { Icon(Icons.Rounded.SkipPrevious, "Anterior", modifier = Modifier.size(38.dp)) }
            Box(
                Modifier.size(76.dp).drawBehind { drawCircle(Neon.Green.copy(alpha = 0.16f), size.minDimension * 0.6f) }
                    .clip(CircleShape).background(Neon.Brand)
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Tocar/Pausar",
                    tint = Neon.OnGreen, modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Próxima", modifier = Modifier.size(38.dp)) }
            IconButton(onClick = vm::toggleRepeat) { Icon(Icons.Rounded.Repeat, "Repetir", tint = if (vm.repeat) Neon.Green else Neon.Dim) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill(
                Icons.Rounded.AutoAwesome,
                if (AudioFx.enabled) "Efeitos: ${AudioFx.activeName}" else "Efeitos desligados",
                AudioFx.enabled
            ) { AudioFx.setMasterEnabled(!AudioFx.enabled) }
            ActionPill(Icons.Rounded.QueueMusic, "Fila (${vm.queue.size})", false, openQueue)
            ActionPill(
                Icons.Rounded.Bedtime,
                if (SleepTimer.leftSec > 0) "Timer ${SleepTimer.leftSec / 60 + 1} min" else "Timer",
                SleepTimer.leftSec > 0, openTimer
            )
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, text: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(if (active) Neon.Green.copy(alpha = 0.18f) else Neon.Glass)
            .border(1.dp, if (active) Neon.Green.copy(alpha = 0.7f) else Neon.Border, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = if (active) Neon.Green else Neon.Dim, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, color = if (active) Neon.Green else Color.White, maxLines = 1)
    }
}

@Composable
private fun QueueDialog(vm: PlayerViewModel, onClose: () -> Unit) {
    GlassDialog(
        onDismissRequest = onClose,
        title = { Text("Fila (${vm.queue.size})", fontWeight = FontWeight.Bold) },
        text = {
            if (vm.queue.isEmpty()) {
                Text("A fila está vazia.", color = Neon.Dim)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(vm.queue.size) { i ->
                        val t = vm.queue[i]
                        val now = i == vm.queueIndex
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .then(if (now) Modifier.background(Neon.Green.copy(alpha = 0.10f)) else Modifier)
                                .clickable { vm.playQueueIndex(i) }.padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${i + 1}", color = if (now) Neon.Green else Neon.Dim, fontSize = 12.sp, modifier = Modifier.width(26.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = if (now) Neon.Green else Color.White)
                                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Neon.Dim)
                            }
                            if (i > 0) IconButton(onClick = { vm.moveQueueItem(i, i - 1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir", tint = Neon.Dim) }
                            IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Rounded.Close, "Remover", tint = Neon.Dim) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar", color = Neon.Green, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun TimerDialog(onClose: () -> Unit) {
    GlassDialog(
        onDismissRequest = onClose,
        title = { Text("Timer para dormir", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (SleepTimer.leftSec > 0) "Pausa em ${SleepTimer.leftSec / 60}:${"%02d".format(SleepTimer.leftSec % 60)} (com fade-out)" else "Desligado",
                    color = Neon.Dim
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 45, 60, 90).forEach { m -> Chip("$m min") { SleepTimer.start(m); onClose() } }
                }
                if (SleepTimer.leftSec > 0) GhostButton("Cancelar timer", Modifier.fillMaxWidth()) { SleepTimer.cancel(); onClose() }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar", color = Neon.Green, fontWeight = FontWeight.Bold) } }
    )
}

/** Espectro REAL (FFT do áudio que sai do motor de DSP). */
@Composable
private fun LiveSpectrum(active: Boolean) {
    var bars by remember { mutableStateOf(FloatArray(24)) }
    LaunchedEffect(active) {
        while (true) {
            val raw = if (active) DspEngine.core.spectrum(24) else null
            val old = bars
            // Parado e já zerado: não mexe no estado (evita redesenhar à toa).
            if (raw != null || old.any { it > 0.005f }) bars = FloatArray(24) { max(raw?.get(it) ?: 0f, old[it] * 0.8f) }
            delay(if (active) 50L else 150L)
        }
    }
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 24) {
            Box(
                Modifier.weight(1f).fillMaxHeight(0.05f + bars[i] * 0.95f).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Neon.Lime, Neon.Green.copy(alpha = 0.18f))))
            )
        }
    }
}

/** Marca do app desenhada em código: trevo de radiação com o degradê da marca. */
@Composable
internal fun ToxicMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Neon.Green.copy(alpha = 0.28f), Color.Transparent), center = c, radius = r), radius = r)
        val big = r * 0.78f
        val mid = big * 0.69f
        for (k in 0 until 3) {
            drawArc(
                Neon.Brand, startAngle = -120f + 120f * k, sweepAngle = 60f, useCenter = false,
                topLeft = Offset(c.x - mid, c.y - mid), size = Size(mid * 2f, mid * 2f),
                style = Stroke(width = big * 0.62f)
            )
        }
        drawCircle(Neon.Brand, radius = big * 0.2f, center = c)
        drawCircle(Neon.Green.copy(alpha = 0.35f), radius = big * 1.12f, center = c, style = Stroke(1.5.dp.toPx()))
    }
}

// ───────────── Biblioteca ─────────────
@Composable
fun LibraryScreen(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit, goPlayer: () -> Unit) {
    val open = vm.openFolder
    BackHandler(enabled = open != null) { vm.enterFolder(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 22.dp)) {
        if (!granted) {
            PageHeader("NO APARELHO", "Músicas do celular")
            PermissionCard("Permita o acesso às músicas do celular para ver e tocar a sua biblioteca.", "Permitir acesso", askPerm)
        } else if (open != null) {
            TrackList(vm, vm.tracksIn(open), groupTitle(vm, open), true, goPlayer, if (open.startsWith("P:")) open.drop(2) else null)
        } else {
            LibraryRoot(vm, goPlayer)
        }
    }
}

private fun groupTitle(vm: PlayerViewModel, key: String): String = when {
    key == ALL_FOLDERS -> "Todas as músicas"
    key.startsWith("P:") -> key.drop(2)
    else -> vm.groups(0).plus(vm.groups(1)).plus(vm.groups(2)).firstOrNull { it.path == key }?.name ?: "Grupo"
}

/** Resultado das playlists do Drive, em destaque (borda e título verdes), com as faixas não encontradas. */
@Composable
internal fun SmartInfoCard(info: com.meuapp.player.cloud.SmartInfo, onClose: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Neon.Green.copy(alpha = 0.10f)).border(1.5.dp, Neon.Green.copy(alpha = 0.75f), shape)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PLAYLISTS DO DRIVE", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
            Text(info.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (info.report.isNotBlank()) {
                Text("Não encontradas:", fontSize = 12.sp, color = Neon.Dim, modifier = Modifier.padding(top = 4.dp))
                Text(info.report, fontSize = 12.sp, color = Neon.Dim)
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fechar aviso", tint = Neon.Dim) }
    }
}

@Composable
private fun LibraryRoot(vm: PlayerViewModel, goPlayer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlistMessage by remember { mutableStateOf("") }
    val playlistPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { com.meuapp.player.SafeRead.readText(ctx, uri) }
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?')?.substringBeforeLast('.') ?: "Playlist importada"
            val count = vm.importPlaylist(name, com.meuapp.player.M3uPlaylist.parse(text.orEmpty()))
            playlistMessage = if (text == null) "Arquivo ilegível ou maior que 1 MB." else if (count > 0) "Playlist importada: $count faixas." else "Nenhuma faixa da playlist foi encontrada na biblioteca do celular."
        }
    }
    var newListDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pickFor by remember { mutableStateOf<Track?>(null) }
    val searching = vm.query.isNotBlank()
    val folderGroups = remember(vm.tracks, vm.libMode) { vm.groups(vm.libMode) }
    val groups = if (vm.libMode == 3) vm.groups(3) else folderGroups
    val icon = when (vm.libMode) { 1 -> Icons.Rounded.Person; 2 -> Icons.Rounded.Album; 3 -> Icons.Rounded.QueueMusic; else -> Icons.Rounded.Folder }
    val results = if (searching) vm.searchResults() else emptyList()

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PageHeader("NO APARELHO", plural(vm.tracks.size)) }
        item { SearchField(vm.query, vm::changeQuery, "Buscar música, artista, álbum ou pasta") }
        if (searching) {
            item { Text("Resultados · ${plural(results.size)}", fontSize = 13.sp, color = Neon.Dim, modifier = Modifier.padding(top = 4.dp)) }
            if (results.isEmpty()) item { EmptyState(Icons.Rounded.Search, "Nada encontrado para \"${vm.query.trim()}\".") }
            items(results.size) { i ->
                TrackRow(vm, results[i], null, { vm.playList(results, i); goPlayer() }, { pickFor = results[i] })
            }
        } else {
            item { SegmentedTabs(listOf("Pastas", "Artistas", "Álbuns", "Listas"), vm.libMode) { vm.changeLibMode(it) } }
            if (vm.libMode == 3) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Criar lista", Modifier.weight(1f)) { newListDialog = true }
                        GhostButton("Importar .m3u", Modifier.weight(1f)) {
                            playlistPicker.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
                        }
                    }
                }
                if (playlistMessage.isNotBlank()) item { Text(playlistMessage, fontSize = 12.sp, color = Neon.Dim) }
            }
            if (vm.tracks.isEmpty() && vm.libMode != 3) {
                item { EmptyState(Icons.Rounded.LibraryMusic, "Nenhuma música encontrada no aparelho.") }
            } else {
                if (vm.libMode != 3) item { GroupTile("Todas as músicas", vm.tracks.size, Icons.Rounded.LibraryMusic) { vm.enterFolder(ALL_FOLDERS) } }
                items(groups) { g -> GroupTile(g.name, g.count, if (g.name == FAVORITES) Icons.Rounded.Favorite else icon) { vm.enterFolder(g.path) } }
            }
        }
    }
    if (newListDialog) {
        GlassDialog(
            onDismissRequest = { newListDialog = false },
            title = { Text("Nova lista", fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Nome da lista") }) },
            confirmButton = { TextButton(onClick = { vm.createPlaylist(newName); newName = ""; newListDialog = false }) { Text("Criar", color = Neon.Green, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { newListDialog = false }) { Text("Cancelar") } }
        )
    }
    pickFor?.let { tr -> AddToListDialog(vm, tr) { pickFor = null } }
}

@Composable
internal fun TrackList(
    vm: PlayerViewModel, list: List<Track>, name: String, showBack: Boolean,
    goPlayer: () -> Unit, playlist: String? = null, onBack: () -> Unit = { vm.enterFolder(null) }
) {
    var pickFor by remember { mutableStateOf<Track?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Neon.Green) }
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plural(list.size), fontSize = 12.sp, color = Neon.Dim)
                }
            }
        }
        if (playlist == null) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Título", vm.sortMode == 0) { vm.changeSort(0) }
                Chip("Artista", vm.sortMode == 1) { vm.changeSort(1) }
                Chip("Duração", vm.sortMode == 2) { vm.changeSort(2) }
            }
        }
        if (list.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PrimaryButton("Tocar tudo") { vm.playList(list, 0); goPlayer() } }
                GhostButton("Aleatório", Modifier.width(128.dp)) { vm.playList(list.shuffled(), 0); goPlayer() }
            }
        }
        if (playlist != null && playlist != FAVORITES) item {
            GhostButton("Apagar esta lista", Modifier.fillMaxWidth()) { vm.deletePlaylist(playlist) }
        }
        if (list.isEmpty()) item { EmptyState(Icons.Rounded.MusicNote, "Nenhuma música aqui ainda.") }
        items(list.size) { i ->
            val t = list[i]
            TrackRow(vm, t, playlist, { vm.playList(list, i); goPlayer() }, { pickFor = t })
        }
    }
    pickFor?.let { tr -> AddToListDialog(vm, tr) { pickFor = null } }
}

@Composable
private fun TrackRow(vm: PlayerViewModel, t: Track, playlist: String?, onPlay: () -> Unit, onAdd: () -> Unit) {
    val now = vm.currentTrack?.id == t.id
    val fav = vm.isFav(t.id)
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .then(
                if (now) Modifier.background(Neon.Green.copy(alpha = 0.10f)).border(1.dp, Neon.Green.copy(alpha = 0.35f), shape)
                else Modifier.background(Neon.Glass)
            )
            .clickable(onClick = onPlay).padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackThumb(t, now, vm.isPlaying)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (now) Neon.Green else Color.White, fontWeight = FontWeight.Medium)
            Text("${t.artist} · ${fmt(t.durationMs)}", fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.toggleFav(t.id) }) {
            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favoritar", tint = if (fav) Neon.Green else Neon.Dim)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Neon.Dim) }
            GlassMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Tocar a seguir") }, onClick = { vm.playNext(t); menu = false })
                DropdownMenuItem(text = { Text("Adicionar à fila") }, onClick = { vm.addToQueue(t); menu = false })
                DropdownMenuItem(text = { Text("Adicionar à lista...") }, onClick = { onAdd(); menu = false })
                if (playlist != null) {
                    DropdownMenuItem(text = { Text("Remover desta lista") }, onClick = { vm.removeFromPlaylist(playlist, t.id); menu = false })
                }
            }
        }
    }
}

@Composable
internal fun AddToListDialog(vm: PlayerViewModel, track: Track, onClose: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    GlassDialog(
        onDismissRequest = onClose,
        title = { Text("Adicionar à lista", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    vm.playlists.keys.sorted().forEach { n ->
                        Text(
                            n,
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { vm.addToPlaylist(n, track.id); onClose() }.padding(12.dp),
                            color = Color.White
                        )
                    }
                }
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Ou crie uma nova lista") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (vm.createPlaylist(newName)) vm.addToPlaylist(newName.trim(), track.id)
                onClose()
            }) { Text("Criar e adicionar", color = Neon.Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Fechar") } }
    )
}

@Composable
internal fun PermissionCard(text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(28.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Neon.Green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Neon.Green, modifier = Modifier.size(32.dp))
        }
        Text(text, color = Neon.Dim, textAlign = TextAlign.Center)
        PrimaryButton(button, onClick)
    }
}

// ───────────── Mesa de áudio ─────────────
@Composable
fun MesaScreen(vm: PlayerViewModel) {
    var sec by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 22.dp)) {
        PageHeader("MESA DE ÁUDIO", "Toxic Sound") {
            Switch(checked = AudioFx.enabled, onCheckedChange = { AudioFx.setMasterEnabled(it) }, colors = neonSwitch())
        }
        if (!AudioFx.ready) {
            Text("Toque uma música para ativar os efeitos do sistema.", color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        SegmentedTabs(listOf("EQ", "Carro", "Dinâmica", "Efeitos", "Presets"), sec) { sec = it }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusCard()
            MetersCard()
            when (sec) {
                0 -> EqSection()
                1 -> CarSection()
                2 -> DynSection()
                3 -> FxSection(vm)
                else -> PresetSection()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Cartão recolhível: toque no cabeçalho para abrir/fechar. Opcionalmente com chave liga/desliga. */
@Composable
private fun Panel(
    title: String, subtitle: String = "", checked: Boolean? = null, onChecked: (Boolean) -> Unit = {},
    startOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit
) {
    var open by remember { mutableStateOf(startOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "panelChevron")
    Column(Modifier.fillMaxWidth().glass(22.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Title(title)
                if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = Neon.Faint, modifier = Modifier.padding(top = 2.dp))
            }
            if (checked != null) Switch(checked = checked, onCheckedChange = onChecked, colors = neonSwitch())
            Icon(Icons.Rounded.ExpandMore, if (open) "Recolher" else "Expandir", tint = Neon.Dim, modifier = Modifier.padding(start = 6.dp).rotate(rot))
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

@Composable
private fun LevelBar(frac: Float, hot: Boolean) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
        Box(
            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight()
                .background(if (hot) SolidColor(Neon.Danger) else Brush.horizontalGradient(listOf(Neon.Green, Neon.Lime)))
        )
    }
}

/** Medidores reais de pico, RMS, redução de ganho, headroom e clipping. */
@Composable
private fun MetersCard() {
    var m by remember { mutableStateOf(Meters()) }
    var shownL by remember { mutableFloatStateOf(0f) }
    var shownR by remember { mutableFloatStateOf(0f) }
    var clipUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val cur = DspEngine.core.meters
            m = cur
            shownL = max(cur.peakL, shownL * 0.85f)
            shownR = max(cur.peakR, shownR * 0.85f)
            if (cur.clip) clipUntil = System.currentTimeMillis() + 1500
            delay(60)
        }
    }
    val clipping = System.currentTimeMillis() < clipUntil
    val pL = toDb(shownL); val pR = toDb(shownR)
    val headroom = -max(pL, pR)
    Column(Modifier.fillMaxWidth().glass(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title("MEDIDORES")
            Text(
                if (clipping) "CLIPPING!" else "Headroom ${"%.1f".format(headroom.coerceAtMost(60f))} dB",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (clipping) Neon.Danger else Neon.Green
            )
        }
        LevelBar((pL + 60f) / 60f, pL > -1f)
        LevelBar((pR + 60f) / 60f, pR > -1f)
        Text("RMS  L ${"%.0f".format(toDb(m.rmsL))} · R ${"%.0f".format(toDb(m.rmsR))} dB", fontSize = 11.sp, color = Neon.Dim)
        Text(
            "Comp -${"%.1f".format(m.compGrDb)} · MB -${"%.1f".format(m.mbGrDb)} · Lim -${"%.1f".format(m.limGrDb)} · Nív ${"%+.1f".format(m.levGainDb)} dB",
            fontSize = 11.sp, color = Neon.Dim
        )
    }
}

// ---- EQ ----
@Composable
private fun EqCurve() {
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val pts = 100
        val curve = AudioFx.responseCurve(pts)
        fun y(db: Float) = size.height * (1f - ((db.coerceIn(-18f, 18f)) + 18f) / 36f)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y(0f)), Offset(size.width, y(0f)), 1.dp.toPx())
        val line = Path()
        val area = Path()
        area.moveTo(0f, y(0f))
        for (i in 0 until pts) {
            val x = size.width * i / (pts - 1)
            val yy = y(curve[i])
            if (i == 0) line.moveTo(x, yy) else line.lineTo(x, yy)
            area.lineTo(x, yy)
        }
        area.lineTo(size.width, y(0f))
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(Neon.Green.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, Brush.horizontalGradient(listOf(Neon.Green, Neon.Lime)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Slider vertical (um slider normal girado). */
@Composable
private fun VSlider(value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range, colors = neonSlider(),
        modifier = modifier
            .layout { measurable, c ->
                val p = measurable.measure(
                    Constraints(minWidth = c.minHeight, maxWidth = c.maxHeight, minHeight = c.minWidth, maxHeight = c.maxWidth)
                )
                layout(p.height, p.width) { p.place(-p.width / 2 + p.height / 2, p.width / 2 - p.height / 2) }
            }
            .graphicsLayer(rotationZ = 270f)
    )
}

private fun freqLabel(f: Int) = if (f >= 1000) "${f / 1000}k" else "$f"

@Composable
private fun EqSection() {
    var param by remember { mutableStateOf(false) }
    SegmentedTabs(listOf("Gráfico · 10 bandas", "Paramétrico"), if (param) 1 else 0) { param = it == 1 }
    Column(Modifier.fillMaxWidth().glass(22.dp).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Title("RESPOSTA COMBINADA")
            Text("±18 dB", fontSize = 11.sp, color = Neon.Dim)
        }
        Spacer(Modifier.height(6.dp))
        EqCurve()
    }
    if (!param) {
        Column(Modifier.fillMaxWidth().glass(22.dp).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Title("EQ GRÁFICO")
                Switch(checked = AudioFx.eqOn, onCheckedChange = { AudioFx.eqOn = it; AudioFx.apply() }, colors = neonSwitch())
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                for (i in 0 until AudioFx.bands.size) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%+.0f".format(AudioFx.bands[i]), fontSize = 9.sp, color = Neon.Green)
                        VSlider(AudioFx.bands[i], -12f..12f, Modifier.height(170.dp).fillMaxWidth()) { AudioFx.bands[i] = it; AudioFx.apply() }
                        Text(freqLabel(AudioFx.EQ_FREQS[i]), fontSize = 9.sp, color = Neon.Dim)
                    }
                }
            }
        }
        GhostButton("Zerar equalizador gráfico", Modifier.fillMaxWidth()) { AudioFx.applyPreset("Flat") }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title("FILTROS (${AudioFx.filters.size}/${DspCore.MAX_PARAM})")
            Switch(checked = AudioFx.paramOn, onCheckedChange = { AudioFx.paramOn = it; AudioFx.apply() }, colors = neonSwitch())
        }
        AudioFx.filters.toList().forEachIndexed { i, f -> FilterCard(i, f) }
        if (AudioFx.filters.size < DspCore.MAX_PARAM) PrimaryButton("Adicionar filtro") { AudioFx.addFilter() }
    }
}

@Composable
private fun FilterCard(i: Int, f: EqFilter) {
    Column(Modifier.fillMaxWidth().glass(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filtro ${i + 1}", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = f.enabled, onCheckedChange = { AudioFx.updateFilter(i, f.copy(enabled = it)) }, colors = neonSwitch())
                MiniAction("Remover", Neon.Danger) { AudioFx.removeFilter(i) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterType.values().forEach { t -> Chip(t.label, f.type == t) { AudioFx.updateFilter(i, f.copy(type = t)) } }
        }
        val lo = log10(20f); val hi = log10(20000f)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Frequência", fontSize = 12.sp, color = Neon.Dim)
            Text(if (f.freq >= 1000) "%.2f kHz".format(f.freq / 1000f) else "%.0f Hz".format(f.freq), fontSize = 12.sp, color = Neon.Green)
        }
        NeonSlider(
            value = log10(f.freq).coerceIn(lo, hi), onChange = { AudioFx.updateFilter(i, f.copy(freq = 10f.pow(it))) },
            range = lo..hi
        )
        if (f.type.usesGain) {
            SliderRow("Ganho", f.gainDb, -18f..18f, "dB", 1) { AudioFx.updateFilter(i, f.copy(gainDb = it)) }
        }
        SliderRow("Q (largura)", f.q, 0.2f..10f, "", 2, hint = "Baixo = faixa larga e suave; alto = estreita") { AudioFx.updateFilter(i, f.copy(q = it)) }
    }
}

@Composable
private fun SliderRow(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    decimals: Int = 0, hint: String? = null, onChange: (Float) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Neon.Dim, modifier = Modifier.weight(1f))
            Text(
                String.format("%.${decimals}f", value) + " " + unit, fontSize = 13.sp, color = Neon.Green, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Neon.Green.copy(alpha = 0.12f)).clickable { editing = true }.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (hint != null) Text(hint, fontSize = 10.sp, color = Neon.Faint)
        NeonSlider(value = value, onChange = onChange, range = range)
    }
    if (editing) {
        var txt by remember { mutableStateOf(String.format("%.${decimals}f", value)) }
        GlassDialog(
            onDismissRequest = { editing = false },
            title = { Text(label, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = txt, onValueChange = { txt = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(if (unit.isBlank()) "valor" else unit) }, supportingText = { Text("De ${range.start} a ${range.endInclusive}") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    txt.replace(',', '.').toFloatOrNull()?.let { onChange(it.coerceIn(range.start, range.endInclusive)) }
                    editing = false
                }) { Text("OK", color = Neon.Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = Neon.Dim)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = neonSwitch())
    }
}

// ---- Dinâmica ----
@Composable
private fun DynSection() {
    Panel("COMPRESSOR", "Processamento próprio, joelho suave", AudioFx.compOn, { AudioFx.compOn = it; AudioFx.apply() }) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Detector RMS", AudioFx.compRms) { AudioFx.compRms = true; AudioFx.apply() }
            Chip("Detector Pico", !AudioFx.compRms) { AudioFx.compRms = false; AudioFx.apply() }
        }
        SliderRow("Threshold", AudioFx.thr, -60f..0f, "dB", 1, hint = "Nível a partir do qual o compressor começa a atuar") { AudioFx.thr = it; AudioFx.apply() }
        SliderRow("Ratio", AudioFx.ratio, 1f..20f, ": 1", 1, hint = "Quanto o excesso é reduzido (2:1 suave, 10:1 firme)") { AudioFx.ratio = it; AudioFx.apply() }
        SliderRow("Knee (joelho)", AudioFx.knee, 0f..24f, "dB", 1, hint = "Alto = transição suave; 0 = brusca") { AudioFx.knee = it; AudioFx.apply() }
        SliderRow("Attack", AudioFx.attack, 0.1f..100f, "ms", 1, hint = "Alto preserva o ataque dos graves; baixo segura picos") { AudioFx.attack = it; AudioFx.apply() }
        SliderRow("Release", AudioFx.release, 10f..1000f, "ms", hint = "Alto deixa o som mais contínuo e suave") { AudioFx.release = it; AudioFx.apply() }
        SliderRow("Ganho de compensação", AudioFx.makeup, 0f..18f, "dB", 1, hint = "Devolve o volume perdido na compressão") { AudioFx.makeup = it; AudioFx.apply() }
        SliderRow("Filtro do detector (sidechain)", AudioFx.compScHpf, 0f..500f, "Hz", 0, hint = "0 = desligado. Impede que os graves disparem a compressão") { AudioFx.compScHpf = it; AudioFx.apply() }
    }
    Panel("LIMITADOR", "Look-ahead de 5 ms + proteção final no teto", AudioFx.limOn, { AudioFx.limOn = it; AudioFx.apply() }) {
        SwitchRow("True-peak (4×)", "Detecta picos entre as amostras. É uma aproximação, não um medidor certificado.", AudioFx.truePeak) { AudioFx.truePeak = it; AudioFx.apply() }
        SliderRow("Teto (ceiling)", AudioFx.limThr, -12f..0f, "dB", 1, hint = "Nível máximo de saída; -1 dB é seguro") { AudioFx.limThr = it; AudioFx.apply() }
        SliderRow("Release", AudioFx.limRelease, 10f..500f, "ms") { AudioFx.limRelease = it; AudioFx.apply() }
    }
    Panel(
        "COMPRESSOR MULTIBANDA", "3 bandas (graves, médios, agudos) com cruzamentos Linkwitz-Riley de 4ª ordem",
        AudioFx.mbcOn, { AudioFx.mbcOn = it; AudioFx.apply() }, startOpen = false
    ) {
        SliderRow("Cruzamento graves | médios", AudioFx.mbX1, 60f..600f, "Hz", 0, hint = "Abaixo desta frequência fica a banda de graves") { AudioFx.mbX1 = it; AudioFx.apply() }
        SliderRow("Cruzamento médios | agudos", AudioFx.mbX2, 1500f..8000f, "Hz") { AudioFx.mbX2 = it; AudioFx.apply() }
        SliderRow("Threshold · graves", AudioFx.mbThrL, -60f..0f, "dB", 1) { AudioFx.mbThrL = it; AudioFx.apply() }
        SliderRow("Threshold · médios", AudioFx.mbThrM, -60f..0f, "dB", 1) { AudioFx.mbThrM = it; AudioFx.apply() }
        SliderRow("Threshold · agudos", AudioFx.mbThrH, -60f..0f, "dB", 1) { AudioFx.mbThrH = it; AudioFx.apply() }
        SliderRow("Ratio", AudioFx.mbcRatio, 1f..20f, ": 1", 1) { AudioFx.mbcRatio = it; AudioFx.apply() }
        SliderRow("Attack", AudioFx.mbAttack, 0.5f..100f, "ms", 1) { AudioFx.mbAttack = it; AudioFx.apply() }
        SliderRow("Release", AudioFx.mbRelease, 20f..1000f, "ms") { AudioFx.mbRelease = it; AudioFx.apply() }
        SliderRow("Knee", AudioFx.mbKnee, 0f..24f, "dB", 1) { AudioFx.mbKnee = it; AudioFx.apply() }
        SliderRow("Ganho de compensação", AudioFx.mbMakeup, 0f..18f, "dB", 1) { AudioFx.mbMakeup = it; AudioFx.apply() }
    }
}

// ---- Efeitos / estéreo ----
@Composable
private fun FxSection(vm: PlayerViewModel) {
    Panel("GANHO E SEGURANÇA") {
        SliderRow("Pré-amplificador", AudioFx.preamp, -12f..12f, "dB", 1, hint = "Reduza se ouvir distorção ao reforçar graves") { AudioFx.preamp = it; AudioFx.apply() }
        SwitchRow("Atenuação automática", "Reduz o ganho pelo maior reforço do EQ para evitar clipping", AudioFx.autoHeadroom) { AudioFx.autoHeadroom = it; AudioFx.apply() }
        SliderRow("Balanço (esquerda ← → direita)", AudioFx.balance, -1f..1f, "", 2) { AudioFx.balance = it; AudioFx.apply() }
    }
    Panel("QUALIDADE DE ÁUDIO") {
        SwitchRow("Dither (TPDF)", "Evita distorção de arredondamento na saída de 16 bits", AudioFx.ditherOn) { AudioFx.ditherOn = it; AudioFx.apply() }
        SwitchRow("Nivelador de volume", "Aproxima o volume entre músicas. Não é ReplayGain: mede o som enquanto toca.", AudioFx.levelerOn) { AudioFx.levelerOn = it; AudioFx.apply() }
        if (AudioFx.levelerOn) {
            SliderRow("Volume alvo", AudioFx.levelerTarget, -24f..-10f, "dB", 0, hint = "Mais baixo = menos volume geral e mais folga") { AudioFx.levelerTarget = it; AudioFx.apply() }
        }
    }
    Panel("ESTÉREO", startOpen = false) {
        SliderRow("Largura (0 = mono, 1 = original)", AudioFx.width, 0f..2f, "x", 2) { AudioFx.width = it; AudioFx.apply() }
        SliderRow("Crossfeed (para fones)", AudioFx.crossfeed, 0f..1f, "", 2, hint = "Mistura leve entre os lados; reduz o som \"dentro da cabeça\"") { AudioFx.crossfeed = it; AudioFx.apply() }
        Text("A largura usa Mid/Side: o centro (mono) nunca é alterado.", fontSize = 11.sp, color = Neon.Dim)
    }
    Panel("EFEITOS DO SISTEMA", "Dependem do aparelho", startOpen = false) {
        SliderRow("Loudness (volume extra)", AudioFx.loudness, 0f..10f, "dB", 1) { AudioFx.loudness = it; AudioFx.apply() }
        SliderRow("Bass Boost", AudioFx.bassPct, 0f..100f, "%") { AudioFx.bassPct = it; AudioFx.apply() }
        SliderRow("Surround (virtualizador)", AudioFx.virtPct, 0f..100f, "%") { AudioFx.virtPct = it; AudioFx.apply() }
    }
    Panel("TRANSIÇÃO ENTRE MÚSICAS", startOpen = false) {
        SliderRow("Fade entre músicas", AudioFx.fadeSec, 0f..8f, "s", 1, hint = "0 = desligado. Escurece o fim e abre o início de cada faixa (sem sobreposição)") { AudioFx.setFade(it) }
    }
    Panel("VELOCIDADE E TOM", startOpen = false) {
        SliderRow("Velocidade", vm.speed, 0.5f..2f, "x", 2) { vm.changeSpeed(it) }
        SliderRow("Tom (pitch)", vm.pitch, 0.5f..2f, "x", 2) { vm.changePitch(it) }
    }
    GhostButton("Resetar todos os ajustes de áudio", Modifier.fillMaxWidth()) { AudioFx.resetAll() }
}


// ---- Estado em tempo real: o preset está valendo? O motor está processando? O que mudou no som? ----
/** Barra centrada em 0 dB: para a direita = reforço, para a esquerda = corte. Escala de ±12 dB. */
@Composable
private fun DiffBar(label: String, db: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = Neon.Dim, modifier = Modifier.width(112.dp))
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val mid = size.width / 2f
            drawRect(Color.White.copy(alpha = 0.10f), Offset.Zero, size)
            val len = (kotlin.math.abs(db).coerceAtMost(12f) / 12f) * mid
            val color = if (db >= 0f) Neon.Green else Neon.Danger.copy(alpha = 0.8f)
            if (db >= 0f) drawRect(color, Offset(mid, 0f), Size(len, size.height)) else drawRect(color, Offset(mid - len, 0f), Size(len, size.height))
            drawRect(Color.White.copy(alpha = 0.5f), Offset(mid - 0.5f, 0f), Size(1f, size.height))
        }
        Text("%+.1f dB".format(db), fontSize = 12.sp, color = Neon.Green, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
    }
}

/** Mostra, sem depender do ouvido, se os efeitos estão ligados, qual preset vale e quanto o som realmente mudou (entrada x saída). */
@Composable
private fun StatusCard() {
    var m by remember { mutableStateOf(Meters()) }
    var flowing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var last = DspEngine.core.meters.frames
        while (true) {
            delay(300)
            val cur = DspEngine.core.meters
            flowing = cur.frames != last
            last = cur.frames
            m = cur
        }
    }
    val on = AudioFx.enabled
    val custom = AudioFx.activeName == "Personalizado"
    val title = when {
        !on -> "EFEITOS DESLIGADOS"
        custom -> "EFEITOS LIGADOS · Personalizado"
        else -> "EFEITOS LIGADOS · ${AudioFx.activeName}"
    }
    val sub = when {
        !on -> "Você está ouvindo o som original, sem nenhum ajuste."
        custom -> "Ajustes feitos por você (nenhum preset pronto está valendo sem mudanças)."
        else -> "Preset em uso, sem alterações."
    }
    Column(Modifier.fillMaxWidth().glass(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Neon.Green else Neon.Faint))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Neon.Green else Neon.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(sub, fontSize = 11.sp, color = Neon.Dim)
        val engine = when {
            flowing && DspEngine.inChain -> "Motor processando o áudio agora · ${DspEngine.formatInfo}" to Neon.Green
            !DspEngine.inChain && DspEngine.formatInfo.isNotEmpty() -> DspEngine.formatInfo to Neon.Danger
            else -> "Nenhum áudio passando pelo motor. Toque uma música para ouvir e medir o efeito." to Neon.Dim
        }
        Text(engine.first, fontSize = 11.sp, color = engine.second)
        if (on && flowing && m.measured) {
            Text("O QUE MUDOU NO SOM (saída comparada com a entrada)", fontSize = 10.sp, letterSpacing = 1.sp, color = Neon.Faint, modifier = Modifier.padding(top = 4.dp))
            DiffBar("Graves 40–160 Hz", m.diffLowDb)
            DiffBar("Médios 160–4k Hz", m.diffMidDb)
            DiffBar("Agudos > 4 kHz", m.diffHighDb)
            if (m.bassLimGrDb > 0.5f) Text("Limitador de graves segurando ${"%.1f".format(m.bassLimGrDb)} dB (protege o alto-falante)", fontSize = 11.sp, color = Neon.Dim)
            Text("Se os graves sobem aqui e você não sente, o limite é o alto-falante ou o volume: alto-falante de porta quase não toca abaixo de 50 Hz.", fontSize = 10.sp, color = Neon.Faint)
        } else if (on && flowing) {
            Text("Medindo a diferença entre entrada e saída…", fontSize = 11.sp, color = Neon.Faint)
        }
        GhostButton(if (on) "Comparar: ouvir SEM efeitos" else "Voltar a ouvir COM efeitos", Modifier.fillMaxWidth()) { AudioFx.setMasterEnabled(!on) }
    }
}

// ---- Carro: processador de áudio (cortes, grave virtual, limitador de graves, alinhamento...) ----
@Composable
private fun CutPanel(
    title: String, subtitle: String, on: Boolean, onToggle: (Boolean) -> Unit,
    freq: Float, freqRange: ClosedFloatingPointRange<Float>, onFreq: (Float) -> Unit,
    slope: Int, onSlope: (Int) -> Unit, type: XoverType, onType: (XoverType) -> Unit, freqHint: String
) {
    Panel(title, subtitle, on, onToggle) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XoverType.values().forEach { t -> Chip(t.label, type == t) { onType(t) } }
        }
        Text("Inclinação (dB por oitava)", fontSize = 12.sp, color = Neon.Dim)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Crossover.SLOPES.filter { type == XoverType.BUTTERWORTH || it % 12 == 0 }.forEach { sl -> Chip("$sl", slope == sl) { onSlope(sl) } }
        }
        SliderRow("Frequência de corte", freq, freqRange, "Hz", 0, hint = freqHint) { onFreq(it) }
    }
}

@Composable
private fun CarSection() {
    Column(Modifier.fillMaxWidth().glass(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Title("PROCESSADOR DE ÁUDIO DE CARRO")
        Text(
            "Tudo aqui age no som que o celular manda para o rádio (2 canais: esquerdo e direito). Cortes, grave virtual e limitador protegem " +
                "alto-falantes comuns de porta e ainda deixam o grave mais presente. A divisão em vias (kit 2 vias, subwoofer com canal próprio) " +
                "é feita pelo seu amplificador ou rádio: aqui o app prepara o sinal.",
            fontSize = 11.sp, color = Neon.Dim
        )
    }
    CutPanel(
        "CORTE DE GRAVES (PASSA-ALTAS)", "Protege o alto-falante de porta de graves que ele não aguenta",
        AudioFx.hpfOn, { AudioFx.hpfOn = it; AudioFx.apply() },
        AudioFx.hpfFreq, 20f..250f, { AudioFx.hpfFreq = it; AudioFx.apply() },
        AudioFx.hpfSlope, { AudioFx.hpfSlope = it; AudioFx.apply() },
        AudioFx.hpfType, { AudioFx.hpfType = it; AudioFx.hpfSlope = Crossover.effectiveSlope(it, AudioFx.hpfSlope); AudioFx.apply() },
        "Porta comum: 38 a 50 Hz. Com subwoofer: 25 a 30 Hz."
    )
    Panel("GRAVE VIRTUAL", "Cria harmônicos dos graves fundos: o ouvido percebe mais grave sem o cone se mexer tanto", AudioFx.vbOn, { AudioFx.vbOn = it; AudioFx.apply() }) {
        SliderRow("Quantidade", AudioFx.vbAmount, 0f..1f, "", 2, hint = "0,4 a 0,7 é o ponto bom") { AudioFx.vbAmount = it; AudioFx.apply() }
        SliderRow("Pega graves abaixo de", AudioFx.vbFreq, 30f..120f, "Hz", 0, hint = "Quanto mais alto, mais grave virtual (e mais artificial)") { AudioFx.vbFreq = it; AudioFx.apply() }
    }
    Panel("LIMITADOR DE GRAVES", "Segura só os graves no teto; voz e instrumentos continuam inteiros", AudioFx.blOn, { AudioFx.blOn = it; AudioFx.apply() }) {
        SliderRow("Teto dos graves", AudioFx.blCeil, -24f..0f, "dB", 1, hint = "Mais baixo = protege mais e vibra menos. -3 a -5 dB é o ponto bom") { AudioFx.blCeil = it; AudioFx.apply() }
        SliderRow("Faixa limitada (até)", AudioFx.blFreq, 60f..250f, "Hz", 0) { AudioFx.blFreq = it; AudioFx.apply() }
    }
    Panel("VOZ, BRILHO E LOUDNESS", "Realces suaves por cima do EQ") {
        SliderRow("Foco na voz", AudioFx.vocalFocus, 0f..1f, "", 2, hint = "Realça a região da voz (2,8 kHz) só no centro, sem mexer nos lados") { AudioFx.vocalFocus = it; AudioFx.apply() }
        SliderRow("Ar (brilho nos agudos)", AudioFx.air, 0f..1f, "", 2, hint = "Gera harmônicos acima de 8 kHz; bom para alto-falante que perde agudo") { AudioFx.air = it; AudioFx.apply() }
        SliderRow("Contorno de loudness", AudioFx.contour, 0f..1f, "", 2, hint = "Mais grave e agudo quando você ouve baixinho") { AudioFx.contour = it; AudioFx.apply() }
    }
    Panel("GRAVES EM MONO", "Deixa os graves iguais nos dois lados (mais firmes e centrados)", AudioFx.monoBassOn, { AudioFx.monoBassOn = it; AudioFx.apply() }, startOpen = false) {
        SliderRow("Mono abaixo de", AudioFx.monoBassFreq, 40f..300f, "Hz", 0) { AudioFx.monoBassFreq = it; AudioFx.apply() }
    }
    Panel("ALINHAMENTO DE TEMPO E GANHO", "Acerta o som para quem está sentado no banco do motorista", startOpen = false) {
        Text("Cada 1 ms de atraso equivale a cerca de 34 cm de caminho do som. Atrase o lado MAIS PERTO de você.", fontSize = 11.sp, color = Neon.Dim)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Motorista (esquerda)") { AudioFx.delayL = 1.5f; AudioFx.delayR = 0f; AudioFx.apply() }
            Chip("Passageiro (direita)") { AudioFx.delayL = 0f; AudioFx.delayR = 1.5f; AudioFx.apply() }
            Chip("Centro") { AudioFx.delayL = 0f; AudioFx.delayR = 0f; AudioFx.apply() }
        }
        SliderRow("Atraso esquerdo", AudioFx.delayL, 0f..DspCore.MAX_DELAY_MS, "ms", 2) { AudioFx.delayL = it; AudioFx.apply() }
        SliderRow("Atraso direito", AudioFx.delayR, 0f..DspCore.MAX_DELAY_MS, "ms", 2) { AudioFx.delayR = it; AudioFx.apply() }
        SliderRow("Ganho esquerdo", AudioFx.trimL, -24f..6f, "dB", 1) { AudioFx.trimL = it; AudioFx.apply() }
        SliderRow("Ganho direito", AudioFx.trimR, -24f..6f, "dB", 1) { AudioFx.trimR = it; AudioFx.apply() }
        SwitchRow("Inverter polaridade do lado direito", "Só se um lado estiver ligado ao contrário no carro", AudioFx.invertR) { AudioFx.invertR = it; AudioFx.apply() }
    }
    Panel("CORTE DE AGUDOS (PASSA-BAIXAS)", "Para tirar chiado ou limitar a faixa", AudioFx.lpfOn, { AudioFx.lpfOn = it; AudioFx.apply() }, startOpen = false) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XoverType.values().forEach { t -> Chip(t.label, AudioFx.lpfType == t) { AudioFx.lpfType = t; AudioFx.lpfSlope = Crossover.effectiveSlope(t, AudioFx.lpfSlope); AudioFx.apply() } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Crossover.SLOPES.filter { AudioFx.lpfType == XoverType.BUTTERWORTH || it % 12 == 0 }.forEach { sl -> Chip("$sl dB", AudioFx.lpfSlope == sl) { AudioFx.lpfSlope = sl; AudioFx.apply() } }
        }
        SliderRow("Frequência de corte", AudioFx.lpfFreq, 1000f..20000f, "Hz", 0) { AudioFx.lpfFreq = it; AudioFx.apply() }
    }
    Panel("SUB-HARMÔNICO (SÓ COM SUBWOOFER)", "Gera uma oitava abaixo dos graves. Em alto-falante de porta, deixe desligado", AudioFx.subOn, { AudioFx.subOn = it; AudioFx.apply() }, startOpen = false) {
        SliderRow("Quantidade", AudioFx.subAmount, 0f..1f, "", 2) { AudioFx.subAmount = it; AudioFx.apply() }
        SliderRow("Gera a partir de graves até", AudioFx.subFreq, 50f..200f, "Hz", 0) { AudioFx.subFreq = it; AudioFx.apply() }
    }
    GhostButton("Zerar só os ajustes de carro (EQ fica como está)", Modifier.fillMaxWidth()) { AudioFx.resetCar() }
}

private val CAR_PRESET_INFO = mapOf(
    "Toxic Carro · Grave Forte + Voz" to "O seu: grave forte e vibrante (60 a 100 Hz) com voz e instrumentos bem à frente. Para alto-falante de porta, sem subwoofer.",
    "Toxic Carro · Vibração Máxima" to "Grave bem mais forte e com mais grave virtual, para sentir o carro vibrar. Use com volume moderado.",
    "Toxic Carro · Equilibrado" to "Som natural, com um pouco mais de grave e voz clara. Bom para o dia a dia.",
    "Toxic Carro · Kit 2 Vias" to "Para kit 2 vias (woofer + tweeter): grave firme sem deixar o agudo agressivo.",
    "Toxic Carro · Com Subwoofer" to "Para quando você instalar o subwoofer: corte baixo, sub-harmônico e grave em mono.",
    "Toxic Carro · Volume Baixo" to "Contorno de loudness: mantém grave e brilho quando você ouve baixinho."
)

/** Cartão de um preset pronto: mostra claramente se ele está valendo agora. */
@Composable
private fun PresetCard(name: String, info: String, active: Boolean, onApply: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) Neon.Green.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (active) Neon.Green else Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .clickable(onClick = onApply).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (active) "● ATIVO" else "Aplicar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Neon.Green else Neon.Dim)
        }
        if (info.isNotEmpty()) Text(info, fontSize = 11.sp, color = Neon.Dim)
    }
}

// ---- Presets ----
@Composable
private fun PresetSection() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            msg = "Exportação cancelada."
        } else {
            msg = try {
                val stream = ctx.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("O Android não abriu o arquivo para gravação.")
                stream.use { it.write(pending.toByteArray(Charsets.UTF_8)) }
                "Arquivo exportado com sucesso."
            } catch (e: Exception) {
                "Falha ao exportar: ${e.message ?: "erro de gravação"}"
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            msg = "Importação cancelada."
        } else {
            msg = try {
                val txt = com.meuapp.player.SafeRead.readText(ctx, uri)
                when {
                    txt == null -> "Não consegui ler o arquivo. Verifique a permissão e se ele tem até 1 MB."
                    txt.isBlank() -> "O arquivo está vazio."
                    txt.trimStart().startsWith("{") -> AudioFx.importJson(txt)
                    else -> {
                        val n = AudioFx.importApo(txt)
                        if (n > 0) "Importado como EQ paramétrico: $n filtros. ${AudioFx.importNote}"
                        else "Nenhum filtro AutoEQ/Equalizer APO reconhecido. Confira o formato do arquivo."
                    }
                }
            } catch (e: Exception) {
                "Falha ao importar: ${e.message ?: "erro inesperado"}"
            }
        }
    }
    Panel("PRESETS DE CARRO", "Prontos para alto-falante de porta; o que está valendo aparece como ATIVO") {
        CarPresets.ALL.keys.forEach { n ->
            PresetCard(n, CAR_PRESET_INFO[n].orEmpty(), AudioFx.isActive(n)) {
                msg = if (AudioFx.applyCarPreset(n)) "\"$n\" aplicado e valendo agora" else "Preset não encontrado"
            }
        }
        Text("Mexeu em qualquer controle depois? O selo ATIVO some e o painel passa a mostrar Personalizado.", fontSize = 11.sp, color = Neon.Dim, modifier = Modifier.padding(top = 8.dp))
    }
    Panel("PRESETS PRONTOS", "Aplicam-se ao EQ gráfico (zeram o resto)", startOpen = false) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioFx.PRESETS.keys.forEach { n -> Chip(n, AudioFx.isActive(n)) { AudioFx.applyPreset(n); msg = "\"$n\" aplicado e valendo agora" } }
        }
        Text("Não há curvas de fones embutidas: use a importação AutoEQ para o seu modelo.", fontSize = 11.sp, color = Neon.Dim, modifier = Modifier.padding(top = 6.dp))
    }
    Panel("MEUS PERFIS") {
        OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Nome do perfil") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        PrimaryButton("Salvar ajustes atuais") {
            if (newName.isNotBlank()) { AudioFx.saveCustom(newName); msg = "Perfil \"${newName.trim()}\" salvo"; newName = "" }
        }
        if (AudioFx.customNames.isEmpty()) Text("Nenhum perfil salvo ainda.", fontSize = 12.sp, color = Neon.Dim, modifier = Modifier.padding(top = 6.dp))
        AudioFx.customNames.toList().forEach { n ->
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (AudioFx.isActive(n)) "$n  ● ATIVO" else n, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (AudioFx.isActive(n)) Neon.Green else Color.Unspecified)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniAction("Aplicar", Neon.Green) { AudioFx.loadCustom(n); msg = "\"$n\" aplicado e valendo agora" }
                    MiniAction("Duplicar") { AudioFx.duplicateCustom(n) }
                    MiniAction("Renomear") {
                        if (newName.isNotBlank()) { AudioFx.renameCustom(n, newName); newName = "" } else msg = "Digite o novo nome no campo acima"
                    }
                    MiniAction("Apagar", Neon.Danger) { AudioFx.deleteCustom(n) }
                }
            }
        }
    }
    Panel("PERFIL AUTOMÁTICO POR SAÍDA", "Troca sozinho quando a saída de áudio muda", startOpen = false) {
        val routes = listOf("speaker" to "Alto-falante", "wired" to "Fones com fio", "bluetooth" to "Bluetooth")
        routes.forEach { (key, label) ->
            val cur = AudioFx.routeProfiles[key].orEmpty()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label + if (AudioFx.currentRoute == key) " (em uso)" else "", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Chip(if (cur.isBlank()) "Nenhum" else cur) {
                    val opts = listOf("") + AudioFx.customNames.toList()
                    AudioFx.setRouteProfile(key, opts[(opts.indexOf(cur).coerceAtLeast(0) + 1) % opts.size])
                }
            }
        }
        Text("Toque no nome para trocar o perfil. Ele é aplicado sozinho quando a saída muda.", fontSize = 11.sp, color = Neon.Dim)
    }
    Panel("COMPARAR A / B", "Guarde dois ajustes e alterne para ouvir a diferença", startOpen = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Guardar A", Modifier.weight(1f)) { AudioFx.storeAB(0); msg = "Ajustes atuais guardados em A" }
            GhostButton("Guardar B", Modifier.weight(1f)) { AudioFx.storeAB(1); msg = "Ajustes atuais guardados em B" }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Ouvir A", Modifier.weight(1f)) { if (!AudioFx.recallAB(0)) msg = "A está vazio" }
            GhostButton("Ouvir B", Modifier.weight(1f)) { if (!AudioFx.recallAB(1)) msg = "B está vazio" }
        }
    }
    Panel("IMPORTAR / EXPORTAR", startOpen = false) {
        GhostButton("Importar (AutoEQ, Equalizer APO ou JSON)", Modifier.fillMaxWidth()) {
            msg = "Abrindo seletor de arquivos…"
            try { picker.launch(arrayOf("*/*")) }
            catch (e: Exception) { msg = "Não consegui abrir o seletor: ${e.message ?: "erro do Android"}" }
        }
        Spacer(Modifier.height(4.dp))
        GhostButton("Exportar ajustes atuais (JSON)", Modifier.fillMaxWidth()) { pending = AudioFx.exportCurrent(); saver.launch("toxic-preset.json") }
        Spacer(Modifier.height(4.dp))
        GhostButton("Backup de todos os perfis (JSON)", Modifier.fillMaxWidth()) { pending = AudioFx.exportAll(); saver.launch("toxic-backup.json") }
    }
    if (msg.isNotEmpty()) {
        Text(msg, color = Neon.Green, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().glass(16.dp).padding(12.dp))
    }
}

// ───────────── Atualização ─────────────
@Composable
fun UpdateScreen(vm: PlayerViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        ToxicMark(Modifier.size(150.dp))
        Text("TOXIC PLAYER", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Text("Versão ${BuildConfig.VERSION_NAME}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Código instalado: ${BuildConfig.VERSION_CODE}", color = Neon.Dim, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().glass(24.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(vm.updateMsg.ifBlank { "Toque em \"Verificar agora\" para buscar atualizações." }, color = Neon.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            vm.update?.let {
                Text("Disponível: ${it.versionName} (código ${it.versionCode})", color = Neon.Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            vm.update?.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it.take(400), fontSize = 12.sp, color = Neon.Dim)
            }
        }
        if (vm.progress >= 0f) {
            LinearProgressIndicator(progress = { vm.progress }, color = Neon.Green, trackColor = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().clip(CircleShape))
        }
        if (vm.update != null) PrimaryButton(if (vm.busy) "Aguarde..." else "Baixar e instalar") { vm.installUpdate() }
        else PrimaryButton(if (vm.busy) "Verificando..." else "Verificar agora") { vm.checkUpdate(true) }
    }
}
