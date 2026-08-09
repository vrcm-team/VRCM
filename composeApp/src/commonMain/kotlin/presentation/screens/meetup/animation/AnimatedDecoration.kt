package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.vrcmteam.vrcm.service.meetup.DecorationRenderMode
import io.github.vrcmteam.vrcm.service.meetup.ResolvedDecoration
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** 装饰当前可绘制的画面：动画的某一帧，或静态回退图。 */
sealed interface DecorationVisual {
    data class Frame(val bitmap: ImageBitmap) : DecorationVisual

    data class Static(val model: String) : DecorationVisual
}

/**
 * 官方 Profile Decoration 播放器：Animated 资产逐帧播放，解码失败时
 * 回退同槽位 static base，两者都不可用则不渲染。装饰层不接收指针事件。
 * 只在 Lifecycle RESUMED 时推进帧；离开组合释放全部帧与解码器。
 *
 * 播放状态挂在调用它的那一层：装饰若跟着会重建的子树（例如切换卡片版式时
 * 整棵模板树），每次重建都要在主线程等解码器放锁、再从第 0 帧重放整段动画，
 * 连续切换会直接卡住 UI。因此卡片侧在稳定的层调用它，把画面传下去渲染。
 */
@Composable
fun rememberDecorationVisual(
    decoration: ResolvedDecoration?,
    enabled: Boolean = true,
): DecorationVisual? {
    val assetStore: MeetupCardAssetStore = koinInject()
    val decoder: AnimatedWebpDecoder = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    // 播放器只跟着素材走：开关关掉时暂停而不是销毁，
    // 否则每次开关都要在主线程等解码器放锁，再从第 0 帧重放整段动画。
    val animatedAsset = decoration
        ?.takeIf { it.mode == DecorationRenderMode.Animated }
        ?.asset
    var frameBitmap by remember(animatedAsset) { mutableStateOf<ImageBitmap?>(null) }
    var animationFailed by remember(animatedAsset) { mutableStateOf(false) }
    val playback = remember(animatedAsset) { DecorationPlayback() }
    val playbackEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(animatedAsset) {
        val asset = animatedAsset ?: return@LaunchedEffect
        // 被替换的帧不能在解码线程立即关闭：显示列表可能仍引用旧位图
        //（Android 上表现为 "trying to use a recycled bitmap"）。
        // 等两个 Choreographer 帧、让重组与重录制完成后再关闭。
        launch {
            for (frame in playback.retiredFrames) {
                withFrameNanos {}
                withFrameNanos {}
                frame.close()
            }
        }
        val animation = try {
            val bytes = withContext(Dispatchers.IO) { assetStore.read(asset) }
            decoder.decode(bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            animationFailed = true
            return@LaunchedEffect
        }
        playback.attach(animation)
        animation.start(
            onFrame = { frame -> playback.push(frame)?.let { frameBitmap = it } },
            onError = {
                // 运行期失败只暂停并切静态回退；帧统一在离开组合时释放。
                playback.pause()
                animationFailed = true
            },
        )
        if (!playbackEnabled || lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED) {
            animation.pause()
        }
    }
    // 关掉开关只停帧，不动播放器；重新打开就地续播。
    LaunchedEffect(animatedAsset, enabled) {
        if (animatedAsset == null) return@LaunchedEffect
        if (enabled && lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            playback.resume()
        } else {
            playback.pause()
        }
    }
    DisposableEffect(animatedAsset, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // 应用回前台时，被开关关掉的装饰不该跟着恢复播放。
                Lifecycle.Event.ON_RESUME -> if (playbackEnabled) playback.resume()
                Lifecycle.Event.ON_PAUSE -> playback.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playback.release()
        }
    }

    val asset = decoration?.asset
    return when {
        !enabled || decoration == null || asset == null -> null
        decoration.mode == DecorationRenderMode.Unavailable -> null
        decoration.mode == DecorationRenderMode.Static -> DecorationVisual.Static(assetStore.model(asset))
        animationFailed ->
            decoration.staticFallback?.let { DecorationVisual.Static(assetStore.model(it)) }
        else -> frameBitmap?.let(DecorationVisual::Frame)
    }
}

/** 纯渲染：画面由 [rememberDecorationVisual] 在上层维护，这里只负责画出来。 */
@Composable
fun DecorationVisualImage(
    visual: DecorationVisual?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    when (visual) {
        null -> Unit
        is DecorationVisual.Frame -> Image(
            bitmap = visual.bitmap,
            contentDescription = null,
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier,
        )
        is DecorationVisual.Static -> AsyncImage(
            model = visual.model,
            contentDescription = null,
            imageLoader = koinInject<ImageLoader>(),
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier,
        )
    }
}

/**
 * 播放器与帧的所有权边界。帧在解码线程到达，被替换的帧进入退休队列，
 * 由 UI 侧协程延迟关闭；[release] 后到达的帧立即关闭。
 */
private class DecorationPlayback {
    private val lock = SynchronizedObject()
    private val retired = Channel<OwnedAnimationFrame>(Channel.UNLIMITED)
    private var current: OwnedAnimationFrame? = null
    private var animation: DecodedAnimation? = null
    private var released = false

    /** 供 UI 侧退休协程消费的旧帧。 */
    val retiredFrames: Channel<OwnedAnimationFrame> get() = retired

    fun attach(animation: DecodedAnimation) {
        synchronized(lock) {
            if (released) {
                animation.close()
                return
            }
            this.animation = animation
        }
    }

    /** 接管新帧并把旧帧移入退休队列；释放后返回 null 且立即关闭来帧。 */
    fun push(frame: OwnedAnimationFrame): ImageBitmap? = synchronized(lock) {
        if (released) {
            frame.close()
            return@synchronized null
        }
        current?.let { previous ->
            if (retired.trySend(previous).isFailure) previous.close()
        }
        current = frame
        frame.bitmap
    }

    fun pause() {
        synchronized(lock) { animation }?.pause()
    }

    fun resume() {
        synchronized(lock) { animation }?.resume()
    }

    fun release() {
        val lastFrame = synchronized(lock) {
            released = true
            animation?.close()
            animation = null
            current.also { current = null }
        }
        retired.close()
        // 帧的 close 幂等：与退休协程竞争关闭同一帧是安全的。
        while (true) {
            val frame = retired.tryReceive().getOrNull() ?: break
            frame.close()
        }
        lastFrame?.close()
    }
}
