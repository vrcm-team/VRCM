package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
        val scope = rememberCoroutineScope()
        val onClickInvite = {
            scope.launch(Dispatchers.IO) {
                inviteApi.inviteMyselfToInstance(friendLocation.location)
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
                        .glideBack{ close() }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        AImage(
                            modifier = Modifier
                                .sharedElementBy(
                                    key = friendLocation.location + "WorldImage",
                                    sharedTransitionScope = LocalSharedTransitionDialogScope.current,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(MaterialTheme.shapes.medium),
                            imageData = friendLocation.instants.value.worldImageUrl,
                            contentDescription = "WorldImage"
                        )
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
                            Text(
                                text = currentInstants.worldName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Owner:${currentInstants.ownerName}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Author:${currentInstants.worldAuthorName}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "AuthorTag:",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = currentInstants.worldAuthorTag.joinToString(",\t"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = "Description:",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = currentInstants.worldDescription,
                                style = MaterialTheme.typography.bodySmall,
                            )
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
                        TextButton(onClick = { onClickInvite() }) {
                            Text(text = "Invite Myself")
                        }
                    }
                }
            }
        }
    }

    override fun close(): Unit = onConfirmClick()

}