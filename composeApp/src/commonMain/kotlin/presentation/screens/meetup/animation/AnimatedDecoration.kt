package io.github.vrcmteam.vrcm.presentation.screens.meetup.animation

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** 保留最近两帧再释放更早的帧，确保 Compose 换帧期间不会绘制已释放位图。 */
private class FrameHandoff {
    private var previous: OwnedAnimationFrame? = null
    private var current: OwnedAnimationFrame? = null

    fun push(frame: OwnedAnimationFrame): ImageBitmap {
        previous?.close()
        previous = current
        current = frame
        return frame.bitmap
    }

    fun closeAll() {
        previous?.close()
        current?.close()
        previous = null
        current = null
    }
}

/**
 * 官方 Profile Decoration 播放器：Animated 资产逐帧播放，解码失败时
 * 回退同槽位 static base，两者都不可用则不渲染。装饰层不接收指针事件。
 * 只在 Lifecycle RESUMED 时推进帧；离开组合释放全部帧与解码器。
 */
@Composable
fun AnimatedDecoration(
    decoration: ResolvedDecoration,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val asset = decoration.asset
    if (decoration.mode == DecorationRenderMode.Unavailable || asset == null) return
    val assetStore: MeetupCardAssetStore = koinInject()

    if (decoration.mode == DecorationRenderMode.Static) {
        AsyncImage(
            model = assetStore.model(asset),
            contentDescription = null,
            imageLoader = koinInject<ImageLoader>(),
            contentScale = contentScale,
            modifier = modifier,
        )
        return
    }

    val decoder: AnimatedWebpDecoder = koinInject()
    var frameBitmap by remember(asset) { mutableStateOf<ImageBitmap?>(null) }
    var animationFailed by remember(asset) { mutableStateOf(false) }
    val playback = remember(asset) { DecorationPlayback(FrameHandoff()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(asset) {
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
            onFrame = { frame -> frameBitmap = playback.push(frame) },
            onError = { animationFailed = true },
        )
        if (lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED) {
            animation.pause()
        }
    }
    DisposableEffect(asset, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> playback.resume()
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

    if (animationFailed) {
        // 运行期解码/播放失败：回退同槽位已缓存的静态 base；没有 base 则不渲染。
        decoration.staticFallback?.let { fallback ->
            AsyncImage(
                model = assetStore.model(fallback),
                contentDescription = null,
                imageLoader = koinInject<ImageLoader>(),
                contentScale = contentScale,
                modifier = modifier,
            )
        }
        return
    }
    frameBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

/** 播放器与帧的所有权边界；attach 前的 pause/resume 调用会被忽略。 */
private class DecorationPlayback(private val frames: FrameHandoff) {
    private var animation: DecodedAnimation? = null
    private var released = false

    fun attach(animation: DecodedAnimation) {
        if (released) {
            animation.close()
            return
        }
        this.animation = animation
    }

    fun push(frame: OwnedAnimationFrame): ImageBitmap = frames.push(frame)

    fun pause() {
        animation?.pause()
    }

    fun resume() {
        animation?.resume()
    }

    fun release() {
        released = true
        animation?.close()
        animation = null
        frames.closeAll()
    }
}
