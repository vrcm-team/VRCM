package presentation.screens.world

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class InstancesDialog(
    private val instance: InstanceVo,
    private val sharedSuffixKey: String,
    private val onClose: () -> Unit = {},
) : SharedDialog {
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content(animatedVisibilityScope: AnimatedVisibilityScope) {
        val currentNavigator = currentNavigator
        val onClickUserIcon = { user: IUser ->
            currentNavigator push UserProfileScreen(
                sharedSuffixKey,
                UserProfileVo(user)
            )
        }
        val inviteApi: InviteApi = koinInject()
        val authService: AuthService = koinInject()
        var isInvited by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val localeStrings = strings
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        contentColor = MaterialTheme.colorScheme.primary
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
                                Text(
                                    text = "${localeStrings.locationDialogOwner}:",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Icon(
                                    modifier = Modifier.size(16.dp),
                                    imageVector = owner.iconVector,
                                    contentDescription = "OwnerIcon"
                                )
                                // TODO: Group详情页跳转
                                Text(
                                    modifier = if (owner.type == BlueprintType.User)
                                        Modifier.clickable { onClickUserIcon(UserProfileVo(owner.id)) }
                                    else Modifier,
                                    textDecoration = if (owner.type == BlueprintType.User) TextDecoration.Underline else null,
                                    text = owner.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
//                            UserIconsRow(friends = instance.f) {
//                                onClickUserIcon(it)
//                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                RegionIcon(
                                    region = instance.regionType
                                )
                                Text(
                                    text = "${instance.accessType}(${instance.instanceName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                TextLabel(
                                    text = "${instance.currentUsers ?: "0"}",
                                )
                                Button(
                                    modifier = Modifier.animateContentSize(),
                                    enabled = !isInvited,
                                    onClick = { onClickInvite() }
                                ) {
                                    Text(text = if (isInvited) localeStrings.locationInvited else localeStrings.locationInviteMe)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() = onClose()
}