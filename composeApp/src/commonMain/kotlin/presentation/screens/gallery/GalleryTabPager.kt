package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.compoments.EmptyContent
import io.github.vrcmteam.vrcm.presentation.compoments.LocationDialogContent
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import org.koin.compose.koinInject

sealed class GalleryTabPager(private val tagType: FileTagType) : Pager {

    override val index: Int
        get() = tagType.ordinal

    override val title: String
        @Composable
        get() = tagType.toString().replaceFirstChar { it.uppercase() }

    override val icon: Painter?
        @Composable
        get() = null

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val galleryScreenModel: GalleryScreenModel = koinScreenModel()

        val navigator = LocalNavigator.currentOrThrow


        RefreshBox(
            isRefreshing = galleryScreenModel.isRefreshingByTag(tagType),
            doRefresh = { galleryScreenModel.refreshFiles(tagType) }
        ) {
            val files = galleryScreenModel.getFilesByTag(tagType)

            if (files.isEmpty() && !galleryScreenModel.isRefreshingByTag(tagType)) {
                EmptyContent(
                    message = strings.galleryTabNoFiles.replace("%s", title),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 如果是Gallery标签页，添加上传按钮
//                    if (tagType == FileTagType.Gallery) {
//                        // 上传按钮
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(horizontal = 16.dp, vertical = 8.dp),
//                            horizontalArrangement = Arrangement.End
//                        ) {
//                            val coroutineScope = rememberCoroutineScope()
//                            val platform = getAppPlatform()
//
//                            Button(
//                                onClick = {
//                                    coroutineScope.launch {
//                                        // 从系统相册选择图片
//                                        val imagePath = platform.selectImageFromGallery()
//                                        if (imagePath != null) {
//                                            // 打开图片编辑器进行裁剪
//                                            navigator.push(
//                                                ImageEditorScreen(
//                                                    imagePath = imagePath,
//                                                    fileTagType = tagType,
//                                                ) { croppedImagePath ->
//                                                    // 裁剪完成后上传图片
//                                                    coroutineScope.launch {
//                                                        SharedFlowCentre.toastText.emit(ToastText.Info("正在上传图片..."))
//                                                        galleryScreenModel.uploadImage(croppedImagePath, tagType)
//                                                    }
//                                                }
//                                            )
//                                        }
//                                    }
//                                },
//                                modifier = Modifier.padding(4.dp)
//                            ) {
//                                Icon(
//                                    painter = rememberVectorPainter(AppIcons.SaveAlt),
//                                    contentDescription = strings.galleryTabUploadImage,
//                                    modifier = Modifier.size(20.dp)
//                                )
//                                Spacer(modifier = Modifier.width(4.dp))
//                                Text(strings.galleryTabUploadImage)
//                            }
//                        }
//                    }

                    GalleryGrid(files, tagType)
                }
            }
        }
    }

    @Composable
    private fun GalleryGrid(files: List<FileData>, tagType: FileTagType) {
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
                GalleryItem(file, tagType, aspectRatio, shape)
            }
        }
    }

    @Composable
    private fun GalleryItem(
        file: FileData,
        tagType: FileTagType,
        aspectRatio: Float = 1f,
        shape: Shape = MaterialTheme.shapes.medium
    ) {
        val (_, setDialogContent) = LocationDialogContent.current



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
                    .clickable {
                        // 点击图片时，打开全屏预览
                        setDialogContent(ImagePreviewDialog(file.id, file.name, file.extension))
                    },
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

    }

    companion object {
        data object Icon : GalleryTabPager(FileTagType.Icon)
        data object Emoji : GalleryTabPager(FileTagType.Emoji)
        data object Sticker : GalleryTabPager(FileTagType.Sticker)
        data object Gallery : GalleryTabPager(FileTagType.Gallery)

        data object Print : GalleryTabPager(FileTagType.Print)
    }
}
