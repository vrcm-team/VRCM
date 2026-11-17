package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteEditRequest
import io.github.vrcmteam.vrcm.service.FavoriteEditService
import io.github.vrcmteam.vrcm.service.FavoriteService
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
    val editor: FavoriteEditService = koinInject()
    val scope = rememberCoroutineScope()
    val strings = strings
    val session by SharedFlowCentre.currentSession.collectAsState()
    val ownerId = session?.token?.userId
    var changingRequest by remember(ownerId, favoriteId, favoriteType, favoriteRecordId) {
        mutableStateOf<FavoriteEditRequest?>(null)
    }
    var failedRequest by remember(ownerId, favoriteId, favoriteType, favoriteRecordId) {
        mutableStateOf<FavoriteEditRequest?>(null)
    }
    var errorMessage by remember(ownerId, favoriteId, favoriteType, favoriteRecordId) { mutableStateOf<String?>(null) }
    var loadFailed by remember(ownerId, favoriteType) { mutableStateOf(false) }
    var loadAttempt by remember(ownerId, favoriteType) { mutableStateOf(0) }
    val isChanging = changingRequest != null
    val sheetState = rememberAppSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            targetValue != AppSheetValue.Hidden || changingRequest == null
        },
    )

    val favoritesByGroup by favoriteService.favoritesByGroup(favoriteType).collectAsState()
    LaunchedEffect(isVisible, ownerId, favoriteType, loadAttempt) {
        if (!isVisible) return@LaunchedEffect
        errorMessage = null
        failedRequest = null
        loadFailed = false
        if (favoritesByGroup.isNotEmpty()) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            authService.reTryAuth { favoriteService.loadFavoriteByGroup(favoriteType) }
        }
        loadFailed = result.isFailure
    }

    // 查找当前项目在哪个收藏组中
    val existingFavorite = remember(favoritesByGroup, favoriteId, favoriteRecordId) {
        findFavoriteForManagement(favoritesByGroup, favoriteId, favoriteRecordId)
    }
    val existingGroup = existingFavorite?.first

    // 已收藏的组名和ID
    val currentGroupName = existingGroup?.name
    val submit: (FavoriteEditRequest) -> Unit = submit@{ request ->
        val token = session?.token ?: return@submit
        if (changingRequest != null) return@submit
        changingRequest = request
        failedRequest = null
        errorMessage = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { editor.edit(token, request) }
                if (SharedFlowCentre.currentSession.value?.token?.userId != token.userId) return@launch
                val isMove = request.originalRecordId != null && request.targetGroupName != null
                val message = when {
                    request.targetGroupName == null -> if (result.isSuccess) strings.favoriteRemoveSuccess else strings.favoriteRemoveFailed
                    isMove -> if (result.isSuccess) strings.favoriteMoveSuccess else strings.favoriteMoveFailed
                    else -> if (result.isSuccess) strings.favoriteAddSuccess else strings.favoriteAddFailed
                }
                onConfirm(result.map { request.targetGroupName ?: currentGroupName.orEmpty() })
                if (result.isFailure) {
                    failedRequest = request
                    errorMessage = message
                } else {
                    SharedFlowCentre.toastText.emit(ToastText.Success(message))
                    sheetState.hide()
                    onDismiss()
                }
            } finally {
                changingRequest = null
            }
        }
    }
    fun request(groupName: String?) = FavoriteEditRequest(
        type = favoriteType,
        favoriteId = favoriteId,
        originalRecordId = existingFavorite?.second?.id,
        targetGroupName = groupName,
    )

    ABottomSheet(
        isVisible = isVisible,
        sheetState = sheetState,
        onDismissRequest = { if (!isChanging) onDismiss() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            AppText(
                text = when {
                    !allowGroupChange -> strings.remove
                    currentGroupName != null -> strings.favoriteMoveToAnotherGroup
                    else -> strings.selectFavoriteGroup
                },
                style = AppTheme.type.headline,
                modifier = Modifier.fillMaxWidth(),
                color = AppTheme.colors.label,
                textAlign = TextAlign.Center
            )
            AppSurface(
                color = AppTheme.colors.secondaryGroupedBackground,
                shape = AppShapes.l,
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
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadFailed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AppText(strings.favoritesGroupLoadFailed, color = AppTheme.colors.destructive)
                                    AppButton(strings.retry, onClick = { loadAttempt++ })
                                }
                            } else {
                                AppActivityIndicator()
                            }
                        }
                    } else {
                        val maxFavoritesPerGroup = favoriteService.getMaxFavoritesPerGroup(favoriteType)
                        val entries = favoritesByGroup.entries
                        entries.forEachIndexed { index, (group, favorites) ->
                            val isCurrentGroup = group.name == currentGroupName
                            val isLocal = group.ownerId == "local"
                            val isFull = !isLocal && favorites.size >= maxFavoritesPerGroup
                            val isPendingTarget = isChanging && changingRequest?.targetGroupName == group.name
                            val backgroundColor = if (isPendingTarget || (isCurrentGroup && !isChanging))
                                AppTheme.colors.tintSoft
                            else
                                AppTheme.colors.secondaryGroupedBackground

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(backgroundColor)
                                    .semantics { selected = isCurrentGroup }
                                    .clickable(
                                        enabled = allowGroupChange && !isChanging && !isCurrentGroup && !isFull,
                                        onClick = { submit(request(group.name)) },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Stable status slot keeps the row aligned while saving.
                                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                        if (isPendingTarget) {
                                            AppActivityIndicator(Modifier.size(20.dp))
                                        } else if (isCurrentGroup) {
                                            AppIcon(AppIcons.Check, contentDescription = null, tint = AppTheme.colors.tint)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        AppText(
                                            text = if (isLocal) strings.favoriteLocalGroup else group.displayName,
                                            style = AppTheme.type.headline.copy(
                                                fontWeight = if (isCurrentGroup) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isFull && !isCurrentGroup) AppTheme.colors.secondaryLabel else AppTheme.colors.label,
                                        )
                                        if (isPendingTarget) {
                                            AppText(
                                                text = strings.favoriteUpdating,
                                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                                style = AppTheme.type.caption1,
                                                color = AppTheme.colors.tint,
                                            )
                                        }
                                    }
                                    AppText(
                                        text = if (isLocal) favorites.size.toString() else "${favorites.size}/$maxFavoritesPerGroup",
                                        style = AppTheme.type.caption1,
                                        color = AppTheme.colors.secondaryLabel
                                    )
                                }
                            }
                            if (entries.size - 1 <= index) return@forEachIndexed
                            AppDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
                        }
                    }

                }

            }
            if (isChanging && changingRequest?.targetGroupName == null) {
                AppText(strings.favoriteUpdating, color = AppTheme.colors.secondaryLabel)
            }
            errorMessage?.let { message ->
                AppText(message, color = AppTheme.colors.destructive)
                failedRequest?.let { retry -> AppButton(strings.retry, onClick = { submit(retry) }) }
            }
            // 底部按钮
            if (currentGroupName != null) {
                AppButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChanging,
                    onClick = { submit(request(null)) },
                    style = AppButtonStyle.Tinted,
                    role = AppButtonRole.Destructive,
                ) {
                    if (isChanging && changingRequest?.targetGroupName == null) {
                        AppActivityIndicator(Modifier.size(20.dp))
                    }
                    AppText(strings.remove)
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
