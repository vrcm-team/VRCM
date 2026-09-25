package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Resolves user profiles through an account-bound cache with shared request deduplication.
 * Ordinary profile failures are omitted from batch results and briefly cooled down.
 */
@OptIn(ExperimentalTime::class)
class UserProfileEnrichmentService internal constructor(
    private val fetchUser: suspend (String) -> UserData,
    private val currentSessionToken: () -> AccountSessionToken?,
    private val requestScope: CoroutineScope,
    sessionTokens: Flow<AccountSessionToken?> = emptyFlow(),
    maxConcurrentRequests: Int = DEFAULT_MAX_CONCURRENT_REQUESTS,
    private val failureCooldownMillis: Long = DEFAULT_FAILURE_COOLDOWN_MILLIS,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    constructor(usersApi: UsersApi) : this(
        usersApi = usersApi,
        requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    internal constructor(
        usersApi: UsersApi,
        requestScope: CoroutineScope,
    ) : this(
        fetchUser = usersApi::fetchUser,
        currentSessionToken = { SharedFlowCentre.currentSession.value?.token },
        requestScope = requestScope,
        sessionTokens = SharedFlowCentre.currentSession
            .map { session -> session?.token }
            .distinctUntilChanged(),
    )

    private sealed interface CachedProfile {
        data class Found(val user: UserData) : CachedProfile
        data class Failed(val retryAtMillis: Long) : CachedProfile
    }

    private sealed interface ProfileLookup {
        data class Cached(val user: UserData?) : ProfileLookup
        data class Pending(val request: Deferred<UserData?>) : ProfileLookup
        data object StaleSession : ProfileLookup
    }

    private sealed interface FetchOutcome {
        data class Found(val user: UserData) : FetchOutcome
        data object Failed : FetchOutcome
    }

    private data class InFlightRequest(
        val id: Long,
        val deferred: Deferred<UserData?>,
    )

    private val cacheMutex = Mutex()
    private val requestSemaphore = Semaphore(maxConcurrentRequests)
    private val cachedProfiles = mutableMapOf<String, CachedProfile>()
    private val inFlightRequests = mutableMapOf<String, InFlightRequest>()
    private var activeSessionToken: AccountSessionToken? = null
    private var nextRequestId = 0L

    init {
        require(maxConcurrentRequests > 0)
        require(failureCooldownMillis >= 0)
        requestScope.launch {
            sessionTokens.collect(::activateSession)
        }
    }

    /** Returns every profile resolved for the supplied session; individual failures are omitted. */
    suspend fun fetchProfiles(
        sessionToken: AccountSessionToken,
        userIds: Iterable<String>,
    ): Map<String, UserData> = coroutineScope {
        userIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .map { userId ->
                async { userId to fetchProfile(sessionToken, userId) }
            }
            .toList()
            .awaitAll()
            .mapNotNull { (userId, user) -> user?.let { userId to it } }
            .toMap()
    }

    private suspend fun fetchProfile(
        sessionToken: AccountSessionToken,
        userId: String,
    ): UserData? {
        if (!isCurrentSession(sessionToken)) return null

        var staleRequests = emptyList<Deferred<UserData?>>()
        val lookup = cacheMutex.withLock {
            if (!isCurrentSession(sessionToken)) return@withLock ProfileLookup.StaleSession
            if (activeSessionToken != sessionToken) {
                staleRequests = inFlightRequests.values.map(InFlightRequest::deferred)
                inFlightRequests.clear()
                cachedProfiles.clear()
                activeSessionToken = sessionToken
            }

            when (val cached = cachedProfiles[userId]) {
                is CachedProfile.Found -> ProfileLookup.Cached(cached.user)
                is CachedProfile.Failed -> {
                    if (nowMillis() < cached.retryAtMillis) {
                        ProfileLookup.Cached(null)
                    } else {
                        cachedProfiles.remove(userId)
                        pendingLookup(sessionToken, userId)
                    }
                }

                null -> pendingLookup(sessionToken, userId)
            }
        }
        staleRequests.forEach { request ->
            request.cancel(CancellationException("User profile session changed"))
        }

        return when (lookup) {
            is ProfileLookup.Cached -> lookup.user
            is ProfileLookup.Pending -> lookup.request.await()
            ProfileLookup.StaleSession -> null
        }.takeIf { isCurrentSession(sessionToken) }
    }

    private fun pendingLookup(
        sessionToken: AccountSessionToken,
        userId: String,
    ): ProfileLookup {
        inFlightRequests[userId]?.let { return ProfileLookup.Pending(it.deferred) }

        val requestId = ++nextRequestId
        val request = requestScope.async(start = CoroutineStart.LAZY) {
            executeFetch(sessionToken, userId, requestId)
        }
        inFlightRequests[userId] = InFlightRequest(requestId, request)
        request.start()
        return ProfileLookup.Pending(request)
    }

    private suspend fun executeFetch(
        sessionToken: AccountSessionToken,
        userId: String,
        requestId: Long,
    ): UserData? {
        val outcome = try {
            FetchOutcome.Found(
                requestSemaphore.withPermit {
                    if (!isCurrentSession(sessionToken)) {
                        throw CancellationException("User profile session changed")
                    }
                    fetchUser(userId)
                },
            )
        } catch (cancelled: CancellationException) {
            discardRequest(userId, requestId)
            throw cancelled
        } catch (_: Exception) {
            FetchOutcome.Failed
        }

        cacheMutex.withLock {
            val currentRequest = inFlightRequests[userId]
            if (currentRequest?.id == requestId) {
                inFlightRequests.remove(userId)
                if (activeSessionToken == sessionToken && isCurrentSession(sessionToken)) {
                    cachedProfiles[userId] = when (outcome) {
                        is FetchOutcome.Found -> CachedProfile.Found(outcome.user)
                        FetchOutcome.Failed -> CachedProfile.Failed(
                            retryAtMillis = nowMillis() + failureCooldownMillis,
                        )
                    }
                }
            }
        }
        return (outcome as? FetchOutcome.Found)?.user
    }

    private suspend fun activateSession(sessionToken: AccountSessionToken?) {
        val staleRequests = cacheMutex.withLock {
            if (currentSessionToken() != sessionToken) return
            if (activeSessionToken == sessionToken) return
            activeSessionToken = sessionToken
            cachedProfiles.clear()
            inFlightRequests.values.map(InFlightRequest::deferred).also {
                inFlightRequests.clear()
            }
        }
        staleRequests.forEach { request ->
            request.cancel(CancellationException("User profile session changed"))
        }
    }

    private suspend fun discardRequest(userId: String, requestId: Long) {
        cacheMutex.withLock {
            if (inFlightRequests[userId]?.id == requestId) {
                inFlightRequests.remove(userId)
            }
        }
    }

    private fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        currentSessionToken() == sessionToken

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_REQUESTS = 4
        const val DEFAULT_FAILURE_COOLDOWN_MILLIS = 30_000L
    }
}
