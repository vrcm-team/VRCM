package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.graphics.ImageBitmap

interface PlatformImageCodec {
    suspend fun decode(bytes: ByteArray, request: DecodeRequest): DecodedImage

    suspend fun renderCrop(bytes: ByteArray, request: CropRenderRequest): ImageBitmap

    suspend fun encodePng(bitmap: ImageBitmap): ByteArray
}
