package com.meuapp.player

import org.json.JSONException
import org.json.JSONObject

/** Uma versão publicada no GitHub, já validada. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val size: Long,
    val sha256: String?,
    val notes: String
)

/** Regras puras (sem Android) para decidir se existe atualização. Têm testes em src/test. */
object UpdateLogic {
    /** O workflow publica o APK com este nome: ToxicPlayer-v1.2.7-c107.apk */
    private val APK_RE = Regex("""^ToxicPlayer-v(.+)-c(\d+)\.apk$""")

    /**
     * Lê o JSON de uma Release do GitHub. Devolve null se for rascunho, pré-lançamento,
     * JSON inválido ou se não tiver nenhum APK com o nome esperado.
     * A fonte da verdade é o versionCode escrito no nome do APK (e conferido de novo após o download).
     */
    fun parse(json: String): UpdateInfo? = try {
        val j = JSONObject(json)
        if (j.optBoolean("draft", false) || j.optBoolean("prerelease", false)) {
            null
        } else {
            val assets = j.optJSONArray("assets")
            var best: UpdateInfo? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("state", "uploaded") != "uploaded") continue
                    val m = APK_RE.matchEntire(a.optString("name", "")) ?: continue
                    val code = m.groupValues[2].toLongOrNull() ?: continue
                    val url = a.optString("browser_download_url", "")
                    if (url.isBlank()) continue
                    val digest = a.optString("digest", "").removePrefix("sha256:").takeIf { it.length == 64 }
                    val cand = UpdateInfo(m.groupValues[1], code, url, a.optLong("size", -1L), digest, j.optString("body", ""))
                    if (best == null || cand.versionCode > best.versionCode) best = cand
                }
            }
            best
        }
    } catch (e: JSONException) {
        null
    }

    /** Só existe atualização se o código publicado for MAIOR que o instalado. */
    fun isNewer(availableCode: Long, installedCode: Long): Boolean = availableCode > installedCode
}
