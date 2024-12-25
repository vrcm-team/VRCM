package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.glideBack
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.profile.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

val DialogShapeForSharedElement = RoundedCornerShape(16.dp)

class LocationDialog(
    private val location: FriendLocation,
    private val sharedSuffixKey: String,
    private val onConfirmClick: () -> Unit,
) : SharedDialog {

    @OptIn(ExperimentalLayoutApi::class)
    @ExperimentalSharedTransitionApi
    @Composable
    override fun Content(
        animatedVisibilityScope: AnimatedVisibilityScope,
    ) {
        val friendLocation = location
        val currentInstants by friendLocation.instants
        val currentNavigator = currentNavigator
        val onClickUserIcon = { user: IUser ->
            if (currentNavigator.size <= 1) {
//                close()
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
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            currentInstants.ownerName.value?.let {ownerName->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${localeStrings.locationDialogOwner}:",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = ownerName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${localeStrings.locationDialogAuthor}:",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                SelectionContainer {
                                    Text(
                                        text = currentInstants.worldAuthorName,
                                        color = MaterialTheme.colorScheme.outline,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Text(
                                text = "${localeStrings.locationDialogDescription}:",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            SelectionContainer {
                                Text(
                                    text = currentInstants.worldDescription,
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = "${localeStrings.locationDialogTags}:",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                currentInstants.worldAuthorTag.forEach { tag ->
                                    TextLabel(
                                        text = tag,
                                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                    UserIconsRow(friendLocation.friendList) {
                        onClickUserIcon(it)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
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

    override fun close(): Unit = onConfirmClick()

}