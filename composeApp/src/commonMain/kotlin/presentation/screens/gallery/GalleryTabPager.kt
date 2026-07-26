package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.network.api.prints.data.PrintData
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageFailure
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageLimits
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.handoffPreparedImageToEditor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.localizedMessage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.readBoundedBytes
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

sealed class GalleryTabPager(private val tagType: FileTagType) {

    /** 供 GalleryScreen 读取以展示计数/上限 */
    val fileTagType: FileTagType get() = tagType

    val title: String
        @Composable
        get() = tagType.toString().replaceFirstChar { it.uppercase() }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(galleryScreenModel: GalleryScreenModel) {
        val navigator = LocalNavigator.currentOrThrow
        val printImageProcessor: PrintImageProcessor = koinInject()
        val editorSessionStore: PrintImageEditorSessionStore = koinInject()
        val locale = strings

        val isPrint = tagType == FileTagType.Print
        val isVrcPlus = galleryScreenModel.isVrcPlus
        val coroutineScope = rememberCoroutineScope()
        var isPreparing by remember { mutableStateOf(false) }

        LaunchedEffect(isPrint, editorSessionStore) {
            if (isPrint) {
                editorSessionStore.uploadCompletions.collect {
                    galleryScreenModel.refreshPrints()
                }
            }
        }

        // 非 Print 类型的图片选择器（直接上传）
        val simpleImagePicker = rememberFilePickerLauncher(
            type = FileKitType.File(*GalleryUploadImageFormat.allowedExtensions.toTypedArray()),
        ) { image ->
            if (image != null && !isPreparing) {
                coroutineScope.launch {
                    isPreparing = true
                    try {
                        val bytes = runGalleryCatching {
                            image.readBoundedBytes(PrintImageLimits.MAX_FILE_BYTES)
                        }.getOrElse {
                            val message = if (it is PrintImageFailure.FileTooLarge) {
                                locale.printEditorFileTooLarge
                            } else {
                                locale.printEditorReadFailed.replace(
                                    "%s",
                                    it.message.orEmpty().ifBlank { locale.unknown },
                                )
                            }
                            SharedFlowCentre.toastText.emit(ToastText.Error(message))
                            return@launch
                        }
                        galleryScreenModel.uploadImageBytes(
                            bytes, image.name, tagType,
                            uploadingMessage = locale.galleryTabUploading,
                            successMessage = locale.galleryTabUploadSuccess,
                            failedMessagePrefix = locale.galleryTabUploadFailed,
                        )
                    } finally {
                        isPreparing = false
                    }
                }
            }
        }

        // Print 类型的图片选择器（经过编辑器）
        val printImagePicker = rememberFilePickerLauncher(
            type = FileKitType.File("jpg", "jpeg", "png", "heic", "heif"),
        ) { image ->
            if (image != null && !isPreparing) {
                coroutineScope.launch {
                    isPreparing = true
                    try {
                        val source = readSelectedImage(image.name) {
                            image.readBoundedBytes(PrintImageLimits.MAX_FILE_BYTES)
                        }.getOrElse {
                            val message = if (it is PrintImageFailure.FileTooLarge) {
                                locale.printEditorFileTooLarge
                            } else {
                                locale.printEditorReadFailed.replace(
                                    "%s",
                                    it.message.orEmpty().ifBlank { locale.unknown },
                                )
                            }
                            SharedFlowCentre.toastText.emit(
                                ToastText.Error(message)
                            )
                            return@launch
                        }
                        val prepared = printImageProcessor.prepare(source).getOrElse { failure ->
                            if (failure is CancellationException) throw failure
                            val message = (failure as? PrintImageFailure)
                                ?.localizedMessage(locale)
                                ?: locale.printEditorDecodeFailed
                            SharedFlowCentre.toastText.emit(ToastText.Error(message))
                            return@launch
                        }
                        handoffPreparedImageToEditor(
                            source = source,
                            prepared = prepared,
                            sessionStore = editorSessionStore,
                            push = { sessionId ->
                                navigator.push(PrintImageEditorScreen(sessionId))
                            },
                        )
                    } finally {
                        isPreparing = false
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            RefreshBox(
                isRefreshing = if (isPrint) galleryScreenModel.isRefreshingPrints else galleryScreenModel.isRefreshingByTag(tagType),
                doRefresh = { if (isPrint) galleryScreenModel.refreshPrints() else galleryScreenModel.refreshFiles(tagType) }
            ) {
                if (isPrint) {
                    PrintContent(galleryScreenModel)
                } else {
                    FileContent(galleryScreenModel)
                }
            }

            // 选中时为红色删除按钮，否则为上传按钮
            val hasSelection = galleryScreenModel.hasSelection(tagType)
            FloatingActionButton(
                onClick = {
                    if (hasSelection) {
                        // 删除选中项
                        if (isPrint) {
                            galleryScreenModel.deleteSelectedPrints(
                                deletingMessage = locale.galleryTabDeleting,
                                successMessage = locale.galleryTabDeleteSuccess,
                                failedMessagePrefix = locale.galleryTabDeleteFailed,
                            )
                        } else {
                            galleryScreenModel.deleteSelectedFiles(
                                tagType = tagType,
                                deletingMessage = locale.galleryTabDeleting,
                                successMessage = locale.galleryTabDeleteSuccess,
                                failedMessagePrefix = locale.galleryTabDeleteFailed,
                            )
                        }
                    } else if (!isPreparing && isVrcPlus) {
                        if (isPrint) printImagePicker.launch() else simpleImagePicker.launch()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = when {
                    hasSelection -> MaterialTheme.colorScheme.error
                    isVrcPlus -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = when {
                    hasSelection -> MaterialTheme.colorScheme.onError
                    isVrcPlus -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (hasSelection) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = locale.galleryTabDelete,
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Publish,
                        contentDescription = if (isVrcPlus) {
                            locale.galleryTabUploadImage
                        } else {
                            locale.galleryTabVrcPlusRequired
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun PrintContent(galleryScreenModel: GalleryScreenModel) {
        val prints = galleryScreenModel.prints
        if (prints.isEmpty() && !galleryScreenModel.isRefreshingPrints) {
            EmptyContent(message = strings.galleryTabNoFiles.replace("%s", title))
        } else {
            PrintGrid(prints, galleryScreenModel)
        }
    }

    @Composable
    private fun FileContent(galleryScreenModel: GalleryScreenModel) {
        val files = galleryScreenModel.getFilesByTag(tagType)
        if (files.isEmpty() && !galleryScreenModel.isRefreshingByTag(tagType)) {
            EmptyContent(message = strings.galleryTabNoFiles.replace("%s", title))
        } else {
            GalleryGrid(files, tagType, galleryScreenModel)
        }
    }

    /**
     * 拍立得网格展示
     */
    @Composable
    private fun PrintGrid(
        prints: List<PrintData>,
        galleryScreenModel: GalleryScreenModel,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = prints,
                key = { it.id },
            ) { print ->
                PrintItem(print, galleryScreenModel)
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
    @Composable
    private fun PrintItem(
        print: PrintData,
        galleryScreenModel: GalleryScreenModel,
    ) {
        val imageUrl = print.files?.image ?: ""
        val (dialogContent, setDialogContent) = LocationDialogContent.current
        val selected = galleryScreenModel.isSelected(FileTagType.Print, print.id)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(16f / 9f),
        ) {
            AnimatedVisibility(dialogContent == null || (dialogContent as? ImagePreviewDialog)?.fileId != print.id) {
                CoilImage(
                    imageModel = { imageUrl },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    imageLoader = { koinInject() },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .sharedBoundsBy(
                            print.id,
                            sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                            animatedVisibilityScope = this@AnimatedVisibility,
                        )
                        .combinedClickable(
                            onClick = {
                                if (galleryScreenModel.hasSelection(FileTagType.Print)) {
                                    galleryScreenModel.toggleSelection(FileTagType.Print, print.id)
                                } else {
                                    setDialogContent(ImagePreviewDialog(print.id, print.id, ".png", imageUrl))
                                }
                            },
                            onLongClick = {
                                galleryScreenModel.toggleSelection(FileTagType.Print, print.id)
                            },
                        ),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    failure = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = strings.galleryTabLoadFailed,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
            // 选中状态覆盖层
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable {
                            galleryScreenModel.toggleSelection(FileTagType.Print, print.id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun GalleryGrid(
        files: List<FileData>,
        tagType: FileTagType,
        galleryScreenModel: GalleryScreenModel,
    ) {
        // 根据文件类型设置不同的列数
        val count = when (tagType) {
            FileTagType.Gallery, FileTagType.Print -> 2
            FileTagType.Emoji, FileTagType.Sticker -> 3
            FileTagType.Icon -> 4
        }
        // 根据文件类型设置不同的宽高比
        val aspectRatio = when (tagType) {
            FileTagType.Icon -> 1.0f  // 圆形展示，使用1:1比例
            FileTagType.Gallery, FileTagType.Print -> 16f / 9f  // 16:9比例
            FileTagType.Emoji, FileTagType.Sticker -> 1.0f  // 正方形展示，使用1:1比例
        }
        // 根据文件类型设置不同的形状
        val shape = when (tagType) {
            FileTagType.Icon -> CircleShape  // 圆形展示
            else -> MaterialTheme.shapes.medium  // 其他类型使用默认的medium形状
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(count),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = files,
                key = { it.id },
            ) { file ->
                GalleryItem(file, tagType, galleryScreenModel, aspectRatio, shape)
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
    @Composable
    private fun GalleryItem(
        file: FileData,
        tagType: FileTagType,
        galleryScreenModel: GalleryScreenModel,
        aspectRatio: Float = 1f,
        shape: Shape = MaterialTheme.shapes.medium
    ) {
        val (dialogContent, setDialogContent) = LocationDialogContent.current
        val selected = galleryScreenModel.isSelected(tagType, file.id)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(aspectRatio),
        ) {
            // 获取最新版本的图片URL
            val latestVersion = file.versions.maxByOrNull { it.version }
            val imageUrl = if (latestVersion != null) {
                FileApi.convertFileUrl(file.id, 256)
            } else {
                ""
            }
            AnimatedVisibility(dialogContent == null || (dialogContent as ImagePreviewDialog).fileId != file.id) {
                CoilImage(
                    imageModel = { imageUrl },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    ),
                    imageLoader = { koinInject() },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .sharedBoundsBy(
                            file.id,
                            sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                            animatedVisibilityScope = this@AnimatedVisibility
                        )
                        .combinedClickable(
                            onClick = {
                                if (galleryScreenModel.hasSelection(tagType)) {
                                    galleryScreenModel.toggleSelection(tagType, file.id)
                                } else {
                                    setDialogContent(ImagePreviewDialog(file.id, file.name, file.extension))
                                }
                            },
                            onLongClick = { galleryScreenModel.toggleSelection(tagType, file.id) },
                        ),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    failure = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = strings.galleryTabLoadFailed,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
            // 选中状态覆盖层
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clip(shape)
                        .clickable { galleryScreenModel.toggleSelection(tagType, file.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }

    companion object {
        data object Icon : GalleryTabPager(FileTagType.Icon)
        data object Emoji : GalleryTabPager(FileTagType.Emoji)
        data object Sticker : GalleryTabPager(FileTagType.Sticker)
        data object Gallery : GalleryTabPager(FileTagType.Gallery)

        data object Print : GalleryTabPager(FileTagType.Print)
    }
}
