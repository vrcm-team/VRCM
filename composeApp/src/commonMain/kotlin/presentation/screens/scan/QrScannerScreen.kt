package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
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
    override fun Content() {
        val navigator = currentNavigator
        val model: QrScannerModel = koinViewModel()
        val state by model.state.collectAsState()
        val locale = strings
        val openLink = LocalScannedLinkHandler.current
        val busy = state is QrScanState.Approving
        QrScannerAppearance()
        HandleBackNavigation(busy) { }

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
        ) {
            if (state == QrScanState.Scanning && qrScanningSupported) {
                QrCamera(Modifier.fillMaxSize(), model::onScanned, model::cameraFailed)
            }
        }

        if (state != QrScanState.Scanning && state !is QrScanState.ContentLink) {
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
                title = { AppText(if (confirmation != null || state == QrScanState.Approved) locale.deviceAuthTitle else locale.scanQrCode) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (confirmation != null) {
                            AppText(locale.deviceAuthMessage)
                            AppText(locale.deviceAuthCode.replace("%s", confirmation.code), style = AppTheme.type.title2)
                        }
                        if (busy) AppActivityIndicator()
                        if (state == QrScanState.Approved) AppText(locale.deviceAuthApproved)
                        failure?.let {
                            AppText(
                                when (it.reason) {
                                    ScanFailure.Camera -> locale.scanCameraFailed
                                    ScanFailure.UnsupportedCode -> locale.scanUnsupportedCode
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
                        enabled = !busy,
                        style = AppButtonStyle.Prominent,
                        onClick = {
                            when {
                                confirmation != null -> model.approve()
                                state == QrScanState.Approved -> navigator.pop()
                                else -> model.scanAgain()
                            }
                        },
                    ) {
                        AppText(when {
                            busy -> locale.loading
                            confirmation != null -> if (failure != null) locale.retry else locale.deviceAuthApprove
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
