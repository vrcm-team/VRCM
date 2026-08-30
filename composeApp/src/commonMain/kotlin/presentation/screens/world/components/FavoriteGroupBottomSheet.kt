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
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 收藏组选择底部表单
 *
 * @param isVisible 是否显示底部表单
 * @param favoriteId 要收藏的目标ID
 * @param favoriteType 收藏类型
 * @param favoriteRecordId 已存在收藏记录的ID；隐藏世界无法提供目标ID时使用
 * @param allowGroupChange 是否允许新增或移动到其他收藏组
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
    favoriteRecordId: String? = null,
    allowGroupChange: Boolean = true,
) {
    val favoriteService: FavoriteService = koinInject()
    val authService: AuthService = koinInject()
    val scope = rememberCoroutineScope()
    val strings = strings
    var isChanging by remember(favoriteId, favoriteType, favoriteRecordId) { mutableStateOf(false) }
    val latestIsChanging = rememberUpdatedState(isChanging)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            targetValue != SheetValue.Hidden || !latestIsChanging.value
        },
    )

    // 加载收藏数据
    LaunchedEffect(isVisible, favoriteId, favoriteType, favoriteRecordId) {
        if (!isVisible) return@LaunchedEffect
        val result = authService.reTryAuth {
            favoriteService.loadFavoriteByGroup(favoriteType)
        }
        result.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            SharedFlowCentre.toastText.emit(
                ToastText.Error("${strings.favoritesGroupLoadFailed}: ${error.message.orEmpty()}"),
            )
        }
    }

    // 获取收藏数据
    val favoritesByGroup by favoriteService.favoritesByGroup(favoriteType).collectAsState()

    // 查找当前项目在哪个收藏组中
    val existingFavorite = remember(favoritesByGroup, favoriteId, favoriteRecordId) {
        findFavoriteForManagement(favoritesByGroup, favoriteId, favoriteRecordId)
    }
    val existingGroup = existingFavorite?.first

    // 已收藏的组名和ID
    val currentGroupName = existingGroup?.name
    // 添加或移动到新收藏组
    val onClickGroupItem = onClick@{ groupName: String ->
        if (isChanging) return@onClick
        isChanging = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    // 如果已在某个组中，先移除
                    authService.reTryAuth {
                        doChangeFavoriteGroup(
                            favoriteService,
                            groupName,
                            currentGroupName,
                            strings,
                            favoriteId,
                            favoriteType,
                            existingFavorite = existingFavorite,
                            allowGroupChange = allowGroupChange,
                        )
                    }.map { groupName }
                }
            } finally {
                isChanging = false
            }
            onConfirm(result)
            sheetState.hide()
            onDismiss()
        }
    }

    ABottomSheet(
        isVisible = isVisible,
        sheetState = sheetState,
        onDismissRequest = { if (!isChanging) onDismiss() }
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
                text = when {
                    !allowGroupChange -> strings.remove
                    currentGroupName != null -> strings.favoriteMoveToAnotherGroup
                    else -> strings.selectFavoriteGroup
                },
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
                                    .clickable(
                                        enabled = allowGroupChange && !isChanging && !isCurrentGroup,
                                        onClick = { onClickGroupItem(group.name) },
                                    ),
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

internal fun findFavoriteForManagement(
    favoritesByGroup: Map<FavoriteGroupData, List<FavoriteData>>,
    favoriteId: String,
    favoriteRecordId: String?,
): Pair<FavoriteGroupData, FavoriteData>? {
    val entries = favoritesByGroup.entries
        .asSequence()
        .flatMap { (group, favorites) -> favorites.asSequence().map { group to it } }
        .toList()

    return if (favoriteRecordId != null) {
        entries.firstOrNull { (_, favorite) -> favorite.id == favoriteRecordId }
    } else {
        entries.firstOrNull { (_, favorite) -> favorite.favoriteId == favoriteId }
    }
}

private suspend fun doChangeFavoriteGroup(
    favoriteService: FavoriteService,
    groupName: String?,
    currentGroupName: String?,
    strings: LocaleStrings,
    favoriteId: String,
    favoriteType: FavoriteType,
    existingFavorite: Pair<FavoriteGroupData, FavoriteData>?,
    allowGroupChange: Boolean,
): Result<Unit> = try {
    val favorite = existingFavorite?.second

    // 移除旧组
    suspend fun removeFavorite() {
        if (favorite != null) {
            try {
                favoriteService.removeFavorite(id = favorite.id)
                if (groupName == currentGroupName) {
                    val successMessage = strings.favoriteRemoveSuccess
                    SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val errorMessage = "${strings.favoriteRemoveFailed}: ${error.message}"
                SharedFlowCentre.toastText.emit(ToastText.Error(errorMessage))
                throw error
            }
        }
    }

    suspend fun addFavorite() {
        // 添加到新组
        if (allowGroupChange && groupName != currentGroupName && groupName != null) {
            val isMove = favorite != null
            try {
                favoriteService.addFavorite(
                    favoriteId = favoriteId,
                    favoriteType = favoriteType,
                    groupName = groupName,
                )
                val successMessage = if (isMove) strings.favoriteMoveSuccess else strings.favoriteAddSuccess
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val errorMessage =
                    "${if (isMove) strings.favoriteMoveFailed else strings.favoriteAddFailed}: ${error.message}"
                SharedFlowCentre.toastText.emit(ToastText.Error(errorMessage))
                throw error
            }
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
    Result.success(Unit)
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    Result.failure(error)
}
