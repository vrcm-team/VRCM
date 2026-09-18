package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.ImageInviteNotInInstanceException

@Composable
internal fun ImageInviteDialog(
    state: ImageInviteUiState,
    targetName: String,
    onSend: () -> Unit,
    onRetryPreparation: () -> Unit,
    onChooseAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is ImageInviteUiState.Idle ||
        state is ImageInviteUiState.Selecting ||
        state is ImageInviteUiState.Sent
    ) return
    val busy = state is ImageInviteUiState.Preparing || state is ImageInviteUiState.Sending
    val selection = state.selectionOrNull()
    val statusText = when (state) {
        is ImageInviteUiState.Preparing -> strings.imageInvitePreparing
        is ImageInviteUiState.Ready -> strings.imageInviteReady
        is ImageInviteUiState.Sending -> strings.imageInviteSending
        is ImageInviteUiState.Failed -> when {
            state.error is ImageInviteNotInInstanceException -> strings.profileInviteNotInInstance
            state.stage == ImageInviteFailureStage.Preparation -> strings.imageInvitePrepareFailed
            else -> strings.imageInviteSendFailed
        }
        ImageInviteUiState.SessionChanged -> strings.imageInviteSessionChanged
        else -> ""
    }

    AppAlert(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            AppText(strings.imageInviteTitle.replace("%name%", targetName))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                selection?.let {
                    AImage(
                        imageData = it.imageUrl,
                        contentDescription = it.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(AppShapes.s),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        AppActivityIndicator(
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    AppText(
                        text = statusText,
                        style = AppTheme.type.subheadline,
                        color = if (state is ImageInviteUiState.Failed ||
                            state is ImageInviteUiState.SessionChanged
                        ) AppTheme.colors.destructive else AppTheme.colors.label,
                    )
                }
                if (state is ImageInviteUiState.Failed &&
                    state.error !is ImageInviteNotInInstanceException
                ) {
                    val error = state.error
                    val reason = if (error is VRCApiException && error.code > 0) {
                        "[${error.code}] ${error.message.orEmpty()}".trimEnd()
                    } else {
                        error.message
                    }
                    if (!reason.isNullOrBlank()) {
                        SelectionContainer {
                            AppText(
                                text = reason,
                                style = AppTheme.type.caption1,
                                color = AppTheme.colors.destructive,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is ImageInviteUiState.Ready -> AppButton(onClick = onSend, style = AppButtonStyle.Prominent) {
                    AppText(strings.imageInviteSend)
                }
                is ImageInviteUiState.Failed -> AppButton(
                    onClick = if (state.stage == ImageInviteFailureStage.Preparation) {
                        onRetryPreparation
                    } else {
                        onSend
                    },
                    style = AppButtonStyle.Prominent,
                ) {
                    AppText(strings.retry)
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (!busy) {
                Row(modifier = Modifier.padding(end = 4.dp)) {
                    if (selection != null) {
                        AppButton(onClick = onChooseAnother, style = AppButtonStyle.Plain) {
                            AppText(strings.imageInviteChooseAnother)
                        }
                    }
                    AppButton(onClick = onDismiss, style = AppButtonStyle.Plain) {
                        AppText(strings.cancel)
                    }
                }
            }
        },
    )
}

private fun ImageInviteUiState.selectionOrNull(): GallerySelection? = when (this) {
    is ImageInviteUiState.Preparing -> selection
    is ImageInviteUiState.Ready -> selection
    is ImageInviteUiState.Sending -> selection
    is ImageInviteUiState.Failed -> selection
    is ImageInviteUiState.Sent -> selection
    else -> null
}
