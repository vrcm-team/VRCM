package  io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.SortOption

/**
 * 世界搜索选项数据类
 */
data class WorldSearchOptions(
    val featured: Boolean? = null,
    val sortOption: SortOption = SortOption.Popularity,
    val user: String? = null,
    val userId: String? = null,
    val resultsCount: Int = 50,
    val order: String = "descending",
    val offset: Int = 0,
    val releaseStatus: String? = null,
    val tag: String? = null,
    val notag: String? = null
)