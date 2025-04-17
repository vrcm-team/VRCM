package io.github.vrcmteam.vrcm

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

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

    override suspend fun selectImageFromGallery(): String? = withContext(Dispatchers.IO) {
        try {
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "选择图片"
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.isAcceptAllFileFilterUsed = false

            // 设置文件过滤器，只显示图片文件
            val filter = FileNameExtensionFilter(
                "图片文件", "jpg", "jpeg", "png", "gif", "bmp"
            )
            fileChooser.addChoosableFileFilter(filter)

            // 显示文件选择对话框
            val result = fileChooser.showOpenDialog(null)

            // 如果用户选择了文件，返回文件路径
            if (result == JFileChooser.APPROVE_OPTION) {
                return@withContext fileChooser.selectedFile.absolutePath
            }

            // 用户取消选择，返回null
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun readFileBytes(filePath: String): ByteArray = withContext(Dispatchers.IO) {
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
