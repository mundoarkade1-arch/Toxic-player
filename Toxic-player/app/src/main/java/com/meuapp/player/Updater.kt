package com.meuapp.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Atualização via GitHub Releases, com verificações de versão, integridade e assinatura. */
object Updater {
    sealed class Check {
        data class Available(val info: UpdateInfo) : Check()
        data class UpToDate(val latestName: String) : Check()
        data class Failed(val message: String) : Check()
    }

    private const val MIN_INTERVAL_MS = 10 * 60 * 1000L

    /** Consulta a última Release (com cache de 10 min e ETag para não estourar o limite do GitHub). */
    suspend fun check(ctx: Context, force: Boolean): Check = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext Check.Failed("Versão de teste (debug): atualização desativada")
        val prefs = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var body: String? = prefs.getString("body", null)
        val last = prefs.getLong("last", 0L)
        if (force || body == null || now - last > MIN_INTERVAL_MS) {
            try {
                val api = "https://api.github.com/repos/${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}/releases/latest"
                val c = URL(api).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "ToxicPlayer")
                val etag = prefs.getString("etag", null)
                if (etag != null && body != null) c.setRequestProperty("If-None-Match", etag)
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                when (val code = c.responseCode) {
                    200 -> {
                        body = c.inputStream.bufferedReader().use { it.readText() }
                        prefs.edit().putString("body", body).putString("etag", c.getHeaderField("ETag")).putLong("last", now).apply()
                    }
                    304 -> prefs.edit().putLong("last", now).apply()
                    404 -> return@withContext Check.Failed("Nenhuma versão publicada no repositório ainda")
                    403, 429 -> return@withContext Check.Failed("Limite de consultas do GitHub atingido. Tente mais tarde.")
                    else -> return@withContext Check.Failed("O GitHub respondeu com erro $code")
                }
            } catch (e: IOException) {
                return@withContext Check.Failed("Sem conexão com a internet")
            }
        }
        val info = body?.let { UpdateLogic.parse(it) }
            ?: return@withContext Check.Failed("A última versão publicada não tem um APK válido")
        if (UpdateLogic.isNewer(info.versionCode, BuildConfig.VERSION_CODE.toLong())) {
            Check.Available(info)
        } else {
            File(ctx.cacheDir, "update").deleteRecursively() // limpa APK antigo já instalado
            Check.UpToDate(info.versionName)
        }
    }

    /** Baixa para um arquivo temporário, confere tamanho/hash e só então renomeia. Reaproveita download já válido. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "ToxicPlayer-c${info.versionCode}.apk")
        dir.listFiles()?.filter { it.name != out.name }?.forEach { it.delete() }
        if (out.exists() && integrityError(out, info) == null) { onProgress(1f); return@withContext out }
        val part = File(dir, out.name + ".part")
        part.delete()
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
        val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
        c.inputStream.use { input ->
            part.outputStream().use { o ->
                val buf = ByteArray(16 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        out.delete()
        if (!part.renameTo(out)) throw IOException("Não foi possível salvar o APK")
        integrityError(out, info)?.let { out.delete(); throw IOException(it) }
        out
    }

    private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun integrityError(f: File, info: UpdateInfo): String? {
        if (info.size > 0 && f.length() != info.size) return "Download incompleto"
        if (info.sha256 != null && !sha256Hex(f.readBytes()).equals(info.sha256, ignoreCase = true)) return "Arquivo corrompido (hash diferente)"
        return null
    }

    private fun certs(si: SigningInfo?): Set<String> =
        si?.apkContentsSigners?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()

    /** Confere pacote, versionCode e assinatura do APK baixado contra o app instalado. Retorna a mensagem de erro, ou null se estiver tudo certo. */
    fun verifyApk(ctx: Context, apk: File, info: UpdateInfo): String? {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val arch = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return "APK inválido ou corrompido"
        if (arch.packageName != ctx.packageName) return "O APK é de outro aplicativo"
        if (arch.longVersionCode != info.versionCode) return "O APK não corresponde à versão anunciada"
        val installed = certs(pm.getPackageInfo(ctx.packageName, flags).signingInfo)
        val incoming = certs(arch.signingInfo)
        if (installed.isEmpty() || installed != incoming) return "Assinatura diferente da versão instalada: o Android não aceitaria a atualização"
        return null
    }

    /** Abre o instalador do Android. Retorna false se faltar a permissão "instalar apps desconhecidos". */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
