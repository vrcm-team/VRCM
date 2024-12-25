package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.presentation.animations.fadeSlideHorizontally
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.AuthFold
import io.github.vrcmteam.vrcm.presentation.compoments.TextLabel
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.LoginCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.card.VerifyCardInput
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.launch

object AuthScreen : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = currentNavigator
        val authScreenModel: AuthScreenModel = koinScreenModel()
        var showAccountMenu by remember { mutableStateOf(false) }
        val onAccountChange:(AccountDto) -> Unit = {
            authScreenModel.onAccountChange(it)
        }
        LaunchedEffect(Unit) {
            authScreenModel.tryAuth()
        }
        val authUIState = authScreenModel.uiState
        BoxWithConstraints(
            modifier = Modifier.imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            AuthFold(
                authUIState = authUIState,
                clickIcon = if (authUIState.cardState == AuthCardPage.Login && !authUIState.btnIsLoading)
                    { { showAccountMenu = true }} else  null
                ,
                iconYOffset = maxHeight.times(-0.2f),
                cardHeightDp = maxHeight.times(0.42f),
            ) {
                AuthCard(
                    cardState = authUIState.cardState,
                ) { state ->
                    when (state) {
                        AuthCardPage.Loading -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 5.dp
                                )
                            }
                        }

                        AuthCardPage.Login -> {
                            NavCard(strings.authLoginTitle) {
                                LoginCardInput(
                                    uiState = authUIState,
                                    onUsernameChange = authScreenModel::onUsernameChange,
                                    onPasswordChange = authScreenModel::onPasswordChange,
                                    onClick = authScreenModel::login
                                )
                            }
                        }

                        AuthCardPage.EmailCode, AuthCardPage.TFACode, AuthCardPage.TTFACode -> {
                            NavCard(strings.authVerifyTitle, barContent = {
                                ReturnIcon {
                                    authScreenModel.cancelJob()
                                    authScreenModel.onCardStateChange(AuthCardPage.Login)
                                }
                            }) {
                                VerifyCardInput(
                                    uiState = authUIState,
                                    onVerifyCodeChange = authScreenModel::onVerifyCodeChange,
                                    onClick = authScreenModel::verify
                                )
                            }
                        }

                        AuthCardPage.Authed -> {
                            LaunchedEffect(Unit) {
                                currentNavigator replace AuthAnimeScreen(true)
                            }
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        AccountBottomSheet(
            showAccountMenu = showAccountMenu,
            onDismissRequest = { showAccountMenu = false },
            findAccountList = authScreenModel::accountDtoList,
            onAccountChange = onAccountChange,
            authUIState = authUIState
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AccountBottomSheet(
    showAccountMenu: Boolean,
    onDismissRequest: () -> Unit,
    findAccountList: ()-> List<AccountDto>,
    onAccountChange: (AccountDto) -> Unit,
    authUIState: AuthUIState,
) {
    val scope = rememberCoroutineScope()
    val sheetState: SheetState = rememberModalBottomSheetState()
    ABottomSheet(
        isVisible = showAccountMenu,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                findAccountList().forEach { accountDto ->
                    ListItem(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .clickable {
                                onAccountChange(accountDto)
                                scope.launch { sheetState.hide() }
                                    .invokeOnCompletion {  onDismissRequest() }
                            },
                        leadingContent = {
                            UserStateIcon(
                                modifier = Modifier.size(60.dp),
                                iconUrl = accountDto.iconUrl,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = accountDto.username,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        },
                        trailingContent = {
                            if (authUIState.userId == accountDto.userId) {
                                TextLabel(
                                    text = strings.authCurrent,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                )
                            }
                        }
                    )
                }
            }
        }

    }
}

@Composable
private fun AuthCard(
    modifier: Modifier = Modifier,
    cardState: AuthCardPage,
    content: @Composable (AuthCardPage) -> Unit,
) {
    val cardChangeDurationMillis = 600
    val cardChangeAnimationSpec =
        remember { tween<Float>(cardChangeDurationMillis, cardChangeDurationMillis) }
    val animationSpec = remember { tween<Float>(cardChangeDurationMillis) }
    AnimatedContent(
        modifier = modifier,
        label = "AuthSurfaceChange",
        targetState = cardState,
        transitionSpec = {
            when (this.targetState) {
                AuthCardPage.Loading -> fadeIn(cardChangeAnimationSpec)
                    .togetherWith(fadeOut(animationSpec))

                AuthCardPage.Login -> fadeSlideHorizontally(
                    cardChangeDurationMillis,
                    direction = -1
                )

                AuthCardPage.EmailCode, AuthCardPage.TFACode, AuthCardPage.TTFACode -> fadeSlideHorizontally(
                    cardChangeDurationMillis
                )

                AuthCardPage.Authed -> EnterTransition.None
                    .togetherWith(fadeOut(animationSpec))
            }
        },
    ) {
        content(it)
    }

}


@Composable
private fun ReturnIcon(onClick: () -> Unit) {
    IconButton(
        modifier = Modifier
            .padding(start = 6.dp, top = 6.dp),
        onClick = onClick
    ) {
        Icon(
            imageVector =AppIcons.KeyboardArrowLeft,
            contentDescription = "ReturnIcon",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}


@Composable
private fun NavCard(
    tileText: String,
    barContent: @Composable () -> Unit = {
//        Spacer(modifier = Modifier.height(42.dp))
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    Box {
        barContent()
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = tileText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            content()
        }
    }

}