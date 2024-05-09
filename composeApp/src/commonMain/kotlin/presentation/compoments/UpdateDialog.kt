package presentation.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import presentation.screens.auth.data.VersionVo

@Composable
fun UpdateDialog(
    version: VersionVo,
    onDismissRequest: () -> Unit = {},
    onRememberVersion: (String?) -> Unit
) {
    if (version.hasNewVersion) {
        val appPlatform = getAppPlatform()
        var rememberVersionChecked by remember { mutableStateOf(false) }
        AlertDialog(
            icon = {
                Icon(Icons.Filled.Update, contentDescription = "AlertDialogIcon")
            },
            title = {
                Text(text = strings.startupDialogTitle)
            },
            text = {
                // 版本更新提示单选框
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "Ver.${version.tagName}")
                    // 版本更新提示单选框
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Checkbox(
                            checked = rememberVersionChecked,
                            onCheckedChange = {
                                rememberVersionChecked = it
                                // 记住或清除此版本更新提示
                                val versionTagName = if (rememberVersionChecked) version.tagName else null
                                onRememberVersion(versionTagName)
                            }
                        )
                        Text(text = strings.startupDialogRememberVersion)
                    }
                }

            },
            onDismissRequest = onDismissRequest,
            confirmButton = {
                FilledTonalButton(
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = {
                        appPlatform.openUrl(version.htmlUrl)
                    }
                ) {
                    Text(strings.startupDialogUpdate)
                }
                FilledTonalButton(
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    onClick = {
                        // 关闭弹窗
                        onDismissRequest()
                    }
                ) {
                    Text(strings.startupDialogIgnore)
                }
            }
        )
    }
}
