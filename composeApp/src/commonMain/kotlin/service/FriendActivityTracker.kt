package io.github.vrcmteam.vrcm.service

internal data class FriendActivityObservation(
    val userId: String,
    val displayName: String,
    val profileImageUrl: String,
    val location: String,
    val status: String,
    val statusDescription: String,
    val bio: String,
    val lastActivityAtMillis: Long?,
    val travelingToLocation: String = "",
)

internal enum class FriendSocketPresenceType { Online, Offline }

internal data class FriendSocketPresenceEvent(
    val userId: String,
    val type: FriendSocketPresenceType,
    val occurredAtMillis: Long,
)

enum class FriendActivityAccessType {
    Public,
    FriendsPlus,
    Friends,
    InvitePlus,
    Invite,
    Group,
    Unknown,
}

internal sealed interface FriendMeetingChange {
    val userId: String
    val occurredAtMillis: Long

    data class Started(
        override val userId: String,
        override val occurredAtMillis: Long,
        val worldId: String,
        val accessType: FriendActivityAccessType,
        val announce: Boolean,
    ) : FriendMeetingChange

    data class Ended(
        override val userId: String,
        override val occurredAtMillis: Long,
        val durationMillis: Long,
    ) : FriendMeetingChange

    data class Checkpoint(
        override val userId: String,
        override val occurredAtMillis: Long,
        val durationMillis: Long,
    ) : FriendMeetingChange
}

enum class FriendActivityEventType {
    Online,
    Offline,
    LocationChanged,
    StatusChanged,
    BioChanged,
    Met,
    Left,
}

internal data class FriendActivityEventDraft(
    val userId: String,
    val displayName: String,
    val profileImageUrl: String,
    val type: FriendActivityEventType,
    val occurredAtMillis: Long,
    val previousValue: String? = null,
    val currentValue: String? = null,
    val worldId: String? = null,
    val accessType: FriendActivityAccessType? = null,
)

internal data class FriendActivityBatch(
    val meetings: List<FriendMeetingChange> = emptyList(),
    val events: List<FriendActivityEventDraft> = emptyList(),
)

internal class FriendActivityTracker(
    private val derivePresenceEvents: Boolean = false,
) {
    private data class ActiveMeeting(
        val instanceKey: String,
        val startedAtMillis: Long,
    )

    private val previousByUserId = mutableMapOf<String, FriendActivityObservation>()
    private val activeMeetings = mutableMapOf<String, ActiveMeeting>()
    private val lastKnownInstanceByUserId = mutableMapOf<String, String>()
    private val lastKnownFriendByUserId = mutableMapOf<String, FriendActivityObservation>()
    private val lastSocketPresenceByUserId = mutableMapOf<String, FriendSocketPresenceType>()
    private var hasObservedSelfLocation = false

    fun observe(
        friends: Collection<FriendActivityObservation>,
        selfLocation: String?,
        nowMillis: Long,
    ): FriendActivityBatch {
        val selfInstance = selfLocation.normalizedInstanceKey()
        val canAnnounceNewMeeting = hasObservedSelfLocation
        if (selfLocation != null) hasObservedSelfLocation = true
        val events = mutableListOf<FriendActivityEventDraft>()
        val currentUserIds = friends.mapTo(mutableSetOf(), FriendActivityObservation::userId)
        val meetings = buildList {
            (previousByUserId.keys - currentUserIds).forEach { missingUserId ->
                activeMeetings.remove(missingUserId)?.let { activeMeeting ->
                    add(
                        FriendMeetingChange.Ended(
                            userId = missingUserId,
                            occurredAtMillis = nowMillis,
                            durationMillis =
                                (nowMillis - activeMeeting.startedAtMillis).coerceAtLeast(0L),
                        )
                    )
                }
                previousByUserId.remove(missingUserId)
            }

            friends.forEach { friend ->
                lastKnownFriendByUserId[friend.userId] = friend
                val previous = previousByUserId[friend.userId]
                val friendInstance = friend.location.normalizedInstanceKey()
                if (friendInstance != null) lastKnownInstanceByUserId[friend.userId] = friendInstance
                val sharedInstance = selfInstance?.takeIf { it == friendInstance }
                val activeMeeting = activeMeetings[friend.userId]

                if (previous == null) {
                    previousByUserId[friend.userId] = friend
                    if (sharedInstance != null) {
                        activeMeetings[friend.userId] = ActiveMeeting(sharedInstance, nowMillis)
                        add(
                            FriendMeetingChange.Started(
                                userId = friend.userId,
                                occurredAtMillis = nowMillis,
                                worldId = sharedInstance.substringBefore(':'),
                                accessType = sharedInstance.accessType(),
                                announce = false,
                            )
                        )
                    }
                    return@forEach
                }

                events += friend.eventsSince(previous, nowMillis)

                if (activeMeeting != null && sharedInstance == activeMeeting.instanceKey) {
                    val elapsed = (nowMillis - activeMeeting.startedAtMillis).coerceAtLeast(0L)
                    if (elapsed > 0L) add(FriendMeetingChange.Checkpoint(friend.userId, nowMillis, elapsed))
                }

                if (activeMeeting != null && activeMeeting.instanceKey != sharedInstance) {
                    add(
                        FriendMeetingChange.Ended(
                            userId = friend.userId,
                            occurredAtMillis = nowMillis,
                            durationMillis = (nowMillis - activeMeeting.startedAtMillis).coerceAtLeast(0L),
                        )
                    )
                    activeMeetings.remove(friend.userId)
                }

                if (sharedInstance != null && activeMeetings[friend.userId] == null) {
                    activeMeetings[friend.userId] = ActiveMeeting(sharedInstance, nowMillis)
                    add(
                        FriendMeetingChange.Started(
                            userId = friend.userId,
                            occurredAtMillis = nowMillis,
                            worldId = sharedInstance.substringBefore(':'),
                            accessType = sharedInstance.accessType(),
                            announce = canAnnounceNewMeeting,
                        )
                    )
                }
                previousByUserId[friend.userId] = friend
            }
        }
        return FriendActivityBatch(meetings = meetings, events = events)
    }

    fun observeSocketPresence(
        friends: Collection<FriendActivityObservation>,
        event: FriendSocketPresenceEvent,
    ): FriendActivityBatch {
        if (lastSocketPresenceByUserId.put(event.userId, event.type) == event.type) {
            return FriendActivityBatch()
        }
        val friend = friends.firstOrNull { it.userId == event.userId }
            ?: lastKnownFriendByUserId[event.userId]
            ?: FriendActivityObservation(
                userId = event.userId,
                displayName = "",
                profileImageUrl = "",
                location = "offline",
                status = "offline",
                statusDescription = "",
                bio = "",
                lastActivityAtMillis = null,
            )
        val type = when (event.type) {
            FriendSocketPresenceType.Online -> FriendActivityEventType.Online
            FriendSocketPresenceType.Offline -> FriendActivityEventType.Offline
        }
        val location = friend.location.normalizedInstanceKey() ?: lastKnownInstanceByUserId[event.userId]
        return FriendActivityBatch(events = listOf(FriendActivityEventDraft(
            userId = friend.userId,
            displayName = friend.displayName,
            profileImageUrl = friend.profileImageUrl,
            type = type,
            occurredAtMillis = event.occurredAtMillis,
            currentValue = if (type == FriendActivityEventType.Online) friend.statusValue() else null,
            worldId = location?.substringBefore(':'),
            accessType = location?.accessType(),
        )))
    }

    fun finish(nowMillis: Long): FriendActivityBatch {
        val ended = activeMeetings.map { (userId, active) ->
            FriendMeetingChange.Ended(
                userId = userId,
                occurredAtMillis = nowMillis,
                durationMillis = (nowMillis - active.startedAtMillis).coerceAtLeast(0L),
            )
        }
        activeMeetings.clear()
        previousByUserId.clear()
        lastKnownInstanceByUserId.clear()
        lastKnownFriendByUserId.clear()
        lastSocketPresenceByUserId.clear()
        hasObservedSelfLocation = false
        return FriendActivityBatch(meetings = ended)
    }

    private fun FriendActivityObservation.eventsSince(
        previous: FriendActivityObservation,
        nowMillis: Long,
    ): List<FriendActivityEventDraft> = buildList {
        val previousInstance = previous.activityInstanceKey()
        val currentInstance = activityInstanceKey()
        val locationChanged =
            previousInstance != null && currentInstance != null && previousInstance != currentInstance
        val baseEvent = FriendActivityEventDraft(
            userId = userId,
            displayName = displayName,
            profileImageUrl = profileImageUrl,
            type = FriendActivityEventType.LocationChanged,
            occurredAtMillis = nowMillis,
        )

        when {
            derivePresenceEvents && previousInstance == null && currentInstance != null -> add(
                baseEvent.copy(
                    type = FriendActivityEventType.Online,
                    currentValue = statusValue(),
                    worldId = currentInstance.substringBefore(':'),
                    accessType = currentInstance.accessType(),
                )
            )
            derivePresenceEvents && previousInstance != null && currentInstance == null -> add(
                baseEvent.copy(
                    type = FriendActivityEventType.Offline,
                    worldId = previousInstance.substringBefore(':'),
                    accessType = previousInstance.accessType(),
                )
            )
            locationChanged -> add(
                baseEvent.copy(
                    previousValue = previousInstance.substringBefore(':'),
                    currentValue = statusValue(),
                    worldId = currentInstance.substringBefore(':'),
                    accessType = currentInstance.accessType(),
                )
            )
        }

        if (previousInstance != null && currentInstance != null) {
            val previousStatus = previous.statusValue()
            val currentStatus = statusValue()
            if (!locationChanged && previousStatus != currentStatus) {
                add(
                    baseEvent.copy(
                        type = FriendActivityEventType.StatusChanged,
                        previousValue = previousStatus,
                        currentValue = currentStatus,
                        worldId = null,
                        accessType = null,
                    )
                )
            }
        }

        // A profile update can arrive while the friend is offline or only on the web.
        // Keep it independent from game-presence transitions so the profile diff is usable.
        if (previous.bio != bio) {
            add(
                baseEvent.copy(
                    type = FriendActivityEventType.BioChanged,
                    previousValue = previous.bio,
                    currentValue = bio,
                    worldId = null,
                    accessType = null,
                )
            )
        }
    }

    private fun FriendActivityObservation.statusValue(): String =
        listOf(status.trim(), statusDescription.trim()).filter(String::isNotEmpty).joinToString("\n")

    private fun FriendActivityObservation.activityInstanceKey(): String? =
        location.normalizedInstanceKey()
            ?: travelingToLocation.normalizedInstanceKey().takeIf {
                location.trim() == "traveling"
            }

    private fun String?.normalizedInstanceKey(): String? =
        this?.trim()?.takeIf { it.startsWith("wrld_") && ':' in it }

    private fun String.accessType(): FriendActivityAccessType = when {
        "~private(" in this && "~canRequestInvite" in this -> FriendActivityAccessType.InvitePlus
        "~private(" in this -> FriendActivityAccessType.Invite
        "~friends(" in this -> FriendActivityAccessType.Friends
        "~hidden(" in this -> FriendActivityAccessType.FriendsPlus
        "~group(" in this -> FriendActivityAccessType.Group
        startsWith("wrld_") -> FriendActivityAccessType.Public
        else -> FriendActivityAccessType.Unknown
    }
}
