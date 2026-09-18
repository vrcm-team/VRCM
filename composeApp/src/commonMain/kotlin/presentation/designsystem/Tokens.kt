package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 语义色一色一义；字体刻度 = Apple 文本样式；间距 4 / 8 / 16 / 20；圆角 6 / 10 / 14 / 20 / 28。
// 不依赖 compose.material3；SF Pro 只在 Apple 平台由系统提供，其余平台用系统默认字体套同一刻度。

/** 语义色。名字对齐 UIKit 语义色。 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val label: Color,
    val secondaryLabel: Color,
    /** 占位文字、chevron、禁用态等非正文元素。 */
    val tertiaryLabel: Color,
    val systemBackground: Color,
    val secondarySystemBackground: Color,
    /** 页面底色（inset grouped 风格：灰底上放白卡片）。 */
    val groupedBackground: Color,
    /** 页面上的卡片 / 分组容器。 */
    val secondaryGroupedBackground: Color,
    /** 卡片内再嵌一层的容器。 */
    val tertiaryGroupedBackground: Color,
    val separator: Color,
    /** 灰底按钮 / 分段控件轨道 / 未选中开关：半透明，叠在任何底上都成立。 */
    val fill: Color,
    /** 更淡的填充：输入框底、占位块。 */
    val secondaryFill: Color,
    val tint: Color,
    val onTint: Color,
    val tintSoft: Color,
    val onTintSoft: Color,
    /** 第二强调色：只用于标签 / 角标等需要与主强调色区分的小面积元素。 */
    val secondaryTint: Color,
    val onSecondaryTint: Color,
    val secondaryTintSoft: Color,
    val onSecondaryTintSoft: Color,
    val warningSoft: Color,
    val onWarningSoft: Color,
    val destructive: Color,
    val onDestructive: Color,
    val destructiveSoft: Color,
    val onDestructiveSoft: Color,
    val success: Color,
    /** 玻璃三件套：填充（含透明度）、0.5 dp 描边、顶部高光线。 */
    val glassFill: Color,
    val glassStroke: Color,
    val glassHighlight: Color,
) {
    /** 模态背后的压暗层。 */
    val scrim: Color get() = Color.Black.copy(alpha = if (isDark) 0.48f else 0.2f)

    /**
     * 容器色对应的前景色：着色面返回配套的 on 色，普通底色返回 [label]；
     * 不是令牌里的颜色（图片取色、游戏语义色等）返回 [Color.Unspecified]，由调用方沿用当前前景色。
     */
    fun contentColorFor(container: Color): Color = when (container) {
        tint -> onTint
        tintSoft -> onTintSoft
        secondaryTint -> onSecondaryTint
        secondaryTintSoft -> onSecondaryTintSoft
        destructive -> onDestructive
        destructiveSoft -> onDestructiveSoft
        warningSoft -> onWarningSoft
        systemBackground, secondarySystemBackground, groupedBackground,
        secondaryGroupedBackground, tertiaryGroupedBackground, fill, secondaryFill -> label
        else -> Color.Unspecified
    }

    /**
     * 模态面板（sheet / alert / 菜单）内部用的"抬升"令牌：深色下整体上移一档，面板上的卡片才不会和面板糊成一片；
     * 浅色下面板用分组底色，其余不变（对齐 UIKit 的 elevated 用户界面层级）。
     */
    fun elevated(): AppColors = if (!isDark) this else copy(
        systemBackground = Color(Palette.DarkSecondarySystemBackground),
        secondarySystemBackground = Color(Palette.DarkTertiaryGroupedBackground),
        groupedBackground = Color(Palette.DarkSecondarySystemBackground),
        secondaryGroupedBackground = Color(Palette.DarkTertiaryGroupedBackground),
        tertiaryGroupedBackground = Color(Palette.DarkElevatedTertiaryBackground),
    )

    companion object {
        fun light(accent: AccentSpec = Accents.default): AppColors = AppColors(
            isDark = false,
            label = Color(Palette.LightLabel),
            secondaryLabel = Color(Palette.LightSecondaryLabel),
            tertiaryLabel = Color(Palette.LightTertiaryLabel),
            systemBackground = Color(Palette.LightSystemBackground),
            secondarySystemBackground = Color(Palette.LightSecondarySystemBackground),
            groupedBackground = Color(Palette.LightGroupedBackground),
            secondaryGroupedBackground = Color(Palette.LightSecondaryGroupedBackground),
            tertiaryGroupedBackground = Color(Palette.LightTertiaryGroupedBackground),
            separator = Color(Palette.LightSeparator),
            fill = Color(Palette.LightFill),
            secondaryFill = Color(Palette.LightSecondaryFill),
            tint = Color(accent.light.tint),
            onTint = Color(accent.light.onTint),
            tintSoft = Color(accent.light.tintSoft),
            onTintSoft = Color(accent.light.onTintSoft),
            secondaryTint = Color(accent.secondaryLight.tint),
            onSecondaryTint = Color(accent.secondaryLight.onTint),
            secondaryTintSoft = Color(accent.secondaryLight.tintSoft),
            onSecondaryTintSoft = Color(accent.secondaryLight.onTintSoft),
            warningSoft = Color(Palette.LightWarningSoft),
            onWarningSoft = Color(Palette.LightOnWarningSoft),
            destructive = Color(Palette.LightDestructive),
            onDestructive = Color.White,
            destructiveSoft = Color(Palette.LightDestructiveSoft),
            onDestructiveSoft = Color(Palette.LightOnDestructiveSoft),
            success = Color(Palette.LightSuccess),
            glassFill = Color(Palette.LightGlassFill),
            glassStroke = Color(Palette.LightGlassStroke),
            glassHighlight = Color(Palette.LightGlassHighlight),
        )

        fun dark(accent: AccentSpec = Accents.default): AppColors = AppColors(
            isDark = true,
            label = Color(Palette.DarkLabel),
            secondaryLabel = Color(Palette.DarkSecondaryLabel),
            tertiaryLabel = Color(Palette.DarkTertiaryLabel),
            systemBackground = Color(Palette.DarkSystemBackground),
            secondarySystemBackground = Color(Palette.DarkSecondarySystemBackground),
            groupedBackground = Color(Palette.DarkGroupedBackground),
            secondaryGroupedBackground = Color(Palette.DarkSecondaryGroupedBackground),
            tertiaryGroupedBackground = Color(Palette.DarkTertiaryGroupedBackground),
            separator = Color(Palette.DarkSeparator),
            fill = Color(Palette.DarkFill),
            secondaryFill = Color(Palette.DarkSecondaryFill),
            tint = Color(accent.dark.tint),
            onTint = Color(accent.dark.onTint),
            tintSoft = Color(accent.dark.tintSoft),
            onTintSoft = Color(accent.dark.onTintSoft),
            secondaryTint = Color(accent.secondaryDark.tint),
            onSecondaryTint = Color(accent.secondaryDark.onTint),
            secondaryTintSoft = Color(accent.secondaryDark.tintSoft),
            onSecondaryTintSoft = Color(accent.secondaryDark.onTintSoft),
            warningSoft = Color(Palette.DarkWarningSoft),
            onWarningSoft = Color(Palette.DarkOnWarningSoft),
            destructive = Color(Palette.DarkDestructive),
            onDestructive = Color(0xFF3B0A07),
            destructiveSoft = Color(Palette.DarkDestructiveSoft),
            onDestructiveSoft = Color(Palette.DarkOnDestructiveSoft),
            success = Color(Palette.DarkSuccess),
            glassFill = Color(Palette.DarkGlassFill),
            glassStroke = Color(Palette.DarkGlassStroke),
            glassHighlight = Color(Palette.DarkGlassHighlight),
        )

        val Light: AppColors = light()
        val Dark: AppColors = dark()
    }
}

/** 两套令牌之间逐色插值，供主题切换的过渡动画使用；[AppColors.isDark] 在过半时翻转。 */
internal fun lerp(start: AppColors, stop: AppColors, fraction: Float): AppColors = AppColors(
    isDark = if (fraction < 0.5f) start.isDark else stop.isDark,
    label = lerp(start.label, stop.label, fraction),
    secondaryLabel = lerp(start.secondaryLabel, stop.secondaryLabel, fraction),
    tertiaryLabel = lerp(start.tertiaryLabel, stop.tertiaryLabel, fraction),
    systemBackground = lerp(start.systemBackground, stop.systemBackground, fraction),
    secondarySystemBackground = lerp(start.secondarySystemBackground, stop.secondarySystemBackground, fraction),
    groupedBackground = lerp(start.groupedBackground, stop.groupedBackground, fraction),
    secondaryGroupedBackground = lerp(start.secondaryGroupedBackground, stop.secondaryGroupedBackground, fraction),
    tertiaryGroupedBackground = lerp(start.tertiaryGroupedBackground, stop.tertiaryGroupedBackground, fraction),
    separator = lerp(start.separator, stop.separator, fraction),
    fill = lerp(start.fill, stop.fill, fraction),
    secondaryFill = lerp(start.secondaryFill, stop.secondaryFill, fraction),
    tint = lerp(start.tint, stop.tint, fraction),
    onTint = lerp(start.onTint, stop.onTint, fraction),
    tintSoft = lerp(start.tintSoft, stop.tintSoft, fraction),
    onTintSoft = lerp(start.onTintSoft, stop.onTintSoft, fraction),
    secondaryTint = lerp(start.secondaryTint, stop.secondaryTint, fraction),
    onSecondaryTint = lerp(start.onSecondaryTint, stop.onSecondaryTint, fraction),
    secondaryTintSoft = lerp(start.secondaryTintSoft, stop.secondaryTintSoft, fraction),
    onSecondaryTintSoft = lerp(start.onSecondaryTintSoft, stop.onSecondaryTintSoft, fraction),
    warningSoft = lerp(start.warningSoft, stop.warningSoft, fraction),
    onWarningSoft = lerp(start.onWarningSoft, stop.onWarningSoft, fraction),
    destructive = lerp(start.destructive, stop.destructive, fraction),
    onDestructive = lerp(start.onDestructive, stop.onDestructive, fraction),
    destructiveSoft = lerp(start.destructiveSoft, stop.destructiveSoft, fraction),
    onDestructiveSoft = lerp(start.onDestructiveSoft, stop.onDestructiveSoft, fraction),
    success = lerp(start.success, stop.success, fraction),
    glassFill = lerp(start.glassFill, stop.glassFill, fraction),
    glassStroke = lerp(start.glassStroke, stop.glassStroke, fraction),
    glassHighlight = lerp(start.glassHighlight, stop.glassHighlight, fraction),
)

/**
 * Apple 文本样式刻度（HIG Typography › Specifications）：正文 17 pt、最小 11 pt。
 * 用 sp 以支持 Dynamic Type / fontScale；行高按 Apple 默认值。`*Emphasized` 是各样式的强调字重。
 */
@Immutable
data class AppTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
) {
    val calloutEmphasized: TextStyle get() = callout.copy(fontWeight = FontWeight.SemiBold)
    val subheadlineEmphasized: TextStyle get() = subheadline.copy(fontWeight = FontWeight.SemiBold)
    val footnoteEmphasized: TextStyle get() = footnote.copy(fontWeight = FontWeight.SemiBold)
    val caption1Emphasized: TextStyle get() = caption1.copy(fontWeight = FontWeight.Medium)
    val caption2Emphasized: TextStyle get() = caption2.copy(fontWeight = FontWeight.SemiBold)

    companion object {
        fun default(family: FontFamily = FontFamily.Default): AppTypography = AppTypography(
            largeTitle = TextStyle(fontFamily = family, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            title1 = TextStyle(fontFamily = family, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            title2 = TextStyle(fontFamily = family, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            title3 = TextStyle(fontFamily = family, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            headline = TextStyle(fontFamily = family, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            body = TextStyle(fontFamily = family, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            callout = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
            subheadline = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
            footnote = TextStyle(fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
            caption1 = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
            caption2 = TextStyle(fontFamily = family, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal),
        )
    }
}

/** 间距刻度 4 / 8 / 16 / 20（外加页边距与列表行内距）。 */
object AppSpacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 16.dp
    val l: Dp = 20.dp
    /** 页面左右边距（inset grouped 列表与卡片）。 */
    val page: Dp = 16.dp
    /** 列表行水平内距。 */
    val row: Dp = 16.dp
}

/** 圆角刻度 6 / 10 / 14 / 20 / 28。 */
object AppRadius {
    val xs: Dp = 6.dp
    val s: Dp = 10.dp
    val m: Dp = 14.dp
    val l: Dp = 20.dp
    val xl: Dp = 28.dp
}

/** 与 [AppRadius] 一一对应的形状；胶囊用 [capsule]。 */
object AppShapes {
    val xs: CornerBasedShape = RoundedCornerShape(AppRadius.xs)
    val s: CornerBasedShape = RoundedCornerShape(AppRadius.s)
    val m: CornerBasedShape = RoundedCornerShape(AppRadius.m)
    val l: CornerBasedShape = RoundedCornerShape(AppRadius.l)
    val xl: CornerBasedShape = RoundedCornerShape(AppRadius.xl)
    val capsule: CornerBasedShape = CircleShape
}

/** 控件尺寸：触控目标 iOS 44 pt、Android 48 dp（取 44 作为视觉尺寸，外扩由调用方留白保证）。 */
object AppSize {
    val touchTarget: Dp = 44.dp
    val rowMinHeight: Dp = 44.dp
    val navBar: Dp = 52.dp
    val tabBar: Dp = 64.dp
    /** 内容里的图标按钮。 */
    val iconButton: Dp = 40.dp
    /** 玻璃圆钮（导航栏 / 头图上的悬浮按钮）：44 pt，和 iOS 的最小触控目标一致。 */
    val glassButton: Dp = 44.dp
    val icon: Dp = 24.dp
}

/** 动效：Reduce Motion 时 [AppMotion.forReduced] 把时长归零、去掉形变。 */
@Immutable
data class AppMotion(val fastMs: Int = 150, val normalMs: Int = 250, val slowMs: Int = 400, val reduced: Boolean = false) {
    companion object {
        val Default = AppMotion()
        fun forReduced() = AppMotion(fastMs = 0, normalMs = 0, slowMs = 0, reduced = true)
    }
}
