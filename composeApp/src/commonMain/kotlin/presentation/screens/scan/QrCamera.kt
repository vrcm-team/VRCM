package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal expect val qrScanningSupported: Boolean

/** Keeps system controls legible over the camera and restores the previous appearance on exit. */
@Composable
internal expect fun QrScannerAppearance()

/** Owns camera permission and preview resources for the lifetime of this composition. */
@Composable
internal expect fun QrCamera(modifier: Modifier, onScanned: (String) -> Unit, onFailure: () -> Unit)
