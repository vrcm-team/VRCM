package io.github.vrcmteam.vrcm.core.extensions

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.core.content.FileProvider

/**
 * Android平台实现：保存图片到系统相册
 */
actual suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        this@saveImageToGallery as AndroidAppPlatform
        val httpClient = HttpClient()
        val response = httpClient.get(imageUrl)
        val inputStream = response.bodyAsChannel().toInputStream()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageWithMediaStore(inputStream, fileName)
        } else {
            saveImageLegacy(inputStream, fileName)
        }

        httpClient.close()
        result
    }

/**
 * Android平台实现：保存已生成的图片字节到系统相册
 */
actual suspend fun AppPlatform.saveImageBytesToGallery(bytes: ByteArray, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        this@saveImageBytesToGallery as AndroidAppPlatform
        bytes.inputStream().use { inputStream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveImageWithMediaStore(inputStream, fileName)
            } else {
                saveImageLegacy(inputStream, fileName)
            }
        }
    }

actual suspend fun AppPlatform.shareImageBytes(bytes: ByteArray, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        this@shareImageBytes as AndroidAppPlatform
        if (bytes.isEmpty()) return@withContext false
        val shareFile = File(context.cacheDir, "share/${fileName.sanitizeShareFileName()}")
        shareFile.parentFile?.mkdirs()
        shareFile.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(fileName)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            runCatching {
                context.startActivity(
                    Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
        }
    }

actual suspend fun AppPlatform.shareImage(imageUrl: String, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        this@shareImage as AndroidAppPlatform
        val httpClient = HttpClient()
        try {
            val response = httpClient.get(imageUrl)
            shareImageBytes(response.bodyAsChannel().toInputStream().use { it.readBytes() }, fileName)
        } finally {
            httpClient.close()
        }
    }

/**
 * Android平台实现：读取文件字节
 */
actual suspend fun AppPlatform.readFileBytes(filePath: String): ByteArray = withContext(Dispatchers.IO) {

    val platform = this@readFileBytes as AndroidAppPlatform
    // 如果是content://开头的URI
    if (filePath.startsWith("content://")) {
        val uri = Uri.parse(filePath)
        platform.context.contentResolver.openInputStream(uri)?.use { inputStream ->
            return@withContext inputStream.readBytes()
        }
        throw IllegalArgumentException("Cannot open input stream for URI: $filePath")
    } else {
        // 如果是普通文件路径
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }
        return@withContext file.readBytes()
    }
}

/**
 * Android平台实现：获取图片尺寸
 */
actual suspend fun AppPlatform.getImageDimensions(filePath: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
    try {
        val platform = this@getImageDimensions as AndroidAppPlatform
        // 如果是content://开头的URI
        if (filePath.startsWith("content://")) {
            val uri = Uri.parse(filePath)
            platform.context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 使用BitmapFactory.Options来获取图片尺寸而不加载整个图片
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true // 只获取尺寸，不加载图片
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                if (options.outWidth > 0 && options.outHeight > 0) {
                    return@withContext Pair(options.outWidth, options.outHeight)
                }
            }
            return@withContext null
        } else {
            // 如果是普通文件路径
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext null
            }

            // 使用BitmapFactory.Options来获取图片尺寸而不加载整个图片
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true // 只获取尺寸，不加载图片
            }
            BitmapFactory.decodeFile(filePath, options)

            if (options.outWidth > 0 && options.outHeight > 0) {
                return@withContext Pair(options.outWidth, options.outHeight)
            }
            return@withContext null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}

/** 相册里的 MIME 必须与真实字节一致，PNG 写成 image/jpeg 会让部分相册应用打不开。 */
private fun mimeTypeFor(fileName: String): String =
    if (fileName.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"

private fun String.sanitizeShareFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image.png" }

// 辅助方法
private fun AndroidAppPlatform.saveImageWithMediaStore(inputStream: InputStream, fileName: String): Boolean {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(fileName))
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "VRCM")
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    return uri?.let { imageUri ->
        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        true
    } == true
}

private fun AndroidAppPlatform.saveImageLegacy(inputStream: InputStream, fileName: String): Boolean {
    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "VRCM")
    if (!directory.exists()) {
        directory.mkdirs()
    }

    val file = File(directory, fileName)
    return FileOutputStream(file).use { outputStream ->
        inputStream.copyTo(outputStream)
        true
    }
}

private fun AndroidAppPlatform.getPathFromUri(uri: Uri): String {
    // 对于content://类型的URI，直接返回URI字符串
    // 在readFileBytes方法中会特殊处理这种情况
    return uri.toString()
}
