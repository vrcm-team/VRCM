package io.github.vrcmteam.vrcm

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

class DesktopAppPlatform : AppPlatform {
    override val name: String  = System.getProperty("os.name")
    override val version: String  = System.getProperty("os.version")
    override val type: AppPlatformType  = AppPlatformType.Desktop
    
    override suspend fun saveImageToGallery(imageUrl: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val httpClient = HttpClient()
            val response = httpClient.get(imageUrl)
            
            // 获取用户图片目录
            val picturesDir = getPicturesDirectory()
            val vrcmDir = File(picturesDir, "VRCM")
            if (!vrcmDir.exists()) {
                vrcmDir.mkdirs()
            }
            
            // 创建文件
            val file = File(vrcmDir, fileName)
            
            // 保存图片
            response.bodyAsChannel().toInputStream().use { input ->
                Files.copy(input, file.toPath())
            }
            
            httpClient.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun getPicturesDirectory(): String {
        // 尝试获取系统图片目录
        val userHome = System.getProperty("user.home")
        val picturesPath = when {
            File(userHome, "Pictures").exists() -> File(userHome, "Pictures").absolutePath
            File(userHome, "图片").exists() -> File(userHome, "图片").absolutePath
            File(userHome, "Images").exists() -> File(userHome, "Images").absolutePath
            else -> userHome // 如果没有找到图片目录，就使用用户主目录
        }
        return picturesPath
    }
}