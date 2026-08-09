package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.screens.avatar.AvatarProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.OfficialLinkContent
import io.github.vrcmteam.vrcm.service.OfficialLinkInbox
import io.github.vrcmteam.vrcm.service.OfficialLinkService
import io.github.vrcmteam.vrcm.service.OfficialLinkType
import org.koin.compose.koinInject

@Composable
internal fun OfficialLinkPrompt(
    navigator: AppNavigator,
    inbox: OfficialLinkInbox,
) {
    val clipboard = LocalClipboardManager.current
    val service: OfficialLinkService = koinInject()
    val locale = strings
    val scope = rememberCoroutineScope()
    val incomingRequest by inbox.pendingRequest.collectAsState()
    var foregroundGeneration by remember { mutableIntStateOf(0) }
    var lastInspectedTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    val isAuthenticated = navigator.items.any { it is HomeScreen }
    val controller = remember(service, scope, inbox, navigator) {
        OfficialLinkPromptController(
            scope = scope,
            resolve = service::resolve,
            onResolved = { navigator push it.toRoute() },
            onExternalConsumed = inbox::consume,
            initialInspectedTargetKey = lastInspectedTargetKey,
            onTargetInspected = { lastInspectedTargetKey = it },
        )
    }
    val promptState by controller.state.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        foregroundGeneration++
    }

    SideEffect {
        controller.updateAuthentication(isAuthenticated)
    }

    LaunchedEffect(
        controller,
        foregroundGeneration,
        isAuthenticated,
        incomingRequest?.id,
    ) {
        if (foregroundGeneration == 0 || !isAuthenticated || incomingRequest != null) {
            return@LaunchedEffect
        }
        val clipboardText = runCatching { clipboard.getText()?.text }.getOrNull() ?: return@LaunchedEffect
        controller.inspectClipboard(clipboardText)
    }

    LaunchedEffect(controller, incomingRequest?.id, isAuthenticated) {
        val request = incomingRequest ?: return@LaunchedEffect
        if (!isAuthenticated) return@LaunchedEffect
        controller.openExternal(request)
    }

    when (val state = promptState) {
        OfficialLinkPromptState.Idle -> Unit
        is OfficialLinkPromptState.ClipboardConfirmation -> ClipboardConfirmationDialog(
            targetType = state.operation.target.type,
            locale = locale,
            onConfirm = controller::confirmClipboard,
            onDismiss = controller::dismiss,
        )
        is OfficialLinkPromptState.Resolving -> ResolvingOfficialLinkDialog(locale)
        is OfficialLinkPromptState.Failure -> OfficialLinkFailureDialog(
            retryAvailable = state.operation != null,
            locale = locale,
            onRetry = controller::retry,
            onDismiss = controller::dismiss,
        )
    }
}

@Composable
private fun ClipboardConfirmationDialog(
    targetType: OfficialLinkType,
    locale: LocaleStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Link, contentDescription = null) },
        title = { Text(locale.officialLinkPromptTitle) },
        text = {
            Text(
                locale.officialLinkPromptMessage.replace(
                    "%s",
                    targetType.localizedName(locale),
                )
            )
        },
        confirmButton = {
            Button(
                modifier = Modifier.widthIn(min = 96.dp),
                onClick = onConfirm,
            ) {
                Text(locale.officialLinkOpen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(locale.cancel)
            }
        },
    )
}

@Composable
private fun ResolvingOfficialLinkDialog(locale: LocaleStrings) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(AppIcons.Link, contentDescription = null) },
        title = { Text(locale.officialLinkPromptTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(locale.loading)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun OfficialLinkFailureDialog(
    retryAvailable: Boolean,
    locale: LocaleStrings,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Link, contentDescription = null) },
        title = { Text(locale.officialLinkPromptTitle) },
        text = {
            Text(
                text = locale.officialLinkOpenFailed,
                color = MaterialTheme.colorScheme.error,
            )
        },
        confirmButton = {
            if (retryAvailable) {
                Button(onClick = onRetry) {
                    Text(locale.retry)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(locale.cancel)
            }
        },
    )
}

private fun OfficialLinkType.localizedName(locale: LocaleStrings): String = when (this) {
    OfficialLinkType.User -> locale.officialLinkTypeUser
    OfficialLinkType.World -> locale.officialLinkTypeWorld
    OfficialLinkType.Group -> locale.officialLinkTypeGroup
    OfficialLinkType.Avatar -> locale.officialLinkTypeAvatar
}

private fun OfficialLinkContent.toRoute() = when (this) {
    is OfficialLinkContent.User -> UserProfileScreen(UserProfileVo(data))
    is OfficialLinkContent.World -> WorldProfileScreen(WorldProfileVo(data))
    is OfficialLinkContent.Group -> GroupProfileScreen(GroupProfileVo(data))
    is OfficialLinkContent.Avatar -> AvatarProfileScreen(AvatarProfileVo(data))
}
