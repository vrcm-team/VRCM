package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlin.time.TimeSource
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.AnimationFrameInfo
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat

class SkiaAnimatedWebpDecoder : AnimatedWebpDecoder {
    override fun decode(bytes: ByteArray): DecodedAnimation {
        val data = Data.makeFromBytes(bytes)
        val codec = try {
            Codec.makeFromData(data)
        } catch (cause: Throwable) {
            closeAfterFailure(cause) { data.close() }
            throw cause
        }
        try {
            require(codec.encodedImageFormat == EncodedImageFormat.WEBP) {
                "Only animated WebP images are supported"
            }
            val frameCount = codec.frameCount
            require(frameCount > 1) { "WebP image must contain more than one frame" }
            val frameInfo = codec.framesInfo
            require(frameInfo.size == frameCount) { "WebP frame metadata is incomplete" }
            val durations = IntArray(frameCount) { index ->
                frameInfo[index].duration.coerceAtLeast(MINIMUM_FRAME_DURATION_MILLIS)
            }
            return SkiaDecodedAnimation(data, codec, frameInfo, durations)
        } catch (cause: Throwable) {
            closeAfterFailure(cause) { codec.close() }
            closeAfterFailure(cause) { data.close() }
            throw cause
        }
    }

    private companion object {
        const val MINIMUM_FRAME_DURATION_MILLIS = 16
    }
}

private class SkiaDecodedAnimation(
    private val data: Data,
    private val codec: Codec,
    private val frameInfo: Array<AnimationFrameInfo>,
    private val durations: IntArray,
) : DecodedAnimation {
    private val lock = SynchronizedObject()
    private val dispatchGate = SynchronizedObject()
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var closed = false
    private var paused = true
    private var listener: ((OwnedAnimationFrame) -> Unit)? = null
    private var playbackJob: Job? = null
    private var playbackGeneration = 0
    private var activeGeneration: Int? = null
    private var nextFrameIndex = 0
    private var failed = false
    private var errorListener: ((Throwable) -> Unit)? = null

    /** 仅保留最近一帧的合成结果及其序号，顺序播放的下一帧直接在其像素上续解。 */
    private var composedFrame: Bitmap? = null
    private var composedFrameIndex = NO_REQUIRED_FRAME

    override val frameCount: Int
        get() = synchronized(lock) {
            ensureOpen()
            durations.size
        }

    override fun durationMillis(index: Int): Int = synchronized(lock) {
        ensureOpen()
        durations[index]
    }

    override fun start(
        onFrame: (OwnedAnimationFrame) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val transition = synchronized(lock) {
            ensureOpen()
            check(!failed) { "Animated WebP decoder has failed" }
            activeGeneration = null
            listener = onFrame
            errorListener = onError
            nextFrameIndex = 0
            clearComposedFrameLocked()
            paused = true
            playbackGeneration++
            playbackJob?.cancel()
            playbackJob = null
            playbackGeneration
        }
        synchronized(dispatchGate) {
            synchronized(lock) {
                if (closed || failed || playbackGeneration != transition || activeGeneration != null) return
                paused = false
                activeGeneration = transition
                restartPlaybackLocked(transition)
            }
        }
    }

    override fun pause() {
        synchronized(lock) {
            if (!closed) {
                activeGeneration = null
                paused = true
                playbackGeneration++
                playbackJob?.cancel()
                playbackJob = null
            }
        }
        synchronized(dispatchGate) {}
    }

    override fun resume() {
        val transition = synchronized(lock) {
            ensureOpen()
            if (!paused || listener == null || failed) return@synchronized null
            activeGeneration = null
            paused = true
            playbackGeneration++
            playbackJob?.cancel()
            playbackJob = null
            playbackGeneration
        } ?: return
        synchronized(dispatchGate) {
            synchronized(lock) {
                if (closed || failed || playbackGeneration != transition || activeGeneration != null) return
                paused = false
                activeGeneration = transition
                restartPlaybackLocked(transition)
            }
        }
    }

    override fun close() {
        var job: Job? = null
        synchronized(lock) {
            if (closed) return@synchronized
            closed = true
            paused = true
            playbackGeneration++
            activeGeneration = null
            job = playbackJob
            playbackJob = null
            listener = null
            errorListener = null
            clearComposedFrameLocked()
            closeBoth({ codec.close() }, { data.close() })
        }
        job?.cancel()
        synchronized(dispatchGate) {}
    }

    private fun restartPlaybackLocked(generation: Int) {
        playbackJob = playbackScope.launch { play(generation) }
    }

    private suspend fun play(generation: Int) {
        while (currentCoroutineContext().isActive) {
            // 记录帧起始时间，delay 时补偿解码与分发耗时，保持帧节奏稳定。
            val frameStart = TimeSource.Monotonic.markNow()
            val playbackFrame = synchronized(lock) {
                if (!isCurrentPlaybackLocked(generation)) return@synchronized null
                val callback = listener ?: return@synchronized null
                val index = nextFrameIndex
                nextFrameIndex = (nextFrameIndex + 1) % durations.size
                PlaybackFrame(index, durations[index], callback)
            } ?: return

            val frame = try {
                decodeFrame(playbackFrame.index, generation)
            } catch (cause: Throwable) {
                fail(generation, cause)
                return
            } ?: return
            val delivered = synchronized(dispatchGate) {
                val callback = synchronized(lock) {
                    if (isCurrentPlaybackLocked(generation)) playbackFrame.callback else null
                }
                if (callback == null) return@synchronized false
                try {
                    callback(frame)
                    true
                } catch (cause: Throwable) {
                    closeAfterFailure(cause) { frame.close() }
                    failWhileDispatching(generation, cause)
                    false
                }
            }
            if (!delivered) {
                frame.close()
                return
            }
            val elapsedMillis = frameStart.elapsedNow().inWholeMilliseconds
            delay((playbackFrame.durationMillis - elapsedMillis).coerceAtLeast(0))
        }
    }

    private fun decodeFrame(index: Int, generation: Int): OwnedAnimationFrame? = synchronized(lock) {
        if (!isCurrentPlaybackLocked(generation)) return@synchronized null
        ensureOpen()
        val bitmap = composeFrameLocked(index)
        try {
            retainComposedFrameLocked(index, bitmap)
            SkiaOwnedAnimationFrame(bitmap)
        } catch (cause: Throwable) {
            closeAfterFailure(cause) { bitmap.close() }
            throw cause
        }
    }

    /**
     * 合成第 [index] 帧：顺序播放时直接克隆保留的上一合成帧续解；
     * 跳帧或回卷时沿 requiredFrame 依赖链回溯到独立帧后重放（最坏 O(n)）。
     */
    private fun composeFrameLocked(index: Int): Bitmap {
        val replay = ArrayDeque<Int>()
        var cursor = index
        var base: Bitmap? = null
        while (true) {
            val required = frameInfo[cursor].requiredFrame
            replay.addFirst(cursor)
            if (required == NO_REQUIRED_FRAME) break
            require(required in 0 until cursor) { "Invalid WebP frame dependency: $required" }
            val retained = composedFrame
            if (retained != null && composedFrameIndex == required) {
                base = retained.makeClone()
                break
            }
            cursor = required
        }
        val bitmap = base ?: Bitmap()
        try {
            if (base == null) {
                check(bitmap.allocPixels(codec.imageInfo)) { "Unable to allocate animation frame bitmap" }
            }
            replay.forEach { frameIndex ->
                codec.readPixels(bitmap, frameIndex, frameInfo[frameIndex].requiredFrame)
            }
            return bitmap
        } catch (cause: Throwable) {
            closeAfterFailure(cause) { bitmap.close() }
            throw cause
        }
    }

    /** 先释放旧的保留帧再克隆新帧，保证峰值缓存不超过两帧位图。 */
    private fun retainComposedFrameLocked(index: Int, bitmap: Bitmap) {
        val previous = composedFrame
        composedFrame = null
        composedFrameIndex = NO_REQUIRED_FRAME
        previous?.close()
        composedFrame = bitmap.makeClone()
        composedFrameIndex = index
    }

    private fun ensureOpen() {
        check(!closed) { "Animated WebP decoder is closed" }
    }

    private fun isCurrentPlaybackLocked(generation: Int): Boolean =
        !closed && !paused && generation == playbackGeneration && activeGeneration == generation

    private fun clearComposedFrameLocked() {
        composedFrame?.close()
        composedFrame = null
        composedFrameIndex = NO_REQUIRED_FRAME
    }

    private fun fail(generation: Int, cause: Throwable) {
        synchronized(dispatchGate) {
            failWhileDispatching(generation, cause)
        }
    }

    private fun failWhileDispatching(generation: Int, cause: Throwable) {
        val onError = synchronized(lock) {
            if (!isCurrentPlaybackLocked(generation) || failed) return@synchronized null
            failed = true
            paused = true
            playbackGeneration++
            playbackJob = null
            activeGeneration = null
            listener = null
            clearComposedFrameLocked()
            errorListener.also { errorListener = null }
        }
        onError?.invoke(cause)
    }

    private data class PlaybackFrame(
        val index: Int,
        val durationMillis: Int,
        val callback: (OwnedAnimationFrame) -> Unit,
    )

    private companion object {
        const val NO_REQUIRED_FRAME = -1
    }
}

private class SkiaOwnedAnimationFrame(bitmap: Bitmap) : OwnedAnimationFrame {
    private val lock = SynchronizedObject()
    private var ownedBitmap: Bitmap? = bitmap

    override val bitmap: ImageBitmap = try {
        bitmap.asComposeImageBitmap()
    } catch (cause: Throwable) {
        closeAfterFailure(cause) { bitmap.close() }
        throw cause
    }

    override fun close() = synchronized(lock) {
        ownedBitmap?.close()
        ownedBitmap = null
    }
}

private fun closeBoth(first: () -> Unit, second: () -> Unit) {
    var failure: Throwable? = null
    try {
        first()
    } catch (cause: Throwable) {
        failure = cause
    }
    try {
        second()
    } catch (cause: Throwable) {
        failure?.addSuppressed(cause) ?: run { failure = cause }
    }
    failure?.let { throw it }
}

private fun closeAfterFailure(primaryFailure: Throwable, closeResource: () -> Unit) {
    try {
        closeResource()
    } catch (closeFailure: Throwable) {
        if (closeFailure !== primaryFailure) primaryFailure.addSuppressed(closeFailure)
    }
}
