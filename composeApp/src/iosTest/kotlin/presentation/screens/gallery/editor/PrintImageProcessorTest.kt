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
    fun portraitImageIsCenteredWithOpaqueWhiteSidePadding() = runBlocking {
        val codec = IosPlatformImageCodec()
        val source = ImageBitmap(width = 9, height = 16, hasAlpha = false)
        Canvas(source).drawRect(
            rect = Rect(0f, 0f, 9f, 16f),
            paint = Paint().apply { color = Color.Red },
        )
        val sourceBytes = try {
            codec.encodePng(source)
        } finally {
            releasePlatformImageBitmap(source)
        }

        val rendered = DefaultPrintImageProcessor(codec).render(
            source = SelectedImage("portrait.png", sourceBytes),
            originalSize = ImageSize(9, 16),
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

        assertOpaqueWhite(pixels[64, 609])
        assertOpaqueRed(pixels[1_024, 609])
        assertOpaqueWhite(pixels[1_983, 609])
    }

    private fun assertOpaqueWhite(color: Color) {
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
    }

    private fun assertOpaqueRed(color: Color) {
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0f, color.green, 0.01f)
        assertEquals(0f, color.blue, 0.01f)
    }
}
