package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.CropTransformCalculator
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageSize
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.DecorationVisualImage
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.rememberDecorationVisual
import io.github.vrcmteam.vrcm.service.meetup.DecorationSlot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.templateFor
import org.koin.compose.koinInject

/**
 * 身份卡实时分层画布：主题背景、照片、遮罩、资料特效、模板内容与
 * 控制层 slot 固定层级，不预生成海报位图。[contentPadding] 只约束模板内容。
 * 聚光与侧签的照片/资料特效全出血；资料栏把它们裁切在独立图片区。
 * 最低可展示状态是主题背景加完整 Display Name。
 */
@Composable
fun MeetupCardCanvas(
    state: MeetupCardUiState,
    orientation: MeetupOrientation,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
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
    val template = state.config.templateFor(orientation)
    // 装饰播放器挂在稳定画布层，切换模板时只改变画面放置位置，不重建解码器。
    val profileEffect = rememberDecorationVisual(
        decoration = state.decorations[DecorationSlot.ProfileEffect],
        enabled = state.config.showProfileEffect,
    )
    val templateDecorations = MeetupDecorationVisuals(
        iconFrame = rememberDecorationVisual(
            decoration = state.decorations[DecorationSlot.IconFrame],
            enabled = state.config.showIconFrame,
        ),
        nameplateEffect = rememberDecorationVisual(
            decoration = state.decorations[DecorationSlot.NameplateEffect],
            enabled = state.config.showNameplateEffect,
        ),
    )
    val landscapeOverride = state.config.landscapePhoto
        ?.takeIf { orientation == MeetupOrientation.Landscape && state.landscapePhotoModel != null }
    val photoRecord = landscapeOverride ?: state.config.photo
    val mediaLayer: @Composable BoxScope.() -> Unit = {
        // 照片：横屏优先使用独立照片，未设置或不可用时沿用竖屏照片。
        MeetupCardPhoto(
            photoModel = if (landscapeOverride != null) state.landscapePhotoModel else state.photoModel,
            photoSize = photoRecord
                .takeIf { it.width > 0 && it.height > 0 }
                ?.let { ImageSize(it.width, it.height) },
            crop = crop,
            orientation = orientation,
            calculator = calculator,
            modifier = Modifier.fillMaxSize(),
        )
        if (state.config.scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = state.config.scrimAlpha.coerceIn(0f, 1f))),
            )
        }
        // ProfileEffect 属于照片视觉层：资料栏中随图片区裁切，其他模板仍全屏。
        DecorationVisualImage(
            visual = profileEffect,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().clipToBounds(),
        )
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
        // 2-5. 资料栏由模板测量图片区并把媒体层裁进去；其他模板先全屏绘制媒体层。
        if (template != MeetupCardTemplate.InfoBar) mediaLayer()
        // 模板内容（名字、字段、二维码、头像框与铭牌特效在内部按槽位渲染）。
        //    装饰的解码与播放留在这一层：切换版式会重建整棵模板树，
        //    播放器跟着重建就要在主线程等解码器放锁再从头重放，连续切换会卡死 UI。
        MeetupCardTemplateContent(
            state = state,
            orientation = orientation,
            decorations = templateDecorations,
            contentPadding = contentPadding,
            infoBarMedia = mediaLayer.takeIf { template == MeetupCardTemplate.InfoBar },
            modifier = Modifier.fillMaxSize(),
        )
        // 6. 控制层 slot。
        controls?.invoke(this)
    }
}
