package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/**
 * 只当"预览位图句柄"用的空实现。
 *
 * 这些是纯模型测试，只在会话里传递预览引用、从不绘制它，但 `ImageBitmap(w, h)`
 * 在 Android 单元测试的 JVM 上会去调用没有实现的 `android.graphics.Bitmap`，
 * 于是同一份 commonTest 在桌面绿、在 Android 红。用接口桩把平台踢出这条路径。
 */
internal fun stubImageBitmap(width: Int, height: Int): ImageBitmap =
    StubImageBitmap(width, height)

private class StubImageBitmap(
    override val width: Int,
    override val height: Int,
) : ImageBitmap {
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = throw UnsupportedOperationException("Stub preview bitmap has no pixels")

    override fun prepareToDraw() = Unit
}
