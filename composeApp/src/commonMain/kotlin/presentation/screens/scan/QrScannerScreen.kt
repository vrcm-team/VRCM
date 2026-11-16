package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.AppRoute
import io.github.vrcmteam.vrcm.presentation.navigation.HandleBackNavigation
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.OfficialLinkInbox
import io.github.vrcmteam.vrcm.service.ScanLaunchInbox
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.readBoundedBytes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal val LocalScannedLinkHandler = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
internal fun ScanLaunchHandler(inbox: ScanLaunchInbox, links: OfficialLinkInbox, content: @Composable (Boolean) -> Unit) {
    val navigator = currentNavigator
    val pending by inbox.pending.collectAsState()
    val authenticated = navigator.items.any { it is HomeScreen }
    LaunchedEffect(pending, authenticated) {
        if (pending && authenticated) {
            if (qrScanningSupported) navigator push QrScannerScreen
            inbox.consume()
        }
    }
    CompositionLocalProvider(LocalScannedLinkHandler provides links::submit) {
        content(pending || navigator.items.any { it is QrScannerScreen })
    }
}

@Serializable
object QrScannerScreen : AppRoute {
    @Composable
    @OptIn(ExperimentalTime::class)
    override fun Content() {
        val navigator = currentNavigator
        val model: QrScannerModel = koinViewModel()
        val state by model.state.collectAsState()
        val locale = strings
        val openLink = LocalScannedLinkHandler.current
        val busy = state is QrScanState.Approving
        val loadingInfo = state is QrScanState.LoadingDeviceInfo
        val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
            if (file == null) model.imageSelectionCancelled()
            else model.readImage { file.readBoundedBytes(QR_IMAGE_MAX_BYTES) }
        }
        val showMyCode: () -> Unit = {
            model.showMyCode()?.let { navigator push MyQrCodeScreen(it) }
        }
        QrScannerAppearance()
        HandleBackNavigation(busy) { }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            if (navigator.lastItem == QrScannerScreen) model.resumeFromMyCode()
        }
        LaunchedEffect(state) {
            if (state == QrScanState.ChoosingImage) imagePicker.launch()
        }

        LaunchedEffect(state) {
            (state as? QrScanState.ContentLink)?.let {
                navigator.pop()
                openLink(it.url)
            }
        }
        QrScannerViewfinder(
            scanning = state == QrScanState.Scanning,
            canGoBack = !busy,
            onBack = { navigator.pop() },
            onMyCode = showMyCode,
            onAlbum = model::chooseImage,
            readingImage = state == QrScanState.ReadingImage,
            selection = (state as? QrScanState.Selecting)?.let { current ->
                { QrCodeSelection(current.batch, model::selectCode) }
            },
            onRescan = if (state is QrScanState.Selecting) model::scanAgain else null,
        ) { controls ->
            if (state == QrScanState.Scanning && qrScanningSupported) {
                QrCamera(Modifier.fillMaxSize(), model::onScanned, model::cameraFailed,
                    showMyCode, model::chooseImage, model::onCandidates, controls)
            } else controls()
        }

        if (loadingInfo || state is QrScanState.DeviceConfirmation || state is QrScanState.Approving ||
            state is QrScanState.Failure || state == QrScanState.Approved
        ) {
            val confirmation = when (val current = state) {
                is QrScanState.DeviceConfirmation -> current
                is QrScanState.Approving -> current.confirmation
                is QrScanState.Failure -> current.confirmation
                else -> null
            }
            val failure = state as? QrScanState.Failure
            AppAlert(
                onDismissRequest = { if (!busy) navigator.pop() },
                icon = { AppIcon(AppIcons.QrCodeScanner, null) },
                title = { AppText(if (loadingInfo || confirmation != null || failure?.reason == ScanFailure.DeviceInfo ||
                    state == QrScanState.Approved) locale.deviceAuthTitle else locale.scanQrCode) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (confirmation != null) {
                            AppText(locale.deviceAuthMessage)
                            val info = confirmation.info
                            val createdAt = remember(info.createdAt) {
                                Instant.fromEpochMilliseconds(info.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
                                    .let { "${it.date} ${it.time.toString().substringBefore('.')}" }
                            }
                            val details = listOf(
                                locale.deviceAuthCreatedAt to createdAt,
                                locale.deviceAuthCountry to info.geolocation?.country,
                                locale.deviceAuthRegion to (info.geolocation?.region?.takeIf { it.isNotBlank() }
                                    ?: info.geolocation?.regionCode),
                                locale.deviceAuthCity to info.geolocation?.city,
                            )
                            details.forEach { (label, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    AppText(label, style = AppTheme.type.footnote, color = AppTheme.colors.secondaryLabel)
                                    AppText(value?.takeIf { it.isNotBlank() } ?: locale.unknown)
                                }
                            }
                        }
                        if (loadingInfo) AppText(locale.deviceAuthInfoLoading)
                        if (busy || loadingInfo) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppActivityIndicator() }
                        }
                        if (state == QrScanState.Approved) AppText(locale.deviceAuthApproved)
                        failure?.let {
                            AppText(
                                when (it.reason) {
                                    ScanFailure.Camera -> locale.scanCameraFailed
                                    ScanFailure.ImageRead -> locale.scanImageFailed
                                    ScanFailure.ImageNoCode -> locale.scanImageNoCode
                                    ScanFailure.UnsupportedCode -> locale.scanUnsupportedCode
                                    ScanFailure.DeviceInfo -> locale.deviceAuthInfoFailed
                                    ScanFailure.Approval -> locale.deviceAuthFailed
                                    ScanFailure.Expired -> locale.deviceAuthExpired
                                    ScanFailure.SessionChanged -> locale.deviceAuthSessionChanged
                                },
                                color = AppTheme.colors.destructive,
                            )
                        }
                    }
                },
                confirmButton = {
                    AppButton(
                        enabled = !busy && !loadingInfo,
                        style = AppButtonStyle.Prominent,
                        onClick = {
                            when {
                                failure?.reason == ScanFailure.DeviceInfo -> model.retryDeviceInfo()
                                confirmation != null -> model.approve()
                                failure?.reason == ScanFailure.Camera -> model.chooseImage()
                                state == QrScanState.Approved -> navigator.pop()
                                else -> model.scanAgain()
                            }
                        },
                    ) {
                        AppText(when {
                            loadingInfo -> locale.deviceAuthApprove
                            busy -> locale.loading
                            failure?.reason == ScanFailure.DeviceInfo -> locale.retry
                            confirmation != null -> if (failure != null) locale.retry else locale.deviceAuthApprove
                            failure?.reason == ScanFailure.Camera -> locale.scanAlbum
                            state == QrScanState.Approved -> locale.confirm
                            else -> locale.scanAgain
                        })
                    }
                },
                dismissButton = if (state == QrScanState.Approved) null else {
                    {
                        AppButton(onClick = { navigator.pop() }, enabled = !busy, style = AppButtonStyle.Plain) {
                            AppText(locale.cancel)
                        }
                    }
                },
            )
        }
    }
}
