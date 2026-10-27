package com.kimgdecierdo.kimp.engine

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies finished files into the phone's shared storage: MP3s to Music/KIMP, MP4s to Movies/KIMP,
 * so they show up in music players and the gallery. No storage permission is needed (Android 10+).
 */
object MediaSaver {
    enum class Result { SAVED, ALREADY_THERE }

    fun save(context: Context, file: File, subfolder: String?): Result {
        val audio = file.extension.equals("mp3", ignoreCase = true)
        val collection = if (audio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val base = (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/KIMP/"
        val relativePath = base + (subfolder?.let { safeName(it) + "/" } ?: "")
        val resolver = context.contentResolver

        val exists = resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(file.name, relativePath), null,
        )?.use { it.count > 0 } == true
        if (exists) return Result.ALREADY_THERE

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (audio) "audio/mpeg" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Couldn't create ${file.name} in $relativePath")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Result.SAVED
    }

    private fun safeName(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(100)
}
