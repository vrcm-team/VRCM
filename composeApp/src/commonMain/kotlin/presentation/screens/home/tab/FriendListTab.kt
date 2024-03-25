package io.github.vrcmteam.vrcm.presentation.screens.home.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen
import io.github.vrcmteam.vrcm.presentation.supports.RefreshLazyColumnTab
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

object FriendListTab: RefreshLazyColumnTab() {

    override val options: TabOptions
        @Composable
        get() {
            val painter = rememberVectorPainter(image = Icons.Rounded.Person)
            return remember {
                TabOptions(
                    index = 1u,
                    title = "Friend",
                    icon = painter
                )
            }
        }

    @Composable
    override fun doRefreshCall(): suspend () -> Unit {
        val parentNavigator = currentNavigator.parent!!
        val friendListTabModel: FriendListTabModel= getCallbackScreenModel(
            createFailureCallbackDoNavigation(parentNavigator) { AuthAnimeScreen(false) }
        )
        return { friendListTabModel.refreshFriendList() }
    }

    @Composable
    override fun BoxContent() {
        val parentNavigator = currentNavigator.parent!!
        val friendListTabModel: FriendListTabModel= getCallbackScreenModel(
            createFailureCallbackDoNavigation(parentNavigator) { AuthAnimeScreen(false) }
        )
        val toProfile = { user: IUser ->
            if (parentNavigator.size <= 1) parentNavigator push ProfileScreen(user)
        }
        val friendList = friendListTabModel.friendList

        // 如果没有底部系统手势条，默认12dp
        val bottomPadding =
            (getInsetPadding(WindowInsets::getBottom).takeIf { it != 0.dp } ?: 12.dp) + 86.dp
        RememberLazyColumn (
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 6.dp, bottom = bottomPadding)
        ) {
            items(friendList, key = { it.id }) {
                FriendListItem(it, toProfile)
            }
        }
    }
}

@Composable
fun FriendListItem(friend: FriendData, toProfile: (FriendData) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 1.dp
    ){
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { toProfile(friend) }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UserStateIcon(
                modifier = Modifier
                    .height(56.dp)
                    .clip(CircleShape),
                iconUrl = friend.iconUrl,
                userStatus = friend.status
            )
            Column{
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .size(16.dp),
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "TrustRankIcon",
                        tint = GameColor.Rank.fromValue(friend.trustRank)
                    )
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }

                Text(
                    text = friend.statusDescription.ifBlank { friend.status.value },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
            Spacer(Modifier.weight(1f))

        }
    }
}
