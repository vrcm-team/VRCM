package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodeRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.releasePlatformImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val QR_IMAGE_MAX_BYTES = 20L * 1024 * 1024

internal fun interface QrImageDecoder {
    /** Null means the image is readable but contains no recognizable QR code. */
    suspend fun decode(bytes: ByteArray): String?
}

/** Uses the existing orientation-aware codec and bounds pixel memory before QR detection. */
internal class DefaultQrImageDecoder(private val codec: PlatformImageCodec) : QrImageDecoder {
    override suspend fun decode(bytes: ByteArray): String? = withContext(Dispatchers.Default) {
        require(bytes.size <= QR_IMAGE_MAX_BYTES)
        val image = codec.decode(bytes, DecodeRequest(maxDimension = 2048, maxPixels = 2048L * 2048))
        try {
            decodeQrBitmap(image.bitmap)
        } finally {
            releasePlatformImageBitmap(image.bitmap)
        }
    }
}

internal expect suspend fun decodeQrBitmap(bitmap: ImageBitmap): String?
