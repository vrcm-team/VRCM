package io.github.vrcmteam.vrcm.storage

import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import io.github.vrcmteam.vrcm.storage.meetup.MeetupAssetRef
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

internal data class AccountCacheWriteToken(
    val userId: String,
    val globalGeneration: Long,
    val accountGeneration: Long,
)

internal data class MeetupDecorationLease(
    val id: Long,
    val templateIds: Set<String>,
)

internal data class AccountCacheInvalidation(
    val ownerId: String?,
    val epoch: Long,
)

internal data class MeetupPhotoArtifactLease(
    val id: Long,
    val ownerId: String,
)

class AccountCacheManager(
    private val friendListCacheDao: FriendListCacheDao,
    private val userProfileCacheStore: UserProfileCacheStore,
    private val friendActivityStore: FriendActivityCacheStore,
    private val meetupCardConfigDao: MeetupCardConfigDao,
    private val meetupCardAssetStore: MeetupCardAssetStore,
) {
    private val lock = SynchronizedObject()
    private var globalGeneration = 0L
    private var invalidationEpoch = 0L
    private val accountGenerations = mutableMapOf<String, Long>()
    private val invalidationListeners = mutableListOf<suspend (AccountCacheInvalidation) -> Unit>()
    private val decorationLeases = mutableMapOf<Long, Set<String>>()
    private var nextDecorationLeaseId = 0L
    private val photoArtifactLeases = mutableMapOf<Long, PhotoArtifactLeaseState>()
    private var nextPhotoArtifactLeaseId = 0L
    private val accountMutationMutex = Mutex()

    internal fun captureWriteToken(userId: String): AccountCacheWriteToken = synchronized(lock) {
        AccountCacheWriteToken(
            userId = userId,
            globalGeneration = globalGeneration,
            accountGeneration = accountGenerations[userId] ?: 0L,
        )
    }

    internal fun isCurrent(token: AccountCacheWriteToken): Boolean = synchronized(lock) {
        token.globalGeneration == globalGeneration &&
            token.accountGeneration == (accountGenerations[token.userId] ?: 0L)
    }

    internal fun currentInvalidationEpoch(): Long = synchronized(lock) { invalidationEpoch }

    internal suspend fun mutateIfCurrent(
        token: AccountCacheWriteToken,
        mutation: suspend () -> Unit,
    ): Boolean {
        accountMutationMutex.lock()
        return try {
            if (!isCurrent(token)) return false
            mutation()
            true
        } finally {
            accountMutationMutex.unlock()
        }
    }

    internal fun addInvalidationListener(
        listener: suspend (AccountCacheInvalidation) -> Unit,
    ) {
        synchronized(lock) { invalidationListeners += listener }
    }

    internal suspend fun acquireDecorationLease(
        token: AccountCacheWriteToken,
        templateIds: Set<String>,
    ): MeetupDecorationLease? {
        accountMutationMutex.lock()
        return try {
            if (!isCurrent(token)) return null
            val validIds = templateIds.filterTo(mutableSetOf(), DECORATION_ID_PATTERN::matches)
            synchronized(lock) {
                MeetupDecorationLease(
                    id = ++nextDecorationLeaseId,
                    templateIds = validIds,
                ).also { lease -> decorationLeases[lease.id] = lease.templateIds }
            }
        } finally {
            accountMutationMutex.unlock()
        }
    }

    internal suspend fun releaseDecorationLease(lease: MeetupDecorationLease) {
        accountMutationMutex.lock()
        try {
            val removed = synchronized(lock) { decorationLeases.remove(lease.id) != null }
            if (removed) meetupCardAssetStore.pruneDecorations(retainedDecorationTemplateIds())
        } finally {
            accountMutationMutex.unlock()
        }
    }

    internal suspend fun acquirePhotoArtifactLease(
        token: AccountCacheWriteToken,
    ): MeetupPhotoArtifactLease? {
        accountMutationMutex.lock()
        return try {
            if (!isCurrent(token)) return null
            synchronized(lock) {
                MeetupPhotoArtifactLease(
                    id = ++nextPhotoArtifactLeaseId,
                    ownerId = token.userId,
                ).also { lease ->
                    photoArtifactLeases[lease.id] = PhotoArtifactLeaseState(lease.ownerId)
                }
            }
        } finally {
            accountMutationMutex.unlock()
        }
    }

    internal fun recordPhotoArtifact(
        lease: MeetupPhotoArtifactLease,
        asset: MeetupAssetRef,
    ) {
        synchronized(lock) {
            val state = checkNotNull(photoArtifactLeases[lease.id]) {
                "Meetup photo artifact lease is no longer active"
            }
            check(state.ownerId == lease.ownerId) { "Meetup photo artifact lease owner changed" }
            state.assets += asset
        }
    }

    internal suspend fun releasePhotoArtifactLease(lease: MeetupPhotoArtifactLease) {
        accountMutationMutex.lock()
        try {
            val released = synchronized(lock) { photoArtifactLeases.remove(lease.id) } ?: return
            val config = meetupCardConfigDao.load(lease.ownerId)
            val retained = buildSet {
                config?.photo?.localAsset?.let(::add)
                config?.profileBackgroundFallback?.localAsset?.let(::add)
                synchronized(lock) {
                    photoArtifactLeases.values
                        .filter { it.ownerId == lease.ownerId }
                        .forEach { addAll(it.assets) }
                }
            }
            released.assets
                .filterNot { it in retained }
                .forEach { meetupCardAssetStore.deletePhoto(it) }
        } finally {
            accountMutationMutex.unlock()
        }
    }

    internal fun saveFriendListIfCurrent(
        token: AccountCacheWriteToken,
        cache: FriendListCache,
    ): Boolean = synchronized(lock) {
        if (token.globalGeneration != globalGeneration ||
            token.accountGeneration != (accountGenerations[token.userId] ?: 0L)
        ) {
            return@synchronized false
        }
        friendListCacheDao.save(token.userId, cache)
        true
    }

    suspend fun clearAccount(userId: String) {
        var invalidation: AccountCacheInvalidation? = null
        accountMutationMutex.lock()
        try {
            synchronized(lock) {
                accountGenerations[userId] = (accountGenerations[userId] ?: 0L) + 1L
                invalidationEpoch++
                friendListCacheDao.clear(userId)
                meetupCardConfigDao.clear(userId)
                invalidation = AccountCacheInvalidation(userId, invalidationEpoch)
            }
            // Room 的清理是挂起调用，只能放在 synchronized 之外。
            userProfileCacheStore.clearOwner(userId)
            meetupCardAssetStore.deleteAccount(userId)
            friendActivityStore.clearAccount(userId)
            meetupCardAssetStore.pruneDecorations(retainedDecorationTemplateIds())
        } finally {
            accountMutationMutex.unlock()
            invalidation?.let { notifyInvalidated(it) }
        }
    }

    suspend fun clearAll() {
        var invalidation: AccountCacheInvalidation? = null
        accountMutationMutex.lock()
        try {
            synchronized(lock) {
                globalGeneration++
                invalidationEpoch++
                accountGenerations.clear()
                friendListCacheDao.clearAll()
                invalidation = AccountCacheInvalidation(null, invalidationEpoch)
            }
            userProfileCacheStore.clearAll()
            friendActivityStore.clearAll()
        } finally {
            accountMutationMutex.unlock()
            invalidation?.let { notifyInvalidated(it) }
        }
    }

    private suspend fun notifyInvalidated(invalidation: AccountCacheInvalidation) {
        val listeners = synchronized(lock) { invalidationListeners.toList() }
        listeners.forEach { listener ->
            // 监听器异常不得让已完成的清理对调用方表现为失败，也不能挡住其余监听器。
            try {
                listener(invalidation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    private fun retainedDecorationTemplateIds(): Set<String> = buildSet {
        meetupCardConfigDao.all().forEach { config ->
            add(config.appearance.iconFrameTemplateId.trim())
            add(config.appearance.profileEffectTemplateId.trim())
            add(config.appearance.nameplateEffectTemplateId.trim())
        }
        synchronized(lock) {
            decorationLeases.values.forEach(::addAll)
        }
    }.filterTo(mutableSetOf(), DECORATION_ID_PATTERN::matches)

    private companion object {
        val DECORATION_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }

    private data class PhotoArtifactLeaseState(
        val ownerId: String,
        val assets: MutableSet<MeetupAssetRef> = mutableSetOf(),
    )
}
