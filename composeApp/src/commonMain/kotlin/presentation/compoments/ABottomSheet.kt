package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ABottomSheet(
    isVisible: Boolean,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isVisible) {
        val bottomInsetPadding = getInsetPadding(0, WindowInsets::getBottom)
        ModalBottomSheet(
            modifier = Modifier.offset(y = bottomInsetPadding),
            onDismissRequest = onDismissRequest,
            sheetState = sheetState
        ) {
            content()
            Spacer(modifier = Modifier.height(bottomInsetPadding))
        }
    }
}