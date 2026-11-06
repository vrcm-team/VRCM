package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal actual suspend fun decodeQrBitmap(bitmap: ImageBitmap): String? {
    val image = bitmap.asAndroidBitmap()
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    val luminance = RGBLuminanceSource(image.width, image.height, pixels)
    val reader = QRCodeReader()
    for (source in listOf(luminance, luminance.invert())) {
        currentCoroutineContext().ensureActive()
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (_: ReaderException) {
            // Dark screenshots can contain light-on-dark QR codes; try the inverse as well.
        } finally {
            reader.reset()
        }
    }
    return null
}
