package com.meuapp.player.cloud

/** Formatos de áudio aceitos pelo Toxic Cloud. */
enum class CloudFormat(val label: String, val extensions: List<String>) {
    MP3("MP3", listOf("mp3")),
    FLAC("FLAC", listOf("flac")),
    WAV("WAV", listOf("wav", "wave")),
    M4A("M4A", listOf("m4a", "mp4a")),
    AAC("AAC", listOf("aac")),
    OGG("OGG", listOf("ogg", "oga")),
    OPUS("OPUS", listOf("opus"));

    companion object {
        /** Tipos MIME que o Drive costuma atribuir a cada formato. */
        private val mimeMap = mapOf(
            "audio/mpeg" to MP3, "audio/mp3" to MP3,
            "audio/flac" to FLAC, "audio/x-flac" to FLAC,
            "audio/wav" to WAV, "audio/x-wav" to WAV, "audio/wave" to WAV, "audio/vnd.wave" to WAV,
            "audio/mp4" to M4A, "audio/x-m4a" to M4A, "audio/m4a" to M4A,
            "audio/aac" to AAC, "audio/x-aac" to AAC, "audio/aacp" to AAC,
            "audio/ogg" to OGG, "application/ogg" to OGG, "audio/vorbis" to OGG,
            "audio/opus" to OPUS
        )

        /** Extensão do nome vence o MIME (o Drive às vezes marca FLAC/OPUS como octet-stream ou audio/ogg). */
        fun detect(name: String, mime: String?): CloudFormat? {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) values().firstOrNull { ext in it.extensions }?.let { return it }
            return mime?.lowercase()?.let { mimeMap[it] }
        }

        fun fromName(label: String): CloudFormat? = values().firstOrNull { it.name == label }
    }
}
