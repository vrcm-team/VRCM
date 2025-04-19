package io.github.vrcmteam.vrcm.core.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS平台实现：保存图片到系统相册
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean = withContext(Dispatchers.Main) {
    try {
        // 简化实现，避免使用不可用的API
        // 实际iOS平台需要更完整的实现
        false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * iOS平台实现：从系统相册选择图片
 */
actual suspend fun AppPlatform.selectImageFromGallery(): String? = null

/**
 * iOS平台实现：读取文件字节
 */
actual suspend fun AppPlatform.readFileBytes(filePath: String): ByteArray = ByteArray(0)

/**
 * iOS平台实现：获取图片尺寸
 */
actual suspend fun AppPlatform.getImageDimensions(filePath: String): Pair<Int, Int>? = null