package com.meuapp.player.cloud

/** Constantes do Toxic Cloud. Nenhum segredo aqui: o OAuth Android é identificado por pacote + SHA-1 no Google Cloud. */
object CloudConfig {
    /**
     * Escopo do Drive. Escolha deliberada: "drive.file" só enxerga arquivos criados pelo próprio app ou abertos
     * por um seletor do Google, então NÃO lista uma biblioteca que já existe no Drive. Para ler músicas já
     * enviadas pelo usuário o menor escopo possível é somente-leitura. Veja docs/TOXIC_CLOUD.md.
     */
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

    const val SCHEME = "toxiccloud"
    const val HOST = "drive"
    const val API = "https://www.googleapis.com/drive/v3"
    const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"

    /** Margem de segurança: o token em memória é descartado bem antes de expirar (tokens duram ~1 h). */
    const val TOKEN_TTL_MS = 30 * 60 * 1000L

    const val PAGE_SIZE = 1000
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 30_000
    const val MAX_RETRIES = 4

    /** Resincroniza ao abrir o app apenas se a última sincronização for mais antiga que isto. */
    const val AUTO_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** Quantas leituras parciais (metadados) rodam ao mesmo tempo. Baixo de propósito: respeita a cota da API. */
    const val ENRICH_PARALLELISM = 3

    /** Limites de leitura parcial para metadados/capa. */
    const val MAX_TAG_BYTES = 2 * 1024 * 1024
    const val HEAD_CHUNK = 64 * 1024
    const val MAX_COVER_BYTES = 3 * 1024 * 1024

    val CACHE_LIMIT_OPTIONS_MB = listOf(128, 512, 1024, 2048)
    const val DEFAULT_CACHE_MB = 512

    /** Folga de espaço livre exigida antes de baixar uma música para o modo offline. */
    const val FREE_SPACE_RESERVE = 100L * 1024 * 1024

    fun playbackUri(fileId: String): String = "$SCHEME://$HOST/$fileId"
}
