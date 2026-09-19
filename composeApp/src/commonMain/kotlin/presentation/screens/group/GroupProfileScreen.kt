package io.github.vrcmteam.vrcm.presentation.screens.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonRole
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppCard
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppGroup
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTab
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTabRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.AppToggle
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalGlassBackdrop
import io.github.vrcmteam.vrcm.presentation.designsystem.glassBackdropSource
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberGlassBackdrop
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.network.api.groups.data.Gallery
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupGalleryImage
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupMember
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupPost
import io.github.vrcmteam.vrcm.network.api.groups.data.Role
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.GroupIcon
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.LocationDialogContent
import io.github.vrcmteam.vrcm.presentation.compoments.OfficialUrlShareButton
import io.github.vrcmteam.vrcm.presentation.compoments.RegionIcon
import io.github.vrcmteam.vrcm.presentation.compoments.SharedTextBoundsResizeMode
import io.github.vrcmteam.vrcm.presentation.compoments.TextChip
import io.github.vrcmteam.vrcm.presentation.compoments.TextLabel
import io.github.vrcmteam.vrcm.presentation.compoments.groupNameSharedKey
import io.github.vrcmteam.vrcm.presentation.compoments.sharedBoundsBy
import io.github.vrcmteam.vrcm.presentation.compoments.renderUserItems
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.gallery.ImagePreviewDialog
import io.github.vrcmteam.vrcm.presentation.screens.user.LinksRow
import io.github.vrcmteam.vrcm.presentation.screens.user.LanguagesRow
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import presentation.compoments.TopMenuBar
import kotlinx.serialization.Serializable

@Serializable
class GroupProfileScreen(
    private val groupProfileVo: GroupProfileVo,
    private val sharedSuffixKey: String = "",
) : AppDetailRoute {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val screenModel: GroupProfileScreenModel = koinViewModel()
        val groupState by screenModel.groupProfileState.collectAsState()
        val members by screenModel.members.collectAsState()
        val owner by screenModel.owner.collectAsState()
        val galleryImages by screenModel.galleryImages.collectAsState()
        val posts by screenModel.posts.collectAsState()
        val postAuthors by screenModel.postAuthors.collectAsState()
        val postsLoading by screenModel.postsLoading.collectAsState()
        val postsLoadingMore by screenModel.postsLoadingMore.collectAsState()
        val postsEndReached by screenModel.postsEndReached.collectAsState()
        val membersLoading by screenModel.membersLoading.collectAsState()
        val groupInstances by screenModel.groupInstances.collectAsState()
        val isLoading by screenModel.isLoading.collectAsState()
        val isActionLoading by screenModel.isActionLoading.collectAsState()
        val isRepresentationUpdating by screenModel.isRepresentationUpdating.collectAsState()
        val isNotificationPreferenceUpdating by
            screenModel.isNotificationPreferenceUpdating.collectAsState()
        val currentSession by SharedFlowCentre.currentSession.collectAsState()

        LaunchedEffect(groupProfileVo.groupId) {
            screenModel.loadGroupData(groupProfileVo)
        }

        val group = groupState ?: groupProfileVo
        val representationUpdateFailedMessage = strings.groupRepresentationUpdateFailed
        val representationSessionChangedMessage = strings.groupRepresentationSessionChanged
        val notificationPreferenceUpdateFailedMessage = strings.groupNotificationsUpdateFailed
        val notificationPreferenceSessionChangedMessage = strings.groupNotificationsSessionChanged
        val scrollState = rememberScrollState()
        var selectedTabIndex by rememberSaveable(groupProfileVo.groupId) { mutableStateOf(0) }

        LaunchedEffect(scrollState, selectedTabIndex) {
            snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (value, maxValue) ->
                if (selectedTabIndex == 1 && (maxValue == 0 || maxValue - value <= 600)) {
                    screenModel.loadMorePosts()
                }
            }
        }

        CompositionLocalProvider(LocalSharedSuffixKey provides sharedSuffixKey) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val bannerHeight = maxWidth * 9f / 16f
                val offsetDp = with(LocalDensity.current) { scrollState.value.toDp() }
                val remainingDistance = bannerHeight - offsetDp
                val ratio = ((remainingDistance / bannerHeight).coerceIn(0f, 1f)).let {
                    FastOutSlowInEasing.transform(it)
                }
                val topBarHeight = 64.dp
                val sysTopPadding = getInsetPadding(WindowInsets::getTop)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppTheme.colors.groupedBackground)
                ) {
                    // 页面内容是顶栏玻璃按钮的取样源
                    val glassBackdrop = rememberGlassBackdrop()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassBackdropSource(glassBackdrop)
                            .verticalScroll(scrollState)
                    ) {
                        GroupBanner(
                            group = group,
                            bannerHeight = bannerHeight
                        )
                        GroupHeaderInfo(
                            group = group,
                            isLoading = isLoading,
                            isActionLoading = isActionLoading,
                            isRepresentationUpdating = isRepresentationUpdating,
                            isNotificationPreferenceUpdating = isNotificationPreferenceUpdating,
                            groupSettingsAvailable = currentSession?.token?.let(group::hasActiveMembership) == true,
                            onJoin = { screenModel.joinGroup() },
                            onLeave = { screenModel.leaveGroup() },
                            onRepresentationChange = { isRepresenting ->
                                screenModel.updateRepresentation(
                                    isRepresenting = isRepresenting,
                                    failureMessage = representationUpdateFailedMessage,
                                    sessionChangedMessage = representationSessionChangedMessage,
                                )
                            },
                            onNotificationPreferenceChange = { enabled ->
                                screenModel.updateNotificationPreference(
                                    enabled = enabled,
                                    failureMessage = notificationPreferenceUpdateFailedMessage,
                                    sessionChangedMessage = notificationPreferenceSessionChangedMessage,
                                )
                            },
                        )
                        AppTabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val tabs = listOf(strings.groupTabDetails, strings.groupTabPosts, strings.groupTabMembers, strings.groupTabGallery)
                            tabs.forEachIndexed { index, title ->
                                AppTab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { AppText(text = title, maxLines = 1) }
                                )
                            }
                        }
                        when (selectedTabIndex) {
                            0 -> DetailsContent(group = group, owner = owner, instances = groupInstances)
                            1 -> PostsContent(
                                posts = posts,
                                roles = group.roles,
                                postAuthors = postAuthors,
                                isLoading = postsLoading,
                                isLoadingMore = postsLoadingMore,
                                endReached = postsEndReached,
                            )
                            2 -> MembersContent(members = members, isLoading = membersLoading)
                            else -> GalleriesContent(group = group, galleryImages = galleryImages)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
                        TopMenuBar(
                            topBarHeight = topBarHeight,
                            sysTopPadding = sysTopPadding,
                            offsetDp = 0.dp,
                            ratio = ratio,
                            onReturn = { currentNavigator.pop() },
                            onMenu = null,
                            actions = {
                                OfficialUrlShareButton(
                                    url = "https://vrchat.com/home/group/${group.groupId}",
                                )
                                AppIconButton(
                                    enabled = !isLoading &&
                                        !isRepresentationUpdating &&
                                        !isNotificationPreferenceUpdating,
                                    onClick = screenModel::refreshGroupData,
                                ) {
                                    if (isLoading) {
                                        AppActivityIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = LocalContentColor.current,
                                        )
                                    } else {
                                        AppIcon(
                                            imageVector = AppIcons.Refresh,
                                            contentDescription = "Refresh",
                                        )
                                    }
                                }
                            },
                        )
                    }
                    CollapsingTitleRow(
                        group = group,
                        membershipStatus = group.membershipStatus,
                        scrollPx = scrollState.value.toFloat(),
                        bannerHeight = bannerHeight,
                        topBarHeight = topBarHeight,
                        sysTopPadding = sysTopPadding
                    )

                }
            }
        }
    }
}

@Composable
private fun GroupBanner(
    group: GroupProfileVo,
    bannerHeight: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
    ) {
        val heroImage = group.bannerUrl ?: group.iconUrl
        if (!heroImage.isNullOrBlank()) {
            AImage(
                modifier = Modifier.fillMaxSize(),
                imageData = heroImage,
                contentDescription = "GroupBanner"
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AppTheme.colors.fill,
                                AppTheme.colors.groupedBackground
                            )
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, AppTheme.colors.groupedBackground.copy(alpha = 0.9f))
                    )
                )
        )
    }
}

@Composable
private fun GroupHeaderInfo(
    group: GroupProfileVo,
    isLoading: Boolean,
    isActionLoading: Boolean,
    isRepresentationUpdating: Boolean,
    isNotificationPreferenceUpdating: Boolean,
    groupSettingsAvailable: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onRepresentationChange: (Boolean) -> Unit,
    onNotificationPreferenceChange: (Boolean) -> Unit,
) {
    val membershipStatus = group.membershipStatus.lowercase()
    val joinState = group.joinState.lowercase()
    val isMember = membershipStatus == "member"
    val isRequested = membershipStatus == "requested"
    val isInvited = membershipStatus == "invited"
    val isBlocked = membershipStatus == "banned" || membershipStatus == "userblocked"

    val actionLabel = when {
        isBlocked -> strings.groupClosed
        isMember -> strings.groupLeave
        isRequested -> strings.groupRequested
        isInvited -> strings.groupJoin
        joinState == "request" -> strings.groupRequestToJoin
        joinState == "invite" -> strings.groupInviteOnly
        joinState == "closed" -> strings.groupClosed
        else -> strings.groupJoin
    }
    val actionEnabled = !isActionLoading && !isRequested && !isBlocked && (
        isMember || isInvited || joinState == "open" || joinState == "request"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatPill(icon = AppIcons.Groups, text = "${group.memberCount} ${strings.groupMembers}")
            StatPill(icon = AppIcons.Person, text = "${group.onlineMemberCount} ${strings.groupOnlineMembers}")
            if (group.isVerified) {
                StatPill(icon = AppIcons.CheckCircle, text = strings.groupVerifiedLabel)
            }
        }
        if (group.badges.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                group.badges.take(8).forEach { badge ->
                    TextChip(text = badge)
                }
            }
        }
        if (groupSettingsAvailable) {
            val settingsEnabled = !isLoading &&
                !isActionLoading &&
                !isRepresentationUpdating &&
                !isNotificationPreferenceUpdating
            AppGroup {
                GroupSettingRow(
                    title = strings.groupRepresentation,
                    description = when {
                        isRepresentationUpdating -> strings.groupRepresentationUpdating
                        group.myMember?.isRepresenting == true -> strings.groupRepresentationEnabled
                        else -> strings.groupRepresentationDisabled
                    },
                    checked = group.myMember?.isRepresenting == true,
                    updating = isRepresentationUpdating,
                    enabled = settingsEnabled,
                    onCheckedChange = onRepresentationChange,
                )
                AppDivider(Modifier.padding(start = AppSpacing.row))
                GroupSettingRow(
                    title = strings.groupNotifications,
                    description = when {
                        isNotificationPreferenceUpdating -> strings.groupNotificationsUpdating
                        group.myMember?.isSubscribedToAnnouncements == true ->
                            strings.groupNotificationsEnabled
                        else -> strings.groupNotificationsDisabled
                    },
                    checked = group.myMember?.isSubscribedToAnnouncements == true,
                    updating = isNotificationPreferenceUpdating,
                    enabled = settingsEnabled,
                    onCheckedChange = onNotificationPreferenceChange,
                )
            }
        }
        LoadingButton(
            modifier = Modifier.fillMaxWidth(),
            text = actionLabel,
            enabled = actionEnabled &&
                !isRepresentationUpdating &&
                !isNotificationPreferenceUpdating,
            isLoading = isActionLoading,
            // 退出是破坏性操作：灰底红字，不和"加入"一样用最醒目的主色
            style = if (isMember) AppButtonStyle.Gray else AppButtonStyle.Prominent,
            role = if (isMember) AppButtonRole.Destructive else AppButtonRole.Default,
            onClick = { if (isMember) onLeave() else onJoin() }
        )
    }
}

/** 群组设置的一行：标题 + 当前状态说明，尾部是开关；提交中在开关前转圈。 */
@Composable
private fun GroupSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    updating: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppSize.rowMinHeight)
            .padding(horizontal = AppSpacing.row, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(text = title, style = AppTheme.type.body)
            AppText(
                text = description,
                style = AppTheme.type.footnote,
                color = AppTheme.colors.secondaryLabel,
            )
        }
        if (updating) {
            AppActivityIndicator(modifier = Modifier.size(18.dp))
        }
        AppToggle(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CollapsingTitleRow(
    group: GroupProfileVo,
    membershipStatus: String,
    scrollPx: Float,
    bannerHeight: Dp,
    topBarHeight: Dp,
    sysTopPadding: Dp,
) {
    val density = LocalDensity.current
    val startIconSize = 64.dp
    val endIconSize = 28.dp
    val startYPx = with(density) { (bannerHeight - 16.dp - startIconSize).toPx() }
    val endYPx = with(density) { (sysTopPadding + (topBarHeight - endIconSize) / 2).toPx() }
    val travelPx = (startYPx - endYPx).coerceAtLeast(1f)
    val progress = (scrollPx / travelPx).coerceIn(0f, 1f)
    val iconSize = lerp(startIconSize, endIconSize, progress)
    val nameScale = lerp(1f, 0.8f, progress)
    val xShift = lerp(0.dp, 50.dp, progress) // align to back button + 8.dp
    val rowSpacing = lerp(12.dp, 8.dp, progress)
    val yPx = (startYPx - scrollPx).coerceAtLeast(endYPx)
    val statusAlpha = 1f - progress
    val statusText = membershipStatus.lowercase()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
    ) {
        val trailingActionsWidth = 112.dp * progress
        val nameMaxWidth = (
            maxWidth - 16.dp - xShift - iconSize - rowSpacing - trailingActionsWidth
        ).coerceAtLeast(32.dp)
        Row(
            modifier = Modifier
                .padding(start = 16.dp)
                .height(IntrinsicSize.Min)
                .offset {
                    IntOffset(
                        x = with(density) { xShift.roundToPx() },
                        y = yPx.toInt()
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            GroupIcon(
                iconUrl = group.iconUrl,
                size = iconSize,
                modifier = Modifier.sharedBoundsBy("${group.groupId}GroupIcon")
            )
            Column(
                modifier = Modifier.graphicsLayer {
                    scaleX = nameScale
                    scaleY = nameScale
                },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppText(
                    modifier = Modifier.sharedBoundsBy(
                        key = groupNameSharedKey(group.groupId),
                        resizeMode = SharedTextBoundsResizeMode,
                    ).widthIn(max = nameMaxWidth),
                    text = group.name.ifBlank { strings.unknown },
                    style = AppTheme.type.title2,
                    color = AppTheme.colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (statusAlpha == 0f) return@Row
                Row(
                    modifier = Modifier.graphicsLayer { alpha = statusAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (group.shortCode.isNotBlank()) {
                        TextLabel(text = "#${group.shortCode}")
                    }
                    if (group.discriminator.isNotBlank()) {
                        TextLabel(text = group.discriminator)
                    }
                    if (statusText.isNotBlank() && statusText != "inactive") {
                        TextLabel(text = statusText.formatStatus())
                    }
                }
            }
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun lerp(start: Dp, end: Dp, fraction: Float): Dp =
    Dp(lerp(start.value, end.value, fraction))

@Composable
private fun DetailsContent(group: GroupProfileVo, owner: UserData?, instances: List<InstanceData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (instances.isNotEmpty()) {
            GroupInstancesSection(instances = instances)
        }

        OwnerCard(
            owner = owner,
            ownerId = group.ownerId,
            modifier = Modifier.fillMaxWidth()
        )

        if (group.languages.isNotEmpty() || group.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (group.languages.isNotEmpty()) {
                    LanguagesCard(
                        languages = group.languages,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (group.tags.isNotEmpty()) {
                    ChipSection(
                        title = strings.groupTags,
                        items = group.tags,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (group.links.isNotEmpty()) {
                    SectionCard(
                        title = strings.groupLinks,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LinksRow(bioLinks = group.links, width = 36.dp)
                    }
                }
            }
        }

        if (group.description.isNotBlank()) {
            TextSection(
                title = strings.groupDescription,
                text = group.description,
                modifier = Modifier.fillMaxWidth()
            )
        }
        SectionCard(
            title = strings.groupTabDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {

            val detailItems = listOf(
                strings.groupPrivacy to group.privacy,
                strings.groupJoinState to group.joinState,
                strings.groupMembershipStatus to group.membershipStatus,
                strings.groupVerifiedLabel to boolLabel(group.isVerified),
                strings.groupSearchableLabel to group.isSearchable?.let { boolLabel(it) },
                strings.groupAllowJoinPrompt to group.allowGroupJoinPrompt?.let { boolLabel(it) },
                strings.groupAgeVerificationCode to group.ageVerificationBetaCode,
                strings.groupAgeVerificationSlots to group.ageVerificationBetaSlots?.toString(),
                strings.groupAgeVerificationAvailable to group.ageVerificationSlotsAvailable?.let { boolLabel(it) },
                strings.groupCreatedAt to formatLocalTime(group.createdAt),
                strings.groupUpdatedAt to formatLocalTime(group.updatedAt),
                strings.groupJoinedAt to formatLocalTime(group.myMember?.joinedAt),
                strings.groupMemberSync to formatLocalTime(group.memberCountSyncedAt),
                strings.groupLastPost to formatLocalTime(group.lastPostCreatedAt),
            ).filter { !it.second.isNullOrBlank() }
            if (detailItems.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detailItems.forEach { (label, value) ->
                        KeyValueChip(
                            label = label,
                            value = value,
                            modifier = Modifier.widthIn(min = 140.dp)
                        )
                    }
                }
            }
        }
        if (group.badges.isNotEmpty()) {
            ChipSection(title = strings.groupBadges, items = group.badges)
        }
        if (!group.rules.isNullOrBlank()) {
            TextSection(title = strings.groupRules, text = group.rules.orEmpty())
        }

    }
}

@Composable
private fun GroupInstancesSection(instances: List<InstanceData>) {
    SectionCard(
        title = "${strings.groupInstances} (${instances.size})",
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            instances.forEach { instance ->
                GroupInstanceCard(instance = instance)
            }
        }
    }
}

@Composable
private fun GroupInstanceCard(instance: InstanceData) {
    val currentNavigator = currentNavigator
    val world = instance.world
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.m)
            .enableIf(world.id.isNotBlank()) {
                clickable {
                    currentNavigator push WorldProfileScreen(
                        worldProfileVO = WorldProfileVo(
                            worldId = world.id,
                            worldName = world.name,
                            worldImageUrl = world.imageUrl,
                            worldDescription = world.description.orEmpty(),
                            authorID = world.authorId,
                            authorName = world.authorName,
                        ),
                    )
                }
            },
        shape = AppShapes.m,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AImage(
                modifier = Modifier
                    .size(64.dp)
                    .clip(AppShapes.m),
                imageData = world.imageUrl,
                contentDescription = "WorldImage"
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = world.name.ifBlank { strings.unknown },
                    style = AppTheme.type.subheadlineEmphasized,
                    color = AppTheme.colors.tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RegionIcon(
                        size = 14.dp,
                        region = instance.region
                    )
                    AppText(
                        text = instance.accessType.displayName,
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.tertiaryLabel
                    )
                    AppText(
                        text = "#${instance.name}",
                        style = AppTheme.type.caption2Emphasized,
                        color = AppTheme.colors.tertiaryLabel
                    )
                }
                AppText(
                    text = "${instance.nUsers}/${instance.capacity}",
                    style = AppTheme.type.caption2Emphasized,
                    color = AppTheme.colors.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun PostsContent(
    posts: List<GroupPost>,
    roles: List<Role>,
    postAuthors: Map<String, String>,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    endReached: Boolean = false,
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AppActivityIndicator()
        }
        return
    }
    if (posts.isEmpty()) {
        EmptyState(
            message = strings.groupNoPosts,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        posts.forEach { post ->
            PostCard(post = post, roles = roles, authorName = postAuthors[post.authorId])
        }
        if (isLoadingMore && !endReached) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppActivityIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PostCard(post: GroupPost, roles: List<Role>, authorName: String? = null) {
    SectionCard(
        title = post.title.ifBlank { strings.unknown },
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!post.imageUrl.isNullOrBlank()) {
            AImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(AppShapes.m),
                imageData = post.imageUrl,
                contentDescription = "PostImage"
            )
        }
        if (post.text.isNotBlank()) {
            SelectionContainer {
                AppText(
                    text = post.text,
                    style = AppTheme.type.subheadline
                )
            }
        }
        if (post.roleIds.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                post.roleIds.forEach { roleId ->
                    val roleName = roles.firstOrNull { it.id == roleId }?.name ?: roleId
                    TextChip(text = roleName)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayAuthor = authorName ?: strings.unknown
            AppText(
                text = displayAuthor,
                style = AppTheme.type.caption2Emphasized,
                color = AppTheme.colors.secondaryLabel
            )
            if (!post.createdAt.isNullOrBlank()) {
                AppText(
                    text = formatLocalTime(post.createdAt) ?: "",
                    style = AppTheme.type.caption2Emphasized,
                    color = AppTheme.colors.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun MembersContent(members: List<GroupMember>, isLoading: Boolean = false) {
    val currentNavigator = currentNavigator
    val users = remember(members) { members.mapNotNull { it.user } }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AppActivityIndicator()
        }
        return
    }

    if (users.isEmpty()) {
        EmptyState(
            message = strings.unknown,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 4000.dp),
        contentPadding = PaddingValues(horizontal = AppSpacing.page),
    ) {
        renderUserItems(
            users = users,
            grouped = true,
            onUserClick = { user, sharedSuffixKey ->
                currentNavigator push UserProfileScreen(
                    userProfileVO = UserProfileVo(user),
                    sharedSuffixKey = sharedSuffixKey,
                )
            }
        )
    }
}

@Composable
private fun GalleriesContent(
    group: GroupProfileVo,
    galleryImages: Map<String, List<GroupGalleryImage>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (group.galleries.isEmpty()) {
            EmptyState(
                message = strings.galleryTabNoFiles.formatPlaceholder(strings.galleryScreenTitle),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        } else {
            group.galleries.forEach { gallery ->
                GallerySection(
                    gallery = gallery,
                    images = galleryImages[gallery.id].orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun GallerySection(
    gallery: Gallery,
    images: List<GroupGalleryImage>,
    modifier: Modifier = Modifier,
) {
    val (dialogContent, setDialogContent) = LocationDialogContent.current
    SectionCard(title = gallery.name, modifier = modifier.fillMaxWidth()) {
        if (gallery.description.isNotBlank()) {
            AppText(
                text = gallery.description,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel
            )
        }
        if (gallery.membersOnly) {
            TextLabel(text = "Members Only")
        }
        if (images.isEmpty()) {
            EmptyState(message = strings.galleryTabNoFiles.formatPlaceholder(gallery.name))
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    val previewFileId = image.fileId.ifBlank { image.id }
                    val previewKey = previewFileId.ifBlank { image.imageUrl }
                    val currentPreviewId = (dialogContent as? ImagePreviewDialog)?.fileId
                    AnimatedVisibility(visible = currentPreviewId != previewFileId) {
                        AImage(
                            modifier = Modifier
                                .width(180.dp)
                                .height(110.dp)
                                .clip(AppShapes.m)
                                .sharedBoundsBy(
                                    previewKey,
                                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                                    animatedVisibilityScope = this
                                )
                                .enableIf(previewFileId.isNotBlank()) {
                                    clickable {
                                        setDialogContent(
                                            ImagePreviewDialog(
                                                fileId = previewFileId,
                                                fileName = previewFileId,
                                                fileExtension = ""
                                            )
                                        )
                                    }
                                },
                            imageData = image.imageUrl,
                            contentDescription = "GalleryImage"
                        )
                    }
                }
            }
        }
        KeyValueRow(label = "Created At", value = formatLocalTime(gallery.createdAt))
        KeyValueRow(label = "Updated At", value = formatLocalTime(gallery.updatedAt))
    }
}

@Composable
private fun OwnerCard(
    owner: UserData?,
    ownerId: String?,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = strings.groupOwner, modifier = modifier) {
        val currentNavigator = currentNavigator
        val ownerUserId = owner?.id ?: ownerId.orEmpty()
        if (owner == null) {
            KeyValueRow(label = strings.groupOwner, value = strings.unknown)
            return@SectionCard
        }
        val statusText = owner.statusDescription.takeIf { it.isNotBlank() }
            ?: owner.status.value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.enableIf(ownerUserId.isNotBlank()) {
                clickable {
                    currentNavigator push UserProfileScreen(
                        userProfileVO = UserProfileVo(owner)
                    )
                }
            }
        ) {
            GroupIcon(iconUrl = owner.iconUrl, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(
                    text = owner.displayName,
                    style = AppTheme.type.subheadlineEmphasized,
                    fontWeight = FontWeight.Medium
                )
                TextLabel(text = statusText)
            }
        }
    }
}

@Composable
private fun LanguagesCard(
    languages: List<String>,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = strings.groupLanguages, modifier = modifier) {
        LanguagesRow(speakLanguages = languages, width = 28.dp)
    }
}

@Composable
private fun MemberCard(member: GroupMember) {
    val currentNavigator = currentNavigator
    val userId = member.user?.id?.ifBlank { member.userId } ?: member.userId
    val displayName = member.user?.displayName?.ifBlank { userId } ?: userId
    val avatarUrl = member.user?.iconUrl
        ?: member.user?.thumbnailUrl
        ?: member.user?.profilePicOverride
    val statusText = member.membershipStatus.formatStatus()
    AppCard(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .enableIf(userId.isNotBlank()) {
                clickable {
                    currentNavigator push UserProfileScreen(
                        userProfileVO = member.user?.let(::UserProfileVo)
                            ?: UserProfileVo(id = userId, displayName = displayName)
                    )
                }
            },
        color = AppTheme.colors.secondaryGroupedBackground) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (avatarUrl.isNullOrBlank()) {
                AppSurface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = AppTheme.colors.fill
                ) {
                    AppIcon(
                        imageVector = AppIcons.Person,
                        contentDescription = "MemberIcon",
                        tint = AppTheme.colors.tint,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                GroupIcon(iconUrl = avatarUrl, size = 44.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = displayName,
                    style = AppTheme.type.subheadline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AppText(
                    text = statusText,
                    style = AppTheme.type.caption2Emphasized,
                    color = AppTheme.colors.secondaryLabel
                )
            }
            if (member.isRepresenting) {
                TextLabel(text = "Representing")
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier,
        shape = AppShapes.l,
        color = AppTheme.colors.secondaryGroupedBackground) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppText(
                text = title,
                style = AppTheme.type.headline,
                color = AppTheme.colors.label
            )
            content()
        }
    }
}

@Composable
private fun TextSection(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = title, modifier = modifier.fillMaxWidth()) {
        SelectionContainer {
            AppText(
                text = text,
                style = AppTheme.type.subheadline
            )
        }
    }
}

@Composable
private fun ChipSection(
    title: String,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = title, modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.take(18).forEach { item ->
                TextChip(text = item)
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    AppSurface(
        shape = AppShapes.m,
        color = AppTheme.colors.secondaryGroupedBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(14.dp),
                tint = AppTheme.colors.tint
            )
            AppText(
                text = text,
                style = AppTheme.type.caption2Emphasized
            )
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String?) {
    val valueText = value?.takeIf { it.isNotBlank() } ?: strings.unknown
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = label,
            style = AppTheme.type.caption1,
            color = AppTheme.colors.secondaryLabel
        )
        AppText(
            text = valueText,
            style = AppTheme.type.caption1,
            color = AppTheme.colors.label
        )
    }
}

@Composable
private fun KeyValueChip(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    val valueText = value?.takeIf { it.isNotBlank() } ?: strings.unknown
    AppSurface(
        modifier = modifier,
        shape = AppShapes.m,
        color = AppTheme.colors.secondaryGroupedBackground
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppText(
                text = label,
                style = AppTheme.type.caption2Emphasized,
                color = AppTheme.colors.secondaryLabel
            )
            AppText(
                text = valueText,
                style = AppTheme.type.caption1,
                color = AppTheme.colors.label
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = message,
            style = AppTheme.type.subheadline,
            color = AppTheme.colors.secondaryLabel
        )
    }
}

private fun boolLabel(value: Boolean): String = if (value) "Yes" else "No"

private fun String.formatPlaceholder(value: String): String = replace("%s", value)

private fun String.formatStatus(): String =
    when (lowercase()) {
        "userblocked" -> "Blocked"
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

private fun formatLocalTime(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return null
    return raw.toLocalDateTime()?.ignoredFormat ?: raw
}
