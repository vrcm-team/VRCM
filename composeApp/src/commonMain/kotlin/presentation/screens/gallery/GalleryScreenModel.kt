package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.runtime.mutableStateMapOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.core.extensions.readFileBytes
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

class GalleryScreenModel(
    private val authService: AuthService,
    private val fileApi: FileApi,
    private val logger: Logger,
    private val platform: AppPlatform,
) : ScreenModel {

    // 使用Map存储不同标签类型的图片文件列表
    private val _filesByTag = mutableStateMapOf<FileTagType, List<FileData>>().apply {
        FileTagType.entries.forEach { tagType ->
            this[tagType] = emptyList()
        }
    }

    // 使用Map存储不同标签类型的刷新状态
    private val _isRefreshingByTag = mutableStateMapOf<FileTagType, Boolean>().apply {
        FileTagType.entries.forEach { tagType ->
            this[tagType] = false
        }
    }

    fun init() {
        refreshAllFiles()
    }

    private fun refreshAllFiles() {
        refreshFiles(FileTagType.Icon)
        refreshFiles(FileTagType.Emoji)
        refreshFiles(FileTagType.Sticker)
        refreshFiles(FileTagType.Gallery)
    }

    /**
     * 根据标签类型刷新文件列表
     */
    fun refreshFiles(tagType: FileTagType, n: Int = 60, offset: Int = 0) {
        // 设置刷新状态为true
        _isRefreshingByTag[tagType] = true

        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { fileApi.getFiles(tagType, n = n, offset = offset) }
                .onGalleryFailure()
                .onSuccess { files ->
                    // 更新文件列表
                    _filesByTag[tagType] = files
                }
                .also {
                    // 设置刷新状态为false
                    _isRefreshingByTag[tagType] = false
                }
        }
    }

    /**
     * 根据标签类型获取对应的文件列表
     */
    fun getFilesByTag(tagType: FileTagType): List<FileData> {
        return _filesByTag[tagType] ?: emptyList()
    }

    /**
     * 根据标签类型获取对应的刷新状态
     */
    fun isRefreshingByTag(tagType: FileTagType): Boolean {
        return _isRefreshingByTag[tagType] ?: false
    }

    private inline fun <T> Result<T>.onGalleryFailure() =
        onApiFailure("Gallery") {
            logger.error(it)
            screenModelScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
        }

    /**
     * 上传图片到服务器
     * @param imagePath 图片文件路径
     */
    fun uploadImage(imagePath: String, fileTagType: FileTagType) {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                // 通知用户正在上传
                SharedFlowCentre.toastText.emit(ToastText.Info("正在上传图片..."))

                // 读取文件字节
                val fileBytes = platform.readFileBytes(imagePath)

                // 获取文件名
                val fileName = imagePath.substringAfterLast('\\').substringAfterLast('/')

                // 获取MIME类型
                val mimeType = getMimeType(fileName)

                // 上传图片文件
                val result = fileApi.uploadImageFile(fileBytes, fileName, mimeType, fileTagType)

                result.onSuccess {
                    // 上传成功，刷新图片列表
                    SharedFlowCentre.toastText.emit(ToastText.Success("图片上传成功"))
                    refreshFiles(FileTagType.Gallery)
                }.onFailure {
                    // 上传失败
                    SharedFlowCentre.toastText.emit(ToastText.Error("图片上传失败: ${it.message}"))
                    logger.error("Upload failed: ${it.message}")
                }
            } catch (e: Exception) {
                // 处理异常
                SharedFlowCentre.toastText.emit(ToastText.Error("图片上传失败: ${e.message}"))
                logger.error("Upload exception: ${e.message}")
            }
        }
    }


    /**
     * 根据文件名获取MIME类型
     */
    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", ignoreCase = true) || 
            fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".bmp", ignoreCase = true) -> "image/bmp"
            else -> "application/octet-stream"
        }
    }
}
