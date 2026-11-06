package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/** The iOS shell owns AVFoundation and presents its camera above the Compose controller. */
interface QrScannerPresenter {
    fun start(title: String, hint: String, backLabel: String, tintArgb: Long, onScanned: (String) -> Unit, onDismiss: () -> Unit, onFailure: () -> Unit)
    fun stop()
}

object QrScannerBridge {
    var presenter: QrScannerPresenter? = null
}

internal actual val qrScanningSupported = true

@Composable
internal actual fun QrScannerAppearance() = Unit

@Composable
internal actual fun QrCamera(modifier: Modifier, onScanned: (String) -> Unit, onFailure: () -> Unit) {
    val navigator = io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
    val title = strings.scanQrCode
    val hint = strings.scanQrHint
    val backLabel = strings.back
    val tintArgb = AppTheme.colors.tint.toArgb().toLong()
    val scanned by rememberUpdatedState(onScanned)
    val failure by rememberUpdatedState(onFailure)
    DisposableEffect(Unit) {
        val presenter = QrScannerBridge.presenter
        if (presenter == null) failure() else presenter.start(title, hint, backLabel, tintArgb, { scanned(it) }, { navigator.pop() }, { failure() })
        onDispose { presenter?.stop() }
    }
}
