package io.github.vrcmteam.vrcm.presentation.screens.home.dialog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.presentation.compoments.ITextField
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialog
import io.github.vrcmteam.vrcm.presentation.compoments.SharedDialogContainer
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

class UserStatusDialog(val currentUser: CurrentUserData) : SharedDialog {
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        SharedDialogContainer(
            key = "UserStatus",
            animatedVisibilityScope = animatedVisibilityScope,
        ) {
            var statusDescriptionText by remember { mutableStateOf(currentUser.statusDescription) }
            var currentStatus by remember { mutableStateOf(currentUser.status) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val focusManager = LocalFocusManager.current
                ITextField(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    imageVector = Icons.Default.Edit,
                    hintText = strings.fiendListPagerSearch,
                    textValue = statusDescriptionText,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    onValueChange = { statusDescriptionText = it })

                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StateItem(UserStatus.JoinMe)
                    StateItem(UserStatus.Active)
                    StateItem(UserStatus.AskMe)
                    StateItem(UserStatus.Busy)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateItem(
    userStatus: UserStatus
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
        ) {
            drawCircle(GameColor.Status.fromValue(userStatus))
        }
    }
}