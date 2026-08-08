package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidAnimatedWebpDecoderTest {
    @Test
    fun animatedFixtureIsRecognizedByAwebp() {
        val animation = AndroidAnimatedWebpDecoder().decode(animatedWebpFixture())

        try {
            assertTrue(animation.frameCount > 1)
            repeat(animation.frameCount) { index ->
                assertTrue(animation.durationMillis(index) > 0)
            }
        } finally {
            animation.close()
        }
    }

    @Test
    fun rejectsFrameDurationsThatWouldBusyLoopAwebpScheduling() {
        assertFailsWith<IllegalArgumentException> {
            AndroidDecodedAnimation(BlockingPlayback(frameDuration = 9), TestFrameFactory())
        }
    }

    @Test
    fun acceptsSchedulableFrameDurationsAndCoercesReportedValue() {
        val animation = AndroidDecodedAnimation(BlockingPlayback(frameDuration = 10), TestFrameFactory())
        try {
            assertEquals(16, animation.durationMillis(0))
        } finally {
            animation.close()
        }
    }

    @Test
    fun secondStartRewindsPlaybackToFirstFrame() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        try {
            animation.start(onFrame = { it.close() }, onError = {})
            assertEquals(0, playback.resetCount.get())
            animation.start(onFrame = { it.close() }, onError = {})
            assertEquals(1, playback.resetCount.get())
            assertTrue(playback.isRunning.get())
        } finally {
            animation.close()
        }
    }

    @Test
    fun closeAfterBlockedStartLeavesPlaybackStopped() {
        val playback = BlockingPlayback(blockStart = true)
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val startFailure = AtomicReference<Throwable>()
        val startThread = Thread {
            try {
                animation.start(onFrame = { it.close() }, onError = {})
            } catch (cause: Throwable) {
                startFailure.set(cause)
            }
        }

        startThread.start()
        assertTrue(playback.startEntered.await(5, TimeUnit.SECONDS))
        val closeFailure = AtomicReference<Throwable>()
        val closeThread = Thread {
            try {
                animation.close()
            } catch (cause: Throwable) {
                closeFailure.set(cause)
            }
        }
        closeThread.start()
        playback.releaseStart.countDown()
        startThread.join(5_000)
        closeThread.join(5_000)

        assertTrue(!startThread.isAlive)
        assertTrue(!closeThread.isAlive)
        assertNull(startFailure.get())
        assertNull(closeFailure.get())
        assertTrue(!playback.isRunning.get())
    }

    @Test
    fun pauseAfterBlockedStartLeavesPlaybackPaused() {
        val playback = BlockingPlayback(blockStart = true)
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val startFailure = AtomicReference<Throwable>()
        val startThread = Thread {
            try {
                animation.start(onFrame = { it.close() }, onError = {})
            } catch (cause: Throwable) {
                startFailure.set(cause)
            }
        }

        startThread.start()
        assertTrue(playback.startEntered.await(5, TimeUnit.SECONDS))
        val pauseFailure = AtomicReference<Throwable>()
        val pauseThread = Thread {
            try {
                animation.pause()
            } catch (cause: Throwable) {
                pauseFailure.set(cause)
            }
        }
        pauseThread.start()
        playback.releaseStart.countDown()
        startThread.join(5_000)
        pauseThread.join(5_000)

        assertTrue(!startThread.isAlive)
        assertTrue(!pauseThread.isAlive)
        assertNull(startFailure.get())
        assertNull(pauseFailure.get())
        assertTrue(!playback.isRunning.get())
        animation.close()
    }

    @Test
    fun closingDuringFrameCopyDiscardsTheFrame() {
        val playback = BlockingPlayback()
        val frames = TestFrameFactory(blockCopy = true)
        val animation = AndroidDecodedAnimation(playback, frames)
        val delivered = AtomicInteger()
        animation.start(
            onFrame = { frame ->
                delivered.incrementAndGet()
                frame.close()
            },
            onError = {},
        )

        val renderFailure = AtomicReference<Throwable>()
        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(frames.copyEntered.await(5, TimeUnit.SECONDS))
        val closeFailure = AtomicReference<Throwable>()
        val closeThread = Thread {
            try {
                animation.close()
            } catch (cause: Throwable) {
                closeFailure.set(cause)
            }
        }
        closeThread.start()
        frames.releaseCopy.countDown()
        renderThread.join(5_000)
        closeThread.join(5_000)

        assertTrue(!renderThread.isAlive)
        assertTrue(!closeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(closeFailure.get())
        assertEquals(0, delivered.get())
        assertEquals(1, frames.closedFrames.get())
    }

    @Test
    fun closeWaitsForInFlightFrameCallback() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val renderFailure = AtomicReference<Throwable>()
        animation.start(
            onFrame = { frame ->
                frame.close()
                callbackStarted.countDown()
                assertTrue(releaseCallback.await(5, TimeUnit.SECONDS))
            },
            onError = {},
        )

        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))

        val closeFailure = AtomicReference<Throwable>()
        val closeThread = Thread {
            try {
                animation.close()
                closeReturned.countDown()
            } catch (cause: Throwable) {
                closeFailure.set(cause)
            }
        }
        closeThread.start()

        assertFalse(closeReturned.await(250, TimeUnit.MILLISECONDS))
        releaseCallback.countDown()

        assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
        renderThread.join(5_000)
        closeThread.join(5_000)
        assertFalse(renderThread.isAlive)
        assertFalse(closeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(closeFailure.get())
    }

    @Test
    fun noOpResumeDoesNotWaitForRunningFrameCallback() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val resumeReturned = CountDownLatch(1)
        val renderFailure = AtomicReference<Throwable>()
        animation.start(
            onFrame = { frame ->
                frame.close()
                callbackStarted.countDown()
                assertTrue(releaseCallback.await(5, TimeUnit.SECONDS))
            },
            onError = {},
        )

        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))

        val resumeFailure = AtomicReference<Throwable>()
        val resumeThread = Thread {
            try {
                animation.resume()
                resumeReturned.countDown()
            } catch (cause: Throwable) {
                resumeFailure.set(cause)
            }
        }
        resumeThread.start()

        assertTrue(resumeReturned.await(250, TimeUnit.MILLISECONDS))
        releaseCallback.countDown()
        renderThread.join(5_000)
        resumeThread.join(5_000)

        assertFalse(renderThread.isAlive)
        assertFalse(resumeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(resumeFailure.get())
        animation.close()
    }

    @Test
    fun resumeWaitsForPausedCallbackBeforeStartingNextGeneration() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val callbackPaused = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val resumeReturned = CountDownLatch(1)
        val renderFailure = AtomicReference<Throwable>()
        animation.start(
            onFrame = { frame ->
                frame.close()
                animation.pause()
                callbackPaused.countDown()
                assertTrue(releaseCallback.await(5, TimeUnit.SECONDS))
            },
            onError = {},
        )

        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(callbackPaused.await(5, TimeUnit.SECONDS))

        val resumeFailure = AtomicReference<Throwable>()
        val resumeThread = Thread {
            try {
                animation.resume()
                resumeReturned.countDown()
            } catch (cause: Throwable) {
                resumeFailure.set(cause)
            }
        }
        resumeThread.start()

        assertFalse(resumeReturned.await(250, TimeUnit.MILLISECONDS))
        releaseCallback.countDown()

        assertTrue(resumeReturned.await(5, TimeUnit.SECONDS))
        renderThread.join(5_000)
        resumeThread.join(5_000)

        assertFalse(renderThread.isAlive)
        assertFalse(resumeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(resumeFailure.get())
        animation.close()
    }

    @Test
    fun closeWaitsForRetiredCallbackWhileReplacementStartIsPending() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val replacementReturned = CountDownLatch(1)
        val replacementFrame = CountDownLatch(1)
        animation.start(
            onFrame = { frame ->
                frame.close()
                callbackStarted.countDown()
                assertTrue(releaseCallback.await(5, TimeUnit.SECONDS))
            },
            onError = {},
        )

        val renderFailure = AtomicReference<Throwable>()
        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(callbackStarted.await(5, TimeUnit.SECONDS))

        val replacementFailure = AtomicReference<Throwable>()
        val replacementThread = Thread {
            try {
                animation.start(
                    onFrame = { frame ->
                        frame.close()
                        replacementFrame.countDown()
                    },
                    onError = {},
                )
                replacementReturned.countDown()
            } catch (cause: Throwable) {
                replacementFailure.set(cause)
            }
        }
        replacementThread.start()
        assertTrue(playback.pauseEntered.await(5, TimeUnit.SECONDS))
        assertFalse(replacementReturned.await(250, TimeUnit.MILLISECONDS))

        val closeFailure = AtomicReference<Throwable>()
        val closeReturned = CountDownLatch(1)
        val closeThread = Thread {
            try {
                animation.close()
                closeReturned.countDown()
            } catch (cause: Throwable) {
                closeFailure.set(cause)
            }
        }
        closeThread.start()

        assertFalse(closeReturned.await(250, TimeUnit.MILLISECONDS))
        releaseCallback.countDown()

        assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
        assertTrue(replacementReturned.await(5, TimeUnit.SECONDS))
        assertFalse(replacementFrame.await(250, TimeUnit.MILLISECONDS))
        renderThread.join(5_000)
        replacementThread.join(5_000)
        closeThread.join(5_000)

        assertFalse(renderThread.isAlive)
        assertFalse(replacementThread.isAlive)
        assertFalse(closeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(replacementFailure.get())
        assertNull(closeFailure.get())
    }

    @Test
    fun closeWaitsForRetiredErrorCallback() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val errorStarted = CountDownLatch(1)
        val releaseError = CountDownLatch(1)
        val renderFailure = AtomicReference<Throwable>()
        animation.start(
            onFrame = { throw IllegalStateException("frame consumer failed") },
            onError = {
                errorStarted.countDown()
                assertTrue(releaseError.await(5, TimeUnit.SECONDS))
            },
        )

        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(errorStarted.await(5, TimeUnit.SECONDS))

        val closeFailure = AtomicReference<Throwable>()
        val closeReturned = CountDownLatch(1)
        val closeThread = Thread {
            try {
                animation.close()
                closeReturned.countDown()
            } catch (cause: Throwable) {
                closeFailure.set(cause)
            }
        }
        closeThread.start()

        assertFalse(closeReturned.await(250, TimeUnit.MILLISECONDS))
        releaseError.countDown()

        assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
        renderThread.join(5_000)
        closeThread.join(5_000)
        assertFalse(renderThread.isAlive)
        assertFalse(closeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(closeFailure.get())
    }

    @Test
    fun repeatedCloseWaitsForErrorCallbackAfterReentrantClose() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val errorStarted = CountDownLatch(1)
        val releaseError = CountDownLatch(1)
        val renderFailure = AtomicReference<Throwable>()
        animation.start(
            onFrame = { throw IllegalStateException("frame consumer failed") },
            onError = {
                animation.close()
                errorStarted.countDown()
                assertTrue(releaseError.await(5, TimeUnit.SECONDS))
            },
        )

        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()
        assertTrue(errorStarted.await(5, TimeUnit.SECONDS))

        val closeFailure = AtomicReference<Throwable>()
        val closeReturned = CountDownLatch(1)
        val closeThread = Thread {
            try {
                animation.close()
                closeReturned.countDown()
            } catch (cause: Throwable) {
                closeFailure.set(cause)
            }
        }
        closeThread.start()

        assertFalse(closeReturned.await(250, TimeUnit.MILLISECONDS))
        releaseError.countDown()

        assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
        renderThread.join(5_000)
        closeThread.join(5_000)
        assertFalse(renderThread.isAlive)
        assertFalse(closeThread.isAlive)
        assertNull(renderFailure.get())
        assertNull(closeFailure.get())
    }

    @Test
    fun errorCallbackCanCloseFromRenderThread() {
        val playback = BlockingPlayback()
        val animation = AndroidDecodedAnimation(playback, TestFrameFactory())
        val errorDelivered = CountDownLatch(1)
        val renderFailure = AtomicReference<Throwable>()
        animation.start(
            onFrame = { throw IllegalStateException("frame consumer failed") },
            onError = {
                animation.close()
                errorDelivered.countDown()
            },
        )

        val renderThread = Thread {
            try {
                playback.emitFrame()
            } catch (cause: Throwable) {
                renderFailure.set(cause)
            }
        }
        renderThread.start()

        assertTrue(errorDelivered.await(5, TimeUnit.SECONDS))
        renderThread.join(5_000)
        assertFalse(renderThread.isAlive)
        assertNull(renderFailure.get())
    }

    private fun animatedWebpFixture(): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/meetup/animated.webp"),
    ).use { it.readBytes() }

    private class BlockingPlayback(
        private val blockStart: Boolean = false,
        private val frameDuration: Int = 100,
    ) : AndroidWebpPlayback {
        override val frameCount: Int = 2
        override val width: Int = 1
        override val height: Int = 1
        override val sampleSize: Int = 1
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val pauseEntered = CountDownLatch(1)
        val isRunning = AtomicBoolean()
        val resetCount = AtomicInteger()
        private var onFrame: ((ByteBuffer) -> Unit)? = null

        override fun setFrameListener(listener: ((ByteBuffer) -> Unit)?) {
            onFrame = listener
        }

        override fun frameDurationMillis(index: Int): Int = frameDuration

        override fun start() {
            startEntered.countDown()
            if (blockStart) assertTrue(releaseStart.await(5, TimeUnit.SECONDS))
            isRunning.set(true)
        }

        override fun pause() {
            pauseEntered.countDown()
            isRunning.set(false)
        }

        override fun resume() {
            isRunning.set(true)
        }

        override fun reset() {
            resetCount.incrementAndGet()
        }

        override fun stop() {
            isRunning.set(false)
        }

        fun emitFrame() {
            onFrame?.invoke(ByteBuffer.allocate(4))
        }
    }

    private class TestFrameFactory(
        private val blockCopy: Boolean = false,
    ) : AndroidAnimationFrameFactory {
        val copyEntered = CountDownLatch(1)
        val releaseCopy = CountDownLatch(1)
        val closedFrames = AtomicInteger()

        override fun copyFrame(
            width: Int,
            height: Int,
            buffer: ByteBuffer,
        ): OwnedAnimationFrame {
            copyEntered.countDown()
            if (blockCopy) assertTrue(releaseCopy.await(5, TimeUnit.SECONDS))
            return TestFrame(closedFrames)
        }
    }

    private class TestFrame(
        private val closedFrames: AtomicInteger,
    ) : OwnedAnimationFrame {
        override val bitmap: ImageBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()

        override fun close() {
            closedFrames.incrementAndGet()
        }
    }
}
