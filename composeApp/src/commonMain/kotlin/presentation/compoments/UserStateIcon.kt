package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.presentation.extensions.drawSateCircle
import io.github.vrcmteam.vrcm.presentation.extensions.enableIf
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

@Composable
fun UserStateIcon(
    modifier: Modifier = Modifier,
    iconUrl: String?,
    userStatus: UserStatus? = null
) {
     AImage(
        modifier = Modifier
            .then(modifier)
            .enableIf(userStatus != null) { drawSateCircle(GameColor.Status.fromValue(userStatus)) }
            .aspectRatio(1f)
            .clip(CircleShape),
        imageData = iconUrl.orEmpty(),
        contentDescription = "UserStateIcon"
    )
}

@Composable
fun UserIconsRow(
//    modifier: Modifier = Modifier,
    friends: List<State<FriendData>>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClickUserIcon: (IUser) -> Unit,
) {
    if (friends.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(friends, key = { it.value.id }) {
            val friend = it.value
            LocationFriend(
                id = friend.id,
                iconUrl = friend.iconUrl,
                name = friend.displayName,
                userStatus = friend.status
            ) { onClickUserIcon(friend) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.LocationFriend(
    id: String,
    iconUrl: String,
    name: String,
    userStatus: UserStatus,
    onClickUserIcon: () -> Unit,
) {
    Column(
        modifier = Modifier.width(60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClickUserIcon).animateItem(),
        verticalArrangement = Arrangement.Center
    ) {
        UserStateIcon(
            modifier = Modifier.sharedBoundsBy("${id}UserIcon").fillMaxWidth(),
            iconUrl = iconUrl,
            userStatus = userStatus
        )
        Text(
            modifier = Modifier.sharedBoundsBy("${id}UserName").fillMaxWidth(),
            text = name,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserInfo(
    modifier: Modifier = Modifier,
    spacedBy: Dp = 6.dp,
    iconSize: Dp = 16.dp,
    user:IUser?,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacedBy),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .sharedBoundsBy("${user?.id}UserTrustRankIcon")
                .size(iconSize),
            imageVector = Icons.Rounded.Shield,
            contentDescription = "TrustRankIcon",
            tint = GameColor.Rank.fromValue(user?.trustRank)
        )
        Text(
            modifier = Modifier.sharedBoundsBy("${user?.id}UserName"),
            text = user?.displayName.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun UserStatusRow(
    modifier: Modifier = Modifier,
    spacedBy: Dp = 6.dp,
    iconSize: Dp = 12.dp,
    user:IUser?,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacedBy)
    ) {
        Canvas(modifier = Modifier
            .sharedBoundsBy("${user?.id}UserStatusIcon")
            .size(iconSize)
        ) {
            drawCircle(GameColor.Status.fromValue(user?.status))
        }
        Text(
            modifier = Modifier.sharedBoundsBy("${user?.id}UserStatusDescription"),
            text = user?.statusDescription?.ifBlank { user.status.value }.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color =  MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
    }
}