package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 状态胶囊：不靠颜色单独传达状态——胶囊同时用文字（和可选图标），颜色只是语气。
 */
enum class AppCapsuleTone { Tint, SecondaryTint, Gray, Warning, Destructive }

@Composable
fun AppCapsule(
    text: String,
    modifier: Modifier = Modifier,
    tone: AppCapsuleTone = AppCapsuleTone.Gray,
    icon: ImageVector? = null,
) {
    val c = AppTheme.colors
    val (bg, fg) = when (tone) {
        AppCapsuleTone.Tint -> c.tintSoft to c.onTintSoft
        AppCapsuleTone.SecondaryTint -> c.secondaryTintSoft to c.onSecondaryTintSoft
        AppCapsuleTone.Gray -> c.fill to c.label
        AppCapsuleTone.Warning -> c.warningSoft to c.onWarningSoft
        AppCapsuleTone.Destructive -> c.destructiveSoft to c.onDestructiveSoft
    }
    Row(
        modifier = modifier.background(bg, CircleShape).padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) AppIcon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = fg)
        AppText(text, style = AppTheme.type.caption1Emphasized, color = fg, maxLines = 1)
    }
}

/** 可选中的筛选胶囊：选中着色、未选中灰底；状态同时由 selected 语义传达。 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    val motion = AppTheme.motion
    val container by animateColorAsState(if (selected) c.tint else c.fill, tween(motion.fastMs), label = "chipContainer")
    val foreground by animateColorAsState(if (selected) c.onTint else c.label, tween(motion.fastMs), label = "chipContent")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .enabledAlpha(enabled)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(foreground, AppTheme.type.footnoteEmphasized) {
            if (leadingIcon != null) Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            label()
            if (trailingIcon != null) Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { trailingIcon() }
        }
    }
}

/** 角标容器：把 [badge] 叠在 [content] 的右上角。 */
@Composable
fun AppBadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        Box(Modifier.align(Alignment.TopEnd)) { badge() }
    }
}
