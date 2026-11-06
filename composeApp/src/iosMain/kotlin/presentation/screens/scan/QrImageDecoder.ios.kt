package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun decodeQrBitmap(bitmap: ImageBitmap): String? {
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes }
    } ?: error("Unable to encode the QR image")
    val image = CIImage.imageWithData(bytes.toNSData()) ?: error("Unable to read the QR image")
    val detector = CIDetector.detectorOfType(
        CIDetectorTypeQRCode,
        context = null,
        options = mapOf(CIDetectorAccuracy to CIDetectorAccuracyHigh),
    ) ?: error("QR detector unavailable")
    return detector.featuresInImage(image).filterIsInstance<CIQRCodeFeature>().firstOrNull()?.messageString
}
