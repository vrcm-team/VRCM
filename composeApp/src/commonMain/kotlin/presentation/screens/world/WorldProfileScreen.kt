package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.HandleBackNavigation
import io.github.vrcmteam.vrcm.presentation.screens.world.data.SheetState
import kotlin.math.abs
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
import io.github.vrcmteam.vrcm.presentation.screens.world.components.InstanceCard
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
        val displayedWorld = profileVoState ?: worldProfileVO
        var pendingHomeWorldAction by remember(displayedWorld.worldId) {
            mutableStateOf<HomeWorldAction?>(null)
        }
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
                pendingHomeWorldAction = null
            }
        }

        LaunchedEffect(imageEditState.canEdit) {
            if (!imageEditState.canEdit) showImageEditSheet = false
        }
        LaunchedEffect(metadataEditState.canEdit) {
            if (!metadataEditState.canEdit) showMetadataEditor = false
        }

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
                onHomeWorldClick = { action -> pendingHomeWorldAction = action },
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

        pendingHomeWorldAction?.let { action ->
            val resetHomeWorld = action == HomeWorldAction.Reset
            val actionStillAllowed = homeWorldActionState.availability !=
                HomeWorldActionAvailability.Unavailable &&
                (action != HomeWorldAction.Reset ||
                    homeWorldActionState.availability == HomeWorldActionAvailability.Current)
            AppAlert(
                onDismissRequest = {
                    if (!homeWorldActionState.isUpdating) pendingHomeWorldAction = null
                },
                title = {
                    AppText(
                        if (resetHomeWorld) strings.worldProfileResetHomeWorld
                        else strings.worldProfileSetHomeWorld
                    )
                },
                text = {
                    AppText(
                        if (resetHomeWorld) strings.worldProfileResetHomeWorldConfirmation
                        else strings.worldProfileSetHomeWorldConfirmation
                    )
                },
                confirmButton = {
                    AppButton(
                        enabled = !homeWorldActionState.isUpdating &&
                            actionStillAllowed,
                        onClick = {
                            pendingHomeWorldAction = null
                            screenModel.updateHomeWorld(action)
                        },
                        style = AppButtonStyle.Plain,
                    ) {
                        AppText(strings.confirm)
                    }
                },
                dismissButton = {
                    AppButton(
                        enabled = !homeWorldActionState.isUpdating,
                        onClick = { pendingHomeWorldAction = null },
                        style = AppButtonStyle.Plain,
                    ) {
                        AppText(strings.cancel)
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
        onHomeWorldClick: (HomeWorldAction) -> Unit = {},
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

        // 模糊效果状态
        val hazeState = remember { HazeState() }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            // 屏幕宽度减去左右中间边距

            // 每行四个信息块：两侧页面边距 + 三个 8 dp 间隔
            val itemSize = DpSize(width = (maxWidth - AppSpacing.page * 2 - 8.dp * 3) / 4, height = 68.dp)
            // ========== 尺寸计算 ==========
            val sysTopPadding = getInsetPadding(WindowInsets::getTop)
            val imageHigh = maxHeight / 5 // 图片高度为屏幕高度的1/5
            val contentPadding = 8.dp // 内容区域内边距

            val sizes = remember(maxHeight, maxWidth, sysTopPadding) {
                WorldDetailSizesState(
                    maxHeight = maxHeight,
                    imageHigh = imageHigh,
                    // 折叠高度：留出图片高度+顶部信息+两行信息块的空间
                    collapsedHeight = (maxHeight * 2 / 3) - contentPadding - (itemSize.height * 2 + contentPadding * 2),
                    // 半展开高度：屏幕高度的2/3
                    halfExpandedHeight = (maxHeight * 2 / 3) - contentPadding,
                    // 完全展开高度：完整屏幕高度减去状态栏
                    expandedHeight = maxHeight - sysTopPadding,
                    topBarHeight = 64.dp,
                    sysTopPadding = sysTopPadding,
                    itemSize = itemSize
                )
            }
            // ========== BottomSheet状态管理 ==========
            var sheetState by rememberSaveable(worldProfileVo.worldId) { mutableStateOf(SheetState.HALF_EXPANDED) }
            var dragOffset by remember { mutableStateOf(0f) }
            val collapseExpandedInstances = {
                sheetState = SheetState.HALF_EXPANDED
                dragOffset = 0f
            }
            val handleReturn = {
                if (sheetState == SheetState.EXPANDED) {
                    collapseExpandedInstances()
                } else {
                    onReturn()
                }
            }

            HandleBackNavigation(
                enabled = sheetState == SheetState.EXPANDED,
                onBack = collapseExpandedInstances,
            )

            // 计算目标高度和当前高度
            val bottomSheetState = calculateBottomSheetState(
                sheetState = sheetState,
                dragOffset = dragOffset,
                sizes = sizes
            )

            // 背景图、信息区和底部面板是内容层：顶栏的玻璃按钮取样它做模糊
            val glassBackdrop = rememberGlassBackdrop()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassBackdropSource(glassBackdrop)
                    .background(AppTheme.colors.groupedBackground)
            ) {
                // ========== 渲染背景图像 ==========
                RenderBackgroundImage(
                    worldId = location ?: worldProfileVo.worldId,
                    imageUrl = worldProfileVo.worldImageUrl ?: "",
                    hazeState = hazeState,
                    imageHeight = sizes.imageHigh * 2,
                    sharedKeyPrefix = sharedKeyPrefix,
                    sharedImageCacheKey = sharedImageCacheKey,
                )

                // ========== 应用模糊效果 ==========
                ApplyBlurEffect(
                    hazeState = hazeState,
                    blurRadius = bottomSheetState.blurRadius.dp,
                    overlayAlpha = bottomSheetState.overlayAlpha
                )

                // ========== 主内容区域 ==========
                RenderMainContent(
                    worldProfileVo = worldProfileVo,
                    sizes = sizes,
                    collapsedAlphaVariant = 1 - bottomSheetState.collapsedAlpha,
                )

                // ========== BottomSheet ==========
                RenderBottomSheet(
                    worldProfileVo = worldProfileVo,
                    activeInstances = activeInstances,
                    favoriteEntryState = favoriteEntryState,
                    bottomSheetState = bottomSheetState,
                    sizes = sizes,
                    onExpanded = { sheetState = SheetState.EXPANDED },
                    onDragDelta = { delta -> dragOffset += -delta },
                    onDragStopped = { velocity ->
                        // 决定最终状态并重置拖动偏移
                        sheetState = determineSheetState(
                            currentHeightValue = bottomSheetState.targetHeight.value + dragOffset,
                            velocity = velocity,
                            currentState = sheetState,
                            sizes = sizes
                        )
                        dragOffset = 0f
                    },
                    onCreateRoom = createRoom,
                    onFavoriteWorld = favoriteWorld,
                    onOpenRoom = openRoom,
                )
            }

            // ========== 顶部菜单栏 ==========
            CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
                WorldProfileTopBar(
                    worldId = worldProfileVo.worldId,
                    worldName = worldProfileVo.worldName,
                    blurProgress = bottomSheetState.blurProgress,
                    topBarHeight = sizes.topBarHeight,
                    sysTopPadding = sizes.sysTopPadding,
                    onReturn = handleReturn,
                    onCollapse = { sheetState = SheetState.COLLAPSED },
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

// ======================================
// 状态计算函数
// ======================================

/**
 * 计算BottomSheet状态
 */
@Composable
private fun calculateBottomSheetState(
    sheetState: SheetState,
    dragOffset: Float,
    sizes: WorldDetailSizesState,
): BottomSheetUIState {
    // 计算目标高度
    val targetHeight = when (sheetState) {
        SheetState.COLLAPSED -> sizes.collapsedHeight
        SheetState.HALF_EXPANDED -> sizes.halfExpandedHeight
        SheetState.EXPANDED -> sizes.expandedHeight
    }

    // 计算当前显示高度（原目标高度 + 拖动偏移）
    val currentHeight = (targetHeight.value + dragOffset / 2).coerceIn(
        sizes.collapsedHeight.value,
        sizes.expandedHeight.value
    ).dp

    // 使用动画平滑过渡
    val animatedHeight by animateDpAsState(
        targetValue = currentHeight,
        label = "BottomSheet Height Animation"
    )

    // 计算模糊相关状态
    val blurProgress = if (currentHeight.value > sizes.halfExpandedHeight.value) {
        (currentHeight.value - sizes.halfExpandedHeight.value) /
                (sizes.expandedHeight.value - sizes.halfExpandedHeight.value)
    } else {
        0f
    }.coerceIn(0f, 1f)

    val blurRadius by animateFloatAsState(
        targetValue = (blurProgress * 25f).coerceIn(0f, 25f),
        animationSpec = tween(100),
        label = "Blur Animation"
    )

    val blurAlpha by animateFloatAsState(
        targetValue = blurProgress,
        label = "Blur Animation"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = (blurProgress * 0.6f).coerceIn(0f, 0.6f),
        label = "Overlay Animation"
    )

    // 计算折叠状态进度
    val collapsedProgress = (currentHeight.value - sizes.collapsedHeight.value) /
            (sizes.halfExpandedHeight.value - sizes.collapsedHeight.value)
    val collapsedAlpha by animateFloatAsState(
        targetValue = collapsedProgress.coerceIn(0f, 1f),
    )

    return BottomSheetUIState(
        targetHeight = targetHeight,
        currentHeight = currentHeight,
        animatedHeight = animatedHeight,
        blurProgress = blurProgress,
        blurRadius = blurRadius,
        blurAlpha = blurAlpha,
        overlayAlpha = overlayAlpha,
        collapsedProgress = collapsedProgress,
        collapsedAlpha = collapsedAlpha
    )
}

/**
 * 根据拖动结束时的状态确定最终Sheet状态
 */
private fun determineSheetState(
    currentHeightValue: Float,
    velocity: Float,
    currentState: SheetState,
    sizes: WorldDetailSizesState,
): SheetState {
    // 计算各状态高度
    val collapsedHeight = sizes.collapsedHeight.value
    val halfExpandedHeight = sizes.halfExpandedHeight.value
    val expandedHeight = sizes.expandedHeight.value

    // 计算当前高度距离各状态的距离
    val distToCollapsed = abs(currentHeightValue - collapsedHeight)
    val distToHalfExpanded = abs(currentHeightValue - halfExpandedHeight)
    val distToExpanded = abs(currentHeightValue - expandedHeight)

    // 计算相对位置 - 当前高度在整个范围内的位置比例(0~1)
    val positionRatio = (currentHeightValue - collapsedHeight) / (expandedHeight - collapsedHeight)

    // 速度处理 - 正规化速度值 (正值表示向下拖动/收起，负值表示向上拖动/展开)
    val normalizedVelocity = (velocity / 800f).coerceIn(-3f, 3f)

    // 防止状态跳跃：根据当前状态和速度限制可达状态
    val allowedStates = when (currentState) {
        SheetState.COLLAPSED -> {
            // 从折叠状态只能到达半展开
            if (normalizedVelocity < -1.5f) listOf(SheetState.HALF_EXPANDED)
            else listOf(SheetState.COLLAPSED, SheetState.HALF_EXPANDED)
        }

        SheetState.HALF_EXPANDED -> {
            // 从半展开可到达任何状态，但需要根据位置和速度判断
            listOf(SheetState.COLLAPSED, SheetState.HALF_EXPANDED, SheetState.EXPANDED)
        }

        SheetState.EXPANDED -> {
            // 从展开状态只能到达半展开
            if (normalizedVelocity > 1.5f) listOf(SheetState.HALF_EXPANDED)
            else listOf(SheetState.HALF_EXPANDED, SheetState.EXPANDED)
        }
    }

    // 强磁吸效果：如果非常接近某个状态且没有明显反向速度，直接返回该状态
    when {
        // 非常接近折叠状态 (距离小于总范围的10%)
        distToCollapsed < (expandedHeight - collapsedHeight) * 0.1f &&
                normalizedVelocity > -1f &&
                SheetState.COLLAPSED in allowedStates ->
            return SheetState.COLLAPSED

        // 非常接近半展开状态 (距离小于总范围的10%)
        distToHalfExpanded < (expandedHeight - collapsedHeight) * 0.1f &&
                abs(normalizedVelocity) < 1f &&
                SheetState.HALF_EXPANDED in allowedStates ->
            return SheetState.HALF_EXPANDED

        // 非常接近展开状态 (距离小于总范围的10%)
        distToExpanded < (expandedHeight - collapsedHeight) * 0.1f &&
                normalizedVelocity < 1f &&
                SheetState.EXPANDED in allowedStates ->
            return SheetState.EXPANDED
    }

    // 处理中等速度滑动 - 主要根据方向和位置决定
    if (abs(normalizedVelocity) > 1f) {
        return when {
            normalizedVelocity < 0 -> { // 向上滑动
                // 在底部区域向上滑，到达半展开
                if (positionRatio < 0.4f && SheetState.HALF_EXPANDED in allowedStates)
                    SheetState.HALF_EXPANDED
                // 在上部区域向上滑，且允许展开，则展开
                else if (positionRatio > 0.6f && SheetState.EXPANDED in allowedStates)
                    SheetState.EXPANDED
                // 默认保持在半展开
                else SheetState.HALF_EXPANDED
            }

            else -> { // 向下滑动
                // 在上部区域向下滑，到达半展开
                if (positionRatio > 0.6f && SheetState.HALF_EXPANDED in allowedStates)
                    SheetState.HALF_EXPANDED
                // 在底部区域向下滑，且允许折叠，则折叠
                else if (positionRatio < 0.4f && SheetState.COLLAPSED in allowedStates)
                    SheetState.COLLAPSED
                // 默认保持在半展开
                else SheetState.HALF_EXPANDED
            }
        }
    }

    // 对于低速或停止的情况，纯粹根据位置决定
    return when {
        // 位于下1/3区域，倾向于折叠
        positionRatio < 0.33f && SheetState.COLLAPSED in allowedStates ->
            SheetState.COLLAPSED
        // 位于上1/3区域，倾向于展开
        positionRatio > 0.67f && SheetState.EXPANDED in allowedStates ->
            SheetState.EXPANDED
        // 中间区域或其他情况，倾向于半展开
        else -> SheetState.HALF_EXPANDED
    }
}

// ======================================
// UI 渲染组件
// ======================================

/**
 * 渲染背景图像
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RenderBackgroundImage(
    worldId: String,
    imageUrl: String,
    hazeState: HazeState,
    imageHeight: Dp,
    sharedKeyPrefix: String = "",
    sharedImageCacheKey: String? = null,
) {
    AImage(
        modifier = Modifier
            .height(imageHeight)
            .hazeSource(hazeState)
            .sharedBoundsBy(
                key = sharedKeyPrefix + worldId + "WorldImage",
                renderInOverlayDuringTransition = false
        ),
        imageData = imageUrl,
        loadOriginalSize = true,
        cachedPlaceholderKey = sharedImageCacheKey,
    )
}

/**
 * 应用模糊效果
 */
@Composable
private fun ApplyBlurEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    overlayAlpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(
                hazeState,
                style = HazeStyle(
                    blurRadius = blurRadius,
                    tint = null,
                    backgroundColor = AppTheme.colors.label.copy(alpha = overlayAlpha)
                )
            )
    )
}

/**
 * 渲染主内容区域
 */
@Composable
private fun RenderMainContent(
    worldProfileVo: WorldProfileVo,
    sizes: WorldDetailSizesState,
    collapsedAlphaVariant: Float,
) {

    // 渐变和卡片样式
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent, // 起始颜色（完全透明）
            AppTheme.colors.groupedBackground// 结束
        ),
        endY = with(LocalDensity.current){ 100.dp.toPx() },
    )


    val itemSize = sizes.itemSize
    val navigator = LocalNavigator.currentOrThrow

    // 主内容区域
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = sizes.maxHeight - sizes.halfExpandedHeight - sizes.imageHigh * 0.75f)
            .background(gradientBrush)
            .padding(horizontal = AppSpacing.page),
    ) {
        Spacer(modifier = Modifier.height(sizes.imageHigh * 0.25f))
        // 顶部信息区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            ATooltipBox(
                tooltip = {
                    AppText(text = worldProfileVo.worldName)
                },
            ) {
                SelectionContainer {
                    AppText(
                        text = worldProfileVo.worldName,
                        color = AppTheme.colors.label,
                        style = AppTheme.type.title1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier.enableIf(worldProfileVo.authorName != null){
                    simpleClickable {
                        worldProfileVo.authorID?.let {
                            val userProfileScreen = UserProfileScreen(
                                userProfileVO = UserProfileVo(
                                    id = it,
                                    displayName = worldProfileVo.authorName.orEmpty(),
                                )
                            )
                            navigator.push(userProfileScreen)
                        }
                    }
                }
            ) {
                // 可点的作者名按链接处理：tint 色、不加下划线
                AppText(
                    text = worldProfileVo.authorName ?: strings.unknown,
                    color = if (worldProfileVo.authorName != null) AppTheme.colors.tint else AppTheme.colors.secondaryLabel,
                    style = AppTheme.type.subheadlineEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        InfoArea(worldProfileVo, collapsedAlphaVariant, itemSize)

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ColumnScope.InfoArea(
    worldProfileVo: WorldProfileVo,
    collapsedAlphaVariant: Float,
    itemSize: DpSize,
) {
    // 基本信息卡片（日期类只在有值时显示，不放"未知"占位）
    val basicInfoCards = buildList {
        // 世界容量
        add(Triple(AppIcons.Person, "${worldProfileVo.capacity}", strings.worldProfileCapacity))
        // 在线人数
        add(
            Triple(
                AppIcons.Group,
                "${worldProfileVo.publicOccupants + worldProfileVo.privateOccupants}",
                strings.worldProfileOnlineUsers
            )
        )
        // 访问次数
        add(Triple(AppIcons.Visibility, "${worldProfileVo.visits}", strings.worldProfileVisits))
        // 收藏数
        add(Triple(AppIcons.Favorite, "${worldProfileVo.favorites}", strings.worldProfileFavorites))
        // 热度
        add(Triple(AppIcons.Hot, "${worldProfileVo.heat}", strings.worldProfileHeat))
        // 热门程度
        add(Triple(AppIcons.Trending, "${worldProfileVo.popularity}", strings.worldProfilePopularity))
        // 版本
        add(Triple(AppIcons.Refresh, "v${worldProfileVo.version ?: 1}", strings.worldProfileVersion))
        // 发布时间
        worldProfileVo.publicationDate.toWorldDisplayDate()?.let { add(Triple(AppIcons.Publish, it, strings.worldProfilePublishDate)) }
        // 更新时间
        worldProfileVo.updatedAt.toWorldDisplayDate()?.let { add(Triple(AppIcons.DateRange, it, strings.worldProfileUpdateDate)) }
        // 创建时间
        worldProfileVo.createdAt.toWorldDisplayDate()?.let { add(Triple(AppIcons.DateRange, it, strings.worldProfileCreatedDate)) }
        // 实验室发布日期
        worldProfileVo.labsPublicationDate.toWorldDisplayDate()?.let { add(Triple(AppIcons.FlaskConical, it, strings.worldProfileLabReleaseDate)) }
    }

    // 平台文件大小卡片
    val platformSizeCards = worldProfileVo.platformFileSizes.map { platformSize ->
        val icon = when (platformSize.platform) {
            Windows -> AppIcons.Computer
            Ios -> AppIcons.Apple
            Android -> AppIcons.Android
        }
        Triple(icon, platformSize.formattedSize, platformSize.displayName)
    }

    // 合并所有卡片
    val infoCards = basicInfoCards + platformSizeCards

    // 计算每页显示的卡片数量
    val cardsPerRow = 4 // 每行显示4个卡片
    val rowsPerPage = 2 // 每页显示2行
    val cardsPerPage = cardsPerRow * rowsPerPage // 每页8个卡片
    val pageCount = (infoCards.size + cardsPerPage - 1) / cardsPerPage
    // 使用HorizontalPager实现水平滑动
    val pagerState = rememberPagerState(pageCount = { pageCount })
    // 信息卡片区域
    AnimatedVisibility(
        visible = collapsedAlphaVariant > 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(collapsedAlphaVariant),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemSize.height * 2 + 8.dp), // 增加高度限制以适应更大的卡片
            ) { page ->
                // 计算当前页应显示的卡片
                val startIndex = page * cardsPerPage
                val endIndex = minOf(startIndex + cardsPerPage, infoCards.size)
                val pageCards = infoCards.subList(startIndex, endIndex)

                // 添加页面过渡动画
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 第一行卡片
                    val firstRowEnd = minOf(startIndex + cardsPerRow, endIndex)
                    if (startIndex < firstRowEnd) {
                        val firstRowCards = pageCards.subList(0, firstRowEnd - startIndex)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for ((icon, label, description) in firstRowCards) {
                                InfoItemBlock(
                                    size = itemSize,
                                    icon = icon,
                                    label = label,
                                    description = description
                                )
                            }
                        }
                    }

                    // 第二行卡片
                    val secondRowStart = firstRowEnd
                    val secondRowEnd = minOf(secondRowStart + cardsPerRow, endIndex)
                    if (secondRowStart < secondRowEnd) {
                        val secondRowCards = pageCards.subList(firstRowEnd - startIndex, secondRowEnd - startIndex)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for ((icon, label, description) in secondRowCards) {
                                InfoItemBlock(
                                    size = itemSize,
                                    icon = icon,
                                    label = label,
                                    description = description
                                )
                            }
                        }
                    }
                }
            }

        }

    }
}

private fun String?.toWorldDisplayDate(): String? = this
    ?.takeIf { it.isNotEmpty() }
    ?.toLocalDate()
    ?.simpleFormat

/**
 * 渲染BottomSheet
 */
@Composable
private fun RenderBottomSheet(
    worldProfileVo: WorldProfileVo,
    activeInstances: List<InstanceVo>,
    favoriteEntryState: FavoriteEntryState,
    bottomSheetState: BottomSheetUIState,
    sizes: WorldDetailSizesState,
    onExpanded: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onCreateRoom: () -> Unit,
    onFavoriteWorld: () -> Unit,
    onOpenRoom: (InstanceVo) -> Unit,
) {
    // BottomSheet容器
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomSheetState.animatedHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        state = rememberDraggableState(onDelta = onDragDelta),
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> onDragStopped(velocity) }
                    )
            ) {
                // 拖动指示条
                DragBar(dragOffset = bottomSheetState.currentHeight.value - bottomSheetState.targetHeight.value)

                // 全屏时添加额外空间
                Spacer(modifier = Modifier.height(bottomSheetState.blurProgress * (sizes.topBarHeight - 24.dp)))

                // 主要信息内容
                RenderBottomSheetContent(
                    worldProfileVo = worldProfileVo,
                    activeInstances = activeInstances,
                    favoriteEntryState = favoriteEntryState,
                    bottomSheetState = bottomSheetState,
                    onExpanded = onExpanded,
                    onCreateRoom = onCreateRoom,
                    onFavoriteWorld = onFavoriteWorld,
                    onOpenRoom = onOpenRoom,
                )
            }
        }
    }
}

/**
 * 渲染BottomSheet内容
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderBottomSheetContent(
    worldProfileVo: WorldProfileVo,
    activeInstances: List<InstanceVo>,
    favoriteEntryState: FavoriteEntryState,
    bottomSheetState: BottomSheetUIState,
    onExpanded: () -> Unit,
    onCreateRoom: () -> Unit,
    onFavoriteWorld: () -> Unit,
    onOpenRoom: (InstanceVo) -> Unit,
) {
    // 上滑渐变小
    val fl = 1 - bottomSheetState.blurProgress
    val hasNoInstances = activeInstances.isEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = getInsetPadding(WindowInsets::getBottom))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标签区域

            if (fl > 0f) {
                Column(
                    modifier = Modifier.alpha(fl),
                    verticalArrangement = Arrangement.spacedBy(4.dp * fl)
                ) {
                    // 描述标题
                    AppText(
                        text = strings.worldProfileDescription,
                        style = AppTheme.type.headline,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.label.copy(
                            alpha = 1f - (0.3f * abs(
                                (bottomSheetState.currentHeight.value - bottomSheetState.targetHeight.value).coerceIn(
                                    -30f,
                                    30f
                                )
                            ) / 30f)
                        )
                    )

                    // 描述内容
                    AppText(
                        modifier = Modifier.heightIn(max = (bottomSheetState.animatedHeight / 3.5f * fl))
                            .verticalScroll(rememberScrollState()),
                        text = worldProfileVo.worldDescription,
                        style = AppTheme.type.subheadline,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    if (worldProfileVo.tags?.isNotEmpty() == true) {
                        AppText(
                            text = strings.worldProfileAuthorTags,
                            fontWeight = FontWeight.Bold,
                            style = AppTheme.type.headline,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            worldProfileVo.tags.forEach { tag ->
                                TextChip(text = tag)
                            }
                        }
                    }
                }
            }

            // 堆叠卡片 - 改为随着滑动过程展开

            if (hasNoInstances && bottomSheetState.blurProgress > 0f) {
                EmptyInstanceCard(
                    onCreateInstance = onCreateRoom,
                    enabled = bottomSheetState.blurProgress >= 1f,
                    modifier = Modifier.alpha(bottomSheetState.blurProgress),
                )
            }

            if (!hasNoInstances && bottomSheetState.collapsedAlpha > 0f) {
                Box(modifier = Modifier.alpha(bottomSheetState.collapsedAlpha)) {
                    StackedCards(
                        instances = activeInstances,
                        maxVisibleCards = 3,
                        // 传递展开程度，用于调整卡片样式
                        expandProgress = bottomSheetState.blurProgress,
                        onOpenRoom = onOpenRoom,
                        onExpandCardClick = { onExpanded() },
                    )
                }
            }
        }

        val buttonAlpha = (1 - bottomSheetState.blurProgress * 2).coerceIn(0f, 1f)
        // 操作按钮 - 始终显示在底部
        if (buttonAlpha <= 0f) return@Box
        WorldProfilePrimaryActions(
            favoriteEntryState = favoriteEntryState,
            onCreateRoom = onCreateRoom,
            onFavoriteWorld = onFavoriteWorld,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .offset(y = 80.dp * bottomSheetState.blurProgress)
                .alpha(buttonAlpha)
                .align(Alignment.BottomCenter)
                .padding(vertical = 16.dp),
        )
    }
}

/**
 * 拖动指示条
 */
@Composable
private fun DragBar(dragOffset: Float = 100f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(5.dp)
                .clip(AppShapes.capsule)
                .background(
                    AppTheme.colors.tertiaryLabel.copy(
                        alpha = 0.5f + (0.5f * abs(dragOffset.coerceIn(-100f, 100f)) / 100f)
                    )
                )
        )
    }
}

/**
 * 信息块
 */
@Composable
private fun InfoItemBlock(
    color: Color = AppTheme.colors.secondaryGroupedBackground,
    size: DpSize,
    icon: ImageVector,
    label: String,
    description: String,
) {
    ATooltipBox(
        tooltip = {
            AppText(
                text = description,
                style = AppTheme.type.caption2Emphasized
            )
        }
    ) {
        Column(
            modifier = Modifier
                .size(size)
                .clip(AppShapes.m)
                .background(color),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(
                imageVector = icon,
                tint = AppTheme.colors.tint,
                contentDescription = description,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = label,
                color = AppTheme.colors.label,
                style = AppTheme.type.footnoteEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
fun StackedCards(
    instances: List<InstanceVo>,
    maxVisibleCards: Int = 3,
    expandProgress: Float = 0f,  // 默认值为0，表示未展开
    onOpenRoom: (InstanceVo) -> Unit = {},
    onExpandCardClick: () -> Unit,
) {

    val size = instances.size
    // 空状态由调用方优先渲染，保留防御性返回。
    if (size == 0) return
    val isFullyExpanded = expandProgress >= 1f

    val doExpandCardClick = if (expandProgress == 0f) onExpandCardClick else null
    if (isFullyExpanded) {
        // 在完全展开状态下使用Column布局垂直排列所有卡片
        val lazyListState = rememberLazyListState()
        val layoutInfo by remember { derivedStateOf { lazyListState.layoutInfo } }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(instances, key = { _, instance -> instance.id }) { index, instance ->
                val visibleItemInfo by remember {
                    derivedStateOf {
                        layoutInfo.visibleItemsInfo.find { it.index == index }
                    }
                }
                // 计算缩放比例
                val scale by animateFloatAsState(
                    targetValue = visibleItemInfo?.let {
                        val itemBottom = it.offset + it.size
                        val viewportBottom = layoutInfo.viewportEndOffset
                        val distanceFromBottom = viewportBottom - itemBottom

                        when {
                            // 元素完全在视口下方
                            distanceFromBottom < -it.size -> 0.7f
                            // 元素开始进入视口
                            distanceFromBottom < 0 -> 0.7f + 0.3f * (1 - distanceFromBottom / -it.size.toFloat())
                            // 元素完全可见
                            else -> 1f
                        }
                    } ?: 0.7f,  // 不可见元素保持最小缩放
                    animationSpec = tween(300)
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    InstanceCard(
                        instance = instance,
                        size = instances.size - index,
                        index = index,
                        verticalOffset = 0.dp, // 在Column中不需要手动设置偏移
                        scaleEffect = 1f,
                        alphaEffect = 1f,
                        // 展开后点卡片打开房间详情（管理 / 关闭房间在这里）
                        onClick = { onOpenRoom(instance) },
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .enableIf(expandProgress >= 1f) {
                    fillMaxHeight()
                }
        ) {
            // 计算需要显示的卡片数量
            val visibleCardsCount = minOf(maxVisibleCards, size)

            // 只显示前visibleCardsCount张卡片，并且倒序渲染（最后一张卡片最先渲染，在最底层）
            // 获取要显示的卡片子列表
            val visibleCards = instances.take(visibleCardsCount)

            // 从下往上渲染卡片（索引从visibleCards.size-1到0）
            for (i in visibleCards.size - 1 downTo 1) {
                val instance = visibleCards[i]


                // 基础偏移和视觉效果
                val baseOffset = 10.dp * i
                val baseScale = 1f - (0.1f * i)
                val baseAlpha = 1f - (0.25f * i)

                // 随展开程度调整的偏移量（展开时增加间距）
                val expandedOffset = i * 130f
                val currentOffset = baseOffset + (expandedOffset.dp - baseOffset) * expandProgress

                // 随展开程度调整的透明度和缩放（展开时减少透明度和缩放效果）
                val currentScale = baseScale + ((1f - baseScale) * expandProgress)
                val currentAlpha = baseAlpha + ((1f - baseAlpha) * expandProgress)

                InstanceCard(
                    instance = instance,
                    size = size,
                    index = i,
                    verticalOffset = currentOffset,
                    scaleEffect = currentScale,
                    alphaEffect = currentAlpha,
                    expandProgress = expandProgress
                )
            }

            // 显示顶部卡片（始终在最上方且完全不透明不缩小）
            if (visibleCards.isNotEmpty()) {
                InstanceCard(
                    instance = visibleCards.first(),
                    size = size,
                    index = 0,
                    verticalOffset = 0.dp,
                    scaleEffect = 1f,
                    alphaEffect = 1f,
                    expandProgress = expandProgress,
                    onClick = doExpandCardClick
                )
            }

            // 显示剩余卡片数量的指示器
            if (instances.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 16.dp)
                        .alpha((1f - expandProgress * 3f).coerceIn(0f, 1f)) // 随着展开进度增加而变透明
                        .background(
                            color = AppTheme.colors.tint,
                            shape = AppShapes.capsule
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    AppText(
                        text = "+${instances.size - 1}",
                        color = AppTheme.colors.onTint,
                        style = AppTheme.type.caption1Emphasized
                    )
                }
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
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppButton(
            onClick = onCreateRoom,
            modifier = Modifier.weight(1f),
            style = AppButtonStyle.Prominent,
        ) {
            AppText(
                text = strings.createInstance,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AppButton(
            onClick = onFavoriteWorld,
            enabled = favoriteEntryState != FavoriteEntryState.Loading &&
                favoriteEntryState != FavoriteEntryState.Unavailable,
            modifier = Modifier.weight(1f),
            style = AppButtonStyle.Gray,
        ) {
            AppText(
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
private fun WorldProfileTopBar(
    worldId: String,
    worldName: String,
    blurProgress: Float,
    topBarHeight: Dp,
    sysTopPadding: Dp,
    onReturn: () -> Unit,
    onCollapse: () -> Unit,
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
    onHomeWorldClick: (HomeWorldAction) -> Unit,
    canEditImage: Boolean,
    onEditImage: () -> Unit,
    canEditMetadata: Boolean,
    onEditMetadata: () -> Unit,
) {
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    val sheetState = rememberAppSheetState()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val topBarRatio = (1 - blurProgress).coerceIn(0f, 1f)
        val titleMaxWidth = (maxWidth - 208.dp).coerceIn(40.dp, 200.dp)

        TopMenuBar(
            topBarHeight = topBarHeight,
            sysTopPadding = sysTopPadding,
            offsetDp = 0.dp,
            ratio = topBarRatio,
            color = AppTheme.colors.secondaryGroupedBackground,
            onReturn = onReturn,
            onMenu = { bottomSheetIsVisible = true },
            menuContentDescription = strings.worldProfileMoreActions,
            actions = {
                OfficialUrlShareButton(
                    url = "https://vrchat.com/home/world/$worldId",
                    forceSharePresentation = true,
                )
            },
        )
        // 标题显示：面板全展开时淡入，点一下收起面板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight + sysTopPadding)
                .alpha(blurProgress)
                .padding(top = sysTopPadding)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .simpleClickable(onClick = onCollapse),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppText(
                    modifier = Modifier.widthIn(max = titleMaxWidth),
                    text = worldName,
                    textAlign = TextAlign.Center,
                    style = AppTheme.type.headline,
                    color = AppTheme.colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

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
    onHomeWorldClick: (HomeWorldAction) -> Unit,
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
        enabled = homeWorldActionState.action != null && !homeWorldActionState.isUpdating,
        loading = homeWorldActionState.isUpdating,
        onClick = {
            homeWorldActionState.action?.let { action ->
                dismissAndRun { onHomeWorldClick(action) }
            }
        },
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
        AppText(text = text)
    }
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

    AppAlert(
        onDismissRequest = onDismiss,
        icon = {
            AppIcon(
                imageVector = when (action) {
                    WorldPublicationAction.Publish -> AppIcons.Publish
                    WorldPublicationAction.Unpublish -> AppIcons.VisibilityOff
                },
                contentDescription = null,
            )
        },
        title = { AppText(title) },
        text = { AppText(message) },
        confirmButton = {
            AppButton(onClick = onConfirm, enabled = enabled, style = AppButtonStyle.Plain) {
                AppText(strings.confirm)
            }
        },
        dismissButton = {
            AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
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
    AppAlert(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            AppIcon(
                imageVector = AppIcons.Delete,
                contentDescription = null,
                tint = AppTheme.colors.destructive,
            )
        },
        title = { AppText(strings.worldDeleteConfirmationTitle) },
        text = {
            AppText(strings.worldDeleteConfirmationMessage.replace("%name%", worldName))
        },
        confirmButton = {
            AppButton(
                enabled = enabled,
                onClick = onConfirm,
                style = AppButtonStyle.Prominent,
                role = AppButtonRole.Destructive,
            ) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDeleting) {
                        AppActivityIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = LocalContentColor.current,
                        )
                    } else {
                        AppIcon(
                            imageVector = AppIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                AppText(strings.worldDeleteAction)
            }
        },
        dismissButton = {
            AppButton(
                enabled = !isDeleting,
                onClick = onDismiss,
                style = AppButtonStyle.Plain,
            ) {
                AppText(strings.cancel)
            }
        },
    )
}
