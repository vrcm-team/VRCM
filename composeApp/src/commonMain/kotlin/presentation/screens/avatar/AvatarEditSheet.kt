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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.vrcmteam.vrcm.presentation.screens.gallery.readSelectedImage
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
    impostorState: AvatarImpostorState,
    imageProcessor: PrintImageProcessor,
    onDismiss: () -> Unit,
    onSaveMetadata: (AvatarMetadataDraft) -> Unit,
    onRetryStyles: () -> Unit,
    onEnqueueImpostor: () -> Unit,
    onUpdatePublication: (AvatarPublicationStatus) -> Unit,
    onEditCover: (SelectedImage, PreparedImage) -> Unit,
    onEditGallery: (SelectedImage, PreparedImage) -> Unit,
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
    var galleryError by remember(avatar.avatarId) { mutableStateOf<String?>(null) }
    var isPreparingGallery by remember(avatar.avatarId) { mutableStateOf(false) }
    var showPublicConfirmation by remember(avatar.avatarId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = strings
    val isRemoteUpdateBusy = state.isSavingMetadata || state.isUpdatingPublication
    val isBusy = isRemoteUpdateBusy || isPreparingCover || isPreparingGallery ||
        impostorState.isSubmitting || impostorState.isLoadingQueueEstimate
    val latestIsBusy = rememberUpdatedState(isBusy)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { targetValue ->
            targetValue != SheetValue.Hidden || !latestIsBusy.value
        },
    )
    LaunchedEffect(state.publication) {
        if (state.publication != AvatarPublicationStatus.Private) {
            showPublicConfirmation = false
        }
    }

    val coverPicker = rememberFilePickerLauncher(
        type = galleryImagePickerType(AvatarCoverLimits.ALLOWED_EXTENSIONS),
    ) { file ->
        if (file != null && !isBusy) {
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

    val galleryPicker = rememberFilePickerLauncher(
        type = galleryImagePickerType(AvatarGalleryLimits.ALLOWED_EXTENSIONS),
    ) { file ->
        if (file != null && !isBusy) {
            scope.launch {
                isPreparingGallery = true
                galleryError = null
                try {
                    val source = readSelectedImage(file.name) {
                        file.readBoundedBytes(AvatarGalleryLimits.MAX_FILE_BYTES)
                    }.getOrElse { failure ->
                        galleryError = if (failure is PrintImageFailure.FileTooLarge) {
                            locale.avatarGalleryFileTooLarge
                        } else {
                            locale.avatarGalleryReadFailed
                        }
                        return@launch
                    }
                    val prepared = imageProcessor.prepare(source).getOrElse { failure ->
                        if (failure is CancellationException) throw failure
                        galleryError = (failure as? PrintImageFailure)
                            ?.localizedMessage(locale)
                            ?: locale.avatarGalleryReadFailed
                        return@launch
                    }
                    onEditGallery(source, prepared)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    galleryError = locale.avatarGalleryReadFailed
                } finally {
                    isPreparingGallery = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !isBusy,
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
                enabled = !isBusy,
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
                enabled = !isBusy,
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
                enabled = name.isNotBlank() && !isBusy,
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

            state.publication?.let { currentPublication ->
                Text(
                    text = locale.avatarEditPublication,
                    style = MaterialTheme.typography.titleMedium,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AvatarPublicationStatus.entries.forEachIndexed { index, publication ->
                        SegmentedButton(
                            selected = publication == currentPublication,
                            onClick = {
                                if (publication == currentPublication) return@SegmentedButton
                                if (publication == AvatarPublicationStatus.Public) {
                                    showPublicConfirmation = true
                                } else {
                                    onUpdatePublication(publication)
                                }
                            },
                            enabled = !isBusy,
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                AvatarPublicationStatus.entries.size,
                            ),
                            label = {
                                Text(
                                    when (publication) {
                                        AvatarPublicationStatus.Private ->
                                            locale.avatarEditPublicationPrivate
                                        AvatarPublicationStatus.Public ->
                                            locale.avatarEditPublicationPublic
                                    }
                                )
                            },
                        )
                    }
                }
                if (state.isUpdatingPublication) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = locale.avatarEditUpdatingPublication,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()
            }

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
                enabled = !isBusy,
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

            HorizontalDivider()
            Text(
                text = locale.avatarGalleryTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = locale.avatarGalleryHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = galleryPicker::launch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
            ) {
                if (isPreparingGallery) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(AppIcons.Publish, contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(locale.avatarGalleryChooseImage)
            }
            galleryError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            AvatarImpostorSection(
                state = impostorState,
                localPreparationInProgress = isPreparingCover || isPreparingGallery,
                onEnqueue = onEnqueueImpostor,
            )

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

    if (showPublicConfirmation) {
        AlertDialog(
            onDismissRequest = { showPublicConfirmation = false },
            title = { Text(locale.avatarEditPublishConfirmTitle) },
            text = { Text(locale.avatarEditPublishConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPublicConfirmation = false
                        onUpdatePublication(AvatarPublicationStatus.Public)
                    },
                    enabled = !isBusy &&
                        state.publication == AvatarPublicationStatus.Private,
                ) {
                    Text(locale.avatarEditPublishConfirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublicConfirmation = false }) {
                    Text(locale.cancel)
                }
            },
        )
    }
}

@Composable
private fun AvatarImpostorSection(
    state: AvatarImpostorState,
    localPreparationInProgress: Boolean,
    onEnqueue: () -> Unit,
) {
    val locale = strings
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = locale.avatarImpostorTitle,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (state.hasImpostor) {
                locale.avatarImpostorAvailable
            } else {
                locale.avatarImpostorUnavailable
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = locale.avatarImpostorTaskStatus,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = state.taskState?.localizedImpostorState(locale)
                ?: locale.avatarImpostorTaskEmpty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.isLoadingQueueEstimate -> Text(
                text = locale.avatarImpostorQueueEstimateLoading,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.estimatedQueueSeconds != null -> Text(
                text = locale.avatarImpostorQueueEstimateMinutes.replace(
                    "%minutes%",
                    ((state.estimatedQueueSeconds + 59) / 60).coerceAtLeast(1).toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.queueEstimateFailed -> Text(
                text = locale.avatarImpostorQueueEstimateUnavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.failure?.let { failure ->
            Text(
                text = failure.localizedMessage(locale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedButton(
            onClick = onEnqueue,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canBuild && !localPreparationInProgress,
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(AppIcons.Update, contentDescription = null, Modifier.size(18.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    state.isSubmitting -> locale.avatarImpostorSubmitting
                    state.hasImpostor -> locale.avatarImpostorRebuild
                    else -> locale.avatarImpostorCreate
                }
            )
        }
    }
}

private fun String.localizedImpostorState(
    locale: io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings,
): String = when (lowercase()) {
    "queued", "pending" -> locale.avatarImpostorStatusQueued
    "processing", "running", "in_progress", "in-progress" ->
        locale.avatarImpostorStatusProcessing
    "complete", "completed", "success", "succeeded" ->
        locale.avatarImpostorStatusCompleted
    "failed", "failure", "error", "cancelled", "canceled" ->
        locale.avatarImpostorStatusFailed
    else -> locale.avatarImpostorStatusUnknown.replace("%s", this)
}

private fun AvatarImpostorFailure.localizedMessage(
    locale: io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings,
): String = when (this) {
    AvatarImpostorFailure.Authentication -> locale.avatarImpostorAuthenticationFailed
    AvatarImpostorFailure.Permission -> locale.avatarImpostorPermissionFailed
    AvatarImpostorFailure.NotFound -> locale.avatarImpostorNotFound
    AvatarImpostorFailure.Conflict -> locale.avatarImpostorConflict
    AvatarImpostorFailure.RateLimited -> locale.avatarImpostorRateLimited
    AvatarImpostorFailure.Server -> locale.avatarImpostorServerFailed
    AvatarImpostorFailure.InvalidResponse,
    AvatarImpostorFailure.Unknown -> locale.avatarImpostorUnknownFailed
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
