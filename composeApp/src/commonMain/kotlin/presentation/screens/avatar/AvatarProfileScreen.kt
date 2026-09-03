package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.core.extensions.toLocalDate
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.OfficialUrlShareButton
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.extensions.simpleFormat
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarPlatformInfo
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.ImagePreviewDialog
import io.github.vrcmteam.vrcm.presentation.compoments.LocationDialogContent
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageEditorTarget
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.handoffPreparedImageToEditor
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.components.FavoriteGroupBottomSheet
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

internal fun AvatarProfileNotice.localizedToast(locale: LocaleStrings): ToastText = when (this) {
    AvatarProfileNotice.Banned -> ToastText.Error(locale.avatarProfileBanned)
    AvatarProfileNotice.Switched -> ToastText.Success(locale.avatarProfileSwitched)
    AvatarProfileNotice.Copied -> ToastText.Success(locale.avatarProfileCopied)
    is AvatarProfileNotice.SelectionFailed -> ToastText.Error(
        message ?: locale.avatarProfileSelectFailed
    )
    AvatarProfileNotice.InvalidName -> ToastText.Error(locale.avatarEditInvalidName)
    AvatarProfileNotice.InvalidContentTags ->
        ToastText.Error(locale.avatarEditInvalidContentTags)
    AvatarProfileNotice.InvalidPrimaryStyle ->
        ToastText.Error(locale.avatarEditInvalidStyle)
    AvatarProfileNotice.InvalidSecondaryStyle ->
        ToastText.Error(locale.avatarEditInvalidStyle)
    AvatarProfileNotice.NoMetadataChanges -> ToastText.Info(locale.avatarEditNoChanges)
    AvatarProfileNotice.MetadataSaved -> ToastText.Success(locale.avatarEditMetadataSaved)
    is AvatarProfileNotice.MetadataSaveFailed -> ToastText.Error(
        message ?: locale.avatarEditMetadataSaveFailed
    )
    AvatarProfileNotice.CoverSaved -> ToastText.Success(locale.avatarEditCoverSaved)
    AvatarProfileNotice.GalleryUploaded -> ToastText.Success(locale.avatarGalleryUploaded)
    AvatarProfileNotice.PublicationMadePublic ->
        ToastText.Success(locale.avatarEditPublicationMadePublic)
    AvatarProfileNotice.PublicationMadePrivate ->
        ToastText.Success(locale.avatarEditPublicationMadePrivate)
    is AvatarProfileNotice.PublicationUpdateFailed -> ToastText.Error(
        when (reason) {
            AvatarPublicationFailure.BadRequest -> locale.avatarEditPublicationBadRequest
            AvatarPublicationFailure.Unauthorized -> locale.avatarEditPublicationUnauthorized
            AvatarPublicationFailure.Forbidden -> locale.avatarEditPublicationForbidden
            AvatarPublicationFailure.NotFound -> locale.avatarEditPublicationNotFound
            AvatarPublicationFailure.Other -> locale.avatarEditPublicationFailed
        }
    )
}

internal fun AvatarActionAvailability.localizedButtonText(locale: LocaleStrings): String = when (this) {
    AvatarActionAvailability.Checking -> locale.avatarProfileActionChecking
    AvatarActionAvailability.Current -> locale.avatarProfileActionCurrent
    AvatarActionAvailability.Banned -> locale.avatarProfileBanned
    AvatarActionAvailability.Own -> locale.avatarProfileActionSwitch
    AvatarActionAvailability.Copyable -> locale.avatarProfileActionSwitch
    AvatarActionAvailability.NotCopyable -> locale.avatarProfileActionNotCopyable
    AvatarActionAvailability.CheckFailed -> locale.avatarProfileActionCheckFailed
}

internal fun AvatarImpostorDeletionNotice.localizedToast(locale: LocaleStrings): ToastText =
    when (this) {
        AvatarImpostorDeletionNotice.Deleted -> ToastText.Success(locale.avatarImpostorDeleteSuccess)
        AvatarImpostorDeletionNotice.DeleteFailed ->
            ToastText.Error(locale.avatarImpostorDeleteFailed)
        AvatarImpostorDeletionNotice.VerificationFailed ->
            ToastText.Error(locale.avatarImpostorVerificationFailed)
    }

@Serializable
class AvatarProfileScreen(
    private val avatarProfileVo: AvatarProfileVo,
    private val sharedSuffixKey: String = "",
    private val sharedImageCacheKey: String? = null,
) : AppDetailRoute {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val navigator = currentNavigator
        val screenModel: AvatarProfileScreenModel = koinViewModel()
        val imageProcessor: PrintImageProcessor = koinInject()
        val editorSessionStore: PrintImageEditorSessionStore = koinInject()
        val refreshedAvatar by screenModel.avatarProfileState.collectAsState()
        val avatarGalleryState by screenModel.avatarGalleryState.collectAsState()
        val actionState by screenModel.actionState.collectAsState()
        val editState by screenModel.editState.collectAsState()
        val impostorDeletionState by screenModel.impostorDeletionState.collectAsState()
        val impostorState by screenModel.impostorState.collectAsState()
        val avatarCoverUpdates by editorSessionStore.avatarCoverUpdates.collectAsState()
        val avatarGalleryUpdates by editorSessionStore.avatarGalleryUpdates.collectAsState()
        val currentSession by SharedFlowCentre.currentSession.collectAsState()
        val favoriteEntryState by screenModel.favoriteEntryState.collectAsState()
        val locale = strings
        var showEditSheet by remember { mutableStateOf(false) }
        var showFavoriteSheet by remember { mutableStateOf(false) }
        var showImpostorDeletionConfirmation by remember { mutableStateOf(false) }

        LaunchedEffect(screenModel, locale) {
            screenModel.notices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(locale))
            }
        }
        LaunchedEffect(screenModel, locale) {
            screenModel.impostorDeletionNotices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(locale))
            }
        }

        LaunchedEffect(avatarProfileVo.avatarId) {
            screenModel.refreshAvatarData(avatarProfileVo)
        }

        LaunchedEffect(editState.canEdit) {
            if (!editState.canEdit) showEditSheet = false
        }
        LaunchedEffect(
            impostorDeletionState.isAvailable,
            impostorDeletionState.hasImpostor,
            impostorDeletionState.deleteFailed,
            impostorDeletionState.verificationFailed,
        ) {
            if (!impostorDeletionState.isAvailable ||
                !impostorDeletionState.hasImpostor ||
                impostorDeletionState.deleteFailed ||
                impostorDeletionState.verificationFailed
            ) {
                showImpostorDeletionConfirmation = false
            }
        }

        val displayedAvatar = refreshedAvatar ?: avatarProfileVo
        LaunchedEffect(displayedAvatar.avatarId, avatarCoverUpdates) {
            val updated = avatarCoverUpdates[displayedAvatar.avatarId]
                ?: return@LaunchedEffect
            if (screenModel.applyCoverUpdate(updated)) {
                editorSessionStore.consumeAvatarCoverUpdate(updated.id)
            }
        }
        LaunchedEffect(displayedAvatar.avatarId, avatarGalleryUpdates, currentSession?.token) {
            val update = avatarGalleryUpdates[displayedAvatar.avatarId] ?: return@LaunchedEffect
            if (screenModel.applyGalleryUpdate(update) ||
                !SharedFlowCentre.isCurrentSession(update.sessionToken)
            ) {
                editorSessionStore.consumeAvatarGalleryUpdate(update.avatarId)
            }
        }

        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey) {
            ProfileScaffold(
                imageModifier = Modifier.sharedBoundsBy("${displayedAvatar.avatarId}AvatarImage"),
                profileImageUrl = displayedAvatar.avatarImageUrl,
                iconUrl = displayedAvatar.avatarImageUrl,
                sharedImageCacheKey = sharedImageCacheKey,
                onReturn = { navigator.pop() },
                topBarActions = { colors ->
                    OfficialUrlShareButton(
                        url = "https://vrchat.com/home/avatar/${displayedAvatar.avatarId}",
                        colors = colors,
                    )
                },
            ) { ratio, contentMinHeight ->
                AvatarProfileContent(
                    avatarProfileVo = displayedAvatar,
                    contentMinHeight = contentMinHeight,
                    actionState = actionState,
                    onSelectAvatar = screenModel::selectAvatar,
                    onFavorite = { showFavoriteSheet = true },
                    favoriteEntryState = favoriteEntryState,
                    onRetryFavorite = screenModel::retryFavoriteEntryLoad,
                    canEdit = editState.canEdit,
                    onEdit = {
                        screenModel.loadAvatarStyles()
                        showEditSheet = true
                    },
                    impostorDeletionState = impostorDeletionState,
                    onDeleteImpostor = { showImpostorDeletionConfirmation = true },
                    onRetryImpostorVerification = screenModel::retryImpostorVerification,
                    avatarGalleryState = avatarGalleryState,
                    onLoadMoreAvatarGallery = screenModel::loadMoreAvatarGallery,
                    onRetryAvatarGallery = screenModel::retryAvatarGallery,
                )
            }
        }
        FavoriteGroupBottomSheet(
            isVisible = showFavoriteSheet,
            favoriteId = displayedAvatar.avatarId,
            favoriteType = FavoriteType.Avatar,
            onDismiss = { showFavoriteSheet = false },
        )
        if (showEditSheet && editState.canEdit) {
            AvatarEditSheet(
                avatar = displayedAvatar,
                state = editState,
                impostorState = impostorState,
                imageProcessor = imageProcessor,
                onDismiss = { showEditSheet = false },
                onSaveMetadata = screenModel::saveMetadata,
                onRetryStyles = screenModel::loadAvatarStyles,
                onEnqueueImpostor = screenModel::enqueueImpostor,
                onUpdatePublication = screenModel::updatePublication,
                onEditCover = { source, prepared ->
                    handoffPreparedImageToEditor(
                        source = source,
                        prepared = prepared,
                        sessionStore = editorSessionStore,
                        target = ImageEditorTarget.AvatarCover(displayedAvatar.avatarId),
                        push = { sessionId ->
                            navigator.push(PrintImageEditorScreen(sessionId))
                            showEditSheet = false
                        },
                    )
                },
                onEditGallery = { source, prepared ->
                    val session = currentSession
                    if (session != null && session.account.userId == displayedAvatar.authorId) {
                        handoffPreparedImageToEditor(
                            source = source,
                            prepared = prepared,
                            sessionStore = editorSessionStore,
                            target = ImageEditorTarget.AvatarGallery(
                                AvatarGalleryTarget(
                                    avatarId = displayedAvatar.avatarId,
                                    ownerUserId = displayedAvatar.authorId,
                                    sessionToken = session.token,
                                )
                            ),
                            push = { sessionId ->
                                navigator.push(PrintImageEditorScreen(sessionId))
                                showEditSheet = false
                            },
                        )
                    }
                },
            )
        }
        if (showImpostorDeletionConfirmation &&
            impostorDeletionState.isAvailable &&
            impostorDeletionState.hasImpostor
        ) {
            AvatarImpostorDeletionConfirmationDialog(
                avatarName = displayedAvatar.avatarName,
                isDeleting = impostorDeletionState.isBusy,
                enabled = impostorDeletionState.canDelete,
                onDismiss = { showImpostorDeletionConfirmation = false },
                onConfirm = { screenModel.deleteImpostor() },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AvatarProfileContent(
    avatarProfileVo: AvatarProfileVo,
    contentMinHeight: Dp,
    actionState: AvatarActionState,
    onSelectAvatar: () -> Unit,
    onFavorite: () -> Unit,
    favoriteEntryState: FavoriteEntryState,
    onRetryFavorite: () -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    impostorDeletionState: AvatarImpostorDeletionUiState,
    onDeleteImpostor: () -> Unit,
    onRetryImpostorVerification: () -> Unit,
    avatarGalleryState: AvatarGalleryState,
    onLoadMoreAvatarGallery: () -> Unit,
    onRetryAvatarGallery: () -> Unit,
) {
    val navigator = currentNavigator

    // 名称
    SelectionContainer {
        Text(
            text = avatarProfileVo.avatarName,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    // 作者
    if (avatarProfileVo.authorName.isNotBlank()) {
        Text(
            text = avatarProfileVo.authorName,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.simpleClickable {
                navigator.push(
                    UserProfileScreen(
                        userProfileVO = UserProfileVo(
                            id = avatarProfileVo.authorId,
                            displayName = avatarProfileVo.authorName,
                        )
                    )
                )
            }
        )
    }

    AvatarActionButton(
        state = actionState,
        onClick = onSelectAvatar,
    )

    OutlinedButton(
        onClick = {
            if (favoriteEntryState == FavoriteEntryState.LoadFailed) {
                onRetryFavorite()
            } else {
                onFavorite()
            }
        },
        enabled = favoriteEntryState != FavoriteEntryState.Loading &&
            favoriteEntryState != FavoriteEntryState.Unavailable,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = AppIcons.Favorite,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when (favoriteEntryState) {
                FavoriteEntryState.Loading -> strings.loading
                FavoriteEntryState.Favorited -> strings.editFavorite
                FavoriteEntryState.NotFavorited -> strings.favoriteAvatar
                FavoriteEntryState.LoadFailed -> strings.retry
                FavoriteEntryState.Unavailable -> strings.favoriteAvatar
            }
        )
    }

    if (canEdit) {
        FilledTonalButton(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = AppIcons.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(strings.avatarEditTitle)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (impostorDeletionState.isAvailable) {
        AvatarImpostorDeletionSection(
            state = impostorDeletionState,
            onDelete = onDeleteImpostor,
            onRetryVerification = onRetryImpostorVerification,
        )
        Spacer(Modifier.height(12.dp))
    }

    // 描述
    if (avatarProfileVo.avatarDescription.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            SelectionContainer {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = avatarProfileVo.avatarDescription,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 属性信息卡片
    AvatarInfoCards(avatarProfileVo)

    Spacer(modifier = Modifier.height(12.dp))

    // 平台信息（过滤没有适配的平台）
    val knownPlatforms = avatarProfileVo.platformInfos.filter {
        !it.performanceRating.isNullOrEmpty()
    }
    if (knownPlatforms.isNotEmpty()) {
        AvatarPlatformSection(knownPlatforms)
    }

    AvatarGallerySection(
        state = avatarGalleryState,
        onLoadMore = onLoadMoreAvatarGallery,
        onRetry = onRetryAvatarGallery,
    )

}

@Composable
private fun AvatarGallerySection(
    state: AvatarGalleryState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!state.isAvailable) return

    val (dialogContent, setDialogContent) = LocationDialogContent.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = strings.avatarGalleryTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }

            state.initialLoadFailed -> AvatarGalleryMessage(
                message = strings.avatarGalleryLoadFailed,
                actionText = strings.retry,
                onAction = onRetry,
            )

            state.files.isEmpty() -> AvatarGalleryMessage(
                message = strings.avatarGalleryEmpty,
            )

            else -> {
                AvatarGalleryGrid(
                    files = state.files,
                    dialogContent = dialogContent,
                    onOpen = { file, version ->
                        setDialogContent(
                            ImagePreviewDialog(
                                fileId = file.id,
                                fileName = file.name,
                                fileExtension = file.extension,
                                fileVersion = version,
                            )
                        )
                    },
                )
                if (state.isLoadingMore) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                } else if (state.loadMoreFailed) {
                    AvatarGalleryMessage(
                        message = strings.avatarGalleryLoadMoreFailed,
                        actionText = strings.retry,
                        onAction = onRetry,
                    )
                } else if (state.hasMore) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(strings.avatarGalleryLoadMore) }
                }
            }
        }
    }
}

@Composable
private fun AvatarGalleryMessage(
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
private fun AvatarGalleryGrid(
    files: List<io.github.vrcmteam.vrcm.network.api.files.data.FileData>,
    dialogContent: io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog?,
    onOpen: (io.github.vrcmteam.vrcm.network.api.files.data.FileData, Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 560.dp) 3 else 2
        val spacing = 8.dp
        val rows = files.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEach { file ->
                        val version = file.latestGalleryVersion()?.version
                        val isDialogOpen = (dialogContent as? ImagePreviewDialog)?.fileId == file.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f),
                        ) {
                            if (!isDialogOpen) {
                                SubcomposeAsyncImage(
                                    model = version?.let { FileApi.imageUrl(file.id, it, 256) },
                                    contentDescription = file.name,
                                    imageLoader = koinInject<ImageLoader>(),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable(enabled = version != null) {
                                            version?.let { selectedVersion -> onOpen(file, selectedVersion) }
                                        },
                                    loading = {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                        }
                                    },
                                    error = {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = strings.galleryTabLoadFailed,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AvatarImpostorDeletionSection(
    state: AvatarImpostorDeletionUiState,
    onDelete: () -> Unit,
    onRetryVerification: () -> Unit,
) {
    Text(
        text = strings.avatarImpostorTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = when {
            state.verificationFailed -> strings.avatarImpostorVerificationFailed
            state.deleteFailed -> strings.avatarImpostorDeleteFailed
            state.hasImpostor -> strings.avatarImpostorAvailable
            else -> strings.avatarImpostorEmpty
        },
        color = if (state.deleteFailed || state.verificationFailed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (state.hasImpostor) {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = if (state.verificationFailed) onRetryVerification else onDelete,
            enabled = state.canDelete || state.canRetryVerification,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.phase == AvatarImpostorDeletionPhase.Deleting ->
                        strings.avatarImpostorDeleting
                    state.phase == AvatarImpostorDeletionPhase.Verifying ->
                        strings.avatarImpostorVerifying
                    state.verificationFailed -> strings.avatarImpostorRetryVerification
                    else -> strings.avatarImpostorDeleteAction
                }
            )
        }
    }
}

@Composable
private fun AvatarImpostorDeletionConfirmationDialog(
    avatarName: String,
    isDeleting: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(strings.avatarImpostorDeleteConfirmationTitle) },
        text = {
            Text(strings.avatarImpostorDeleteConfirmationMessage.replace("%name%", avatarName))
        },
        confirmButton = {
            Button(
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = onConfirm,
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(strings.avatarImpostorDeleteAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun AvatarActionButton(
    state: AvatarActionState,
    onClick: () -> Unit,
) {
    val availability = state.availability
    val enabled = !state.isSelecting && (
        availability == AvatarActionAvailability.Own ||
            availability == AvatarActionAvailability.Copyable
        )
    val showProgress = state.isSelecting || availability == AvatarActionAvailability.Checking
    val icon = when (availability) {
        AvatarActionAvailability.Current -> AppIcons.CheckCircle
        AvatarActionAvailability.Own -> AppIcons.Update
        AvatarActionAvailability.Copyable -> AppIcons.Queue
        AvatarActionAvailability.Banned,
        AvatarActionAvailability.NotCopyable -> AppIcons.Block
        AvatarActionAvailability.Checking,
        AvatarActionAvailability.CheckFailed -> AppIcons.QuestionMark
    }

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        enabled = enabled,
        onClick = onClick,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = availability.localizedButtonText(strings),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvatarInfoCards(avatarProfileVo: AvatarProfileVo) {
    val infoCards = mutableListOf<Triple<ImageVector, String, String>>()

    // 版本
    avatarProfileVo.version?.let {
        infoCards.add(Triple(AppIcons.Update, "v$it", strings.avatarProfileVersion))
    }

    // 发布状态
    infoCards.add(Triple(
        AppIcons.Visibility,
        avatarProfileVo.releaseStatus.replaceFirstChar { it.uppercase() },
        strings.avatarProfileStatus
    ))

    // 创建时间
    avatarProfileVo.createdAt?.takeIf { it.isNotEmpty() }?.toLocalDate()?.simpleFormat?.let {
        infoCards.add(Triple(AppIcons.Publish, it, strings.avatarProfileCreated))
    }

    // 更新时间
    avatarProfileVo.updatedAt?.takeIf { it.isNotEmpty() }?.toLocalDate()?.simpleFormat?.let {
        infoCards.add(Triple(AppIcons.DateRange, it, strings.avatarProfileUpdated))
    }

    val cardHeight = 68.dp
    val cardsPerRow = 4
    val rows = (infoCards.size + cardsPerRow - 1) / cardsPerRow
    val spacing = 8.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - spacing * (cardsPerRow - 1)) / cardsPerRow
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (rowIndex in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (colIndex in 0 until cardsPerRow) {
                        val cardIndex = rowIndex * cardsPerRow + colIndex
                        if (cardIndex < infoCards.size) {
                            val (icon, label, description) = infoCards[cardIndex]
                            AvatarInfoItemBlock(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                icon = icon,
                                label = label,
                                description = description
                            )
                        } else {
                            Spacer(modifier = Modifier.width(cardWidth).height(cardHeight))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarInfoItemBlock(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    description: String,
) {
    ATooltipBox(
        tooltip = {
            Text(text = description, style = MaterialTheme.typography.labelSmall)
        }
    ) {
        val bgColor = MaterialTheme.colorScheme.tertiary
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                tint = MaterialTheme.colorScheme.onPrimary,
                contentDescription = description,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AvatarPlatformSection(platformInfos: List<AvatarPlatformInfo>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = strings.avatarProfilePlatforms,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                platformInfos.forEach { info ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = info.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = info.ratingDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            color = ratingColor(info.performanceRating)
                        )
                    }
                    if (info != platformInfos.last()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 评级颜色
 */
@Composable
private fun ratingColor(rating: String?): androidx.compose.ui.graphics.Color {
    return when (rating?.lowercase()) {
        "excellent" -> androidx.compose.ui.graphics.Color(0xFF51E57E)
        "good" -> androidx.compose.ui.graphics.Color(0xFF51E57E)
        "medium" -> androidx.compose.ui.graphics.Color(0xFFFFD24C)
        "poor" -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        "verypoor" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
}
