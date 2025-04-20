package io.github.vrcmteam.vrcm.core.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

/**
 * iOS平台实现：保存图片到系统相册
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean =
    withContext(Dispatchers.Main) {

        // 检查相册权限
        if (!requestPhotoLibraryPermission()) {
            return@withContext false
        }

        // 创建URL并下载图片数据
        val url = NSURL.URLWithString(imageUrl) ?: return@withContext false
        val imageData = NSData.dataWithContentsOfURL(url) ?: return@withContext false

        // 转换为UIImage
        val image = UIImage.imageWithData(imageData) ?: return@withContext false

        // 根据文件扩展名选择合适的图片格式
        val imageDataToSave = when {
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> {
                UIImageJPEGRepresentation(image, 0.9)
            }

            else -> {
                UIImagePNGRepresentation(image)
            }
        } ?: return@withContext false

        // 保存图片到相册
        var success = false
        val semaphore = Semaphore(1)
        semaphore.acquire()

        PHPhotoLibrary.sharedPhotoLibrary().performChanges({
            // 创建图片保存请求
            PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
                PHAssetResourceTypePhoto,
                imageDataToSave,
                null
            )
        }, { didSucceed, error ->
            success = didSucceed
            if (!didSucceed && error != null) {
                error("保存图片失败: ${error.localizedDescription}")
            }
            semaphore.release()
        })

        // 等待操作完成
        semaphore.acquire()
        semaphore.release()
        return@withContext success

    }

/**
 * 请求相册权限
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun requestPhotoLibraryPermission(): Boolean = withContext(Dispatchers.Main) {
    val semaphore = Semaphore(1)
    semaphore.acquire()

    var authorized = false
    val authStatus = PHPhotoLibrary.authorizationStatus()

    if (authStatus == PHAuthorizationStatusAuthorized) {
        authorized = true
        semaphore.release()
    } else {
        PHPhotoLibrary.requestAuthorization { status ->
            authorized = (status == PHAuthorizationStatusAuthorized)
            semaphore.release()
        }
    }

    semaphore.acquire()
    semaphore.release()
    return@withContext authorized
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