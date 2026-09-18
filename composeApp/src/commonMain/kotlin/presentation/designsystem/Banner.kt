package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 一条正在显示的横幅。 */
@Stable
class AppBannerData internal constructor(val message: String)

/**
 * 横幅队列：一次只显示一条，[show] 挂起到这条横幅消失为止，后来的排队。
 * 横幅只做不打断操作的短反馈（HIG：常见可撤销动作不弹窗）。
 */
@Stable
class AppBannerHostState {
    private val mutex = Mutex()

    var current: AppBannerData? by mutableStateOf(null)
        private set

    suspend fun show(message: String, durationMillis: Long = 4000L) {
        mutex.withLock {
            current = AppBannerData(message)
            try {
                delay(durationMillis)
            } finally {
                current = null
            }
        }
    }
}

/** 横幅的显示位：当前横幅缩放淡入、淡出。 */
@Composable
fun AppBannerHost(
    hostState: AppBannerHostState,
    modifier: Modifier = Modifier,
    banner: @Composable (AppBannerData) -> Unit = { AppBanner(it.message) },
) {
    val motion = AppTheme.motion
    AnimatedContent(
        targetState = hostState.current,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(motion.normalMs)) + scaleIn(tween(motion.normalMs), initialScale = 0.92f)) togetherWith
                (fadeOut(tween(motion.fastMs)) + scaleOut(tween(motion.fastMs), targetScale = 0.96f))
        },
        contentAlignment = Alignment.Center,
        label = "AppBannerHost",
    ) { data ->
        if (data != null) banner(data) else Box(Modifier)
    }
}

/** 横幅本体：浮起的圆角面板，一两行居中的说明文字。 */
@Composable
fun AppBanner(
    message: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.elevated().secondarySystemBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(containerColor).takeOrElse { AppTheme.colors.label },
) {
    AppBanner(modifier = modifier, containerColor = containerColor, contentColor = contentColor) {
        AppText(message, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppBanner(
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.elevated().secondarySystemBackground,
    contentColor: Color = AppTheme.colors.contentColorFor(containerColor).takeOrElse { AppTheme.colors.label },
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.s)
            .widthIn(max = 520.dp)
            .shadow(16.dp, AppShapes.l, clip = false)
            .clip(AppShapes.l)
            .background(containerColor)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(contentColor, AppTheme.type.subheadline, content)
    }
}
