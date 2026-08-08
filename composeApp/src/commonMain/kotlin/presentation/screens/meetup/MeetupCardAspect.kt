package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppContentSize
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/**
 * 铭牌整页在指定方向下的实际尺寸：取页面内容区而非窗口——Desktop 上自绘标题栏
 * 占据窗口顶部，用窗口尺寸会让编辑预览比真实展示更"高"，构图对不上。
 */
@Composable
fun meetupCardPageSize(orientation: MeetupOrientation): DpSize {
    val content = LocalAppContentSize.current
    val shortSide = minOf(content.width, content.height)
    val longSide = maxOf(content.width, content.height)
    return when (orientation) {
        MeetupOrientation.Portrait -> DpSize(shortSide, longSide)
        MeetupOrientation.Landscape -> DpSize(longSide, shortSide)
    }
}

/**
 * 当前内容区在指定方向下的纵横比。编辑预览与裁剪视口都使用它，
 * 保证所见与真实展示一致；尺寸尚不可用时退回 9:16。
 */
@Composable
fun meetupCardAspectRatio(orientation: MeetupOrientation): Float {
    val size = meetupCardPageSize(orientation)
    val width = size.width.value
    val height = size.height.value
    if (width <= 0f || height <= 0f) {
        return if (orientation == MeetupOrientation.Portrait) 9f / 16f else 16f / 9f
    }
    return width / height
}
