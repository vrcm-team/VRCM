package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.PreparedStatisticsImport
import io.github.vrcmteam.vrcm.service.StatisticsTransferService
import io.github.vrcmteam.vrcm.storage.StatisticsTransferException
import io.github.vrcmteam.vrcm.storage.StatisticsTransferFailure
import io.github.vrcmteam.vrcm.storage.StatisticsSnapshot
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class StatisticsOperation { Import, Export }

/** 与设置页相同的生命周期和行布局；文件选择取消时不修改任何统计。 */
@Composable
internal fun StatisticsTransferRows(enabled: Boolean) {
    val service = koinInject<StatisticsTransferService>()
    val scope = rememberCoroutineScope()
    val locale = strings
    val session by SharedFlowCentre.currentSession.collectAsState()
    var operation by remember { mutableStateOf<StatisticsOperation?>(null) }
    var selectedSession by remember { mutableStateOf<AccountSessionToken?>(null) }
    var prepared by remember { mutableStateOf<PreparedStatisticsImport?>(null) }
    var exportSnapshot by remember { mutableStateOf<StatisticsSnapshot?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(session?.token) {
        if (selectedSession != null && selectedSession != session?.token) {
            job?.cancel()
            prepared = null
        }
    }

    suspend fun report(error: Exception) {
        if (error is CancellationException) throw error
        val failure = (error as? StatisticsTransferException)?.failure ?: StatisticsTransferFailure.Io
        SharedFlowCentre.toastText.emit(ToastText.Error(failure.message(locale)))
    }

    val saver = rememberFileSaverLauncher { file ->
        val token = selectedSession
        val snapshot = exportSnapshot
        if (file == null || token == null || snapshot == null || !SharedFlowCentre.isCurrentSession(token)) {
            operation = null
            exportSnapshot = null
        } else {
            job = scope.launch {
                try {
                    service.export(token, snapshot, file)
                    SharedFlowCentre.toastText.emit(ToastText.Success(locale.statisticsExportSuccess))
                } catch (error: Exception) {
                    report(error)
                } finally {
                    operation = null
                    exportSnapshot = null
                }
            }
        }
    }
    val picker = rememberFilePickerLauncher(type = FileKitType.File(listOf("sqlite3", "db", "sqlite"))) { file ->
        val token = selectedSession
        if (file == null || token == null || !SharedFlowCentre.isCurrentSession(token)) {
            operation = null
        } else {
            job = scope.launch {
                try {
                    prepared = service.prepareImport(token, file)
                } catch (error: Exception) {
                    report(error)
                } finally {
                    operation = null
                }
            }
        }
    }
    val canStart = enabled && session != null && operation == null && prepared == null
    AppRow(
        title = locale.statisticsExport,
        enabled = canStart,
        trailing = { if (operation == StatisticsOperation.Export) AppActivityIndicator(Modifier.size(20.dp)) },
        onClick = {
            val token = session?.token
            if (canStart && operation == null && token != null) {
                selectedSession = token
                operation = StatisticsOperation.Export
                job = scope.launch {
                    try {
                        exportSnapshot = service.prepareExport(token)
                        saver.launch(suggestedName = "VRCM-statistics-${token.userId}", extension = "sqlite3")
                    } catch (error: Exception) {
                        operation = null
                        report(error)
                    }
                }
            }
        },
    )
    AppDivider(Modifier.padding(start = AppSpacing.row))
    AppRow(
        title = locale.statisticsImport,
        enabled = canStart,
        trailing = { if (operation == StatisticsOperation.Import) AppActivityIndicator(Modifier.size(20.dp)) },
        onClick = {
            if (canStart && operation == null) {
                selectedSession = session?.token
                operation = StatisticsOperation.Import
                picker.launch()
            }
        },
    )
    prepared?.let { pending ->
        AppAlert(
            onDismissRequest = { if (operation == null) prepared = null },
            title = { AppText(locale.statisticsImport) },
            text = { AppText(locale.statisticsImportConfirmation
                .replace("%friends%", pending.snapshot.summaries.size.toString())
                .replace("%events%", pending.snapshot.events.size.toString())
                .replace("%sessions%", pending.snapshot.sessions.size.toString())) },
            confirmButton = {
                AppButton(enabled = operation == null, style = AppButtonStyle.Plain, onClick = {
                    if (operation == null) {
                        operation = StatisticsOperation.Import
                        job = scope.launch {
                            try {
                                service.import(pending)
                                SharedFlowCentre.toastText.emit(ToastText.Success(locale.statisticsImportSuccess))
                            } catch (error: Exception) {
                                report(error)
                            } finally {
                                prepared = null
                                operation = null
                            }
                        }
                    }
                }) { AppText(locale.confirm) }
            },
            dismissButton = {
                AppButton(enabled = operation == null, onClick = { prepared = null }, style = AppButtonStyle.Plain) {
                    AppText(locale.cancel)
                }
            },
        )
    }
}

private fun StatisticsTransferFailure.message(locale: LocaleStrings): String = when (this) {
    StatisticsTransferFailure.InvalidFile -> locale.statisticsInvalidFile
    StatisticsTransferFailure.WrongAccount -> locale.statisticsWrongAccount
    StatisticsTransferFailure.UnsupportedVersion -> locale.statisticsUnsupportedVersion
    StatisticsTransferFailure.Empty -> locale.statisticsEmpty
    StatisticsTransferFailure.SessionChanged -> locale.statisticsSessionChanged
    StatisticsTransferFailure.Io -> locale.statisticsIoFailed
}
