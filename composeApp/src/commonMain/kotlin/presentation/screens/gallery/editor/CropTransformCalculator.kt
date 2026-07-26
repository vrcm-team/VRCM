package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import kotlin.math.max

class CropTransformCalculator(
    private val maxZoom: Float = 3f,
) {
    fun geometry(
        source: ImageSize,
        viewport: ImageSize,
        transform: CropTransform,
    ): RenderGeometry {
        require(source.width > 0 && source.height > 0) { "Source dimensions must be positive" }
        require(viewport.width > 0 && viewport.height > 0) { "Viewport dimensions must be positive" }

        val turns = normalizeTurns(transform.quarterTurns)
        val rotatedWidth = if (turns % 2 == 0) source.width.toFloat() else source.height.toFloat()
        val rotatedHeight = if (turns % 2 == 0) source.height.toFloat() else source.width.toFloat()
        val coverScale = max(
            viewport.width / rotatedWidth,
            viewport.height / rotatedHeight,
        )
        val zoom = transform.zoom.coerceIn(1f, maxZoom)
        val imageWidth = rotatedWidth * coverScale * zoom
        val imageHeight = rotatedHeight * coverScale * zoom
        val maxTranslationX = ((imageWidth - viewport.width) / 2f).coerceAtLeast(0f)
        val maxTranslationY = ((imageHeight - viewport.height) / 2f).coerceAtLeast(0f)

        return RenderGeometry(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            translationX = (transform.centerOffsetX * viewport.width)
                .coerceIn(-maxTranslationX, maxTranslationX),
            translationY = (transform.centerOffsetY * viewport.height)
                .coerceIn(-maxTranslationY, maxTranslationY),
            rotationDegrees = turns * 90f,
            scaleXSign = if (transform.flipHorizontal) -1f else 1f,
            scaleYSign = if (transform.flipVertical) -1f else 1f,
        )
    }

    fun transform(
        source: ImageSize,
        viewport: ImageSize,
        current: CropTransform,
        panX: Float,
        panY: Float,
        zoomChange: Float,
    ): CropTransform = clamp(
        source = source,
        viewport = viewport,
        transform = current.copy(
            centerOffsetX = current.centerOffsetX + panX / viewport.width,
            centerOffsetY = current.centerOffsetY + panY / viewport.height,
            zoom = (current.zoom * zoomChange).coerceIn(1f, maxZoom),
            quarterTurns = normalizeTurns(current.quarterTurns),
        ),
    )

    fun rotate(
        source: ImageSize,
        viewport: ImageSize,
        current: CropTransform,
        turns: Int,
    ): CropTransform = clamp(
        source = source,
        viewport = viewport,
        transform = current.copy(quarterTurns = normalizeTurns(current.quarterTurns + turns)),
    )

    fun flipHorizontal(current: CropTransform): CropTransform =
        current.copy(flipHorizontal = !current.flipHorizontal)

    fun flipVertical(current: CropTransform): CropTransform =
        current.copy(flipVertical = !current.flipVertical)

    fun reset(): CropTransform = CropTransform()

    private fun clamp(
        source: ImageSize,
        viewport: ImageSize,
        transform: CropTransform,
    ): CropTransform {
        val geometry = geometry(source, viewport, transform)
        return transform.copy(
            centerOffsetX = geometry.translationX / viewport.width,
            centerOffsetY = geometry.translationY / viewport.height,
            zoom = transform.zoom.coerceIn(1f, maxZoom),
            quarterTurns = normalizeTurns(transform.quarterTurns),
        )
    }

    private fun normalizeTurns(turns: Int): Int = ((turns % 4) + 4) % 4
}
