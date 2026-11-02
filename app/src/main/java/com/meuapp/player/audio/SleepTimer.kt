package com.meuapp.player.audio

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/** Timer para dormir. Roda dentro do serviço de áudio, então funciona mesmo com o app fechado. */
object SleepTimer {
    var leftSec by mutableIntStateOf(0); private set
    private var player: Player? = null
    private val handler = Handler(Looper.getMainLooper())
    private var end = 0L

    private val tick = object : Runnable {
        override fun run() {
            val rem = ((end - System.currentTimeMillis()) / 1000).toInt()
            leftSec = rem.coerceAtLeast(0)
            val p = player
            if (rem <= 8 && p != null) p.volume = (rem / 8f).coerceIn(0f, 1f)   // últimos 8 s: fade-out
            if (rem <= 0) {
                p?.pause()
                p?.volume = 1f
                leftSec = 0
                return
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun attach(p: Player) { player = p }
    fun detach() { cancel(); player = null }

    fun start(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        end = System.currentTimeMillis() + minutes * 60_000L
        leftSec = minutes * 60
        handler.post(tick)
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        leftSec = 0
        player?.volume = 1f
    }
}
