package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheetState
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppSheetState

@Composable
fun ABottomSheet(
    isVisible: Boolean,
    sheetState: AppSheetState = rememberAppSheetState(),
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isVisible) {
        // 底部系统栏的内边距由 AppSheet 自己处理
        AppSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            content = content,
        )
    }
}
