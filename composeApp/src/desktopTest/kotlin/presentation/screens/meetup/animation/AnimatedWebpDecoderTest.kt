package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import androidx.compose.ui.graphics.asSkiaBitmap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

class AnimatedWebpDecoderTest {
    @Test
    fun fixtureStreamsAnimatedFramesWithPositiveDurations() {
        val animation = SkiaAnimatedWebpDecoder().decode(animatedWebpFixture())

        try {
            assertTrue(animation.frameCount > 1)
            repeat(animation.frameCount) { index ->
                assertTrue(animation.durationMillis(index) > 0)
            }

            val streamedFrames = AtomicInteger()
            val finished = CountDownLatch(1)
            val playbackError = AtomicReference<Throwable>()
            animation.start(
                onFrame = { frame ->
                    try {
                        assertTrue(frame.bitmap.asSkiaBitmap().width > 0)
                        assertTrue(frame.bitmap.asSkiaBitmap().height > 0)
                        if (streamedFrames.incrementAndGet() == 2) finished.countDown()
                    } finally {
                        frame.close()
                    }
                },
                onError = {
                    playbackError.compareAndSet(null, it)
                    finished.countDown()
                },
            )
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertNull(playbackError.get())
            // latch 触发后播放仍在继续，只能断言下限。
            assertTrue(streamedFrames.get() >= 2)
            animation.pause()
        } finally {
            animation.close()
        }
    }

    @Test
    fun closingAnimationIsIdempotent() {
        val animation = SkiaAnimatedWebpDecoder().decode(animatedWebpFixture())

        animation.close()
        animation.close()
    }

    @Test
    fun dependentFixtureFrameMatchesSequentialSkiaComposition() {
        val bytes = animatedWebpFixture()
        val expected = sequentialFrameChecksum(bytes, frameIndex = 1)
        val delivered = AtomicReference<Long>()
        val callbackCount = AtomicInteger()
        val secondFrame = CountDownLatch(1)
        val animation = SkiaAnimatedWebpDecoder().decode(bytes)

        try {
            animation.start(
                onFrame = { frame ->
                    try {
                        if (callbackCount.getAndIncrement() == 1) {
                            delivered.set(frameChecksum(frame.bitmap.asSkiaBitmap()))
                            secondFrame.countDown()
                        }
                    } finally {
                        frame.close()
                    }
                },
                onError = {},
            )

            assertTrue(secondFrame.await(5, TimeUnit.SECONDS))
            assertEquals(expected, delivered.get())
        } finally {
            animation.close()
        }
    }

    @Test
    fun truncatedResourceFailsDuringDecode() {
        assertFailsWith<IllegalArgumentException> {
            SkiaAnimatedWebpDecoder().decode(animatedWebpFixture().copyOf(32))
        }
    }

    @Test
    fun callbackFailureReportsOnePlaybackError() {
        val error = CountDownLatch(1)
        val errorCount = AtomicInteger()
        val animation = SkiaAnimatedWebpDecoder().decode(animatedWebpFixture())

        try {
            animation.start(
                onFrame = { throw IllegalStateException("frame consumer failed") },
                onError = {
                    errorCount.incrementAndGet()
                    error.countDown()
                },
            )

            assertTrue(error.await(5, TimeUnit.SECONDS))
            assertEquals(1, errorCount.get())
        } finally {
            animation.close()
        }
    }

    @Test
    fun staleCallbackFailureDoesNotStopReplacementStream() {
        val firstReplacementFrame = CountDownLatch(1)
        val secondReplacementFrame = CountDownLatch(1)
        val replacementErrors = AtomicInteger()
        val replacementFrames = AtomicInteger()
        val animation = SkiaAnimatedWebpDecoder().decode(animatedWebpFixture())

        try {
            animation.start(
                onFrame = { frame ->
                    frame.close()
                    animation.start(
                        onFrame = { replacementFrame ->
                            try {
                                when (replacementFrames.incrementAndGet()) {
                                    1 -> firstReplacementFrame.countDown()
                                    2 -> secondReplacementFrame.countDown()
                                }
                            } finally {
                                replacementFrame.close()
                            }
                        },
                        onError = { replacementErrors.incrementAndGet() },
                    )
                    throw IllegalStateException("old frame consumer failed")
                },
                onError = {},
            )
            assertTrue(firstReplacementFrame.await(5, TimeUnit.SECONDS))
            assertTrue(secondReplacementFrame.await(5, TimeUnit.SECONDS))
            assertEquals(0, replacementErrors.get())
        } finally {
            animation.close()
        }
    }

    @Test
    fun replacementStartWaitsForInFlightFrameCallback() {
        val oldCallbackStarted = CountDownLatch(1)
        val releaseOldCallback = CountDownLatch(1)
        val replacementReturned = CountDownLatch(1)
        val replacementFrame = CountDownLatch(1)
        val animation = SkiaAnimatedWebpDecoder().decode(animatedWebpFixture())

        try {
            animation.start(
                onFrame = { frame ->
                    frame.close()
                    oldCallbackStarted.countDown()
                    assertTrue(releaseOldCallback.await(5, TimeUnit.SECONDS))
                },
                onError = {},
            )
            assertTrue(oldCallbackStarted.await(5, TimeUnit.SECONDS))

            val replacementThread = Thread {
                animation.start(
                    onFrame = { frame ->
                        frame.close()
                        replacementFrame.countDown()
                    },
                    onError = {},
                )
                replacementReturned.countDown()
            }
            replacementThread.start()

            assertFalse(replacementReturned.await(250, TimeUnit.MILLISECONDS))
            releaseOldCallback.countDown()

            assertTrue(replacementReturned.await(5, TimeUnit.SECONDS))
            assertTrue(replacementFrame.await(5, TimeUnit.SECONDS))
            replacementThread.join(5_000)
            assertFalse(replacementThread.isAlive)
        } finally {
            animation.close()
        }
    }

    private fun animatedWebpFixture(): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/meetup/animated.webp"),
    ).use { it.readBytes() }

    private fun sequentialFrameChecksum(bytes: ByteArray, frameIndex: Int): Long =
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                val framesInfo = codec.framesInfo
                assertEquals(0, framesInfo[frameIndex].requiredFrame)
                Bitmap().use { bitmap ->
                    check(bitmap.allocPixels(codec.imageInfo))
                    codec.readPixels(bitmap, 0, -1)
                    codec.readPixels(bitmap, frameIndex, framesInfo[frameIndex].requiredFrame)
                    frameChecksum(bitmap)
                }
            }
        }

    private fun frameChecksum(bitmap: Bitmap): Long {
        var checksum = 1L
        for (y in 0 until bitmap.height step 7) {
            for (x in 0 until bitmap.width step 7) {
                checksum = checksum * 31 + bitmap.getColor(x, y).toLong()
            }
        }
        return checksum
    }
}
