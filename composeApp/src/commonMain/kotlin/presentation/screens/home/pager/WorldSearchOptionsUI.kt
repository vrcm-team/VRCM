package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.screens.home.data.WorldSearchOptions
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/**
 * 世界搜索高级选项UI组件
 * 
 * 用于显示和编辑世界搜索的高级选项
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            Text(
                text = strings.worldSearchFeaturedOnly,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
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
            Text(
                text = strings.worldSearchSortBy,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 排序下拉菜单
            var expandSortMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandSortMenu,
                onExpandedChange = { expandSortMenu = it }
            ) {
                OutlinedTextField(
                    value = options.sortOption.displayName,
                    onValueChange = {},
                    shape = MaterialTheme.shapes.medium,
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandSortMenu)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                
                ExposedDropdownMenu(
                    shape = MaterialTheme.shapes.medium,
                    expanded = expandSortMenu,
                    onDismissRequest = { expandSortMenu = false }
                ) {
                    SortOption.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = { Text(sortOption.displayName) },
                            onClick = {
                                onOptionsChanged(options.copy(sortOption = sortOption))
                                expandSortMenu = false
                            }
                        )
                    }
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
            Text(
                text = strings.worldSearchOrder,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            
            // 单选按钮组
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = options.order == "descending",
                        onClick = {
                            onOptionsChanged(options.copy(order = "descending"))
                        }
                    )
                    Text(strings.worldSearchDescending)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = options.order == "ascending",
                        onClick = {
                            onOptionsChanged(options.copy(order = "ascending"))
                        }
                    )
                    Text(strings.worldSearchAscending)
                }
            }
        }
        
        // 显示数量选项
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = strings.worldSearchResultCount,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
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
            Text(
                text = strings.worldSearchResultsFormat.replaceFirst("%d", options.resultsCount.toString()),
                style = MaterialTheme.typography.bodySmall
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