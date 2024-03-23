package io.github.vrcmteam.vrcm.presentation.screens.home.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.extensions.createFailureCallbackDoNavigation
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getCallbackScreenModel
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

object FriendListTab: Tab {

    private var isRefreshed = true

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val parentNavigator = currentNavigator.parent!!
        val friendListTabModel: FriendListTabModel= getCallbackScreenModel(
            createFailureCallbackDoNavigation(parentNavigator) { AuthAnimeScreen(false) }
        )
        RefreshBox(
            isStartRefresh = isRefreshed,
            doRefresh = {
                isRefreshed = false
                friendListTabModel.refreshFriendList()
            }
        ) {
            FriendListPage(friendListTabModel.friendList){
                if (parentNavigator.size <= 1) parentNavigator push ProfileScreen(it)
            }
        }
    }



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

}

@Composable
private fun FriendListPage(friendData: List<FriendData>, toProfile:  (FriendData) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(friendData, key = { it.id }) {
            FriendListItem(it, toProfile)
        }
    }
}

@Composable
fun FriendListItem(friend: FriendData, toProfile: (FriendData) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { toProfile(friend) },
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
            Text(
                text = friend.displayName
            )
            Text(
               text = friend.statusDescription.ifBlank { friend.status.value }
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            modifier = Modifier
                .size(26.dp),
            imageVector = Icons.Rounded.Shield,
            contentDescription = "TrustRankIcon",
            tint = GameColor.Rank.fromValue(friend.trustRank)
        )
    }
}
