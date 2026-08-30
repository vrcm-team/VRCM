package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.ResponseData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoopNotificationResolverTest {
    @Test
    fun realBoopReplyTargetsUsersApi() {
        val item = NotificationItemData(
            notification(
                link = null,
                responses = listOf(
                    ResponseData(
                        responseData = "",
                        icon = "reply",
                        text = "Boop back",
                        textKey = "notification.boop.reply",
                        type = "boop",
                    )
                )
            )
        )

        assertEquals("reply", item.actions.single().icon)
        assertEquals("usr_sender", item.senderId)
        assertEquals(
            NotificationResponseTarget.BOOP_USER_API,
            item.responseTarget(item.actions.single()),
        )
    }

    @Test
    fun boopEmojiUsesDetailsMetadataWhenPresent() {
        val item = NotificationItemData(
            notification(
                link = null,
                responses = emptyList(),
                details = NotificationData.Data(emojiId = "default_heart"),
            )
        )

        assertEquals("default_heart", item.boopEmojiId)
    }

    @Test
    fun senderUserIdEnrichesBoopWithNonUserLink() = runTest {
        val resolver = BoopNotificationResolver()
        val item = boop(id = "generic-link", link = "world:wrld_example")

        val result = resolver.resolve(
            notifications = listOf(item),
            friends = mapOf(
                "usr_sender" to NotificationUserPresentation(
                    imageUrl = "https://example.com/sender.png",
                    displayName = "Sender",
                )
            ),
        ) {
            error("sender in friend snapshot should not use the network")
        }

        assertEquals("usr_sender", item.senderId)
        assertEquals(null, item.linkedUserId)
        assertEquals("https://example.com/sender.png", result.single().imageUrl)
        assertEquals("Sender", result.single().title)
    }

    @Test
    fun duplicateSenderUsesOneFallbackRequestAndCachesIt() = runTest {
        val resolver = BoopNotificationResolver()
        val notifications = listOf(boop("first"), boop("second"))
        var fetchCount = 0

        val firstResult = resolver.resolve(notifications, friends = emptyMap()) {
            fetchCount++
            NotificationUserPresentation("https://example.com/avatar.png", "Sender")
        }
        val secondResult = resolver.resolve(notifications, friends = emptyMap()) {
            fetchCount++
            error("cached sender should not be fetched again")
        }

        assertEquals(1, fetchCount)
        assertEquals(List(2) { "https://example.com/avatar.png" }, firstResult.map { it.imageUrl })
        assertEquals(firstResult, secondResult)
    }

    @Test
    fun friendSnapshotAvoidsFallbackRequest() = runTest {
        val resolver = BoopNotificationResolver()
        var fetchCount = 0

        val result = resolver.resolve(
            notifications = listOf(boop("cached")),
            friends = mapOf(
                "usr_sender" to NotificationUserPresentation(
                    imageUrl = "https://example.com/friend.png",
                    displayName = "Cached Friend",
                )
            ),
        ) {
            fetchCount++
            error("friend cache hit should not use the network")
        }

        assertEquals(0, fetchCount)
        assertEquals("https://example.com/friend.png", result.single().imageUrl)
        assertEquals("Cached Friend", result.single().title)
    }

    @Test
    fun fallbackCancellationIsPropagated() = runTest {
        val resolver = BoopNotificationResolver()

        assertFailsWith<CancellationException> {
            resolver.resolve(listOf(boop("cancelled")), friends = emptyMap()) {
                throw CancellationException("cancel refresh")
            }
        }
    }

    private fun boop(
        id: String,
        link: String? = null,
    ) = NotificationItemData(
        id = id,
        source = NotificationSource.PIPELINE,
        imageUrl = "",
        title = null,
        message = "sent you a boop",
        createdAt = "2026-07-30T00:00:00Z",
        senderUserId = "usr_sender",
        link = link,
        type = "boop",
        actions = emptyList(),
    )

    private fun notification(
        link: String?,
        responses: List<ResponseData>,
        details: NotificationData.Data? = null,
    ) = NotificationData(
        canDelete = true,
        category = "social",
        createdAt = "2026-07-30T00:00:00Z",
        data = NotificationData.Data(announcementTitle = null, groupName = null),
        details = details,
        expiresAt = "2026-08-30T00:00:00Z",
        expiryAfterSeen = null,
        id = "notification",
        ignoreDND = false,
        imageUrl = null,
        isSystem = false,
        link = link,
        linkText = null,
        linkTextKey = null,
        message = "sent you a boop",
        messageKey = null,
        receiverUserId = "usr_receiver",
        relatedNotificationsId = null,
        requireSeen = false,
        responses = responses,
        seen = false,
        senderUserId = "usr_sender",
        senderUsername = "Sender",
        title = null,
        titleKey = null,
        type = "boop",
        updatedAt = "2026-07-30T00:00:00Z",
        version = 2,
    )
}
