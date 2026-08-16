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
    fun downloadedPrintUsesTheInnerPhotoForPreviewAndSourceBytes() = runBlocking {
        val codec = DesktopPlatformImageCodec()
        val downloadedPrint = ImageBitmap(width = 2_048, height = 1_440, hasAlpha = false)
        Canvas(downloadedPrint).apply {
            drawRect(
                rect = Rect(0f, 0f, 2_048f, 1_440f),
                paint = Paint().apply { color = Color.White },
            )
            drawRect(
                rect = Rect(64f, 69f, 1_984f, 1_149f),
                paint = Paint().apply { color = Color.Red },
            )
        }
        val sourceBytes = try {
            codec.encodePng(downloadedPrint)
        } finally {
            releasePlatformImageBitmap(downloadedPrint)
        }

        val preparedSource = DefaultPrintImageProcessor(codec)
            .preparePrint(SelectedImage("downloaded-print.png", sourceBytes))
            .getOrThrow()
        val previewPixels = try {
            preparedSource.prepared.preview.toPixelMap()
        } finally {
            releasePlatformImageBitmap(preparedSource.prepared.preview)
        }
        val decodedSource = codec.decode(
            preparedSource.source.bytes,
            DecodeRequest(maxDimension = 2_048, maxPixels = 4_000_000L),
        )
        val sourcePixels = try {
            decodedSource.bitmap.toPixelMap()
        } finally {
            releasePlatformImageBitmap(decodedSource.bitmap)
        }

        assertEquals(ImageSize(1_920, 1_080), preparedSource.prepared.originalSize)
        assertEquals(ImageSize(1_920, 1_080), decodedSource.originalSize)
        listOf(previewPixels, sourcePixels).forEach { pixels ->
            assertOpaqueRed(pixels[0, 0])
            assertOpaqueRed(pixels[1_919, 0])
            assertOpaqueRed(pixels[0, 1_079])
            assertOpaqueRed(pixels[1_919, 1_079])
        }
    }

    @Test
    fun portraitImageIsCenteredWithOpaqueWhiteSidePadding() = runBlocking {
        val codec = DesktopPlatformImageCodec()
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

    @Test
    fun galleryRenderPreservesTransparentPixels() = runBlocking {
        val codec = DesktopPlatformImageCodec()
        val source = ImageBitmap(width = 16, height = 9, hasAlpha = true)
        Canvas(source).drawRect(
            rect = Rect(8f, 0f, 16f, 9f),
            paint = Paint().apply {
                color = Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f)
            },
        )
        val sourceBytes = try {
            codec.encodePng(source)
        } finally {
            releasePlatformImageBitmap(source)
        }
        val sourceSpec = PrintCanvasSpec(
            canvasWidth = 16,
            canvasHeight = 9,
            contentWidth = 16,
            contentHeight = 9,
            contentOffsetX = 0,
            contentOffsetY = 0,
        )

        val rendered = DefaultPrintImageProcessor(codec, sourceSpec).render(
            source = SelectedImage("transparent.png", sourceBytes),
            originalSize = ImageSize(16, 9),
            transform = CropTransform(),
            background = CanvasBackground.Transparent,
        ).getOrThrow()
        val decoded = codec.decode(
            rendered,
            DecodeRequest(maxDimension = 16, maxPixels = 144L),
        )
        val pixels = try {
            decoded.bitmap.toPixelMap()
        } finally {
            releasePlatformImageBitmap(decoded.bitmap)
        }

        assertEquals(0f, pixels[2, 4].alpha, 0.01f)
        assertEquals(0.5f, pixels[12, 4].alpha, 0.02f)
        assertEquals(1f, pixels[12, 4].red, 0.01f)
    }

    @Test
    fun galleryWhiteBackgroundIsCompositedIntoTheCropBitmap() = runBlocking {
        val codec = DesktopPlatformImageCodec()
        val source = ImageBitmap(width = 16, height = 9, hasAlpha = true)
        Canvas(source).drawRect(
            rect = Rect(8f, 0f, 16f, 9f),
            paint = Paint().apply {
                color = Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f)
            },
        )
        val sourceBytes = try {
            codec.encodePng(source)
        } finally {
            releasePlatformImageBitmap(source)
        }
        val sourceSpec = PrintCanvasSpec(
            canvasWidth = 16,
            canvasHeight = 9,
            contentWidth = 16,
            contentHeight = 9,
            contentOffsetX = 0,
            contentOffsetY = 0,
        )

        val rendered = DefaultPrintImageProcessor(codec, sourceSpec).render(
            source = SelectedImage("transparent.png", sourceBytes),
            originalSize = ImageSize(16, 9),
            transform = CropTransform(),
            background = CanvasBackground.White,
        ).getOrThrow()
        val decoded = codec.decode(
            rendered,
            DecodeRequest(maxDimension = 16, maxPixels = 144L),
        )
        val pixels = try {
            decoded.bitmap.toPixelMap()
        } finally {
            releasePlatformImageBitmap(decoded.bitmap)
        }

        assertOpaqueWhite(pixels[2, 4])
        assertOpaqueBlendedRed(pixels[12, 4])
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

    private fun assertOpaqueBlendedRed(color: Color) {
        assertEquals(1f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.5f, color.green, 0.02f)
        assertEquals(0.5f, color.blue, 0.02f)
    }
}
