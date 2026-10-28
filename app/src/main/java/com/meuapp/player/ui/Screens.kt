package com.meuapp.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.BuildConfig
import com.meuapp.player.PlayerViewModel
import com.meuapp.player.audio.AudioFx

// ───────────── Estrutura principal + navegação ─────────────
@Composable
fun App(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit) {
    var tab by remember { mutableIntStateOf(1) }
    Scaffold(
        containerColor = Neon.Bg,
        bottomBar = { NavBar(tab, vm.update != null) { tab = it } }
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize().clipToBounds()
                .background(Brush.verticalGradient(listOf(Neon.Glow, Neon.Bg), endY = 1300f))
        ) {
            when (tab) {
                0 -> LibraryScreen(vm, granted, askPerm) { tab = 1 }
                1 -> PlayerScreen(vm)
                2 -> MesaScreen()
                else -> UpdateScreen(vm)
            }
        }
    }
}

@Composable
private fun NavBar(tab: Int, hasUpdate: Boolean, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple("Biblioteca", Icons.Rounded.LibraryMusic, 0),
        Triple("Player", Icons.Rounded.GraphicEq, 1),
        Triple("Mesa", Icons.Rounded.Tune, 2),
        Triple("Atualizar", Icons.Rounded.SystemUpdate, 3)
    )
    Row(Modifier.fillMaxWidth().padding(16.dp).glass(30.dp).padding(6.dp)) {
        items.forEach { (label, icon, i) ->
            val sel = tab == i
            Column(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (sel) Neon.Green.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onTab(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Icon(icon, null, tint = if (sel) Neon.Green else Neon.Dim, modifier = Modifier.size(24.dp))
                    if (i == 3 && hasUpdate) Box(Modifier.size(8.dp).clip(CircleShape).background(Neon.Green).align(Alignment.TopEnd))
                }
                Text(label, fontSize = 10.sp, color = if (sel) Neon.Green else Neon.Dim)
            }
        }
    }
}

@Composable
private fun Watermark(icon: ImageVector, modifier: Modifier) =
    Icon(icon, null, tint = Neon.Green.copy(alpha = 0.07f), modifier = modifier.requiredSize(420.dp))

@Composable
private fun neonSlider() = SliderDefaults.colors(
    thumbColor = Neon.Green, activeTrackColor = Neon.Green, inactiveTrackColor = Color.White.copy(alpha = 0.14f)
)

private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

// ───────────── Player ─────────────
@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.GraphicEq, Modifier.align(Alignment.TopEnd).offset(130.dp, (-40).dp))
        Watermark(Icons.Rounded.MusicNote, Modifier.align(Alignment.BottomStart).offset((-150).dp, 70.dp))
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("TOCANDO AGORA", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
            Vinyl(vm.isPlaying)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vm.artist, fontSize = 14.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spectrum(vm.isPlaying)
            Column {
                Slider(
                    value = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { vm.seek((it * vm.duration).toLong()) },
                    colors = neonSlider()
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(vm.position), fontSize = 12.sp, color = Neon.Dim)
                    Text(fmt(vm.duration), fontSize = 12.sp, color = Neon.Dim)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Aleatório", tint = if (vm.shuffle) Neon.Green else Neon.Dim) }
                IconButton(onClick = vm::prev) { Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(36.dp)) }
                Box(
                    Modifier.size(72.dp).drawBehind { drawCircle(Neon.Green.copy(alpha = 0.25f), size.minDimension * 0.75f) }
                        .clip(CircleShape).background(Neon.Green).clickable { vm.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Tocar/Pausar", tint = Neon.OnGreen, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Próxima", Modifier.size(36.dp)) }
                IconButton(onClick = vm::toggleRepeat) { Icon(Icons.Rounded.Repeat, "Repetir", tint = if (vm.repeat) Neon.Green else Neon.Dim) }
            }
        }
    }
}

@Composable
private fun Vinyl(playing: Boolean) {
    val rot = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) while (true) rot.animateTo(rot.value + 360f, tween(9000, easing = LinearEasing))
    }
    Box(Modifier.size(260.dp).drawBehind { drawCircle(Neon.Green.copy(alpha = 0.16f), size.minDimension * 0.62f) }) {
        Canvas(Modifier.fillMaxSize().rotate(rot.value % 360f)) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Color(0xFF101612), Color(0xFF070A08))), r)
            for (i in 1..14) drawCircle(Color.White.copy(alpha = 0.04f), r * (0.3f + i * 0.045f), style = Stroke(1f))
            drawCircle(Neon.Green, r * 0.28f)
            drawCircle(Neon.Bg, r * 0.05f)
            drawCircle(Neon.Green.copy(alpha = 0.5f), r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
    }
}

/** Barras decorativas animadas (não leem o áudio real). */
@Composable
private fun Spectrum(active: Boolean) {
    val t = rememberInfiniteTransition(label = "sp")
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(20) { i ->
            val h by t.animateFloat(
                initialValue = 0.15f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(400 + (i * 53) % 500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "b$i"
            )
            Box(
                Modifier.weight(1f).fillMaxHeight(if (active) h else 0.12f).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Neon.Green, Neon.Green.copy(alpha = 0.15f))))
            )
        }
    }
}

// ───────────── Biblioteca ─────────────
@Composable
fun LibraryScreen(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit, goPlayer: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.LibraryMusic, Modifier.align(Alignment.TopEnd).offset(150.dp, (-20).dp))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text("BIBLIOTECA", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
            Text("${vm.tracks.size} músicas", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
            if (!granted) {
                PermissionCard("Permita o acesso às músicas do celular.", "Permitir", askPerm)
            } else if (vm.tracks.isEmpty()) {
                Text("Nenhuma música encontrada no aparelho.", color = Neon.Dim)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.tracks.size) { i ->
                        val t = vm.tracks[i]
                        val now = t.title == vm.title
                        Row(
                            Modifier.fillMaxWidth().glass(18.dp).clickable { vm.play(i); goPlayer() }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = if (now) Neon.Green else Neon.Dim)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (now) Neon.Green else Color.White)
                                Text(t.artist, fontSize = 12.sp, color = Neon.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(fmt(t.durationMs), fontSize = 12.sp, color = Neon.Dim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text, color = Neon.Dim, textAlign = TextAlign.Center)
        PrimaryButton(button, onClick)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) = Button(
    onClick = onClick, shape = CircleShape,
    colors = ButtonDefaults.buttonColors(containerColor = Neon.Green, contentColor = Neon.OnGreen),
    modifier = Modifier.fillMaxWidth().height(52.dp)
) { Text(text, fontWeight = FontWeight.Bold) }

// ───────────── Mesa de áudio ─────────────
@Composable
fun MesaScreen() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val n = AudioFx.importApo(txt)
            msg = if (n > 0) "Preset importado: $n filtros" else "Nenhum filtro reconhecido no arquivo"
        }
    }
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.Tune, Modifier.align(Alignment.TopEnd).offset(150.dp, 200.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MESA DE ÁUDIO", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
                    Text("Som profissional", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Switch(checked = AudioFx.enabled, onCheckedChange = { AudioFx.enabled = it; AudioFx.apply() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Flat") { AudioFx.preset { 0f } }
                Chip("Bass") { AudioFx.preset { f -> if (f < 250) 6f else if (f < 500) 3f else 0f } }
                Chip("Vocal") { AudioFx.preset { f -> if (f in 1000..4500) 4f else if (f < 150) -2f else 0f } }
                Chip("Studio") { AudioFx.preset { f -> if (f < 120) 2f else if (f > 8000) 3f else if (f in 300..900) -1f else 0f } }
            }
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Text("EQUALIZADOR", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
                if (AudioFx.freqs.isEmpty()) {
                    Text("Toque uma música para ativar o equalizador.", color = Neon.Dim, modifier = Modifier.padding(top = 10.dp))
                }
                for (i in AudioFx.bands.indices) {
                    val f = AudioFx.freqs.getOrElse(i) { 0 }
                    val label = if (f >= 1000) "%.1f kHz".format(f / 1000f) else "$f Hz"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 12.sp, color = Neon.Dim, modifier = Modifier.width(64.dp))
                        Slider(
                            value = AudioFx.bands[i], onValueChange = { AudioFx.bands[i] = it; AudioFx.apply() },
                            valueRange = AudioFx.minDb..AudioFx.maxDb, colors = neonSlider(), modifier = Modifier.weight(1f)
                        )
                        Text("%+.1f".format(AudioFx.bands[i]), fontSize = 12.sp, color = Neon.Green, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Text("PRÉ-AMPLIFICADOR", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(AudioFx.preamp, { AudioFx.preamp = it; AudioFx.apply() }, valueRange = 0f..10f, colors = neonSlider(), modifier = Modifier.weight(1f))
                    Text("+%.1f dB".format(AudioFx.preamp), fontSize = 12.sp, color = Neon.Green)
                }
            }
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("COMPRESSOR / LIMITADOR", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
                    Switch(checked = AudioFx.compOn, onCheckedChange = { AudioFx.compOn = it; AudioFx.apply() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen))
                }
                Text("Threshold  %.0f dB".format(AudioFx.threshold), fontSize = 12.sp, color = Neon.Dim)
                Slider(AudioFx.threshold, { AudioFx.threshold = it; AudioFx.apply() }, valueRange = -40f..0f, colors = neonSlider())
                Text("Ratio  %.1f : 1".format(AudioFx.ratio), fontSize = 12.sp, color = Neon.Dim)
                Slider(AudioFx.ratio, { AudioFx.ratio = it; AudioFx.apply() }, valueRange = 1f..20f, colors = neonSlider())
            }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("text/plain", "*/*")) }, shape = CircleShape,
                border = BorderStroke(1.dp, Neon.Green), modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.FileOpen, null, tint = Neon.Green)
                Text("  Importar preset (AutoEQ / Equalizer APO)", color = Neon.Green, fontWeight = FontWeight.SemiBold)
            }
            if (msg.isNotEmpty()) Text(msg, color = Neon.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) =
    Text(text, Modifier.glass(20.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), fontSize = 13.sp)

// ───────────── Atualização ─────────────
@Composable
fun UpdateScreen(vm: PlayerViewModel) {
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.SystemUpdate, Modifier.align(Alignment.Center))
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("ATUALIZAÇÃO", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
            Text("Versão ${BuildConfig.VERSION_NAME}", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Text(vm.updateMsg, color = Neon.Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp))
            if (vm.progress >= 0f) {
                LinearProgressIndicator(progress = { vm.progress }, color = Neon.Green, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            if (vm.update != null) PrimaryButton("Baixar e instalar ${vm.update?.tag}") { vm.installUpdate() }
            else PrimaryButton("Verificar agora") { vm.checkUpdate() }
        }
    }
}
