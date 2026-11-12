package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*

/** The iOS shell supplies a child controller; Compose navigation owns its presentation and lifetime. */
@OptIn(ExperimentalForeignApi::class)
interface QrScannerPresenter {
    fun create(title: String, hint: String, backLabel: String, myCodeLabel: String, albumLabel: String,
        chooseHint: String, rescanLabel: String, selectCodeLabel: String, tintArgb: Long,
        onScanned: (String) -> Unit, onMyCode: () -> Unit, onAlbum: () -> Unit,
        onDismiss: () -> Unit, onFailure: () -> Unit): UIViewController
    fun stop(controller: UIViewController)
}

object QrScannerBridge {
    var presenter: QrScannerPresenter? = null
}

internal actual val qrScanningSupported = true

@Composable
@OptIn(ExperimentalForeignApi::class)
internal actual fun QrScannerAppearance() {
    val controller = LocalUIViewController.current
    DisposableEffect(controller) {
        val previousStyle = controller.overrideUserInterfaceStyle
        controller.overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
        controller.setNeedsStatusBarAppearanceUpdate()
        onDispose {
            controller.overrideUserInterfaceStyle = previousStyle
            controller.setNeedsStatusBarAppearanceUpdate()
        }
    }
}

@Composable
@OptIn(ExperimentalForeignApi::class)
internal actual fun QrCamera(modifier: Modifier, onScanned: (String) -> Unit, onFailure: () -> Unit,
    onMyCode: () -> Unit, onAlbum: () -> Unit, onCandidates: (QrScanBatch) -> Unit,
    controls: @Composable () -> Unit) {
    val navigator = io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
    val title = strings.scanMode
    val hint = strings.scanQrHint
    val backLabel = strings.back
    val myCodeLabel = strings.scanMyQrCode
    val albumLabel = strings.scanAlbum
    val chooseHint = strings.scanChooseCode
    val rescanLabel = strings.scanAgain
    val selectCodeLabel = strings.scanSelectCode
    val tintArgb = AppTheme.colors.tint.toArgb().toLong()
    val scanned by rememberUpdatedState(onScanned)
    val failure by rememberUpdatedState(onFailure)
    val myCode by rememberUpdatedState(onMyCode)
    val album by rememberUpdatedState(onAlbum)
    val presenter = QrScannerBridge.presenter
    if (presenter == null) {
        controls()
        LaunchedEffect(Unit) { failure() }
    } else {
        UIKitViewController(
            factory = {
                presenter.create(title, hint, backLabel, myCodeLabel, albumLabel,
                    chooseHint, rescanLabel, selectCodeLabel, tintArgb,
                    { scanned(it) }, { myCode() }, { album() }, { navigator.pop() }, { failure() })
            },
            modifier = modifier,
            onRelease = { presenter.stop(it) },
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
            ),
        )
    }
}
