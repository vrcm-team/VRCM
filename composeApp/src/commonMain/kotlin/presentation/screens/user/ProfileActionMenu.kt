package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

internal enum class ProfileActionPage { Overview, Interaction, Invite, More }

@Composable
internal fun ProfileActionHeader(
    currentUser: UserProfileVo,
    page: ProfileActionPage,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val localeStrings = strings
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page == ProfileActionPage.Overview) {
            // 头像与工具栏按钮共用 44 dp 的几何契约；标题可以随字体增大而撑高。
            AImage(
                imageData = currentUser.iconUrl,
                modifier = Modifier.size(AppSize.touchTarget).clip(AppShapes.m),
            )
        } else {
            AppIconButton(onClick = onBack, size = AppSize.touchTarget) {
                AppIcon(AppIcons.ArrowBackIosNew, localeStrings.back)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(
                text = when (page) {
                    ProfileActionPage.Overview -> currentUser.displayName
                    ProfileActionPage.Interaction -> localeStrings.profileActionInteraction
                    ProfileActionPage.Invite -> localeStrings.profileActionInvite
                    ProfileActionPage.More -> localeStrings.profileActionMore
                },
                style = AppTheme.type.title3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (page == ProfileActionPage.Overview && currentUser.note.isNotBlank()) {
                AppText(
                    text = currentUser.note,
                    style = AppTheme.type.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppIconButton(onClick = onClose, size = AppSize.touchTarget, style = AppButtonStyle.Gray) {
            AppIcon(AppIcons.Close, localeStrings.close)
        }
    }
}

@Composable
internal fun ProfileActionSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        AppSectionHeader(title)
        AppGroup(content = content)
    }
}

@Composable
internal fun ProfileActionDivider() {
    AppDivider(Modifier.padding(start = appRowDividerInset(AppSize.icon)))
}

@Composable
internal fun ProfileActionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    chevron: Boolean = true,
    divider: Boolean = false,
) {
    if (divider) ProfileActionDivider()
    val color = if (destructive) AppTheme.colors.destructive else AppTheme.colors.label
    AppRow(
        title = text,
        leading = { AppIcon(icon, null, Modifier.size(AppSize.icon), tint = if (destructive) color else AppTheme.colors.secondaryLabel) },
        titleColor = color,
        enabled = enabled,
        chevron = chevron,
        onClick = onClick,
    )
}

@Composable
internal fun RowScope.ProfileQuickAction(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AppButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        style = AppButtonStyle.Tinted,
        shape = AppShapes.l,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = AppSpacing.xs, vertical = AppSpacing.m),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s),
        ) {
            AppIcon(icon, null, Modifier.size(AppSize.icon))
            AppText(text, style = AppTheme.type.footnoteEmphasized, textAlign = TextAlign.Center)
        }
    }
}

/** 已知的二态值由整行切换；读取或更新中显示进度，读取失败保留可点击的重试入口。 */
@Composable
internal fun ProfileModerationRow(
    text: String,
    checked: Boolean? = null,
    status: String? = null,
    enabled: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit = {},
) {
    AppRow(
        title = text,
        subtitle = status,
        modifier = if (checked != null) {
            Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
        } else {
            Modifier
        },
        enabled = enabled,
        onClick = if (checked == null && enabled) onClick else null,
        trailing = {
            when {
                loading -> AppActivityIndicator(Modifier.size(20.dp))
                checked != null -> AppToggle(checked = checked, onCheckedChange = null, enabled = enabled)
                enabled -> AppIcon(AppIcons.Refresh, strings.retry, Modifier.size(20.dp), tint = AppTheme.colors.tint)
            }
        },
    )
}
