package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

internal actual fun releasePlatformImageBitmap(bitmap: ImageBitmap) {
    val androidBitmap = bitmap.asAndroidBitmap()
    if (!androidBitmap.isRecycled) androidBitmap.recycle()
}
