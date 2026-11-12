package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.AImage
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppSheetState
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivitySummary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
internal fun FriendActivitySection(
    summary: FriendActivitySummary,
    events: List<FriendActivityEvent>,
) {
    var showRecentActivity by remember(summary.friendUserId) { mutableStateOf(false) }

    if (showRecentActivity) {
        RecentActivityBottomSheet(
            events = events,
            onDismiss = { showRecentActivity = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(
            text = strings.friendActivityTitle,
            style = AppTheme.type.headline,
            color = AppTheme.colors.label,
        )
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.secondaryGroupedBackground,
            shape = AppShapes.m,
        ) {
            Column {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActivityMetricRow(
                        leftLabel = strings.friendActivityLastTogether,
                        leftValue = summary.lastSeenTogetherAtMillis.asActivityTime(),
                        rightLabel = strings.friendActivityLastActivity,
                        rightValue = summary.lastActivityAtMillis.asActivityTime(),
                    )
                    ActivityMetricRow(
                        leftLabel = strings.friendActivityMeetingCount,
                        leftValue = summary.meetingCount.toString(),
                        rightLabel = strings.friendActivityTogetherTime,
                        rightValue = summary.togetherDurationMillis.asActivityDuration(),
                    )
                }

                if (events.isNotEmpty()) {
                    AppButton(
                        onClick = { showRecentActivity = true },
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .fillMaxWidth(),
                        shape = AppShapes.m,
                        containerColor = AppTheme.colors.fill,
                        contentColor = AppTheme.colors.label,
                        style = AppButtonStyle.Tinted,
                    ) {
                        AppText(strings.friendActivityShowTimeline)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivityBottomSheet(
    events: List<FriendActivityEvent>,
    onDismiss: () -> Unit,
) {
    AppSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberAppSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                AppText(
                    text = strings.friendActivityLastActivity,
                    style = AppTheme.type.title2,
                    color = AppTheme.colors.tint,
                )
                AppText(
                    text = strings.friendActivityObservedHint,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            itemsIndexed(
                items = events,
                key = { _, event -> event.id },
            ) { index, event ->
                if (index > 0) {
                    AppDivider(color = AppTheme.colors.separator)
                }
                ActivityEventRow(
                    event = event,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
internal fun ActivityEventRow(
    event: FriendActivityEvent,
    modifier: Modifier = Modifier,
    onWorldClick: (() -> Unit)? = null,
) {
    val detail = event.activityDetail()
    val bioDiff = remember(event.id, event.previousValue, event.currentValue) {
        if (event.type == FriendActivityEventType.BioChanged) {
            friendActivityBioDiff(event.previousValue, event.currentValue)
        } else {
            emptyList()
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppSurface(
            modifier = Modifier.padding(top = 7.dp).size(8.dp),
            shape = CircleShape,
            color = AppTheme.colors.tint,
            content = {},
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppText(
                text = event.activityLabel(),
                style = AppTheme.type.subheadline,
                fontWeight = FontWeight.Medium,
            )
            if (event.type == FriendActivityEventType.AvatarChanged) {
                AvatarChangePreview(event.previousValue, event.currentValue)
            } else if (bioDiff.isNotEmpty()) {
                BioDiffLines(bioDiff)
            } else if (detail != null) {
                val hasWorld = event.worldId?.isNotBlank() == true
                AppText(
                    text = detail,
                    modifier = Modifier.then(
                        if (hasWorld && onWorldClick != null) {
                            Modifier.clickable(onClick = onWorldClick)
                        } else {
                            Modifier
                        }
                    ),
                    style = AppTheme.type.caption1,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppText(
            text = event.occurredAtMillis.asActivityTime(),
            style = AppTheme.type.caption1,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun AvatarChangePreview(previousUrl: String?, currentUrl: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AvatarChangeImage(previousUrl, strings.friendActivityAvatarBefore, Modifier.weight(1f))
        AvatarChangeImage(currentUrl, strings.friendActivityAvatarAfter, Modifier.weight(1f))
    }
}

@Composable
private fun AvatarChangeImage(imageUrl: String?, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(label, style = AppTheme.type.caption1, color = AppTheme.colors.secondaryLabel)
        // 64dp 是时间线缩略图的最大边长；窄窗口按各自列宽缩小，文字不限制高度。
        AImage(
            imageData = imageUrl.orEmpty(),
            modifier = Modifier.widthIn(max = 64.dp).fillMaxWidth().aspectRatio(1f).clip(AppShapes.s),
        )
    }
}

@Composable
private fun BioDiffLines(lines: List<FriendActivityBioDiffLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            AppText(
                text = if (line.added) "+ ${line.text}" else "- ${line.text}",
                style = AppTheme.type.caption1,
                color = if (line.added) AppTheme.colors.success else AppTheme.colors.destructive,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
private fun ActivityMetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityMetric(leftLabel, leftValue, Modifier.weight(1f))
        ActivityMetric(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun ActivityMetric(label: String, value: String, modifier: Modifier = Modifier) {
    AppSurface(
        modifier = modifier,
        color = AppTheme.colors.tertiaryGroupedBackground,
        shape = AppShapes.s,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppText(
                text = label,
                style = AppTheme.type.caption1Emphasized,
                color = AppTheme.colors.secondaryLabel,
            )
            AppText(
                text = value,
                style = AppTheme.type.subheadline,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalTime::class)
private fun Long?.asActivityTime(): String = this?.let {
    Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).ignoredFormat
} ?: strings.unknown

@Composable
private fun Long.asActivityDuration(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        strings.friendActivityDurationHours
            .replace("%hours%", hours.toString())
            .replace("%minutes%", minutes.toString())
    } else {
        strings.friendActivityDurationMinutes.replace("%minutes%", minutes.toString())
    }
}

@Composable
internal fun FriendActivityEvent.activityLabel(): String = when (type) {
    FriendActivityEventType.Online -> strings.friendActivityEventOnline
    FriendActivityEventType.Offline -> strings.friendActivityEventOffline
    FriendActivityEventType.LocationChanged -> strings.friendActivityEventLocation
    FriendActivityEventType.StatusChanged -> strings.friendActivityEventStatus
    FriendActivityEventType.BioChanged -> strings.friendActivityEventBio
    FriendActivityEventType.DisplayNameChanged -> strings.friendActivityEventDisplayName
    FriendActivityEventType.AvatarChanged -> strings.friendActivityEventAvatar
    FriendActivityEventType.Met -> strings.friendActivityEventMet
    FriendActivityEventType.Left -> strings.friendActivityEventLeft
}

@Composable
internal fun FriendActivityEvent.activityDetail(): String? {
    val location = worldName?.takeIf(String::isNotBlank)
        ?: worldId?.takeIf(String::isNotBlank)
    val access = accessType?.activityLabel()
    val status = currentValue?.activityStatus()
    val parts = when (type) {
        FriendActivityEventType.Online,
        FriendActivityEventType.LocationChanged,
        -> listOfNotNull(location, access, status)
        FriendActivityEventType.Offline,
        FriendActivityEventType.Met,
        FriendActivityEventType.Left,
        -> listOfNotNull(location, access)
        FriendActivityEventType.StatusChanged -> listOfNotNull(status)
        FriendActivityEventType.BioChanged -> emptyList()
        FriendActivityEventType.DisplayNameChanged -> listOfNotNull(previousValue, currentValue)
        FriendActivityEventType.AvatarChanged -> emptyList()
    }
    val separator = if (type == FriendActivityEventType.DisplayNameChanged) " → " else " · "
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(separator)
}

@Composable
private fun FriendActivityAccessType.activityLabel(): String = when (this) {
    FriendActivityAccessType.Public -> strings.friendActivityAccessPublic
    FriendActivityAccessType.FriendsPlus -> strings.friendActivityAccessFriendsPlus
    FriendActivityAccessType.Friends -> strings.friendActivityAccessFriends
    FriendActivityAccessType.InvitePlus -> strings.friendActivityAccessInvitePlus
    FriendActivityAccessType.Invite -> strings.friendActivityAccessInvite
    FriendActivityAccessType.Group -> strings.friendActivityAccessGroup
    FriendActivityAccessType.Unknown -> strings.friendActivityAccessUnknown
}

@Composable
private fun String.activityStatus(): String? {
    val values = lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    if (values.isEmpty()) return null
    val status = when (values.first().lowercase().replace(" ", "")) {
        "active" -> strings.editProfileStatusOnline
        "joinme" -> strings.editProfileStatusJoinMe
        "askme" -> strings.editProfileStatusAskMe
        "busy" -> strings.editProfileStatusBusy
        else -> values.first()
    }
    return (listOf(status) + values.drop(1)).joinToString(" · ")
}
