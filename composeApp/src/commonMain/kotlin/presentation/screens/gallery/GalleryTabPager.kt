package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.min

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



        RefreshBox(
            isRefreshing = galleryScreenModel.isRefreshingByTag(tagType),
            doRefresh = { galleryScreenModel.refreshFiles(tagType) }
        ) {
            val files = galleryScreenModel.getFilesByTag(tagType)

            if (files.isEmpty() && !galleryScreenModel.isRefreshingByTag(tagType)) {
                EmptyContent(
                    message = "暂无${title}文件",
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 如果是Gallery标签页，添加上传按钮
                    if (tagType == FileTagType.Gallery) {
                        // 上传按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            val coroutineScope = rememberCoroutineScope()
                            val platform = getAppPlatform()

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        // 从系统相册选择图片
                                        val imagePath = platform.selectImageFromGallery()
                                        if (imagePath != null) {
                                            // 选择了图片，通知用户
                                            SharedFlowCentre.toastText.emit(ToastText.Info("正在上传图片..."))
                                            // 调用ScreenModel的上传方法
                                            galleryScreenModel.uploadImage(imagePath)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    painter = rememberVectorPainter(AppIcons.SaveAlt),
                                    contentDescription = "上传图片",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("上传图片")
                            }
                        }
                    }

                    GalleryGrid(files, tagType)
                }
            }
        }
    }

    @Composable
    private fun GalleryGrid(files: List<FileData>, tagType: FileTagType) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(8.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = files,
                key = { it.id },
            ) { file ->
                // 根据文件名的哈希值计算一个随机的宽高比，使布局更有交错感
                val aspectRatio = when {
                    file.name.hashCode() % 3 == 0 -> 0.8f
                    file.name.hashCode() % 3 == 1 -> 1.0f
                    else -> 1.2f
                }

                GalleryItem(file, tagType, aspectRatio)
            }
        }
    }

    @Composable
    private fun GalleryItem(file: FileData, tagType: FileTagType, aspectRatio: Float = 1f) {
        var dialogContent by LocationDialogContent.current

        // 在垂直布局中，不需要随机宽度，只需要填满整行
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        // 点击图片时，打开全屏预览
                        dialogContent = ImagePreviewDialog(file)
                    },
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
                        .clip(MaterialTheme.shapes.medium),
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
                                text = "加载失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        }
    }

    companion object {
        data object Icon : GalleryTabPager(FileTagType.Icon)
        data object Emoji : GalleryTabPager(FileTagType.Emoji)
        data object Sticker : GalleryTabPager(FileTagType.Sticker)
        data object Gallery : GalleryTabPager(FileTagType.Gallery)
    }
}

/**
 * 图片预览对话框
 */
class ImagePreviewDialog(private val file: FileData) : SharedDialog {

    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val coroutineScope = rememberCoroutineScope()
        val platform = getAppPlatform()
        var dialogContent by LocationDialogContent.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // 去除点击波纹效果
                ) {
                    // 点击背景关闭对话框
                    dialogContent = null
                }
        ) {
            // 显示原始大小的图片
            val imageUrl = FileApi.convertFileUrl(file.id, 2048)

            // 为了防止ZoomableImage拦截背景点击事件，单独放在一个Box中
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 阻止事件传递到背景，但不进行任何操作
                    }
            ) {
                ZoomableImage(
                    imageUrl = imageUrl,
                    contentDescription = file.name,
                    maxScale = 5f,
                    minScale = 0.5f
                )
            }

            // 添加FloatingActionButton用于保存图片，放在右下角
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val result = platform.saveImageToGallery(
                            imageUrl = imageUrl,
                            fileName = "${file.name}.jpg"
                        )

                        if (result) {
                            SharedFlowCentre.toastText.emit(ToastText.Success("图片已保存到相册"))
                        } else {
                            SharedFlowCentre.toastText.emit(ToastText.Error("保存图片失败"))
                        }
                    }
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    painter = rememberVectorPainter(AppIcons.SaveAlt),
                    contentDescription = "保存到相册"
                )
            }

            // 添加一个关闭按钮，放在左上角
            IconButton(
                onClick = {
                    // 关闭对话框
                    dialogContent = null
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    painter = rememberVectorPainter(AppIcons.Close),
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
        }
    }
}


/**
 * 支持手势缩放和平移的图片组件
 */
@Composable
fun ZoomableImage(
    imageUrl: String,
    contentDescription: String? = null,
    maxScale: Float = 3f,
    minScale: Float = 0.8f
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var doubleTapState by remember { mutableStateOf(0) } // 0: 正常, 1: 放大, 2: 缩小

    // 监视scale变化，当缩放回到1.0f时，重置offset到中心
    LaunchedEffect(scale) {
        if (scale <= 1.0f) {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 处理缩放
                    val prevScale = scale
                    scale = (scale * zoom).coerceIn(minScale, maxScale)

                    // 处理平移 (只有在放大状态下才能平移)
                    if (scale > 1f) {
                        // 计算新的偏移量
                        val newOffset = offset + pan

                        // 限制平移范围，防止图片移出屏幕太多
                        val maxX = (scale - 1) * size.width / 2
                        val maxY = (scale - 1) * size.height / 2

                        offset = Offset(
                            newOffset.x.coerceIn(-maxX, maxX),
                            newOffset.y.coerceIn(-maxY, maxY)
                        )
                    } else if (prevScale > 1f && scale <= 1f) {
                        // 如果从放大状态缩小到正常或更小，重置位置
                        offset = Offset.Zero
                    }
                }
            }
            // 支持双击放大/缩小
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        when (doubleTapState) {
                            0 -> {
                                // 正常 -> 放大
                                scale = min(maxScale, 2.5f)

                                // 可以选择让双击的位置作为放大的中心点
                                val centerX = size.width / 2
                                val centerY = size.height / 2
                                val targetOffsetX = (centerX - tapOffset.x) * (scale - 1)
                                val targetOffsetY = (centerY - tapOffset.y) * (scale - 1)

                                // 限制偏移量
                                val maxX = (scale - 1) * size.width / 2
                                val maxY = (scale - 1) * size.height / 2
                                offset = Offset(
                                    targetOffsetX.coerceIn(-maxX, maxX),
                                    targetOffsetY.coerceIn(-maxY, maxY)
                                )

                                doubleTapState = 1
                            }
                            1 -> {
                                // 放大 -> 更放大
                                scale = maxScale
                                doubleTapState = 2
                            }
                            else -> {
                                // 缩小回正常
                                scale = 1f
                                offset = Offset.Zero  // 确保位置回到中心
                                doubleTapState = 0
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        CoilImage(
            imageModel = { imageUrl },
            imageOptions = ImageOptions(
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                requestSize = IntSize(2048, 2048),
                contentDescription = contentDescription
            ),
            imageLoader = { koinInject() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            failure = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "图片加载失败",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}
