package io.github.takusan23.androidreversevideo.processor.tool

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreTool {

    /** 端末の動画フォルダに保存する */
    suspend fun saveToVideoFolder(
        context: Context,
        file: File
    ) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val contentValues = contentValuesOf(
            MediaStore.MediaColumns.DISPLAY_NAME to file.name,
            MediaStore.MediaColumns.RELATIVE_PATH to "${Environment.DIRECTORY_MOVIES}/AndroidReverseVideo",
            MediaStore.MediaColumns.MIME_TYPE to "video/mp4"
        )
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext
        // コピーする
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    /** Uri のファイル名を取得する */
    suspend fun getFileName(
        context: Context,
        uri: Uri
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)!!.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
        }
    }

}