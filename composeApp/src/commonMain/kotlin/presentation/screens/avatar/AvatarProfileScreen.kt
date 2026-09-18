package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.core.extensions.toLocalDate
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.OfficialUrlShareButton
import io.github.vrcmteam.vrcm.presentation.compoments.ProfileScaffold
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

internal fun AvatarProfileNotice.localizedToast(locale: LocaleStrings): ToastText = when (this) {
    AvatarProfileNotice.Banned -> ToastText.Error(locale.avatarProfileBanned)
    AvatarProfileNotice.Switched -> ToastText.Success(locale.avatarProfileSwitched)
    AvatarProfileNotice.Copied -> ToastText.Success(locale.avatarProfileCopied)
    is AvatarProfileNotice.SelectionFailed -> ToastText.Error(
        message ?: locale.avatarProfileSelectFailed
    )
    AvatarProfileNotice.FallbackSelected -> ToastText.Success(locale.avatarProfileFallbackSelected)
    AvatarProfileNotice.FallbackIneligible -> ToastText.Error(locale.avatarProfileFallbackIneligible)
    AvatarProfileNotice.FallbackNotFound -> ToastText.Error(locale.avatarProfileFallbackNotFound)
    AvatarProfileNotice.FallbackUnauthorized -> ToastText.Error(locale.avatarProfileFallbackUnauthorized)
    AvatarProfileNotice.FallbackSelectionFailed ->
        ToastText.Error(locale.avatarProfileFallbackSelectFailed)
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
    AvatarProfileNotice.ModerationBlocked -> ToastText.Success(locale.avatarModerationBlocked)
    AvatarProfileNotice.ModerationUnblocked -> ToastText.Success(locale.avatarModerationUnblocked)
    AvatarProfileNotice.ModerationLoadFailed -> ToastText.Error(locale.avatarModerationLoadFailed)
    AvatarProfileNotice.ModerationChangeFailed -> ToastText.Error(locale.avatarModerationChangeFailed)
    AvatarProfileNotice.Deleted -> ToastText.Success(locale.avatarDeleteSuccess)
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

internal fun AvatarDeletionFailure.localizedMessage(locale: LocaleStrings): String = when (this) {
    AvatarDeletionFailure.BadRequest -> locale.avatarDeleteBadRequest
    AvatarDeletionFailure.Unauthorized -> locale.avatarDeleteUnauthorized
    AvatarDeletionFailure.Forbidden -> locale.avatarDeleteForbidden
    AvatarDeletionFailure.NotFound -> locale.avatarDeleteNotFound
    AvatarDeletionFailure.InvalidResponse -> locale.avatarDeleteInvalidResponse
    AvatarDeletionFailure.Unexpected -> locale.avatarDeleteUnexpected
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

internal fun AvatarFallbackAvailability.localizedButtonText(locale: LocaleStrings): String = when (this) {
    AvatarFallbackAvailability.Hidden -> ""
    AvatarFallbackAvailability.Available -> locale.avatarProfileFallbackActionSet
    AvatarFallbackAvailability.Current -> locale.avatarProfileFallbackActionCurrent
    AvatarFallbackAvailability.Ineligible -> locale.avatarProfileFallbackActionIneligible
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
        val fallbackActionState by screenModel.fallbackActionState.collectAsState()
        val editState by screenModel.editState.collectAsState()
        val moderationState by screenModel.moderationState.collectAsState()
        val deletionState by screenModel.deletionState.collectAsState()
        val impostorDeletionState by screenModel.impostorDeletionState.collectAsState()
        val impostorState by screenModel.impostorState.collectAsState()
        val avatarCoverUpdates by editorSessionStore.avatarCoverUpdates.collectAsState()
        val avatarGalleryUpdates by editorSessionStore.avatarGalleryUpdates.collectAsState()
        val currentSession by SharedFlowCentre.currentSession.collectAsState()
        val favoriteEntryState by screenModel.favoriteEntryState.collectAsState()
        val locale = strings
        var showEditSheet by remember { mutableStateOf(false) }
        var showFavoriteSheet by remember { mutableStateOf(false) }
        var actionSheetIsVisible by remember { mutableStateOf(false) }
        val actionSheetState = rememberAppSheetState()
        var pendingModerationChange by remember { mutableStateOf<Boolean?>(null) }
        var showImpostorDeletionConfirmation by remember { mutableStateOf(false) }

        LaunchedEffect(screenModel, locale) {
            screenModel.notices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(locale))
                if (notice == AvatarProfileNotice.Deleted) navigator.pop()
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
        LaunchedEffect(displayedAvatar.avatarId) {
            pendingModerationChange = null
        }
        LaunchedEffect(moderationState.status, moderationState.isUpdating) {
            val blocked = pendingModerationChange ?: return@LaunchedEffect
            val requiredStatus = if (blocked) {
                AvatarModerationStatus.NotBlocked
            } else {
                AvatarModerationStatus.Blocked
            }
            if (moderationState.status != requiredStatus || moderationState.isUpdating) {
                pendingModerationChange = null
            }
        }
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

        val favoriteAvatar = {
            if (favoriteEntryState == FavoriteEntryState.LoadFailed) {
                screenModel.retryFavoriteEntryLoad()
            } else {
                showFavoriteSheet = true
            }
        }
        val editAvatar = {
            screenModel.loadAvatarStyles()
            showEditSheet = true
        }

        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey) {
            AppSurface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
                contentColor = AppTheme.colors.label,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ProfileScaffold(
                        modifier = Modifier.weight(1f),
                        imageModifier = Modifier.sharedBoundsBy(
                            "${displayedAvatar.avatarId}AvatarImage"
                        ),
                        profileImageUrl = displayedAvatar.avatarImageUrl,
                        iconUrl = displayedAvatar.avatarImageUrl,
                        sharedImageCacheKey = sharedImageCacheKey,
                        onReturn = { navigator.pop() },
                        onMenu = { actionSheetIsVisible = true },
                        menuContentDescription = strings.avatarProfileMoreActions,
                        topBarActions = {
                            OfficialUrlShareButton(
                                url = "https://vrchat.com/home/avatar/${displayedAvatar.avatarId}",
                            )
                        },
                    ) { _, _ ->
                        AvatarProfileContent(
                            avatarProfileVo = displayedAvatar,
                            avatarGalleryState = avatarGalleryState,
                            onLoadMoreAvatarGallery = screenModel::loadMoreAvatarGallery,
                            onRetryAvatarGallery = screenModel::retryAvatarGallery,
                        )
                    }

                    AvatarProfileBottomActions(
                        actionState = actionState,
                        favoriteEntryState = favoriteEntryState,
                        sysBottomPadding = getInsetPadding(WindowInsets::getBottom),
                        onSelectAvatar = screenModel::selectAvatar,
                        onFavoriteAvatar = favoriteAvatar,
                    )
                }
            }
        }
        ABottomSheet(
            isVisible = actionSheetIsVisible,
            sheetState = actionSheetState,
            onDismissRequest = { actionSheetIsVisible = false },
        ) {
            AvatarProfileActionSheet(
                hideSheet = { actionSheetState.hide() },
                onHideCompletion = {
                    if (!actionSheetState.isVisible) actionSheetIsVisible = false
                },
                fallbackActionState = fallbackActionState,
                onSelectFallbackAvatar = screenModel::selectFallbackAvatar,
                moderationState = moderationState,
                onRetryModeration = screenModel::retryAvatarModerationLoad,
                onModerationChangeRequested = { blocked ->
                    pendingModerationChange = blocked
                },
                canEdit = editState.canEdit,
                onEdit = editAvatar,
                deletionState = deletionState,
                onDelete = screenModel::requestAvatarDeletion,
                impostorDeletionState = impostorDeletionState,
                onDeleteImpostor = { showImpostorDeletionConfirmation = true },
                onRetryImpostorVerification = screenModel::retryImpostorVerification,
            )
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
        deletionState.confirmation?.let { target ->
            AvatarDeletionDialog(
                avatarName = target.avatarName,
                state = deletionState,
                onDismiss = screenModel::dismissAvatarDeletion,
                onConfirm = screenModel::confirmAvatarDeletion,
            )
        }
        pendingModerationChange?.let { blocked ->
            AvatarModerationConfirmationDialog(
                blocked = blocked,
                onDismiss = { pendingModerationChange = null },
                onConfirm = {
                    pendingModerationChange = null
                    screenModel.setAvatarBlocked(blocked)
                },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AvatarProfileContent(
    avatarProfileVo: AvatarProfileVo,
    avatarGalleryState: AvatarGalleryState,
    onLoadMoreAvatarGallery: () -> Unit,
    onRetryAvatarGallery: () -> Unit,
) {
    val navigator = currentNavigator

    // 名称
    SelectionContainer {
        AppText(
            text = avatarProfileVo.avatarName,
            color = AppTheme.colors.label,
            style = AppTheme.type.title2,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    // 作者
    if (avatarProfileVo.authorName.isNotBlank()) {
        AppText(
            text = avatarProfileVo.authorName,
            color = AppTheme.colors.secondaryLabel,
            style = AppTheme.type.caption1Emphasized,
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

    // 描述
    if (avatarProfileVo.avatarDescription.isNotBlank()) {
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.secondaryGroupedBackground,
            shape = AppShapes.xl
        ) {
            SelectionContainer {
                AppText(
                    modifier = Modifier.padding(12.dp),
                    text = avatarProfileVo.avatarDescription,
                    style = AppTheme.type.subheadline,
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

private val AvatarProfileContentMaxWidth = 720.dp

@Composable
private fun AvatarProfileBottomActions(
    actionState: AvatarActionState,
    favoriteEntryState: FavoriteEntryState,
    sysBottomPadding: Dp,
    onSelectAvatar: () -> Unit,
    onFavoriteAvatar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = sysBottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AvatarProfilePrimaryActions(
            actionState = actionState,
            favoriteEntryState = favoriteEntryState,
            onSelectAvatar = onSelectAvatar,
            onFavoriteAvatar = onFavoriteAvatar,
            modifier = Modifier
                .widthIn(max = AvatarProfileContentMaxWidth)
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun AvatarProfilePrimaryActions(
    actionState: AvatarActionState,
    favoriteEntryState: FavoriteEntryState,
    onSelectAvatar: () -> Unit,
    onFavoriteAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarActionButton(
            state = actionState,
            onClick = onSelectAvatar,
            modifier = Modifier.weight(1f),
        )

        AppButton(
            onClick = onFavoriteAvatar,
            enabled = favoriteEntryState != FavoriteEntryState.Loading &&
                favoriteEntryState != FavoriteEntryState.Unavailable,
            modifier = Modifier.weight(1f),
            style = AppButtonStyle.Gray,
        ) {
            AppIcon(
                imageVector = AppIcons.Favorite,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            AppText(
                text = when (favoriteEntryState) {
                    FavoriteEntryState.Loading -> strings.loading
                    FavoriteEntryState.Favorited -> strings.editFavorite
                    FavoriteEntryState.NotFavorited -> strings.favoriteAvatar
                    FavoriteEntryState.LoadFailed -> strings.retry
                    FavoriteEntryState.Unavailable -> strings.favoriteAvatar
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.AvatarProfileActionSheet(
    hideSheet: suspend () -> Unit,
    onHideCompletion: () -> Unit,
    fallbackActionState: AvatarFallbackActionState,
    onSelectFallbackAvatar: () -> Unit,
    moderationState: AvatarModerationState,
    onRetryModeration: () -> Unit,
    onModerationChangeRequested: (Boolean) -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    deletionState: AvatarDeletionState,
    onDelete: () -> Unit,
    impostorDeletionState: AvatarImpostorDeletionUiState,
    onDeleteImpostor: () -> Unit,
    onRetryImpostorVerification: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismissAndRun: (() -> Unit) -> Unit = { action ->
        scope.launch {
            hideSheet()
            onHideCompletion()
            action()
        }
    }

    if (canEdit) {
        AvatarProfileSheetButton(
            text = strings.avatarEditTitle,
            onClick = { dismissAndRun(onEdit) },
        )
    }

    val fallbackAvailability = fallbackActionState.availability
    if (fallbackAvailability != AvatarFallbackAvailability.Hidden) {
        AvatarProfileSheetButton(
            text = if (fallbackActionState.isSelecting) {
                strings.avatarProfileFallbackActionSetting
            } else {
                fallbackAvailability.localizedButtonText(strings)
            },
            enabled = !fallbackActionState.isSelecting &&
                !fallbackActionState.isBlockedByDeletion &&
                fallbackAvailability == AvatarFallbackAvailability.Available,
            loading = fallbackActionState.isSelecting,
            onClick = { dismissAndRun(onSelectFallbackAvatar) },
        )
    }

    val moderationStatus = moderationState.status
    val isBlockAction = moderationStatus == AvatarModerationStatus.NotBlocked
    val moderationEnabled = !moderationState.isUpdating && (
        isBlockAction ||
            moderationStatus == AvatarModerationStatus.Blocked ||
            moderationStatus == AvatarModerationStatus.LoadFailed
        )
    val moderationText = when {
        moderationState.isUpdating && isBlockAction -> strings.avatarModerationBlocking
        moderationState.isUpdating && moderationStatus == AvatarModerationStatus.Blocked ->
            strings.avatarModerationUnblocking
        moderationStatus == AvatarModerationStatus.Unavailable ->
            strings.avatarModerationUnavailable
        moderationStatus == AvatarModerationStatus.Loading -> strings.avatarModerationChecking
        moderationStatus == AvatarModerationStatus.Blocked -> strings.avatarModerationUnblock
        moderationStatus == AvatarModerationStatus.NotBlocked -> strings.avatarModerationBlock
        else -> strings.avatarModerationRetry
    }
    AvatarProfileSheetButton(
        text = moderationText,
        enabled = moderationEnabled,
        loading = moderationState.isUpdating ||
            moderationStatus == AvatarModerationStatus.Loading,
        isDestructive = isBlockAction,
        onClick = {
            when (moderationStatus) {
                AvatarModerationStatus.Blocked -> dismissAndRun {
                    onModerationChangeRequested(false)
                }
                AvatarModerationStatus.NotBlocked -> dismissAndRun {
                    onModerationChangeRequested(true)
                }
                AvatarModerationStatus.LoadFailed -> dismissAndRun(onRetryModeration)
                AvatarModerationStatus.Unavailable,
                AvatarModerationStatus.Loading -> Unit
            }
        },
    )

    if (impostorDeletionState.isAvailable && impostorDeletionState.hasImpostor) {
        AvatarProfileSheetButton(
            text = when {
                impostorDeletionState.phase == AvatarImpostorDeletionPhase.Deleting ->
                    strings.avatarImpostorDeleting
                impostorDeletionState.phase == AvatarImpostorDeletionPhase.Verifying ->
                    strings.avatarImpostorVerifying
                impostorDeletionState.verificationFailed ->
                    strings.avatarImpostorRetryVerification
                else -> strings.avatarImpostorDeleteAction
            },
            enabled = impostorDeletionState.canDelete ||
                impostorDeletionState.canRetryVerification,
            loading = impostorDeletionState.isBusy,
            isDestructive = true,
            onClick = {
                dismissAndRun(
                    if (impostorDeletionState.verificationFailed) {
                        onRetryImpostorVerification
                    } else {
                        onDeleteImpostor
                    }
                )
            },
        )
    }

    if (deletionState.canDelete) {
        AvatarProfileSheetButton(
            text = if (deletionState.isDeleting) {
                strings.avatarDeleteDeleting
            } else {
                strings.avatarDeleteAction
            },
            enabled = !deletionState.isDeleting && !deletionState.isBlockedByFallback,
            loading = deletionState.isDeleting,
            isDestructive = true,
            onClick = { dismissAndRun(onDelete) },
        )
    }
}

@Composable
private fun ColumnScope.AvatarProfileSheetButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    AppSheetAction(
        enabled = enabled,
        onClick = onClick,
        role = if (isDestructive) AppButtonRole.Destructive else AppButtonRole.Default,
    ) {
        if (loading) {
            AppActivityIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        AppText(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AvatarGallerySection(
    state: AvatarGalleryState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!state.isAvailable) return

    val (_, setDialogContent) = LocationDialogContent.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(
            text = strings.avatarGalleryTitle,
            style = AppTheme.type.headline,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.label,
        )
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                contentAlignment = Alignment.Center,
            ) { AppActivityIndicator(modifier = Modifier.size(28.dp)) }

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
                    ) { AppActivityIndicator(modifier = Modifier.size(24.dp)) }
                } else if (state.loadMoreFailed) {
                    AvatarGalleryMessage(
                        message = strings.avatarGalleryLoadMoreFailed,
                        actionText = strings.retry,
                        onAction = onRetry,
                    )
                } else if (state.hasMore) {
                    AppButton(
                        onClick = onLoadMore,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = AppButtonStyle.Gray,
                    ) { AppText(strings.avatarGalleryLoadMore) }
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
        AppText(
            text = message,
            style = AppTheme.type.subheadline,
            color = AppTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            AppButton(onClick = onAction, style = AppButtonStyle.Plain) { AppText(actionText) }
        }
    }
}

@Composable
private fun AvatarGalleryGrid(
    files: List<io.github.vrcmteam.vrcm.network.api.files.data.FileData>,
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f),
                        ) {
                            SubcomposeAsyncImage(
                                model = version?.let { FileApi.imageUrl(file.id, it, 256) },
                                contentDescription = file.name,
                                imageLoader = koinInject<ImageLoader>(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(AppShapes.m)
                                    .clickable(enabled = version != null) {
                                        version?.let { selectedVersion -> onOpen(file, selectedVersion) }
                                    },
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        AppActivityIndicator(modifier = Modifier.size(22.dp))
                                    }
                                },
                                error = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        AppText(
                                            text = strings.galleryTabLoadFailed,
                                            style = AppTheme.type.caption2Emphasized,
                                            color = AppTheme.colors.destructive,
                                        )
                                    }
                                },
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
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
    AppAlert(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            AppIcon(
                imageVector = AppIcons.Delete,
                contentDescription = null,
                tint = AppTheme.colors.destructive,
            )
        },
        title = { AppText(strings.avatarImpostorDeleteConfirmationTitle) },
        text = {
            AppText(strings.avatarImpostorDeleteConfirmationMessage.replace("%name%", avatarName))
        },
        confirmButton = {
            AppButton(
                enabled = enabled,
                onClick = onConfirm,
                style = AppButtonStyle.Prominent,
                role = AppButtonRole.Destructive,
            ) {
                if (isDeleting) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                AppText(strings.avatarImpostorDeleteAction)
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, enabled = !isDeleting, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
            }
        },
    )
}

@Composable
private fun AvatarDeletionDialog(
    avatarName: String,
    state: AvatarDeletionState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlert(
        onDismissRequest = { if (!state.isDeleting) onDismiss() },
        icon = {
            AppIcon(
                imageVector = AppIcons.Delete,
                contentDescription = null,
                tint = AppTheme.colors.destructive,
            )
        },
        title = { AppText(strings.avatarDeleteTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(strings.avatarDeleteMessage.replace("%s", avatarName))
                state.failure?.let { failure ->
                    AppText(
                        text = failure.localizedMessage(strings),
                        color = AppTheme.colors.destructive,
                        style = AppTheme.type.subheadline,
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = onConfirm,
                enabled = !state.isDeleting,
                style = AppButtonStyle.Plain,
                role = AppButtonRole.Destructive,
            ) {
                if (state.isDeleting) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                AppText(
                    if (state.isDeleting) strings.avatarDeleteDeleting
                    else strings.avatarDeleteConfirm
                )
            }
        },
        dismissButton = {
            AppButton(
                onClick = onDismiss,
                enabled = !state.isDeleting,
                style = AppButtonStyle.Plain,
            ) {
                AppText(strings.cancel)
            }
        },
    )
}

@Composable
private fun AvatarModerationConfirmationDialog(
    blocked: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlert(
        onDismissRequest = onDismiss,
        icon = { AppIcon(AppIcons.Block, contentDescription = null) },
        title = {
            AppText(
                if (blocked) {
                    strings.avatarModerationBlockConfirmTitle
                } else {
                    strings.avatarModerationUnblockConfirmTitle
                }
            )
        },
        text = {
            AppText(
                if (blocked) {
                    strings.avatarModerationBlockConfirmMessage
                } else {
                    strings.avatarModerationUnblockConfirmMessage
                }
            )
        },
        confirmButton = {
            AppButton(
                onClick = onConfirm,
                contentColor = if (blocked) {
                        AppTheme.colors.destructive
                    } else {
                        AppTheme.colors.tint
                    },
                style = AppButtonStyle.Plain,
            ) {
                AppText(
                    if (blocked) {
                        strings.avatarModerationBlock
                    } else {
                        strings.avatarModerationUnblock
                    }
                )
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) { AppText(strings.cancel) }
        },
    )
}

@Composable
private fun AvatarActionButton(
    state: AvatarActionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availability = state.availability
    val enabled = !state.isSelecting && (
        availability == AvatarActionAvailability.Own ||
            availability == AvatarActionAvailability.Copyable
        )
    val showProgress = state.isSelecting || availability == AvatarActionAvailability.Checking
    val icon = when (availability) {
        AvatarActionAvailability.Current -> AppIcons.CheckCircle
        AvatarActionAvailability.Own -> AppIcons.Refresh
        AvatarActionAvailability.Copyable -> AppIcons.Duplicate
        AvatarActionAvailability.Banned,
        AvatarActionAvailability.NotCopyable -> AppIcons.Block
        AvatarActionAvailability.Checking,
        AvatarActionAvailability.CheckFailed -> AppIcons.QuestionMark
    }

    AppButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        style = AppButtonStyle.Prominent,
    ) {
        if (showProgress) {
            AppActivityIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
            )
        } else {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        AppText(
            text = availability.localizedButtonText(strings),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AvatarInfoCards(avatarProfileVo: AvatarProfileVo) {
    val infoCards = mutableListOf<Triple<ImageVector, String, String>>()

    // 版本
    avatarProfileVo.version?.let {
        infoCards.add(Triple(AppIcons.Refresh, "v$it", strings.avatarProfileVersion))
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

@Composable
private fun AvatarInfoItemBlock(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    description: String,
) {
    ATooltipBox(
        tooltip = {
            AppText(text = description, style = AppTheme.type.caption2Emphasized)
        }
    ) {
        val bgColor = AppTheme.colors.secondaryTint
        Column(
            modifier = modifier
                .clip(AppShapes.m)
                .background(bgColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(
                imageVector = icon,
                tint = AppTheme.colors.onSecondaryTint,
                contentDescription = description,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            AppText(
                text = label,
                color = AppTheme.colors.onSecondaryTint,
                style = AppTheme.type.caption2Emphasized,
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
        AppText(
            text = strings.avatarProfilePlatforms,
            style = AppTheme.type.headline,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.label
        )
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.secondaryGroupedBackground,
            shape = AppShapes.xl
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
                        AppText(
                            text = info.displayName,
                            style = AppTheme.type.subheadline,
                            color = AppTheme.colors.label
                        )
                        AppText(
                            text = info.ratingDisplay,
                            style = AppTheme.type.caption1Emphasized,
                            color = ratingColor(info.performanceRating)
                        )
                    }
                    if (info != platformInfos.last()) {
                        AppDivider(
                            thickness = 0.5.dp,
                            color = AppTheme.colors.separator.copy(alpha = 0.5f)
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
        "poor" -> AppTheme.colors.destructive.copy(alpha = 0.8f)
        "verypoor" -> AppTheme.colors.destructive
        else -> AppTheme.colors.tertiaryLabel
    }
}
