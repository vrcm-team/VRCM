package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import org.koin.compose.viewmodel.koinViewModel
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

class UserStatusDialog(
    private val currentUser: CurrentUserData,
    private val sharedUserId: String = currentUser.id,
    private val onConfirmClick: () -> Unit,
) : SharedDialog {
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {

        val homeScreenModel: HomeScreenModel = koinViewModel()
        SharedDialogContainer(
            key = "UserStatus",
            animatedVisibilityScope = animatedVisibilityScope,
        ) {
            val (statusDescriptionText, setStatusDescriptionText) = remember { mutableStateOf(currentUser.statusDescription) }
            val (currentStatus, setCurrentStatus) = remember { mutableStateOf(currentUser.status) }


            Column(
                modifier = Modifier.padding(8.dp).glideBack { close() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 将ITextField和StatusDropdownMenu放在同一行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // 状态下拉菜单
                    StatusDropdownMenu(
                        id = sharedUserId,
                        currentStatus = currentStatus,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onStatusSelected = setCurrentStatus,
//                        modifier = Modifier.weight(0.1f)
                    )

                    // 状态描述输入框
                    StatusInput(
                        id = sharedUserId,
                        statusDescriptionText = statusDescriptionText,
                        animatedVisibilityScope = animatedVisibilityScope,
                        setStatusDescriptionText = setStatusDescriptionText
                    )

                }

                // 更新状态按钮
                AppButton(
                    onClick = {
                        homeScreenModel.updateUserStatus(currentStatus, statusDescriptionText)
                        close()
                    },
                    modifier = Modifier.padding(8.dp),
                    style = AppButtonStyle.Prominent,
                ) {
                    AppText(strings.homeUpdateStatus)
                }
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun RowScope.StatusInput(
        id: String,
        statusDescriptionText: String,
        animatedVisibilityScope: AnimatedVisibilityScope,
        setStatusDescriptionText: (String) -> Unit,
    ) {
        var historyExpanded by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        ITextField(
            modifier = Modifier.weight(1f)
                .sharedBoundsBy(
                    key = "${id}UserStatusText",
                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                    animatedVisibilityScope = animatedVisibilityScope
                ),
            leadingIcon = {
                AppIcon(
                    imageVector = if (historyExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = "history status",
                    tint = AppTheme.colors.tint,
                    modifier = Modifier
                        .clip(AppShapes.m)
                        .clickable { historyExpanded = true }
                )
                // 历史状态描述下拉菜单
                AppMenu(
                    expanded = historyExpanded,
                    onDismissRequest = { historyExpanded = false }
                ) {
                    // 显示历史状态描述
                    currentUser.statusHistory.take(10).forEach { historyStatus ->
                        AppMenuItem(
                            modifier = Modifier.clip(AppShapes.m),
                            text = { AppText(historyStatus) },
                            onClick = {
                                setStatusDescriptionText(historyStatus)
                                historyExpanded = false
                            },
                            trailingIcon = {
                                if (historyStatus == statusDescriptionText) {
                                    AppIcon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = "checked",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            },
            hintText = strings.homeStatusEdit,
            textValue = statusDescriptionText,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            onValueChange = {
                // 限制输入最多32个字符
                if (it.length <= 32) {
                    setStatusDescriptionText(it)
                }
            },
            supportingText = {
                AppText(
                    text = "${statusDescriptionText.length}/32",
                    style = AppTheme.type.caption2Emphasized,
                    color = if (statusDescriptionText.length >= 32)
                        AppTheme.colors.destructive
                    else
                        AppTheme.colors.secondaryLabel
                )
            }
        )
    }

    override fun close() = onConfirmClick()
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StatusDropdownMenu(
    id: String,
    modifier: Modifier = Modifier,
    currentStatus: UserStatus,
    onStatusSelected: (UserStatus) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
    ) {
        AppCard(
            modifier = Modifier
                .clip(AppShapes.m)
                .clickable { expanded = true }
                .sharedBoundsBy(
                    key = "${id}UserStatusIcon",
                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                    animatedVisibilityScope = animatedVisibilityScope
                )

        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前状态的圆形指示器
                Canvas(
                    modifier = Modifier
                        .size(16.dp)
                ) {
                    drawCircle(
                        color = GameColor.Status.fromValue(currentStatus),
                        style = Fill
                    )
                }
                AppIcon(
                    imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = "select status"
                )
            }
        }

        AppMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val userStatuses =
                remember { UserStatus.entries.filter { it != UserStatus.Offline && it != currentStatus } }
            userStatuses.forEach { status ->
                AppMenuItem(
                    modifier = Modifier.clip(AppShapes.m),
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 状态圆形指示器
                            Canvas(modifier = Modifier.size(16.dp)) {
                                drawCircle(
                                    color = GameColor.Status.fromValue(status),
                                    style = Fill
                                )
                            }
                            // 状态文本
                            AppText(text = status.value)
                        }
                    },
                    onClick = {
                        onStatusSelected(status)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StateItem(
    userStatus: UserStatus,
    isSelected: Boolean = false,
    onClick: (UserStatus) -> Unit = {},
) {
    AppTooltipBox(
        tooltip = { AppText(text = userStatus.value) },
    ) {
        Canvas(
            modifier = Modifier
                .size(24.dp)
                .clickable { onClick(userStatus) }
        ) {
            val drawStyle = if (isSelected) Fill else Stroke(width = 2f)
            drawCircle(
                color = GameColor.Status.fromValue(userStatus),
                style = drawStyle
            )
        }
    }
}
