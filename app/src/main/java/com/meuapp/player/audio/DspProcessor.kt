package com.meuapp.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Liga o [DspCore] ao pipeline de áudio do ExoPlayer: PCM 16, 24, 32 bits ou float, mono ou estéreo.
 * Formatos que o motor não entende (mais de 2 canais, por exemplo) passam sem processamento, e a tela avisa
 * ([DspEngine.inChain] e [DspEngine.formatInfo]) em vez de deixar o usuário mexendo em controles que não fazem nada.
 */
@OptIn(UnstableApi::class)
class DspProcessor(private val core: DspCore) : BaseAudioProcessor() {
    private var tmp = FloatArray(0)
    private var channels = 2
    private var fmt = PcmFormat.INT16
    private val quantizer = Quantizer()

    private fun formatOf(encoding: Int): PcmFormat? = when (encoding) {
        C.ENCODING_PCM_16BIT -> PcmFormat.INT16
        C.ENCODING_PCM_24BIT -> PcmFormat.INT24
        C.ENCODING_PCM_32BIT -> PcmFormat.INT32
        C.ENCODING_PCM_FLOAT -> PcmFormat.FLOAT
        else -> null
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val f = formatOf(inputAudioFormat.encoding)
        val ch = inputAudioFormat.channelCount
        val where = "${inputAudioFormat.sampleRate} Hz · ${if (ch == 1) "mono" else if (ch == 2) "estéreo" else "$ch canais"}"
        if (f == null || ch !in 1..2) {
            DspEngine.inChain = false
            DspEngine.formatInfo = "$where · formato sem suporte (${if (f == null) "codificação ${inputAudioFormat.encoding}" else "só mono e estéreo"}): SEM efeitos"
            return AudioProcessor.AudioFormat.NOT_SET
        }
        fmt = f
        channels = ch
        core.configure(inputAudioFormat.sampleRate, channels)
        DspEngine.inChain = true
        DspEngine.formatInfo = "$where · PCM ${f.label}"
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val samples = size / fmt.bytes
        if (tmp.size < samples) tmp = FloatArray(samples)
        PcmCodec.read(inputBuffer, fmt, tmp, samples)
        core.process(tmp, samples / channels)
        val p = core.params
        PcmCodec.write(out, fmt, tmp, samples, quantizer, p.enabled && p.ditherOn)
        out.flip()
    }
}

/** Instância única do motor, compartilhada entre o player e a interface. */
object DspEngine {
    val core = DspCore()
    val processor: DspProcessor by lazy { DspProcessor(core) }
    /** O motor está recebendo o áudio que toca agora? (falso = formato sem suporte, passa direto) */
    @Volatile var inChain = false
    /** Descrição do áudio que está passando: taxa, canais e formato. */
    @Volatile var formatInfo = ""
}
