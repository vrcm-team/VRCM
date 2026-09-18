package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.InstanceCloseState
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.world.canOfferInstanceClose
import io.github.vrcmteam.vrcm.presentation.screens.world.closeTargetOrNull
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.requestOrNull
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class InstancesDialog(
    private val instance: InstanceVo,
    private val sharedSuffixKey: String,
    private val screenModel: WorldProfileScreenModel,
    private val onClose: () -> Unit = {},
) : SharedDialog {
    @OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val currentNavigator = currentNavigator
        val onClickUserIcon = { user: IUser ->
            currentNavigator push UserProfileScreen(
                UserProfileVo(user),
                sharedSuffixKey
            )
        }
        val inviteApi: InviteApi = koinInject()
        val authService: AuthService = koinInject()
        var isInvited by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val localeStrings = strings
        val currentSession by SharedFlowCentre.currentSession.collectAsState()
        val instanceCloseState by screenModel.instanceCloseState.collectAsState()
        val closeTarget = instance.closeTargetOrNull()
        val closeLocation = closeTarget?.location
        val isCurrentClose = closeLocation != null &&
            instanceCloseState.requestOrNull?.target?.location == closeLocation
        val isAuthorizingClose = isCurrentClose && instanceCloseState is InstanceCloseState.Authorizing
        val isAwaitingCloseConfirmation = isCurrentClose &&
            instanceCloseState is InstanceCloseState.AwaitingConfirmation
        val isSubmittingClose = isCurrentClose && instanceCloseState is InstanceCloseState.Submitting
        val canOfferClose = instance.canOfferInstanceClose(currentSession?.token)

        LaunchedEffect(closeLocation) {
            if (closeLocation == null) return@LaunchedEffect
            screenModel.closedInstanceLocations.collect { closedLocation ->
                if (closedLocation == closeLocation) close()
            }
        }

        val onClickInvite = {
            scope.launch(Dispatchers.IO) {
                authService.reTryAuthCatching { inviteApi.inviteMyselfToInstance(instance.id) }.onSuccess {
                    isInvited = true
                }
            }
        }
        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey,
        ) {
            SharedDialogContainer(
                key = instance.id,
                animatedVisibilityScope = animatedVisibilityScope,
            ) {
                Column(
                    modifier = Modifier
                        .glideBack { close() }
                        .sharedElementBy(
                            key = instance.id + "WorldImage",
                            sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.m,
                    ) {

                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val owner = instance.owner.collectAsState().value ?: return@Row
                                AppText(
                                    text = "${localeStrings.locationDialogOwner}:",
                                    fontWeight = FontWeight.Medium,
                                    style = AppTheme.type.subheadlineEmphasized,
                                )
                                AppIcon(
                                    modifier = Modifier.size(16.dp),
                                    imageVector = owner.iconVector,
                                    contentDescription = "OwnerIcon"
                                )
                                // TODO: Group详情页跳转
                                AppText(
                                    modifier = if (owner.type == BlueprintType.User)
                                        Modifier.clickable {
                                            onClickUserIcon(
                                                UserProfileVo(id = owner.id, displayName = owner.displayName)
                                            )
                                        }
                                    else Modifier,
                                    textDecoration = if (owner.type == BlueprintType.User) TextDecoration.Underline else null,
                                    text = owner.displayName,
                                    style = AppTheme.type.subheadline,
                                    color = AppTheme.colors.tertiaryLabel,
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
//                            UserIconsRow(friends = instance.f) {
//                                onClickUserIcon(it)
//                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                RegionIcon(
                                    region = instance.regionType
                                )
                                AppText(
                                    text = "${instance.accessType}(${instance.instanceName})",
                                    style = AppTheme.type.subheadlineEmphasized,
                                    color = AppTheme.colors.tertiaryLabel
                                )
                                TextLabel(
                                    text = "${instance.currentUsers ?: "0"}",
                                )
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (canOfferClose) {
                                    AppButton(
                                        enabled = instanceCloseState is InstanceCloseState.Idle,
                                        onClick = {
                                            screenModel.requestInstanceClose(instance, localeStrings)
                                        },
                                        style = AppButtonStyle.Gray,
                                        role = AppButtonRole.Destructive,
                                    ) {
                                        if (isAuthorizingClose) {
                                            AppActivityIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = LocalContentColor.current,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            AppIcon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = null,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        AppText(
                                            if (isAuthorizingClose) {
                                                localeStrings.instanceCloseCheckingPermission
                                            } else {
                                                localeStrings.instanceCloseAction
                                            }
                                        )
                                    }
                                }
                                AppButton(
                                    modifier = Modifier.animateContentSize(),
                                    enabled = !isInvited,
                                    onClick = { onClickInvite() },
                                    style = AppButtonStyle.Prominent,
                                ) {
                                    AppText(text = if (isInvited) localeStrings.locationInvited else localeStrings.locationInviteMe)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isAwaitingCloseConfirmation || isSubmittingClose) {
            AppAlert(
                onDismissRequest = {
                    if (!isSubmittingClose) {
                        closeLocation?.let(screenModel::abandonInstanceClose)
                    }
                },
                title = { AppText(localeStrings.instanceCloseConfirmTitle) },
                text = { AppText(localeStrings.instanceCloseConfirmMessage) },
                confirmButton = {
                    AppButton(
                        enabled = !isSubmittingClose,
                        onClick = { screenModel.confirmInstanceClose(localeStrings) },
                        style = AppButtonStyle.Plain,
                        role = AppButtonRole.Destructive,
                    ) {
                        if (isSubmittingClose) {
                            AppActivityIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        AppText(
                            if (isSubmittingClose) {
                                localeStrings.instanceCloseInProgress
                            } else {
                                localeStrings.instanceCloseAction
                            }
                        )
                    }
                },
                dismissButton = {
                    AppButton(
                        enabled = !isSubmittingClose,
                        onClick = {
                            closeLocation?.let(screenModel::abandonInstanceClose)
                        },
                        style = AppButtonStyle.Plain,
                    ) {
                        AppText(localeStrings.cancel)
                    }
                },
            )
        }
    }

    override fun close() {
        instance.closeTargetOrNull()?.location?.let(screenModel::abandonInstanceClose)
        onClose()
    }
}
