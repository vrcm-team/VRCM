package io.github.vrcmteam.vrcm.core.extensions

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.vrcmteam.vrcm.AndroidAppPlatform
import io.github.vrcmteam.vrcm.AppPlatform
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume

/**
 * Android平台实现：保存图片到系统相册
 */
actual suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val platform = this@saveImageToGallery as AndroidAppPlatform
        val httpClient = HttpClient()
        val response = httpClient.get(imageUrl)
        val inputStream = response.bodyAsChannel().toInputStream()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            platform.saveImageWithMediaStore(inputStream, fileName)
        } else {
            platform.saveImageLegacy(inputStream, fileName)
        }

        httpClient.close()
        result
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Android平台实现：从系统相册选择图片
 */
actual suspend fun AppPlatform.selectImageFromGallery(): String? {
    val platform = this as AndroidAppPlatform
    return suspendCancellableCoroutine { continuation ->
        try {
            // 确保context是Activity类型
            if (platform.context !is ComponentActivity) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val activity = platform.context

            // 使用ActivityResultContracts.GetContent来选择图片
            val getContent = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    // 获取文件的真实路径
                    val filePath = platform.getPathFromUri(uri)
                    continuation.resume(filePath)
                } else {
                    // 用户取消选择
                    continuation.resume(null)
                }
            }

            // 启动图片选择器
            getContent.launch("image/*")

            // 如果协程被取消，注销ActivityResultLauncher
            continuation.invokeOnCancellation {
                getContent.unregister()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            continuation.resume(null)
        }
    }
}

/**
 * Android平台实现：读取文件字节
 */
actual suspend fun AppPlatform.readFileBytes(filePath: String): ByteArray = withContext(Dispatchers.IO) {
    try {
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
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
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

// 辅助方法
private fun AndroidAppPlatform.saveImageWithMediaStore(inputStream: InputStream, fileName: String): Boolean {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "VRCM")
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    return uri?.let { imageUri ->
        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        true
    } ?: false
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