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
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.extensions.shareUrl
import io.github.vrcmteam.vrcm.presentation.extensions.supportsSystemShare
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch

/** Shares a public VRChat URL, with a clipboard fallback on unsupported platforms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialUrlShareButton(
    url: String,
    modifier: Modifier = Modifier,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    val platform = getAppPlatform()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val usesSystemShare = platform.supportsSystemShare
    val actionDescription = if (usesSystemShare) strings.shareOfficialUrl else strings.copyOfficialUrl
    val copiedMessage = strings.officialUrlCopied
    val failedMessage = if (usesSystemShare) {
        strings.officialUrlShareFailed
    } else {
        strings.officialUrlCopyFailed
    }

    ATooltipBox(tooltip = { Text(actionDescription) }) {
        IconButton(
            modifier = modifier,
            colors = colors,
            onClick = {
                if (usesSystemShare) {
                    if (!runCatching { platform.shareUrl(url) }.getOrDefault(false)) {
                        scope.launch {
                            SharedFlowCentre.toastText.emit(ToastText.Error(failedMessage))
                        }
                    }
                } else {
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
                }
            },
        ) {
            Icon(
                imageVector = if (usesSystemShare) AppIcons.Share else AppIcons.ContentCopy,
                contentDescription = actionDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
