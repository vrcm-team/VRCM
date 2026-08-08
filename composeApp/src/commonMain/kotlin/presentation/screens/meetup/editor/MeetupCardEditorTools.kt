package io.github.vrcmteam.vrcm.presentation.screens.meetup.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupCardUiState
import io.github.vrcmteam.vrcm.presentation.screens.meetup.MeetupEditorError
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation

/** 编辑器工具区回调集合；均为幂等操作，由 ViewModel 决定持久化时机。 */
internal class MeetupEditorActions(
    val onTemplate: (MeetupCardTemplate) -> Unit,
    val onShowAvatar: (Boolean) -> Unit,
    val onShowPronouns: (Boolean) -> Unit,
    val onShowLanguages: (Boolean) -> Unit,
    val onShowStatus: (Boolean) -> Unit,
    val onShowStatusDescription: (Boolean) -> Unit,
    val onShowShortText: (Boolean) -> Unit,
    val onShortText: (String) -> Unit,
    val onShowQrCode: (Boolean) -> Unit,
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
        TabRow(selectedTabIndex = selectedTab.ordinal) {
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
                        )
                    },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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

@Composable
private fun PhotoTools(
    state: MeetupCardUiState,
    actions: MeetupEditorActions,
    photoTarget: MeetupPhotoTarget,
    onPhotoTarget: (MeetupPhotoTarget) -> Unit,
) {
    val enabled = !state.savingPhoto
    Text(strings.meetupCardPhotoTarget, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    HorizontalDivider()
    OutlinedButton(
        onClick = actions.onPickProfileBackground,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(strings.meetupCardProfileBackground) }
    OutlinedButton(
        onClick = actions.onPickLocalAlbum,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(strings.meetupCardAlbum) }
    OutlinedButton(
        onClick = actions.onPickGallery,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(strings.meetupCardGallery) }
}

@Composable
private fun LayoutTools(state: MeetupCardUiState, actions: MeetupEditorActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MeetupCardTemplate.entries.forEach { template ->
            FilterChip(
                selected = state.config.template == template,
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

@Composable
private fun ContentTools(state: MeetupCardUiState, actions: MeetupEditorActions) {
    val config = state.config
    ToggleRow(strings.meetupCardShowAvatar, config.showAvatar, actions.onShowAvatar)
    ToggleRow(strings.meetupCardShowPronouns, config.showPronouns, actions.onShowPronouns)
    ToggleRow(strings.meetupCardShowLanguages, config.showLanguages, actions.onShowLanguages)
    ToggleRow(strings.meetupCardShowStatus, config.showStatus, actions.onShowStatus)
    ToggleRow(
        strings.meetupCardShowStatusDescription,
        config.showStatusDescription,
        actions.onShowStatusDescription,
    )
    HorizontalDivider()
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
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HorizontalDivider()
    ToggleRow(strings.meetupCardShowQrCode, config.showQrCode, actions.onShowQrCode)
}

@Composable
private fun StyleTools(state: MeetupCardUiState, actions: MeetupEditorActions) {
    val config = state.config
    Text(strings.meetupCardAccentColor, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AccentSwatches.forEach { argb ->
            val selected = config.accentArgb == argb
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { actions.onAccent(argb) },
            )
        }
    }
    Text(strings.meetupCardScrim, style = MaterialTheme.typography.titleSmall)
    // 拖动期间只更新本地值，手势结束才提交持久化。
    var scrimDraft by remember(config.scrimAlpha) { mutableFloatStateOf(config.scrimAlpha) }
    Slider(
        value = scrimDraft,
        onValueChange = { scrimDraft = it },
        onValueChangeFinished = { actions.onScrim(scrimDraft) },
        valueRange = 0f..0.8f,
    )
    HorizontalDivider()
    ToggleRow(strings.meetupCardIconFrame, config.showIconFrame, actions.onShowIconFrame)
    ToggleRow(strings.meetupCardProfileEffect, config.showProfileEffect, actions.onShowProfileEffect)
    ToggleRow(
        strings.meetupCardNameplateEffect,
        config.showNameplateEffect,
        actions.onShowNameplateEffect,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
