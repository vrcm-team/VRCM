package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.DecodeRequest
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PlatformImageCodec
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.releasePlatformImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val QR_IMAGE_MAX_BYTES = 20L * 1024 * 1024

/** Position is normalized to the upright source image, before fitting it into the viewport. */
internal data class QrDetection(val value: String, val x: Float, val y: Float)

/** A preview is retained only when the user needs to choose between several detections. */
internal data class QrScanBatch(
    val codes: List<QrDetection>,
    val preview: ByteArray? = null,
    val width: Int = 1,
    val height: Int = 1,
)

internal fun interface QrImageDecoder {
    suspend fun decode(bytes: ByteArray): QrScanBatch
}

/** Uses the existing orientation-aware codec and bounds pixel memory before QR detection. */
internal class DefaultQrImageDecoder(private val codec: PlatformImageCodec) : QrImageDecoder {
    override suspend fun decode(bytes: ByteArray): QrScanBatch = withContext(Dispatchers.Default) {
        require(bytes.size <= QR_IMAGE_MAX_BYTES)
        val image = codec.decode(bytes, DecodeRequest(maxDimension = 2048, maxPixels = 2048L * 2048))
        try {
            val codes = decodeQrBitmap(image.bitmap)
            QrScanBatch(codes, if (codes.size > 1) codec.encodePng(image.bitmap) else null,
                image.bitmap.width, image.bitmap.height)
        } finally {
            releasePlatformImageBitmap(image.bitmap)
        }
    }
}

internal expect suspend fun decodeQrBitmap(bitmap: ImageBitmap): List<QrDetection>
