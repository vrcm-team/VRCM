package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenu
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenuItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch

/**
 * 长按弹出菜单：上面是完整的 [text]（放不下就换行），下面一行复制。
 * 给详情页里可能被截断的名字用：[content] 就是那段名字文本，菜单锚在外面这个 Box 上。
 */
@Composable
fun FullTextMenuBox(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val open by rememberUpdatedState {
        if (text.isNotBlank()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            expanded = true
        }
    }
    Box(
        modifier
            .pointerInput(Unit) { detectTapGestures(onLongPress = { open() }) }
            .semantics { onLongClick { open(); true } },
    ) {
        content()
        FullTextMenu(expanded = expanded, text = text, onDismissRequest = { expanded = false })
    }
}

@Composable
private fun FullTextMenu(
    expanded: Boolean,
    text: String,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = strings.copied
    AppMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        AppText(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = AppTheme.type.subheadline,
        )
        AppDivider()
        AppMenuItem(
            text = { AppText(strings.copy) },
            onClick = {
                onDismissRequest()
                clipboard.setText(AnnotatedString(text))
                scope.launch { SharedFlowCentre.toastText.emit(ToastText.Success(copiedMessage)) }
            },
            trailingIcon = {
                AppIcon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
            },
        )
    }
}
