package com.meuapp.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.meuapp.player.ALL_FOLDERS
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
                2 -> MesaScreen(vm)
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

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) = Button(
    onClick = onClick, shape = CircleShape,
    colors = ButtonDefaults.buttonColors(containerColor = Neon.Green, contentColor = Neon.OnGreen),
    modifier = Modifier.fillMaxWidth().height(52.dp)
) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) =
    Text(
        text,
        Modifier.clip(CircleShape)
            .background(if (selected) Neon.Green.copy(alpha = 0.2f) else Neon.Glass)
            .border(1.dp, if (selected) Neon.Green else Neon.Border, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        fontSize = 13.sp, color = if (selected) Neon.Green else Color.White
    )

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
            Text("TOXIC PLAYER", color = Neon.Green, fontSize = 12.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
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
    Box(Modifier.size(250.dp).drawBehind { drawCircle(Neon.Green.copy(alpha = 0.16f), size.minDimension * 0.62f) }) {
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
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
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

// ───────────── Biblioteca (por pastas) ─────────────
@Composable
fun LibraryScreen(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit, goPlayer: () -> Unit) {
    val open = vm.openFolder
    BackHandler(enabled = open != null) { vm.enterFolder(null) }
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.LibraryMusic, Modifier.align(Alignment.TopEnd).offset(150.dp, (-20).dp))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            if (!granted) {
                Text("BIBLIOTECA", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(14.dp))
                PermissionCard("Permita o acesso às músicas do celular.", "Permitir", askPerm)
            } else if (open == null) {
                FolderList(vm)
            } else {
                TrackList(vm, open, goPlayer)
            }
        }
    }
}

@Composable
private fun FolderList(vm: PlayerViewModel) {
    Text("BIBLIOTECA", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
    Text("${vm.folders.size} pastas · ${vm.tracks.size} músicas", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
    if (vm.tracks.isEmpty()) {
        Text("Nenhuma música encontrada no aparelho.", color = Neon.Dim)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FolderRow("Todas as músicas", vm.tracks.size, Icons.Rounded.LibraryMusic) { vm.enterFolder(ALL_FOLDERS) } }
        items(vm.folders) { f -> FolderRow(f.name, f.count, Icons.Rounded.Folder) { vm.enterFolder(f.path) } }
    }
}

@Composable
private fun FolderRow(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(18.dp).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Neon.Green, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count músicas", fontSize = 12.sp, color = Neon.Dim)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Neon.Dim)
    }
}

@Composable
private fun TrackList(vm: PlayerViewModel, path: String, goPlayer: () -> Unit) {
    val list = vm.tracksIn(path)
    val name = if (path == ALL_FOLDERS) "Todas as músicas" else vm.folders.firstOrNull { it.path == path }?.name ?: "Pasta"
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.enterFolder(null) }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Neon.Green) }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${list.size} músicas", fontSize = 12.sp, color = Neon.Dim)
        }
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton("Tocar toda a pasta") { vm.playList(list, 0); goPlayer() }
    Spacer(Modifier.height(10.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.size) { i ->
            val t = list[i]
            val now = t.title == vm.title
            Row(
                Modifier.fillMaxWidth().glass(18.dp).clickable { vm.playList(list, i); goPlayer() }.padding(14.dp),
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

@Composable
private fun PermissionCard(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text, color = Neon.Dim, textAlign = TextAlign.Center)
        PrimaryButton(button, onClick)
    }
}

// ───────────── Mesa de áudio (avançada) ─────────────
@Composable
fun MesaScreen(vm: PlayerViewModel) {
    var sec by remember { mutableIntStateOf(0) }
    val names = listOf("EQ", "Dinâmica", "Efeitos", "Presets")
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.Tune, Modifier.align(Alignment.TopEnd).offset(150.dp, 200.dp))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MESA DE ÁUDIO", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
                    Text("Toxic Sound", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = AudioFx.enabled, onCheckedChange = { AudioFx.enabled = it; AudioFx.apply() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen)
                )
            }
            if (!AudioFx.ready) {
                Text("Toque uma música para ativar os efeitos.", color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().glass(24.dp).padding(4.dp)) {
                names.forEachIndexed { i, n ->
                    Text(
                        n,
                        Modifier.weight(1f).clip(CircleShape)
                            .background(if (sec == i) Neon.Green.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { sec = i }.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center, fontSize = 13.sp,
                        color = if (sec == i) Neon.Green else Neon.Dim,
                        fontWeight = if (sec == i) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (sec) {
                    0 -> EqSection()
                    1 -> DynSection()
                    2 -> FxSection(vm)
                    else -> PresetSection()
                }
            }
        }
    }
}

private fun freqLabel(f: Int) = if (f >= 1000) "${f / 1000}k" else "$f"

/** Slider vertical (um slider normal girado). */
@Composable
private fun VSlider(value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range, colors = neonSlider(),
        modifier = modifier
            .layout { measurable, c ->
                val p = measurable.measure(
                    androidx.compose.ui.unit.Constraints(
                        minWidth = c.minHeight, maxWidth = c.maxHeight, minHeight = c.minWidth, maxHeight = c.maxWidth
                    )
                )
                layout(p.height, p.width) { p.place(-p.width / 2 + p.height / 2, p.width / 2 - p.height / 2) }
            }
            .graphicsLayer(rotationZ = 270f)
    )
}

@Composable
private fun EqCurve() {
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val n = AudioFx.bands.size
        if (n < 2) return@Canvas
        fun y(v: Float) = size.height * (1f - (v + 12f) / 24f)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y(0f)), Offset(size.width, y(0f)), 1.dp.toPx())
        val path = Path()
        for (i in 0 until n) {
            val x = size.width * i / (n - 1)
            if (i == 0) path.moveTo(x, y(AudioFx.bands[i])) else path.lineTo(x, y(AudioFx.bands[i]))
        }
        drawPath(path, Neon.Green, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun EqSection() {
    Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("EQUALIZADOR · 10 BANDAS", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
            Switch(
                checked = AudioFx.eqOn, onCheckedChange = { AudioFx.eqOn = it; AudioFx.apply() },
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen)
            )
        }
        EqCurve()
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            for (i in 0 until AudioFx.bands.size) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%+.0f".format(AudioFx.bands[i]), fontSize = 9.sp, color = Neon.Green)
                    VSlider(AudioFx.bands[i], -12f..12f, Modifier.height(170.dp).fillMaxWidth()) {
                        AudioFx.bands[i] = it; AudioFx.apply()
                    }
                    Text(freqLabel(AudioFx.EQ_FREQS[i]), fontSize = 9.sp, color = Neon.Dim)
                }
            }
        }
    }
    OutlinedButton(
        onClick = { AudioFx.applyPreset("Flat") }, shape = CircleShape,
        border = BorderStroke(1.dp, Neon.Green), modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text("Zerar equalizador", color = Neon.Green) }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, decimals: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = Neon.Dim)
            Text(String.format("%.${decimals}f", value) + " " + unit, fontSize = 12.sp, color = Neon.Green)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = neonSlider())
    }
}

@Composable
private fun DynSection() {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("COMPRESSOR MULTIBANDA", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
                Text("3 bandas: graves, médios e agudos", fontSize = 11.sp, color = Neon.Dim)
            }
            Switch(
                checked = AudioFx.compOn, onCheckedChange = { AudioFx.compOn = it; AudioFx.apply() },
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen)
            )
        }
        SliderRow("Threshold", AudioFx.thr, -60f..0f, "dB") { AudioFx.thr = it; AudioFx.apply() }
        SliderRow("Ratio", AudioFx.ratio, 1f..20f, ": 1", 1) { AudioFx.ratio = it; AudioFx.apply() }
        SliderRow("Attack", AudioFx.attack, 1f..100f, "ms") { AudioFx.attack = it; AudioFx.apply() }
        SliderRow("Release", AudioFx.release, 10f..1000f, "ms") { AudioFx.release = it; AudioFx.apply() }
        SliderRow("Ganho de compensação", AudioFx.makeup, 0f..12f, "dB", 1) { AudioFx.makeup = it; AudioFx.apply() }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("LIMITADOR", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
                Text("Evita distorção quando o som está alto", fontSize = 11.sp, color = Neon.Dim)
            }
            Switch(
                checked = AudioFx.limOn, onCheckedChange = { AudioFx.limOn = it; AudioFx.apply() },
                colors = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen)
            )
        }
        SliderRow("Teto (threshold)", AudioFx.limThr, -12f..0f, "dB", 1) { AudioFx.limThr = it; AudioFx.apply() }
    }
}

@Composable
private fun FxSection(vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("GANHO E BALANÇO", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
        SliderRow("Pré-amplificador", AudioFx.preamp, -12f..12f, "dB", 1) { AudioFx.preamp = it; AudioFx.apply() }
        SliderRow("Loudness (volume extra)", AudioFx.loudness, 0f..10f, "dB", 1) { AudioFx.loudness = it; AudioFx.apply() }
        SliderRow("Balanço (esquerda ← → direita)", AudioFx.balance, -1f..1f, "", 2) { AudioFx.balance = it; AudioFx.apply() }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("EFEITOS", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
        SliderRow("Bass Boost (reforço de graves)", AudioFx.bassPct, 0f..100f, "%") { AudioFx.bassPct = it; AudioFx.apply() }
        SliderRow("Surround (virtualizador)", AudioFx.virtPct, 0f..100f, "%") { AudioFx.virtPct = it; AudioFx.apply() }
        Text("Bass Boost e Surround dependem do seu aparelho; em alguns não fazem efeito.", fontSize = 11.sp, color = Neon.Dim)
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("VELOCIDADE E TOM", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
        SliderRow("Velocidade", vm.speed, 0.5f..2f, "x", 2) { vm.changeSpeed(it) }
        SliderRow("Tom (pitch)", vm.pitch, 0.5f..2f, "x", 2) { vm.changePitch(it) }
        Text("Volte os dois para 1,00 para o som original.", fontSize = 11.sp, color = Neon.Dim)
    }
}

@Composable
private fun PresetSection() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val n = AudioFx.importApo(txt)
            msg = if (n > 0) "Preset importado: $n filtros" else "Nenhum filtro reconhecido no arquivo"
        }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PRESETS PRONTOS", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioFx.PRESETS.keys.forEach { n -> Chip(n) { AudioFx.applyPreset(n) } }
        }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MEUS PRESETS", fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)
        OutlinedTextField(
            value = newName, onValueChange = { newName = it }, singleLine = true,
            label = { Text("Nome do preset") }, modifier = Modifier.fillMaxWidth()
        )
        PrimaryButton("Salvar ajustes atuais") {
            if (newName.isNotBlank()) { AudioFx.saveCustom(newName); msg = "Preset \"${newName.trim()}\" salvo"; newName = "" }
        }
        if (AudioFx.customNames.isEmpty()) Text("Nenhum preset salvo ainda.", fontSize = 12.sp, color = Neon.Dim)
        AudioFx.customNames.toList().forEach { n ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(n, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Aplicar", color = Neon.Green, modifier = Modifier.clickable { AudioFx.loadCustom(n); msg = "Preset \"$n\" aplicado" }.padding(10.dp))
                Text("Apagar", color = Neon.Dim, modifier = Modifier.clickable { AudioFx.deleteCustom(n) }.padding(10.dp))
            }
        }
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

// ───────────── Atualização ─────────────
@Composable
fun UpdateScreen(vm: PlayerViewModel) {
    Box(Modifier.fillMaxSize()) {
        Watermark(Icons.Rounded.SystemUpdate, Modifier.align(Alignment.Center))
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("TOXIC PLAYER", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
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
