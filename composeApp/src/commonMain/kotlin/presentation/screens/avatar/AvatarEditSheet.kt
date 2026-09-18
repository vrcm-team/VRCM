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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppFilterChip
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenuItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPopUpButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegment
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegmentedRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheetValue
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppSheetState
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
import io.github.vrcmteam.vrcm.presentation.supports.rememberConsumeRemainingUpwardScrollConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val AvatarNameMaxLength = 64
private const val AvatarDescriptionMaxLength = 256

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
    val sheetState = rememberAppSheetState(
        confirmValueChange = { targetValue ->
            targetValue != AppSheetValue.Hidden || !latestIsBusy.value
        },
    )
    val formNestedScrollConnection = rememberConsumeRemainingUpwardScrollConnection()
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

    AppSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !isBusy,
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
                text = locale.avatarEditTitle,
                style = AppTheme.type.title2,
                color = AppTheme.colors.tint,
            )

            AppTextField(
                value = name,
                onValueChange = { if (it.length <= AvatarNameMaxLength) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.avatarEditName) },
                supportingText = { AppText("${name.length}/$AvatarNameMaxLength") },
                singleLine = true,
                enabled = !isBusy,
            )
            AppTextField(
                value = description,
                onValueChange = {
                    if (it.length <= AvatarDescriptionMaxLength) description = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.avatarEditDescription) },
                supportingText = {
                    AppText("${description.length}/$AvatarDescriptionMaxLength")
                },
                minLines = 3,
                maxLines = 6,
                enabled = !isBusy,
            )

            AppDivider()

            AppText(
                text = locale.avatarEditContentTags,
                style = AppTheme.type.headline,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AvatarContentTag.entries.forEach { tag ->
                    AppFilterChip(
                        selected = tag.apiValue in contentTags,
                        onClick = {
                            contentTags = if (tag.apiValue in contentTags) {
                                contentTags - tag.apiValue
                            } else {
                                contentTags + tag.apiValue
                            }
                        },
                        enabled = !state.isSavingMetadata,
                        label = { AppText(tag.localizedLabel(locale)) },
                    )
                }
            }

            AppText(
                text = locale.avatarEditStyles,
                style = AppTheme.type.headline,
            )
            when (val styles = state.styles) {
                AvatarStylesLoadState.NotLoaded,
                AvatarStylesLoadState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppActivityIndicator(modifier = Modifier.size(18.dp))
                    AppText(locale.avatarEditStylesLoading)
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

            AppTextField(
                value = authorTags,
                onValueChange = { authorTags = it },
                modifier = Modifier.fillMaxWidth(),
                label = { AppText(locale.avatarEditAuthorTags) },
                supportingText = { AppText(locale.avatarEditAuthorTagsHint) },
                minLines = 2,
                maxLines = 5,
                enabled = !state.isSavingMetadata,
            )

            AppButton(
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
                style = AppButtonStyle.Prominent,
            ) {
                if (state.isSavingMetadata) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AppText(
                    if (state.isSavingMetadata) {
                        locale.avatarEditSavingMetadata
                    } else {
                        locale.avatarEditSaveMetadata
                    }
                )
            }

            AppDivider()

            state.publication?.let { currentPublication ->
                AppText(
                    text = locale.avatarEditPublication,
                    style = AppTheme.type.headline,
                )
                AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
                    AvatarPublicationStatus.entries.forEachIndexed { index, publication ->
                        AppSegment(
                            selected = publication == currentPublication,
                            onClick = {
                                if (publication == currentPublication) return@AppSegment
                                if (publication == AvatarPublicationStatus.Public) {
                                    showPublicConfirmation = true
                                } else {
                                    onUpdatePublication(publication)
                                }
                            },
                            enabled = !isBusy,
                            label = {
                                AppText(
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
                        AppActivityIndicator(
                            modifier = Modifier.size(18.dp),
                        )
                        AppText(
                            text = locale.avatarEditUpdatingPublication,
                            style = AppTheme.type.caption1,
                            color = AppTheme.colors.secondaryLabel,
                        )
                    }
                }

                AppDivider()
            }

            AppText(
                text = locale.avatarEditCover,
                style = AppTheme.type.headline,
            )
            AppText(
                text = locale.avatarEditCoverHint,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )

            AppButton(
                onClick = coverPicker::launch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                style = AppButtonStyle.Gray,
            ) {
                if (isPreparingCover) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    AppIcon(AppIcons.Publish, contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                AppText(locale.avatarEditChooseCover)
            }

            coverError?.let { error ->
                AppText(
                    text = error,
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.destructive,
                )
            }

            AppDivider()
            AppText(
                text = locale.avatarGalleryTitle,
                style = AppTheme.type.headline,
            )
            AppText(
                text = locale.avatarGalleryHint,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            AppButton(
                onClick = galleryPicker::launch,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                style = AppButtonStyle.Gray,
            ) {
                if (isPreparingGallery) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    AppIcon(AppIcons.Publish, contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                AppText(locale.avatarGalleryChooseImage)
            }
            galleryError?.let { error ->
                AppText(
                    text = error,
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.destructive,
                )
            }

            AvatarImpostorSection(
                state = impostorState,
                localPreparationInProgress = isPreparingCover || isPreparingGallery,
                onEnqueue = onEnqueueImpostor,
            )

            AppButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                enabled = !isBusy,
                style = AppButtonStyle.Plain,
            ) {
                AppText(locale.cancel)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showPublicConfirmation) {
        AppAlert(
            onDismissRequest = { showPublicConfirmation = false },
            title = { AppText(locale.avatarEditPublishConfirmTitle) },
            text = { AppText(locale.avatarEditPublishConfirmMessage) },
            confirmButton = {
                AppButton(
                    onClick = {
                        showPublicConfirmation = false
                        onUpdatePublication(AvatarPublicationStatus.Public)
                    },
                    enabled = !isBusy &&
                        state.publication == AvatarPublicationStatus.Private,
                    style = AppButtonStyle.Plain,
                ) {
                    AppText(locale.avatarEditPublishConfirmAction)
                }
            },
            dismissButton = {
                AppButton(onClick = { showPublicConfirmation = false }, style = AppButtonStyle.Plain) {
                    AppText(locale.cancel)
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
        AppText(
            text = locale.avatarImpostorTitle,
            style = AppTheme.type.headline,
        )
        AppText(
            text = if (state.hasImpostor) {
                locale.avatarImpostorAvailable
            } else {
                locale.avatarImpostorUnavailable
            },
            style = AppTheme.type.subheadline,
            color = AppTheme.colors.secondaryLabel,
        )
        AppText(
            text = locale.avatarImpostorTaskStatus,
            style = AppTheme.type.subheadlineEmphasized,
        )
        AppText(
            text = state.taskState?.localizedImpostorState(locale)
                ?: locale.avatarImpostorTaskEmpty,
            style = AppTheme.type.subheadline,
            color = AppTheme.colors.secondaryLabel,
        )

        when {
            state.isLoadingQueueEstimate -> AppText(
                text = locale.avatarImpostorQueueEstimateLoading,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            state.estimatedQueueSeconds != null -> AppText(
                text = locale.avatarImpostorQueueEstimateMinutes.replace(
                    "%minutes%",
                    ((state.estimatedQueueSeconds + 59) / 60).coerceAtLeast(1).toString(),
                ),
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            state.queueEstimateFailed -> AppText(
                text = locale.avatarImpostorQueueEstimateUnavailable,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
        }

        state.failure?.let { failure ->
            AppText(
                text = failure.localizedMessage(locale),
                style = AppTheme.type.caption1,
                color = AppTheme.colors.destructive,
            )
        }

        AppButton(
            onClick = onEnqueue,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canBuild && !localPreparationInProgress,
            style = AppButtonStyle.Gray,
        ) {
            if (state.isSubmitting) {
                AppActivityIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current,
                )
            } else {
                AppIcon(AppIcons.Refresh, contentDescription = null, Modifier.size(18.dp))
            }
            Spacer(Modifier.size(8.dp))
            AppText(
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
        AppText(
            text = message,
            style = AppTheme.type.caption1,
            color = AppTheme.colors.secondaryLabel,
        )
        AppButton(onClick = onRetry, enabled = enabled, style = AppButtonStyle.Plain) {
            AppText(retryLabel)
        }
    }
}

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

    AppPopUpButton(
        value = selectedText,
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
        label = label,
        enabled = enabled,
    ) {
        AppMenuItem(
            text = { AppText(noneLabel) },
            onClick = {
                onChoice(AvatarStyleChoice.Clear)
                expanded = false
            },
        )
        options.forEach { style ->
            AppMenuItem(
                text = { AppText(style.styleName) },
                onClick = {
                    onChoice(AvatarStyleChoice.Selected(style.id))
                    expanded = false
                },
            )
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
