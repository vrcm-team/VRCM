package io.github.vrcmteam.vrcm.presentation.screens.home.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

data class PersonalDrawerUser(
    val avatarUrl: String?,
    val displayName: String,
    val pronouns: String?,
    val status: UserStatus,
    val statusDescription: String,
)

/** Personal navigation drawer shell. Services and navigation remain owned by its caller. */
@Composable
fun PersonalNavigationDrawer(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    user: PersonalDrawerUser?,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
    onFriendNetworkClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onRecentWorldsClick: () -> Unit,
    onNameplateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerDescription = strings.personalDrawerTitle
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        scrimColor = DrawerDefaults.scrimColor,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                modifier = Modifier
                    .fillMaxWidth(.82f)
                    .widthIn(max = 360.dp)
                    .semantics { contentDescription = drawerDescription },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Vertical + WindowInsetsSides.Start,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                ) {
                    PersonalHeader(user, onProfileClick, onStatusClick)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerItem(
                        AppIcons.Person,
                        strings.drawerMyProfile,
                        onProfileClick,
                        enabled = user != null,
                    )
                    DrawerItem(AppIcons.PersonSearch, strings.friendNetworkTitle, onFriendNetworkClick)
                    DrawerItem(AppIcons.Dashboard, strings.galleryScreenTitle, onGalleryClick)
                    DrawerItem(AppIcons.Favorite, strings.favoritesTitle, onFavoritesClick)
                    DrawerItem(AppIcons.Explore, strings.recentWorldsTitle, onRecentWorldsClick)
                    DrawerItem(AppIcons.AccountCircle, strings.meetupCardTitle, onNameplateClick)
                    DrawerItem(AppIcons.Settings, strings.drawerSettings, onSettingsClick)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    DrawerItem(AppIcons.Login, strings.stettingLogout, onLogoutClick, error = true)
                }
            }
        },
        content = content,
    )
}

@Composable
private fun PersonalHeader(
    user: PersonalDrawerUser?,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    val loaded = user != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        UserStateIcon(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .clickable(enabled = loaded, onClick = onProfileClick),
            iconUrl = user?.avatarUrl,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clickable(enabled = loaded, onClick = onProfileClick)) {
            Text(
                user?.displayName ?: strings.loading,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            user?.pronouns?.takeIf { it.isNotBlank() }?.let { pronouns ->
                Text(
                    pronouns,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(enabled = loaded, onClick = onStatusClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(Modifier.size(10.dp), shape = CircleShape, color = GameColor.Status.fromValue(user?.status)) {}
            Text(
                text = user?.statusDescription.orEmpty().ifBlank {
                    user?.status?.localizedLabel() ?: strings.loading
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    error: Boolean = false,
    enabled: Boolean = true,
) {
    val baseColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val color = baseColor.copy(alpha = if (enabled) 1f else .38f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UserStatus.localizedLabel(): String = when (this) {
    UserStatus.Active -> strings.editProfileStatusOnline
    UserStatus.JoinMe -> strings.editProfileStatusJoinMe
    UserStatus.AskMe -> strings.editProfileStatusAskMe
    UserStatus.Busy -> strings.editProfileStatusBusy
    UserStatus.Offline -> strings.friendDirectoryOffline
}
