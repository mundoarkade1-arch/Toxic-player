package com.meuapp.player

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** Valor especial: "todas as músicas". */
const val ALL_FOLDERS = "__ALL__"

/** Nome da lista de favoritas. */
const val FAVORITES = "Favoritas"

/** Um grupo da biblioteca. [path] é a chave: "F:pasta", "A:artista" ou "B:álbum". */
data class Folder(val path: String, val name: String, val count: Int)

/** Uma música encontrada no celular. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folderPath: String,
    val folderName: String,
    val filePath: String = "",
    /** Toxic Cloud: id do arquivo no Google Drive (null = música local). */
    val cloudId: String? = null,
    /** Muda quando a capa da nuvem passa a existir, para a interface recarregar a imagem. */
    val coverStamp: Long = 0L
) {
    /** Música local: URI do MediaStore. Música da nuvem: URI interna de streaming (nunca um link do Drive). */
    val uri: Uri get() =
        if (cloudId != null) Uri.parse("toxiccloud://drive/$cloudId")
        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

