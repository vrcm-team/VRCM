package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

// ---------- Liquid Glass：玻璃只出现在功能层（导航栏按钮 / 标签栏 / 弹层），内容层绝不用 ----------
// 实现：内容层用 haze 标记为取样源（hazeSource），每个玻璃件用 hazeEffect 取样并模糊 + 叠半透明填充 + 2% 噪点，
// 上面再画 0.5 dp 描边与沿轮廓淡出的顶部边缘高光。平台差异：iOS / 桌面走 Skia RenderEffect，Android 12+ 走 RenderNode + RenderEffect；
// Android < 12 没有 RenderEffect（HazeDefaults.blurEnabled() = false），不挂 haze，直接画高填充。
// Reduce Transparency → 不走 haze，不透明回退；Increase Contrast → 加强描边与填充。

/** 内容层的取样源：由 [AppScaffold] 等骨架创建、[glassBackdropSource] 挂到内容层、[glass] 读。一个页面一个。 */
@Stable
class GlassBackdrop internal constructor(internal val hazeState: HazeState)

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop(HazeState()) }

/** 为 null 表示此处拿不到内容层（不在玻璃骨架里，或自己就在内容层中）：玻璃走高填充回退。 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 挂在内容层根上：把这个节点及其子树标记为玻璃的取样源。 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this.hazeSource(backdrop.hazeState)

enum class GlassStyle {
    /** 标准：浅色白玻璃 / 深色黑玻璃，用于标签栏、弹层。 */
    Regular,

    /** 薄玻璃：填充更淡、更透，用于悬浮的圆钮 / 胶囊按钮——面积小，压在图片上也不该像一块白贴纸。 */
    Thin,

    /** 图片 / 视频画面之上的浮动控件：清透变体 + 约 35% 压暗。 */
    Clear,
}

/** 玻璃视觉参数，随无障碍偏好变化。[blurEnabled] 为 false 时没有模糊可叠，只画填充。 */
data class GlassSpec(
    val fill: Color,
    val stroke: Color,
    val highlight: Color,
    val blurRadius: Dp,
    val blurEnabled: Boolean,
)

/** 平台能不能做背景模糊：iOS / 桌面恒可以，Android 需要 API 31+ 的 RenderEffect。 */
fun platformSupportsBackdropBlur(): Boolean = HazeDefaults.blurEnabled()

@Composable
fun rememberGlassSpec(style: GlassStyle = GlassStyle.Regular, backdropAvailable: Boolean = true): GlassSpec {
    val colors = AppTheme.colors
    val a11y = AppTheme.a11y
    return remember(colors, a11y, style, backdropAvailable) {
        val blurEnabled = backdropAvailable && !a11y.reduceTransparency && platformSupportsBackdropBlur()
        val baseFill = when (style) {
            GlassStyle.Regular -> colors.glassFill
            GlassStyle.Thin -> colors.glassFill.copy(alpha = colors.glassFill.alpha * ThinGlassFillScale)
            GlassStyle.Clear -> Color(0x47141416)
        }
        val fill = when {
            a11y.reduceTransparency -> when (style) {
                GlassStyle.Regular, GlassStyle.Thin -> colors.secondarySystemBackground.copy(alpha = 0.98f)
                GlassStyle.Clear -> Color(0xEB141416)
            }
            !blurEnabled -> baseFill.boostedForFallback()
            a11y.increaseContrast -> baseFill.copy(alpha = (baseFill.alpha + 0.15f).coerceAtMost(1f))
            else -> baseFill
        }
        val stroke = if (a11y.increaseContrast) {
            colors.glassStroke.copy(alpha = (colors.glassStroke.alpha * 2.5f).coerceAtMost(1f))
        } else {
            colors.glassStroke
        }
        val highlight = if (style == GlassStyle.Clear) Color(0x59FFFFFF) else colors.glassHighlight
        GlassSpec(fill = fill, stroke = stroke, highlight = highlight, blurRadius = 26.dp, blurEnabled = blurEnabled)
    }
}

/**
 * 薄玻璃的填充是标准玻璃的 75%：浅色约 43% 白、深色约 54% 黑。
 * 这是下限而不是随手取的值：再透的话，符号（正文色）压在纯黑 / 纯白的图片上对比度会跌破图形元素要求的 3:1。
 */
private const val ThinGlassFillScale = 0.75f

/** 没有模糊可叠时的高填充：透明度 +34%，封顶 96%，视觉上仍是"材质"而不是纯色块。 */
private fun Color.boostedForFallback(): Color = copy(alpha = (alpha + 0.34f).coerceAtMost(0.96f))

/**
 * 把这个节点画成玻璃：背景模糊取样自 [backdrop]（默认取 [LocalGlassBackdrop]），叠填充 + 描边 + 高光 + 投影。
 * 不传 backdrop、系统开了「降低透明度」或平台不支持模糊（Android < 12）时不挂 haze，退化为高填充半透明面。
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    style: GlassStyle = GlassStyle.Regular,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    elevation: Dp = 12.dp,
    fillOverride: Color? = null,
): Modifier {
    val spec = rememberGlassSpec(style, backdropAvailable = backdrop != null)
    val fill = fillOverride ?: spec.fill
    val shadowColor = if (AppTheme.colors.isDark) Color(0xB3000000) else Color(0x33000000)
    val base = this
        .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
    val material = if (spec.blurEnabled && backdrop != null) {
        val hazeStyle = remember(fill, spec.blurRadius) {
            HazeStyle(
                backgroundColor = fill.copy(alpha = 1f),
                tints = listOf(HazeTint(fill)),
                blurRadius = spec.blurRadius,
                noiseFactor = 0.02f,
                fallbackTint = HazeTint(fill.boostedForFallback()),
            )
        }
        base.hazeEffect(backdrop.hazeState, style = hazeStyle)
    } else {
        base.drawBehind { drawRect(fill) }
    }
    return material
        .rimHighlight(shape, spec.highlight)
        .border(0.5.dp, spec.stroke, shape)
}

/** 边缘高光的描边粗细。 */
private val RimHighlightWidth = 1.dp

/** 高光从顶边往下淡出的距离：大约绕过一个标准圆角；比半个控件还高时只取到半高。 */
private val RimHighlightFade = 20.dp

/**
 * 顶部的边缘高光（模拟玻璃边缘的折射）：沿 [shape] 的轮廓描边，亮度自上而下淡出——平直的顶边最亮，顺着顶部两个圆角绕下去后消失。
 * 不能画成一条横贯整宽的直线：直线只落在平直的那一段上，到圆角处被形状裁掉，两头就像被截断，深色模式下尤其显眼。
 */
private fun Modifier.rimHighlight(shape: Shape, color: Color): Modifier = drawWithCache {
    val stroke = RimHighlightWidth.toPx()
    if (size.minDimension <= stroke) return@drawWithCache onDrawBehind {}
    val fade = minOf(RimHighlightFade.toPx(), size.height / 2f)
    val brush = Brush.verticalGradient(0f to color, 1f to Color.Transparent, startY = 0f, endY = fade)
    // 轮廓按描边宽度内缩后再画，整条描边都落在形状以内，不会被外层的 clip 削掉一半
    val outline = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
    onDrawBehind {
        translate(stroke / 2f, stroke / 2f) { drawOutline(outline, brush = brush, style = Stroke(stroke)) }
    }
}

/**
 * 弹层 / sheet 的平板材质：比标签栏更高的填充。
 * 只有真能模糊（拿得到内容层、平台支持、没开「降低透明度」）才半透明；否则下面的页面会直接穿出来，改为不透明。
 */
@Composable
fun Modifier.glassFlat(
    shape: Shape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    elevation: Dp = 16.dp,
): Modifier {
    val colors = AppTheme.colors
    val spec = rememberGlassSpec(backdropAvailable = backdrop != null)
    val fill = if (spec.blurEnabled) colors.secondarySystemBackground.copy(alpha = 0.78f) else colors.secondarySystemBackground
    return this.glass(shape, backdrop = backdrop, elevation = elevation, fillOverride = fill)
}

/**
 * 滚动边缘效果（iOS 26 的 scroll edge effect）：透明的顶栏背后，内容从下面滚过时自上而下渐进模糊并淡入页面底色，
 * 既不挡内容也保证栏上的标题 / 按钮可读。拿不到内容层或不能模糊时退化为页面底色到透明的渐变。
 */
@Composable
fun Modifier.scrollEdgeEffect(
    edgeColor: Color = AppTheme.colors.groupedBackground,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
): Modifier {
    val spec = rememberGlassSpec(backdropAvailable = backdrop != null)
    if (spec.blurEnabled && backdrop != null) {
        val hazeStyle = remember(edgeColor) {
            HazeStyle(
                backgroundColor = edgeColor,
                tints = listOf(HazeTint(edgeColor.copy(alpha = 0.72f))),
                blurRadius = 18.dp,
                noiseFactor = 0f,
            )
        }
        return this.hazeEffect(backdrop.hazeState, style = hazeStyle) {
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f, preferPerformance = true)
        }
    }
    return this.drawBehind {
        drawRect(
            Brush.verticalGradient(
                0f to edgeColor,
                0.55f to edgeColor.copy(alpha = 0.86f),
                1f to edgeColor.copy(alpha = 0f),
            )
        )
    }
}
