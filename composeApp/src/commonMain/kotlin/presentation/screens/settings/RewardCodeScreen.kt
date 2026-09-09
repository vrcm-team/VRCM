package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemption
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object RewardCodeScreen : AppDetailRoute {
    @Composable
    override fun Content() {
        val model: RewardCodeScreenModel = koinViewModel()
        val state by model.state.collectAsState()
        RewardCodeContent(
            state = state,
            onCodeChange = model::updateCode,
            onSubmit = model::submit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardCodeContent(
    state: RewardCodeUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val failureText = when (state.failure) {
        RewardCodeFailure.EmptyCode -> strings.rewardCodeRequired
        RewardCodeFailure.RequestFailed -> strings.rewardCodeFailed
        RewardCodeFailure.SessionUnavailable -> strings.rewardCodeSessionUnavailable
        null -> null
    }
    val rewards = state.rewards

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(strings.rewardCodeTitle) },
                navigationIcon = {
                    IconButton(onClick = navigator::pop) {
                        Icon(
                            imageVector = AppIcons.ArrowBackIosNew,
                            contentDescription = strings.back,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "reward-code-input") {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sessionToken != null && !state.isSubmitting,
                        label = { Text(strings.rewardCodeInputLabel) },
                        singleLine = true,
                        isError = failureText != null,
                        supportingText = failureText?.let { message ->
                            { Text(message) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    )
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sessionToken != null &&
                            state.code.isNotBlank() &&
                            !state.isSubmitting,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            if (state.isSubmitting) {
                                strings.rewardCodeSubmitting
                            } else {
                                strings.rewardCodeSubmit
                            }
                        )
                    }
                }
            }

            if (state.rewards != null) {
                item(key = "reward-code-success") {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = AppIcons.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = strings.rewardCodeSuccess,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (rewards != null) {
                itemsIndexed(
                    items = rewards,
                    key = { index, reward -> "${reward.type}:$index" },
                ) { _, reward ->
                    RewardResultCard(
                        reward = reward,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardResultCard(
    reward: RewardRedemption,
    modifier: Modifier = Modifier,
) {
    val presentation = reward.presentation()
    val typeLabel = when (reward.type.lowercase()) {
        "badge" -> strings.rewardCodeBadge
        "item" -> strings.rewardCodeItem
        else -> reward.type.ifBlank { strings.rewardCodeReward }
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (presentation.imageUrl.isNotBlank()) {
                AImage(
                    imageData = presentation.imageUrl,
                    contentDescription = presentation.name,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = presentation.name.ifBlank { typeLabel },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (presentation.description.isNotBlank()) {
                    Text(
                        text = presentation.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class RewardPresentation(
    val name: String,
    val description: String,
    val imageUrl: String,
)

private fun RewardRedemption.presentation(): RewardPresentation {
    data.badge?.let { badge ->
        return RewardPresentation(badge.name, badge.description, badge.imageUrl)
    }
    data.item?.let { item ->
        return RewardPresentation(item.name, item.description, item.imageUrl)
    }
    return RewardPresentation(name = "", description = "", imageUrl = "")
}
