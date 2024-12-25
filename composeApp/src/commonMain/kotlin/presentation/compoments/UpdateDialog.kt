package presentation.compoments

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.AppPlatformType
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import presentation.screens.auth.data.VersionVo

@Composable
fun UpdateDialog(
    version: VersionVo,
    onDismissRequest: () -> Unit = {},
    onRememberVersion: ((String?) -> Unit)? = null,
) {
    if (version.hasNewVersion) {
        val appPlatform = getAppPlatform()
        var rememberVersionChecked by remember { mutableStateOf(false) }
        val url = remember {
            when (appPlatform.type) {
                AppPlatformType.Android -> version.downloadUrl.firstOrNull {
                    it.contains(".apk")
                }
                AppPlatformType.Desktop,
                AppPlatformType.Ios,
                AppPlatformType.Web,
                -> null
            } ?: version.htmlUrl
        }
        AlertDialog(
            icon = {
                Icon(AppIcons.Update, contentDescription = "AlertDialogIcon")
            },
            title = {
                Text(
                    text = strings.startupDialogTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                // 版本更新提示单选框
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Ver.${version.tagName}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = version.body
                        )
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
                        appPlatform.openUrl(url)
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
                if (onRememberVersion == null) return@AlertDialog
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
        )
    }
}
