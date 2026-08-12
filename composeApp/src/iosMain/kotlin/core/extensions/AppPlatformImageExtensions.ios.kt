package io.github.vrcmteam.vrcm.core.extensions

import io.github.vrcmteam.vrcm.AppPlatform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * iOS平台实现：保存图片到系统相册
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun AppPlatform.saveImageToGallery(imageUrl: String, fileName: String): Boolean =
    withContext(Dispatchers.IO) {

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
 * iOS平台实现：保存已生成的图片字节到系统相册
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun AppPlatform.saveImageBytesToGallery(bytes: ByteArray, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext false
        if (!requestPhotoLibraryPermission()) return@withContext false
        savePhotoData(bytes.toNSData())
    }

@OptIn(ExperimentalForeignApi::class)
actual suspend fun AppPlatform.shareImageBytes(bytes: ByteArray, fileName: String): Boolean =
    withContext(Dispatchers.Main) {
        if (bytes.isEmpty()) return@withContext false
        sharePhotoData(bytes.toNSData())
    }

@OptIn(ExperimentalForeignApi::class)
private fun sharePhotoData(data: NSData): Boolean {
        val presenter = UIApplication.sharedApplication.keyWindow
            ?.rootViewController
            ?.topPresentedViewController()
            ?: return false
        val controller = UIActivityViewController(
            activityItems = listOf(data),
            applicationActivities = null,
        )
        controller.popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
            permittedArrowDirections = 0u
        }
        presenter.presentViewController(controller, animated = true, completion = null)
        return true
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun AppPlatform.shareImage(imageUrl: String, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        val url = NSURL.URLWithString(imageUrl) ?: return@withContext false
        val data = NSData.dataWithContentsOfURL(url) ?: return@withContext false
        withContext(Dispatchers.Main) { sharePhotoData(data) }
    }

private tailrec fun UIViewController.topPresentedViewController(): UIViewController =
    presentedViewController?.topPresentedViewController() ?: this

/** 把已编码的图片数据写入相册，等待系统回调后再返回结果。 */
private suspend fun savePhotoData(data: NSData): Boolean = withContext(Dispatchers.IO) {
    var success = false
    val semaphore = Semaphore(1)
    semaphore.acquire()

    PHPhotoLibrary.sharedPhotoLibrary().performChanges({
        PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
            PHAssetResourceTypePhoto,
            data,
            null,
        )
    }, { didSucceed, _ ->
        success = didSucceed
        semaphore.release()
    })

    semaphore.acquire()
    semaphore.release()
    success
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
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
    authorized
}

/**
 * iOS平台实现：读取文件字节
 */
actual suspend fun AppPlatform.readFileBytes(filePath: String): ByteArray = ByteArray(0)

/**
 * iOS平台实现：获取图片尺寸
 */
actual suspend fun AppPlatform.getImageDimensions(filePath: String): Pair<Int, Int>? = null
