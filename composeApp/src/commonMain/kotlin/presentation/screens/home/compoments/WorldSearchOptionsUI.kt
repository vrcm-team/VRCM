package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenuItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPopUpButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRadioButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSlider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.AppToggle
import io.github.vrcmteam.vrcm.presentation.screens.home.data.WorldSearchOptions
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/**
 * 世界搜索高级选项UI组件
 * 
 * 用于显示和编辑世界搜索的高级选项
 */
@Composable
fun WorldSearchOptionsUI(
    options: WorldSearchOptions,
    onOptionsChanged: (WorldSearchOptions) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 精选世界选项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppText(
                text = strings.worldSearchFeaturedOnly,
                style = AppTheme.type.subheadline
            )
            AppToggle(
                checked = options.featured ?: false,
                onCheckedChange = { 
                    onOptionsChanged(options.copy(featured = if (it) true else null))
                }
            )
        }
        
        // 排序方式选项
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            AppText(
                text = strings.worldSearchSortBy,
                style = AppTheme.type.subheadline
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 排序下拉菜单
            var expandSortMenu by remember { mutableStateOf(false) }
            AppPopUpButton(
                value = options.sortOption.displayName,
                expanded = expandSortMenu,
                onExpandedChange = { expandSortMenu = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                SortOption.entries.forEach { sortOption ->
                    AppMenuItem(
                        text = { AppText(sortOption.displayName) },
                        onClick = {
                            onOptionsChanged(options.copy(sortOption = sortOption))
                            expandSortMenu = false
                        }
                    )
                }
            }
        }
        
        // 排序顺序选项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = strings.worldSearchOrder,
                style = AppTheme.type.subheadline
            )
            Spacer(modifier = Modifier.weight(1f))
            
            // 单选按钮组
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppRadioButton(
                        selected = options.order == "descending",
                        onClick = {
                            onOptionsChanged(options.copy(order = "descending"))
                        }
                    )
                    AppText(strings.worldSearchDescending)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppRadioButton(
                        selected = options.order == "ascending",
                        onClick = {
                            onOptionsChanged(options.copy(order = "ascending"))
                        }
                    )
                    AppText(strings.worldSearchAscending)
                }
            }
        }
        
        // 显示数量选项
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            AppText(
                text = strings.worldSearchResultCount,
                style = AppTheme.type.subheadline
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppSlider(
                value = options.resultsCount.toFloat(),
                onValueChange = { 
                    val newValue = it.toInt()
                    if (newValue != options.resultsCount) {
                        onOptionsChanged(options.copy(resultsCount = newValue))
                    }
                },
                valueRange = 10f..100f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )
            AppText(
                text = strings.worldSearchResultsFormat.replaceFirst("%d", options.resultsCount.toString()),
                style = AppTheme.type.caption1
            )
        }
    }
}


/**
 * 排序选项枚举
 */
enum class SortOption(val value: String) {
    Popularity("popularity"),
    Heat("heat"),
    Trust("trust"),
    Shuffle("shuffle"),
    Random("random"),
    Favorites("favorites"),
    Created("created"),
    Updated("updated"),
    Relevance("relevance"),
    Name("name");

    /**
     * 获取排序选项的本地化显示名称
     */
    val displayName: String
        @Composable
        get() {
            return when (this) {
                Popularity -> strings.worldSearchSortPopularity
                Heat -> strings.worldSearchSortHeat
                Trust -> strings.worldSearchSortTrust
                Shuffle -> strings.worldSearchSortShuffle
                Random -> strings.worldSearchSortRandom
                Favorites -> strings.worldSearchSortFavorites
                Created -> strings.worldSearchSortCreated
                Updated -> strings.worldSearchSortUpdated
                Relevance -> strings.worldSearchSortRelevance
                Name -> strings.worldSearchSortName
            }
        }
}