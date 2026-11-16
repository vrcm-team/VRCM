package io.github.vrcmteam.vrcm.presentation.screens.scan

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal actual suspend fun decodeQrBitmap(bitmap: ImageBitmap): List<QrDetection> {
    currentCoroutineContext().ensureActive()
    val image = bitmap.asAndroidBitmap()
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    return decodeQrSource(RGBLuminanceSource(image.width, image.height, pixels))
        .map { it.detection(image.width, image.height) }
}

/** Camera and album use the same multi-code reader, including mixed light/dark codes. */
internal fun decodeQrSource(source: LuminanceSource): List<Result> {
    val reader = QRCodeMultiReader()
    val results = mutableListOf<Result>()
    for (input in listOf(source, source.invert())) {
        val found = try {
            reader.decodeMultiple(BinaryBitmap(HybridBinarizer(input)), mapOf(DecodeHintType.TRY_HARDER to true)).toList()
        } catch (_: ReaderException) {
            emptyList()
        } finally {
            reader.reset()
        }
        for (result in found) {
            // The inverse pass may rediscover a code; repeated payloads at different positions stay selectable.
            if (results.none { other ->
                other.text == result.text && other.containsCenterOf(result) && result.containsCenterOf(other)
            }) results += result
        }
    }
    return results
}

/** Two passes describe one physical code when their centers lie inside each other's finder footprint. */
private fun Result.containsCenterOf(other: Result): Boolean {
    val points = resultPoints
    val target = other.resultPoints
    if (points.size < 3 || target.size < 3) return false
    val origin = points[1]
    val ax = points[0].x - origin.x
    val ay = points[0].y - origin.y
    val bx = points[2].x - origin.x
    val by = points[2].y - origin.y
    val px = (target[0].x + target[2].x) / 2 - origin.x
    val py = (target[0].y + target[2].y) / 2 - origin.y
    val determinant = ax * by - ay * bx
    if (determinant == 0f) return false
    return (px * by - py * bx) / determinant in 0f..1f &&
        (ax * py - ay * px) / determinant in 0f..1f
}

internal fun Result.detection(width: Int, height: Int): QrDetection {
    val points = resultPoints
    // ZXing reports bottom-left, top-left, top-right, then an optional alignment pattern.
    val x = if (points.size >= 3) (points[0].x + points[2].x) / 2f / width else 0.5f
    val y = if (points.size >= 3) (points[0].y + points[2].y) / 2f / height else 0.5f
    return QrDetection(text, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
