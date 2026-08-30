package io.github.vrcmteam.vrcm.presentation.screens.home.drawer

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.presentation.compoments.UserStateIcon
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.theme.GameColor

data class PersonalDrawerUser(
    val avatarUrl: String?,
    val displayName: String,
    val username: String,
    val status: UserStatus,
    val statusDescription: String,
)

/** Stateless personal navigation drawer. Services and navigation remain owned by its caller. */
@Composable
fun PersonalNavigationDrawer(
    visible: Boolean,
    user: PersonalDrawerUser?,
    onDismissRequest: () -> Unit,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
    onFriendNetworkClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onRecentWorldsClick: () -> Unit,
    onNameplateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    if (!visible) return
    val drawerDescription = strings.personalDrawerTitle
    val closeText = strings.close
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val drawerWidth = (this.maxWidth * .82f).coerceAtMost(360.dp)
            Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidth)
                        .semantics { contentDescription = drawerDescription },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                    ) {
                        PersonalHeader(user, onProfileClick, onStatusClick)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        DrawerItem(AppIcons.Person, strings.drawerMyProfile, onProfileClick)
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = .42f))
                        .clickable(role = Role.Button, onClickLabel = closeText, onClick = onDismissRequest)
                        .semantics { contentDescription = closeText },
                )
            }
        }
    }
}

@Composable
private fun PersonalHeader(
    user: PersonalDrawerUser?,
    onProfileClick: () -> Unit,
    onStatusClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        UserStateIcon(
            modifier = Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onProfileClick),
            iconUrl = user?.avatarUrl,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clickable(onClick = onProfileClick)) {
            Text(user?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("@${user?.username.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onStatusClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(Modifier.size(10.dp), shape = CircleShape, color = GameColor.Status.fromValue(user?.status)) {}
            Text(user?.statusDescription.orEmpty().ifBlank { user?.status?.value.orEmpty() }, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, text: String, onClick: () -> Unit, error: Boolean = false) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(text, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
