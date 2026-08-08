package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.github.penfeizhou.animation.decode.FrameSeqDecoder
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import com.github.penfeizhou.animation.webp.decode.WebPDecoder
import java.nio.ByteBuffer

/** Streams animated WebP frames through AWebP's sequential decoder. */
class AndroidAnimatedWebpDecoder : AnimatedWebpDecoder {
    override fun decode(bytes: ByteArray): DecodedAnimation = AndroidDecodedAnimation(bytes.copyOf())
}

internal interface AndroidWebpPlayback {
    val frameCount: Int
    val width: Int
    val height: Int
    val sampleSize: Int

    fun setFrameListener(listener: ((ByteBuffer) -> Unit)?)

    fun frameDurationMillis(index: Int): Int

    fun start()

    fun pause()

    fun resume()

    fun stop()
}

internal interface AndroidAnimationFrameFactory {
    fun copyFrame(width: Int, height: Int, buffer: ByteBuffer): OwnedAnimationFrame
}

private class AwebpPlayback(bytes: ByteArray) :
    AndroidWebpPlayback,
    FrameSeqDecoder.RenderListener {
    private val decoder = WebPDecoder(
        object : ByteBufferLoader() {
            override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
        },
        this,
    )
    @Volatile
    private var frameListener: ((ByteBuffer) -> Unit)? = null

    init {
        decoder.bounds
    }

    override val frameCount: Int
        get() = decoder.frameCount

    override val width: Int
        get() = decoder.bounds.width()

    override val height: Int
        get() = decoder.bounds.height()

    override val sampleSize: Int
        get() = decoder.sampleSize

    override fun setFrameListener(listener: ((ByteBuffer) -> Unit)?) {
        frameListener = listener
    }

    override fun frameDurationMillis(index: Int): Int = requireNotNull(decoder.getFrame(index)).frameDuration

    override fun start() = decoder.start()

    override fun pause() = decoder.pause()

    override fun resume() = decoder.resume()

    override fun stop() = decoder.stop()

    override fun onStart() = Unit

    override fun onRender(buffer: ByteBuffer) {
        frameListener?.invoke(buffer)
    }

    override fun onEnd() = Unit
}

private object AndroidBitmapFrameFactory : AndroidAnimationFrameFactory {
    override fun copyFrame(width: Int, height: Int, buffer: ByteBuffer): OwnedAnimationFrame {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            return AndroidOwnedAnimationFrame(bitmap)
        } catch (cause: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw cause
        } finally {
            buffer.rewind()
        }
    }
}

internal class AndroidDecodedAnimation(
    private val playback: AndroidWebpPlayback,
    private val frameFactory: AndroidAnimationFrameFactory,
) : DecodedAnimation {
    constructor(bytes: ByteArray) : this(AwebpPlayback(bytes), AndroidBitmapFrameFactory)

    private val controlGate = Any()
    private val dispatchGate = Any()
    private var closed = false
    private var paused = true
    private var started = false
    private var failed = false
    private var generation = 0
    private var activeGeneration: Int? = null
    private var listener: ((OwnedAnimationFrame) -> Unit)? = null
    private var errorListener: ((Throwable) -> Unit)? = null
    private val durations: IntArray

    init {
        require(playback.frameCount > 1) { "WebP image must contain more than one frame" }
        durations = IntArray(playback.frameCount) { index ->
            playback.frameDurationMillis(index).coerceAtLeast(
                MINIMUM_FRAME_DURATION_MILLIS,
            )
        }
        playback.setFrameListener(::onRender)
    }

    override val frameCount: Int
        get() = synchronized(controlGate) {
            ensureOpen()
            durations.size
        }

    override fun durationMillis(index: Int): Int = synchronized(controlGate) {
        ensureOpen()
        durations[index]
    }

    override fun start(
        onFrame: (OwnedAnimationFrame) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val transition = synchronized(controlGate) {
            ensureOpen()
            check(!failed) { "Animated WebP decoder has failed" }
            activeGeneration = null
            listener = onFrame
            errorListener = onError
            paused = true
            generation++
            if (started) playback.pause()
            generation
        }
        synchronized(dispatchGate) {
            synchronized(controlGate) {
                if (closed || failed || generation != transition || activeGeneration != null) return
                if (started) playback.resume() else playback.start()
                if (closed || failed || generation != transition || activeGeneration != null) return
                started = true
                paused = false
                activeGeneration = transition
            }
        }
    }

    override fun pause() {
        synchronized(controlGate) {
            if (!closed) {
                activeGeneration = null
                paused = true
                generation++
                playback.pause()
            }
        }
        synchronized(dispatchGate) {}
    }

    override fun resume() {
        val transition = synchronized(controlGate) {
            ensureOpen()
            if (!started || !paused || failed) return
            activeGeneration = null
            generation++
            playback.pause()
            generation
        }
        synchronized(dispatchGate) {
            synchronized(controlGate) {
                if (closed || failed || generation != transition || activeGeneration != null) return
                playback.resume()
                if (closed || failed || generation != transition || activeGeneration != null) return
                paused = false
                activeGeneration = transition
            }
        }
    }

    override fun close() {
        synchronized(controlGate) {
            if (!closed) {
                closed = true
                paused = true
                generation++
                activeGeneration = null
                listener = null
                errorListener = null
                playback.setFrameListener(null)
                playback.stop()
            }
        }
        synchronized(dispatchGate) {}
    }

    private fun onRender(buffer: ByteBuffer) {
        val renderGeneration = synchronized(controlGate) {
            activeGeneration?.takeIf(::isActiveGenerationLocked)
        } ?: return
        val frame = try {
            frameFactory.copyFrame(
                playback.width / playback.sampleSize,
                playback.height / playback.sampleSize,
                buffer,
            )
        } catch (cause: Throwable) {
            fail(renderGeneration, cause)
            return
        }
        val delivered = synchronized(dispatchGate) {
            val callback = synchronized(controlGate) {
                if (isActiveGenerationLocked(renderGeneration)) listener else null
            }
            if (callback == null) return@synchronized false
            try {
                callback(frame)
                true
            } catch (cause: Throwable) {
                closeAfterFailure(frame, cause)
                failWhileDispatching(renderGeneration, cause)
                false
            }
        }
        if (!delivered) {
            frame.close()
        }
    }

    private fun ensureOpen() {
        check(!closed) { "Animated WebP decoder is closed" }
    }

    private fun isActiveGenerationLocked(expectedGeneration: Int): Boolean =
        !closed && !paused && !failed && activeGeneration == expectedGeneration

    private fun fail(expectedGeneration: Int, cause: Throwable) {
        synchronized(dispatchGate) {
            failWhileDispatching(expectedGeneration, cause)
        }
    }

    private fun failWhileDispatching(expectedGeneration: Int, cause: Throwable) {
        val onError = synchronized(controlGate) {
            if (!isActiveGenerationLocked(expectedGeneration)) return@synchronized null
            failed = true
            paused = true
            generation++
            activeGeneration = null
            listener = null
            playback.setFrameListener(null)
            playback.stop()
            errorListener.also { errorListener = null }
        }
        onError?.invoke(cause)
    }

    private companion object {
        const val MINIMUM_FRAME_DURATION_MILLIS = 16
    }
}

private class AndroidOwnedAnimationFrame(bitmap: Bitmap) : OwnedAnimationFrame {
    private val lock = Any()
    private var ownedBitmap: Bitmap? = bitmap

    override val bitmap: ImageBitmap = bitmap.asImageBitmap()

    override fun close() = synchronized(lock) {
        ownedBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        ownedBitmap = null
    }
}

private fun closeAfterFailure(frame: OwnedAnimationFrame, primaryFailure: Throwable) {
    try {
        frame.close()
    } catch (closeFailure: Throwable) {
        if (closeFailure !== primaryFailure) primaryFailure.addSuppressed(closeFailure)
    }
}
