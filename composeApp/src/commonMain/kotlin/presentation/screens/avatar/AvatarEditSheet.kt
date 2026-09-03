package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.galleryImagePickerType
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PreparedImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageFailure
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.SelectedImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.localizedMessage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.readBoundedBytes
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val AvatarNameMaxLength = 64
private const val AvatarDescriptionMaxLength = 256

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarEditSheet(
    avatar: AvatarProfileVo,
    state: AvatarEditState,
    imageProcessor: PrintImageProcessor,
    onDismiss: () -> Unit,
    onSaveMetadata: (AvatarMetadataDraft) -> Unit,
    onRetryStyles: () -> Unit,
    onEditCover: (SelectedImage, PreparedImage) -> Unit,
) {
    val metadataKey = arrayOf(avatar.avatarId, avatar.version, avatar.updatedAt, avatar.tags)
    var name by remember(*metadataKey) { mutableStateOf(avatar.avatarName) }
    var description by remember(*metadataKey) { mutableStateOf(avatar.avatarDescription) }
    var contentTags by remember(*metadataKey) { mutableStateOf(avatar.contentTags()) }
    var authorTags by remember(*metadataKey) { mutableStateOf(avatar.authorTagsText()) }
    var primaryStyle by remember(*metadataKey, avatar.primaryStyle) {
        mutableStateOf<AvatarStyleChoice>(AvatarStyleChoice.Unchanged)
    }
    var secondaryStyle by remember(*metadataKey, avatar.secondaryStyle) {
        mutableStateOf<AvatarStyleChoice>(AvatarStyleChoice.Unchanged)
    }
    var coverError by remember(avatar.avatarId) { mutableStateOf<String?>(null) }
    var isPreparingCover by remember(avatar.avatarId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = strings
    val isBusy = state.isSavingMetadata || isPreparingCover

    val coverPicker = rememberFilePickerLauncher(
        type = galleryImagePickerType(AvatarCoverLimits.ALLOWED_EXTENSIONS),
    ) { file ->
        if (file != null && !isPreparingCover) {
            scope.launch {
                isPreparingCover = true
                coverError = null
                try {
                    val bytes = file.readBoundedBytes(AvatarCoverLimits.MAX_FILE_BYTES)
                    when (val validation = validateAvatarCover(file.name, bytes)) {
                        AvatarCoverValidation.FileTooLarge -> {
                            coverError = locale.avatarEditFileTooLarge
                        }
                        AvatarCoverValidation.UnsupportedFormat -> {
                            coverError = locale.avatarEditUnsupportedFormat
                        }
                        is AvatarCoverValidation.Valid -> {
                            val source = SelectedImage(
                                fileName = validation.cover.fileName,
                                bytes = validation.cover.bytes,
                            )
                            val prepared = imageProcessor.prepare(source).getOrElse { failure ->
                                if (failure is CancellationException) throw failure
                                coverError = (failure as? PrintImageFailure)
                                    ?.localizedMessage(locale)
                                    ?: locale.avatarEditReadFailed
                                return@launch
                            }
                            onEditCover(source, prepared)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: PrintImageFailure.FileTooLarge) {
                    coverError = locale.avatarEditFileTooLarge
                } catch (_: Exception) {
                    coverError = locale.avatarEditReadFailed
                } finally {
                    isPreparingCover = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
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
                text = locale.avatarEditTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= AvatarNameMaxLength) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.avatarEditName) },
                supportingText = { Text("${name.length}/$AvatarNameMaxLength") },
                singleLine = true,
                enabled = !state.isSavingMetadata,
            )
            OutlinedTextField(
                value = description,
                onValueChange = {
                    if (it.length <= AvatarDescriptionMaxLength) description = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.avatarEditDescription) },
                supportingText = {
                    Text("${description.length}/$AvatarDescriptionMaxLength")
                },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSavingMetadata,
            )

            HorizontalDivider()

            Text(
                text = locale.avatarEditContentTags,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AvatarContentTag.entries.forEach { tag ->
                    FilterChip(
                        selected = tag.apiValue in contentTags,
                        onClick = {
                            contentTags = if (tag.apiValue in contentTags) {
                                contentTags - tag.apiValue
                            } else {
                                contentTags + tag.apiValue
                            }
                        },
                        enabled = !state.isSavingMetadata,
                        label = { Text(tag.localizedLabel(locale)) },
                    )
                }
            }

            Text(
                text = locale.avatarEditStyles,
                style = MaterialTheme.typography.titleMedium,
            )
            when (val styles = state.styles) {
                AvatarStylesLoadState.NotLoaded,
                AvatarStylesLoadState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(locale.avatarEditStylesLoading)
                }
                AvatarStylesLoadState.Empty -> StyleLoadMessage(
                    message = locale.avatarEditStylesEmpty,
                    retryLabel = locale.retry,
                    enabled = !state.isSavingMetadata,
                    onRetry = onRetryStyles,
                )
                is AvatarStylesLoadState.Failed -> StyleLoadMessage(
                    message = locale.avatarEditStylesLoadFailed,
                    retryLabel = locale.retry,
                    enabled = !state.isSavingMetadata,
                    onRetry = onRetryStyles,
                )
                is AvatarStylesLoadState.Ready -> {
                    AvatarStyleDropdown(
                        label = locale.avatarEditPrimaryStyle,
                        currentStyle = avatar.primaryStyle,
                        choice = primaryStyle,
                        options = styles.options,
                        noneLabel = locale.avatarEditNoStyle,
                        enabled = !state.isSavingMetadata,
                        onChoice = { primaryStyle = it },
                    )
                    AvatarStyleDropdown(
                        label = locale.avatarEditSecondaryStyle,
                        currentStyle = avatar.secondaryStyle,
                        choice = secondaryStyle,
                        options = styles.options,
                        noneLabel = locale.avatarEditNoStyle,
                        enabled = !state.isSavingMetadata,
                        onChoice = { secondaryStyle = it },
                    )
                }
            }

            OutlinedTextField(
                value = authorTags,
                onValueChange = { authorTags = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(locale.avatarEditAuthorTags) },
                supportingText = { Text(locale.avatarEditAuthorTagsHint) },
                minLines = 2,
                maxLines = 5,
                enabled = !state.isSavingMetadata,
            )

            Button(
                onClick = {
                    onSaveMetadata(
                        AvatarMetadataDraft(
                            name = name,
                            description = description,
                            contentTags = contentTags,
                            authorTags = authorTags,
                            primaryStyle = primaryStyle,
                            secondaryStyle = secondaryStyle,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !state.isSavingMetadata,
            ) {
                if (state.isSavingMetadata) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    if (state.isSavingMetadata) {
                        locale.avatarEditSavingMetadata
                    } else {
                        locale.avatarEditSaveMetadata
                    }
                )
            }

            HorizontalDivider()

            Text(
                text = locale.avatarEditCover,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = locale.avatarEditCoverHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = coverPicker::launch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPreparingCover,
            ) {
                if (isPreparingCover) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(AppIcons.Publish, contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(locale.avatarEditChooseCover)
            }

            coverError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                enabled = !isBusy,
            ) {
                Text(locale.cancel)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StyleLoadMessage(
    message: String,
    retryLabel: String,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, enabled = enabled) {
            Text(retryLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarStyleDropdown(
    label: String,
    currentStyle: String?,
    choice: AvatarStyleChoice,
    options: List<io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarStyle>,
    noneLabel: String,
    enabled: Boolean,
    onChoice: (AvatarStyleChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = when (choice) {
        AvatarStyleChoice.Unchanged -> options.firstOrNull {
            it.id == currentStyle || it.styleName == currentStyle
        }?.styleName ?: currentStyle.orEmpty().ifBlank { noneLabel }
        AvatarStyleChoice.Clear -> noneLabel
        is AvatarStyleChoice.Selected -> options.firstOrNull { it.id == choice.id }
            ?.styleName
            .orEmpty()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryNotEditable
            ),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            readOnly = true,
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = {
                    onChoice(AvatarStyleChoice.Clear)
                    expanded = false
                },
            )
            options.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.styleName) },
                    onClick = {
                        onChoice(AvatarStyleChoice.Selected(style.id))
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun AvatarContentTag.localizedLabel(locale: LocaleStrings): String = when (this) {
    AvatarContentTag.Horror -> locale.avatarEditContentHorror
    AvatarContentTag.Gore -> locale.avatarEditContentGore
    AvatarContentTag.Violence -> locale.avatarEditContentViolence
    AvatarContentTag.Adult -> locale.avatarEditContentAdult
    AvatarContentTag.Sex -> locale.avatarEditContentSex
}
