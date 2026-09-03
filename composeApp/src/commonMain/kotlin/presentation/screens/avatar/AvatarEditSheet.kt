package io.github.vrcmteam.vrcm.presentation.screens.avatar

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    imageProcessor: PrintImageProcessor,
    onDismiss: () -> Unit,
    onSaveMetadata: (String, String) -> Unit,
    onUpdatePublication: (AvatarPublicationStatus) -> Unit,
    onEditCover: (SelectedImage, PreparedImage) -> Unit,
) {
    var name by remember(avatar.avatarId) { mutableStateOf(avatar.avatarName) }
    var description by remember(avatar.avatarId) { mutableStateOf(avatar.avatarDescription) }
    var coverError by remember(avatar.avatarId) { mutableStateOf<String?>(null) }
    var isPreparingCover by remember(avatar.avatarId) { mutableStateOf(false) }
    var showPublicConfirmation by remember(avatar.avatarId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = strings
    val isRemoteUpdateBusy = state.isSavingMetadata || state.isUpdatingPublication
    val isBusy = isRemoteUpdateBusy || isPreparingCover
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
                enabled = !isRemoteUpdateBusy,
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
                enabled = !isRemoteUpdateBusy,
            )
            Button(
                onClick = { onSaveMetadata(name, description) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !isRemoteUpdateBusy,
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
