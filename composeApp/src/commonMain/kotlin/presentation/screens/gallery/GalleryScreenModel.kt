package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

class GalleryScreenModel internal constructor(
    private val dataSource: GalleryDataSource,
    private val logger: Logger,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ScreenModel {

    companion object {
        /** Gallery/Icon/Print 的上限 */
        const val MAX_FIXED_FILES = 64
        /** Emoji 默认上限 */
        const val MAX_EMOJI_DEFAULT = 32
        /** Sticker 默认上限 */
        const val MAX_STICKER_DEFAULT = 32
    }

    // 使用Map存储不同标签类型的图片文件列表
    private val _filesByTag = mutableStateMapOf<FileTagType, List<FileData>>().apply {
        // 不包含 Print，Print 使用独立的 API
        FileTagType.entries.filter { it != FileTagType.Print }.forEach { tagType ->
            this[tagType] = emptyList()
        }
    }

    // 使用Map存储不同标签类型的刷新状态
    private val _isRefreshingByTag = mutableStateMapOf<FileTagType, Boolean>().apply {
        FileTagType.entries.forEach { tagType ->
            this[tagType] = false
        }
    }

    // 拍立得使用独立的 API (GET /prints/user/{userId})
    private val _prints = mutableStateOf<List<PrintData>>(emptyList())
    private val _isRefreshingPrints = mutableStateOf(false)

    // 是否为 VRC+ 用户
    private val _isVrcPlus = mutableStateOf(false)
    val isVrcPlus: Boolean get() = _isVrcPlus.value

    private data class Selection(
        val tagType: FileTagType,
        val ids: Set<String>,
    )

    private val _selection = mutableStateOf<Selection?>(null)

    fun selectedIds(tagType: FileTagType): Set<String> =
        _selection.value?.takeIf { it.tagType == tagType }?.ids.orEmpty()

    fun hasSelection(tagType: FileTagType): Boolean = selectedIds(tagType).isNotEmpty()

    fun toggleSelection(tagType: FileTagType, id: String) {
        val current = _selection.value
        if (current?.tagType != tagType) {
            _selection.value = Selection(tagType, setOf(id))
            return
        }
        val updatedIds = if (id in current.ids) current.ids - id else current.ids + id
        _selection.value = updatedIds.takeIf { it.isNotEmpty() }?.let { Selection(tagType, it) }
    }

    fun clearSelection() {
        _selection.value = null
    }

    private fun clearSelection(tagType: FileTagType) {
        if (_selection.value?.tagType == tagType) clearSelection()
    }

    fun isSelected(tagType: FileTagType, id: String): Boolean = id in selectedIds(tagType)

    private fun retainLoadedSelection(tagType: FileTagType, loadedIds: Set<String>) {
        val current = _selection.value?.takeIf { it.tagType == tagType } ?: return
        val retainedIds = current.ids intersect loadedIds
        _selection.value = retainedIds.takeIf { it.isNotEmpty() }?.let { Selection(tagType, it) }
    }

    fun init() {
        clearSelection()
        refreshAllFiles()
        screenModelScope.launch(workerDispatcher) {
            runGalleryCatching {
                _isVrcPlus.value = dataSource.isCurrentUserSupporter()
            }.onFailure {
                logger.error("Failed to fetch current user: $it")
            }
        }
    }

    private fun refreshAllFiles() {
        refreshFiles(FileTagType.Icon)
        refreshFiles(FileTagType.Emoji)
        refreshFiles(FileTagType.Sticker)
        refreshFiles(FileTagType.Gallery)
        refreshPrints()
    }

    /**
     * 根据标签类型刷新文件列表
     */
    fun refreshFiles(tagType: FileTagType, n: Int = 100, offset: Int = 0) {
        // 设置刷新状态为true
        _isRefreshingByTag[tagType] = true

        screenModelScope.launch(workerDispatcher) {
            runGalleryCatching { dataSource.getFiles(tagType, n, offset) }
                .onGalleryFailure()
                .onSuccess { files ->
                    // 更新文件列表
                    val sortedFiles = files.sortedByDescending { file ->
                        file.versions.maxByOrNull { it.version }?.createdAt
                    }
                    _filesByTag[tagType] = sortedFiles
                    retainLoadedSelection(tagType, sortedFiles.mapTo(mutableSetOf(), FileData::id))
                }
                .also {
                    // 设置刷新状态为false
                    _isRefreshingByTag[tagType] = false
                }
        }
    }

    /**
     * 获取拍立得列表（使用独立 API: GET /prints/user/{userId}）
     */
    fun refreshPrints(n: Int = 100, offset: Int = 0) {
        _isRefreshingPrints.value = true
        screenModelScope.launch(workerDispatcher) {
            runGalleryCatching { dataSource.getPrints(n, offset) }
                .onGalleryFailure()
                .onSuccess { prints ->
                    val sortedPrints = prints.sortedByDescending { it.createdAt ?: it.timestamp }
                    _prints.value = sortedPrints
                    retainLoadedSelection(FileTagType.Print, sortedPrints.mapTo(mutableSetOf(), PrintData::id))
                }
                .also {
                    _isRefreshingPrints.value = false
                }
        }
    }

    val prints: List<PrintData> get() = _prints.value

    val isRefreshingPrints: Boolean get() = _isRefreshingPrints.value

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

    /**
     * 获取指定标签类型的当前文件数量
     */
    fun getFileCount(tagType: FileTagType): Int = when (tagType) {
        FileTagType.Print -> prints.size
        else -> getFilesByTag(tagType).size
    }

    /**
     * 获取指定标签类型的数量上限（参照 VRCX）
     */
    fun getMaxCount(tagType: FileTagType): Int = when (tagType) {
        FileTagType.Gallery, FileTagType.Icon, FileTagType.Print -> MAX_FIXED_FILES
        FileTagType.Emoji -> MAX_EMOJI_DEFAULT
        FileTagType.Sticker -> MAX_STICKER_DEFAULT
    }

    private inline fun <T> Result<T>.onGalleryFailure() =
        onApiFailure("Gallery") {
            logger.error(it)
            screenModelScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
        }

    /**
     * 通过字节数组上传图片到指定标签类型
     * @param fileBytes 文件字节数组
     * @param fileName 文件名
     * @param tagType 文件标签类型
     * @param uploadingMessage 上传中提示
     * @param successMessage 上传成功提示
     * @param failedMessagePrefix 上传失败提示前缀
     */
    fun uploadImageBytes(
        fileBytes: ByteArray,
        fileName: String,
        tagType: FileTagType,
        uploadingMessage: String,
        successMessage: String,
        failedMessagePrefix: String,
    ) {
        screenModelScope.launch(workerDispatcher) {
            SharedFlowCentre.toastText.emit(ToastText.Info(uploadingMessage))
            val format = GalleryUploadImageFormat.fromFileName(fileName)
            if (format == null) {
                val message = "$failedMessagePrefix: Unsupported image format"
                SharedFlowCentre.toastText.emit(ToastText.Error(message))
                logger.error(message)
                return@launch
            }
            val result = dataSource.uploadImage(fileBytes, fileName, format.mimeType, tagType)
            result.onSuccess {
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                refreshFiles(tagType)
            }.onFailure {
                SharedFlowCentre.toastText.emit(
                    ToastText.Error("$failedMessagePrefix: ${it.message}")
                )
                logger.error("Upload failed: ${it.message}")
            }
        }
    }

    /**
     * 删除选中的文件（非 Print 类型）
     * @param tagType 文件标签类型
     * @param deletingMessage 删除中提示
     * @param successMessage 删除成功提示
     * @param failedMessagePrefix 删除失败提示前缀
     */
    fun deleteSelectedFiles(
        tagType: FileTagType,
        deletingMessage: String,
        successMessage: String,
        failedMessagePrefix: String,
    ) {
        val loadedIds = getFilesByTag(tagType).mapTo(mutableSetOf(), FileData::id)
        val ids = selectedIds(tagType).intersect(loadedIds).toList()
        clearSelection(tagType)
        if (ids.isEmpty()) return
        screenModelScope.launch(workerDispatcher) {
            SharedFlowCentre.toastText.emit(ToastText.Info(deletingMessage))
            var failed = 0
            for (id in ids) {
                runGalleryCatching { dataSource.deleteFile(id) }
                    .onFailure { failed++; logger.error("Delete file $id failed: $it") }
            }
            if (failed == 0) {
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            } else {
                SharedFlowCentre.toastText.emit(
                    ToastText.Error("$failedMessagePrefix ($failed/${ids.size})")
                )
            }
            refreshFiles(tagType)
        }
    }

    /**
     * 删除选中的拍立得
     * @param deletingMessage 删除中提示
     * @param successMessage 删除成功提示
     * @param failedMessagePrefix 删除失败提示前缀
     */
    fun deleteSelectedPrints(
        deletingMessage: String,
        successMessage: String,
        failedMessagePrefix: String,
    ) {
        val loadedIds = prints.mapTo(mutableSetOf(), PrintData::id)
        val ids = selectedIds(FileTagType.Print).intersect(loadedIds).toList()
        clearSelection(FileTagType.Print)
        if (ids.isEmpty()) return
        screenModelScope.launch(workerDispatcher) {
            SharedFlowCentre.toastText.emit(ToastText.Info(deletingMessage))
            var failed = 0
            for (id in ids) {
                runGalleryCatching { dataSource.deletePrint(id) }
                    .onFailure { failed++; logger.error("Delete print $id failed: $it") }
            }
            if (failed == 0) {
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            } else {
                SharedFlowCentre.toastText.emit(
                    ToastText.Error("$failedMessagePrefix ($failed/${ids.size})")
                )
            }
            refreshPrints()
        }
    }

}
