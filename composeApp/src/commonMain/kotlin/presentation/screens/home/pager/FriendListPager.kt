package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTabType
import io.github.vrcmteam.vrcm.presentation.compoments.StandardSearchList
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.GroupOptionsUI
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import org.jetbrains.compose.resources.painterResource
import vrcm.composeapp.generated.resources.Res
import vrcm.composeapp.generated.resources.star

object FriendListPager : Pager {

    override val index: Int
        get() = 1
    override val title: String
        @Composable
        get() = "Favorite"

    override val icon: Painter
        @Composable get() = painterResource(Res.drawable.star)

    @Composable
    override fun Content() {
        val friendListPagerModel: FriendListPagerModel = koinScreenModel()

        // 搜索文本
        val searchText by friendListPagerModel.searchText.collectAsState()

        // 选中的标签页索引
        val selectedTabIndex by friendListPagerModel.selectedTabIndex.collectAsState()

        // 获取好友组列表和世界组列表
        val friendFavoriteGroups by friendListPagerModel.friendFavoriteGroupsFlow.collectAsState()
        val worldFavoriteGroups by friendListPagerModel.worldFavoriteGroupsFlow.collectAsState()

        // 获取分组选项状态
        val friendGroupOptions by friendListPagerModel.friendGroupOptions.collectAsState()
        val worldGroupOptions by friendListPagerModel.worldGroupOptions.collectAsState()

        // 获取列表数据
        val filteredFriends by friendListPagerModel.friendList.collectAsState()
        val filteredWorlds by friendListPagerModel.worldList.collectAsState()
        val isRefreshing by friendListPagerModel.isRefreshing.collectAsState()
        // 初始加载缓存数据: 只有第一次默认为ture才会刷新一次
        LaunchedEffect(Unit) {
            if (isRefreshing) {
                friendListPagerModel.refreshCurrentTabCacheData(tabIndex = 0)
                friendListPagerModel.refreshCurrentTabCacheData(tabIndex = 1)
            }
        }

        StandardSearchList(
            key = title,
            searchText = searchText,
            updateSearchText = friendListPagerModel::setSearchText,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = friendListPagerModel::setSelectedTabIndex,
            isRefreshing = isRefreshing,
            doRefresh = friendListPagerModel::refreshCurrentTabCacheData,
            userList = filteredFriends,
            worldList = filteredWorlds,
            advancedOptionsContent = { tabType ->
                // 根据当前选中的标签页显示不同的高级选项
                when (tabType) {
                    SearchTabType.USER -> { // 好友标签页

                        GroupOptionsUI(
                            currentOptions = friendGroupOptions,
                            favoriteGroups = friendFavoriteGroups,
                            favoriteType = FavoriteType.Friend,
                            defaultText = strings.friendListPagerAllFriends,
                            total = filteredFriends.size,
                            onOptionsChanged = { newOptions ->
                                friendListPagerModel.updateFriendGroupOptions(newOptions)
                            },
                            getSelectedGroup = { it.selectedGroup },
                            updateOptions = { options, group -> options.copy(selectedGroup = group) }
                        )

                    }

                    SearchTabType.WORLD -> { // 世界标签页

                        GroupOptionsUI(
                            currentOptions = worldGroupOptions,
                            favoriteGroups = worldFavoriteGroups,
                            favoriteType = FavoriteType.World,
                            total = filteredWorlds.size,
                            defaultText = strings.friendListPagerAllWorlds,
                            onOptionsChanged = { newOptions ->
                                friendListPagerModel.updateWorldGroupOptions(newOptions)
                            },
                            getSelectedGroup = { it.selectedGroup },
                            updateOptions = { options, group -> options.copy(selectedGroup = group) }
                        )

                    }

                    else -> {}
                }
            }
        )
    }
}
