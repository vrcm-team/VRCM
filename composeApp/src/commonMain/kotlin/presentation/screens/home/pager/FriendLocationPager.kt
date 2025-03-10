package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
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
    val friendLocationPagerModel: FriendLocationPagerModel = koinScreenModel()
    var currentLocation by friendLocationPagerModel.currentLocation

    var currentDialog by LocationDialogContent.current
    val sharedSuffixKey = LocalSharedSuffixKey.current
    val onClickLocation: (FriendLocation) -> Unit = {
        currentLocation = it
        currentDialog = LocationDialog(
            friendLocation = it,
            sharedSuffixKey = sharedSuffixKey
        ){
            currentDialog = null
            currentLocation = null
        }

    }
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    val currentNavigator = currentNavigator
    val onClickUserIcon = { user: IUser ->
        if (currentNavigator.size <= 1) {
            currentNavigator push UserProfileScreen(
                sharedSuffixKey,
                UserProfileVo(user)
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
                    AnimatedVisibility(
                        visible = location != currentLocation,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier.animateItem()
                    ){
                        LocationCard(location) { onClickLocation(location) }
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
private fun AnimatedVisibilityScope.LocationCard(
    location: FriendLocation,
    clickable: () -> Unit,
) {
    val instants by location.instants
    val friendList = location.friendList
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .sharedBoundsBy(
                key = location.location + "Container",
                sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                animatedVisibilityScope = this,
                clipInOverlayDuringTransition = with(LocalSharedTransitionDialogScope.current){
                    OverlayClip(DialogShapeForSharedElement)
                }
            )
            .fillMaxWidth(),
        tonalElevation = (-2).dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = clickable)
                .animateContentSize()
                .padding(8.dp),
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
                        .sharedElementBy(
                            key = location.location + "WorldImage",
                            sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                            animatedVisibilityScope = this@LocationCard,
                        )
                        .weight(0.5f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 8.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 8.dp
                            )
                        ),
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
                        RegionIcon(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            region = instants.region
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
                    MemberInfoRow(friendList, instants)
                }
            }
        }
    }
}

/**
 * 房间好友头像/房间持有者与房间人数比
 */
@Composable
private inline fun MemberInfoRow(
//    showUser: Boolean,
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
//            AnimatedContent(
//                targetState = showUser,
//                transitionSpec = {
//                    (fadeIn() + expandHorizontally()) togetherWith (fadeOut() + shrinkHorizontally())
//                }
//            ) {
//                if (!it) {
//                    Row(
//                        modifier = Modifier.fillMaxSize(),
//                        horizontalArrangement = Arrangement.spacedBy((-8).dp)
//                    ) {
//                        friendList.take(5).forEach { friendState ->
//                            UserStateIcon(
//                                modifier = Modifier
//                                    .align(Alignment.CenterVertically)
//                                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
//                                iconUrl = friendState.value.iconUrl,
//                            )
//                        }
//                    }
//                } else {
//                    instants.ownerName.value?.let { ownerName ->
//                        Row(
//                            modifier = Modifier.fillMaxHeight().background(
//                                MaterialTheme.colorScheme.inverseOnSurface,
//                                MaterialTheme.shapes.medium
//                            )
//                                .clip(MaterialTheme.shapes.medium)
//                                .padding(horizontal = 8.dp),
//                            verticalAlignment = Alignment.CenterVertically,
//                        ) {
//                            Icon(
//                                modifier = Modifier.size(16.dp),
//                                imageVector = Icons.Default.Home,
//                                contentDescription = "OwnerIcon",
//                                tint = MaterialTheme.colorScheme.outline
//                            )
//                            Spacer(modifier = Modifier.width(2.dp))
//                            Text(
//                                text = ownerName,
//                                maxLines = 1,
//                                overflow = TextOverflow.Ellipsis,
//                                style = MaterialTheme.typography.labelSmall,
//                                color = MaterialTheme.colorScheme.outline
//                            )
//                        }
//                    }
//                }
//            }
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


