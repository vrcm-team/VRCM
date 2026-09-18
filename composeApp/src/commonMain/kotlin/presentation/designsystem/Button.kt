package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Apple 四档按钮（HIG Buttons）：plain 无底 / gray 灰底 / tinted 淡色底 / prominent 着色底。
 * 一个视图最多 1–2 个 prominent。iOS 26 起默认胶囊形。
 */
enum class AppButtonStyle { Plain, Gray, Tinted, Prominent }

/** 按钮语气：破坏性动作用红色系。 */
enum class AppButtonRole { Default, Destructive }

/** 控件尺寸：Small 用在行内 / 卡片角落，Regular 是默认，Large 是页面主操作。 */
enum class AppButtonSize(internal val minHeight: Dp, internal val horizontalPadding: Dp) {
    Small(30.dp, 12.dp),
    Regular(38.dp, 16.dp),
    Large(50.dp, 20.dp),
}

/**
 * 控件所处的功能层。和 SwiftUI 一样，同一个按钮放进导航栏 / 弹窗 / 菜单会自动换成那一层的样子：
 * 导航栏里是玻璃圆钮 / 玻璃胶囊，弹窗里是整格的文字动作。
 */
enum class AppControlContext { Content, NavBar, Alert }

val LocalAppControlContext = compositionLocalOf { AppControlContext.Content }

/** 弹窗动作格里，默认动作（确认）加粗。由 [AppAlert] 提供。 */
internal val LocalAlertActionIsDefault = compositionLocalOf { false }

internal data class ButtonPalette(val container: Color, val content: Color)

@Composable
internal fun buttonPalette(style: AppButtonStyle, role: AppButtonRole): ButtonPalette {
    val c = AppTheme.colors
    return when (role) {
        AppButtonRole.Default -> when (style) {
            AppButtonStyle.Plain -> ButtonPalette(Color.Transparent, c.tint)
            AppButtonStyle.Gray -> ButtonPalette(c.fill, c.tint)
            AppButtonStyle.Tinted -> ButtonPalette(c.tintSoft, c.onTintSoft)
            AppButtonStyle.Prominent -> ButtonPalette(c.tint, c.onTint)
        }
        AppButtonRole.Destructive -> when (style) {
            AppButtonStyle.Plain -> ButtonPalette(Color.Transparent, c.destructive)
            AppButtonStyle.Gray -> ButtonPalette(c.fill, c.destructive)
            AppButtonStyle.Tinted -> ButtonPalette(c.destructiveSoft, c.onDestructiveSoft)
            AppButtonStyle.Prominent -> ButtonPalette(c.destructive, c.onDestructive)
        }
    }
}

/**
 * 按钮。[containerColor] / [contentColor] 只在令牌配色表达不了时才传（例如主题色预览）。
 * 禁用态整体 40% 不透明度。
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Gray,
    role: AppButtonRole = AppButtonRole.Default,
    size: AppButtonSize = AppButtonSize.Regular,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = size.horizontalPadding),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val context = LocalAppControlContext.current
    if (context == AppControlContext.Alert) {
        AlertActionButton(onClick, modifier, role, enabled, contentColor, interactionSource, content)
        return
    }
    val palette = buttonPalette(style, role)
    val container = if (containerColor.isSpecified) containerColor else palette.container
    val foreground = if (contentColor.isSpecified) contentColor else palette.content
    val type = AppTheme.type
    val textStyle = when (size) {
        AppButtonSize.Small -> type.footnoteEmphasized
        AppButtonSize.Regular -> type.subheadlineEmphasized
        AppButtonSize.Large -> type.headline
    }
    val inNavBar = context == AppControlContext.NavBar
    val surface = if (inNavBar && style != AppButtonStyle.Prominent) {
        // 导航栏里的文字按钮是玻璃胶囊；主操作（Prominent）保持着色底
        Modifier.glass(shape, style = GlassStyle.Thin, elevation = 6.dp)
    } else {
        Modifier.clip(shape).background(container)
    }
    Row(
        modifier = modifier
            .then(surface)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.4f)
            .defaultMinSize(minWidth = size.minHeight, minHeight = if (inNavBar) AppSize.glassButton else size.minHeight)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(foreground, textStyle) { content() }
    }
}

/** 文字按钮的便捷版。 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Gray,
    role: AppButtonRole = AppButtonRole.Default,
    size: AppButtonSize = AppButtonSize.Regular,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    AppButton(onClick = onClick, modifier = modifier, style = style, role = role, size = size, enabled = enabled) {
        if (icon != null) AppIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        AppText(text, maxLines = 1)
    }
}

/** 弹窗里的动作格：撑满所在格、44 dp 高、无底，文字用强调色；默认动作加粗，破坏性动作红字。 */
@Composable
private fun AlertActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    role: AppButtonRole,
    enabled: Boolean,
    contentColor: Color,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val c = AppTheme.colors
    val foreground = when {
        contentColor.isSpecified -> contentColor
        role == AppButtonRole.Destructive -> c.destructive
        else -> c.tint
    }
    val weight = if (LocalAlertActionIsDefault.current) FontWeight.SemiBold else FontWeight.Normal
    // 宽度由 AppAlert 的动作格用最小约束撑开，这里不自己 fillMaxWidth
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.4f)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(foreground, AppTheme.type.body.copy(fontWeight = weight)) { content() }
    }
}

/**
 * 图标按钮：默认无底、前景跟随当前前景色；放进导航栏自动变成玻璃圆钮。
 * 视觉尺寸 [size] 默认随形态：内容里 40 dp，玻璃圆钮 44 dp。图标按钮的图标必须给 contentDescription。
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppButtonStyle = AppButtonStyle.Plain,
    role: AppButtonRole = AppButtonRole.Default,
    size: Dp = Dp.Unspecified,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val inNavBar = LocalAppControlContext.current == AppControlContext.NavBar
    val palette = buttonPalette(style, role)
    val container = if (containerColor.isSpecified) containerColor else palette.container
    val foreground = when {
        contentColor.isSpecified -> contentColor
        // 无底图标按钮是"符号"，不是链接：跟随所在容器的前景色，而不是强调色
        style == AppButtonStyle.Plain && role == AppButtonRole.Default -> LocalContentColor.current
        else -> palette.content
    }
    val glass = inNavBar && style == AppButtonStyle.Plain && !containerColor.isSpecified
    val surface = if (glass) {
        Modifier.glass(CircleShape, style = GlassStyle.Thin, elevation = 8.dp)
    } else {
        Modifier.clip(CircleShape).background(container)
    }
    Box(
        modifier = modifier
            .size(if (size.isSpecified) size else if (glass) AppSize.glassButton else AppSize.iconButton)
            .then(surface)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        if (glass) {
            // 玻璃圆钮里的符号统一 22 dp、正文色
            Box(Modifier.requiredSize(22.dp), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
                ProvideContentColor(if (contentColor.isSpecified) contentColor else AppTheme.colors.label, content = content)
            }
        } else {
            ProvideContentColor(foreground, content = content)
        }
    }
}

/** 可切换的图标按钮：选中时着色底。 */
@Composable
fun AppIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = AppSize.iconButton,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) c.tint else c.fill)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(if (checked) c.onTint else c.label, content = content)
    }
}

/**
 * 浮动主操作：悬在内容之上的圆形着色按钮（56 dp），带投影。一个页面最多一个。
 */
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppTheme.colors.tint,
    contentColor: Color = AppTheme.colors.contentColorFor(containerColor).let { if (it.isSpecified) it else AppTheme.colors.onTint },
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(contentColor, content = content)
    }
}
