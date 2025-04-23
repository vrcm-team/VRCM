package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
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
import io.github.vrcmteam.vrcm.presentation.compoments.ITextField
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialogContainer
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.extensions.koinScreenModelByLastItem
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreenModel
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

class UserStatusDialog(
    private val currentUser: CurrentUserData,
    private val onConfirmClick: () -> Unit,
) : SharedDialog {
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {

        val homeScreenModel: HomeScreenModel = koinScreenModelByLastItem()
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
                        currentStatus = currentStatus,
                        onStatusSelected = setCurrentStatus,
//                        modifier = Modifier.weight(0.1f)
                    )

                    // 状态描述输入框
                    StatusInput(statusDescriptionText, setStatusDescriptionText)

                }

                // 更新状态按钮
                Button(
                    onClick = {
                        homeScreenModel.updateUserStatus(currentStatus, statusDescriptionText)
                        close()
                    },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Text(strings.homeUpdateStatus)
                }
            }
        }
    }

    @Composable
    private fun RowScope.StatusInput(
        statusDescriptionText: String,
        setStatusDescriptionText: (String) -> Unit,
    ) {
        var historyExpanded by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        ITextField(
            modifier = Modifier.weight(1f),
            leadingIcon = {
                Icon(
                    imageVector = if (historyExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = "history status",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { historyExpanded = true }
                )
                // 历史状态描述下拉菜单
                DropdownMenu(
                    shape = MaterialTheme.shapes.medium,
                    expanded = historyExpanded,
                    onDismissRequest = { historyExpanded = false }
                ) {
                    // 显示历史状态描述
                    currentUser.statusHistory.take(10).forEach { historyStatus ->
                        DropdownMenuItem(
                            modifier = Modifier.clip(MaterialTheme.shapes.medium),
                            text = { Text(historyStatus) },
                            onClick = {
                                setStatusDescriptionText(historyStatus)
                                historyExpanded = false
                            },
                            trailingIcon = {
                                if (historyStatus == statusDescriptionText) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
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
                Text(
                    text = "${statusDescriptionText.length}/32",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (statusDescriptionText.length >= 32)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }

    override fun close() = onConfirmClick()
}

@Composable
fun StatusDropdownMenu(
    currentStatus: UserStatus,
    onStatusSelected: (UserStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
    ) {
        OutlinedCard(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前状态的圆形指示器
                Canvas(modifier = Modifier.size(16.dp)) {
                    drawCircle(
                        color = GameColor.Status.fromValue(currentStatus),
                        style = Fill
                    )
                }
                Icon(
                    imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = "select status"
                )
            }
        }

        DropdownMenu(
            shape = MaterialTheme.shapes.medium,
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val userStatuses = remember { UserStatus.entries.filter { it != UserStatus.Offline && it != currentStatus } }
            userStatuses.forEach { status ->
                DropdownMenuItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
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
                            Text(text = status.value)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateItem(
    userStatus: UserStatus,
    isSelected: Boolean = false,
    onClick: (UserStatus) -> Unit = {},
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = userStatus.value)
            }
        },
        state = rememberTooltipState()
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