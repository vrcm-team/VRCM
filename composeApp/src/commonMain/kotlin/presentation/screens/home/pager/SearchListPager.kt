package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.launch

object SearchListPager : Pager {
    override val index: Int
        get() = 2

    override val title: String
        @Composable
        get() = strings.fiendListPagerSearch

    override val icon: Painter
        @Composable
        get() = rememberVectorPainter(image = AppIcons.PersonSearch)

    @Composable
    override fun Content() {
        // 获取ViewModel
        val searchListPagerModel: SearchListPagerModel = koinScreenModel()
        val coroutineScope = rememberCoroutineScope()


        // 获取当前选中的标签索引
        val selectedTabIndex by searchListPagerModel.searchType.collectAsState()

        // 搜索文本
        val searchText by searchListPagerModel.searchText.collectAsState()

        // 高级搜索选项状态
        var showAdvancedOptions by remember { mutableStateOf(false) }

        // 标签页列表
        val tabs = listOf(strings.searchUsers, strings.searchWorlds, "群组", "模型")
        val currentNavigator = currentNavigator
        val sharedSuffixKey = LocalSharedSuffixKey.current
        val users by searchListPagerModel.userSearchList.collectAsState()
        val worlds by searchListPagerModel.worldSearchList.collectAsState()
        // 当搜索文本改变时执行搜索
        LaunchedEffect(searchText, selectedTabIndex) {
            searchListPagerModel.refreshSearchList()
        }
        GenericSearchList(
            key = "GenericSearchPager",
            searchText = searchText,
            updateSearchText = { newText ->
                searchListPagerModel.setSearchText(newText)
            },
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                coroutineScope.launch {
                    searchListPagerModel.setSearchType(index)
                }
            },
            advancedOptionsContent = {
                // 仅在世界搜索标签下显示高级选项
                if (selectedTabIndex == 1) {
                    val worldSearchOptions by searchListPagerModel.worldSearchOptions.collectAsState()

                    AdvancedOptionsPanel(
                        title = strings.worldSearchAdvancedOptions,
                        expanded = showAdvancedOptions,
                        onExpandToggle = { showAdvancedOptions = !showAdvancedOptions }
                    ) {
                        // 世界搜索高级选项UI
                        WorldSearchOptionsUI(
                            options = worldSearchOptions,
                            onOptionsChanged = { newOptions ->
                                coroutineScope.launch {
                                    searchListPagerModel.updateWorldSearchOptions(newOptions)
                                }
                            }
                        )
                    }
                }
            }
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    // 用户搜索结果

                    renderUserItems(
                        users = users,
                        onUserClick = { user ->
                            // 处理用户项点击
                            val navigator = currentNavigator
                            val sharedSuffixKey = sharedSuffixKey
                            if (navigator.size <= 1) {
                                navigator push UserProfileScreen(
                                    UserProfileVo(user),
                                    sharedSuffixKey
                                )
                            }
                        }
                    )
                }

                1 -> {
                    // 世界搜索结果
                    renderWorldItems(
                        worlds = worlds,
                        onWorldClick = { world ->
                            // 处理世界项点击
                            val navigator = currentNavigator
                            if (navigator.size <= 1) {
                                coroutineScope.launch {
                                    navigator push WorldProfileScreen(
                                        WorldProfileVo(world)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}




