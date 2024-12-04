package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.compoments.sharedElementBy
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.InstantsVo
import io.github.vrcmteam.vrcm.presentation.screens.home.sheet.FriendLocationBottomSheet
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.Pager

object FriendLocationPager : Pager {

    override val index: Int
        get() = 0
    override val title: String
        @Composable
        get() = "Location"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = Icons.Rounded.Explore)

    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content() {
        val friendLocationPagerModel: FriendLocationPagerModel = koinScreenModel()
        // 控制只有第一次跳转到当前页面时自动刷新
        val lazyListState = rememberLazyListState()
        LaunchedEffect(Unit) {
            // 未clear()的同步刷新一次
            friendLocationPagerModel.doRefreshFriendLocation(removeNotIncluded = true)
        }
        LaunchedEffect(Unit) {
            SharedFlowCentre.toPagerTop.collect {
                // 防止滑动时手动阻止滑动动画导致任务取消,监听失效的bug
                kotlin.runCatching {
                    lazyListState.animateScrollToFirst()
                }
            }
        }

        FriendLocationPager(
            friendLocationMap = friendLocationPagerModel.friendLocationMap,
            isRefreshing = friendLocationPagerModel.isRefreshing,
            lazyListState = lazyListState,
            doRefresh = friendLocationPagerModel::refreshFriendLocation
        )

    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendLocationPager(
    friendLocationMap: Map<LocationType, MutableList<FriendLocation>>,
    isRefreshing: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    doRefresh: suspend () -> Unit,
) {
    var bottomSheetIsVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val currentLocation: MutableState<FriendLocation?> = remember { mutableStateOf(null) }
    val onClickLocation: (FriendLocation) -> Unit = {
        currentLocation.value = it
        bottomSheetIsVisible = true
    }
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    RefreshBox(
        refreshContainerOffsetY = topPadding,
        isRefreshing = isRefreshing,
        doRefresh = doRefresh
    ) {
        val currentNavigator = currentNavigator
        val offlineFriendLocation = friendLocationMap[LocationType.Offline]?.get(0)
        val privateFriendLocation = friendLocationMap[LocationType.Private]?.get(0)
        val travelingFriendLocation = friendLocationMap[LocationType.Traveling]?.get(0)
        val instanceFriendLocations = friendLocationMap[LocationType.Instance]
        val onClickUserIcon = { user: IUser ->
            if (currentNavigator.size <= 1) currentNavigator push UserProfileScreen(UserProfileVo(user))
        }
        // 如果没有底部系统手势条，默认12dp
        val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + 80.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding
            )
        ) {

            SimpleCLocationCard(
                friendLocation = offlineFriendLocation,
                locationType = LocationType.Offline,
                onClickUserIcon = onClickUserIcon
            ) { strings.fiendLocationPagerWebsite }

            SimpleCLocationCard(
                friendLocation = privateFriendLocation,
                locationType = LocationType.Private,
                onClickUserIcon = onClickUserIcon
            ) { strings.fiendLocationPagerPrivate }

            SimpleCLocationCard(
                friendLocation = travelingFriendLocation,
                locationType = LocationType.Traveling,
                onClickUserIcon = onClickUserIcon
            ) { strings.fiendLocationPagerTraveling }

            if (!instanceFriendLocations.isNullOrEmpty()) {
                item(key = LocationType.Instance) {
                    LocationTitle(
                        text = strings.fiendLocationPagerLocation,
                    )
                }
                items(instanceFriendLocations, key = { it.location }) { location ->
                    LocationCard(location, { onClickLocation(location) }) {
                        UserIconsRow(
                            friends = it,
                            onClickUserIcon = onClickUserIcon
                        )
                    }
                }
            }

        }
    }
    FriendLocationBottomSheet(bottomSheetIsVisible, sheetState, currentLocation) {
        bottomSheetIsVisible = false
    }
}

private fun LazyListScope.SimpleCLocationCard(
    friendLocation: FriendLocation?,
    locationType: LocationType,
    onClickUserIcon: (IUser) -> Unit,
    text: @Composable () -> String,

    ) {
    friendLocation?.friendList.let {
        if (it.isNullOrEmpty()) return@let
        item(key = locationType) {
            LocationTitle(text())
        }
        item(key = locationType.value) {
            UserIconsRow(
                friends = it,
                contentPadding = PaddingValues(horizontal = 16.dp),
                onClickUserIcon = onClickUserIcon
            )
        }
    }
}

@Composable
private fun LocationTitle(
    text: String,
) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UserIconsRow(
    friends: List<State<FriendData>>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClickUserIcon: (IUser) -> Unit,
) {
    if (friends.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(friends, key = { it.value.id }) {
            val friend = it.value
            LocationFriend(
                id = friend.id,
                iconUrl = friend.iconUrl,
                name = friend.displayName,
                userStatus = friend.status
            ) { onClickUserIcon(friend) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyItemScope.LocationFriend(
    id: String,
    iconUrl: String,
    name: String,
    userStatus: UserStatus,
    onClickUserIcon: () -> Unit,
) {
    Column(
        modifier = Modifier.width(60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClickUserIcon).animateItem(fadeInSpec = null, fadeOutSpec = null),
        verticalArrangement = Arrangement.Center
    ) {
        UserStateIcon(
            modifier = Modifier.fillMaxSize().sharedElementBy("${id}UserIcon"),
            iconUrl = iconUrl,
            userStatus = userStatus
        )
        Text(
            modifier = Modifier.fillMaxSize(),
            text = name,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun LazyItemScope.LocationCard(
    location: FriendLocation,
    clickable: () -> Unit,
    content: @Composable (List<State<FriendData>>) -> Unit,
) {
    val instants by location.instants
    var showUser by rememberSaveable(location.location) { mutableStateOf(false) }
    val friendList = location.friendList
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        tonalElevation = (-2).dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .clickable { showUser = !showUser }
                .padding(8.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AImage(
                    modifier = Modifier
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 8.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 8.dp
                            )
                        )
                        .clickable(onClick = clickable),
                    imageData = instants.worldImageUrl,
                    contentDescription = "WorldImage"
                )
                Column(
                    modifier = Modifier
                        .weight(0.5f),
                ) {
                    Text(
                        text = instants.worldName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier
                            .height(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AImage(
                            modifier = Modifier
                                .size(15.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape
                                ),
                            imageData = instants.regionIconUrl
                        )
                        Text(
                            text = instants.accessType,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )

                    }
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = instants.worldDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 房间好友头像/房间持有者与房间人数比
                    MemberInfoRow(showUser, friendList, instants)
                }
            }
            AnimatedVisibility(showUser) {
                content(friendList)
            }
        }
    }
}

/**
 * 房间好友头像/房间持有者与房间人数比
 */
@Composable
private inline fun MemberInfoRow(
    showUser: Boolean,
    friendList: List<State<FriendData>>,
    instants: InstantsVo,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
    ) {
        // 房间好友头像/房间持有者
        Box(modifier = Modifier.fillMaxHeight().weight(0.6f)) {
            AnimatedContent(
                targetState = showUser,
                transitionSpec = {
                    (fadeIn() + expandHorizontally()) togetherWith (fadeOut() + shrinkHorizontally())
                }
            ) {
                if (!it) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy((-8).dp)
                    ) {
                        friendList.take(5).forEach { friendState ->
                            UserStateIcon(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                iconUrl = friendState.value.iconUrl,
                            )
                        }
                    }
                } else {
                    instants.ownerName.value?.let { ownerName ->
                        Row(
                            modifier = Modifier.fillMaxHeight().background(
                                MaterialTheme.colorScheme.inverseOnSurface,
                                MaterialTheme.shapes.medium
                            )
                                .clip(MaterialTheme.shapes.medium)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = Icons.Default.Home,
                                contentDescription = "OwnerIcon",
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ownerName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))
        // 房间人数比行
        if (instants.userCount.isEmpty()) return@Row
        Box(
            modifier = Modifier.fillMaxHeight()
                .weight(0.3f)
                .background(
                    MaterialTheme.colorScheme.inverseOnSurface,
                    MaterialTheme.shapes.medium
                )
                .clip(MaterialTheme.shapes.medium)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = instants.userCount,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

