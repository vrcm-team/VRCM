package io.github.vrcmteam.vrcm

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


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
}
