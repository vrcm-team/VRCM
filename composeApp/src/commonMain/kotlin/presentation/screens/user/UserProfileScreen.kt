package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.getAppPlatform
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.FriendRequestStatus.*
import io.github.vrcmteam.vrcm.network.api.files.resolveOriginalImageUrl
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.compoments.isHiddenWorld
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GalleryScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.RecentWorldsScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.components.FavoriteGroupBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.LanguageIcons
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.service.BoopResult
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.koin.core.parameter.parametersOf

internal class OneShotScrollRestorer(savedPosition: Int) {
    private var pendingPosition = savedPosition.takeIf { it > 0 }

    fun consume(maxValue: Int): Int? {
        val position = pendingPosition ?: return null
        if (maxValue == Int.MAX_VALUE || maxValue < position) return null
        pendingPosition = null
        return position
    }
}

internal class OneShotEntranceAnimationGate {
    private var pending = true

    fun consume(): Boolean = pending.also { pending = false }
}

@Serializable
data class UserProfileScreen(
    private val userProfileVO: UserProfileVo,
    private val sharedSuffixKey: String = "",
) : AppDetailRoute {
    @Transient
    private val groupEntranceAnimationGate = OneShotEntranceAnimationGate()

    // Keep dialog/shared-element state distinct for profiles with different IDs.
    override val key = "UserProfileScreen:${userProfileVO.id}"

    @OptIn(ExperimentalMaterial3Api::class)
    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val userProfileScreenModel: UserProfileScreenModel = koinViewModel { parametersOf(userProfileVO) }
        val animateGroupEntrance = remember { groupEntranceAnimationGate.consume() }

        LaunchedEffect(userProfileVO.id) {
            userProfileScreenModel.setPlayerChatboxModerationTarget(userProfileVO.id)
            userProfileScreenModel.refreshUser(userProfileVO.id)
        }

        LaunchedEffect(Unit) {
            SharedFlowCentre.logout.collect {
                currentNavigator replaceAll AuthAnimeScreen(false)
            }
        }

        val currentUser = userProfileScreenModel.userState
        val userGroups = userProfileScreenModel.userGroups
        val mutualGroups = userProfileScreenModel.mutualGroups
        val playerChatboxModerationState by userProfileScreenModel.playerChatboxModerationState.collectAsState()
        var bottomSheetIsVisible by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        var openAlertDialog by remember { mutableStateOf(false) }
        var openEditProfileDialog by remember { mutableStateOf(false) }
        var openEditNoteDialog by remember { mutableStateOf(false) }
        var openBoopDialog by remember { mutableStateOf(false) }
        var boopSending by remember { mutableStateOf(false) }
        val actionScope = rememberCoroutineScope()
        // Control showing favorite group management for Friend type
        var showFriendFavoriteSheet by remember { mutableStateOf(false) }

        // 保存/恢复滚动位置
        val outerScrollState = rememberScrollState()
        val innerScrollState = rememberScrollState()
        val outerScrollRestorer = remember {
            OneShotScrollRestorer(userProfileScreenModel.savedOuterScrollPosition)
        }
        val innerScrollRestorer = remember {
            OneShotScrollRestorer(userProfileScreenModel.savedInnerScrollPosition)
        }
        DisposableEffect(Unit) {
            onDispose {
                userProfileScreenModel.savedOuterScrollPosition = outerScrollState.value
                userProfileScreenModel.savedInnerScrollPosition = innerScrollState.value
            }
        }
        LaunchedEffect(outerScrollState.maxValue) {
            outerScrollRestorer.consume(outerScrollState.maxValue)?.let {
                outerScrollState.scrollTo(it)
            }
        }
        LaunchedEffect(innerScrollState.maxValue) {
            innerScrollRestorer.consume(innerScrollState.maxValue)?.let {
                innerScrollState.scrollTo(it)
            }
        }

        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey) {
            ProfileScaffold(
                imageModifier = Modifier.sharedBoundsBy("${userProfileVO.id}UserIcon"),
                profileImageUrl = currentUser.profileImageUrl,
                iconUrl = currentUser.iconUrl,
                onReturn = { currentNavigator.pop() },
                onMenu = { bottomSheetIsVisible = true },
                outerScrollState = outerScrollState,
                innerScrollState = innerScrollState,
                topBarActions = { colors ->
                    OfficialUrlShareButton(
                        url = "https://vrchat.com/home/user/${currentUser.id}",
                        colors = colors,
                    )
                },
            ) { _, contentMinHeight ->
                ProfileContent(
                    currentUser = currentUser,
                    sharedUserId = userProfileVO.id,
                    friendLocation = userProfileScreenModel.friendLocation,
                    userGroups = userGroups,
                    mutualGroups = mutualGroups,
                    createdWorlds = userProfileScreenModel.createdWorlds,
                    createdAvatars = userProfileScreenModel.createdAvatars,
                    favoritedWorlds = userProfileScreenModel.favoritedWorlds,
                    friendActivitySummary = userProfileScreenModel.friendActivitySummary,
                    friendActivityEvents = userProfileScreenModel.friendActivityEvents,
                    contentMinHeight = contentMinHeight,
                    animateGroupEntrance = animateGroupEntrance,
                    onLoadWorlds = { userProfileScreenModel.loadCreatedWorlds(userProfileVO.id) },
                    onLoadAvatars = { userProfileScreenModel.loadCreatedAvatars() },
                    onLoadFavoritedWorlds = { userProfileScreenModel.loadFavoritedWorlds(userProfileVO.id) },
                )
            }
        }
        ABottomSheet(
            isVisible = bottomSheetIsVisible,
            sheetState = sheetState,
            onDismissRequest = { bottomSheetIsVisible = false }
        ) {
            SheetItems(
                currentUser = currentUser,
                userProfileScreenModel = userProfileScreenModel,
                hideSheet = { sheetState.hide() },
                onHideCompletion = {
                    if (!sheetState.isVisible) bottomSheetIsVisible = false
                },
                openAlertDialog = { openAlertDialog = true },
                openEditProfileDialog = { openEditProfileDialog = true },
                onManageFriendFavorite = { showFriendFavoriteSheet = true },
                openEditNoteDialog = { openEditNoteDialog = true },
                boopEnabled = userProfileScreenModel.isBoopAllowed,
                openBoopDialog = { openBoopDialog = true },
                playerChatboxModerationState = playerChatboxModerationState,
            )
        }
        // Friend FavoriteType group management bottom sheet
        FavoriteGroupBottomSheet(
            isVisible = showFriendFavoriteSheet,
            favoriteId = currentUser.id,
            favoriteType = FavoriteType.Friend,
            onDismiss = { showFriendFavoriteSheet = false }
        )
        JsonAlertDialog(
            openAlertDialog = openAlertDialog,
            onDismissRequest = { openAlertDialog = false }
        ) {
            Text(text = userProfileScreenModel.userJson)
        }
        // 编辑资料底部弹窗
        val editSuccessMsg = strings.editProfileUpdateSuccess
        val bioLinksUpdateState by userProfileScreenModel.bioLinksUpdateState.collectAsState()
        EditProfileSheet(
            isVisible = openEditProfileDialog,
            currentUser = currentUser,
            bioLinksUpdateState = bioLinksUpdateState,
            onDismiss = { openEditProfileDialog = false },
            onStatusSave = { status, statusDescription ->
                userProfileScreenModel.updateUserProfile(status = status, statusDescription = statusDescription, successMessage = editSuccessMsg)
            },
            onLanguageSave = { languages ->
                userProfileScreenModel.updateUserProfile(languages = languages, successMessage = editSuccessMsg)
            },
            onPronounsSave = { pronouns ->
                userProfileScreenModel.updateUserProfile(pronouns = pronouns, successMessage = editSuccessMsg)
            },
            onBioSave = { bio ->
                userProfileScreenModel.updateUserProfile(bio = bio, successMessage = editSuccessMsg)
            },
            onBioLinksSave = { bioLinks ->
                userProfileScreenModel.updateBioLinks(
                    bioLinks = bioLinks,
                    successMessage = editSuccessMsg,
                )
            },
        )
        // 编辑备注弹窗
        val noteSavedMsg = strings.userNoteSaved
        EditNoteDialog(
            isVisible = openEditNoteDialog,
            initialNote = currentUser.note,
            onDismiss = { openEditNoteDialog = false },
            onSave = { note ->
                userProfileScreenModel.saveUserNote(note, noteSavedMsg)
                openEditNoteDialog = false
            }
        )
        val boopSuccessMessage = strings.profileBoopSuccess
        val boopCooldownMessage = strings.profileBoopAlreadySent
        val boopDisabledMessage = strings.profileBoopDisabled
        BoopSelectorDialog(
            visible = openBoopDialog,
            targetName = currentUser.displayName,
            sending = boopSending,
            onDismiss = { openBoopDialog = false },
            onSend = { emojiId ->
                if (!boopSending) {
                    actionScope.launch {
                        boopSending = true
                        val result = userProfileScreenModel.boop(
                            userId = currentUser.id,
                            emojiId = emojiId,
                            successMessage = boopSuccessMessage,
                            cooldownMessage = boopCooldownMessage,
                            disabledMessage = boopDisabledMessage,
                        )
                        boopSending = false
                        if (result == BoopResult.Sent) openBoopDialog = false
                    }
                }
            },
        )
    }

}

@Composable
private fun ColumnScope.SheetItems(
    currentUser: UserProfileVo,
    userProfileScreenModel: UserProfileScreenModel,
    hideSheet: suspend () -> Unit,
    onHideCompletion: () -> Unit,
    openAlertDialog: () -> Unit,
    openEditProfileDialog: () -> Unit,
    onManageFriendFavorite: () -> Unit,
    openEditNoteDialog: () -> Unit,
    boopEnabled: Boolean,
    openBoopDialog: () -> Unit,
    playerChatboxModerationState: PlayerChatboxModerationState,
) {
    val navigator = LocalNavigator.currentOrThrow
    val localeStrings = strings
    val scope = rememberCoroutineScope()
    // 只有当是自己的个人资料时才显示
    if (currentUser.isSelf) {

        SheetButtonItem(text = localeStrings.profileEditProfile, onClick = {
            scope.launch { hideSheet() }.invokeOnCompletion {
                onHideCompletion()
                openEditProfileDialog()
            }
        })

        SheetButtonItem(text = localeStrings.profileViewGallery, onClick = {
            scope.launch { hideSheet() }.invokeOnCompletion {
                onHideCompletion()
                navigator.push(GalleryScreen)
            }
        })

        SheetButtonItem(text = localeStrings.recentWorldsTitle, onClick = {
            scope.launch { hideSheet() }.invokeOnCompletion {
                onHideCompletion()
                navigator.push(RecentWorldsScreen)
            }
        })

    }

    // 管理好友收藏分组，仅当不是自己且是好友时显示
    if (!currentUser.isSelf) {
        SheetButtonItem(text = localeStrings.selectFavoriteGroup, onClick = {
            scope.launch { hideSheet() }.invokeOnCompletion {
                onHideCompletion()
                onManageFriendFavorite()
            }
        })
        SheetButtonItem(text = localeStrings.userNoteEditTitle, onClick = {
            scope.launch { hideSheet() }.invokeOnCompletion {
                onHideCompletion()
                openEditNoteDialog()
            }
        })

        if (currentUser.isFriend) {
            SheetButtonItem(text = localeStrings.profileBoop, enabled = boopEnabled, onClick = {
                scope.launch { hideSheet() }.invokeOnCompletion {
                    onHideCompletion()
                    openBoopDialog()
                }
            })
            SheetButtonItem(text = localeStrings.profileInviteToMyInstance, onClick = {
                scope.launch { hideSheet() }.invokeOnCompletion {
                    onHideCompletion()
                    userProfileScreenModel.inviteToMyInstance(
                        userId = currentUser.id,
                        successMessage = localeStrings.profileInviteSent,
                        notInInstanceMessage = localeStrings.profileInviteNotInInstance,
                    )
                }
            })
        }

        PlayerChatboxModerationSheetItem(
            state = playerChatboxModerationState,
            screenModel = userProfileScreenModel,
        )
    }

    SheetButtonItem(
        text = if (currentUser.isSelf) localeStrings.profileViewFriendNetwork else localeStrings.profileViewMutualFriends,
        onClick = {
            scope.launch { hideSheet() }.invokeOnCompletion {
                onHideCompletion()
                if (currentUser.isSelf) {
                    navigator.push(FriendNetworkScreen)
                } else {
                    navigator.push(MutualFriendsScreen(currentUser.id, currentUser.displayName))
                }
            }
        }
    )

    FriendRequestSheetItem(
        currentUser,
        userProfileScreenModel,
        hideSheet,
        onHideCompletion,
    )
    SheetButtonItem(localeStrings.profileViewJsonData, onClick = {
        scope.launch { hideSheet() }.invokeOnCompletion {
            onHideCompletion()
            openAlertDialog()
        }
    })

}

@Composable
private fun ColumnScope.PlayerChatboxModerationSheetItem(
    state: PlayerChatboxModerationState,
    screenModel: UserProfileScreenModel,
) {
    val localeStrings = strings
    val text = when (state) {
        PlayerChatboxModerationState.Unavailable -> return
        PlayerChatboxModerationState.Checking -> localeStrings.profileChatboxModerationChecking
        is PlayerChatboxModerationState.Failed -> localeStrings.profileChatboxModerationRetry
        is PlayerChatboxModerationState.Ready -> if (state.isMuted) {
            localeStrings.profileChatboxModerationUnmute
        } else {
            localeStrings.profileChatboxModerationMute
        }
        is PlayerChatboxModerationState.Updating -> if (state.willMute) {
            localeStrings.profileChatboxModerationMuting
        } else {
            localeStrings.profileChatboxModerationUnmuting
        }
    }
    val enabled = state is PlayerChatboxModerationState.Ready ||
        state is PlayerChatboxModerationState.Failed

    SheetButtonItem(
        text = text,
        enabled = enabled,
        onClick = {
            when (state) {
                is PlayerChatboxModerationState.Failed ->
                    screenModel.retryPlayerChatboxModeration()
                is PlayerChatboxModerationState.Ready ->
                    screenModel.togglePlayerChatboxModeration(
                        mutedMessage = localeStrings.profileChatboxModerationMuted,
                        unmutedMessage = localeStrings.profileChatboxModerationUnmuted,
                        failureMessage = localeStrings.profileChatboxModerationUpdateFailed,
                    )
                else -> Unit
            }
        },
    )
}

@Composable
private fun ColumnScope.FriendRequestSheetItem(
    currentUser: UserProfileVo,
    userProfileScreenModel: UserProfileScreenModel,
    hideSheet: suspend () -> Unit,
    onHideCompletion: () -> Unit,
) {
    val localeStrings = strings
    val action: Pair<String, suspend () -> Boolean>? = when {
        // 当前用户不是朋友且不是自己
        !currentUser.isFriend && !currentUser.isSelf -> {
            when (currentUser.friendRequestStatus) {
                // 状态为Null,则发送好友请求
                Null -> localeStrings.profileSendFriendRequest to {
                    userProfileScreenModel.sendFriendRequest(currentUser.id, localeStrings.profileFriendRequestSent)
                }
                // 状态为Outgoing,则取消发送好友请求
                Outgoing -> localeStrings.profileDeleteFriendRequest to {
                    userProfileScreenModel.deleteFriendRequest(
                        currentUser.id,
                        localeStrings.profileFriendRequestDeleted
                    )
                }

                // 状态为Incoming,则接受好友请求
                Incoming -> localeStrings.profileAcceptFriendRequest to {
                    userProfileScreenModel.acceptFriendRequest(
                        currentUser.id,
                        localeStrings.profileFriendRequestAccepted
                    )
                }

                else -> null
            }
        }
        // 状态为Completed,则删除好友
        // TODO: 加一个弹窗提示是否删除好友
        currentUser.isFriend && currentUser.friendRequestStatus == Completed ->
            localeStrings.profileUnfriend to {
                userProfileScreenModel.unfriend(currentUser.id, localeStrings.profileUnfriended)
            }

        else -> null
    }

    if (action == null) return

    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    SheetButtonItem(action.first, onClick = {
        scope.launch { hideSheet() }.invokeOnCompletion {
            scope.launch {
                enabled = false
                when (action.second()) {
                    true -> hideSheet()
                    false -> enabled = true
                }
            }.invokeOnCompletion {
                onHideCompletion()
            }
        }
    })
}

@Composable
private fun ColumnScope.SheetButtonItem(
    text: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.(String) -> Unit = { Text(text = it) },
) {
    TextButton(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 24.dp),
        enabled = enabled,
        onClick = onClick
    ) {
        content(text.orEmpty())
    }
}

@Composable
private fun JsonAlertDialog(
    openAlertDialog: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (openAlertDialog) {
        AlertDialog(
            icon = {
                Icon(AppIcons.Person, contentDescription = "AlertDialogIcon")
            },
            text = {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        content()
                    }
                }
            },
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = onDismissRequest
                ) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.ProfileContent(
    currentUser: UserProfileVo?,
    sharedUserId: String,
    friendLocation: FriendLocation?,
    userGroups: List<LimitedUserGroup>,
    mutualGroups: List<LimitedUserGroup>,
    createdWorlds: List<WorldData>,
    createdAvatars: List<AvatarData>,
    favoritedWorlds: List<Pair<String, List<FavoritedWorld>>>,
    friendActivitySummary: io.github.vrcmteam.vrcm.service.FriendActivitySummary?,
    friendActivityEvents: List<io.github.vrcmteam.vrcm.service.FriendActivityEvent>,
    contentMinHeight: Dp,
    animateGroupEntrance: Boolean,
    onLoadWorlds: () -> Unit,
    onLoadAvatars: () -> Unit,
    onLoadFavoritedWorlds: () -> Unit,
) {
    if (currentUser == null) return
    val sharedSuffixKey = LocalSharedSuffixKey.current
    val navigator = currentNavigator
    val scope = rememberCoroutineScope()
    val localeStrings = strings

    // 加载创建的世界和模型
    LaunchedEffect(currentUser.id, currentUser.isSelf) {
        onLoadWorlds()
        onLoadFavoritedWorlds()
        if (currentUser.isSelf) {
            onLoadAvatars()
        }
    }

    UserProfileIdentity(
        userProfileVO = currentUser,
        sharedUserId = sharedUserId,
    )

    var isSelected by remember { mutableStateOf(false) }
    // LocationCard: show the room of this user and friends in the same room
    friendLocation?.let { loc ->
        val navigator = currentNavigator
        val locationSharedSuffixKey = "UER:$sharedSuffixKey"
        // 创建临时的 WorldProfileVo
        val onClickWorldImage = { transitionSuffixKey: String ->
            val homeInstanceVo = friendLocation.instants.value
            val tempWorldProfileVo = WorldProfileVo(homeInstanceVo)
            navigator push WorldProfileScreen(
                worldProfileVO = tempWorldProfileVo,
                location = friendLocation.location,
                sharedSuffixKey = transitionSuffixKey,
                sharedImageCacheKey = homeInstanceVo.worldImageUrl,
            )
        }

        // 防止当前用户的共享元素冲突
        CompositionLocalProvider(LocalSharedSuffixKey provides locationSharedSuffixKey) {
            LocationCard(
                modifier = Modifier.fillMaxWidth(),
                location = loc,
                isSelected = isSelected,
                onClickWorldImage = onClickWorldImage,
                onClickLocationCard = { isSelected = !isSelected },
                travelingIds = loc.travelingIds.value,
                isCurrentUserLocation = currentUser.isSelf,
            ) { friends ->
                UserIconsRow(
                    modifier = Modifier.fillMaxWidth(),
                    instanceId = loc.location,
                    friends = friends,
                    onClickUserIcon = { user, transitionSuffixKey ->
                        navigator replace UserProfileScreen(
                            userProfileVO = UserProfileVo(user),
                            sharedSuffixKey = transitionSuffixKey,
                        )
                    }
                )
            }
        }
    }

    // 个人简介
    val latestBioChange = remember(friendActivityEvents) {
        friendActivityEvents
            .asSequence()
            .filter { it.type == FriendActivityEventType.BioChanged }
            .maxByOrNull { it.occurredAtMillis }
    }
    BottomCardTab(
        bioMinHeight = contentMinHeight,
        userProfileVO = currentUser,
        latestBioChange = latestBioChange,
    )

    if (!currentUser.isSelf && friendActivitySummary != null) {
        FriendActivitySection(
            summary = friendActivitySummary,
            events = friendActivityEvents,
        )
    }

    if (!currentUser.isSelf) {
        UserGroupsSection(
            groups = mutualGroups,
            title = strings.userMutualGroups,
            animateEntrance = animateGroupEntrance,
            onGroupClick = { group ->
                navigator push GroupProfileScreen(
                    groupProfileVo = GroupProfileVo(group),
                    sharedSuffixKey = sharedSuffixKey
                )
            }
        )
    }

    UserGroupsSection(
        groups = userGroups,
        animateEntrance = animateGroupEntrance,
        onGroupClick = { group ->
            navigator push GroupProfileScreen(
                groupProfileVo = GroupProfileVo(group),
                sharedSuffixKey = sharedSuffixKey
            )
        }
    )

    // 创建的世界（在个人简介下方）
    UserCreatedWorldsSection(
        worlds = createdWorlds,
        sharedSuffixKey = sharedSuffixKey,
        onWorldClick = { world ->
            if (world.isHiddenWorld()) {
                scope.launch {
                    SharedFlowCentre.toastText.emit(ToastText.Info(localeStrings.hiddenWorldCannotView))
                }
            } else {
                navigator push WorldProfileScreen(
                    worldProfileVO = WorldProfileVo(world).copy(
                        worldImageUrl = resolveOriginalImageUrl(world.imageUrl, world.thumbnailImageUrl)
                    ),
                    sharedSuffixKey = sharedSuffixKey,
                    sharedKeyPrefix = "Created_",
                    sharedImageCacheKey = resolveOriginalImageUrl(
                        world.imageUrl,
                        world.thumbnailImageUrl,
                    ),
                )
            }
        }
    )

    // 创建的模型（仅自己可见，在个人简介下方）
    if (currentUser.isSelf) {
        UserCreatedAvatarsSection(
            avatars = createdAvatars,
            sharedSuffixKey = sharedSuffixKey,
        )
    }

    // 收藏的世界（在创建的模型下方）
    UserFavoritedWorldsSection(
        groupedWorlds = favoritedWorlds,
        sharedSuffixKey = sharedSuffixKey,
        onWorldClick = { world ->
            if (world.id == "???") {
                scope.launch {
                    SharedFlowCentre.toastText.emit(ToastText.Info(localeStrings.hiddenWorldCannotView))
                }
            } else {
                navigator push WorldProfileScreen(
                    worldProfileVO = WorldProfileVo(
                        worldId = world.id,
                        worldName = world.name,
                        worldImageUrl = resolveOriginalImageUrl(world.imageUrl, world.thumbnailImageUrl),
                        thumbnailImageUrl = world.thumbnailImageUrl?.ifBlank { null },
                        worldDescription = world.description.orEmpty(),
                        authorID = world.authorId,
                        authorName = world.authorName,
                        capacity = world.capacity ?: 0,
                        recommendedCapacity = world.recommendedCapacity ?: 0,
                        visits = world.visits ?: 0,
                        favorites = world.favorites ?: 0,
                        heat = world.heat ?: 0,
                        popularity = world.popularity ?: 0,
                        featured = world.featured,
                        tags = world.tags.filter { it.startsWith("author_tag_") }
                            .map { it.substringAfter("author_tag_") },
                        releaseStatus = world.releaseStatus,
                        version = world.version,
                        createdAt = world.createdAt,
                        updatedAt = world.updatedAt,
                        publicationDate = world.publicationDate,
                        labsPublicationDate = world.labsPublicationDate
                    ),
                    sharedSuffixKey = sharedSuffixKey,
                    sharedKeyPrefix = "Fav_",
                    sharedImageCacheKey = resolveOriginalImageUrl(
                        world.imageUrl,
                        world.thumbnailImageUrl,
                    ),
                )
            }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UserGroupsSection(
    groups: List<LimitedUserGroup>,
    title: String = strings.groups,
    animateEntrance: Boolean = true,
    onGroupClick: (LimitedUserGroup) -> Unit,
) {
    if (groups.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(title = title)
        val shownGroups = rememberStaggeredReveal(
            items = groups.distinctBy(LimitedUserGroup::groupId),
            animateEntrance = animateEntrance,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(shownGroups, key = { _, group -> group.groupId }) { index, group ->
                Surface(
                    modifier = Modifier
                        .animateItem(fadeInSpec = entranceFadeSpec(index))
                        .width(180.dp)
                        .height(88.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onGroupClick(group) },
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GroupIcon(
                                iconUrl = group.iconUrl,
                                size = 36.dp,
                                modifier = Modifier.sharedBoundsBy("${group.groupId}GroupIcon")
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    modifier = Modifier.sharedBoundsBy(
                                        key = groupNameSharedKey(group.groupId),
                                        resizeMode = SharedTextBoundsResizeMode,
                                    ),
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (group.shortCode.isNotBlank()) {
                                    Text(
                                        text = "#${group.shortCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${group.memberCount} ${strings.groupMembers}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 区域标题（标题 + 可选计数）
 */
@Composable
private fun SectionHeader(
    title: String,
    countText: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (countText != null) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 堆叠卡片列表：多卡片重叠效果，点击跳转新页面展开
 * @param detailTitle 新页面标题
 * @param onNavigateToDetail 点击堆叠卡片时跳转到详情页
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun <T> StackedLocationCardList(
    items: List<T>,
    key: (T) -> Any,
    imageUrl: (T) -> String?,
    title: (T) -> String,
    subtitle: (T) -> String,
    detailTitle: String,
    label: String? = null,
    imageModifier: @Composable (T, Modifier) -> Modifier = { _, m -> m },
    onClickItem: ((T) -> Unit)? = null,
    onNavigateToDetail: (List<T>) -> Unit,
) {
    if (items.isEmpty()) return
    val firstItem = items.first()

    // 非 lazy 作用域无法使用 animateItem，用等效的纯淡入入场
    val entranceAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceAlpha.animateTo(1f, entranceFadeSpec())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(MaterialTheme.shapes.large)
            .graphicsLayer { alpha = entranceAlpha.value }
            .clickable {
                if (items.size == 1) {
                    onClickItem?.invoke(firstItem)
                } else {
                    onNavigateToDetail(items)
                }
            }
    ) {
        // 后方堆叠卡片（与 StackedCards 一致的堆叠方式）
        val visibleCount = minOf(3, items.size)
        for (i in visibleCount - 1 downTo 1) {
            val baseOffset = 10.dp * i
            val baseScale = 1f - (0.1f * i)
            val baseAlpha = 1f - (0.25f * i)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = -baseOffset.toPx()
                        scaleX = baseScale
                        scaleY = baseScale
                        alpha = baseAlpha
                    },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {}
        }

        // 前方主卡片
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .align(Alignment.BottomCenter),
            tonalElevation = (-2).dp,
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AImage(
                    modifier = Modifier
                        .sharedBoundsBy("${detailTitle}_${key(firstItem)}_StackedImage")
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp, topEnd = 8.dp,
                                bottomStart = 16.dp, bottomEnd = 8.dp
                            )
                        )
                        .let { modifier ->
                            if (shouldShareStackedCardWithItemDetail(items.size)) {
                                imageModifier(firstItem, modifier)
                            } else {
                                modifier
                            }
                        },
                    imageData = imageUrl(firstItem),
                    contentDescription = null
                )
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title(firstItem),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = subtitle(firstItem),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 标签气泡（左上角）
        if (label != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
            }
        }

        // 剩余数量指示器（底部右侧，与 StackedCards 一致）
        if (items.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+${items.size - 1}",
                    color = MaterialTheme.colorScheme.onTertiary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * 详情页顶部栏：标题居中，配色与用户界面下拉状态一致
 */
@Composable
private fun DetailTopBar(
    title: String,
    sysTopPadding: Dp,
    onReturn: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = sysTopPadding)
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onReturn) {
                Icon(
                    imageVector = AppIcons.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            // 占位，保持标题居中
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * 卡片列表项数据（可序列化，用于详情页）
 */
@Serializable
data class CardItemVo(
    val id: String,
    val listKey: String = id,
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val title: String,
    val subtitle: String,
    val authorName: String = "",
    val avatarData: AvatarData? = null,
    val worldProfileVO: WorldProfileVo? = null,
)

/**
 * 卡片导航类型
 */
@Serializable
enum class CardScreenType {
    WORLD, AVATAR, FAVORITED_WORLD
}

internal fun worldImageSharedKey(sharedKeyPrefix: String, worldId: String): String? =
    if (worldId == "???") null else "${sharedKeyPrefix}${worldId}WorldImage"

internal fun shouldShareStackedCardWithItemDetail(itemCount: Int): Boolean = itemCount == 1

/**
 * 卡片列表详情页（非泛型，仅携带可序列化数据）
 */
@Serializable
class CardListDetailScreen(
    private val title: String,
    private val items: List<CardItemVo>,
    private val sectionKey: String,
    private val screenType: CardScreenType,
    private val sharedSuffixKey: String = "",
    private val sharedKeyPrefix: String = "",
) : AppDetailRoute {
    @Transient
    private val entranceAnimationGate = OneShotEntranceAnimationGate()

    @Composable
    override fun Content() {
        val navigator = currentNavigator
        val scope = rememberCoroutineScope()
        val animateListEntrance = remember { entranceAnimationGate.consume() }
        val hiddenWorldCannotViewText = strings.hiddenWorldCannotView
        val sysTopPadding = getInsetPadding(WindowInsets::getTop)
        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    DetailTopBar(
                        title = title,
                        sysTopPadding = sysTopPadding,
                        onReturn = { navigator.pop() }
                    )
                    CardListContent(
                        items = items,
                        key = { it.listKey },
                        imageUrl = { it.imageUrl ?: it.thumbnailUrl },
                        itemTitle = { it.title },
                        itemSubtitle = { it.subtitle },
                        sectionKey = sectionKey,
                        animateEntrance = animateListEntrance,
                        imageModifier = when (screenType) {
                            CardScreenType.WORLD, CardScreenType.FAVORITED_WORLD -> { item, modifier ->
                                worldImageSharedKey(sharedKeyPrefix, item.id)
                                    ?.let { modifier.sharedBoundsBy(it) }
                                    ?: modifier
                            }
                            CardScreenType.AVATAR -> { item, modifier ->
                                modifier.sharedBoundsBy("${item.id}AvatarImage")
                            }
                        },
                        onClickItem = { item ->
                            when (screenType) {
                                CardScreenType.WORLD, CardScreenType.FAVORITED_WORLD -> {
                                    if (item.id == "???") {
                                        scope.launch {
                                            SharedFlowCentre.toastText.emit(ToastText.Info(hiddenWorldCannotViewText))
                                        }
                                    } else {
                                        navigator push WorldProfileScreen(
                                            worldProfileVO = item.worldProfileVO ?: WorldProfileVo(
                                                worldId = item.id,
                                                worldName = item.title,
                                                worldImageUrl = item.imageUrl,
                                                thumbnailImageUrl = item.thumbnailUrl,
                                                authorName = item.authorName
                                            ),
                                            sharedSuffixKey = sharedSuffixKey,
                                            sharedKeyPrefix = sharedKeyPrefix,
                                            sharedImageCacheKey = item.imageUrl ?: item.thumbnailUrl,
                                        )
                                    }
                                }
                                CardScreenType.AVATAR -> {
                                    item.avatarData?.let { avatar ->
                                        navigator push AvatarProfileScreen(
                                            avatarProfileVo = AvatarProfileVo(avatar),
                                            sharedSuffixKey = sharedSuffixKey,
                                            sharedImageCacheKey = item.imageUrl ?: item.thumbnailUrl,
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 卡片列表详情页通用内容
 */
@Composable
private fun <T> CardListContent(
    items: List<T>,
    key: (T) -> Any,
    imageUrl: (T) -> String?,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String,
    sectionKey: String = "",
    animateEntrance: Boolean = true,
    imageModifier: @Composable (T, Modifier) -> Modifier = { _, m -> m },
    onClickItem: ((T) -> Unit)? = null,
) {
    val shownItems = rememberStaggeredReveal(items, animateEntrance)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 8.dp, bottom = getInsetPadding(12, WindowInsets::getBottom) + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(shownItems, key = { _, item -> key(item) }) { index, item ->
            Surface(
                modifier = Modifier
                    .animateItem(fadeInSpec = entranceFadeSpec(index))
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(MaterialTheme.shapes.large)
                    .then(if (onClickItem != null) Modifier.clickable { onClickItem(item) } else Modifier),
                tonalElevation = (-2).dp,
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AImage(
                        modifier = imageModifier(
                            item,
                            Modifier
                                .sharedBoundsBy("${sectionKey}_${key(item)}_StackedImage")
                                .weight(0.4f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 8.dp,
                                        bottomStart = 16.dp, bottomEnd = 8.dp
                                    )
                                )
                        ),
                        imageData = imageUrl(item),
                        contentDescription = null
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = itemTitle(item),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = itemSubtitle(item),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 用户创建的世界列表组件
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UserCreatedWorldsSection(
    worlds: List<WorldData>,
    sharedSuffixKey: String = "",
    onWorldClick: (WorldData) -> Unit,
) {
    if (worlds.isEmpty()) return
    val navigator = currentNavigator
    val createdWorldsTitle = strings.userCreatedWorlds
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(title = createdWorldsTitle)
        StackedLocationCardList(
            items = worlds,
            key = { it.id },
            imageUrl = { resolveOriginalImageUrl(it.imageUrl, it.thumbnailImageUrl) },
            title = { it.name },
            subtitle = { it.description ?: "" },
            detailTitle = createdWorldsTitle,
            imageModifier = { item, modifier -> modifier.sharedBoundsBy("Created_${item.id}WorldImage") },
            onClickItem = onWorldClick,
            onNavigateToDetail = { list ->
                navigator push CardListDetailScreen(
                    title = createdWorldsTitle,
                    items = list.map { world ->
                        CardItemVo(
                            id = world.id,
                            imageUrl = resolveOriginalImageUrl(world.imageUrl, world.thumbnailImageUrl),
                            thumbnailUrl = null,
                            title = world.name,
                            subtitle = world.description ?: "",
                            authorName = world.authorName,
                            worldProfileVO = WorldProfileVo(world).copy(
                                worldImageUrl = resolveOriginalImageUrl(world.imageUrl, world.thumbnailImageUrl)
                            )
                        )
                    },
                    sectionKey = createdWorldsTitle,
                    screenType = CardScreenType.WORLD,
                    sharedSuffixKey = sharedSuffixKey,
                    sharedKeyPrefix = "Created_"
                )
            }
        )
    }
}

/**
 * 用户创建的模型列表组件
 */
@Composable
private fun UserCreatedAvatarsSection(
    avatars: List<AvatarData>,
    sharedSuffixKey: String = "",
) {
    if (avatars.isEmpty()) return
    val navigator = currentNavigator
    val createdAvatarsTitle = strings.userCreatedAvatars
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(title = createdAvatarsTitle)
        StackedLocationCardList(
            items = avatars,
            key = { it.id },
            imageUrl = { resolveOriginalImageUrl(it.imageUrl, it.thumbnailImageUrl) },
            title = { it.name },
            subtitle = { it.description ?: it.authorName },
            detailTitle = createdAvatarsTitle,
            imageModifier = { item, modifier -> modifier.sharedBoundsBy("${item.id}AvatarImage") },
            onClickItem = { avatar ->
                navigator push AvatarProfileScreen(
                    avatarProfileVo = AvatarProfileVo(
                        avatar.copy(
                            imageUrl = resolveOriginalImageUrl(
                                avatar.imageUrl,
                                avatar.thumbnailImageUrl,
                            ).orEmpty()
                        )
                    ),
                    sharedSuffixKey = sharedSuffixKey,
                    sharedImageCacheKey = resolveOriginalImageUrl(
                        avatar.imageUrl,
                        avatar.thumbnailImageUrl,
                    ),
                )
            },
            onNavigateToDetail = { list ->
                navigator push CardListDetailScreen(
                    title = createdAvatarsTitle,
                    items = list.map { CardItemVo(
                        id = it.id,
                        imageUrl = resolveOriginalImageUrl(it.imageUrl, it.thumbnailImageUrl),
                        thumbnailUrl = null,
                        title = it.name,
                        subtitle = it.description?.takeIf { d -> d.isNotBlank() } ?: it.authorName,
                        authorName = it.authorName,
                        avatarData = it.copy(
                            imageUrl = resolveOriginalImageUrl(it.imageUrl, it.thumbnailImageUrl).orEmpty()
                        )
                    ) },
                    sectionKey = createdAvatarsTitle,
                    screenType = CardScreenType.AVATAR,
                    sharedSuffixKey = sharedSuffixKey,
                )
            }
        )
    }
}

/**
 * 用户收藏的世界列表组件（按分组显示）
 */
@Composable
private fun UserFavoritedWorldsSection(
    groupedWorlds: List<Pair<String, List<FavoritedWorld>>>,
    sharedSuffixKey: String = "",
    onWorldClick: (FavoritedWorld) -> Unit,
) {
    if (groupedWorlds.isEmpty()) return
    val navigator = currentNavigator
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(title = strings.userFavoritedWorlds)
        for ((groupName, worlds) in groupedWorlds) {
            if (worlds.isEmpty()) continue
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StackedLocationCardList(
                    items = worlds,
                    key = { it.favoriteId },
                    imageUrl = { resolveOriginalImageUrl(it.imageUrl, it.thumbnailImageUrl) },
                    title = { if (it.id == "???") it.favoriteId ?: it.name else it.name },
                    subtitle = { it.description?.takeIf { d -> d.isNotBlank() } ?: "${it.occupants ?: 0} 👤" },
                    detailTitle = groupName,
                    label = groupName,
                    imageModifier = { item, modifier ->
                        worldImageSharedKey("Fav_", item.id)
                            ?.let { modifier.sharedBoundsBy(it) }
                            ?: modifier
                    },
                    onClickItem = onWorldClick,
                    onNavigateToDetail = { list ->
                        navigator push CardListDetailScreen(
                            title = groupName,
                            items = list.map { world ->
                                CardItemVo(
                                    id = world.id,
                                    listKey = world.favoriteId,
                                    imageUrl = resolveOriginalImageUrl(world.imageUrl, world.thumbnailImageUrl),
                                    thumbnailUrl = null,
                                    title = if (world.id == "???") world.favoriteId ?: world.name else world.name,
                                    subtitle = world.description?.takeIf { d -> d.isNotBlank() } ?: "${world.occupants ?: 0} 👤",
                                    authorName = world.authorName ?: "",
                                    worldProfileVO = WorldProfileVo(
                                        worldId = world.id,
                                        worldName = world.name,
                                        worldImageUrl = resolveOriginalImageUrl(
                                            world.imageUrl,
                                            world.thumbnailImageUrl,
                                        ),
                                        thumbnailImageUrl = world.thumbnailImageUrl?.ifBlank { null },
                                        worldDescription = world.description.orEmpty(),
                                        authorID = world.authorId,
                                        authorName = world.authorName,
                                        capacity = world.capacity ?: 0,
                                        recommendedCapacity = world.recommendedCapacity ?: 0,
                                        visits = world.visits ?: 0,
                                        favorites = world.favorites ?: 0,
                                        heat = world.heat ?: 0,
                                        popularity = world.popularity ?: 0,
                                        featured = world.featured,
                                        tags = world.tags.filter { it.startsWith("author_tag_") }
                                            .map { it.substringAfter("author_tag_") },
                                        releaseStatus = world.releaseStatus,
                                        version = world.version,
                                        createdAt = world.createdAt,
                                        updatedAt = world.updatedAt,
                                        publicationDate = world.publicationDate,
                                        labsPublicationDate = world.labsPublicationDate
                                    )
                                )
                            },
                            sectionKey = groupName,
                            screenType = CardScreenType.FAVORITED_WORLD,
                            sharedSuffixKey = sharedSuffixKey,
                            sharedKeyPrefix = "Fav_"
                        )
                    }
                )
            }
        }
    }
}


@Composable
private fun UserProfileIdentity(
    userProfileVO: UserProfileVo,
    sharedUserId: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UserInfoRow(
            user = userProfileVO,
            canCopy = true,
            sharedUserId = sharedUserId,
            pronouns = userProfileVO.pronouns,
        )
        UserStatusRow(
            canCopy = true,
            user = userProfileVO,
            sharedUserId = sharedUserId,
        )
        LangAndLinkRow(userProfileVO)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BottomCardTab(
    bioMinHeight: Dp = 0.dp,
    userProfileVO: UserProfileVo,
    latestBioChange: FriendActivityEvent? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var state by remember { mutableStateOf(0) }
        var showBioChange by remember(userProfileVO.id, latestBioChange?.id) { mutableStateOf(false) }
        AnimatedContent(targetState = state) {
            when (it) {
                0 -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = bioMinHeight),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // TODO: UI 需要重写后恢复“查看最近更改”入口，相关逻辑暂时保留。
                                /*
                                if (latestBioChange != null) {
                                    TextButton(
                                        modifier = Modifier.align(Alignment.End),
                                        onClick = { showBioChange = !showBioChange },
                                    ) {
                                        Text(
                                            if (showBioChange) strings.friendActivityBioDiffHide
                                            else strings.friendActivityBioDiffShow,
                                        )
                                    }
                                }
                                */
                                SelectionContainer {
                                    if (showBioChange && latestBioChange != null) {
                                        val lines = remember(latestBioChange.id, latestBioChange.previousValue, latestBioChange.currentValue) {
                                            friendActivityBioDiff(
                                                previous = latestBioChange.previousValue,
                                                current = latestBioChange.currentValue,
                                                includeUnchanged = true,
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            lines.forEach { line ->
                                                Text(
                                                    text = when {
                                                        line.unchanged -> "  ${line.text}"
                                                        line.added -> "+ ${line.text}"
                                                        else -> "- ${line.text}"
                                                    },
                                                    color = when {
                                                        line.unchanged -> MaterialTheme.colorScheme.primary
                                                        line.added -> MaterialTheme.colorScheme.tertiary
                                                        else -> MaterialTheme.colorScheme.error
                                                    },
                                                )
                                            }
                                        }
                                    } else {
                                        Text(text = userProfileVO.bio)
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                else -> {
                    // TODO: 未来实现 Worlds/Groups 标签页
                }
            }
        }
    }
}

@Composable
private fun LangAndLinkRow(userProfileVO: UserProfileVo) {
    val speakLanguages = userProfileVO.speakLanguages
    val bioLinks = userProfileVO.bioLinks
    val width = 32.dp
    if (speakLanguages.isEmpty() && bioLinks.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (speakLanguages.isNotEmpty()) {
            LanguagesRow(speakLanguages, width)
        }
        if (speakLanguages.isNotEmpty() && bioLinks.isNotEmpty()) {
            VerticalDivider(
                modifier = Modifier.height(width).padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
        }
        if (bioLinks.isNotEmpty()) {
            LinksRow(bioLinks, width)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguagesRow(
    speakLanguages: List<String>,
    width: Dp = 32.dp,
) {
    if (speakLanguages.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.height(width),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        speakLanguages.forEach { language ->
            val imageVector = LanguageIcons.getFlag(language)
            ATooltipBox(
                tooltip = { Text(text = language) }
            ) {
                if (imageVector == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(width)
                            .padding(vertical = 3.dp)
                            .background(MaterialTheme.colorScheme.inversePrimary, MaterialTheme.shapes.extraSmall)
                    ) {
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
                            imageVector = AppIcons.QuestionMark,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            contentDescription = "NotKnownLanguageIcon",
                        )
                    }
                } else {
                    Image(
                        imageVector = imageVector,
                        contentDescription = "LanguageIcon",
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.CenterVertically)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .width(width),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksRow(
    bioLinks: List<String>,
    width: Dp = 32.dp,
) {
    if (bioLinks.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.height(width),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val appPlatform = getAppPlatform()
        bioLinks.forEach { link ->
            val webIconVector = WebIcons.selectIcon(link)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(text = link)
                    }
                },
                state = rememberTooltipState()
            ) {
                FilledIconButton(
                    modifier = Modifier.size(width),
                    onClick = { appPlatform.openUrl(link) },
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(6.dp)
                            .enableIf(webIconVector == null) { rotate(-45F) },
                        imageVector = webIconVector ?: AppIcons.Link,
                        contentDescription = "BioLinkIcon"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNoteDialog(
    isVisible: Boolean,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (!isVisible) return
    val localeStrings = strings
    var noteText by remember { mutableStateOf(initialNote) }
    val maxLen = 256

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            EditHeader(localeStrings.userNoteEditTitle, onDismiss)
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= maxLen) noteText = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 8,
                placeholder = {
                    Text(
                        localeStrings.userNoteEditTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                supportingText = { Text("${noteText.length}/$maxLen") },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(noteText) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(localeStrings.editProfileSave)
            }
        }
    }
}
