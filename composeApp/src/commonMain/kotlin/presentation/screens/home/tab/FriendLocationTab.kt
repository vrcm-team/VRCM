package io.github.vrcmteam.vrcm.presentation.screens.home.tab

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.compoments.RefreshBox
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.profile.ProfileScreen
import io.github.vrcmteam.vrcm.presentation.theme.MediumRoundedShape

object FriendLocationTab : Tab {

    private var isRefreshed = true
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val friendLocationTabModel = getScreenModel<FriendLocationTabModel>()
        val currentNavigator = LocalNavigator.currentOrThrow
        val parentNavigator = currentNavigator.parent!!
        RefreshBox(
            isStartRefresh = isRefreshed,
            doRefresh = {
                isRefreshed = false
                friendLocationTabModel.refreshFriendLocation{
                    // TODO() 统一错误提示
                }
            }
        ) {
            FriendLocationPage(friendLocationTabModel.friendLocationMap){
                if (parentNavigator.size <= 1) parentNavigator push ProfileScreen(it)
            }
        }
    }

    override val options: TabOptions
        @Composable
        get() {
            val painter = rememberVectorPainter(image = Icons.Rounded.Home)
            return remember {
                TabOptions(
                    index = 0u,
                    title = "FriendLocation",
                    icon = painter
                )
            }
        }
}


@Composable
private fun FriendLocationPage(
    friendLocationMap: Map<LocationType, MutableList<FriendLocation>>,
    onClickUserIcon: (IUser) -> Unit,
) {
    Box {
        val offlineFriendLocation = friendLocationMap[LocationType.Offline]?.get(0)
        val privateFriendLocation = friendLocationMap[LocationType.Private]?.get(0)
        val travelingFriendLocation = friendLocationMap[LocationType.Traveling]?.get(0)
        val instanceFriendLocations = friendLocationMap[LocationType.Instance]
        LazyColumn(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {

            item(key = LocationType.Offline) {
                SingleLocationCard(offlineFriendLocation, "Active on the Website", onClickUserIcon)
            }

            item(key = LocationType.Private) {
                SingleLocationCard(
                    privateFriendLocation,
                    "Friends in Private Worlds",
                    onClickUserIcon
                )
            }

            item(key = LocationType.Traveling) {
                SingleLocationCard(travelingFriendLocation, "Friends is Traveling", onClickUserIcon)
            }

            if (instanceFriendLocations != null) {
                item(key = LocationType.Instance) {
                    Text(text = "by Location")
                }
                items(instanceFriendLocations, key = { it.location }) { locations ->
                    LocationCard(locations) {
                        UserIconsRow(locations.friends, onClickUserIcon)
                    }
                }
            }
        }

    }
}

@Composable
private fun SingleLocationCard(
    friendLocations: FriendLocation?,
    text: String,
    onClickUserIcon: (IUser) -> Unit
) {
    if (friendLocations == null) return
    Text(text)
    Spacer(modifier = Modifier.height(6.dp))
    UserIconsRow(friendLocations.friends, onClickUserIcon)
}

@Composable
private fun UserIconsRow(
    friends: MutableList<MutableState<FriendData>>,
    onClickUserIcon: (IUser) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(friends, key = { it.value.id }) {
            LocationFriend(
                it.value.iconUrl,
                it.value.displayName,
                it.value.status
            ) { onClickUserIcon(it.value) }
        }
    }
}

@Composable
private fun LocationFriend(
    iconUrl: String,
    name: String,
    userStatus: UserStatus,
    onClickUserIcon: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(MediumRoundedShape)
            .clickable (onClick = onClickUserIcon),
        verticalArrangement = Arrangement.Center
    ) {
        UserStateIcon(
            modifier = Modifier.fillMaxSize(),
            iconUrl = iconUrl,
            userStatus = userStatus
        )
        Text(
            modifier = Modifier.fillMaxSize(),
            text = name,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LocationCard(location: FriendLocation, content: @Composable () -> Unit) {
    val instants by location.instants
    val shape = MediumRoundedShape
    Surface(
        color = MaterialTheme.colorScheme.onPrimary,
        shape = shape,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(80.dp)
                    .fillMaxWidth()
            ) {
                AImage(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(shape),
                    imageData = instants.worldImageUrl,
                    contentDescription = "WorldImage"
                )
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        text = instants.worldName,
                        fontSize = 15.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                    Row(
                        modifier = Modifier
                            .height(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AImage(
                            modifier = Modifier
                                .size(15.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .border(1.dp, Color.LightGray, CircleShape),
                            imageData = instants.regionIconUrl
                        )
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 6.dp),
                            text = instants.accessType?.displayName?:"",
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 6.dp),
                            text = instants.userCount,
                        )
                        Icon(
                            modifier = Modifier
                                .size(15.dp),
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "PersonCount"
                        )
                    }
                }
            }
            content()
        }
    }
}