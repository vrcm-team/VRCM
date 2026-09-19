package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 菜单（HIG Menus）：多选一或一组相关动作；锚在触发控件下方，放不下就翻到上方。
 * 从锚点一角缩放淡入；选中项用对勾表示——不靠颜色单独传达状态。
 * 放在触发控件所在的 Box 里，锚点就是那个 Box。
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = AppTheme.motion
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return

    val density = LocalDensity.current
    val positionProvider = remember(offset, density) {
        with(density) {
            MenuPositionProvider(
                offset = IntOffset(offset.x.roundToPx(), offset.y.roundToPx()),
                windowMargin = 8.dp.roundToPx(),
                inset = MenuShadowInset.roundToPx(),
            )
        }
    }
    val transition = rememberTransition(visibleState, label = "AppMenu")
    val scale by transition.animateFloat({ tween(motion.fastMs) }, label = "menuScale") { if (it) 1f else 0.86f }
    val alpha by transition.animateFloat({ tween(motion.fastMs) }, label = "menuAlpha") { if (it) 1f else 0f }
    val c = AppTheme.colors.elevated()
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(
            LocalAppColors provides c,
            LocalContentColor provides c.label,
            LocalAppControlContext provides AppControlContext.Content,
            LocalGlassBackdrop provides null,
        ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = positionProvider.transformOrigin
                }
                .padding(MenuShadowInset) // 给投影留出不被弹层窗口裁掉的空间，定位时再扣回去
                .shadow(16.dp, AppShapes.m, clip = false)
                .clip(AppShapes.m)
                .background(c.secondarySystemBackground)
                .then(modifier)
                .widthIn(min = 180.dp, max = 300.dp)
                .width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
        }
    }
}

/** 菜单行：44 dp 高、正文字号；[role] 为破坏性时红字。行与行之间自己加 [AppDivider]（成组时）。 */
@Composable
fun AppMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    role: AppButtonRole = AppButtonRole.Default,
) {
    val c = AppTheme.colors
    val foreground = if (role == AppButtonRole.Destructive) c.destructive else c.label
    Row(
        modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .enabledAlpha(enabled)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(foreground, AppTheme.type.body) {
            if (leadingIcon != null) Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            Box(Modifier.weight(1f)) { text() }
            // 尾随位多半是图标，也可能是计数这类短文字：至少占一个图标位，内容更宽时跟着撑开而不是被裁掉
            if (trailingIcon != null) {
                Box(Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp), contentAlignment = Alignment.Center) { trailingIcon() }
            }
        }
    }
}

/**
 * 弹出按钮（HIG Pop-up buttons）：一行里显示当前值 + 上下箭头，点开是一张菜单；用于表单里的多选一。
 * [label] 在上方，和 [AppTextField] 的标签同一套排版。
 */
@Composable
fun AppPopUpButton(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            AppText(label, Modifier.padding(horizontal = 4.dp), style = AppTheme.type.footnote, color = c.secondaryLabel)
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.s)
                    .background(c.secondaryFill)
                    .clickable(interactionSource = null, indication = LocalIndication.current, enabled = enabled) { onExpandedChange(!expanded) }
                    .enabledAlpha(enabled)
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(value, Modifier.weight(1f), style = AppTheme.type.body, color = c.label, maxLines = 1)
                AppIcon(AppChevronUpDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.secondaryLabel)
            }
            AppMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, content = menuContent)
        }
    }
}

private val MenuShadowInset = 12.dp

/**
 * 贴着锚点下沿、起始边对齐；右侧放不下时，宽锚点改为末端对齐、窄锚点就近挪回窗口内；下方放不下翻到上方；最后夹在窗口边距内。
 * 弹层四周有 [inset] 的投影留白，定位按可见面板算，再把留白扣回去。
 */
private class MenuPositionProvider(
    private val offset: IntOffset,
    private val windowMargin: Int,
    private val inset: Int,
) : PopupPositionProvider {
    var transformOrigin: TransformOrigin = TransformOrigin(0f, 0f)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = (popupContentSize.width - inset * 2).coerceAtLeast(0)
        val height = (popupContentSize.height - inset * 2).coerceAtLeast(0)
        val startAligned = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left + offset.x
        } else {
            anchorBounds.right - offset.x - width
        }
        val endAligned = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - offset.x - width
        } else {
            anchorBounds.left + offset.x
        }
        val fitsStart = startAligned >= windowMargin && startAligned + width <= windowSize.width - windowMargin
        val x = when {
            fitsStart -> startAligned
            // 锚点比菜单宽（整行的弹出按钮）：改贴锚点的末端边
            anchorBounds.width >= width -> endAligned
            // 锚点比菜单窄（一个文字标签、一个图标）：只挪到放得下为止，菜单才留在锚点正下方而不是被甩到另一侧
            else -> startAligned
        }.coerceIn(windowMargin, (windowSize.width - width - windowMargin).coerceAtLeast(windowMargin))

        val below = anchorBounds.bottom + offset.y
        val above = anchorBounds.top - offset.y - height
        val fitsBelow = below + height <= windowSize.height - windowMargin
        val y = (if (fitsBelow || above < windowMargin) below else above)
            .coerceIn(windowMargin, (windowSize.height - height - windowMargin).coerceAtLeast(windowMargin))

        val originX = if (popupContentSize.width == 0) 0f else {
            ((anchorBounds.left + anchorBounds.width / 2 - (x - inset)).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        }
        transformOrigin = TransformOrigin(originX, if (y >= anchorBounds.bottom) 0f else 1f)
        return IntOffset(x - inset, y - inset)
    }
}
