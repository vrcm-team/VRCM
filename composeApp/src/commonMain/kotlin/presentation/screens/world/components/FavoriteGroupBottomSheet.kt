package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 收藏组选择底部表单
 *
 * @param isVisible 是否显示底部表单
 * @param favoriteId 要收藏的目标ID
 * @param favoriteType 收藏类型
 * @param onDismiss 关闭回调
 * @param onConfirm 确认选择回调，参数为所选收藏组name的Result
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteGroupBottomSheet(
    isVisible: Boolean,
    favoriteId: String,
    favoriteType: FavoriteType,
    onDismiss: () -> Unit,
    onConfirm: (Result<String>) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val favoriteService: FavoriteService = koinInject()
    val authService: AuthService = koinInject()
    val scope = rememberCoroutineScope()
    val strings = strings

    // 加载收藏数据
    LaunchedEffect(favoriteId, favoriteType) {
        authService.reTryAuth {
            favoriteService.loadFavoriteByGroup(favoriteType)
        }
    }

    // 获取收藏数据
    val favoritesByGroup by favoriteService.favoritesByGroup(favoriteType).collectAsState()

    // 查找当前项目在哪个收藏组中
    val existingGroup = remember(favoritesByGroup, favoriteId) {
        favoritesByGroup.entries.firstOrNull { (_, favorites) ->
            favorites.any { it.favoriteId == favoriteId }
        }?.key
    }

    // 已收藏的组名和ID
    val currentGroupName = existingGroup?.name
    var isChanging by remember(favoriteId, favoriteType) { mutableStateOf(false) }
    // 添加或移动到新收藏组
    val onClickGroupItem = { groupName: String ->
        isChanging = true
        scope.launch(Dispatchers.IO) {
            // 如果已在某个组中，先移除
            authService.reTryAuth {
                doChangeFavoriteGroup(
                    favoriteService,
                    groupName,
                    currentGroupName,
                    strings,
                    favoriteId,
                    favoriteType
                )
            }.map { groupName }
                .let { result ->
                    isChanging = false
                    onConfirm(result)
                }
        }
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    ABottomSheet(
        isVisible = isVisible,
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(
                text = if (currentGroupName != null) strings.favoriteMoveToAnotherGroup else strings.selectFavoriteGroup,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 收藏组列表
                    if (favoritesByGroup.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val maxFavoritesPerGroup = remember { favoriteService.getMaxFavoritesPerGroup(favoriteType) }
                        val entries = favoritesByGroup.entries
                        entries.forEachIndexed { index, (group, favorites) ->
                            val isCurrentGroup = group.name == currentGroupName
                            val backgroundColor = if (isCurrentGroup)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLowest

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(backgroundColor)
                                    .clickable(!isChanging && !isCurrentGroup) { onClickGroupItem(group.name) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = "已收藏",
                                        tint = if (isCurrentGroup) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    )
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = group.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (isCurrentGroup) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${favorites.size}/${maxFavoritesPerGroup}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (entries.size - 1 <= index) return@forEachIndexed
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
                        }
                    }

                }

            }
            // 底部按钮
            if (currentGroupName != null) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChanging,
                    onClick = { onClickGroupItem(currentGroupName) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(strings.remove)
                }
            }
        }
    }
}

private suspend fun doChangeFavoriteGroup(
    favoriteService: FavoriteService,
    groupName: String?,
    currentGroupName: String?,
    strings: LocaleStrings,
    favoriteId: String,
    favoriteType: FavoriteType,
): Result<Unit> = runCatching {
    val favorite = favoriteService.getFavoriteByFavoriteId(favoriteType, favoriteId)

    // 移除旧组
    suspend fun removeFavorite() {
        if (favorite != null) {
            runCatching {
                favoriteService.removeFavorite(id = favorite.id)
            }.onSuccess {
                if (groupName != currentGroupName) return@onSuccess
                val successMessage = strings.favoriteRemoveSuccess
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            }.onFailure {
                val errorMessage = "${strings.favoriteRemoveFailed}: ${it.message}"
                SharedFlowCentre.toastText.emit(ToastText.Error(errorMessage))
            }.getOrThrow()
        }
    }

    suspend fun addFavorite() {
        // 添加到新组
        if (groupName != currentGroupName && groupName != null) {
            val isMove = favorite != null
            runCatching {
                favoriteService.addFavorite(
                    favoriteId = favoriteId,
                    favoriteType = favoriteType,
                    groupName = groupName,
                )
            }.onSuccess {
                val successMessage = if (isMove) strings.favoriteMoveSuccess else strings.favoriteAddSuccess
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            }.onFailure {
                val errorMessage =
                    "${if (isMove) strings.favoriteMoveFailed else strings.favoriteAddFailed}: ${it.message}"
                SharedFlowCentre.toastText.emit(ToastText.Error(errorMessage))
            }.getOrThrow()
        }
    }

    // 如果本地的分组移动时应该尝试添加，添加成功以后再删除本地的，如果是远端的分组应该先删除再添加
    val oldIsLocal = favorite?.id?.let(favoriteService::parseLocalFavoriteId)?.first == true

    if (oldIsLocal) {
        addFavorite()
        removeFavorite()
    } else {
        removeFavorite()
        addFavorite()
    }

    favoriteService.loadFavoriteByGroup(favoriteType).getOrThrow()
}


