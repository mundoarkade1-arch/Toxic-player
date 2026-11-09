package com.meuapp.player

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Leitura de arquivos escolhidos pelo usuário com limite de tamanho (evita estourar a memória com arquivos gigantes). */
object SafeRead {
    const val MAX_TEXT_BYTES = 1_048_576   // 1 MB: sobra para qualquer playlist ou preset real

    /** Devolve o texto, ou null se o arquivo não pôde ser lido ou passa do limite. */
    fun readText(ctx: Context, uri: Uri, maxBytes: Int = MAX_TEXT_BYTES): String? =
        try {
            ctx.contentResolver.openInputStream(uri)?.use { readLimited(it, maxBytes) }
        } catch (e: Exception) {
            null
        }

    /** Lê o fluxo até [maxBytes]; se passar disso, interrompe e devolve null. */
    fun readLimited(input: InputStream, maxBytes: Int): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
