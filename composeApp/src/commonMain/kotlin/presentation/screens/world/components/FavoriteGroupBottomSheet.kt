package io.github.vrcmteam.vrcm.presentation.screens.world.components

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
import kotlinx.coroutines.CoroutineScope
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
 * @param onConfirm 确认选择回调，参数为所选收藏组ID的Result
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteGroupBottomSheet(
    isVisible: Boolean,
    favoriteId: String,
    favoriteType: FavoriteType,
    onDismiss: () -> Unit,
    onConfirm: (Result<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val favoriteService: FavoriteService = koinInject()
    val authService: AuthService = koinInject()
    val scope = rememberCoroutineScope()
    val strings = strings

    // 加载收藏数据
    LaunchedEffect(favoriteId, favoriteType) {
        favoriteService.loadFavoriteByGroup(favoriteType)
    }

    // 获取收藏数据
    val favoritesByGroup by
    favoriteService.favoritesByGroup(favoriteType).collectAsState()

    // 查找当前项目在哪个收藏组中
    val existingGroup = remember(favoritesByGroup) {
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
            }
                .map { groupName }
                .let { result ->
                    launch { sheetState.hide() }.invokeOnCompletion {
                        isChanging = false
                        onConfirm(result)
                    }
                }
        }
    }

    ABottomSheet(
        isVisible = isVisible,
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = if (currentGroupName != null) strings.favoriteMoveToAnotherGroup else strings.selectFavoriteGroup,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )


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
                favoritesByGroup.entries.forEach { (group, favorites) ->
                    val isCurrentGroup = group.name == currentGroupName
                    val backgroundColor = if (isCurrentGroup)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { if (!isChanging && !isCurrentGroup) onClickGroupItem(group.name) },
                        colors = CardDefaults.cardColors(
                            containerColor = backgroundColor
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = "已收藏",
                                tint = if (isCurrentGroup) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.padding(start = 8.dp)
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
                                text = "${favorites.size}/${favoriteService.getMaxFavoritesPerGroup(favoriteType)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        elevation = ButtonDefaults.buttonElevation(2.dp)
                    ) {
                        Text(strings.remove)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private suspend fun CoroutineScope.doChangeFavoriteGroup(
    favoriteService: FavoriteService,
    groupName: String?,
    currentGroupName: String?,
    strings: LocaleStrings,
    favoriteId: String,
    favoriteType: FavoriteType,
): Result<Unit> = runCatching {
    val favorite = favoriteService.getFavoriteByFavoriteId(favoriteType, favoriteId)
    // 移除旧组
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
            val errorMessage = "${if (isMove) strings.favoriteMoveFailed else strings.favoriteAddFailed}: ${it.message}"
            SharedFlowCentre.toastText.emit(ToastText.Error(errorMessage))
        }.getOrThrow()
    }
    favoriteService.loadFavoriteByGroup(favoriteType)
}

