package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

private data class BoopOption(
    val emojiId: String?,
    val label: String,
    val icon: ImageVector,
)

@Composable
internal fun BoopSelectorDialog(
    visible: Boolean,
    targetName: String,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String?) -> Unit,
) {
    if (!visible) return
    var selectedEmojiId by remember { mutableStateOf<String?>(null) }
    val options = listOf(
        BoopOption(null, strings.boopEmojiDefault, AppIcons.Tap),
        BoopOption("default_heart", strings.boopEmojiHeart, AppIcons.FavoriteBorder),
        BoopOption("default_hand_wave", strings.boopEmojiWave, AppIcons.Wave),
        BoopOption("default_laugh", strings.boopEmojiLaugh, AppIcons.FaceLaugh),
        BoopOption("default_thumbs_up", strings.boopEmojiLike, AppIcons.ThumbUp),
        BoopOption("default_thinking", strings.boopEmojiThink, AppIcons.FaceThinking),
        BoopOption("default_wow", strings.boopEmojiSurprise, AppIcons.FaceSurprised),
        BoopOption("default_angry", strings.boopEmojiAngry, AppIcons.FaceAngry),
    )
    AppAlert(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { AppText(strings.boopSelectorTitle.replace("%name%", targetName)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.emojiId ?: "default" }) { option ->
                    val selected = selectedEmojiId == option.emojiId
                    AppSurface(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable(enabled = !sending) { selectedEmojiId = option.emojiId },
                        shape = AppShapes.m,
                        color = if (selected) {
                            AppTheme.colors.fill
                        } else {
                            AppTheme.colors.tertiaryGroupedBackground
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) AppTheme.colors.tint else AppTheme.colors.separator,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppIcon(option.icon, contentDescription = null)
                            AppText(
                                text = option.label,
                                style = AppTheme.type.caption2Emphasized,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(enabled = !sending, onClick = { onSend(selectedEmojiId) }, style = AppButtonStyle.Prominent) {
                if (sending) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AppText(strings.boopSend)
            }
        },
        dismissButton = {
            AppButton(enabled = !sending, onClick = onDismiss, style = AppButtonStyle.Plain) {
                AppText(strings.cancel)
            }
        },
    )
}
