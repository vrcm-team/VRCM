package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.ui.graphics.ImageBitmap

internal actual suspend fun decodeQrBitmap(bitmap: ImageBitmap): String? =
    error("QR image scanning is unavailable on desktop")
