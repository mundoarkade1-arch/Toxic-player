package com.meuapp.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meuapp.player.cloud.CloudAuthNotifier
import com.meuapp.player.ui.App
import com.meuapp.player.ui.AppTheme
import com.meuapp.player.ui.GlassDialog
import com.meuapp.player.ui.Neon

class MainActivity : ComponentActivity() {
    private var vmRef: PlayerViewModel? = null

    /** Sobe a cada toque na notificação "Reconecte sua conta do Google". */
    private var reconnectRequest by mutableIntStateOf(0)

    override fun onStop() {
        vmRef?.savePos()   // guarda faixa e ponto atual para continuar depois
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(CloudAuthNotifier.EXTRA_RECONNECT, false)) reconnectRequest++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (savedInstanceState == null && intent?.getBooleanExtra(CloudAuthNotifier.EXTRA_RECONNECT, false) == true) reconnectRequest = 1

        setContent {
            AppTheme {
                val vm: PlayerViewModel = viewModel()
                vmRef = vm
                var granted by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED)
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
                    granted = allowed || granted
                }
                LaunchedEffect(granted) { if (granted) vm.loadTracks() else vm.skipLocal() }

                // Toque na notificação: abre a tela de reconexão do Google.
                LaunchedEffect(reconnectRequest) {
                    if (reconnectRequest > 0) vm.cloud.reconnectFromNotification()
                }

                // Permissão de notificação: só é pedida quando a conta cai, e só depois de uma explicação curta.
                val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
                    vm.cloud.notifPermissionHandled(allowed)
                }
                if (vm.cloud.askNotifPermission) {
                    GlassDialog(
                        onDismissRequest = { vm.cloud.notifPermissionHandled(false) },
                        title = { Text("Avisar se a conta cair?", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "Se sua conta do Google desconectar, mandamos um aviso. É só tocar nele para voltar a ouvir.",
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else vm.cloud.notifPermissionHandled(true)
                            }) { Text("Permitir", color = Neon.Green, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.cloud.notifPermissionHandled(false) }) { Text("Agora não") }
                        }
                    )
                }

                App(vm, granted) { if (!granted) launcher.launch(audioPerm) }
            }
        }
    }
}
