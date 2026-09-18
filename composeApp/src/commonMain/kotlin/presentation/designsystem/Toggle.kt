package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 开关（HIG Toggles）：只用于二态；状态不只靠颜色——滑块位置本身就是状态，Increase Contrast 下轨道加描边。
 * 尺寸 51×31（UISwitch）。[onCheckedChange] 为 null 时只展示状态，由所在的行负责切换。
 */
@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val track by animateColorAsState(if (checked) c.tint else c.fill, tween(motion.fastMs), label = "toggleTrack")
    val knobX by animateDpAsState(if (checked) 20.dp else 0.dp, tween(motion.fastMs), label = "toggleKnob")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(width = 51.dp, height = 31.dp)
                .clip(CircleShape)
                .background(track)
                .then(if (AppTheme.a11y.increaseContrast) Modifier.border(1.dp, c.label.copy(alpha = 0.5f), CircleShape) else Modifier),
        )
        Box(
            Modifier
                .padding(start = 2.dp)
                .offset(x = knobX)
                .size(27.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * 勾选框：Apple 平台的圆形对勾——选中是着色实心圆 + 白色对勾，未选中是空心圆。
 * 视觉 22 dp；[onCheckedChange] 为 null 时只展示状态。
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val fill by animateColorAsState(if (checked) c.tint else Color.Transparent, tween(motion.fastMs), label = "checkboxFill")
    val markScale by animateFloatAsState(if (checked) 1f else 0f, tween(motion.fastMs), label = "checkboxMark")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .padding(9.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (checked) Modifier else Modifier.border(1.5.dp, c.tertiaryLabel, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppCheckmark, contentDescription = null, modifier = Modifier.size(14.dp).scale(markScale), tint = c.onTint)
    }
}

/**
 * 单选：macOS / iPadOS 表单里的单选圆——选中是着色实心圆 + 白色圆心。
 * [onClick] 为 null 时只展示状态，由所在的行负责选中。
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val fill by animateColorAsState(if (selected) c.tint else Color.Transparent, tween(motion.fastMs), label = "radioFill")
    val dotScale by animateFloatAsState(if (selected) 1f else 0f, tween(motion.fastMs), label = "radioDot")
    val select = if (onClick != null) {
        Modifier.selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(select)
            .padding(9.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, c.tertiaryLabel, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).scale(dotScale).clip(CircleShape).background(c.onTint))
    }
}
