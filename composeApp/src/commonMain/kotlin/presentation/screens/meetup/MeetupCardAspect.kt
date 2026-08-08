package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import kotlin.math.max
import kotlin.math.min

/**
 * 当前窗口在指定方向下的纵横比。编辑预览与裁剪视口都使用它，
 * 保证所见与真实展示（跟随实际视口）一致；窗口尺寸不可用时退回 9:16。
 */
@Composable
fun meetupCardAspectRatio(orientation: MeetupOrientation): Float {
    val container = LocalWindowInfo.current.containerSize
    val shortSide = min(container.width, container.height).toFloat()
    val longSide = max(container.width, container.height).toFloat()
    val portraitAspect = if (shortSide > 0f && longSide > 0f) shortSide / longSide else 9f / 16f
    return when (orientation) {
        MeetupOrientation.Portrait -> portraitAspect
        MeetupOrientation.Landscape -> 1f / portraitAspect
    }
}
