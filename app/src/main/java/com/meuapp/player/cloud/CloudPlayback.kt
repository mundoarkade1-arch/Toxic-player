package com.meuapp.player.cloud

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import java.io.IOException

/**
 * Streaming do Drive no ExoPlayer.
 *
 * A MediaItem carrega apenas uma URI interna (toxiccloud://drive/ID): nenhum link do Drive nem token fica exposto ou
 * guardado. A cada abertura (inclusive reabertura após seek/erro) o resolver troca a URI pelo endpoint oficial
 * files.get?alt=media e injeta o cabeçalho Authorization com um token válido. O ExoPlayer envia o cabeçalho Range
 * (leitura parcial/seek) e o cache em disco fica ACIMA do resolver, indexado pela URI interna.
 */
@OptIn(UnstableApi::class)
object CloudPlayback {

    private object Resolver : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (uri.scheme != CloudConfig.SCHEME) return dataSpec
            val id = uri.lastPathSegment?.takeIf { it.isNotBlank() && Regex("^[A-Za-z0-9_-]+$").matches(it) }
                ?: throw CloudException(CloudErrorKind.FILE_GONE)
            val token = CloudAuth.tokenBlocking()          // pode lançar CloudException (não conectado / expirado)
            val headers = HashMap(dataSpec.httpRequestHeaders)
            headers["Authorization"] = "Bearer $token"
            return dataSpec.buildUpon()
                .setUri(Uri.parse("${CloudConfig.API}/files/$id?alt=media&supportsAllDrives=true"))
                .setHttpRequestHeaders(headers)
                .build()
        }
    }

    /**
     * Fábrica de fontes de dados do player. Arquivos locais (content:// e file://) continuam pelo caminho padrão,
     * sem passar pelo cache; só o esquema toxiccloud:// usa resolver + cache.
     */
    fun dataSourceFactory(ctx: Context): DataSource.Factory {
        CloudAuth.init(ctx); CloudStorage.init(ctx)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("ToxicPlayer")
            .setConnectTimeoutMs(CloudConfig.CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(CloudConfig.READ_TIMEOUT_MS)
        val resolving = ResolvingDataSource.Factory(http, Resolver)
        val cached = CacheDataSource.Factory()
            .setCache(CloudStorage.audioCache())
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultDataSource.Factory(ctx, cached)
    }

    /** Traduz um erro do player em uma categoria compreensível, sem expor URL, token ou resposta do servidor. */
    fun classify(e: PlaybackException): CloudErrorKind {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 8) {
            if (t is HttpDataSource.InvalidResponseCodeException) return CloudErrors.fromHttp(t.responseCode, null).kind
            if (t is CloudException) return t.kind
            t = t.cause
        }
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> CloudErrorKind.NO_INTERNET
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> CloudErrorKind.FILE_GONE
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> CloudErrorKind.CORRUPTED
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> CloudErrorKind.UNSUPPORTED_FORMAT
            else -> {
                val k = CloudErrors.classify(e)
                if (k == CloudErrorKind.UNKNOWN) CloudErrorKind.STREAM_ERROR else k
            }
        }
    }

    /** Falhas de conectividade/sessão: pular para a próxima faixa não ajuda; o certo é pausar e permitir retomar. */
    fun isRecoverable(k: CloudErrorKind) = k == CloudErrorKind.NO_INTERNET || k == CloudErrorKind.TOKEN_EXPIRED ||
        k == CloudErrorKind.RATE_LIMITED || k == CloudErrorKind.NOT_CONNECTED || k == CloudErrorKind.STREAM_ERROR
}
