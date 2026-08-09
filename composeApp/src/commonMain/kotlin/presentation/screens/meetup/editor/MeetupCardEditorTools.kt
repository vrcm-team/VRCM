package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardUiState
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupEditorError
import io.github.vrcmteam.vrcm.presentation.screens.meetup.meetupCardLinkLabel
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MEETUP_QR_MAX_CODES
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.MeetupQrLinkType
import io.github.vrcmteam.vrcm.storage.meetup.resolvedQrLinkTypes
import io.github.vrcmteam.vrcm.storage.meetup.resolvedQrProfileLinks
import io.github.vrcmteam.vrcm.storage.meetup.templateFor

/** 编辑器工具区回调集合；均为幂等操作，由 ViewModel 决定持久化时机。 */
internal class MeetupEditorActions(
    val onTemplate: (MeetupCardTemplate) -> Unit,
    val onShowAvatar: (Boolean) -> Unit,
    val onShowPronouns: (Boolean) -> Unit,
    val onShowLanguages: (Boolean) -> Unit,
    val onShowStatus: (Boolean) -> Unit,
    val onShowStatusDescription: (Boolean) -> Unit,
    val onShowRepresentedGroup: (Boolean) -> Unit,
    val onShowShortText: (Boolean) -> Unit,
    val onShortText: (String) -> Unit,
    val onShowQrCode: (Boolean) -> Unit,
    val onQrLinkTypeToggle: (MeetupQrLinkType) -> Unit,
    val onQrProfileLinkToggle: (String) -> Unit,
    val onShowIconFrame: (Boolean) -> Unit,
    val onShowProfileEffect: (Boolean) -> Unit,
    val onShowNameplateEffect: (Boolean) -> Unit,
    val onAccent: (Long) -> Unit,
    val onScrim: (Float) -> Unit,
    val onPickProfileBackground: () -> Unit,
    val onPickLocalAlbum: () -> Unit,
    val onPickGallery: () -> Unit,
)

internal enum class MeetupEditorTab { Photo, Layout, Content, Style }

/** 照片应用方向对应的裁剪编辑范围。 */
internal fun MeetupPhotoTarget.editableOrientations(): List<MeetupOrientation> = when (this) {
    MeetupPhotoTarget.Both -> MeetupOrientation.entries
    MeetupPhotoTarget.Portrait -> listOf(MeetupOrientation.Portrait)
    MeetupPhotoTarget.Landscape -> listOf(MeetupOrientation.Landscape)
}

private val AccentSwatches = listOf(
    0xFF3F8CFF, 0xFFE85D75, 0xFF34B37E, 0xFFF2A93B,
    0xFF9B6DFF, 0xFF00B8D9, 0xFF66788A, 0xFF17263B,
)

/** 四个工具页：照片来源、模板、内容开关、样式。 */
@Composable
internal fun MeetupEditorTools(
    state: MeetupCardUiState,
    actions: MeetupEditorActions,
    photoTarget: MeetupPhotoTarget,
    onPhotoTarget: (MeetupPhotoTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(MeetupEditorTab.Photo) }
    val locale = strings
    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            MeetupEditorTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = when (tab) {
                                MeetupEditorTab.Photo -> locale.meetupCardPhoto
                                MeetupEditorTab.Layout -> locale.meetupCardLayout
                                MeetupEditorTab.Content -> locale.meetupCardContent
                                MeetupEditorTab.Style -> locale.meetupCardStyle
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedTab) {
                MeetupEditorTab.Photo -> PhotoTools(state, actions, photoTarget, onPhotoTarget)
                MeetupEditorTab.Layout -> LayoutTools(state, actions)
                MeetupEditorTab.Content -> ContentTools(state, actions)
                MeetupEditorTab.Style -> StyleTools(state, actions)
            }
        }
    }
}

/** 统一的设置分组：标题 + 圆角容器，避免整页开关平铺显得杂乱。 */
@Composable
private fun ToolSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoTools(
    state: MeetupCardUiState,
    actions: MeetupEditorActions,
    photoTarget: MeetupPhotoTarget,
    onPhotoTarget: (MeetupPhotoTarget) -> Unit,
) {
    val enabled = !state.savingPhoto
    ToolSection(strings.meetupCardPhotoTarget) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeetupPhotoTarget.entries.forEach { target ->
                FilterChip(
                    selected = photoTarget == target,
                    onClick = { onPhotoTarget(target) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = when (target) {
                                MeetupPhotoTarget.Both -> strings.meetupCardPhotoTargetBoth
                                MeetupPhotoTarget.Portrait -> strings.meetupCardPortrait
                                MeetupPhotoTarget.Landscape -> strings.meetupCardLandscape
                            },
                        )
                    },
                )
            }
        }
    }
    ToolSection(strings.meetupCardPhoto) {
        PhotoSourceRow(
            label = strings.meetupCardProfileBackground,
            icon = AppIcons.Person,
            enabled = enabled,
            onClick = actions.onPickProfileBackground,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        PhotoSourceRow(
            label = strings.meetupCardAlbum,
            icon = AppIcons.Publish,
            enabled = enabled,
            onClick = actions.onPickLocalAlbum,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        PhotoSourceRow(
            label = strings.meetupCardGallery,
            icon = AppIcons.Mirror,
            enabled = enabled,
            onClick = actions.onPickGallery,
        )
    }
}

/** 照片来源入口：图标 + 文案的整行可点条目。 */
@Composable
private fun PhotoSourceRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutTools(state: MeetupCardUiState, actions: MeetupEditorActions) {
    // 版式按方向分别保存，这里改的是预览中选中的那个方向。
    val orientationLabel = when (state.orientation) {
        MeetupOrientation.Portrait -> strings.meetupCardPortrait
        MeetupOrientation.Landscape -> strings.meetupCardLandscape
    }
    ToolSection("${strings.meetupCardLayout} · $orientationLabel") {
        Text(
            text = strings.meetupCardLayoutPerOrientation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeetupCardTemplate.entries.forEach { template ->
                FilterChip(
                    selected = state.config.templateFor(state.orientation) == template,
                    onClick = { actions.onTemplate(template) },
                    label = {
                        Text(
                            text = when (template) {
                                MeetupCardTemplate.InfoBar -> strings.meetupCardInfoBar
                                MeetupCardTemplate.Spotlight -> strings.meetupCardSpotlight
                                MeetupCardTemplate.SideTag -> strings.meetupCardSideTag
                            },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentTools(state: MeetupCardUiState, actions: MeetupEditorActions) {
    val config = state.config
    ToolSection(strings.meetupCardContent) {
        ToggleRow(strings.meetupCardShowAvatar, config.showAvatar, actions.onShowAvatar)
        ToggleRow(strings.meetupCardShowPronouns, config.showPronouns, actions.onShowPronouns)
        ToggleRow(strings.meetupCardShowLanguages, config.showLanguages, actions.onShowLanguages)
        ToggleRow(strings.meetupCardShowStatus, config.showStatus, actions.onShowStatus)
        ToggleRow(
            strings.meetupCardShowStatusDescription,
            config.showStatusDescription,
            actions.onShowStatusDescription,
        )
        // 没有主选群组时开关没有意义；已经开着就继续显示，否则这次没拉到群组
        // 用户连关掉它的入口都找不到。
        if (config.profile.representedGroup != null || config.showRepresentedGroup) {
            ToggleRow(
                strings.meetupCardShowGroupBanner,
                config.showRepresentedGroup,
                actions.onShowRepresentedGroup,
            )
        }
    }
    ToolSection(strings.meetupCardShortText) {
        ToggleRow(strings.meetupCardShortText, config.showShortText, actions.onShowShortText)
        if (config.showShortText) {
            var text by remember(state.ownerUserId) { mutableStateOf(config.shortText) }
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value
                    actions.onShortText(value)
                },
                isError = state.editorError is MeetupEditorError.ShortTextTooLong,
                supportingText = {
                    if (state.editorError is MeetupEditorError.ShortTextTooLong) {
                        Text(
                            text = strings.meetupCardShortTextTooLong,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    ToolSection(strings.meetupCardShowQrCode) {
        ToggleRow(strings.meetupCardShowQrCode, config.showQrCode, actions.onShowQrCode)
        if (config.showQrCode) {
            Text(
                text = strings.meetupCardQrLinkType,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val selectedTypes = config.resolvedQrLinkTypes()
            val selectedLinks = config.resolvedQrProfileLinks()
            // 到达上限后只能取消已选项，避免码多到把铭牌挤出卡片。
            val atLimit = selectedTypes.size + selectedLinks.size >= MEETUP_QR_MAX_CODES
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeetupQrLinkType.entries.forEach { linkType ->
                    val selected = linkType in selectedTypes
                    FilterChip(
                        selected = selected,
                        enabled = selected || !atLimit,
                        onClick = { actions.onQrLinkTypeToggle(linkType) },
                        label = {
                            Text(
                                text = when (linkType) {
                                    MeetupQrLinkType.VrchatWeb -> strings.meetupCardQrLinkVrchat
                                    MeetupQrLinkType.VrcmDeepLink -> strings.meetupCardQrLinkVrcm
                                },
                            )
                        },
                    )
                }
            }
            val profileLinks = config.profile.links
            if (profileLinks.isNotEmpty()) {
                Text(
                    text = strings.meetupCardQrProfileLinks,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profileLinks.forEach { link ->
                        val selected = link in selectedLinks
                        FilterChip(
                            selected = selected,
                            enabled = selected || !atLimit,
                            onClick = { actions.onQrProfileLinkToggle(link) },
                            leadingIcon = {
                                Icon(
                                    imageVector = WebIcons.selectIcon(link) ?: AppIcons.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = meetupCardLinkLabel(link),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            if (atLimit) {
                Text(
                    text = strings.meetupCardQrLimit
                        .replaceFirst("%d", MEETUP_QR_MAX_CODES.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleTools(state: MeetupCardUiState, actions: MeetupEditorActions) {
    val config = state.config
    ToolSection(strings.meetupCardAccentColor) {
        // 色块必须是正圆：Row 里 size 会服从父约束，窄屏放不下最后一个就会被
        // 压成椭圆。改用 Flow 布局换行，并用 requiredSize 锁死尺寸。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentSwatches.forEach { argb ->
                val selected = config.accentArgb == argb
                Box(
                    modifier = Modifier
                        .requiredSize(34.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { actions.onAccent(argb) },
                )
            }
        }
    }
    ToolSection(strings.meetupCardScrim) {
        // 拖动期间只更新本地值，手势结束才提交持久化。
        var scrimDraft by remember(config.scrimAlpha) { mutableFloatStateOf(config.scrimAlpha) }
        Slider(
            value = scrimDraft,
            onValueChange = { scrimDraft = it },
            onValueChangeFinished = { actions.onScrim(scrimDraft) },
            valueRange = 0f..0.8f,
        )
    }
    ToolSection(strings.meetupCardStyle) {
        ToggleRow(strings.meetupCardIconFrame, config.showIconFrame, actions.onShowIconFrame)
        ToggleRow(
            strings.meetupCardProfileEffect,
            config.showProfileEffect,
            actions.onShowProfileEffect,
        )
        ToggleRow(
            strings.meetupCardNameplateEffect,
            config.showNameplateEffect,
            actions.onShowNameplateEffect,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
