package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
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
) : ScreenModel {
    
    // 存储不同标签类型的图片文件列表
    private val _iconFiles = mutableStateOf<List<FileData>>(emptyList())
    val iconFiles by _iconFiles
    
    private val _emojiFiles = mutableStateOf<List<FileData>>(emptyList())
    val emojiFiles by _emojiFiles
    
    private val _stickerFiles = mutableStateOf<List<FileData>>(emptyList())
    val stickerFiles by _stickerFiles
    
    private val _galleryFiles = mutableStateOf<List<FileData>>(emptyList())
    val galleryFiles by _galleryFiles
    
    // 记录各标签页是否正在刷新
    var isIconRefreshing by mutableStateOf(false)
        private set
    
    var isEmojiRefreshing by mutableStateOf(false)
        private set
    
    var isStickerRefreshing by mutableStateOf(false)
        private set
    
    var isGalleryRefreshing by mutableStateOf(false)
        private set
    
    fun init() {
        refreshAllFiles()
    }
    
    fun refreshAllFiles() {
        refreshFiles(FileTagType.Icon)
        refreshFiles(FileTagType.Emoji)
        refreshFiles(FileTagType.Sticker)
        refreshFiles(FileTagType.Gallery)
    }
    
    /**
     * 根据标签类型刷新文件列表
     */
    fun refreshFiles(tagType: FileTagType, n: Int = 60, offset: Int = 0) {
        when (tagType) {
            FileTagType.Icon -> isIconRefreshing = true
            FileTagType.Emoji -> isEmojiRefreshing = true
            FileTagType.Sticker -> isStickerRefreshing = true
            FileTagType.Gallery -> isGalleryRefreshing = true
        }
        
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching { fileApi.getFiles(tagType, n = n, offset = offset) }
                .onGalleryFailure()
                .onSuccess { files ->
                    when (tagType) {
                        FileTagType.Icon -> _iconFiles.value = files
                        FileTagType.Emoji -> _emojiFiles.value = files
                        FileTagType.Sticker -> _stickerFiles.value = files
                        FileTagType.Gallery -> _galleryFiles.value = files
                    }
                }
                .also {
                    when (tagType) {
                        FileTagType.Icon -> isIconRefreshing = false
                        FileTagType.Emoji -> isEmojiRefreshing = false
                        FileTagType.Sticker -> isStickerRefreshing = false
                        FileTagType.Gallery -> isGalleryRefreshing = false
                    }
                }
        }
    }
    
    /**
     * 根据标签类型获取对应的文件列表
     */
    fun getFilesByTag(tagType: FileTagType): List<FileData> {
        return when (tagType) {
            FileTagType.Icon -> iconFiles
            FileTagType.Emoji -> emojiFiles
            FileTagType.Sticker -> stickerFiles
            FileTagType.Gallery -> galleryFiles
        }
    }
    
    /**
     * 根据标签类型获取对应的刷新状态
     */
    fun isRefreshingByTag(tagType: FileTagType): Boolean {
        return when (tagType) {
            FileTagType.Icon -> isIconRefreshing
            FileTagType.Emoji -> isEmojiRefreshing
            FileTagType.Sticker -> isStickerRefreshing
            FileTagType.Gallery -> isGalleryRefreshing
        }
    }
    
    private inline fun <T> Result<T>.onGalleryFailure() =
        onApiFailure("Gallery") {
            logger.error(it)
            screenModelScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
        }
} 