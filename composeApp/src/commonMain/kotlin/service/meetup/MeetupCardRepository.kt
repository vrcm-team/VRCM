package io.github.vrcmteam.vrcm.service.meetup

import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountCacheInvalidation
import io.github.vrcmteam.vrcm.storage.AccountCacheWriteToken
import io.github.vrcmteam.vrcm.storage.MeetupPhotoArtifactLease
import io.github.vrcmteam.vrcm.storage.meetup.MeetupAppearanceSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupAssetRef
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhoto
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import io.github.vrcmteam.vrcm.storage.meetup.MeetupProfileSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Current offline-first state of one owner's meetup card. */
data class MeetupCardState(
    val config: MeetupCardConfig,
    val photoModel: String?,
    val decorations: Map<DecorationSlot, ResolvedDecoration> = emptyMap(),
    val refreshing: Boolean = false,
    val lastRefresh: MeetupRefreshResult = MeetupRefreshResult.NotStarted,
)

/** Profile decoration positions supported by the meetup card renderer. */
enum class DecorationSlot {
    IconFrame,
    ProfileEffect,
    NameplateEffect,
}

/** Outcome of the most recent remote meetup card refresh. */
sealed interface MeetupRefreshResult {
    data object NotStarted : MeetupRefreshResult

    data object Success : MeetupRefreshResult

    data class Partial(val failedParts: Set<String>) : MeetupRefreshResult

    data class Failed(val reason: Throwable) : MeetupRefreshResult
}

/** Persistable photo input prepared by Task 7 without retaining any Compose preview object. */
data class MeetupPhotoCandidate(
    val source: MeetupPhotoSource,
    val sourceId: String?,
    val sourceUrl: String?,
    val fileName: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val portraitCrop: MeetupCrop,
    val landscapeCrop: MeetupCrop,
)

/** Supplies the already-authenticated current user without starting another request. */
fun interface MeetupCurrentUserSnapshotProvider {
    fun snapshot(ownerId: String): MeetupProfileSnapshot?
}

/** Offline-first read, refresh and mutation boundary for meetup cards. */
interface MeetupCardRepository {
    fun hasConfig(ownerId: String): Boolean

    fun observe(ownerId: String): StateFlow<MeetupCardState>

    suspend fun ensureDefault(ownerId: String): MeetupCardConfig

    fun refresh(ownerId: String): Job

    suspend fun update(
        ownerId: String,
        transform: (MeetupCardConfig) -> MeetupCardConfig,
    )

    suspend fun replacePhoto(ownerId: String, candidate: MeetupPhotoCandidate): Result<Unit>
}

/** Default repository coordinating Settings, private assets and remote profile data. */
class DefaultMeetupCardRepository(
    private val configDao: MeetupCardConfigDao,
    private val assetStore: MeetupCardAssetStore,
    private val remote: MeetupCardRemoteDataSource,
    private val decorationResolver: DecorationResolver,
    private val accountCacheManager: AccountCacheManager,
    private val currentUserSnapshotProvider: MeetupCurrentUserSnapshotProvider,
    private val scope: CoroutineScope,
) : MeetupCardRepository {
    private val commitMutex = Mutex()
    private val stateLock = SynchronizedObject()
    private val operationLock = SynchronizedObject()
    private val states = mutableMapOf<String, StateHolder>()
    private val refreshSequences = mutableMapOf<String, Long>()
    private val photoSequences = mutableMapOf<String, Long>()
    private val activeRefreshJobs = mutableMapOf<String, Job>()

    init {
        accountCacheManager.addInvalidationListener(::resetInvalidatedStates)
    }

    override fun hasConfig(ownerId: String): Boolean = configDao.load(ownerId) != null

    override fun observe(ownerId: String): StateFlow<MeetupCardState> = state(ownerId).asStateFlow()

    override suspend fun ensureDefault(ownerId: String): MeetupCardConfig {
        val token = accountCacheManager.captureWriteToken(ownerId)
        var result = defaultConfig(ownerId)
        accountCacheManager.mutateIfCurrent(token) {
            commitMutex.withLock {
                result = loadOrCreateLocked(ownerId).also { config ->
                    updateState(ownerId) { it.copy(config = config) }
                }
            }
        }
        return result
    }

    override fun refresh(ownerId: String): Job {
        val token = accountCacheManager.captureWriteToken(ownerId)
        var previous: Job? = null
        val job = synchronized(operationLock) {
            val sequence = (refreshSequences[ownerId] ?: 0L) + 1L
            refreshSequences[ownerId] = sequence
            val identity = RefreshIdentity(token, sequence)
            scope.launch(start = CoroutineStart.LAZY) {
                var photoLease: MeetupPhotoArtifactLease? = null
                var primaryFailure: Throwable? = null
                try {
                    val acquiredLease = accountCacheManager.acquirePhotoArtifactLease(token)
                        ?: return@launch
                    photoLease = acquiredLease
                    try {
                        refreshOwner(identity, acquiredLease)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        finishUnexpected(identity, error)
                    }
                } catch (failure: Throwable) {
                    primaryFailure = failure
                    throw failure
                } finally {
                    try {
                        withContext(NonCancellable) {
                            finishRefresh(identity, photoLease)
                        }
                    } catch (cleanupFailure: Throwable) {
                        primaryFailure?.let { primary ->
                            if (cleanupFailure !== primary) primary.addSuppressed(cleanupFailure)
                        } ?: throw cleanupFailure
                    }
                }
            }.also { created ->
                previous = activeRefreshJobs.put(ownerId, created)
            }
        }
        previous?.cancel()
        job.start()
        return job
    }

    override suspend fun update(
        ownerId: String,
        transform: (MeetupCardConfig) -> MeetupCardConfig,
    ) {
        val token = accountCacheManager.captureWriteToken(ownerId)
        accountCacheManager.mutateIfCurrent(token) {
            commitMutex.withLock {
                val old = loadOrCreateLocked(ownerId)
                val updated = transform(old).copy(
                    ownerUserId = ownerId,
                    revision = old.revision + 1L,
                )
                configDao.save(updated)
                updateState(ownerId) { it.copy(config = updated) }
            }
        }
    }

    override suspend fun replacePhoto(
        ownerId: String,
        candidate: MeetupPhotoCandidate,
    ): Result<Unit> = try {
        require(candidate.bytes.isNotEmpty()) { "Meetup photo is empty" }
        val token = accountCacheManager.captureWriteToken(ownerId)
        val identity = synchronized(operationLock) {
            val sequence = (photoSequences[ownerId] ?: 0L) + 1L
            photoSequences[ownerId] = sequence
            PhotoIdentity(token, sequence)
        }
        var photoCommitted = false
        val committed = accountCacheManager.mutateIfCurrent(token) {
            if (!isCurrentPhoto(identity)) return@mutateIfCurrent
            val asset = assetStore.writePhoto(
                ownerId = ownerId,
                bytes = candidate.bytes.copyOf(),
                extension = safeExtension(candidate.fileName),
            )
            if (!isCurrentPhoto(identity)) {
                deletePhotoIfUnreferenced(ownerId, asset)
                return@mutateIfCurrent
            }
            val photo = MeetupPhoto(
                source = candidate.source,
                sourceId = candidate.sourceId,
                sourceUrl = candidate.sourceUrl,
                localAsset = asset,
                width = candidate.width.coerceAtLeast(0),
                height = candidate.height.coerceAtLeast(0),
            )
            var cleanupPhoto = false
            commitMutex.withLock {
                synchronized(operationLock) {
                    if (photoSequences[ownerId] != identity.sequence) {
                        cleanupPhoto = true
                    } else {
                        val old = loadOrCreateLocked(ownerId)
                        val updated = old.copy(
                            revision = old.revision + 1L,
                            photo = photo,
                            profileBackgroundFallback = if (
                                photo.source == MeetupPhotoSource.ProfileBackground
                            ) {
                                photo
                            } else {
                                old.profileBackgroundFallback
                            },
                            portraitCrop = candidate.portraitCrop,
                            landscapeCrop = candidate.landscapeCrop,
                        )
                        configDao.save(updated)
                        updateState(ownerId) {
                            it.copy(
                                config = updated,
                                photoModel = assetStore.model(asset),
                            )
                        }
                        photoCommitted = true
                    }
                }
            }
            if (cleanupPhoto) deletePhotoIfUnreferenced(ownerId, asset)
        }
        if (!committed || !photoCommitted) throw StaleAccountException()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun refreshOwner(
        identity: RefreshIdentity,
        photoLease: MeetupPhotoArtifactLease,
    ) {
        val ownerId = identity.token.userId
        var capturedStart: RefreshStart? = null
        val started = accountCacheManager.mutateIfCurrent(identity.token) {
            commitMutex.withLock {
                if (!isCurrentRefresh(identity)) return@withLock
                val config = loadOrCreateLocked(ownerId)
                updateState(ownerId) { it.copy(config = config, refreshing = true) }
                capturedStart = RefreshStart(identity, config)
            }
        }
        if (!started || capturedStart == null) return
        val start = checkNotNull(capturedStart)

        val (profileResult, appearanceResult) = coroutineScope {
            val profile = async { fetchResult { remote.getProfile(ownerId).validatedFor(ownerId) } }
            val appearance = async { fetchResult { remote.getAppearance(ownerId).validatedFor(ownerId) } }
            profile.await() to appearance.await()
        }

        if (!isCurrentRefresh(start.identity)) return

        val photoResolution = resolvePhoto(start, start.config, photoLease)

        if (profileResult is FetchResult.Failure && appearanceResult is FetchResult.Failure) {
            finishFailed(start, profileResult.error, photoResolution)
            return
        }

        val backgroundResult = when (profileResult) {
            is FetchResult.Success -> profileResult.value.profileBackgroundUrl
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let { url ->
                    fetchResult {
                        downloadPhoto(
                            identity = start.identity,
                            source = MeetupPhotoSource.ProfileBackground,
                            sourceId = null,
                            sourceUrl = url,
                            fileName = url,
                            width = 0,
                            height = 0,
                            photoLease = photoLease,
                        )
                    }
                }
            is FetchResult.Failure -> null
        }

        var appearanceForResolution = latestAppearance(identity, appearanceResult) ?: return
        while (isCurrentRefresh(identity)) {
            val decorationLease = accountCacheManager.acquireDecorationLease(
                token = start.identity.token,
                templateIds = decorationTemplateIds(appearanceForResolution).toSet(),
            ) ?: return
            var primaryFailure: Throwable? = null
            try {
                val decorationResult = fetchResult {
                    resolveDecorations(appearanceForResolution)
                }
                val resolvedDecorations = when (decorationResult) {
                    is FetchResult.Success -> decorationResult.value
                    is FetchResult.Failure -> restoreDecorations(appearanceForResolution)
                }
                var retryAppearance: MeetupAppearanceSnapshot? = null
                var committed = false
                accountCacheManager.mutateIfCurrent(start.identity.token) {
                    commitMutex.withLock {
                        if (!isCurrentRefresh(start.identity)) return@withLock
                        val latest = configDao.load(ownerId) ?: return@withLock
                        val mergedAppearance = latest.appearance.merge(appearanceResult)
                        if (mergedAppearance != appearanceForResolution) {
                            retryAppearance = mergedAppearance
                            return@withLock
                        }
                        val currentState = state(ownerId).value
                        var mergedPhoto = latest.photo
                        var mergedPhotoModel = currentState.photoModel
                        if (latest.photo == start.config.photo) {
                            photoResolution.replacement?.let { repaired ->
                                mergedPhoto = repaired.photo
                                mergedPhotoModel = repaired.model
                            }
                            if (photoResolution.replacement == null && photoResolution.model != null) {
                                mergedPhotoModel = photoResolution.model
                            }
                        }
                        var profileBackgroundFallback = latest.profileBackgroundFallback
                        photoResolution.replacement
                            ?.takeIf { repaired ->
                                repaired.photo.source == MeetupPhotoSource.ProfileBackground &&
                                    (
                                        latest.photo == start.config.photo ||
                                            latest.photo.source != MeetupPhotoSource.ProfileBackground
                                        )
                            }
                            ?.let { repaired -> profileBackgroundFallback = repaired.photo }
                        if (backgroundResult is FetchResult.Success) {
                            profileBackgroundFallback = backgroundResult.value.photo
                            if (
                                latest.photo.source == MeetupPhotoSource.ProfileBackground &&
                                latest.photo == start.config.photo
                            ) {
                                mergedPhoto = backgroundResult.value.photo
                                mergedPhotoModel = backgroundResult.value.model
                            }
                        }
                        val merged = latest.copy(
                            revision = latest.revision + 1L,
                            profile = when (profileResult) {
                                is FetchResult.Success -> profileResult.value.toSnapshot(
                                    fallbackDisplayName = latest.profile.displayName,
                                )
                                is FetchResult.Failure -> latest.profile
                            },
                            appearance = mergedAppearance,
                            photo = mergedPhoto,
                            profileBackgroundFallback = profileBackgroundFallback,
                        )
                        configDao.save(merged)
                        val failedParts = buildSet {
                            if (profileResult is FetchResult.Failure) add(PROFILE_PART)
                            if (appearanceResult is FetchResult.Failure) add(APPEARANCE_PART)
                            if (
                                decorationResult is FetchResult.Failure ||
                                resolvedDecorations.values.any {
                                    it.mode == DecorationRenderMode.Unavailable
                                }
                            ) {
                                add(DECORATIONS_PART)
                            }
                            if (backgroundResult is FetchResult.Failure) add(PROFILE_BACKGROUND_PART)
                            if (photoResolution.failed) add(PHOTO_PART)
                        }
                        updateState(ownerId) {
                            it.copy(
                                config = merged,
                                photoModel = mergedPhotoModel,
                                decorations = resolvedDecorations,
                                lastRefresh = if (failedParts.isEmpty()) {
                                    MeetupRefreshResult.Success
                                } else {
                                    MeetupRefreshResult.Partial(failedParts)
                                },
                            )
                        }
                        committed = true
                    }
                }
                if (committed) return
                appearanceForResolution = retryAppearance ?: return
            } catch (failure: Throwable) {
                primaryFailure = failure
                throw failure
            } finally {
                try {
                    withContext(NonCancellable) {
                        accountCacheManager.releaseDecorationLease(decorationLease)
                    }
                } catch (cleanupFailure: Throwable) {
                    primaryFailure?.let { primary ->
                        if (cleanupFailure !== primary) primary.addSuppressed(cleanupFailure)
                    } ?: throw cleanupFailure
                }
            }
        }
    }

    private suspend fun latestAppearance(
        identity: RefreshIdentity,
        result: FetchResult<MeetupRemoteAppearance>,
    ): MeetupAppearanceSnapshot? {
        var appearance: MeetupAppearanceSnapshot? = null
        accountCacheManager.mutateIfCurrent(identity.token) {
            commitMutex.withLock {
                if (!isCurrentRefresh(identity)) return@withLock
                appearance = configDao.load(identity.token.userId)?.appearance?.merge(result)
            }
        }
        return appearance
    }

    private suspend fun finishFailed(
        start: RefreshStart,
        error: Exception,
        photoResolution: PhotoResolution,
    ) {
        accountCacheManager.mutateIfCurrent(start.identity.token) {
            commitMutex.withLock {
                if (!isCurrentRefresh(start.identity)) return@withLock
                val ownerId = start.config.ownerUserId
                val latest = configDao.load(ownerId) ?: return@withLock
                val canRepair = latest.photo == start.config.photo
                val repaired = photoResolution.replacement?.takeIf { canRepair }
                val repairedFallback = photoResolution.replacement?.takeIf { replacement ->
                    replacement.photo.source == MeetupPhotoSource.ProfileBackground &&
                        (
                            canRepair ||
                                latest.photo.source != MeetupPhotoSource.ProfileBackground
                            )
                }
                val merged = if (repaired != null || repairedFallback != null) {
                    latest.copy(
                        revision = latest.revision + 1L,
                        photo = repaired?.photo ?: latest.photo,
                        profileBackgroundFallback = repairedFallback?.photo
                            ?: latest.profileBackgroundFallback,
                    ).also(configDao::save)
                } else {
                    latest
                }
                updateState(ownerId) { current ->
                    current.copy(
                        config = merged,
                        photoModel = when {
                            repaired != null -> repaired.model
                            canRepair -> photoResolution.model
                            else -> current.photoModel
                        },
                        lastRefresh = MeetupRefreshResult.Failed(error),
                    )
                }
            }
        }
    }

    private suspend fun finishUnexpected(identity: RefreshIdentity, error: Exception) {
        accountCacheManager.mutateIfCurrent(identity.token) {
            commitMutex.withLock {
                if (!isCurrentRefresh(identity)) return@withLock
                val ownerId = identity.token.userId
                val latest = configDao.load(ownerId) ?: return@withLock
                updateState(ownerId) {
                    it.copy(
                        config = latest,
                        lastRefresh = MeetupRefreshResult.Failed(error),
                    )
                }
            }
        }
    }

    private suspend fun stopRefreshing(identity: RefreshIdentity) {
        accountCacheManager.mutateIfCurrent(identity.token) {
            commitMutex.withLock {
                if (!isCurrentRefresh(identity)) return@withLock
                val ownerId = identity.token.userId
                updateState(ownerId) { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun finishRefresh(
        identity: RefreshIdentity,
        photoLease: MeetupPhotoArtifactLease?,
    ) {
        var cleanupFailure: Throwable? = null
        try {
            stopRefreshing(identity)
        } catch (failure: Throwable) {
            cleanupFailure = failure
        }
        if (photoLease != null) {
            try {
                accountCacheManager.releasePhotoArtifactLease(photoLease)
            } catch (failure: Throwable) {
                cleanupFailure?.let { primary ->
                    if (failure !== primary) primary.addSuppressed(failure)
                } ?: run { cleanupFailure = failure }
            }
        }
        cleanupFailure?.let { throw it }
    }

    private suspend fun resolveDecorations(
        appearance: MeetupAppearanceSnapshot,
    ): Map<DecorationSlot, ResolvedDecoration> {
        val idsBySlot = decorationIdsBySlot(appearance)
        val resolved = decorationResolver.refresh(idsBySlot.map(Pair<DecorationSlot, String>::second))
        return buildMap {
            idsBySlot.forEach { (slot, templateId) ->
                resolved[templateId]?.let { put(slot, it) }
            }
        }
    }

    private suspend fun resolvePhoto(
        start: RefreshStart,
        config: MeetupCardConfig,
        photoLease: MeetupPhotoArtifactLease,
    ): PhotoResolution {
        val current = config.photo
        var failed = false
        current.localAsset?.let { asset ->
            try {
                require(assetStore.read(asset).isNotEmpty()) { "Cached meetup photo is empty" }
                return PhotoResolution(model = assetStore.model(asset))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }

        val sourceUrl = current.sourceUrl?.trim().orEmpty()
        if (current.source != MeetupPhotoSource.LocalAlbum && sourceUrl.isNotEmpty()) {
            try {
                val downloaded = downloadPhoto(
                    identity = start.identity,
                    source = current.source,
                    sourceId = current.sourceId,
                    sourceUrl = sourceUrl,
                    fileName = sourceUrl,
                    width = current.width,
                    height = current.height,
                    photoLease = photoLease,
                )
                return PhotoResolution(
                    model = downloaded.model,
                    replacement = downloaded,
                    failed = failed,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }

        config.profileBackgroundFallback?.localAsset?.let { fallback ->
            try {
                require(assetStore.read(fallback).isNotEmpty()) { "Fallback meetup photo is empty" }
                return PhotoResolution(
                    model = assetStore.model(fallback),
                    failed = failed,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        return PhotoResolution(model = null, failed = failed)
    }

    private suspend fun downloadPhoto(
        identity: RefreshIdentity,
        source: MeetupPhotoSource,
        sourceId: String?,
        sourceUrl: String,
        fileName: String,
        width: Int,
        height: Int,
        photoLease: MeetupPhotoArtifactLease,
    ): PreparedPhoto {
        val bytes = remote.loadImage(sourceUrl)
        require(bytes.isNotEmpty()) { "Downloaded meetup photo is empty" }
        var asset: MeetupAssetRef? = null
        val written = accountCacheManager.mutateIfCurrent(identity.token) {
            if (!isCurrentRefresh(identity)) return@mutateIfCurrent
            withContext(NonCancellable) {
                asset = assetStore.writePhoto(
                    ownerId = identity.token.userId,
                    bytes = bytes,
                    extension = safeExtension(fileName),
                ).also { stored ->
                    accountCacheManager.recordPhotoArtifact(photoLease, stored)
                }
            }
        }
        if (!written || asset == null) throw StaleAccountException()
        val storedAsset = checkNotNull(asset)
        return PreparedPhoto(
            photo = MeetupPhoto(
                source = source,
                sourceId = sourceId,
                sourceUrl = sourceUrl,
                localAsset = storedAsset,
                width = width.coerceAtLeast(0),
                height = height.coerceAtLeast(0),
            ),
            model = assetStore.model(storedAsset),
        )
    }

    private fun MeetupAppearanceSnapshot.merge(
        remote: MeetupRemoteAppearance,
    ): MeetupAppearanceSnapshot = copy(
        iconFrameTemplateId = remote.iconFrame?.trim() ?: iconFrameTemplateId,
        profileEffectTemplateId = remote.profileEffect?.trim() ?: profileEffectTemplateId,
        nameplateEffectTemplateId = remote.nameplateEffect?.trim() ?: nameplateEffectTemplateId,
    )

    private fun MeetupAppearanceSnapshot.merge(
        result: FetchResult<MeetupRemoteAppearance>,
    ): MeetupAppearanceSnapshot = when (result) {
        is FetchResult.Success -> merge(result.value)
        is FetchResult.Failure -> this
    }

    private fun safeExtension(fileNameOrUrl: String): String {
        val extension = fileNameOrUrl
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        return extension.takeIf { it in PHOTO_EXTENSIONS } ?: DEFAULT_PHOTO_EXTENSION
    }

    private fun loadOrCreateLocked(ownerId: String): MeetupCardConfig =
        configDao.load(ownerId) ?: defaultConfig(ownerId).also(configDao::save)

    private fun state(ownerId: String): MutableStateFlow<MeetupCardState> {
        val epoch = accountCacheManager.currentInvalidationEpoch()
        return synchronized(stateLock) { stateHolderLocked(ownerId, epoch).flow }
    }

    private fun updateState(
        ownerId: String,
        transform: (MeetupCardState) -> MeetupCardState,
    ) {
        val epoch = accountCacheManager.currentInvalidationEpoch()
        synchronized(stateLock) {
            val holder = stateHolderLocked(ownerId, epoch)
            holder.flow.value = transform(holder.flow.value)
            holder.epoch = epoch
        }
    }

    private fun stateHolderLocked(ownerId: String, epoch: Long): StateHolder =
        states.getOrPut(ownerId) {
            val config = configDao.load(ownerId) ?: defaultConfig(ownerId)
            StateHolder(
                flow = MutableStateFlow(
                    MeetupCardState(
                        config = config,
                        photoModel = initialPhotoModel(config),
                        decorations = restoreDecorations(config.appearance),
                    ),
                ),
                epoch = epoch,
            )
        }

    private fun resetInvalidatedStates(invalidation: AccountCacheInvalidation) {
        val ownerIds = synchronized(stateLock) {
            invalidation.ownerId?.let(::listOf) ?: states.keys.toList()
        }
        ownerIds.forEach { invalidatedOwnerId ->
            val config = configDao.load(invalidatedOwnerId) ?: defaultConfig(invalidatedOwnerId)
            synchronized(stateLock) {
                states[invalidatedOwnerId]
                    ?.takeIf { it.epoch < invalidation.epoch }
                    ?.let { holder ->
                        holder.flow.value = MeetupCardState(
                            config = config,
                            photoModel = initialPhotoModel(config),
                            decorations = restoreDecorations(config.appearance),
                        )
                        holder.epoch = invalidation.epoch
                    }
            }
        }
    }

    private fun initialPhotoModel(config: MeetupCardConfig): String? = sequenceOf(
        config.photo.localAsset,
        config.profileBackgroundFallback?.localAsset,
    ).filterNotNull()
        .mapNotNull { asset -> runCatching { assetStore.model(asset) }.getOrNull() }
        .firstOrNull()

    private fun restoreDecorations(
        appearance: MeetupAppearanceSnapshot,
    ): Map<DecorationSlot, ResolvedDecoration> {
        val idsBySlot = decorationIdsBySlot(appearance)
        val restored = decorationResolver.restoreCached(idsBySlot.map { it.second })
        return buildMap {
            idsBySlot.forEach { (slot, templateId) ->
                restored[templateId]?.let { put(slot, it) }
            }
        }
    }

    private fun decorationTemplateIds(appearance: MeetupAppearanceSnapshot): List<String> =
        decorationIdsBySlot(appearance).map { it.second }

    private fun decorationIdsBySlot(
        appearance: MeetupAppearanceSnapshot,
    ): List<Pair<DecorationSlot, String>> = listOf(
            DecorationSlot.IconFrame to appearance.iconFrameTemplateId,
            DecorationSlot.ProfileEffect to appearance.profileEffectTemplateId,
            DecorationSlot.NameplateEffect to appearance.nameplateEffectTemplateId,
        ).mapNotNull { (slot, rawId) ->
            rawId.trim().takeIf(String::isNotEmpty)?.let { slot to it }
        }

    private fun defaultConfig(ownerId: String): MeetupCardConfig {
        val currentProfile = currentUserSnapshotProvider.snapshot(ownerId)
            ?.takeIf { it.displayName.isNotBlank() }
            ?: MeetupProfileSnapshot(displayName = ownerId)
        return defaultMeetupCardConfig(ownerId).copy(
            template = MeetupCardTemplate.InfoBar,
            showAvatar = false,
            showPronouns = false,
            showLanguages = false,
            showStatus = false,
            showStatusDescription = false,
            showShortText = false,
            showQrCode = false,
            showIconFrame = false,
            showProfileEffect = false,
            showNameplateEffect = false,
            profile = currentProfile,
        )
    }

    private suspend fun <T> fetchResult(block: suspend () -> T): FetchResult<T> = try {
        FetchResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        FetchResult.Failure(error)
    }

    private fun MeetupRemoteProfile.validatedFor(ownerId: String): MeetupRemoteProfile = also {
        require(id == ownerId) { "Remote profile owner does not match the request" }
    }

    private fun MeetupRemoteAppearance.validatedFor(ownerId: String): MeetupRemoteAppearance = also {
        require(id == ownerId) { "Remote appearance owner does not match the request" }
    }

    private fun MeetupRemoteProfile.toSnapshot(
        fallbackDisplayName: String,
    ) = MeetupProfileSnapshot(
        displayName = displayName.takeIf(String::isNotBlank) ?: fallbackDisplayName,
        avatarUrl = avatarUrl,
        pronouns = pronouns,
        languages = languages,
        status = status,
        statusDescription = statusDescription,
    )

    private data class RefreshStart(
        val identity: RefreshIdentity,
        val config: MeetupCardConfig,
    )

    private data class StateHolder(
        val flow: MutableStateFlow<MeetupCardState>,
        var epoch: Long,
    )

    private data class RefreshIdentity(
        val token: AccountCacheWriteToken,
        val sequence: Long,
    )

    private data class PhotoIdentity(
        val token: AccountCacheWriteToken,
        val sequence: Long,
    )

    private data class PreparedPhoto(
        val photo: MeetupPhoto,
        val model: String,
    )

    private data class PhotoResolution(
        val model: String?,
        val replacement: PreparedPhoto? = null,
        val failed: Boolean = false,
    )

    private class StaleAccountException : Exception("Meetup card account generation is stale")

    private sealed interface FetchResult<out T> {
        data class Success<T>(val value: T) : FetchResult<T>

        data class Failure(val error: Exception) : FetchResult<Nothing>
    }

    private fun isCurrentRefresh(identity: RefreshIdentity): Boolean =
        accountCacheManager.isCurrent(identity.token) && synchronized(operationLock) {
            refreshSequences[identity.token.userId] == identity.sequence
        }

    private fun isCurrentPhoto(identity: PhotoIdentity): Boolean =
        accountCacheManager.isCurrent(identity.token) && synchronized(operationLock) {
            photoSequences[identity.token.userId] == identity.sequence
        }

    private suspend fun deletePhotoIfUnreferenced(
        ownerId: String,
        asset: MeetupAssetRef,
    ) {
        val config = configDao.load(ownerId)
        val referenced = config?.photo?.localAsset == asset ||
            config?.profileBackgroundFallback?.localAsset == asset
        if (!referenced) assetStore.deletePhoto(asset)
    }

    private companion object {
        const val PROFILE_PART = "profile"
        const val APPEARANCE_PART = "appearance"
        const val DECORATIONS_PART = "decorations"
        const val PROFILE_BACKGROUND_PART = "profileBackground"
        const val PHOTO_PART = "photo"
        const val DEFAULT_PHOTO_EXTENSION = "webp"
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
