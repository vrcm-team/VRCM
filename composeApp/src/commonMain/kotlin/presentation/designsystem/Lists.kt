package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- inset grouped 列表与内容层卡片 ----------

/** 发丝分隔线：默认 0.5 dp、[AppColors.separator]。 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.5.dp,
    color: Color = AppTheme.colors.separator,
) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun AppVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.5.dp,
    color: Color = AppTheme.colors.separator,
) {
    Box(modifier.fillMaxHeight().width(thickness).background(color))
}

/** 分组标题：13 pt 次级色，左对齐到行内距。 */
@Composable
fun AppSectionHeader(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        modifier.padding(start = AppSpacing.row, end = AppSpacing.row, bottom = 6.dp),
        style = AppTheme.type.footnote,
        color = AppTheme.colors.secondaryLabel,
    )
}

/** 分组页脚说明文字（HIG：用页脚解释，不用弹窗）。 */
@Composable
fun AppSectionFooter(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.colors.secondaryLabel) {
    AppText(
        text,
        modifier.padding(start = AppSpacing.row, end = AppSpacing.row, top = 6.dp),
        style = AppTheme.type.footnote,
        color = color,
    )
}

/** 一组圆角容器；行之间用 [AppDivider]。 */
@Composable
fun AppGroup(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.l,
    background: Color = AppTheme.colors.secondaryGroupedBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background),
        content = content,
    )
}

/**
 * 懒加载列表里的分组行容器：按所在位置取圆角（首行圆上角、末行圆下角、中间直角），
 * 相邻的行拼起来就是一张 inset grouped 卡片；除首行外，顶部画一条从 [dividerInset] 起的发丝线。
 * 行数不定或很多、不能整组放进一个 [AppGroup] 时用它。
 */
@Composable
fun AppGroupedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    dividerInset: Dp = AppSpacing.row,
    background: Color = AppTheme.colors.secondaryGroupedBackground,
    content: @Composable () -> Unit,
) {
    val top = if (index == 0) AppRadius.l else 0.dp
    val bottom = if (index == count - 1) AppRadius.l else 0.dp
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            .background(background),
    ) {
        if (index > 0) AppDivider(Modifier.padding(start = dividerInset))
        content()
    }
}

/**
 * 列表行：前导（图标 / 色块）+ 标题 / 副标题 + 尾随（值、胶囊、开关、chevron）。
 * 最小高度 44 dp；`onClick` 为空时不可点。
 */
@Composable
fun AppRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    value: String? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    titleColor: Color = AppTheme.colors.label,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = null, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .defaultMinSize(minHeight = AppSize.rowMinHeight)
            .padding(horizontal = AppSpacing.row, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(RowContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, style = AppTheme.type.body, color = if (enabled) titleColor else c.tertiaryLabel)
            if (subtitle != null) AppText(subtitle, style = AppTheme.type.footnote, color = c.secondaryLabel)
        }
        if (value != null) AppText(value, style = AppTheme.type.body, color = c.secondaryLabel, maxLines = 1)
        if (trailing != null) trailing()
        if (chevron) AppIcon(AppChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = c.tertiaryLabel)
    }
}

/** 插槽版列表行：前导 / 标题 / 说明 / 尾随都是任意内容。 */
@Composable
fun AppListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Transparent,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(containerColor)
            .defaultMinSize(minHeight = AppSize.rowMinHeight)
            .padding(horizontal = AppSpacing.row, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(RowContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) ProvideContentColor(c.secondaryLabel, content = leadingContent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProvideContentColor(c.label, AppTheme.type.body, headlineContent)
            if (supportingContent != null) ProvideContentColor(c.secondaryLabel, AppTheme.type.footnote, supportingContent)
        }
        if (trailingContent != null) ProvideContentColor(c.secondaryLabel, AppTheme.type.subheadline, trailingContent)
    }
}

private val RowContentGap = 12.dp
private val RowIconSize = 29.dp

/** 带 [AppRowIcon] 的行之间的分隔线起点：行内边距 + 图标方块 + 图标与标题的间隔（分隔线从标题起）。 */
val AppRowIconDividerInset: Dp = AppSpacing.row + RowIconSize + RowContentGap

/** [AppRowIcon] 的底色：按功能类别挑一个，同一组里尽量不重复。 */
object AppRowIconColor {
    val Blue = Color(Palette.RowIconBlue)
    val Indigo = Color(Palette.RowIconIndigo)
    val Purple = Color(Palette.RowIconPurple)
    val Pink = Color(Palette.RowIconPink)
    val Red = Color(Palette.RowIconRed)
    val Orange = Color(Palette.RowIconOrange)
    val Green = Color(Palette.RowIconGreen)
    val Teal = Color(Palette.RowIconTeal)
    val Gray = Color(Palette.RowIconGray)
}

/** 设置行前导的彩色圆角方块图标（29 pt，7 pt 圆角，白色符号）。 */
@Composable
fun AppRowIcon(icon: ImageVector, background: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(RowIconSize).clip(RoundedCornerShape(7.dp)).background(background), contentAlignment = Alignment.Center) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
    }
}

/**
 * 内容层卡片：不透明、无阴影，靠与页面底色的差别成层。[onClick] 非空时整卡可点。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = AppShapes.l,
    color: Color = AppTheme.colors.secondaryGroupedBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(color).takeOrElse { LocalContentColor.current },
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        AppSurface(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, color = color, contentColor = contentColor, border = border) {
            Column(content = content)
        }
    } else {
        AppSurface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, border = border) {
            Column(content = content)
        }
    }
}
