package com.meuapp.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Atualização automática: consulta a última Release do GitHub, baixa o APK e abre o instalador. */
object Updater {
    data class Info(val tag: String, val url: String, val notes: String)

    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        try {
            val api = "https://api.github.com/repos/${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}/releases/latest"
            val c = URL(api).openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            if (c.responseCode != 200) return@withContext null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = j.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
            }
            if (apk == null) null else Info(j.getString("tag_name"), apk, j.optString("body"))
        } catch (e: Exception) { null }
    }

    /** Compara versões numéricas: "v1.2.0" > "1.1.9". */
    fun isNewer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(tag); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "app.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(16 * 1024); var done = 0L
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        out
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
