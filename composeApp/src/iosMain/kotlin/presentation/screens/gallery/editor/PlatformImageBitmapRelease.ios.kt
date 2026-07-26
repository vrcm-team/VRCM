package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap

internal actual fun releasePlatformImageBitmap(bitmap: ImageBitmap) {
    bitmap.asSkiaBitmap().close()
}
