package io.github.vrcmteam.vrcm.presentation.screens.home.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal data class NotificationUserPresentation(
    val imageUrl: String,
    val displayName: String,
)

internal class BoopNotificationResolver(
    maxConcurrentFetches: Int = 4,
) {
    private val fallbackSemaphore = Semaphore(maxConcurrentFetches)
    private val fallbackCacheLock = Mutex()
    private val fallbackCache = mutableMapOf<String, NotificationUserPresentation>()

    init {
        require(maxConcurrentFetches > 0)
    }

    suspend fun resolve(
        notifications: List<NotificationItemData>,
        friends: Map<String, NotificationUserPresentation>,
        fetchUser: suspend (String) -> NotificationUserPresentation,
    ): List<NotificationItemData> {
        val cachedFallbacks = fallbackCacheLock.withLock { fallbackCache.toMap() }
        val missingUserIds = notifications.asSequence()
            .filter { it.type.equals("boop", ignoreCase = true) }
            .mapNotNull { it.senderId }
            .distinct()
            .filterNot { it in friends || it in cachedFallbacks }
            .toList()

        val fetchedFallbacks = coroutineScope {
            missingUserIds.map { userId ->
                async {
                    fetchFallback(userId, fetchUser)?.let { userId to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (fetchedFallbacks.isNotEmpty()) {
            fallbackCacheLock.withLock { fallbackCache.putAll(fetchedFallbacks) }
        }

        val presentations = cachedFallbacks + fetchedFallbacks + friends
        return notifications.map { notification ->
            if (!notification.type.equals("boop", ignoreCase = true)) return@map notification
            val presentation = notification.senderId?.let(presentations::get)
                ?: return@map notification
            notification.copy(
                imageUrl = presentation.imageUrl,
                title = notification.title ?: presentation.displayName,
            )
        }
    }

    private suspend fun fetchFallback(
        userId: String,
        fetchUser: suspend (String) -> NotificationUserPresentation,
    ): NotificationUserPresentation? = fallbackSemaphore.withPermit {
        try {
            fetchUser(userId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
