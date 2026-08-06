package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.coroutines.launch

/**
 * 标准搜索列表组件
 * 封装GenericSearchList，固定tabs为用户、世界、模型，可选显示群组
 */
@Composable
fun StandardSearchList(
    key: String,
    searchText: String,
    updateSearchText: (String) -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    isRefreshing: Boolean? = null,
    doRefresh: (suspend () -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    advancedOptionsContent: @Composable ((SearchTabType) -> Unit)? = null,
    userList: List<IUser> = emptyList(),
    worldList: List<WorldData> = emptyList(),
    avatarList: List<AvatarData> = emptyList(),
    groupList: List<LimitedGroup> = emptyList(),
    // 目前群组功能可能还没实现，所以这里暂时留空
    modelContentBuilder: (LazyListScope.() -> Unit)? = null,
    groupContentBuilder: (LazyListScope.() -> Unit)? = null,
    // 是否显示群组标签（替换模型标签）
    includeGroups: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    totalItemsCount: Int = 0,
    isLoadingMore: Boolean = false,
    loadMoreFailed: Boolean = false,
    onRetryLoadMore: (() -> Unit)? = null,
) {
    // 固定的标签页列表：根据includeGroups决定是否显示群组标签
    val tabs = if (includeGroups) {
        listOf(strings.users, strings.worlds, strings.groups)
    } else {
        listOf(strings.users, strings.worlds, strings.avatars)
    }
    val coroutineScope = rememberCoroutineScope()
    val currentNavigator = currentNavigator
    val sharedSuffixKey = LocalSharedSuffixKey.current
    val hiddenModelCannotViewText = strings.hiddenModelCannotView
    val retryLoadMore = onRetryLoadMore
    val onUserClick: (IUser) -> Unit = { user ->
        // 处理用户点击，导航到用户资料页面
        coroutineScope.launch {
            currentNavigator push UserProfileScreen(
                userProfileVO = UserProfileVo(user),
                sharedSuffixKey = sharedSuffixKey
            )
        }
    }
    val hiddenWorldCannotViewText = strings.hiddenWorldCannotView
    val onWorldClick: (WorldData) -> Unit = { world: WorldData ->
        if (world.isHiddenWorld()) {
            coroutineScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Info(hiddenWorldCannotViewText))
            }
        } else {
            // 处理世界点击，导航到世界详情页面
            coroutineScope.launch {
                currentNavigator push WorldProfileScreen(
                    worldProfileVO = WorldProfileVo(world),
                    sharedSuffixKey = sharedSuffixKey
                )
            }
        }
    }
    val onAvatarClick: (AvatarData) -> Unit = { avatar ->
        // 处理模型点击，导航到模型详情页面
        if (avatar.releaseStatus == "hidden") {
            coroutineScope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Info(hiddenModelCannotViewText))
            }
        } else {
            coroutineScope.launch {
                currentNavigator push AvatarProfileScreen(
                    avatarProfileVo = AvatarProfileVo(avatar),
                    sharedSuffixKey = sharedSuffixKey
                )
            }
        }
    }
    val onGroupClick: (LimitedGroup) -> Unit = { group ->
        // 处理群组点击，导航到群组详情页面
        coroutineScope.launch {
            currentNavigator push GroupProfileScreen(
                groupProfileVo = GroupProfileVo(group),
                sharedSuffixKey = sharedSuffixKey
            )
        }
    }

    // 将索引转换为对应的SearchTabType
    val selectedTabType = SearchTabType.fromIndex(selectedTabIndex)

    GenericSearchList(
        key = key,
        searchText = searchText,
        updateSearchText = updateSearchText,
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        isRefreshing = isRefreshing,
        doRefresh = doRefresh,
        headerContent = headerContent,
        advancedOptionsContent = {
            // 使用枚举类型调用高级选项内容
            advancedOptionsContent?.invoke(selectedTabType)
        },
        onLoadMore = onLoadMore,
        totalItemsCount = totalItemsCount,
    ) { tabIndex ->
        when (tabIndex) {
            SearchTabType.USER.index -> { // 用户标签页
                renderUserItems(
                    users = userList,
                    onUserClick = onUserClick
                )
            }
            SearchTabType.WORLD.index -> { // 世界标签页
                renderWorldItems(
                    worlds = worldList,
                    onWorldClick = onWorldClick
                )
            }
            2 -> { // 第三个标签页：根据includeGroups决定显示模型还是群组
                if (includeGroups) {
                    renderGroupItems(
                        groups = groupList,
                        onGroupClick = onGroupClick
                    )
                    if (isLoadingMore || (loadMoreFailed && retryLoadMore != null)) {
                        item(key = "group-search-load-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = { retryLoadMore?.invoke() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.retry)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    renderAvatarItems(
                        avatars = avatarList,
                        onAvatarClick = onAvatarClick
                    )
                }
            }
        }
    }
}
