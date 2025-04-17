package io.github.vrcmteam.vrcm
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.core.readBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIDevice
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

class IosAppPlatform: AppPlatform {
    override val name: String = UIDevice.currentDevice.systemName()
    override val version: String = UIDevice.currentDevice.systemVersion
    override val type: AppPlatformType = AppPlatformType.Ios
    
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun saveImageToGallery(imageUrl: String, fileName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val httpClient = HttpClient()
            val response = httpClient.get(imageUrl)
            
            // 将响应转换为NSData
            val byteArray = response.bodyAsChannel().readRemaining().readBytes()
            val nsData = NSData.create(bytes = byteArray, length = byteArray.size.toULong())
            
            // 创建UIImage
            val uiImage = UIImage.imageWithData(nsData) ?: return@withContext false
            
            // 保存到相册
            var success = false
            UIImageWriteToSavedPhotosAlbum(
                uiImage,
                null,
                null,
                null
            )
            
            httpClient.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}


