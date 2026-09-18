package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * 按压态：Apple 风格的整块压暗 / 提亮，而不是 Material 涟漪。
 * 浅色下叠 8% 黑，深色下叠 10% 白；Increase Contrast 时加倍。指针悬停（桌面）叠一半强度。
 */
class PressHighlightIndication(private val overlay: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressHighlightNode(interactionSource, overlay)

    override fun equals(other: Any?): Boolean = other is PressHighlightIndication && other.overlay == overlay
    override fun hashCode(): Int = overlay.hashCode()

    companion object {
        fun forTheme(isDark: Boolean, increaseContrast: Boolean): PressHighlightIndication {
            val base = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
            return PressHighlightIndication(if (increaseContrast) base.copy(alpha = base.alpha * 2f) else base)
        }
    }
}

private class PressHighlightNode(
    private val interactionSource: InteractionSource,
    private val overlay: Color,
) : Modifier.Node(), DrawModifierNode {
    private var pressCount = 0
    private var hoverCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val wasPressed = pressCount > 0
                val wasHovered = hoverCount > 0
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    is HoverInteraction.Enter -> hoverCount++
                    is HoverInteraction.Exit -> hoverCount = (hoverCount - 1).coerceAtLeast(0)
                }
                if (wasPressed != pressCount > 0 || wasHovered != hoverCount > 0) invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        when {
            pressCount > 0 -> drawRect(overlay)
            hoverCount > 0 -> drawRect(overlay.copy(alpha = overlay.alpha * 0.5f))
        }
    }
}
