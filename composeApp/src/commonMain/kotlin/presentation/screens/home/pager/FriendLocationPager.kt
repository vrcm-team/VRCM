package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager


object FriendLocationPager : Pager {

    override val index: Int
        get() = 0
    override val title: String
        @Composable
        get() = "Location"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = AppIcons.Explore)

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
fun Pager.FriendLocationPager(
    friendLocationMap: Map<LocationType, MutableList<FriendLocation>>,
    isRefreshing: Boolean,
    lazyListState: LazyListState = rememberLazyListState(),
    doRefresh: suspend () -> Unit,
) {
    val navigator = currentNavigator
    val sharedSuffixKey = LocalSharedSuffixKey.current
    // 只会有一个实例被打开
    var selectLocation by rememberSaveable { mutableStateOf<String?>(null) }
    val onClickLocationCard: (FriendLocation) -> Unit = { friendLocation ->
        // 如果当前位置实例和点击的位置实例相同，则收起卡片，否则展开选中的，收起之前的
        selectLocation = if (selectLocation == friendLocation.location) null else friendLocation.location
    }
    val onClickWorldImage: (FriendLocation) -> Unit = { friendLocation ->
        if (navigator.size <= 1) {
            val currentLocation = friendLocation.instants.value
            val instances = friendLocationMap[LocationType.Instance]
                ?.map { it.instants.value }
                ?.filter { it.worldId == currentLocation.worldId }
                ?.map { InstanceVo(it) } ?: emptyList()
            // 创建临时的 WorldProfileVo
            val tempWorldProfileVo = WorldProfileVo(
                worldId = currentLocation.worldId,
                worldName = currentLocation.worldName,
                worldImageUrl = currentLocation.worldImageUrl,
                worldDescription = currentLocation.worldDescription,
                authorID = currentLocation.worldAuthorId,
                authorName = currentLocation.worldAuthorName,
                tags = currentLocation.worldAuthorTag,
                instances = instances.toMutableStateList(),
            )
            navigator push WorldProfileScreen(
                worldProfileVO = tempWorldProfileVo,
                location = friendLocation.location,
                sharedSuffixKey = sharedSuffixKey
            )
        }
    }
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    val onClickUserIcon = { user: IUser ->
        if (navigator.size <= 1) {
            navigator push UserProfileScreen(
                UserProfileVo(user),
                sharedSuffixKey
            )
        }
    }
    RefreshBox(
        refreshContainerOffsetY = topPadding,
        isRefreshing = isRefreshing,
        doRefresh = doRefresh
    ) {
        val offlineFriendLocation = friendLocationMap[LocationType.Offline]?.get(0)
        val privateFriendLocation = friendLocationMap[LocationType.Private]?.get(0)
        val travelingFriendLocation = friendLocationMap[LocationType.Traveling]?.get(0)
        val instanceFriendLocations = friendLocationMap[LocationType.Instance]?.sortedByDescending { it.friendList.size }
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
            ) { "${strings.fiendLocationPagerWebsite}${offlineFriendLocation?.let { "(${it.friends.size})" }}" }

            SimpleCLocationCard(
                friendLocation = privateFriendLocation,
                locationType = LocationType.Private,
                onClickUserIcon = onClickUserIcon
            ) { "${strings.fiendLocationPagerPrivate}${privateFriendLocation?.let { "(${it.friends.size})" }}" }

            SimpleCLocationCard(
                friendLocation = travelingFriendLocation,
                locationType = LocationType.Traveling,
                onClickUserIcon = onClickUserIcon
            ) { "${strings.fiendLocationPagerTraveling}${travelingFriendLocation?.let { "(${it.friends.size})" }}" }

            if (!instanceFriendLocations.isNullOrEmpty()) {
                item(key = LocationType.Instance) {
                    LocationTitle(
                        text = "${strings.fiendLocationPagerLocation}(${instanceFriendLocations.map { it.friends.size }.count()})",
                    )
                }

                items(instanceFriendLocations, key = { it.location }) { location ->
                    LocationCard(
                        location = location,
                        isSelected = selectLocation == location.location,
                        onClickWorldImage = { onClickWorldImage(location) },
                        onClickLocationCard = { onClickLocationCard(location) }
                    ) {
                        UserIconsRow(
                            modifier = Modifier.fillMaxWidth(),
                            friends = it,
                            onClickUserIcon = onClickUserIcon
                        )
                    }
                }
            }

        }
    }


//    FriendLocationBottomSheet(bottomSheetIsVisible, sheetState, currentLocation) {
//        bottomSheetIsVisible = false
//    }
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





@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LocationCard(
    location: FriendLocation,
    isSelected: Boolean,
    onClickWorldImage: () -> Unit,
    onClickLocationCard: () -> Unit,
    content: @Composable (List<State<FriendData>>) -> Unit,
) {
    val instants by location.instants
    val friendList = location.friendList
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        tonalElevation = (-2).dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(MaterialTheme.shapes.medium),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AImage(
                    modifier = Modifier
                        .sharedElementBy(
                            key = location.location + "WorldImage",
                        )
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 8.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 8.dp
                            )
                        )
                        .enableIf(instants.worldId.isNotEmpty()) {
                            clickable(onClick = onClickWorldImage)
                        },
                    imageData = instants.worldImageUrl,
                    contentDescription = "WorldImage"
                )
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 16.dp,
                                bottomStart = 8.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .clickable(onClick = onClickLocationCard),
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
                        RegionIcon(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            region = instants.region
                        )
                        Text(
                            text = instants.accessType.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "#${instants.name}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
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
                    MemberInfoRow(isSelected, friendList, instants)
                }
            }
            AnimatedVisibility(isSelected) {
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
    instants: HomeInstanceVo,
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
                    val owner = instants.owner ?: return@AnimatedContent
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
                            imageVector = owner.iconVector,
                            contentDescription = "OwnerIcon",
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = owner.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))
        // 房间人数比行
        if (instants.userCount.isEmpty()) return@Row
        TextLabel(
            modifier = Modifier.fillMaxHeight().weight(0.3f),
            text = instants.userCount,
        )
    }
}


