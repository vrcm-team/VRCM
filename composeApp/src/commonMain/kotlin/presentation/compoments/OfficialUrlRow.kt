package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch

/** Displays a public VRChat URL with a trailing copy action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialUrlRow(
    url: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = strings.officialUrlCopied
    val failedMessage = strings.officialUrlCopyFailed
    var isTruncated by remember(url) { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = containerColor,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = url,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { isTruncated = it.hasVisualOverflow },
                )
                IconButton(
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
                    )
                }
            }
        }
    }
    if (isTruncated) {
        ATooltipBox(tooltip = { Text(url) }) {
            content()
        }
    } else {
        content()
    }
}
