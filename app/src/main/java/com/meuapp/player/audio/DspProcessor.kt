package com.meuapp.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Liga o [DspCore] ao pipeline de áudio do ExoPlayer (PCM 16 bits, mono ou estéreo). */
@OptIn(UnstableApi::class)
class DspProcessor(private val core: DspCore) : BaseAudioProcessor() {
    private var tmp = FloatArray(0)
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Outros formatos passam sem processamento (processador inativo) em vez de falhar.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        core.configure(inputAudioFormat.sampleRate, channels)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val samples = size / 2
        if (tmp.size < samples) tmp = FloatArray(samples)
        for (i in 0 until samples) tmp[i] = inputBuffer.getShort() / 32768f
        core.process(tmp, samples / channels)
        for (i in 0 until samples) {
            out.putShort((tmp[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort())
        }
        out.flip()
    }
}

/** Instância única do motor, compartilhada entre o player e a interface. */
object DspEngine {
    val core = DspCore()
    val processor: DspProcessor by lazy { DspProcessor(core) }
}
