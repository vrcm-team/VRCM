package io.github.vrcmteam.vrcm.presentation.screens.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import org.koin.compose.koinInject

/**
 * 图片编辑屏幕
 * 允许用户裁剪图片以符合特定文件类型的要求
 */
class ImageEditorScreen(
    private val imagePath: String,
    private val fileTagType: FileTagType,
    private val onImageCropped: (String) -> Unit
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        // 获取当前文件类型的编辑器配置
        val config = ImageEditorConfig.forFileTagType(fileTagType)

        // 图片尺寸状态
        var imageSize by remember { mutableStateOf(IntSize(0, 0)) }
        // 容器尺寸状态
        var containerSize by remember { mutableStateOf(IntSize(0, 0)) }
        // 裁剪区域状态
        var cropRect by remember { mutableStateOf<Rect?>(null) }
        // 拖动起始点
        var dragStart by remember { mutableStateOf(Offset.Zero) }
        // 是否正在拖动
        var isDragging by remember { mutableStateOf(false) }

        // 目标宽高比从配置中获取
        val targetAspectRatio = config.aspectRatio

        // 当容器尺寸变化时，初始化裁剪区域
        LaunchedEffect(containerSize, imageSize) {
            if (containerSize.width > 0 && containerSize.height > 0 && 
                imageSize.width > 0 && imageSize.height > 0) {

                // 计算图片在容器中的实际显示尺寸
                val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
                val displayWidth: Float
                val displayHeight: Float

                if (imageAspectRatio > containerSize.width.toFloat() / containerSize.height.toFloat()) {
                    // 图片较宽，以容器宽度为基准
                    displayWidth = containerSize.width.toFloat()
                    displayHeight = displayWidth / imageAspectRatio
                } else {
                    // 图片较高，以容器高度为基准
                    displayHeight = containerSize.height.toFloat()
                    displayWidth = displayHeight * imageAspectRatio
                }

                // 计算初始裁剪区域，使其符合16:9比例
                val cropWidth: Float
                val cropHeight: Float

                if (imageAspectRatio > targetAspectRatio) {
                    // 图片比例比16:9更宽，以高度为基准
                    cropHeight = displayHeight * 0.8f
                    cropWidth = cropHeight * targetAspectRatio
                } else {
                    // 图片比例比16:9更窄，以宽度为基准
                    cropWidth = displayWidth * 0.8f
                    cropHeight = cropWidth / targetAspectRatio
                }

                // 居中放置裁剪区域
                val left = (containerSize.width - cropWidth) / 2
                val top = (containerSize.height - cropHeight) / 2

                cropRect = Rect(left, top, left + cropWidth, top + cropHeight)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("裁剪图片") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        // 确认按钮
                        IconButton(onClick = {
                            // 处理裁剪逻辑
                            // 在实际应用中，这里需要实现图片裁剪功能
                            // 由于我们没有实际的图片处理库，这里只是模拟裁剪过程
                            // 实际应用中应该使用平台特定的图片处理API
                            onImageCropped(imagePath)
                            navigator.pop()
                        }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.Check),
                                contentDescription = "确认"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .onSizeChanged { containerSize = it }
            ) {
                // 显示原始图片
                CoilImage(
                    imageModel = { imagePath },
                    imageOptions = ImageOptions(
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        contentDescription = "待裁剪图片"
                    ),
                    imageLoader = { koinInject() },
                    modifier = Modifier.fillMaxSize(),
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

                // 使用LaunchedEffect来获取图片尺寸
                LaunchedEffect(imagePath) {
                    // 在实际应用中，这里应该使用平台特定的API来获取图片尺寸
                    // 由于我们没有实际的图片处理库，这里假设图片尺寸为1920x1080
                    imageSize = IntSize(1920, 1080)
                }

                // 添加拖动手势处理
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    isDragging = cropRect?.contains(offset) ?: false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (isDragging && cropRect != null) {
                                        // 移动裁剪框
                                        val currentRect = cropRect!!
                                        var newLeft = currentRect.left + dragAmount.x
                                        var newTop = currentRect.top + dragAmount.y

                                        // 确保裁剪框不会移出图片区域
                                        val imageRect = calculateImageRect(containerSize, imageSize)
                                        newLeft = newLeft.coerceIn(
                                            imageRect.left,
                                            imageRect.right - currentRect.width
                                        )
                                        newTop = newTop.coerceIn(
                                            imageRect.top,
                                            imageRect.bottom - currentRect.height
                                        )

                                        cropRect = Rect(
                                            newLeft,
                                            newTop,
                                            newLeft + currentRect.width,
                                            newTop + currentRect.height
                                        )
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                }
                            )
                        }
                )

                // 绘制裁剪区域
                cropRect?.let { rect ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // 绘制半透明遮罩
                        val path = Path().apply {
                            // 外部矩形（整个画布）
                            addRect(Rect(0f, 0f, size.width, size.height))
                            // 内部矩形（裁剪区域）- 使用非零环绕规则挖空
                            addRect(rect)
                        }
                        drawPath(
                            path = path,
                            color = Color.Black.copy(alpha = 0.5f)
                        )

                        // 绘制裁剪框边界
                        drawRect(
                            color = Color.White,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 绘制网格线
                        val thirdWidth = rect.width / 3
                        val thirdHeight = rect.height / 3

                        // 垂直线
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(rect.left + thirdWidth, rect.top),
                            end = Offset(rect.left + thirdWidth, rect.bottom),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(rect.left + 2 * thirdWidth, rect.top),
                            end = Offset(rect.left + 2 * thirdWidth, rect.bottom),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 水平线
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(rect.left, rect.top + thirdHeight),
                            end = Offset(rect.right, rect.top + thirdHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(rect.left, rect.top + 2 * thirdHeight),
                            end = Offset(rect.right, rect.top + 2 * thirdHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // 底部提示文本
                Text(
                    text = "拖动裁剪框调整位置，${config.description}",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    /**
     * 计算图片在容器中的实际显示区域
     */
    private fun calculateImageRect(containerSize: IntSize, imageSize: IntSize): Rect {
        if (containerSize.width <= 0 || containerSize.height <= 0 || 
            imageSize.width <= 0 || imageSize.height <= 0) {
            return Rect(0f, 0f, 0f, 0f)
        }

        val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
        val containerAspectRatio = containerSize.width.toFloat() / containerSize.height.toFloat()

        val displayWidth: Float
        val displayHeight: Float

        if (imageAspectRatio > containerAspectRatio) {
            // 图片较宽，以容器宽度为基准
            displayWidth = containerSize.width.toFloat()
            displayHeight = displayWidth / imageAspectRatio
            val top = (containerSize.height - displayHeight) / 2
            return Rect(0f, top, displayWidth, top + displayHeight)
        } else {
            // 图片较高，以容器高度为基准
            displayHeight = containerSize.height.toFloat()
            displayWidth = displayHeight * imageAspectRatio
            val left = (containerSize.width - displayWidth) / 2
            return Rect(left, 0f, left + displayWidth, displayHeight)
        }
    }
}
