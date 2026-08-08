package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * 顺时针旋转 90 度并占据旋转后的空间：测量时交换约束，布局尺寸取内容的
 * 高宽互换值，再把内容按中心偏移放置，使旋转后的视觉边界正好填满布局框。
 * 供侧签模板的竖排铭牌使用。
 */
internal fun Modifier.rotateClockwise(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width - placeable.height) / 2,
                y = -(placeable.height - placeable.width) / 2,
            )
        }
    }
    .rotate(90f)
