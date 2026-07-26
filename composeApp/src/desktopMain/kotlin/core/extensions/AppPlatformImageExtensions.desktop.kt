package io.github.vrcmteam.vrcm.core.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.DesktopAppPlatform
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Desktop平台实现：保存图片到系统相册
 */
actual suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val platform = this@saveImageToGallery as DesktopAppPlatform
        val httpClient = HttpClient()
        val response = httpClient.get(imageUrl)

        // 获取用户图片目录
        val picturesDir = platform.getPicturesDirectory()
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

/**
 * Desktop平台实现：读取文件字节
 */
actual suspend fun AppPlatform.readFileBytes(filePath: String): ByteArray = withContext(Dispatchers.IO) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        // 读取文件字节
        Files.readAllBytes(file.toPath())
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }
}

/**
 * Desktop平台实现：获取图片尺寸
 */
actual suspend fun AppPlatform.getImageDimensions(filePath: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext null
        }

        // 使用ImageIO读取图片并获取尺寸
        val image = ImageIO.read(file)
        if (image != null) {
            Pair(image.width, image.height)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 辅助方法
private fun DesktopAppPlatform.getPicturesDirectory(): String {
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
