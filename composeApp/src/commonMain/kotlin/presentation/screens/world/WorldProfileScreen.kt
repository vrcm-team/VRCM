package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.core.extensions.toLocalDate
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType.*
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.*
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.ImageEditorTarget
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageEditorSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.PrintImageProcessor
import io.github.vrcmteam.vrcm.presentation.screens.gallery.editor.handoffPreparedImageToEditor
import io.github.vrcmteam.vrcm.presentation.screens.world.components.CreateInstanceDialog
import io.github.vrcmteam.vrcm.presentation.screens.world.components.EmptyInstanceCard
import io.github.vrcmteam.vrcm.presentation.screens.world.components.FavoriteGroupBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.world.components.InstancesDialog
import io.github.vrcmteam.vrcm.presentation.screens.world.components.WorldPersistenceDialog
import io.github.vrcmteam.vrcm.presentation.screens.world.data.*
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.launch
import presentation.compoments.TopMenuBar

internal fun WorldPublicationNotice.localizedToast(locale: LocaleStrings): ToastText =
    when (this) {
        is WorldPublicationNotice.Changed -> ToastText.Success(
            when (action) {
                WorldPublicationAction.Publish -> locale.worldPublishSuccess
                WorldPublicationAction.Unpublish -> locale.worldUnpublishSuccess
            }
        )
        is WorldPublicationNotice.ChangeFailed -> ToastText.Error(
            when (action) {
                WorldPublicationAction.Publish -> locale.worldPublishFailed
                WorldPublicationAction.Unpublish -> locale.worldUnpublishFailed
            }.withOptionalDetail(message)
        )
        is WorldPublicationNotice.RefreshFailed -> ToastText.Error(
            locale.worldPublicationRefreshRequired.withOptionalDetail(message)
        )
        is WorldPublicationNotice.CacheSyncFailed -> ToastText.Error(
            locale.worldPublicationCacheSyncFailed.withOptionalDetail(message)
        )
    }

private fun String.withOptionalDetail(detail: String?): String =
    detail?.takeIf { it.isNotBlank() }?.let { "$this: $it" } ?: this

internal fun HomeWorldNotice.localizedToast(locale: LocaleStrings): ToastText = when (this) {
    HomeWorldNotice.Set -> ToastText.Success(locale.worldProfileHomeWorldSetSuccess)
    HomeWorldNotice.Reset -> ToastText.Success(locale.worldProfileHomeWorldResetSuccess)
    HomeWorldNotice.UpdateFailed -> ToastText.Error(locale.worldProfileHomeWorldUpdateFailed)
}

/**
 *
 * kotlin类作用描述
 *
 * @author 次音(CiYin) QQ:2964221430
 * @github <a href="https://github.com/Ci-Yin">Ci-Yin</a>
 * @since 2024/3/23 19:44
 * @version: 1.0
 */
@Serializable
class WorldProfileScreen(
    private val worldProfileVO: WorldProfileVo,
    private val location: String? = null,
    private val sharedSuffixKey: String = "",
    private val sharedKeyPrefix: String = "",
    private val sharedImageCacheKey: String? = null,
) : AppDetailRoute {

    @Composable
    override fun Content() {
        // 创建ViewModel
        val screenModel: WorldProfileScreenModel = koinViewModel()
        val imageProcessor: PrintImageProcessor = koinInject()
        val editorSessionStore: PrintImageEditorSessionStore = koinInject()

        // 收集ViewModel状态
        val profileVoState by screenModel.worldProfileState.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val publicationState by screenModel.publicationState.collectAsState()
        val worldPersistenceState by screenModel.worldPersistenceState.collectAsState()
        val deletionState by screenModel.deletionState.collectAsState()
        val currentNavigator = currentNavigator
        val localeStrings = strings
        val homeWorldActionState by screenModel.homeWorldActionState.collectAsState()
        val locale = localeStrings
        var showHomeWorldConfirmation by remember { mutableStateOf(false) }
        val metadataEditState by screenModel.metadataEditState.collectAsState()
        var showMetadataEditor by remember { mutableStateOf(false) }
        val imageEditState by screenModel.imageEditState.collectAsState()
        val worldCoverUpdates by editorSessionStore.worldCoverUpdates.collectAsState()
        var showImageEditSheet by remember { mutableStateOf(false) }

        LaunchedEffect(screenModel, locale) {
            screenModel.notices.collect { notice ->
                val text = when (notice) {
                    WorldProfileNotice.ImageSaved -> locale.worldImageEditSaved
                }
                SharedFlowCentre.toastText.emit(ToastText.Success(text))
            }
        }
        LaunchedEffect(screenModel, locale) {
            screenModel.metadataEditNotices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(locale))
            }
        }
        // 组件首次加载时自动刷新数据
        LaunchedEffect(Unit) {
            screenModel.loadWorldData(worldProfileVO)
        }
        LaunchedEffect(screenModel, localeStrings) {
            screenModel.publicationNotices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(localeStrings))
            }
        }
        LaunchedEffect(screenModel, localeStrings, currentNavigator) {
            screenModel.deletionNotices.collect { notice ->
                when (notice) {
                    is WorldDeletionNotice.Deleted -> {
                        SharedFlowCentre.toastText.emit(
                            if (notice.cacheCleanupFailed) {
                                ToastText.Info(localeStrings.worldDeleteSuccessCacheCleanupFailed)
                            } else {
                                ToastText.Success(localeStrings.worldDeleteSuccess)
                            }
                        )
                        if (currentNavigator.lastItem == this@WorldProfileScreen) {
                            currentNavigator.pop()
                        }
                    }
                    WorldDeletionNotice.Failed -> SharedFlowCentre.toastText.emit(
                        ToastText.Error(localeStrings.worldDeleteFailed)
                    )
                }
            }
        }

        LaunchedEffect(screenModel, locale) {
            screenModel.homeWorldNotices.collect { notice ->
                SharedFlowCentre.toastText.emit(notice.localizedToast(locale))
            }
        }

        LaunchedEffect(homeWorldActionState.availability) {
            if (homeWorldActionState.availability == HomeWorldActionAvailability.Unavailable) {
                showHomeWorldConfirmation = false
            }
        }

        LaunchedEffect(imageEditState.canEdit) {
            if (!imageEditState.canEdit) showImageEditSheet = false
        }
        LaunchedEffect(metadataEditState.canEdit) {
            if (!metadataEditState.canEdit) showMetadataEditor = false
        }

        val displayedWorld = profileVoState ?: worldProfileVO
        LaunchedEffect(displayedWorld.worldId, worldCoverUpdates) {
            val update = worldCoverUpdates[displayedWorld.worldId]
                ?: return@LaunchedEffect
            screenModel.applyWorldImageUpdate(update)
            editorSessionStore.consumeWorldCoverUpdate(displayedWorld.worldId)
        }

        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey,
        ) {
            WorldProfileContent(
                screenModel = screenModel,
                worldProfileVo = displayedWorld,
                onReturn = { currentNavigator.pop() },
                isRefreshing = isLoading,
                onRefresh = screenModel::refreshWorldData,
                publicationState = publicationState,
                onPublicationAction = screenModel::changeWorldPublication,
                worldPersistenceState = worldPersistenceState,
                onCheckWorldPersistence = screenModel::checkWorldPersistence,
                onRequestWorldPersistenceDeletion = screenModel::requestWorldPersistenceDeletion,
                onDismissWorldPersistenceDeletion = screenModel::dismissWorldPersistenceDeletion,
                onConfirmWorldPersistenceDeletion = screenModel::confirmWorldPersistenceDeletion,
                isDeleteAvailable = deletionState.isAvailable,
                isDeleting = deletionState.isDeleting,
                isDeleted = deletionState.isDeleted,
                onDelete = { screenModel.deleteWorld() },
                homeWorldActionState = homeWorldActionState,
                onHomeWorldClick = { showHomeWorldConfirmation = true },
                canEditImage = imageEditState.canEdit,
                onEditImage = {
                    showMetadataEditor = false
                    showImageEditSheet = true
                },
                canEditMetadata = metadataEditState.canEdit,
                onEditMetadata = {
                    showImageEditSheet = false
                    showMetadataEditor = true
                },
                sharedKeyPrefix = sharedKeyPrefix,
                sharedImageCacheKey = sharedImageCacheKey,
            )
        }

        if (showHomeWorldConfirmation) {
            val resetHomeWorld = homeWorldActionState.availability == HomeWorldActionAvailability.Current
            AlertDialog(
                onDismissRequest = {
                    if (!homeWorldActionState.isUpdating) showHomeWorldConfirmation = false
                },
                title = {
                    Text(
                        if (resetHomeWorld) strings.worldProfileResetHomeWorld
                        else strings.worldProfileSetHomeWorld
                    )
                },
                text = {
                    Text(
                        if (resetHomeWorld) strings.worldProfileResetHomeWorldConfirmation
                        else strings.worldProfileSetHomeWorldConfirmation
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !homeWorldActionState.isUpdating &&
                            homeWorldActionState.availability != HomeWorldActionAvailability.Unavailable,
                        onClick = {
                            showHomeWorldConfirmation = false
                            screenModel.updateHomeWorld()
                        },
                    ) {
                        Text(strings.confirm)
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !homeWorldActionState.isUpdating,
                        onClick = { showHomeWorldConfirmation = false },
                    ) {
                        Text(strings.cancel)
                    }
                },
            )
        }

        val editToken = imageEditState.sessionToken
        if (showImageEditSheet && imageEditState.canEdit && editToken != null) {
            WorldImageEditSheet(
                world = displayedWorld,
                imageProcessor = imageProcessor,
                onDismiss = { showImageEditSheet = false },
                onEditImage = { source, prepared ->
                    handoffPreparedImageToEditor(
                        source = source,
                        prepared = prepared,
                        sessionStore = editorSessionStore,
                        target = ImageEditorTarget.WorldCover(
                            worldId = displayedWorld.worldId,
                            sessionToken = editToken,
                        ),
                        push = { sessionId ->
                            currentNavigator.push(PrintImageEditorScreen(sessionId))
                            showImageEditSheet = false
                        },
                    )
                },
            )
        }
        if (showMetadataEditor && metadataEditState.canEdit) {
            WorldMetadataEditSheet(
                world = displayedWorld,
                state = metadataEditState,
                onDismiss = { showMetadataEditor = false },
                onSave = screenModel::saveMetadata,
            )
        }
    }

    // 主要内容组件
    @Composable
    private fun WorldProfileContent(
        screenModel: WorldProfileScreenModel,
        worldProfileVo: WorldProfileVo,
        onReturn: () -> Unit = {},
        isRefreshing: Boolean = false,
        onRefresh: () -> Unit = {},
        publicationState: WorldPublicationUiState = WorldPublicationUiState(),
        onPublicationAction: (WorldPublicationAction) -> Unit = {},
        worldPersistenceState: WorldPersistenceUiState = WorldPersistenceUiState(),
        onCheckWorldPersistence: () -> Unit = {},
        onRequestWorldPersistenceDeletion: () -> Unit = {},
        onDismissWorldPersistenceDeletion: () -> Unit = {},
        onConfirmWorldPersistenceDeletion: () -> Unit = {},
        isDeleteAvailable: Boolean = false,
        isDeleting: Boolean = false,
        isDeleted: Boolean = false,
        onDelete: () -> Unit = {},
        homeWorldActionState: HomeWorldActionState = HomeWorldActionState(),
        onHomeWorldClick: () -> Unit = {},
        canEditImage: Boolean = false,
        onEditImage: () -> Unit = {},
        canEditMetadata: Boolean = false,
        onEditMetadata: () -> Unit = {},
        sharedKeyPrefix: String = "",
        sharedImageCacheKey: String? = null,
    ) {
        var publicationConfirmation by rememberSaveable(worldProfileVo.worldId) {
            mutableStateOf<WorldPublicationAction?>(null)
        }
        LaunchedEffect(
            worldProfileVo.worldId,
            publicationState.action,
            publicationState.canExecute,
        ) {
            val pendingAction = publicationConfirmation ?: return@LaunchedEffect
            if (publicationState.action != pendingAction || !publicationState.canExecute) {
                publicationConfirmation = null
            }
        }

        publicationConfirmation?.let { action ->
            WorldPublicationConfirmationDialog(
                action = action,
                worldName = worldProfileVo.worldName,
                enabled = publicationState.action == action && publicationState.canExecute &&
                    !publicationState.isChanging && !isRefreshing && !isDeleting && !isDeleted,
                onDismiss = { publicationConfirmation = null },
                onConfirm = {
                    onPublicationAction(action)
                    publicationConfirmation = null
                },
            )
        }

        var showWorldPersistenceDialog by rememberSaveable(worldProfileVo.worldId) {
            mutableStateOf(false)
        }

        if (showWorldPersistenceDialog) {
            WorldPersistenceDialog(
                state = worldPersistenceState,
                localeStrings = strings,
                onDismiss = {
                    onDismissWorldPersistenceDeletion()
                    showWorldPersistenceDialog = false
                },
                onCheck = onCheckWorldPersistence,
                onRequestDeletion = onRequestWorldPersistenceDeletion,
                onDismissDeletion = onDismissWorldPersistenceDeletion,
                onConfirmDeletion = onConfirmWorldPersistenceDeletion,
            )
        }

        var showDeleteConfirmation by rememberSaveable(worldProfileVo.worldId) {
            mutableStateOf(false)
        }
        val canDeleteNow = isDeleteAvailable && !isDeleting && !isRefreshing && !isDeleted &&
            !publicationState.isChanging &&
            worldPersistenceState.status != WorldPersistenceStatus.Deleting
        LaunchedEffect(isDeleteAvailable, isDeleted) {
            if (!isDeleteAvailable || isDeleted) showDeleteConfirmation = false
        }

        if (showDeleteConfirmation) {
            WorldDeletionConfirmationDialog(
                worldName = worldProfileVo.worldName,
                isDeleting = isDeleting,
                enabled = canDeleteNow,
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = onDelete,
            )
        }

        val favoriteEntryState by screenModel.favoriteEntryState.collectAsState()
        val instanceCreationGroups by screenModel.instanceCreationGroups.collectAsState()
        val instanceCreationState by screenModel.instanceCreationState.collectAsState()
        var showCreateInstanceDialog by rememberSaveable(worldProfileVo.worldId) {
            mutableStateOf(false)
        }
        var showFavoriteGroupBottomSheet by rememberSaveable(worldProfileVo.worldId) {
            mutableStateOf(false)
        }
        var showRooms by rememberSaveable(worldProfileVo.worldId) { mutableStateOf(false) }
        var currentDialog by LocationDialogContent.current
        val sharedSuffixKey = LocalSharedSuffixKey.current
        val localeStrings = strings
        val activeInstances = remember(worldProfileVo.instances) {
            worldProfileVo.instances.filter { it.isActive != false }
        }

        LaunchedEffect(instanceCreationState) {
            if (instanceCreationState == InstanceCreationSubmissionState.Created) {
                showCreateInstanceDialog = false
                screenModel.resetInstanceCreationState()
            }
        }

        if (showCreateInstanceDialog) {
            CreateInstanceDialog(
                groupsState = instanceCreationGroups,
                submissionState = instanceCreationState,
                onDismiss = {
                    showCreateInstanceDialog = false
                    screenModel.resetInstanceCreationState()
                },
                onRetryGroups = screenModel::prepareInstanceCreation,
                onConfirm = { draft ->
                    screenModel.createInstanceAndInviteSelf(draft, localeStrings)
                },
            ).Content()
        }

        FavoriteGroupBottomSheet(
            isVisible = showFavoriteGroupBottomSheet,
            favoriteId = worldProfileVo.worldId,
            favoriteType = FavoriteType.World,
            onDismiss = { showFavoriteGroupBottomSheet = false },
        )

        val createRoom = {
            screenModel.prepareInstanceCreation()
            showCreateInstanceDialog = true
        }
        val favoriteWorld = {
            if (favoriteEntryState == FavoriteEntryState.LoadFailed) {
                screenModel.retryFavoriteEntryLoad()
            } else {
                showFavoriteGroupBottomSheet = true
            }
        }
        val openRoom: (InstanceVo) -> Unit = { instance ->
            if (currentDialog == null) {
                currentDialog = InstancesDialog(
                    instance = instance,
                    sharedSuffixKey = sharedSuffixKey,
                    screenModel = screenModel,
                    onClose = { currentDialog = null },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val sysTopPadding = getInsetPadding(WindowInsets::getTop)
                val sysBottomPadding = getInsetPadding(WindowInsets::getBottom)

                WorldProfileCompactLayout(
                    worldProfileVo = worldProfileVo,
                    activeInstances = activeInstances,
                    favoriteEntryState = favoriteEntryState,
                    showRooms = showRooms,
                    sysBottomPadding = sysBottomPadding,
                    worldIdForSharedElement = location ?: worldProfileVo.worldId,
                    sharedKeyPrefix = sharedKeyPrefix,
                    sharedImageCacheKey = sharedImageCacheKey,
                    onShowDetails = { showRooms = false },
                    onShowRooms = { showRooms = true },
                    onCreateRoom = createRoom,
                    onFavoriteWorld = favoriteWorld,
                    onOpenRoom = openRoom,
                )

                WorldProfileTopBar(
                    worldId = worldProfileVo.worldId,
                    sysTopPadding = sysTopPadding,
                    onReturn = onReturn,
                    onManagePersistence = { showWorldPersistenceDialog = true },
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    publicationState = publicationState,
                    onPublicationAction = { publicationConfirmation = it },
                    showDelete = isDeleteAvailable && !isDeleted,
                    deleteEnabled = canDeleteNow,
                    isDeleting = isDeleting,
                    isDeleted = isDeleted,
                    onDelete = { showDeleteConfirmation = true },
                    homeWorldActionState = homeWorldActionState,
                    onHomeWorldClick = onHomeWorldClick,
                    canEditImage = canEditImage,
                    onEditImage = onEditImage,
                    canEditMetadata = canEditMetadata,
                    onEditMetadata = onEditMetadata,
                )
            }
        }
    }


}

private val WorldProfileCompactContentMaxWidth = 720.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldProfileCompactLayout(
    worldProfileVo: WorldProfileVo,
    activeInstances: List<InstanceVo>,
    favoriteEntryState: FavoriteEntryState,
    showRooms: Boolean,
    sysBottomPadding: Dp,
    worldIdForSharedElement: String,
    sharedKeyPrefix: String,
    sharedImageCacheKey: String?,
    onShowDetails: () -> Unit,
    onShowRooms: () -> Unit,
    onCreateRoom: () -> Unit,
    onFavoriteWorld: () -> Unit,
    onOpenRoom: (InstanceVo) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            // 保持窄屏信息层级，并将 4:3 封面在宽窗口中的高度限制为 540dp。
            modifier = Modifier
                .widthIn(max = WorldProfileCompactContentMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = sysBottomPadding + 24.dp),
        ) {
            item(key = "hero") {
                WorldProfileHero(
                    worldProfileVo = worldProfileVo,
                    favoriteEntryState = favoriteEntryState,
                    worldIdForSharedElement = worldIdForSharedElement,
                    sharedKeyPrefix = sharedKeyPrefix,
                    sharedImageCacheKey = sharedImageCacheKey,
                    onCreateRoom = onCreateRoom,
                    onFavoriteWorld = onFavoriteWorld,
                    showRooms = showRooms,
                    onShowDetails = onShowDetails,
                    onShowRooms = onShowRooms,
                )
            }

            if (!showRooms) {
                item(key = "details") {
                    WorldDetails(
                        worldProfileVo = worldProfileVo,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item(key = "rooms-heading") {
                    WorldRoomsHeading(
                        count = activeInstances.size,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (activeInstances.isEmpty()) {
                    item(key = "rooms-empty") {
                        EmptyInstanceCard(
                            onCreateInstance = onCreateRoom,
                            enabled = true,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = activeInstances,
                        key = { _, instance -> instance.id },
                    ) { _, instance ->
                        WorldRoomListItem(
                            instance = instance,
                            capacity = worldProfileVo.capacity,
                            onClick = { onOpenRoom(instance) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun WorldProfileHero(
    worldProfileVo: WorldProfileVo,
    favoriteEntryState: FavoriteEntryState,
    worldIdForSharedElement: String,
    sharedKeyPrefix: String,
    sharedImageCacheKey: String?,
    onCreateRoom: () -> Unit,
    onFavoriteWorld: () -> Unit,
    showRooms: Boolean,
    onShowDetails: () -> Unit,
    onShowRooms: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val panelCornerRadius = 24.dp
    val tabPanelHeight = 64.dp
    val bottomScrim = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
        ),
        endY = with(LocalDensity.current) { 100.dp.toPx() },
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val heroHeight = maxOf(maxWidth * 3f / 4f, 400.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight + tabPanelHeight - panelCornerRadius),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .align(Alignment.TopCenter),
            ) {
                AImage(
                    modifier = Modifier
                        .matchParentSize()
                        .sharedBoundsBy(
                            key = sharedKeyPrefix + worldIdForSharedElement + "WorldImage",
                            renderInOverlayDuringTransition = false,
                        ),
                    imageData = worldProfileVo.worldImageUrl.orEmpty(),
                    loadOriginalSize = true,
                    cachedPlaceholderKey = sharedImageCacheKey,
                )

                WorldPlatformBadges(
                    worldProfileVo = worldProfileVo,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = getInsetPadding(WindowInsets::getTop) + 72.dp, end = 12.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(bottomScrim)
                        .padding(
                            start = 8.dp,
                            top = 48.dp,
                            end = 8.dp,
                            bottom = panelCornerRadius + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    ATooltipBox(tooltip = { Text(worldProfileVo.worldName) }) {
                        SelectionContainer {
                            Text(
                                text = worldProfileVo.worldName,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 2.dp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Box(
                        modifier = Modifier.enableIf(worldProfileVo.authorName != null) {
                            simpleClickable {
                                worldProfileVo.authorID?.let { authorId ->
                                    navigator.push(
                                        UserProfileScreen(
                                            userProfileVO = UserProfileVo(
                                                id = authorId,
                                                displayName = worldProfileVo.authorName.orEmpty(),
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    ) {
                        Text(
                            text = worldProfileVo.authorName ?: strings.unknown,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelMedium,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    WorldProfileSummary(
                        worldProfileVo = worldProfileVo,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    WorldProfilePrimaryActions(
                        favoriteEntryState = favoriteEntryState,
                        onCreateRoom = onCreateRoom,
                        onFavoriteWorld = onFavoriteWorld,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tabPanelHeight)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(
                    topStart = panelCornerRadius,
                    topEnd = panelCornerRadius,
                ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
            ) {
                PrimaryTabRow(
                    selectedTabIndex = if (showRooms) 1 else 0,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Tab(
                        selected = !showRooms,
                        onClick = onShowDetails,
                        text = { Text(strings.groupTabDetails) },
                    )
                    Tab(
                        selected = showRooms,
                        onClick = onShowRooms,
                        text = { Text(strings.worldProfileRooms) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldPlatformBadges(
    worldProfileVo: WorldProfileVo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (worldProfileVo.isInCommunityLabs()) {
            WorldPlatformBadge(
                icon = AppIcons.FlaskConical,
                description = strings.worldProfileCommunityLabs,
            )
        }
        listOf(
            Triple(Windows, AppIcons.Computer, "PC"),
            Triple(Android, AppIcons.Android, "Android"),
            Triple(Ios, AppIcons.Apple, "iOS"),
        ).forEach { (platform, icon, description) ->
            if (platform !in worldProfileVo.supportedPlatforms) return@forEach
            WorldPlatformBadge(
                icon = icon,
                description = description,
            )
        }
    }
}

private fun WorldProfileVo.isInCommunityLabs(): Boolean =
    releaseStatus.equals("public", ignoreCase = true) &&
        labsPublicationDate.isPublicationDate() &&
        !publicationDate.isPublicationDate()

private fun String?.isPublicationDate(): Boolean =
    !isNullOrBlank() && !equals("none", ignoreCase = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldPlatformBadge(
    icon: ImageVector,
    description: String,
) {
    ATooltipBox(tooltip = { Text(description) }) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shadowElevation = 2.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.padding(7.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun WorldProfileSummary(
    worldProfileVo: WorldProfileVo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorldSummaryItem(
            icon = AppIcons.Person,
            value = strings.worldProfileCapacityValue.replace(
                "%count%",
                worldProfileVo.capacity.toString(),
            ),
            description = strings.worldProfileCapacity,
            modifier = Modifier.weight(1f),
        )
        WorldSummaryItem(
            icon = AppIcons.Groups,
            value = strings.worldProfileCapacityValue.replace(
                "%count%",
                (worldProfileVo.publicOccupants + worldProfileVo.privateOccupants).toString(),
            ),
            description = strings.worldProfileOnlineUsers,
            modifier = Modifier.weight(1f),
        )
        WorldSummaryItem(
            icon = AppIcons.Visibility,
            value = formatCompactCount(worldProfileVo.visits),
            description = strings.worldProfileVisits,
            modifier = Modifier.weight(1f),
        )
        WorldSummaryItem(
            icon = AppIcons.Favorite,
            value = formatCompactCount(worldProfileVo.favorites),
            description = strings.worldProfileFavorites,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldSummaryItem(
    icon: ImageVector,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.heightIn(min = 32.dp)) {
        ATooltipBox(tooltip = { Text(description) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WorldProfilePrimaryActions(
    favoriteEntryState: FavoriteEntryState,
    onCreateRoom: () -> Unit,
    onFavoriteWorld: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onCreateRoom,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.createInstance,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        OutlinedButton(
            onClick = onFavoriteWorld,
            enabled = favoriteEntryState != FavoriteEntryState.Loading &&
                favoriteEntryState != FavoriteEntryState.Unavailable,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = AppIcons.Favorite,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (favoriteEntryState) {
                    FavoriteEntryState.Loading -> strings.loading
                    FavoriteEntryState.Favorited -> strings.editFavorite
                    FavoriteEntryState.NotFavorited -> strings.favoriteWorld
                    FavoriteEntryState.LoadFailed -> strings.retry
                    FavoriteEntryState.Unavailable -> strings.favoriteWorld
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WorldRoomsHeading(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.worldProfileActiveRooms,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorldRoomListItem(
    instance: InstanceVo,
    capacity: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RegionIcon(
                size = 22.dp,
                region = instance.regionType,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "#${instance.instanceName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${instance.accessType.displayName} · ${instance.regionName.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${instance.currentUsers ?: 0} / $capacity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldDetails(
    worldProfileVo: WorldProfileVo,
    modifier: Modifier = Modifier,
) {
    val information = buildList {
        worldProfileVo.publicationDate.toWorldDisplayDate()?.let { date ->
            add(WorldInformationItem(AppIcons.Publish, strings.worldProfilePublishDate, date))
        }
        worldProfileVo.labsPublicationDate.toWorldDisplayDate()?.let { date ->
            add(WorldInformationItem(AppIcons.FlaskConical, strings.worldProfileLabReleaseDate, date))
        }
        worldProfileVo.updatedAt.toWorldDisplayDate()?.let { date ->
            add(WorldInformationItem(AppIcons.DateRange, strings.worldProfileUpdateDate, date))
        }
        worldProfileVo.createdAt.toWorldDisplayDate()?.let { date ->
            add(WorldInformationItem(AppIcons.DateRange, strings.worldProfileCreatedDate, date))
        }
        worldProfileVo.version?.let { version ->
            add(WorldInformationItem(AppIcons.Update, strings.worldProfileVersion, "v$version"))
        }
        add(WorldInformationItem(AppIcons.Hot, strings.worldProfileHeat, worldProfileVo.heat.toString()))
        add(
            WorldInformationItem(
                AppIcons.Trending,
                strings.worldProfilePopularity,
                worldProfileVo.popularity.toString(),
            )
        )
        worldProfileVo.platformFileSizes
            .sortedBy { platformFile ->
                when (platformFile.platform) {
                    Windows -> 0
                    Android -> 1
                    Ios -> 2
                }
            }
            .forEach { platformFile ->
                val icon = when (platformFile.platform) {
                    Windows -> AppIcons.Computer
                    Android -> AppIcons.Android
                    Ios -> AppIcons.Apple
                }
                add(WorldInformationItem(icon, platformFile.displayName, platformFile.formattedSize))
            }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = strings.worldProfileDescription,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = worldProfileVo.worldDescription.ifBlank { strings.unknown },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = strings.worldProfileInformation,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            information.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        WorldInformationTile(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (!worldProfileVo.tags.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.worldProfileAuthorTags,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    worldProfileVo.tags.forEach { tag ->
                        TextChip(text = tag)
                    }
                }
            }
        }
    }
}

private data class WorldInformationItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

private fun String?.toWorldDisplayDate(): String? = this
    ?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
    ?.toLocalDate()
    ?.simpleFormat

@Composable
private fun WorldInformationTile(
    item: WorldInformationItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldProfileTopBar(
    worldId: String,
    sysTopPadding: Dp,
    onReturn: () -> Unit,
    onManagePersistence: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    publicationState: WorldPublicationUiState,
    onPublicationAction: (WorldPublicationAction) -> Unit,
    showDelete: Boolean,
    deleteEnabled: Boolean,
    isDeleting: Boolean,
    isDeleted: Boolean,
    onDelete: () -> Unit,
    homeWorldActionState: HomeWorldActionState,
    onHomeWorldClick: () -> Unit,
    canEditImage: Boolean,
    onEditImage: () -> Unit,
    canEditMetadata: Boolean,
    onEditMetadata: () -> Unit,
) {
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    TopMenuBar(
        topBarHeight = 64.dp,
        sysTopPadding = sysTopPadding,
        offsetDp = 0.dp,
        ratio = 1f,
        color = MaterialTheme.colorScheme.surface,
        onReturn = onReturn,
        onMenu = { bottomSheetIsVisible = true },
        actions = { colors ->
            OfficialUrlShareButton(
                url = "https://vrchat.com/home/world/$worldId",
                colors = colors,
                forceSharePresentation = true,
            )
        },
    )

    ABottomSheet(
        isVisible = bottomSheetIsVisible,
        sheetState = sheetState,
        onDismissRequest = { bottomSheetIsVisible = false },
    ) {
        WorldProfileActionSheet(
            hideSheet = { sheetState.hide() },
            onHideCompletion = {
                if (!sheetState.isVisible) bottomSheetIsVisible = false
            },
            onManagePersistence = onManagePersistence,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            publicationState = publicationState,
            onPublicationAction = onPublicationAction,
            showDelete = showDelete,
            deleteEnabled = deleteEnabled,
            isDeleting = isDeleting,
            isDeleted = isDeleted,
            onDelete = onDelete,
            homeWorldActionState = homeWorldActionState,
            onHomeWorldClick = onHomeWorldClick,
            canEditImage = canEditImage,
            onEditImage = onEditImage,
            canEditMetadata = canEditMetadata,
            onEditMetadata = onEditMetadata,
        )
    }
}

@Composable
private fun ColumnScope.WorldProfileActionSheet(
    hideSheet: suspend () -> Unit,
    onHideCompletion: () -> Unit,
    onManagePersistence: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    publicationState: WorldPublicationUiState,
    onPublicationAction: (WorldPublicationAction) -> Unit,
    showDelete: Boolean,
    deleteEnabled: Boolean,
    isDeleting: Boolean,
    isDeleted: Boolean,
    onDelete: () -> Unit,
    homeWorldActionState: HomeWorldActionState,
    onHomeWorldClick: () -> Unit,
    canEditImage: Boolean,
    onEditImage: () -> Unit,
    canEditMetadata: Boolean,
    onEditMetadata: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismissAndRun: (() -> Unit) -> Unit = { action ->
        scope.launch {
            hideSheet()
            onHideCompletion()
            action()
        }
    }
    val publicationAction = publicationState.action
    val publicationDescription = when (publicationState.blockReason) {
        WorldPublicationBlockReason.Unavailable -> strings.worldPublishUnavailable
        WorldPublicationBlockReason.CheckFailed -> strings.worldPublishAvailabilityCheckFailed
        WorldPublicationBlockReason.RefreshRequired -> strings.worldPublicationRefreshRequired
        null -> when (publicationAction) {
            WorldPublicationAction.Publish -> strings.worldPublishAction
            WorldPublicationAction.Unpublish -> strings.worldUnpublishAction
            null -> ""
        }
    }
    val homeWorldDescription = when (homeWorldActionState.availability) {
        HomeWorldActionAvailability.Unavailable -> strings.worldProfileHomeWorldUnavailable
        HomeWorldActionAvailability.CanSet -> strings.worldProfileSetHomeWorld
        HomeWorldActionAvailability.Current -> strings.worldProfileResetHomeWorld
    }

    if (canEditMetadata) {
        WorldProfileSheetButton(
            text = strings.worldEditTitle,
            onClick = { dismissAndRun(onEditMetadata) },
        )
    }
    if (canEditImage) {
        WorldProfileSheetButton(
            text = strings.worldImageEditTitle,
            onClick = { dismissAndRun(onEditImage) },
        )
    }
    publicationAction?.let { action ->
        WorldProfileSheetButton(
            text = publicationDescription,
            enabled = publicationState.canExecute &&
                !publicationState.isChecking &&
                !publicationState.isChanging &&
                !isRefreshing && !isDeleting && !isDeleted,
            loading = publicationState.isChecking || publicationState.isChanging,
            onClick = { dismissAndRun { onPublicationAction(action) } },
        )
    }
    WorldProfileSheetButton(
        text = homeWorldDescription,
        enabled = homeWorldActionState.availability != HomeWorldActionAvailability.Unavailable &&
            !homeWorldActionState.isUpdating,
        loading = homeWorldActionState.isUpdating,
        onClick = { dismissAndRun(onHomeWorldClick) },
    )
    WorldProfileSheetButton(
        text = strings.worldPersistenceTitle,
        enabled = !isDeleting && !isDeleted,
        onClick = { dismissAndRun(onManagePersistence) },
    )
    WorldProfileSheetButton(
        text = strings.refresh,
        enabled = !isRefreshing && !publicationState.isChanging && !isDeleting && !isDeleted,
        loading = isRefreshing,
        onClick = { dismissAndRun(onRefresh) },
    )
    if (showDelete) {
        WorldProfileSheetButton(
            text = strings.worldDeleteAction,
            enabled = deleteEnabled,
            loading = isDeleting,
            isDestructive = true,
            onClick = { dismissAndRun(onDelete) },
        )
    }
}

@Composable
private fun ColumnScope.WorldProfileSheetButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = if (isDestructive) {
        ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
            disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
        )
    } else {
        ButtonDefaults.textButtonColors()
    }
    TextButton(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 24.dp),
        enabled = enabled,
        colors = colors,
        onClick = onClick,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text)
    }
}

private fun formatCompactCount(value: Int): String = when {
    value >= 1_000_000 -> formatCompactDecimal(value, 1_000_000, "M")
    value >= 1_000 -> formatCompactDecimal(value, 1_000, "K")
    else -> value.toString()
}

private fun formatCompactDecimal(value: Int, unit: Int, suffix: String): String {
    val tenths = value / (unit / 10)
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) "$whole$suffix" else "$whole.$fraction$suffix"
}


@Composable
private fun WorldPublicationConfirmationDialog(
    action: WorldPublicationAction,
    worldName: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = when (action) {
        WorldPublicationAction.Publish -> strings.worldPublishConfirmationTitle
        WorldPublicationAction.Unpublish -> strings.worldUnpublishConfirmationTitle
    }
    val message = when (action) {
        WorldPublicationAction.Publish -> strings.worldPublishConfirmationMessage
        WorldPublicationAction.Unpublish -> strings.worldUnpublishConfirmationMessage
    }.replace("%s", worldName)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = when (action) {
                    WorldPublicationAction.Publish -> AppIcons.Publish
                    WorldPublicationAction.Unpublish -> AppIcons.VisibilityOff
                },
                contentDescription = null,
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun WorldDeletionConfirmationDialog(
    worldName: String,
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
        title = { Text(strings.worldDeleteConfirmationTitle) },
        text = {
            Text(strings.worldDeleteConfirmationMessage.replace("%name%", worldName))
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
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(strings.worldDeleteAction)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isDeleting,
                onClick = onDismiss,
            ) {
                Text(strings.cancel)
            }
        },
    )
}
