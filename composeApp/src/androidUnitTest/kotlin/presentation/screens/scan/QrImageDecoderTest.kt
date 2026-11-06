package io.github.vrcmteam.vrcm.presentation.screens.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
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
import kotlin.test.assertNull

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
                assertEquals(payload, decoder.decode(screenshot.png()))
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
            assertNull(decoder.decode(blank.png()))
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
}
