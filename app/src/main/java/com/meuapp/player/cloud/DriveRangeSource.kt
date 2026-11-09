package com.meuapp.player.cloud

import com.meuapp.player.cloud.tags.RangeSource

/** Lê trechos de um arquivo do Drive por HTTP Range, com cache do primeiro bloco para reduzir requisições. */
class DriveRangeSource(private val api: DriveApi, private val fileId: String, override val size: Long) : RangeSource {
    private var head: ByteArray? = null

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= size || length <= 0) return ByteArray(0)
        val end = offset + length
        if (offset == 0L && head == null) head = api.readRange(fileId, 0, maxOf(length, CloudConfig.HEAD_CHUNK), size)
        val h = head
        if (h != null && end <= h.size) return h.copyOfRange(offset.toInt(), end.toInt())
        if (h != null && offset < h.size && h.size.toLong() >= size) {                 // arquivo menor que o bloco
            return h.copyOfRange(offset.toInt(), minOf(h.size.toLong(), end).toInt())
        }
        // Leitura que não cabe no bloco inicial: uma requisição própria (apenas o trecho pedido).
        return api.readRange(fileId, offset, length, size)
    }
}
