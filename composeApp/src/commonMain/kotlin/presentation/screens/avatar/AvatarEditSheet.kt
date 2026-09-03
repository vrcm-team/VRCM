package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onSaveMetadata: (String, String) -> Unit,
    onEnqueueImpostor: () -> Unit,
    onEditCover: (SelectedImage, PreparedImage) -> Unit,
) {
    var name by remember(avatar.avatarId) { mutableStateOf(avatar.avatarName) }
    var description by remember(avatar.avatarId) { mutableStateOf(avatar.avatarDescription) }
    var coverError by remember(avatar.avatarId) { mutableStateOf<String?>(null) }
    var isPreparingCover by remember(avatar.avatarId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = strings
    val isBusy = state.isSavingMetadata || isPreparingCover || impostorState.isSubmitting

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
            Button(
                onClick = { onSaveMetadata(name, description) },
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

            HorizontalDivider()

            AvatarImpostorSection(
                state = impostorState,
                localPreparationInProgress = isPreparingCover,
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
