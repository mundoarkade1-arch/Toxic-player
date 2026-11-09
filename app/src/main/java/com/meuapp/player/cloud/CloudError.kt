package com.meuapp.player.cloud

import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Tipos de falha do Toxic Cloud, cada um com uma mensagem compreensível e sem dados sensíveis. */
enum class CloudErrorKind(val message: String) {
    NOT_CONNECTED("Nenhuma conta Google conectada. Conecte sua conta na aba Cloud."),
    NO_INTERNET("Sem conexão com a internet. Verifique a rede e tente de novo."),
    TOKEN_EXPIRED("A sessão com o Google expirou. Toque em Conectar para renovar o acesso."),
    ACCESS_REVOKED("O acesso ao Google Drive foi revogado ou negado. Conecte a conta novamente."),
    ACCESS_DENIED("O Google Drive negou acesso a este arquivo."),
    FILE_GONE("Este arquivo foi removido ou não está mais disponível no Drive."),
    UNSUPPORTED_FORMAT("Formato de áudio não compatível com este aparelho."),
    CORRUPTED("O arquivo parece corrompido e não pôde ser lido."),
    STREAM_ERROR("Falha ao transmitir a música. Tente novamente."),
    SYNC_ERROR("Não foi possível sincronizar a biblioteca. Tente novamente mais tarde."),
    RATE_LIMITED("O Google limitou as requisições por um momento. Aguarde alguns minutos e tente de novo."),
    NO_SPACE("Falta de espaço no aparelho. Libere armazenamento ou reduza o limite do cache."),
    NOT_CONFIGURED("O login Google não está configurado para este app. Veja docs/GOOGLE_DRIVE_SETUP.md."),
    CANCELLED("Autorização cancelada."),
    UNKNOWN("Algo deu errado. Tente novamente.")
}

class CloudException(val kind: CloudErrorKind, val httpCode: Int = 0, val retryAfterSec: Long = 0L, cause: Throwable? = null) :
    IOException(kind.message, cause)

object CloudErrors {
    /** Converte qualquer exceção em um tipo de erro. Nunca inclui URL, token ou corpo da resposta. */
    fun classify(t: Throwable): CloudErrorKind = when {
        t is CloudException -> t.kind
        t is UnknownHostException || t is ConnectException || t is SocketTimeoutException -> CloudErrorKind.NO_INTERNET
        t is FileNotFoundException -> CloudErrorKind.FILE_GONE
        isNoSpace(t) -> CloudErrorKind.NO_SPACE
        t.cause != null && t.cause !== t -> classify(t.cause!!)
        else -> CloudErrorKind.UNKNOWN
    }

    fun message(t: Throwable): String = classify(t).message

    fun isNoSpace(t: Throwable): Boolean {
        val m = t.message ?: return false
        return m.contains("ENOSPC", true) || m.contains("No space left", true)
    }

    /** Traduz o código HTTP do Drive. [reason] é o campo "reason" do erro JSON (nunca o corpo inteiro). */
    fun fromHttp(code: Int, reason: String?, retryAfterSec: Long = 0L): CloudException {
        val r = reason.orEmpty()
        val kind = when {
            code == 401 -> CloudErrorKind.TOKEN_EXPIRED
            code == 429 -> CloudErrorKind.RATE_LIMITED
            code == 403 && (r.contains("rateLimit", true) || r.contains("userRateLimit", true) || r.contains("quotaExceeded", true)) ->
                CloudErrorKind.RATE_LIMITED
            code == 403 && r.contains("downloadQuotaExceeded", true) -> CloudErrorKind.RATE_LIMITED
            code == 403 || code == 400 && r == "invalid_grant" -> CloudErrorKind.ACCESS_DENIED
            code == 404 || code == 410 -> CloudErrorKind.FILE_GONE
            code == 416 -> CloudErrorKind.CORRUPTED
            code in 500..599 -> CloudErrorKind.STREAM_ERROR
            else -> CloudErrorKind.UNKNOWN
        }
        return CloudException(kind, code, retryAfterSec)
    }

    /** Erros que valem uma nova tentativa automática com espera. */
    fun isRetryable(e: CloudException): Boolean =
        e.kind == CloudErrorKind.RATE_LIMITED || e.httpCode in 500..599

    /** Espera exponencial com jitter (ms). [attempt] começa em 0. Respeita Retry-After quando existir. */
    fun backoffMs(attempt: Int, retryAfterSec: Long = 0L, jitter: Double = Math.random()): Long {
        if (retryAfterSec > 0) return (retryAfterSec * 1000).coerceAtMost(60_000)
        val base = 1000L shl attempt.coerceIn(0, 5)
        return (base + (base * 0.3 * jitter).toLong()).coerceAtMost(60_000)
    }
}
