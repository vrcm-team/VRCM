package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale

/**
 * 展示页整卡与编辑页预览共用的共享元素 key：两者渲染同一张铭牌，
 * 进出编辑页时整卡在全屏与预览缩略图之间连续变换。
 */
internal fun meetupCardSharedKey(ownerUserId: String): String = "meetup-card:$ownerUserId"

/**
 * 整卡变换用缩放而非重新测量：两端渲染的是同一张卡，等比缩放不会让
 * 模板在动画中途重排；纵横比不一致时裁剪而不是拉伸变形。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val MeetupCardResizeMode: ResizeMode =
    ResizeMode.scaleToBounds(contentScale = ContentScale.Crop, alignment = Alignment.Center)
