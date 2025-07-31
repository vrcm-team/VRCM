package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.min

/**
 * 图片预览对话框
 */
class ImagePreviewDialog(
    val fileId: String,
    private val fileName: String,
    private val fileExtension: String,
) : SharedDialog {

    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val coroutineScope = rememberCoroutineScope()
        val platform = getAppPlatform()
        val (_, setDialogContent) = LocationDialogContent.current
        // 添加保存状态跟踪
        var isSaving by remember { mutableStateOf(false) }
        val authService = koinInject<AuthService>()
        val strings = strings
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
            ) {
                ZoomableImage(
                    id = fileId,
                    imageUrl = imageUrl,
                    contentDescription = fileName,
                    maxScale = 5f,
                    minScale = 0.5f,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onDismiss = {
                        // 移除对话框
                        setDialogContent(null)
                    },
                )
            }

            // 添加FloatingActionButton用于保存图片，放在右下角
            FloatingActionButton(
                onClick = {
                    if (isSaving) return@FloatingActionButton
                    // 设置保存状态为true
                    isSaving = true
                    coroutineScope.launch(Dispatchers.IO) {
                        authService.reTryAuthCatching {
                            platform.saveImageToGallery(
                                imageUrl = imageUrl,
                                fileName = "${fileName}${fileExtension}"
                            )
                        }.onFailure {
                            SharedFlowCentre.toastText.emit(
                                ToastText.Error(
                                    strings.imageSaveError.replace(
                                        "%s",
                                        it.message.orEmpty()
                                    )
                                )
                            )
                        }.onSuccess { isSuccess ->
                            if (isSuccess) {
                                SharedFlowCentre.toastText.emit(ToastText.Success(strings.imageSaveSuccess))
                            } else {
                                SharedFlowCentre.toastText.emit(ToastText.Error(strings.imageSaveFailed))
                            }
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier.padding(16.dp)
                    .align(Alignment.BottomEnd),
            ) {
                if (isSaving) {
                    // 保存中显示进度指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    // 正常状态显示保存图标
                    Icon(
                        painter = rememberVectorPainter(AppIcons.SaveAlt),
                        contentDescription = "Save Image"
                    )
                }
            }


            // 添加一个关闭按钮，放在左上角
            IconButton(
                onClick = {
                    // 关闭对话框
                    setDialogContent(null)
                },
                modifier = Modifier
                    .padding(top = getInsetPadding(WindowInsets::getTop))
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    painter = rememberVectorPainter(AppIcons.Close),
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}


/**
 * 支持手势缩放和平移的图片组件
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BoxScope.ZoomableImage(
    id: String,
    imageUrl: String,
    contentDescription: String? = null,
    maxScale: Float = 3f,
    minScale: Float = 0.8f,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDismiss: () -> Unit
) {
    var targetScale by remember { mutableStateOf(1f) }
    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    var doubleTapState by remember { mutableStateOf(0) }

    // 用于长按拖动的偏移量

    // 设置拖动阈值 (像素)
    val dismissThreshold = 800f

    // 使用 animateFloatAsState 为 scale 添加动画
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "scale_animation"
    )

    // 使用 animateOffsetAsState 为 offset 添加动画
    val animatedOffset by androidx.compose.animation.core.animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "offset_animation"
    )

    // 监视animatedScale变化，当缩放回到1.0f时，重置offset到中心
    LaunchedEffect(animatedScale) {
        if (animatedScale <= 1.0f) {
            targetOffset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier.align(Alignment.Center),
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
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = animatedOffset.x,
                    translationY = animatedOffset.y
                ).pointerInput(Unit) {
                    detectTransformGestures(true){ _, pan, zoom, _ ->
                        // 处理缩放 - 手势缩放时直接更新targetScale
                        val prevScale = targetScale
                        targetScale = (targetScale * zoom).coerceIn(minScale, maxScale)

                        // 处理平移 (只有在放大状态下才能平移)
                        if (animatedScale > 1f) {
                            // 计算新的偏移量
                            val newOffset = targetOffset + (pan * animatedScale)

                            // 限制平移范围，防止图片移出屏幕太多
                            val maxX = (animatedScale - 1) * size.width / 2
                            val maxY = (animatedScale - 1) * size.height / 2

                            targetOffset = Offset(
                                newOffset.x.coerceIn(-maxX, maxX),
                                newOffset.y.coerceIn(-maxY, maxY)
                            )
                        } else if (prevScale > 1f && targetScale <= 1f) {
                            // 如果从放大状态缩小到正常或更小，重置位置（带动画）
                            targetOffset = Offset.Zero
                        }
                    }
                }
                // 只有在正常大小时才启用长按拖动
                .enableIf(doubleTapState == 0) {
                    pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                // 拖动结束时检查是否达到阈值
                                val distance = kotlin.math.sqrt(
                                    targetOffset.x * targetOffset.x + targetOffset.y * targetOffset.y
                                )

                                if (distance >= dismissThreshold) {
                                    // 达到阈值，调用onDismiss
                                    onDismiss()
                                } else {
                                    // 未达到阈值，重置拖动偏移量（带动画）
                                    targetOffset = Offset.Zero
                                }
                            }
                        ) { change, dragAmount ->
                            // 更新拖动偏移量
                            targetOffset += dragAmount
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
                                    targetScale = min(maxScale, 2.5f)

                                    // 可以选择让双击的位置作为放大的中心点
                                    val centerX = size.width / 2
                                    val centerY = size.height / 2
                                    val targetOffsetX = (centerX - tapOffset.x) * (targetScale - 1)
                                    val targetOffsetY = (centerY - tapOffset.y) * (targetScale - 1)

                                    // 限制偏移量
                                    val maxX = (targetScale - 1) * size.width / 2
                                    val maxY = (targetScale - 1) * size.height / 2
                                    targetOffset = Offset(
                                        targetOffsetX.coerceIn(-maxX, maxX),
                                        targetOffsetY.coerceIn(-maxY, maxY)
                                    )

                                    doubleTapState = 1
                                }

                                1 -> {
                                    // 放大 -> 更放大
                                    targetScale = maxScale
                                    doubleTapState = 2
                                }

                                else -> {
                                    // 缩小回正常
                                    targetScale = 1f
                                    targetOffset = Offset.Zero  // 确保位置回到中心（带动画）
                                    doubleTapState = 0
                                }
                            }
                        }
                    )
                }
                .sharedBoundsBy(
                    id,
                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                    animatedVisibilityScope = animatedVisibilityScope
                ),
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            failure = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.imageLoadFailed,
                        color = MaterialTheme.colorScheme.errorContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        )
    }
}