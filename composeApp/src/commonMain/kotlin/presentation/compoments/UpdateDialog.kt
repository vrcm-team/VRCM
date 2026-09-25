package presentation.compoments

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vrcmteam.vrcm.AppUpdateInstaller
import io.github.vrcmteam.vrcm.DownloadedAppUpdate
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.logger.Logger
import presentation.screens.auth.data.VersionVo

@Composable
fun UpdateDialog(
    version: VersionVo,
    onDismissRequest: () -> Unit = {},
    onRememberVersion: ((String?) -> Unit)? = null,
) {
    if (version.hasNewVersion) {
        val appPlatform = getAppPlatform()
        val installer = appPlatform.appUpdateInstaller
        val scope = rememberCoroutineScope()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val logger: Logger = koinInject()
        // 支持应用内更新的平台在弹窗里下载安装，其余平台跳转版本页面或商店
        val inAppUpdate = remember(installer, version) {
            installer?.let { InAppUpdate(it, version, scope, lifecycle, logger) }
        }
        val updateState = inAppUpdate?.state ?: InAppUpdateState.Idle
        var rememberVersionChecked by remember { mutableStateOf(false) }
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
                    version.versionName?.let { versionName ->
                        AppText(
                            text = "Ver.$versionName",
                            style = AppTheme.type.caption1Emphasized
                        )
                    }
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
                    if (updateState != InAppUpdateState.Idle) {
                        InAppUpdateProgress(updateState, Modifier.padding(top = 12.dp))
                    } else if (onRememberVersion != null) {
                        // 版本更新提示单选框
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AppCheckbox(
                                checked = rememberVersionChecked,
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
            // 下载中只能点"取消"关闭，避免误触弹窗外面把下载停掉
            onDismissRequest = { if (updateState !is InAppUpdateState.Downloading) onDismissRequest() },
            confirmButton = {
                if (inAppUpdate == null) {
                    AppButton(
                        onClick = { appPlatform.openUrl(version.htmlUrl) },
                        style = AppButtonStyle.Tinted,
                    ) {
                        AppText(strings.startupDialogUpdate)
                    }
                } else {
                    InAppUpdateAction(inAppUpdate, onDismissRequest)
                }
            },
            dismissButton = if (updateState is InAppUpdateState.Downloading) null else {
                {
                    AppButton(
                        onClick = {
                            // 关闭弹窗
                            onDismissRequest()
                        },
                        style = AppButtonStyle.Plain,
                    ) {
                        AppText(if (updateState == InAppUpdateState.Idle) strings.startupDialogIgnore else strings.cancel)
                    }
                }
            },
        )
    }
}

/** 弹窗的主操作随应用内更新的进度切换：更新 → 下载中只能取消 → 安装；失败时重试。 */
@Composable
private fun InAppUpdateAction(inAppUpdate: InAppUpdate, onDismissRequest: () -> Unit) {
    when (val state = inAppUpdate.state) {
        InAppUpdateState.Idle -> AppButton(onClick = inAppUpdate::download, style = AppButtonStyle.Tinted) {
            AppText(strings.startupDialogUpdate)
        }
        is InAppUpdateState.Downloading -> AppButton(
            onClick = {
                inAppUpdate.cancel()
                onDismissRequest()
            },
            style = AppButtonStyle.Plain,
        ) {
            AppText(strings.cancel)
        }
        is InAppUpdateState.Downloaded -> AppButton(
            onClick = { inAppUpdate.install(state.update) },
            style = AppButtonStyle.Tinted,
        ) {
            AppText(strings.startupDialogInstall)
        }
        InAppUpdateState.Failed -> AppButton(onClick = inAppUpdate::download, style = AppButtonStyle.Tinted) {
            AppText(strings.retry)
        }
    }
}

/** 进度条下面一行说明，下载中右侧是百分比；失败时只有一行红字。 */
@Composable
private fun InAppUpdateProgress(state: InAppUpdateState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            InAppUpdateState.Idle -> Unit
            is InAppUpdateState.Downloading -> {
                val total = state.totalBytes
                if (total == null) {
                    AppProgressBar()
                } else {
                    AppProgressBar(progress = { state.downloadedBytes.toFloat() / total })
                }
                InAppUpdateCaption(
                    text = strings.startupDialogDownloading,
                    percent = total?.let { "${(state.downloadedBytes * 100 / it).coerceIn(0, 100)}%" },
                )
            }
            is InAppUpdateState.Downloaded -> {
                AppProgressBar(progress = { 1f })
                InAppUpdateCaption(text = strings.startupDialogDownloaded)
            }
            InAppUpdateState.Failed -> AppText(
                text = strings.startupDialogDownloadFailed,
                modifier = Modifier.fillMaxWidth(),
                color = AppTheme.colors.destructive,
                textAlign = TextAlign.Center,
                style = AppTheme.type.caption1,
            )
        }
    }
}

@Composable
private fun InAppUpdateCaption(text: String, percent: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(text = text, style = AppTheme.type.caption1, color = AppTheme.colors.secondaryLabel)
        if (percent != null) {
            AppText(text = percent, style = AppTheme.type.caption1, color = AppTheme.colors.secondaryLabel)
        }
    }
}

private sealed interface InAppUpdateState {
    data object Idle : InAppUpdateState

    /** [totalBytes] 为 null：还没连上下载源，或者下载源没给出大小。 */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : InAppUpdateState

    data class Downloaded(val update: DownloadedAppUpdate) : InAppUpdateState

    data object Failed : InAppUpdateState
}

/** 在弹窗里下载新版本、打开系统安装器。下载跟着弹窗走：弹窗关闭时 [scope] 取消，下载也随之停止。 */
@Stable
private class InAppUpdate(
    private val installer: AppUpdateInstaller,
    private val version: VersionVo,
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle,
    private val logger: Logger,
) {
    var state by mutableStateOf<InAppUpdateState>(InAppUpdateState.Idle)
        private set
    private var downloadJob: Job? = null

    fun download() {
        state = InAppUpdateState.Downloading(downloadedBytes = 0, totalBytes = null)
        downloadJob = scope.launch {
            val result = installer.download(version.tagName, version.downloadUrl) { downloaded, total ->
                state = InAppUpdateState.Downloading(downloaded, total)
            }
            state = result.fold(
                onSuccess = { InAppUpdateState.Downloaded(it) },
                onFailure = {
                    logger.error("Failed to download app update: ${it.message.orEmpty()}")
                    InAppUpdateState.Failed
                },
            )
            result.onSuccess { update ->
                // 下完直接打开安装器；应用在后台时系统不让打开，等回到前台再打开
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                install(update)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        state = InAppUpdateState.Idle
    }

    fun install(update: DownloadedAppUpdate) {
        installer.install(update).onFailure {
            logger.error("Failed to open the app installer: ${it.message.orEmpty()}")
            state = InAppUpdateState.Failed
        }
    }
}
