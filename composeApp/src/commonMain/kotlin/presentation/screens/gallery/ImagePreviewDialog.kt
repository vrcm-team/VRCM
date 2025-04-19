package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import io.github.vrcmteam.vrcm.core.extensions.saveImageToGallery
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.presentation.compoments.LocationDialogContent
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.min

/**
 * 图片预览对话框
 */
class ImagePreviewDialog(
    private val fileId: String,
    private val fileName: String,
    private val fileExtension: String,
) : SharedDialog {

    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val coroutineScope = rememberCoroutineScope()
        val platform = getAppPlatform()
        var dialogContent by LocationDialogContent.current

        Box(
            modifier = Modifier
                .fillMaxSize()

        ) {
            // 显示原始大小的图片
            val imageUrl = FileApi.convertFileUrl(fileId, 2048)

            // 为了防止ZoomableImage拦截背景点击事件，单独放在一个Box中
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .simpleClickable {
                        // 点击背景关闭对话框
                        dialogContent = null
                    }
            ) {
                ZoomableImage(
                    imageUrl = imageUrl,
                    contentDescription = fileName,
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
                            fileName = "${fileName}${fileExtension}"
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
fun BoxScope.ZoomableImage(
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
            .align(Alignment.Center),

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
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ).pointerInput(Unit) {
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
