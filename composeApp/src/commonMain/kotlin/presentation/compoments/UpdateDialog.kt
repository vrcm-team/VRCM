package presentation.compoments

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.AppPlatformType
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.designsystem.*
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
        AppAlert(
            icon = {
                AppIcon(AppIcons.Refresh, contentDescription = "AlertDialogIcon")
            },
            title = {
                AppText(
                    text = strings.startupDialogTitle,
                    style = AppTheme.type.title2
                )
            },
            text = {
                // 版本更新提示单选框
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = "Ver.${version.tagName}",
                        style = AppTheme.type.caption1Emphasized
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        AppText(
                            text = version.body
                        )
                    }
                    if (onRememberVersion != null) {
                        // 版本更新提示单选框
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AppCheckbox(
                                checked = rememberVersionChecked,
                                enabled = !isUpdating,
                                onCheckedChange = {
                                    rememberVersionChecked = it
                                    // 记住或清除此版本更新提示
                                    val versionTagName = if (rememberVersionChecked) version.tagName else null
                                    onRememberVersion(versionTagName)
                                }
                            )
                            AppText(text = strings.startupDialogRememberVersion)
                        }
                    }
                }
            },
            onDismissRequest = { if (!isUpdating) onDismissRequest() },
            confirmButton = {
                AppButton(
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
                    },
                    style = AppButtonStyle.Tinted,
                ) {
                    if (isUpdating) {
                        AppActivityIndicator(
                            modifier = Modifier.size(18.dp),
                        )
                        updateProgress?.let { progress ->
                            Spacer(Modifier.width(8.dp))
                            AppText("${(progress * 100).toInt()}%")
                        }
                    } else {
                        AppText(strings.startupDialogUpdate)
                    }
                }
            },
            dismissButton = {
                AppButton(
                    enabled = !isUpdating,
                    onClick = {
                        // 关闭弹窗
                        onDismissRequest()
                    },
                    style = AppButtonStyle.Plain,
                ) {
                    AppText(strings.startupDialogIgnore)
                }
            },
        )
    }
}
