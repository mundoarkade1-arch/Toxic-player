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
import com.meuapp.player.audio.DspCore
import com.meuapp.player.audio.DspEngine
import com.meuapp.player.audio.EqFilter
import com.meuapp.player.audio.FilterType
import com.meuapp.player.audio.Meters
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

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

@Composable
private fun neonSwitch() = SwitchDefaults.colors(checkedTrackColor = Neon.Green, checkedThumbColor = Neon.OnGreen)

private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
private fun toDb(v: Float) = 20f * log10(max(v, 1e-6f))

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) = Button(
    onClick = onClick, shape = CircleShape,
    colors = ButtonDefaults.buttonColors(containerColor = Neon.Green, contentColor = Neon.OnGreen),
    modifier = Modifier.fillMaxWidth().height(52.dp)
) { Text(text, fontWeight = FontWeight.Bold) }

@Composable
private fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick, shape = CircleShape, border = BorderStroke(1.dp, Neon.Green), modifier = modifier.height(46.dp)
) { Text(text, color = Neon.Green, fontSize = 13.sp, maxLines = 1) }

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

@Composable
private fun Title(text: String) = Text(text, fontSize = 12.sp, letterSpacing = 2.sp, color = Neon.Dim)

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
                vm.playError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF6B6B)) }
            }
            LiveSpectrum(vm.isPlaying)
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
    Box(Modifier.size(230.dp).drawBehind { drawCircle(Neon.Green.copy(alpha = 0.16f), size.minDimension * 0.62f) }) {
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

/** Espectro REAL (FFT do áudio que sai do motor de DSP). */
@Composable
private fun LiveSpectrum(active: Boolean) {
    var bars by remember { mutableStateOf(FloatArray(24)) }
    LaunchedEffect(active) {
        while (true) {
            val raw = if (active) DspEngine.core.spectrum(24) else FloatArray(24)
            val old = bars
            bars = FloatArray(24) { max(raw[it], old[it] * 0.8f) }
            delay(50)
        }
    }
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 24) {
            Box(
                Modifier.weight(1f).fillMaxHeight(0.04f + bars[i] * 0.96f).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Neon.Green, Neon.Green.copy(alpha = 0.15f))))
            )
        }
    }
}

// ───────────── Biblioteca ─────────────
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
            } else if (open != null) {
                TrackList(vm, vm.tracksIn(open), groupTitle(vm, open), true, goPlayer)
            } else {
                LibraryRoot(vm, goPlayer)
            }
        }
    }
}

private fun groupTitle(vm: PlayerViewModel, key: String): String =
    if (key == ALL_FOLDERS) "Todas as músicas" else vm.groups(0).plus(vm.groups(1)).plus(vm.groups(2)).firstOrNull { it.path == key }?.name ?: "Grupo"

@Composable
private fun LibraryRoot(vm: PlayerViewModel, goPlayer: () -> Unit) {
    Text("BIBLIOTECA", color = Neon.Green, fontSize = 11.sp, letterSpacing = 3.sp)
    Text("${vm.tracks.size} músicas", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    OutlinedTextField(
        value = vm.query, onValueChange = vm::changeQuery, singleLine = true,
        label = { Text("Buscar música, artista, álbum ou pasta") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    if (vm.query.isNotBlank()) {
        TrackList(vm, vm.searchResults(), "Resultados", false, goPlayer)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Pastas", vm.libMode == 0) { vm.changeLibMode(0) }
        Chip("Artistas", vm.libMode == 1) { vm.changeLibMode(1) }
        Chip("Álbuns", vm.libMode == 2) { vm.changeLibMode(2) }
    }
    Spacer(Modifier.height(10.dp))
    if (vm.tracks.isEmpty()) {
        Text("Nenhuma música encontrada no aparelho.", color = Neon.Dim)
        return
    }
    val groups = remember(vm.tracks, vm.libMode) { vm.groups(vm.libMode) }
    val icon = when (vm.libMode) { 1 -> Icons.Rounded.Person; 2 -> Icons.Rounded.Album; else -> Icons.Rounded.Folder }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { GroupRow("Todas as músicas", vm.tracks.size, Icons.Rounded.LibraryMusic) { vm.enterFolder(ALL_FOLDERS) } }
        items(groups) { g -> GroupRow(g.name, g.count, icon) { vm.enterFolder(g.path) } }
    }
}

@Composable
private fun GroupRow(name: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
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
private fun TrackList(vm: PlayerViewModel, list: List<com.meuapp.player.Track>, name: String, showBack: Boolean, goPlayer: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) IconButton(onClick = { vm.enterFolder(null) }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Neon.Green) }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${list.size} músicas", fontSize = 12.sp, color = Neon.Dim)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Título", vm.sortMode == 0) { vm.changeSort(0) }
        Chip("Artista", vm.sortMode == 1) { vm.changeSort(1) }
        Chip("Duração", vm.sortMode == 2) { vm.changeSort(2) }
    }
    Spacer(Modifier.height(8.dp))
    if (list.isNotEmpty()) PrimaryButton("Tocar tudo") { vm.playList(list, 0); goPlayer() }
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

// ───────────── Mesa de áudio ─────────────
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
                Switch(checked = AudioFx.enabled, onCheckedChange = { AudioFx.enabled = it; AudioFx.apply() }, colors = neonSwitch())
            }
            if (!AudioFx.ready) Text("Toque uma música para ativar os efeitos do sistema.", color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
                MetersCard()
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

@Composable
private fun LevelBar(frac: Float, hot: Boolean) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(if (hot) Color(0xFFFF4D4D) else Neon.Green))
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
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Title("MEDIDORES")
            Text(if (clipping) "CLIPPING!" else "Headroom ${"%.1f".format(headroom.coerceAtMost(60f))} dB", fontSize = 12.sp, color = if (clipping) Color(0xFFFF4D4D) else Neon.Green)
        }
        LevelBar((pL + 60f) / 60f, pL > -1f)
        LevelBar((pR + 60f) / 60f, pR > -1f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RMS L ${"%.0f".format(toDb(m.rmsL))} · R ${"%.0f".format(toDb(m.rmsR))} dB", fontSize = 11.sp, color = Neon.Dim)
            Text("Comp -${"%.1f".format(m.compGrDb)} · Lim -${"%.1f".format(m.limGrDb)} dB", fontSize = 11.sp, color = Neon.Dim)
        }
    }
}

// ---- EQ ----
@Composable
private fun EqCurve() {
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val pts = 100
        val curve = AudioFx.responseCurve(pts)
        fun y(db: Float) = size.height * (1f - ((db.coerceIn(-18f, 18f)) + 18f) / 36f)
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y(0f)), Offset(size.width, y(0f)), 1.dp.toPx())
        val path = Path()
        for (i in 0 until pts) {
            val x = size.width * i / (pts - 1)
            if (i == 0) path.moveTo(x, y(curve[i])) else path.lineTo(x, y(curve[i]))
        }
        drawPath(path, Neon.Green, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Gráfico · 10 bandas", !param) { param = false }
        Chip("Paramétrico", param) { param = true }
    }
    Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Title("RESPOSTA COMBINADA")
            Text("±18 dB", fontSize = 11.sp, color = Neon.Dim)
        }
        EqCurve()
    }
    if (!param) {
        Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
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
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filtro ${i + 1}", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = f.enabled, onCheckedChange = { AudioFx.updateFilter(i, f.copy(enabled = it)) }, colors = neonSwitch())
                Text("Remover", color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.clickable { AudioFx.removeFilter(i) }.padding(10.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterType.values().forEach { t -> Chip(t.label, f.type == t) { AudioFx.updateFilter(i, f.copy(type = t)) } }
        }
        val lo = log10(20f); val hi = log10(20000f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Frequência", fontSize = 12.sp, color = Neon.Dim)
            Text(if (f.freq >= 1000) "%.2f kHz".format(f.freq / 1000f) else "%.0f Hz".format(f.freq), fontSize = 12.sp, color = Neon.Green)
        }
        Slider(
            value = log10(f.freq).coerceIn(lo, hi), onValueChange = { AudioFx.updateFilter(i, f.copy(freq = 10f.pow(it))) },
            valueRange = lo..hi, colors = neonSlider()
        )
        if (f.type.usesGain) {
            SliderRow("Ganho", f.gainDb, -18f..18f, "dB", 1) { AudioFx.updateFilter(i, f.copy(gainDb = it)) }
        }
        SliderRow("Q (largura)", f.q, 0.2f..10f, "", 2) { AudioFx.updateFilter(i, f.copy(q = it)) }
    }
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
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Title(title)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = Neon.Dim)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = neonSwitch())
    }
}

// ---- Dinâmica ----
@Composable
private fun DynSection() {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        SwitchRow("COMPRESSOR", "Processamento próprio, joelho suave", AudioFx.compOn) { AudioFx.compOn = it; AudioFx.apply() }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Detector RMS", AudioFx.compRms) { AudioFx.compRms = true; AudioFx.apply() }
            Chip("Detector Pico", !AudioFx.compRms) { AudioFx.compRms = false; AudioFx.apply() }
        }
        SliderRow("Threshold", AudioFx.thr, -60f..0f, "dB", 1) { AudioFx.thr = it; AudioFx.apply() }
        SliderRow("Ratio", AudioFx.ratio, 1f..20f, ": 1", 1) { AudioFx.ratio = it; AudioFx.apply() }
        SliderRow("Knee (joelho)", AudioFx.knee, 0f..24f, "dB", 1) { AudioFx.knee = it; AudioFx.apply() }
        SliderRow("Attack", AudioFx.attack, 0.1f..100f, "ms", 1) { AudioFx.attack = it; AudioFx.apply() }
        SliderRow("Release", AudioFx.release, 10f..1000f, "ms") { AudioFx.release = it; AudioFx.apply() }
        SliderRow("Ganho de compensação", AudioFx.makeup, 0f..18f, "dB", 1) { AudioFx.makeup = it; AudioFx.apply() }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        SwitchRow("LIMITADOR", "Look-ahead de 5 ms + proteção no teto (pico de amostra, não true-peak)", AudioFx.limOn) { AudioFx.limOn = it; AudioFx.apply() }
        SliderRow("Teto (ceiling)", AudioFx.limThr, -12f..0f, "dB", 1) { AudioFx.limThr = it; AudioFx.apply() }
        SliderRow("Release", AudioFx.limRelease, 10f..500f, "ms") { AudioFx.limRelease = it; AudioFx.apply() }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        SwitchRow("COMPRESSOR MULTIBANDA (SISTEMA)", "3 bandas; depende do aparelho", AudioFx.mbcOn) { AudioFx.mbcOn = it; AudioFx.apply() }
        SliderRow("Threshold", AudioFx.mbcThr, -60f..0f, "dB", 1) { AudioFx.mbcThr = it; AudioFx.apply() }
        SliderRow("Ratio", AudioFx.mbcRatio, 1f..20f, ": 1", 1) { AudioFx.mbcRatio = it; AudioFx.apply() }
    }
}

// ---- Efeitos / estéreo ----
@Composable
private fun FxSection(vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Title("GANHO E SEGURANÇA")
        SliderRow("Pré-amplificador", AudioFx.preamp, -12f..12f, "dB", 1) { AudioFx.preamp = it; AudioFx.apply() }
        SwitchRow("ATENUAÇÃO AUTOMÁTICA", "Reduz o ganho pelo maior reforço do EQ para evitar clipping", AudioFx.autoHeadroom) { AudioFx.autoHeadroom = it; AudioFx.apply() }
        SliderRow("Balanço (esquerda ← → direita)", AudioFx.balance, -1f..1f, "", 2) { AudioFx.balance = it; AudioFx.apply() }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Title("ESTÉREO")
        SliderRow("Largura (0 = mono, 1 = original)", AudioFx.width, 0f..2f, "x", 2) { AudioFx.width = it; AudioFx.apply() }
        SliderRow("Crossfeed (para fones)", AudioFx.crossfeed, 0f..1f, "", 2) { AudioFx.crossfeed = it; AudioFx.apply() }
        Text("A largura usa Mid/Side: o centro (mono) nunca é alterado.", fontSize = 11.sp, color = Neon.Dim)
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Title("EFEITOS DO SISTEMA (DEPENDEM DO APARELHO)")
        SliderRow("Loudness (volume extra)", AudioFx.loudness, 0f..10f, "dB", 1) { AudioFx.loudness = it; AudioFx.apply() }
        SliderRow("Bass Boost", AudioFx.bassPct, 0f..100f, "%") { AudioFx.bassPct = it; AudioFx.apply() }
        SliderRow("Surround (virtualizador)", AudioFx.virtPct, 0f..100f, "%") { AudioFx.virtPct = it; AudioFx.apply() }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Title("VELOCIDADE E TOM")
        SliderRow("Velocidade", vm.speed, 0.5f..2f, "x", 2) { vm.changeSpeed(it) }
        SliderRow("Tom (pitch)", vm.pitch, 0.5f..2f, "x", 2) { vm.changePitch(it) }
    }
    GhostButton("Resetar todos os ajustes de áudio", Modifier.fillMaxWidth()) { AudioFx.resetAll() }
}

// ---- Presets ----
@Composable
private fun PresetSection() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(pending.toByteArray()) }; msg = "Arquivo salvo" }
            catch (e: Exception) { msg = "Não foi possível salvar o arquivo" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = try { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }
            msg = if (txt.trimStart().startsWith("{")) AudioFx.importJson(txt) else {
                val n = AudioFx.importApo(txt)
                if (n > 0) "Importado como EQ paramétrico: $n filtros. ${AudioFx.importNote}" else "Nenhum filtro reconhecido no arquivo"
            }
        }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("PRESETS PRONTOS (EQ GRÁFICO)")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioFx.PRESETS.keys.forEach { n -> Chip(n) { AudioFx.applyPreset(n) } }
        }
        Text("Não há curvas de fones embutidas: use a importação AutoEQ para o seu modelo.", fontSize = 11.sp, color = Neon.Dim)
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("MEUS PERFIS")
        OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Nome do perfil") }, modifier = Modifier.fillMaxWidth())
        PrimaryButton("Salvar ajustes atuais") {
            if (newName.isNotBlank()) { AudioFx.saveCustom(newName); msg = "Perfil \"${newName.trim()}\" salvo"; newName = "" }
        }
        if (AudioFx.customNames.isEmpty()) Text("Nenhum perfil salvo ainda.", fontSize = 12.sp, color = Neon.Dim)
        AudioFx.customNames.toList().forEach { n ->
            Column {
                Text(n, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Text("Aplicar", color = Neon.Green, modifier = Modifier.clickable { AudioFx.loadCustom(n); msg = "\"$n\" aplicado" }.padding(10.dp))
                    Text("Duplicar", color = Neon.Dim, modifier = Modifier.clickable { AudioFx.duplicateCustom(n) }.padding(10.dp))
                    Text("Renomear", color = Neon.Dim, modifier = Modifier.clickable {
                        if (newName.isNotBlank()) { AudioFx.renameCustom(n, newName); newName = "" } else msg = "Digite o novo nome no campo acima"
                    }.padding(10.dp))
                    Text("Apagar", color = Neon.Dim, modifier = Modifier.clickable { AudioFx.deleteCustom(n) }.padding(10.dp))
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("COMPARAR A / B")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Guardar A", Modifier.weight(1f)) { AudioFx.storeAB(0); msg = "Ajustes atuais guardados em A" }
            GhostButton("Guardar B", Modifier.weight(1f)) { AudioFx.storeAB(1); msg = "Ajustes atuais guardados em B" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Ouvir A", Modifier.weight(1f)) { if (!AudioFx.recallAB(0)) msg = "A está vazio" }
            GhostButton("Ouvir B", Modifier.weight(1f)) { if (!AudioFx.recallAB(1)) msg = "B está vazio" }
        }
    }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("IMPORTAR / EXPORTAR")
        GhostButton("Importar (AutoEQ, Equalizer APO ou JSON)", Modifier.fillMaxWidth()) { picker.launch(arrayOf("text/plain", "application/json", "*/*")) }
        GhostButton("Exportar ajustes atuais (JSON)", Modifier.fillMaxWidth()) { pending = AudioFx.exportCurrent(); saver.launch("toxic-preset.json") }
        GhostButton("Backup de todos os perfis (JSON)", Modifier.fillMaxWidth()) { pending = AudioFx.exportAll(); saver.launch("toxic-backup.json") }
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
            Text("Versão ${BuildConfig.VERSION_NAME}", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("Código instalado: ${BuildConfig.VERSION_CODE}", color = Neon.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            Text(vm.updateMsg, color = Neon.Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
            vm.update?.let {
                Text("Disponível: ${it.versionName} (código ${it.versionCode})", color = Neon.Green, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (vm.progress >= 0f) {
                LinearProgressIndicator(progress = { vm.progress }, color = Neon.Green, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            if (vm.update != null) PrimaryButton(if (vm.busy) "Aguarde..." else "Baixar e instalar") { vm.installUpdate() }
            else PrimaryButton(if (vm.busy) "Verificando..." else "Verificar agora") { vm.checkUpdate(true) }
        }
    }
}
