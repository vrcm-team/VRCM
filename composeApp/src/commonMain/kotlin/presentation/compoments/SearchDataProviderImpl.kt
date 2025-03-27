package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.SearchListPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.WorldSearchOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 用户搜索数据提供者实现
 * 连接SearchListPagerModel与GenericSearchList组件
 */
class UserSearchDataProvider(
    private val searchListPagerModel: SearchListPagerModel,
    coroutineScope: CoroutineScope
) : SearchDataProvider<IUser> {
    
    // 收集用户搜索数据流
    override val data: StateFlow<List<IUser>> =
        searchListPagerModel.userSearchList
    
    override suspend fun search(query: String, options: Map<String, String?>) {
        // 确保当前搜索类型是用户搜索
        searchListPagerModel.setSearchType(0)
        // 执行搜索
        searchListPagerModel.refreshSearchList(query)
    }
}

/**
 * 世界搜索数据提供者实现
 * 连接SearchListPagerModel与GenericSearchList组件
 */
class WorldSearchDataProvider(
    private val searchListPagerModel: SearchListPagerModel,
    coroutineScope: CoroutineScope
) : SearchDataProvider<WorldData> {
    
    // 收集世界搜索数据流
    override val data: StateFlow<List<WorldData>> =
        searchListPagerModel.worldSearchList
    
    override suspend fun search(query: String, options: Map<String, String?>) {
        // 确保当前搜索类型是世界搜索
        searchListPagerModel.setSearchType(1)
        
        // 解析搜索选项
        val currentOptions = searchListPagerModel.worldSearchOptions.value
        val newOptions = WorldSearchOptions(
            featured = options["featured"]?.toBoolean(),
            sortOption = options["sort"]?.let { SortOption.fromString(it) } ?: currentOptions.sortOption,
            resultsCount = options["n"]?.toIntOrNull() ?: currentOptions.resultsCount,
            order = options["order"] ?: currentOptions.order,
            offset = options["offset"]?.toIntOrNull() ?: currentOptions.offset,
            releaseStatus = options["releaseStatus"] ?: currentOptions.releaseStatus,
            tag = options["tag"] ?: currentOptions.tag,
            notag = options["notag"] ?: currentOptions.notag
        )
        
        // 更新搜索选项
        searchListPagerModel.updateWorldSearchOptions(newOptions)
        
        // 执行搜索
        searchListPagerModel.refreshSearchList(query)
    }
}

/**
 * 创建数据提供者的工厂方法
 */
fun createSearchDataProviders(
    searchListPagerModel: SearchListPagerModel,
    coroutineScope: CoroutineScope
): Pair<SearchDataProvider<IUser>, SearchDataProvider<WorldData>> {
    return Pair(
        UserSearchDataProvider(searchListPagerModel, coroutineScope),
        WorldSearchDataProvider(searchListPagerModel, coroutineScope)
    )
} 