package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 内容层容器：按 [shape] 裁剪、铺 [color]、给子树换前景色，并拦住落到自己身上的触摸。
 * 默认是页面（[AppColors.groupedBackground]）上的卡片色；层级靠底色区分而不是阴影，[shadowElevation] 只留给真正浮起的元素。
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = AppTheme.colors.secondaryGroupedBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(color).takeOrElse { LocalContentColor.current },
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .surface(shape, color, border, shadowElevation)
            .semantics(mergeDescendants = false) { isTraversalGroup = true }
            .pointerInput(Unit) {},
        propagateMinConstraints = true,
    ) {
        ProvideContentColor(contentColor, content = content)
    }
}

/** 可点的容器：按压反馈走主题的 [PressHighlightIndication]。 */
@Composable
fun AppSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = AppTheme.colors.secondaryGroupedBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(color).takeOrElse { LocalContentColor.current },
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .surface(shape, color, border, shadowElevation)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        propagateMinConstraints = true,
    ) {
        ProvideContentColor(contentColor, content = content)
    }
}

private fun Modifier.surface(shape: Shape, color: Color, border: BorderStroke?, shadowElevation: Dp): Modifier = this
    .then(if (shadowElevation > 0.dp) Modifier.shadow(shadowElevation, shape, clip = false) else Modifier)
    .then(if (border != null) Modifier.border(border, shape) else Modifier)
    .background(color = color, shape = shape)
    .clip(shape)
