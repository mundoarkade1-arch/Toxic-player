package com.meuapp.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meuapp.player.ui.App
import com.meuapp.player.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        setContent {
            AppTheme {
                val vm: PlayerViewModel = viewModel()
                var granted by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED)
                }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                    granted = r[audioPerm] == true || granted
                    if (granted) vm.loadTracks()
                }
                LaunchedEffect(Unit) {
                    val perms = mutableListOf(audioPerm)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    if (granted) {
                        vm.loadTracks()
                        if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    } else launcher.launch(perms.toTypedArray())
                }
                App(vm, granted) { launcher.launch(arrayOf(audioPerm)) }
            }
        }
    }
}
