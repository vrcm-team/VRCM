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
}

enum class AppPlatformType {
    Android,
    Desktop,
    Ios,
    Web
}

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()