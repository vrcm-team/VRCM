package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// 设计系统自己用到的几个符号（圆头描边，24 pt 视口），页面图标仍走 AppIcons。

private fun strokeSymbol(name: String, width: Float = 2.2f, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = null,
            pathBuilder = block,
        )
        .build()

internal val AppChevronRight: ImageVector by lazy {
    strokeSymbol("ChevronRight", width = 2.6f) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
}

internal val AppChevronUpDown: ImageVector by lazy {
    strokeSymbol("ChevronUpDown") {
        moveTo(8f, 9.5f); lineTo(12f, 5.5f); lineTo(16f, 9.5f)
        moveTo(8f, 14.5f); lineTo(12f, 18.5f); lineTo(16f, 14.5f)
    }
}

internal val AppCheckmark: ImageVector by lazy {
    strokeSymbol("Checkmark", width = 2.6f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
}

internal val AppXmark: ImageVector by lazy {
    strokeSymbol("Xmark", width = 2.6f) { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
}
