package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Alert（HIG Alerts）：居中的标题 + 说明，下面是发丝线分隔的动作格。
 * 两个动作并排（取消在左、默认动作在右且加粗），文字放不下时改为上下堆叠；破坏性动作用 `role = Destructive` 的按钮。
 * [text] 可以放表单等自定义内容，此时内容自己负责对齐。
 */
@Composable
fun AppAlert(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        AppAlertContent(
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
        )
    }
}

/** Alert 的面板本体，不含窗口：给需要自己管显隐动画的调用方用。 */
@Composable
fun AppAlertContent(
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    // 弹窗内部是"抬升"层级：深色下面板和里面的控件各上移一档
    val c = AppTheme.colors.elevated()
    CompositionLocalProvider(LocalAppColors provides c, LocalGlassBackdrop provides null) {
    Column(
        modifier
            .widthIn(min = 270.dp, max = 340.dp)
            .shadow(24.dp, AppShapes.l, clip = false)
            .clip(AppShapes.l)
            .background(c.groupedBackground),
    ) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Box(Modifier.padding(bottom = 4.dp).size(28.dp), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
                    ProvideContentColor(c.tint, content = icon)
                }
            }
            if (title != null) {
                ProvideContentColor(c.label, AppTheme.type.headline.copy(textAlign = TextAlign.Center), title)
            }
            if (text != null) {
                Box(Modifier.weight(1f, fill = false)) {
                    ProvideContentColor(c.label, AppTheme.type.footnote, text)
                }
            }
        }
        AppDivider()
        CompositionLocalProvider(LocalAppControlContext provides AppControlContext.Alert) {
            AlertActions(confirmButton = confirmButton, dismissButton = dismissButton)
        }
    }
    }
}

/** 动作区：两个动作并排且中间一条竖发丝线；任一动作的文字放不进半格就上下堆叠（默认动作在上）。 */
@Composable
private fun AlertActions(confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)?) {
    if (dismissButton == null) {
        CompositionLocalProvider(LocalAlertActionIsDefault provides true) {
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { confirmButton() }
        }
        return
    }
    val separator = AppTheme.colors.separator
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box(propagateMinConstraints = true) { dismissButton() }
            CompositionLocalProvider(LocalAlertActionIsDefault provides true) {
                Box(propagateMinConstraints = true) { confirmButton() }
            }
            Box(Modifier.background(separator))
        },
    ) { measurables, constraints ->
        val (dismiss, confirm, divider) = measurables
        val hairline = 0.5.dp.roundToPx().coerceAtLeast(1)
        val width = constraints.maxWidth
        val half = (width - hairline) / 2
        val sideBySide = dismiss.maxIntrinsicWidth(Constraints.Infinity) <= half &&
            confirm.maxIntrinsicWidth(Constraints.Infinity) <= half
        if (sideBySide) {
            val cell = Constraints(minWidth = half, maxWidth = half)
            val dismissP = dismiss.measure(cell)
            val confirmP = confirm.measure(cell)
            val height = maxOf(dismissP.height, confirmP.height)
            val dividerP = divider.measure(Constraints.fixed(hairline, height))
            layout(width, height) {
                dismissP.placeRelative(0, (height - dismissP.height) / 2)
                dividerP.placeRelative(half, 0)
                confirmP.placeRelative(half + hairline, (height - confirmP.height) / 2)
            }
        } else {
            val cell = Constraints(minWidth = width, maxWidth = width)
            val confirmP = confirm.measure(cell)
            val dismissP = dismiss.measure(cell)
            val dividerP = divider.measure(Constraints.fixed(width, hairline))
            layout(width, confirmP.height + hairline + dismissP.height) {
                confirmP.placeRelative(0, 0)
                dividerP.placeRelative(0, confirmP.height)
                dismissP.placeRelative(0, confirmP.height + hairline)
            }
        }
    }
}

/**
 * 自定义对话框的面板：和 [AppAlert] 同一套材质（大圆角、抬升层级的分组底色、投影），内容任意。
 * 表单类、预览类对话框用它包住内容，再交给 `Dialog`。
 */
@Composable
fun AppDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.l,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors.elevated()
    CompositionLocalProvider(LocalAppColors provides c, LocalGlassBackdrop provides null) {
        AppSurface(
            modifier = modifier,
            shape = shape,
            color = c.groupedBackground,
            contentColor = c.label,
            shadowElevation = 24.dp,
            content = content,
        )
    }
}
