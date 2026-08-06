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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.network.api.groups.data.Gallery
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupGalleryImage
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupMember
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupPost
import io.github.vrcmteam.vrcm.network.api.groups.data.Role
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.core.extensions.toLocalDateTime
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.GroupIcon
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedTransitionDialogScope
import io.github.vrcmteam.vrcm.presentation.compoments.LoadingButton
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.LocationDialogContent
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
import presentation.compoments.topMenuActionColors
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

        LaunchedEffect(groupProfileVo.groupId) {
            screenModel.loadGroupData(groupProfileVo)
        }

        val group = groupState ?: groupProfileVo
        val scrollState = rememberScrollState()
        var selectedTabIndex by remember { mutableStateOf(0) }

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
                val refreshActionColors = topMenuActionColors(
                    ratio = ratio,
                    onPrimary = MaterialTheme.colorScheme.onPrimary,
                    primary = MaterialTheme.colorScheme.primary,
                    primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                )
                val refreshButtonColors = IconButtonColors(
                    containerColor = refreshActionColors.container,
                    contentColor = refreshActionColors.content,
                    disabledContainerColor = refreshActionColors.container,
                    disabledContentColor = refreshActionColors.content,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        GroupBanner(
                            group = group,
                            bannerHeight = bannerHeight
                        )
                        GroupHeaderInfo(
                            group = group,
                            isActionLoading = isActionLoading,
                            onJoin = { screenModel.joinGroup() },
                            onLeave = { screenModel.leaveGroup() }
                        )
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier.fillMaxWidth(),
                            indicator = {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(it[selectedTabIndex]),
                                    width = 28.dp,
                                    shape = RoundedCornerShape(4.dp)
                                )
                            },
                        ) {
                            val tabs = listOf(strings.groupTabDetails, strings.groupTabPosts, strings.groupTabMembers, strings.groupTabGallery)
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text(text = title, maxLines = 1) }
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
                    TopMenuBar(
                        topBarHeight = topBarHeight,
                        sysTopPadding = sysTopPadding,
                        offsetDp = 0.dp,
                        ratio = ratio,
                        onReturn = { currentNavigator.pop() },
                        onMenu = null
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topBarHeight + sysTopPadding)
                            .padding(top = sysTopPadding, end = 10.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        IconButton(
                            enabled = !isLoading,
                            colors = refreshButtonColors,
                            onClick = screenModel::refreshGroupData,
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = refreshActionColors.content,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = refreshActionColors.content,
                                )
                            }
                        }
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
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
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
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    )
                )
        )
    }
}

@Composable
private fun GroupHeaderInfo(
    group: GroupProfileVo,
    isActionLoading: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
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
        LoadingButton(
            modifier = Modifier.fillMaxWidth(),
            text = actionLabel,
            enabled = actionEnabled,
            isLoading = isActionLoading,
            onClick = { if (isMember) onLeave() else onJoin() }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
    ) {
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
                Text(
                    modifier = Modifier.sharedBoundsBy(
                        key = groupNameSharedKey(group.groupId),
                        resizeMode = SharedTextBoundsResizeMode,
                    ),
                    text = group.name.ifBlank { strings.unknown },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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
        SectionCard(title = strings.groupTabDetails, modifier = Modifier.fillMaxWidth()) {

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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
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
        tonalElevation = (-1).dp,
        shape = MaterialTheme.shapes.medium,
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
                    .clip(RoundedCornerShape(12.dp)),
                imageData = world.imageUrl,
                contentDescription = "WorldImage"
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = world.name.ifBlank { strings.unknown },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                    Text(
                        text = instance.accessType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "#${instance.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "${instance.nUsers}/${instance.capacity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            CircularProgressIndicator()
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
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                    .clip(MaterialTheme.shapes.medium),
                imageData = post.imageUrl,
                contentDescription = "PostImage"
            )
        }
        if (post.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium
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
            Text(
                text = displayAuthor,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!post.createdAt.isNullOrBlank()) {
                Text(
                    text = formatLocalTime(post.createdAt) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            CircularProgressIndicator()
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        renderUserItems(
            users = users,
            onUserClick = { user ->
                currentNavigator push UserProfileScreen(
                    userProfileVO = UserProfileVo(user)
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
            Text(
                text = gallery.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                .clip(MaterialTheme.shapes.medium)
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
                Text(
                    text = owner.displayName,
                    style = MaterialTheme.typography.titleSmall,
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
    Card(
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (avatarUrl.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = AppIcons.Person,
                        contentDescription = "MemberIcon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                GroupIcon(iconUrl = avatarUrl, size = 44.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
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
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
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
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
