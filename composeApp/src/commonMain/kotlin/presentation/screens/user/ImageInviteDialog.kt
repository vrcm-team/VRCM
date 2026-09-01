package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
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

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(strings.imageInviteTitle.replace("%name%", targetName))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                selection?.let {
                    AImage(
                        imageData = it.imageUrl,
                        contentDescription = it.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state is ImageInviteUiState.Failed ||
                            state is ImageInviteUiState.SessionChanged
                        ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is ImageInviteUiState.Ready -> Button(onClick = onSend) {
                    Text(strings.imageInviteSend)
                }
                is ImageInviteUiState.Failed -> Button(
                    onClick = if (state.stage == ImageInviteFailureStage.Preparation) {
                        onRetryPreparation
                    } else {
                        onSend
                    },
                ) {
                    Text(strings.retry)
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (!busy) {
                Row(modifier = Modifier.padding(end = 4.dp)) {
                    if (selection != null) {
                        TextButton(onClick = onChooseAnother) {
                            Text(strings.imageInviteChooseAnother)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
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
