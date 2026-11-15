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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuapp.player.PlayerViewModel

/** Ajustes: conta e sincronização do Drive, áudio (equalizador) e atualização do app. */
@Composable
fun SettingsScreen(vm: PlayerViewModel) {
    var page by remember { mutableIntStateOf(0) }
    BackHandler(enabled = page != 0) { page = 0 }
    if (page == 0) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeader("TOXIC PLAYER", "Ajustes")
            SettingsRow(Icons.Rounded.Cloud, "Conta e sincronização", if (vm.cloud.connected) "Conectado: ${vm.cloud.accountEmail.ifBlank { "conta Google" }}" else "Conectar conta Google, sincronizar, cache e downloads", false) { page = 1 }
            SettingsRow(Icons.Rounded.Tune, "Áudio e equalizador", "Equalizador, dinâmica, efeitos e presets", false) { page = 2 }
            SettingsRow(Icons.Rounded.SystemUpdate, "Atualização do app", "Versão instalada e novas versões", vm.update != null) { page = 3 }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = 0 }) { Icon(Icons.Rounded.ArrowBack, "Voltar para Ajustes", tint = Neon.Green) }
                Text(when (page) { 1 -> "Conta e sincronização"; 2 -> "Áudio e equalizador"; else -> "Atualização do app" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    1 -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) { CloudSettings(vm) }
                    2 -> MesaScreen(vm)
                    else -> UpdateScreen(vm)
                }
            }
        }
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
