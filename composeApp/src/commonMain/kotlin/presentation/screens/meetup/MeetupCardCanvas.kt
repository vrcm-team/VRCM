package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedDecoration
import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import org.koin.compose.koinInject

/**
 * 身份卡实时分层画布：主题背景、照片、遮罩、资料特效、模板内容与
 * 控制层 slot 固定层级，不预生成海报位图。最低可展示状态是
 * 主题背景加完整 Display Name。
 */
@Composable
fun MeetupCardCanvas(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier = Modifier,
    controls: (@Composable BoxScope.() -> Unit)? = null,
) {
    val calculator: CropTransformCalculator = koinInject()
    val accent = Color(state.config.accentArgb)
    val crop = if (orientation == state.orientation) {
        state.activeCrop
    } else {
        when (orientation) {
            MeetupOrientation.Portrait -> state.config.portraitCrop
            MeetupOrientation.Landscape -> state.config.landscapeCrop
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        // 1. 主题背景：照片缺失/加载失败时的最终回退层。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.1f)),
                    ),
                ),
        )
        // 2. 照片。
        MeetupCardPhoto(
            photoModel = state.photoModel,
            photoSize = state.config.photo
                .takeIf { it.width > 0 && it.height > 0 }
                ?.let { ImageSize(it.width, it.height) },
            crop = crop,
            orientation = orientation,
            calculator = calculator,
            modifier = Modifier.fillMaxSize(),
        )
        // 3. 遮罩强度。
        if (state.config.scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = state.config.scrimAlpha.coerceIn(0f, 1f))),
            )
        }
        // 4. 官方资料特效：不接收指针事件，不改变控件布局。
        if (state.config.showProfileEffect) {
            state.decorations[DecorationSlot.ProfileEffect]?.let { decoration ->
                AnimatedDecoration(
                    decoration = decoration,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // 5. 模板内容（名字、字段、二维码、头像框与铭牌特效在内部按槽位渲染）。
        MeetupCardTemplateContent(
            state = state,
            orientation = orientation,
            modifier = Modifier.fillMaxSize(),
        )
        // 6. 控制层 slot。
        controls?.invoke(this)
    }
}
