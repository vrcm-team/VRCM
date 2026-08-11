package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch

/** Provides an icon action that copies a public VRChat URL without displaying it inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialUrlCopyButton(
    url: String,
    modifier: Modifier = Modifier,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = strings.officialUrlCopied
    val failedMessage = strings.officialUrlCopyFailed

    ATooltipBox(tooltip = { Text(strings.copyOfficialUrl) }) {
        IconButton(
            modifier = modifier,
            colors = colors,
            onClick = {
                val copied = runCatching {
                    clipboard.setText(AnnotatedString(url))
                }.isSuccess
                scope.launch {
                    SharedFlowCentre.toastText.emit(
                        if (copied) {
                            ToastText.Success(copiedMessage)
                        } else {
                            ToastText.Error(failedMessage)
                        }
                    )
                }
            },
        ) {
            Icon(
                imageVector = AppIcons.ContentCopy,
                contentDescription = strings.copyOfficialUrl,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
