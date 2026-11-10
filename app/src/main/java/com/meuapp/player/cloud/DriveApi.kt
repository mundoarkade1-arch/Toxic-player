package com.meuapp.player.cloud

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Cliente mínimo da Google Drive API v3 (endpoints oficiais, via HttpURLConnection para não adicionar dependências).
 * Regras: o token vai SEMPRE no cabeçalho Authorization (nunca na URL); nada é registrado em log;
 * mensagens de erro nunca carregam URL, token ou corpo da resposta.
 */
class DriveApi(private val tokens: TokenProvider = DefaultTokens) {

    interface TokenProvider {
        fun get(): String
        fun refresh(): String
    }
    object DefaultTokens : TokenProvider {
        override fun get() = CloudAuth.tokenBlocking()
        override fun refresh() = CloudAuth.refreshBlocking()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- chamadas de alto nível ----------

    fun about(): DriveAbout = DriveJson.parseAbout(
        getText("/about?fields=" + enc("user(displayName,emailAddress),storageQuota(limit,usage)"))
    )

    /**
     * Lista arquivos candidatos a áudio. A consulta pega tipos de áudio, ogg e octet-stream (o Drive às vezes classifica
     * FLAC/OPUS assim); o filtro final por extensão/MIME é feito em [DriveJson.parseFile].
     */
    fun listAudio(pageToken: String?): FilesPage {
        val q = "trashed=false and (mimeType contains 'audio/' or mimeType='application/ogg' or mimeType='application/octet-stream')"
        val fields = "nextPageToken,files(id,name,mimeType,size,modifiedTime,createdTime,md5Checksum,trashed)"
        val sb = StringBuilder("/files?q=").append(enc(q))
            .append("&fields=").append(enc(fields))
            .append("&pageSize=").append(CloudConfig.PAGE_SIZE)
            .append("&spaces=drive&corpora=user")
        if (pageToken != null) sb.append("&pageToken=").append(enc(pageToken))
        return DriveJson.parseFilesPage(getText(sb.toString()))
    }

    fun startPageToken(): String {
        val t = org.json.JSONObject(getText("/changes/startPageToken")).optString("startPageToken")
        if (t.isBlank()) throw CloudException(CloudErrorKind.SYNC_ERROR)
        return t
    }

    fun changes(pageToken: String): ChangesPage {
        val fields = "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,size,modifiedTime,createdTime,md5Checksum,trashed))"
        val path = "/changes?pageToken=" + enc(pageToken) + "&fields=" + enc(fields) +
            "&pageSize=" + CloudConfig.PAGE_SIZE + "&includeRemoved=true&spaces=drive&restrictToMyDrive=true"
        return DriveJson.parseChangesPage(getText(path))
    }

    /**
     * Leitura parcial (HTTP Range) de [length] bytes a partir de [offset]. Exige resposta 206.
     * Se o servidor ignorar o Range e responder 200, só aceita quando offset == 0 (lendo apenas o início do fluxo);
     * nunca baixa o arquivo inteiro.
     */
    fun readRange(fileId: String, offset: Long, length: Int, fileSize: Long): ByteArray {
        if (offset < 0 || length <= 0 || offset >= fileSize) return ByteArray(0)
        val end = minOf(fileSize - 1, offset + length - 1)
        return withAuth { token ->
            val c = open("/files/$fileId?alt=media&supportsAllDrives=true", token)
            c.setRequestProperty("Range", "bytes=$offset-$end")
            try {
                val code = c.responseCode
                if (code == 206) return@withAuth readUpTo(c.inputStream, (end - offset + 1).toInt())
                if (code == 200 && offset == 0L) return@withAuth readUpTo(c.inputStream, (end + 1).toInt())
                if (code == 200) throw CloudException(CloudErrorKind.STREAM_ERROR, 200)   // Range não suportado
                throw errorFor(c, code)
            } finally { c.disconnect() }
        }
    }

    /** Resultado de um teste de suporte a Range (usado no diagnóstico da interface). */
    data class RangeProbe(val status: Int, val contentRange: String?, val acceptRanges: String?, val bytes: Int)

    fun probeRange(fileId: String): RangeProbe = withAuth { token ->
        val c = open("/files/$fileId?alt=media&supportsAllDrives=true", token)
        c.setRequestProperty("Range", "bytes=0-1023")
        try {
            val code = c.responseCode
            if (code != 200 && code != 206) throw errorFor(c, code)
            RangeProbe(code, c.getHeaderField("Content-Range"), c.getHeaderField("Accept-Ranges"), readUpTo(c.inputStream, 1024).size)
        } finally { c.disconnect() }
    }

    /**
     * Baixa o arquivo inteiro para [dest] (modo offline). Verifica tamanho e MD5 (quando o Drive informa).
     * [onProgress] recebe bytes baixados; [isCancelled] permite abortar.
     */
    fun download(file: DriveFile, dest: File, onProgress: (Long) -> Unit, isCancelled: () -> Boolean) {
        withAuth { token ->
            val c = open("/files/${file.id}?alt=media&supportsAllDrives=true", token)
            try {
                val code = c.responseCode
                if (code != 200) throw errorFor(c, code)
                val md = MessageDigest.getInstance("MD5")
                var total = 0L
                try {
                    c.inputStream.use { input ->
                        FileOutputStream(dest).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (isCancelled()) throw CloudException(CloudErrorKind.CANCELLED)
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n); md.update(buf, 0, n); total += n
                                onProgress(total)
                            }
                            out.fd.sync()
                        }
                    }
                } catch (e: CloudException) { throw e
                } catch (e: IOException) {
                    throw if (CloudErrors.isNoSpace(e)) CloudException(CloudErrorKind.NO_SPACE, cause = e) else e
                }
                if (total != file.size) throw CloudException(CloudErrorKind.CORRUPTED)
                val want = file.md5
                if (want != null && !md.digest().joinToString("") { "%02x".format(it) }.equals(want, true))
                    throw CloudException(CloudErrorKind.CORRUPTED)
            } finally { c.disconnect() }
            Unit
        }
    }

    // ---------- infraestrutura HTTP ----------

    private fun open(path: String, token: String): HttpURLConnection {
        val url = if (path.startsWith("http")) path else CloudConfig.API + path
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = CloudConfig.CONNECT_TIMEOUT_MS
        c.readTimeout = CloudConfig.READ_TIMEOUT_MS
        c.instanceFollowRedirects = false          // não segue redirecionamentos com o token anexado
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    private fun getText(path: String): String = withAuth { token ->
        val c = open(path, token)
        try {
            val code = c.responseCode
            if (code != 200) throw errorFor(c, code)
            c.inputStream.use { readUpTo(it, 16 * 1024 * 1024).toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    private fun errorFor(c: HttpURLConnection, code: Int): CloudException {
        val body = try { c.errorStream?.use { readUpTo(it, 8 * 1024).toString(Charsets.UTF_8) } } catch (e: Exception) { null }
        val reason = body?.let { DriveJson.parseErrorReason(it) }
        val retry = c.getHeaderField("Retry-After")?.toLongOrNull() ?: 0L
        return CloudErrors.fromHttp(code, reason, retry)
    }

    /**
     * Executa [block] com token, renovando uma vez em caso de 401 e repetindo com espera exponencial
     * em erros de limite de taxa ou 5xx.
     */
    private fun <T> withAuth(block: (String) -> T): T {
        var token = tokens.get()
        var refreshed = false
        var attempt = 0
        while (true) {
            try {
                return block(token)
            } catch (e: CloudException) {
                if (e.kind == CloudErrorKind.TOKEN_EXPIRED && !refreshed) {
                    refreshed = true
                    token = tokens.refresh()
                    continue
                }
                if (CloudErrors.isRetryable(e) && attempt < CloudConfig.MAX_RETRIES) {
                    Thread.sleep(CloudErrors.backoffMs(attempt, e.retryAfterSec))
                    attempt++
                    continue
                }
                throw e
            } catch (e: IOException) {
                // Falha de rede: converte para um erro conhecido, com uma segunda chance antes de desistir.
                val kind = CloudErrors.classify(e)
                if (kind == CloudErrorKind.NO_INTERNET && attempt < 1) { Thread.sleep(800); attempt++; continue }
                throw if (e is CloudException) e else CloudException(kind, cause = e)
            }
        }
    }

    private fun readUpTo(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(max, 64 * 1024))
        val buf = ByteArray(16 * 1024)
        var left = max
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n < 0) break
            out.write(buf, 0, n); left -= n
        }
        return out.toByteArray()
    }
}
