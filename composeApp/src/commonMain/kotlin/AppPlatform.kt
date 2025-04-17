package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.compose.getKoin

interface AppPlatform {
    val name: String
    val version: String
    val type: AppPlatformType

    /**
     * 保存图片到系统相册
     * @param imageUrl 图片URL
     * @param fileName 文件名
     * @return 保存是否成功
     */
    suspend fun saveImageToGallery(imageUrl: String, fileName: String): Boolean

    /**
     * 从系统相册选择图片
     * @return 选择的图片文件路径，如果用户取消选择则返回null
     */
    suspend fun selectImageFromGallery(): String?

    /**
     * 读取文件字节
     * @param filePath 文件路径
     * @return 文件字节数组
     */
    suspend fun readFileBytes(filePath: String): ByteArray
}

enum class AppPlatformType {
    Android,
    Desktop,
    Ios,
    Web
}

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()
