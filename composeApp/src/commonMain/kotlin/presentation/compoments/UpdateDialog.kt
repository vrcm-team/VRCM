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
import kotlinx.coroutines.launch
import presentation.screens.auth.data.VersionVo

@Composable
fun UpdateDialog(
    version: VersionVo,
    onDismissRequest: () -> Unit = {},
    onRememberVersion: ((String?) -> Unit)? = null,
) {
    if (version.hasNewVersion) {
        val appPlatform = getAppPlatform()
        val scope = rememberCoroutineScope()
        var rememberVersionChecked by remember { mutableStateOf(false) }
        var isUpdating by remember { mutableStateOf(false) }
        var updateProgress by remember { mutableStateOf<Float?>(null) }
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
            onDismissRequest = { if (!isUpdating) onDismissRequest() },
            confirmButton = {
                FilledTonalButton(
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    enabled = !isUpdating,
                    onClick = {
                        val hasApk = version.downloadUrl.any { it.endsWith(".apk", ignoreCase = true) }
                        if (appPlatform.type != AppPlatformType.Android || !hasApk) {
                            appPlatform.openUrl(url)
                        } else {
                            isUpdating = true
                            scope.launch {
                                appPlatform.installAppUpdate(
                                    tagName = version.tagName,
                                    downloadUrls = version.downloadUrl,
                                    onProgress = { updateProgress = it },
                                )
                                    .onSuccess { onDismissRequest() }
                                    .onFailure {
                                        appPlatform.openUrl(version.htmlUrl)
                                        onDismissRequest()
                                    }
                                isUpdating = false
                                updateProgress = null
                            }
                        }
                    }
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        updateProgress?.let { progress ->
                            Spacer(Modifier.width(8.dp))
                            Text("${(progress * 100).toInt()}%")
                        }
                    } else {
                        Text(strings.startupDialogUpdate)
                    }
                }
                FilledTonalButton(
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    enabled = !isUpdating,
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
                        enabled = !isUpdating,
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
