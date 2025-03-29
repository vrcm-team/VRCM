package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.compose.koinInject

/**
 * 闪烁效果修饰符 - 使用drawWithCache确保动画速度与组件大小一致
 */
@Composable
fun Modifier.shimmerEffect(
    isLoading: Boolean = true,
    shimmerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
    shape: Shape = RectangleShape
): Modifier = composed {
    if (isLoading) {
        val shimmerAnimatable = rememberInfiniteTransition(label = "shimmer")
        val progress by shimmerAnimatable.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_animation"
        )
        
        drawWithCache {
            val gradientWidth = this.size.width * 0.4f // 增加渐变宽度为控件宽度的40%
            val startX = -gradientWidth + (this.size.width + 2 * gradientWidth) * progress
            
            // 创建渐变brush
            val brush = Brush.linearGradient(
                colors = listOf(
                    backgroundColor,
                    shimmerColor,
                    backgroundColor
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + gradientWidth, this.size.height)
            )

            onDrawBehind {
                drawRect(brush)
            }
        }
    } else {
        this
    }
}

@Composable
fun AImage(
    modifier: Modifier = Modifier,
    imageData: Any?,
    color: Color = MaterialTheme.colorScheme.surface,
    contentDescription: String? = null,
    error: Painter? = remember(color) { ColorPainter(color) },
    placeholder: Painter? = error,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val imageLoader: ImageLoader = koinInject()
    val platformContext = koinInject<PlatformContext>()
    val isLoading = remember(imageData) { mutableStateOf(true) }
    val imageRequest: Any? =
        when (imageData) {
            is String -> remember(imageData) {
                ImageRequest.Builder(platformContext)
                    .data(imageData)
                    .crossfade(600)
                    .build()
            }
            is ImageRequest -> imageData
            else -> imageData
        }

    // 选择合适的闪烁动画颜色
    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    val shimmer =  MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    
    AsyncImage(
        modifier = modifier.shimmerEffect(
            isLoading = isLoading.value,
            backgroundColor = background,
            shimmerColor = shimmer
        ),
        model = imageRequest,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        placeholder = if (isLoading.value || imageRequest == null) null else placeholder,
        error = if (isLoading.value || imageRequest == null) null else error,
        contentScale = contentScale,
        onLoading = { isLoading.value = true },
        onSuccess = { isLoading.value = false },
        onError = { isLoading.value = false }
    )
}

