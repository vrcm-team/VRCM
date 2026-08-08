package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.extensions.ignoredFormat
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityEventType
import io.github.vrcmteam.vrcm.service.FriendActivityAccessType
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
    var expanded by remember(summary.friendUserId) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Box(
                    modifier = Modifier.padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.friendActivityTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.friendActivityObservedHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
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

                    if (events.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        val visibleEvents = if (expanded) events else events.take(2)
                        visibleEvents.forEachIndexed { index, event ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            ActivityEventRow(event)
                        }
                    }
                }

                if (events.size > 2) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (expanded) {
                                strings.friendActivityHideTimeline
                            } else {
                                strings.friendActivityShowTimeline
                            }
                        )
                        Icon(
                            imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityEventRow(event: FriendActivityEvent) {
    val detail = event.activityDetail()
    val bioDiff = remember(event.id, event.previousValue, event.currentValue) {
        if (event.type == FriendActivityEventType.BioChanged) {
            friendActivityBioDiff(event.previousValue, event.currentValue)
        } else {
            emptyList()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.padding(top = 7.dp).size(8.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            content = {},
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.activityLabel(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (bioDiff.isNotEmpty()) {
                BioDiffLines(bioDiff)
            } else if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = event.occurredAtMillis.asActivityTime(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun BioDiffLines(lines: List<FriendActivityBioDiffLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            Text(
                text = if (line.added) "+ ${line.text}" else "- ${line.text}",
                style = MaterialTheme.typography.bodySmall,
                color = if (line.added) BioDiffAddedColor else BioDiffRemovedColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal val BioDiffAddedColor = Color(0xFF238B45)
internal val BioDiffRemovedColor = Color(0xFFC43C3C)

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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
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
private fun FriendActivityEvent.activityLabel(): String = when (type) {
    FriendActivityEventType.Online -> strings.friendActivityEventOnline
    FriendActivityEventType.Offline -> strings.friendActivityEventOffline
    FriendActivityEventType.LocationChanged -> strings.friendActivityEventLocation
    FriendActivityEventType.StatusChanged -> strings.friendActivityEventStatus
    FriendActivityEventType.BioChanged -> strings.friendActivityEventBio
    FriendActivityEventType.Met -> strings.friendActivityEventMet
    FriendActivityEventType.Left -> strings.friendActivityEventLeft
}

@Composable
private fun FriendActivityEvent.activityDetail(): String? {
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
    }
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
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
