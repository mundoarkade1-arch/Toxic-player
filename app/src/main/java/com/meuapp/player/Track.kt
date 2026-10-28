package com.meuapp.player

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** Uma música encontrada no celular. */
data class Track(val id: Long, val title: String, val artist: String, val durationMs: Long) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
