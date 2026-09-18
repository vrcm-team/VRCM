package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.rememberConsumeRemainingUpwardScrollConnection

private const val WorldNameMaxLength = 64
private const val WorldDescriptionMaxLength = 256

internal fun WorldMetadataEditNotice.localizedToast(locale: LocaleStrings): ToastText =
    when (this) {
        WorldMetadataEditNotice.InvalidName -> ToastText.Error(locale.worldEditInvalidName)
        WorldMetadataEditNotice.InvalidCapacity -> ToastText.Error(locale.worldEditInvalidCapacity)
        WorldMetadataEditNotice.InvalidRecommendedCapacity ->
            ToastText.Error(locale.worldEditInvalidRecommendedCapacity)
        WorldMetadataEditNotice.NoChanges -> ToastText.Info(locale.worldEditNoChanges)
        WorldMetadataEditNotice.Saved -> ToastText.Success(locale.worldEditSaved)
        is WorldMetadataEditNotice.SaveFailed -> ToastText.Error(
            message ?: locale.worldEditSaveFailed
        )
    }

@Composable
internal fun WorldMetadataEditSheet(
    world: WorldProfileVo,
    state: WorldMetadataEditState,
    onDismiss: () -> Unit,
    onSave: (WorldMetadataDraft) -> Unit,
) {
    var name by remember(world.worldId, world.version, world.updatedAt, world.worldName) {
        mutableStateOf(world.worldName)
    }
    var description by remember(
        world.worldId,
        world.version,
        world.updatedAt,
        world.worldDescription,
    ) { mutableStateOf(world.worldDescription) }
    var capacity by remember(world.worldId, world.version, world.updatedAt, world.capacity) {
        mutableStateOf(world.capacity.toString())
    }
    var recommendedCapacity by remember(
        world.worldId,
        world.version,
        world.updatedAt,
        world.recommendedCapacity,
    ) { mutableStateOf(world.recommendedCapacity.toString()) }
    var tags by remember(world.worldId, world.version, world.updatedAt, world.rawTags) {
        mutableStateOf(world.rawTags.joinToString("\n"))
    }
    var allowedDomains by remember(
        world.worldId,
        world.version,
        world.updatedAt,
        world.allowedDomains,
    ) { mutableStateOf(world.allowedDomains.joinToString("\n")) }
    val locale = strings
    val formNestedScrollConnection = rememberConsumeRemainingUpwardScrollConnection()

    AppSheet(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .nestedScroll(formNestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = locale.worldEditTitle,
                style = AppTheme.type.title2,
                color = AppTheme.colors.tint,
            )
            AppTextField(
                value = name,
                onValueChange = { if (it.length <= WorldNameMaxLength) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.worldEditName) },
                supportingText = { AppText("${name.length}/$WorldNameMaxLength") },
                singleLine = true,
                enabled = !state.isSaving,
            )
            AppTextField(
                value = description,
                onValueChange = {
                    if (it.length <= WorldDescriptionMaxLength) description = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.worldEditDescription) },
                supportingText = {
                    AppText("${description.length}/$WorldDescriptionMaxLength")
                },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSaving,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { AppText(locale.worldEditCapacity) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AppTextField(
                    value = recommendedCapacity,
                    onValueChange = { recommendedCapacity = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { AppText(locale.worldEditRecommendedCapacity) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            AppText(
                text = locale.worldEditCapacityHint,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            AppTextField(
                value = tags,
                onValueChange = { tags = it },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.worldEditTags) },
                supportingText = { AppText(locale.worldEditTagsHint) },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSaving,
            )
            AppTextField(
                value = allowedDomains,
                onValueChange = { allowedDomains = it },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.worldEditAllowedDomains) },
                supportingText = { AppText(locale.worldEditAllowedDomainsHint) },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSaving,
            )
            AppButton(
                onClick = {
                    onSave(
                        WorldMetadataDraft(
                            name = name,
                            description = description,
                            capacity = capacity,
                            recommendedCapacity = recommendedCapacity,
                            tags = tags,
                            allowedDomains = allowedDomains,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
                style = AppButtonStyle.Prominent,
            ) {
                if (state.isSaving) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AppText(if (state.isSaving) locale.worldEditSaving else locale.worldEditSave)
            }
            AppButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                enabled = !state.isSaving,
                style = AppButtonStyle.Plain,
            ) {
                AppText(locale.cancel)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
