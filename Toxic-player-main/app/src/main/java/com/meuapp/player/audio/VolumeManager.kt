package com.meuapp.player.audio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * Único dono do volume do player: combina o fade entre músicas e o fade do timer para dormir.
 * O fade escurece o fim e abre o início de cada faixa (não há sobreposição entre duas músicas).
 */
object VolumeManager {
    @Volatile var sleepFactor = 1f
    private var player: Player? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p == null) { running = false; return }
            var f = sleepFactor
            val fade = AudioFx.fadeSec
            if (fade > 0f && p.isPlaying) {
                val dur = p.duration
                if (dur > 0L) {
                    val ms = fade * 1000f
                    val fin = (p.currentPosition / ms).coerceIn(0f, 1f)
                    val fout = ((dur - p.currentPosition) / ms).coerceIn(0f, 1f)
                    f *= minOf(fin, fout)
                }
            }
            val target = f.coerceIn(0f, 1f)
            if (kotlin.math.abs(p.volume - target) > 0.005f) p.volume = target
            handler.postDelayed(this, if (fade > 0f || sleepFactor < 1f) 50L else 400L)
        }
    }

    fun attach(p: Player) {
        player = p
        if (!running) { running = true; handler.post(tick) }
    }

    fun detach() {
        handler.removeCallbacks(tick)
        running = false
        player?.volume = 1f
        player = null
    }
}
