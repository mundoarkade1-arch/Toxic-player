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
    val folderName: String
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
