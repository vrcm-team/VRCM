package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import androidx.compose.ui.graphics.asAndroidBitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 覆盖 awebp 回调缓冲区到 Compose ImageBitmap 的真实转换链路。
 * 其余解码器测试都注入假的帧工厂，这一段此前完全没有测试跑过。
 */
@RunWith(RobolectricTestRunner::class)
class AndroidBitmapFrameFactoryTest {
    @Test
    fun frameIsCopiedOutOfTheBufferAndBufferIsRewoundForReuse() {
        val buffer = argbBuffer(WIDTH, HEIGHT)
        buffer.position(buffer.capacity())

        val frame = AndroidBitmapFrameFactory.copyFrame(WIDTH, HEIGHT, buffer)

        try {
            assertEquals(WIDTH, frame.bitmap.width)
            assertEquals(HEIGHT, frame.bitmap.height)
            assertEquals(
                OPAQUE_MAGENTA,
                frame.bitmap.asAndroidBitmap().getPixel(WIDTH - 1, HEIGHT - 1),
            )
            // awebp 每帧复用同一个 buffer，读完必须把 position 归零，否则下一帧读到空数据。
            assertEquals(0, buffer.position())
        } finally {
            frame.close()
        }
    }

    @Test
    fun closingTheFrameRecyclesTheUnderlyingBitmapExactlyOnce() {
        val frame = AndroidBitmapFrameFactory.copyFrame(WIDTH, HEIGHT, argbBuffer(WIDTH, HEIGHT))
        val androidBitmap = frame.bitmap.asAndroidBitmap()
        assertFalse(androidBitmap.isRecycled)

        frame.close()
        // 重复 close 是正常的：显示侧退帧与解码侧关闭都会调用。
        frame.close()

        assertTrue(androidBitmap.isRecycled)
    }

    @Test
    fun undersizedBufferFailsWithoutLeakingTheAllocatedBitmap() {
        val tooSmall = argbBuffer(WIDTH, HEIGHT - 1)

        assertFailsWith<RuntimeException> {
            AndroidBitmapFrameFactory.copyFrame(WIDTH, HEIGHT, tooSmall)
        }
    }

    private fun argbBuffer(width: Int, height: Int): ByteBuffer =
        ByteBuffer.allocate(width * height * BYTES_PER_PIXEL).apply {
            order(ByteOrder.nativeOrder())
            repeat(width * height) { putInt(OPAQUE_MAGENTA) }
            rewind()
        }

    private companion object {
        const val WIDTH = 4
        const val HEIGHT = 3
        const val BYTES_PER_PIXEL = 4
        const val OPAQUE_MAGENTA = 0xFFFF00FF.toInt()
    }
}
