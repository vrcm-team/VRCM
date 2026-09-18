package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 活动指示器：一圈带渐隐拖尾的细环匀速旋转——头部是实色圆头，沿旋转反方向淡出到透明。
 * 默认 28 dp、次级前景色；调用方用 `Modifier.size` 指定其他尺寸。系统「减少动态效果」时转得更慢但不停（否则看不出在加载）。
 */
@Composable
fun AppActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.secondaryLabel,
) {
    val reduced = AppTheme.motion.reduced
    val transition = rememberInfiniteTransition(label = "activityIndicator")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = if (reduced) 1800 else 900, easing = LinearEasing), RepeatMode.Restart),
        label = "activityIndicatorRotation",
    )
    Canvas(modifier.progressSemantics().size(28.dp)) {
        val strokeWidth = size.minDimension * 0.11f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val radius = arcSize.minDimension / 2f
        rotate(rotation) {
            // 拖尾：从透明扫到实色，留一小段缺口让头尾不相接
            drawArc(
                brush = Brush.sweepGradient(0f to color.copy(alpha = 0f), 0.82f to color, 1f to color, center = center),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokeWidth),
            )
            // 头部补一个圆头（渐变弧的端点用圆头会把透明的那端也画出来）
            val headAngle = 300f * (PI.toFloat() / 180f)
            drawCircle(color, radius = strokeWidth / 2f, center = Offset(center.x + radius * cos(headAngle), center.y + radius * sin(headAngle)))
        }
    }
}

/** 环形确定进度：淡色轨道 + 着色弧，从 12 点钟方向顺时针填充。 */
@Composable
fun AppCircularProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.tint,
    trackColor: Color = AppTheme.colors.fill,
) {
    Canvas(modifier.progressSemantics(progress().coerceIn(0f, 1f)).size(28.dp)) {
        val strokeWidth = size.minDimension * 0.12f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokeWidth))
        drawArc(
            color,
            startAngle = -90f,
            sweepAngle = 360f * progress().coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/** 进度条（HIG Progress indicators › Progress bars）：4 dp 高的圆头细条。 */
@Composable
fun AppProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.tint,
    trackColor: Color = AppTheme.colors.fill,
) {
    Canvas(modifier.progressSemantics(progress().coerceIn(0f, 1f)).fillMaxWidth().height(4.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        val fraction = progress().coerceIn(0f, 1f)
        if (fraction > 0f) {
            drawRoundRect(color, size = Size((size.width * fraction).coerceAtLeast(size.height), size.height), cornerRadius = radius)
        }
    }
}

/** 不确定进度条：一段着色条在轨道上来回扫过。 */
@Composable
fun AppProgressBar(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.tint,
    trackColor: Color = AppTheme.colors.fill,
) {
    val transition = rememberInfiniteTransition(label = "progressBar")
    val head by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing), RepeatMode.Restart),
        label = "progressBarHead",
    )
    Canvas(modifier.progressSemantics().fillMaxWidth().height(4.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        val start = (head * size.width).coerceAtLeast(0f)
        val end = ((head + 0.35f) * size.width).coerceAtMost(size.width)
        if (end > start) {
            drawRoundRect(color, topLeft = Offset(start, 0f), size = Size(end - start, size.height), cornerRadius = radius)
        }
    }
}
