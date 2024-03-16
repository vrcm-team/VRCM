package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.ui.graphics.Color

fun Color.interpolateColor(to: Color, progress: Float): Color {
    val startRed = this.red
    val endRed = to.red
    val red = (startRed + (endRed - startRed) * progress).toInt()

    val startGreen = this.green
    val endGreen = to.green
    val green = (startGreen + (endGreen - startGreen) * progress).toInt()

    val startBlue = this.blue
    val endBlue = to.blue
    val blue = (startBlue + (endBlue - startBlue) * progress).toInt()
    return Color(red, green, blue)
}