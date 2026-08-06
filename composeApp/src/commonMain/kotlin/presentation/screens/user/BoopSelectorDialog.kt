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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

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
        BoopOption(null, strings.boopEmojiDefault, Icons.Outlined.TouchApp),
        BoopOption("default_heart", strings.boopEmojiHeart, Icons.Outlined.FavoriteBorder),
        BoopOption("default_hand_wave", strings.boopEmojiWave, Icons.Outlined.WavingHand),
        BoopOption("default_laugh", strings.boopEmojiLaugh, Icons.Outlined.SentimentVerySatisfied),
        BoopOption("default_thumbs_up", strings.boopEmojiLike, Icons.Outlined.ThumbUpOffAlt),
        BoopOption("default_thinking", strings.boopEmojiThink, Icons.Outlined.PsychologyAlt),
        BoopOption("default_wow", strings.boopEmojiSurprise, Icons.Outlined.EmojiEmotions),
        BoopOption("default_angry", strings.boopEmojiAngry, Icons.Outlined.SentimentDissatisfied),
    )
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(strings.boopSelectorTitle.replace("%name%", targetName)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.emojiId ?: "default" }) { option ->
                    val selected = selectedEmojiId == option.emojiId
                    Surface(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable(enabled = !sending) { selectedEmojiId = option.emojiId },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(option.icon, contentDescription = null)
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall,
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
            Button(enabled = !sending, onClick = { onSend(selectedEmojiId) }) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(strings.boopSend)
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}
