package com.meuapp.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.PlayerViewModel

/**
 * Ajustes: conta do Google, áudio (equalizador), atualização, privacidade e "Avançado" (bastidores).
 * As músicas salvas no aparelho ficam na aba Biblioteca. [startPage] abre direto numa página.
 */
@Composable
fun SettingsScreen(vm: PlayerViewModel, granted: Boolean, askPerm: () -> Unit, startPage: Int = 0) {
    var page by remember(startPage) { mutableIntStateOf(startPage) }
    var askClear by remember { mutableStateOf(false) }
    var clearDone by remember { mutableStateOf(false) }
    BackHandler(enabled = page != 0) { page = 0 }
    if (page == 0) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader("TOXIC PLAYER", "Ajustes")
            SettingsRow(Icons.Rounded.Cloud, "Conta do Google", if (vm.cloud.connected) "Conectado: ${vm.cloud.accountEmail.ifBlank { "conta Google" }}" else "Entrar com o Google", false) { page = 1 }
            SettingsRow(Icons.Rounded.Tune, "Áudio e equalizador", "Equalizador, dinâmica, efeitos e presets", false) { page = 2 }
            SettingsRow(Icons.Rounded.SystemUpdate, "Atualização do app", "Versão instalada e novas versões", vm.update != null) { page = 3 }
            SettingsRow(Icons.Rounded.Lock, "Privacidade", "Apagar o que o app aprendeu sobre o que você ouve", false) { askClear = true }
            Spacer(Modifier.height(6.dp))
            Title("AVANÇADO")
            SettingsRow(Icons.Rounded.Build, "Sincronização e armazenamento", "Sincronizar, cache e downloads", false) { page = 4 }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = 0 }) { Icon(Icons.Rounded.ArrowBack, "Voltar para Ajustes", tint = Neon.Green) }
                Text(when (page) { 1 -> "Conta do Google"; 2 -> "Áudio e equalizador"; 4 -> "Avançado"; else -> "Atualização do app" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    1 -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) { CloudSettings(vm) }
                    2 -> MesaScreen(vm)
                    4 -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) { CloudSettings(vm, advanced = true) }
                    else -> UpdateScreen(vm)
                }
            }
        }
    }
    if (askClear) {
        GlassDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Apagar o que o app aprendeu?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Isso apaga o histórico de escuta e o gosto aprendido, só neste aparelho. Suas playlists, suas favoritas e suas músicas não são apagadas. O app começa a aprender de novo do zero.", fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = { vm.clearListeningHistory(); askClear = false; clearDone = true }) {
                    Text("Apagar", color = Neon.Green, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Cancelar") } }
        )
    }
    if (clearDone) {
        GlassDialog(
            onDismissRequest = { clearDone = false },
            title = { Text("Pronto", fontWeight = FontWeight.Bold) },
            text = { Text("O histórico de escuta foi apagado.", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { clearDone = false }) { Text("Fechar", color = Neon.Green, fontWeight = FontWeight.Bold) } }
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, sub: String, badge: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(20.dp).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Neon.Green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Neon.Green)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 12.sp, color = Neon.Dim)
        }
        if (badge) Box(Modifier.padding(end = 8.dp).size(9.dp).clip(CircleShape).background(Neon.Lime))
        Icon(Icons.Rounded.ChevronRight, null, tint = Neon.Dim)
    }
}
