package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            var statusDescriptionText by remember { mutableStateOf(currentUser.statusDescription) }
            val (currentStatus, setCurrentStatus) = remember { mutableStateOf(currentUser.status) }
            Column(
                modifier = Modifier.padding(8.dp).glideBack { close() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val focusManager = LocalFocusManager.current
                ITextField(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    imageVector = Icons.Default.Edit,
                    hintText = strings.fiendListPagerSearch,
                    textValue = statusDescriptionText,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    onValueChange = { statusDescriptionText = it }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StateItem(
                        userStatus = UserStatus.JoinMe,
                        isSelected = currentStatus == UserStatus.JoinMe,
                        onClick = setCurrentStatus
                    )
                    StateItem(
                        userStatus = UserStatus.Active,
                        isSelected = currentStatus == UserStatus.Active,
                        onClick = setCurrentStatus
                    )
                    StateItem(
                        userStatus = UserStatus.AskMe,
                        isSelected = currentStatus == UserStatus.AskMe,
                        onClick = setCurrentStatus
                    )
                    StateItem(
                        userStatus = UserStatus.Busy,
                        isSelected = currentStatus == UserStatus.Busy,
                        onClick = setCurrentStatus
                    )
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
    override fun close()  = onConfirmClick()
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