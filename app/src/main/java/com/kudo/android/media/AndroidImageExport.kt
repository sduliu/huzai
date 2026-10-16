package com.kudo.android.media

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.IOException

class ImageFileProvider : FileProvider()

internal class GalleryImageStore(private val context: Context) : ImageStore {
    override fun save(image: OriginalImageFile) { insert(image) }

    fun insert(image: OriginalImageFile): Uri {
        val resolver = context.contentResolver
        val modern = Build.VERSION.SDK_INT >= 29
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
            if (modern) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/酷DO")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (modern) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: throw IOException("无法创建相册图片")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("无法写入相册")
            output.use { stream -> image.file.inputStream().use { it.copyTo(stream) } }
            if (modern && resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 0) {
                throw IOException("无法完成图片保存")
            }
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}

internal fun imageShareIntent(context: Context, image: OriginalImageFile): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.image-files", image.file, image.displayName)
    return Intent(Intent.ACTION_SEND).apply {
        type = image.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(image.displayName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
