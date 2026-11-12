package io.github.vrcmteam.vrcm.presentation.screens.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.AndroidPlatformImageCodec
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrImageDecoderTest {
    private val decoder = DefaultQrImageDecoder(AndroidPlatformImageCodec())

    @Test
    fun readsQrFromLargeScreenshotsIncludingDarkModeImages() = runBlocking {
        val payload = "https://vrchat.com/home/device?code=F36XWSHN&source=app"
        for (inverted in listOf(false, true)) {
            val screenshot = Bitmap.createBitmap(3600, 2400, Bitmap.Config.ARGB_8888)
            try {
                screenshot.eraseColor(if (inverted) Color.BLACK else Color.WHITE)
                val code = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 800, 800)
                val pixels = IntArray(800 * 800) { index ->
                    if (code[index % 800, index / 800] != inverted) Color.BLACK else Color.WHITE
                }
                screenshot.setPixels(pixels, 0, 800, 1400, 800, 800, 800)
                assertEquals(payload, decoder.decode(screenshot.png()).codes.single().value)
            } finally {
                screenshot.recycle()
            }
        }
    }

    @Test
    fun distinguishesAnImageWithoutQrFromAnUnreadableImage() = runBlocking {
        val blank = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        try {
            blank.eraseColor(Color.WHITE)
            assertTrue(decoder.decode(blank.png()).codes.isEmpty())
        } finally {
            blank.recycle()
        }
        assertFailsWith<Exception> { decoder.decode(byteArrayOf(0, 1, 2)) }
        Unit
    }

    private fun Bitmap.png(): ByteArray = ByteArrayOutputStream().use { stream ->
        check(compress(Bitmap.CompressFormat.PNG, 100, stream))
        stream.toByteArray()
    }

    @Test
    fun keepsAdjacentCopiesOfTheSameCodeInLargeCameraFrames() {
        val payload = "https://vrchat.com/home/user/usr_example"
        val width = 2400
        val height = 1600
        val pixels = IntArray(width * height) { Color.WHITE }
        val qr = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 60, 60)
        for (left in listOf(1000, 1060)) {
            for (y in 0 until 60) for (x in 0 until 60) {
                pixels[(600 + y) * width + left + x] = if (qr[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val results = decodeQrSource(RGBLuminanceSource(width, height, pixels))
        assertEquals(listOf(payload, payload), results.map { it.text })
    }

    @Test
    fun preservesAllPositionsIncludingRepeatedPayloadsAndMixedPolarityAfterSampling() = runBlocking {
        val profile = "https://vrchat.com/home/user/usr_owner"
        val device = "https://vrchat.com/home/device?code=F36XWSHN"
        val screenshot = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        try {
            screenshot.eraseColor(Color.WHITE)
            listOf(Triple(profile, 200 to 200, false), Triple(device, 1600 to 200, true),
                Triple(profile, 1600 to 1000, false)).forEach { (payload, position, inverted) ->
                val qr = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 400, 400)
                val pixels = IntArray(400 * 400) { index ->
                    if (qr[index % 400, index / 400] != inverted) Color.BLACK else Color.WHITE
                }
                screenshot.setPixels(pixels, 0, 400, position.first, position.second, 400, 400)
            }
            val result = decoder.decode(screenshot.png())
            assertEquals(3, result.codes.size)
            assertEquals(2, result.codes.count { it.value == profile })
            val login = result.codes.single { it.value == device }
            assertTrue(login.x in 0.72f..0.78f && login.y in 0.22f..0.28f)
            assertTrue(result.codes.any { it.value == profile && it.x in 0.13f..0.20f && it.y in 0.22f..0.28f })
            assertTrue(result.codes.any { it.value == profile && it.x in 0.72f..0.78f && it.y in 0.72f..0.78f })
            assertTrue(result.preview?.isNotEmpty() == true)
            assertTrue(result.width <= 2048 && result.height <= 2048)
        } finally {
            screenshot.recycle()
        }
    }
}
