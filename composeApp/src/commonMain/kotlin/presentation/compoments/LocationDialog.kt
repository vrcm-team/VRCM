package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

val DialogShapeForSharedElement = RoundedCornerShape(16.dp)

class LocationDialog(
    private val friendLocation: FriendLocation,
    private val sharedSuffixKey: String,
    private val onConfirmClick: () -> Unit,
) : SharedDialog {
    @OptIn(ExperimentalLayoutApi::class)
    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content(
        animatedVisibilityScope: AnimatedVisibilityScope,
    ) {
        val currentInstants by friendLocation.instants
        // remember一下防止owner被刷新为null
        val owner = remember { currentInstants.owner }
        val currentNavigator = currentNavigator
        val onClickUserIcon = { user: IUser ->
            if (currentNavigator.size <= 1) {
                currentNavigator push UserProfileScreen(
                    sharedSuffixKey,
                    UserProfileVo(user)
                )
            }
        }
        val inviteApi: InviteApi = koinInject()
        val authService: AuthService = koinInject()
        var isInvited by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val localeStrings = strings
        val onClickInvite = {
            scope.launch(Dispatchers.IO) {
                authService.reTryAuthCatching { inviteApi.inviteMyselfToInstance(friendLocation.location) }.onSuccess {
                    isInvited = true
                }
            }
        }
        CompositionLocalProvider(
            LocalSharedSuffixKey provides sharedSuffixKey,
        ) {
            SharedDialogContainer(
                key = friendLocation.location,
                animatedVisibilityScope = animatedVisibilityScope,
            ) {
                Column(
                    modifier = Modifier
                        .glideBack { close() }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .sharedElementBy(
                                key = friendLocation.location + "WorldImage",
                                sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        // TODO: World详情页跳转
                        AImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(MaterialTheme.shapes.medium),
                            imageData = friendLocation.instants.value.worldImageUrl,
                            contentDescription = "WorldImage"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                )
                                .padding(3.dp)
                        ) {
                            Text(
                                modifier = Modifier.align(Alignment.Center),
                                text = currentInstants.worldName,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (owner == null) return@Row
                                Text(
                                    text = "${localeStrings.locationDialogOwner}:",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleSmall,
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${localeStrings.locationDialogAuthor}:",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    modifier = Modifier.clickable { onClickUserIcon(UserProfileVo(currentInstants.worldAuthorId)) },
                                    textDecoration = TextDecoration.Underline,
                                    text = currentInstants.worldAuthorName,
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                text = "${localeStrings.locationDialogDescription}:",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            SelectionContainer {
                                Text(
                                    modifier = Modifier.heightIn(max = 80.dp).verticalScroll(rememberScrollState()),
                                    text = currentInstants.worldDescription,
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (currentInstants.worldAuthorTag.isNotEmpty()) {
                                Text(
                                    text = "${localeStrings.locationDialogTags}:",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    currentInstants.worldAuthorTag.forEach { tag ->
                                        TextLabel(
                                            text = tag,
                                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row {
                                UserIconsRow(friends = friendLocation.friendList) {
                                    onClickUserIcon(it)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RegionIcon(
                                            region = currentInstants.region
                                        )
                                        Text(
                                            text = currentInstants.accessType,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
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
    }

    override fun close(): Unit = onConfirmClick()
}