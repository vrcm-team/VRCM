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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

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

@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = locale.worldEditTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= WorldNameMaxLength) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.worldEditName) },
                supportingText = { Text("${name.length}/$WorldNameMaxLength") },
                singleLine = true,
                enabled = !state.isSaving,
            )
            OutlinedTextField(
                value = description,
                onValueChange = {
                    if (it.length <= WorldDescriptionMaxLength) description = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.worldEditDescription) },
                supportingText = {
                    Text("${description.length}/$WorldDescriptionMaxLength")
                },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSaving,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text(locale.worldEditCapacity) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = recommendedCapacity,
                    onValueChange = { recommendedCapacity = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text(locale.worldEditRecommendedCapacity) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(
                text = locale.worldEditCapacityHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.worldEditTags) },
                supportingText = { Text(locale.worldEditTagsHint) },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSaving,
            )
            OutlinedTextField(
                value = allowedDomains,
                onValueChange = { allowedDomains = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.worldEditAllowedDomains) },
                supportingText = { Text(locale.worldEditAllowedDomainsHint) },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSaving,
            )
            Button(
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
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (state.isSaving) locale.worldEditSaving else locale.worldEditSave)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                enabled = !state.isSaving,
            ) {
                Text(locale.cancel)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
