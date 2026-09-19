package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.compoments.ListStateOverlay
import io.github.vrcmteam.vrcm.presentation.compoments.LocationCard
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.withContentTopInset
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 位置页的「群组房间」来源：自己加入的群组现在开着的房间，按群组分段显示。
 * 卡片沿用好友位置那一套 [LocationCard]，只是没有在场好友，点卡片直接进世界。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GroupInstancePager(
    isActive: () -> Boolean,
    model: GroupInstancePagerModel = koinViewModel(),
) {
    val state by model.state.collectAsState()
    val lazyListState = rememberLazyListState()
    val currentIsActive by rememberUpdatedState(isActive)
    val navigator = currentNavigator

    LaunchedEffect(model) { model.loadIfNeeded() }
    LaunchedEffect(lazyListState) {
        SharedFlowCentre.toPagerTop.collect {
            // 子任务承接手势取消，避免终止长期 Flow 监听。
            if (currentIsActive()) launch { lazyListState.animateScrollToFirst() }
        }
    }

    val openWorld: (GroupInstanceVo, String) -> Unit = { room, sharedSuffixKey ->
        navigator push WorldProfileScreen(
            worldProfileVO = WorldProfileVo(
                worldId = room.instants.worldId,
                worldName = room.instants.worldName,
                worldImageUrl = room.instants.worldImageUrl,
                worldDescription = room.instants.worldDescription,
                authorID = room.instants.worldAuthorId,
                authorName = room.instants.worldAuthorName,
                tags = room.instants.worldAuthorTag,
            ),
            location = room.location,
            sharedSuffixKey = sharedSuffixKey,
            sharedImageCacheKey = room.instants.worldImageUrl,
        )
    }

    // 和好友位置页用同一套内边距：顶部留白、底部把系统导航条和悬浮标签栏让出来
    val bottomNavigationPadding = if (
        LocalAppWindowWidthClass.current == AppWindowWidthClass.Compact
    ) 80.dp else 0.dp
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + bottomNavigationPadding
    val roomsByGroup = remember(state.instances) { state.instances.groupBy { it.groupId } }

    RefreshBox(
        refreshContainerOffsetY = 12.dp,
        isRefreshing = state.isLoading,
        doRefresh = { model.refresh() },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = bottomPadding,
            ).withContentTopInset(),
        ) {
            roomsByGroup.forEach { (groupId, rooms) ->
                item(key = "group-instance-title-$groupId") {
                    LocationTitle("${rooms.first().groupName}(${rooms.size})")
                }
                items(rooms, key = { it.location }) { room ->
                    GroupInstanceCard(room = room, onClickRoom = openWorld)
                }
            }
        }
        ListStateOverlay(
            isEmpty = state.instances.isEmpty(),
            isLoading = state.isLoading,
            // 第一次加载出结果前不说"没有房间"，那会儿只是还没查
            emptyMessage = strings.groupInstancesEmpty.takeIf { state.hasLoaded },
            errorMessage = strings.groupInstancesLoadFailed.takeIf { state.error != null },
            onRetry = model::refresh,
            bottomPadding = bottomPadding,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GroupInstanceCard(
    room: GroupInstanceVo,
    onClickRoom: (GroupInstanceVo, String) -> Unit,
) {
    // LocationCard 认的是 FriendLocation：群组房间没有在场好友名单，只包一层实例信息
    val location = remember(room) {
        FriendLocation(
            location = room.location,
            instants = mutableStateOf(room.instants),
            friends = mutableStateMapOf(),
        )
    }
    LocationCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        location = location,
        isSelected = false,
        onClickWorldImage = { sharedSuffixKey -> onClickRoom(room, sharedSuffixKey) },
        onClickLocationCard = { sharedSuffixKey -> onClickRoom(room, sharedSuffixKey) },
        content = {},
    )
}
