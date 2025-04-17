package io.github.vrcmteam.vrcm

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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


class AndroidAppPlatform(val context: Context) : AppPlatform {
    override val name: String = "Android"
    override val version: String = Build.VERSION.SDK_INT.toString()
    override val type: AppPlatformType = AppPlatformType.Android

    override suspend fun saveImageToGallery(imageUrl: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveImageWithMediaStore(inputStream: InputStream, fileName: String): Boolean {
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

    private fun saveImageLegacy(inputStream: InputStream, fileName: String): Boolean {
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

    override suspend fun selectImageFromGallery(): String? = suspendCancellableCoroutine { continuation ->
        try {
            // 确保context是Activity类型
            if (context !is ComponentActivity) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val activity = context as ComponentActivity

            // 使用ActivityResultContracts.GetContent来选择图片
            val getContent = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    // 获取文件的真实路径
                    val filePath = getPathFromUri(uri)
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

    override suspend fun readFileBytes(filePath: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            // 如果是content://开头的URI
            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
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

    // 从Uri获取文件路径
    private fun getPathFromUri(uri: Uri): String {
        // 对于content://类型的URI，直接返回URI字符串
        // 在readFileBytes方法中会特殊处理这种情况
        return uri.toString()
    }
}
