package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PrintImageProcessorTest : PrintImageProcessorContractTest() {
    @Test
    fun realCodecProducesOpaqueWhiteBackedPngFromTransparentContent() = runBlocking {
        val codec = DesktopPlatformImageCodec()
        val source = ImageBitmap(width = 16, height = 9, hasAlpha = true)
        Canvas(source).drawRect(
            rect = Rect(0f, 0f, 16f, 9f),
            paint = Paint().apply {
                color = Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f)
            },
        )
        val sourceBytes = try {
            codec.encodePng(source)
        } finally {
            releasePlatformImageBitmap(source)
        }

        val rendered = DefaultPrintImageProcessor(codec).render(
            source = SelectedImage("transparent.png", sourceBytes),
            originalSize = ImageSize(16, 9),
            transform = CropTransform(),
        ).getOrThrow()
        val decoded = codec.decode(
            rendered,
            DecodeRequest(maxDimension = 2_048, maxPixels = 4_000_000L),
        )
        val pixels = try {
            decoded.bitmap.toPixelMap()
        } finally {
            releasePlatformImageBitmap(decoded.bitmap)
        }

        assertEquals(ImageSize(2_048, 1_440), decoded.originalSize)
        assertOpaqueWhite(pixels[0, 0])
        assertOpaqueWhite(pixels[63, 69])
        assertOpaqueWhite(pixels[64, 68])
        assertOpaqueBlendedRed(pixels[64, 69])
        assertOpaqueBlendedRed(pixels[1_983, 1_148])
        assertOpaqueWhite(pixels[1_984, 1_148])
        assertOpaqueWhite(pixels[1_983, 1_149])
    }

    private fun assertOpaqueWhite(color: Color) {
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
    }

    private fun assertOpaqueBlendedRed(color: Color) {
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.5f, color.green, 0.02f)
        assertEquals(0.5f, color.blue, 0.02f)
    }
}
