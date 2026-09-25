package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppAlert
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppCard
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.LocalContentColor
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

    AppScaffold(
        topBar = {
            AppNavBar(
                title = { AppText(strings.rewardCodeTitle) },
                navigationIcon = {
                    AppIconButton(onClick = navigator::pop) {
                        AppIcon(
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
                    AppTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sessionToken != null && !state.isSubmitting,
                        label = { AppText(strings.rewardCodeInputLabel) },
                        singleLine = true,
                        isError = failureText != null,
                        supportingText = failureText?.let { message ->
                            { AppText(message) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    )
                    AppButton(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sessionToken != null &&
                            state.code.isNotBlank() &&
                            !state.isSubmitting,
                        style = AppButtonStyle.Prominent,
                    ) {
                        if (state.isSubmitting) {
                            AppActivityIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        AppText(
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
                        AppIcon(
                            imageVector = AppIcons.CheckCircle,
                            contentDescription = null,
                            tint = AppTheme.colors.tint,
                        )
                        AppText(
                            text = strings.rewardCodeSuccess,
                            style = AppTheme.type.subheadlineEmphasized,
                            color = AppTheme.colors.tint,
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
internal fun RewardCodeDialog(
    state: RewardCodeUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val failureText = when (state.failure) {
        RewardCodeFailure.EmptyCode -> strings.rewardCodeRequired
        RewardCodeFailure.RequestFailed -> strings.rewardCodeFailed
        RewardCodeFailure.SessionUnavailable -> strings.rewardCodeSessionUnavailable
        null -> null
    }
    val rewards = state.rewards

    AppAlert(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        icon = {
            AppIcon(
                imageVector = AppIcons.Redeem,
                contentDescription = null,
            )
        },
        title = { AppText(strings.rewardCodeTitle) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "reward-code-dialog-input") {
                    AppTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sessionToken != null && !state.isSubmitting,
                        label = { AppText(strings.rewardCodeInputLabel) },
                        singleLine = true,
                        isError = failureText != null,
                        supportingText = failureText?.let { message ->
                            { AppText(message) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    )
                }
                if (rewards != null) {
                    item(key = "reward-code-dialog-success") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                imageVector = AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = AppTheme.colors.tint,
                            )
                            AppText(
                                text = strings.rewardCodeSuccess,
                                style = AppTheme.type.subheadlineEmphasized,
                                color = AppTheme.colors.tint,
                            )
                        }
                    }
                    itemsIndexed(
                        items = rewards,
                        key = { index, reward -> "dialog:${reward.type}:$index" },
                    ) { _, reward ->
                        RewardResultCard(
                            reward = reward,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                onClick = onSubmit,
                enabled = state.sessionToken != null &&
                    state.code.isNotBlank() &&
                    !state.isSubmitting,
                style = AppButtonStyle.Prominent,
            ) {
                if (state.isSubmitting) {
                    AppActivityIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AppText(
                    if (state.isSubmitting) {
                        strings.rewardCodeSubmitting
                    } else {
                        strings.rewardCodeSubmit
                    }
                )
            }
        },
        dismissButton = {
            AppButton(
                onClick = onDismiss,
                enabled = !state.isSubmitting,
                style = AppButtonStyle.Plain,
            ) {
                AppText(if (rewards != null) strings.close else strings.cancel)
            }
        },
    )
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
    AppCard(
        modifier = modifier,
        shape = AppShapes.m,
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
                    modifier = Modifier.size(52.dp).clip(AppShapes.s),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppText(
                    text = typeLabel,
                    style = AppTheme.type.caption1Emphasized,
                    color = AppTheme.colors.tint,
                )
                AppText(
                    text = presentation.name.ifBlank { typeLabel },
                    style = AppTheme.type.subheadlineEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (presentation.description.isNotBlank()) {
                    AppText(
                        text = presentation.description,
                        style = AppTheme.type.caption1,
                        color = AppTheme.colors.secondaryLabel,
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
