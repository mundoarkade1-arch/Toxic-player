package com.meuapp.player.cloud.tags

import com.meuapp.player.cloud.CloudFormat
import java.io.IOException

/**
 * Extrai metadados lendo apenas trechos do arquivo (início e/ou fim) por HTTP Range. Nunca baixa o arquivo todo.
 * Erros de rede (IOException) sobem para quem chamou decidir repetir; arquivo malformado resulta em tags vazias.
 */
object TagReader {
    fun read(format: CloudFormat, src: RangeSource): TrackTags {
        val tags = TrackTags()
        try {
            when (format) {
                CloudFormat.MP3 -> {
                    val id3Len = Id3.readV2(src, tags)
                    Id3.readV1(src, tags)
                    tags.durationMs = Mp3Info.durationMs(src, id3Len.toLong())
                }
                CloudFormat.AAC -> Id3.readV2(src, tags)     // ADTS puro não tem tags nem duração confiável
                CloudFormat.FLAC -> Flac.read(src, tags)
                CloudFormat.OGG, CloudFormat.OPUS -> Ogg.read(src, tags)
                CloudFormat.M4A -> Mp4.read(src, tags)
                CloudFormat.WAV -> Wav.read(src, tags)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            // Dados inesperados no arquivo (IndexOutOfBounds, NumberFormat...): usa o que já foi extraído.
        }
        return tags
    }
}
