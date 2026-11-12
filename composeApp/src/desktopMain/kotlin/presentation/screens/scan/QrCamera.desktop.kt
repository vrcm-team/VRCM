package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

internal actual val qrScanningSupported = false

@Composable
internal actual fun QrScannerAppearance() = Unit

@Composable
internal actual fun QrCamera(modifier: Modifier, onScanned: (String) -> Unit, onFailure: () -> Unit,
    onMyCode: () -> Unit, onAlbum: () -> Unit, onCandidates: (QrScanBatch) -> Unit,
    controls: @Composable () -> Unit) {
    controls()
    LaunchedEffect(Unit) { onFailure() }
}
