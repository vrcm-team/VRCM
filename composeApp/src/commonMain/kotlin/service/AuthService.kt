package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_COOKIE
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.attributes.AuthType
import io.github.vrcmteam.vrcm.network.api.attributes.TWO_FACTOR_AUTH_COOKIE
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.auth.data.Presence
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.content.UserContent
import io.github.vrcmteam.vrcm.network.websocket.WebSocketSessionRecovery
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SessionBoundResponse<T>(
    val result: Result<T>,
    val sessionToken: AccountSessionToken,
)

/**
 * 负责辅助登录验证的类
 * 主要作用是统一验证失效时的重试逻辑
 * @author kamosama
 */
class AuthService(
    private val authApi: AuthApi,
    private val accountDao: AccountDao,
    private val cookiesStorage: PersistentCookiesStorage,
    private val accountCacheManager: AccountCacheManager,
) : WebSocketSessionRecovery {
    private var scope = CoroutineScope(Job())
    private val authMutex = Mutex()
    private val currentUserLock = SynchronizedObject()

    private var currentUser: CurrentUserData? = null
    private var socketPresence: Presence? = null
    private var socketPresenceRevision = 0L
    private val _currentUserState = MutableStateFlow<CurrentUserData?>(null)
    val currentUserState: StateFlow<CurrentUserData?> = _currentUserState.asStateFlow()

    private var currentAccountDto: AccountDto? = null

    init {
        scope.launch {
            SharedFlowCentre.authed.collect { session ->
                val accountDto = session.account
                synchronized(currentUserLock) {
                    if (currentUser?.id != accountDto.userId) clearCurrentUserLocked()
                }
                currentAccountDto = accountDto
                accountDao.saveAccountInfo(accountDto)
            }
        }
    }

    fun accountDto(): AccountDto = currentAccountDto ?: accountDao.currentAccountDto()

    fun accountDtoList(): List<AccountDto> = accountDao.accountDtoList()

    fun accountDtoOrNull(): AccountDto? = accountDao.currentAccountDtoOrNull()

    suspend fun restoreAuth(): AuthState? = authMutex.withLock {
        restoreAuthLocked()
    }

    private suspend fun restoreAuthLocked(): AuthState? {
        val account = accountDao.currentAccountDtoOrNull() ?: return null
        // No auth cookie means the user explicitly logged out (or has never logged in).
        // In that state we must not silently recreate a session from the saved password.
        if (account.authCookie.isNullOrBlank()) return null

        applyAuthCookie(account.username)
        val response = authApi.userRes()
        return when (response.status) {
            HttpStatusCode.OK -> {
                emitAuthed(account.password, response).getOrThrow()
                AuthState.Authed
            }

            HttpStatusCode.Unauthorized -> {
                clearAuthCookie(account.userId)
                val password = account.password
                    ?: return AuthState.Unauthorized(response.bodyAsText())
                loginLocked(account.username, password)
            }

            else -> response.checkSuccess<CurrentUserData>().let { error("Unexpected response: $it") }
        }
    }

    private fun applyAuthCookie(username: String) {
        cookiesStorage.removeCookie(AUTH_COOKIE)
        cookiesStorage.removeCookie(TWO_FACTOR_AUTH_COOKIE)
        accountDao.accountDtoByUserName(username)
            ?.let {
                cookiesStorage.addCookie(AUTH_COOKIE, it.authCookie)
                cookiesStorage.addCookie(TWO_FACTOR_AUTH_COOKIE, it.twoFactorAuthCookie)
            }
    }

    suspend fun currentUser(isRefresh: Boolean = false): CurrentUserData {
        val cached = synchronized(currentUserLock) { currentUser }
        if (cached != null && !isRefresh) return cached
        val refreshed = authApi.currentUser()
        return synchronized(currentUserLock) {
            publishCurrentUserLocked(
                refreshed.copy(
                    presence = selectCurrentPresence(refreshed.presence, socketPresence),
                )
            )
        }
    }

    internal suspend fun refreshCurrentUserPresence(
        sessionToken: AccountSessionToken,
    ): CurrentUserData? {
        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return null
        val requestSocketRevision = synchronized(currentUserLock) { socketPresenceRevision }
        val refreshed = authApi.currentUser()
        return synchronized(currentUserLock) {
            if (!SharedFlowCentre.isCurrentSession(sessionToken) ||
                refreshed.id != sessionToken.userId
            ) {
                return@synchronized null
            }
            // A socket event received during this request is newer than its HTTP response.
            val presence = if (socketPresenceRevision == requestSocketRevision) {
                socketPresence = null
                refreshed.presence
            } else {
                selectCurrentPresence(refreshed.presence, socketPresence)
            }
            publishCurrentUserLocked(refreshed.copy(presence = presence))
        }
    }

    internal fun applyCurrentAvatarUpdate(avatarId: String) {
        synchronized(currentUserLock) {
            val existing = currentUser ?: return@synchronized
            publishCurrentUserLocked(existing.copy(currentAvatar = avatarId))
        }
    }

    fun applySocketUserUpdate(user: UserContent) {
        synchronized(currentUserLock) {
            val existing = currentUser ?: return@synchronized
            if (existing.id != user.id) return@synchronized
            publishCurrentUserLocked(
                existing.copy(
                    currentAvatarImageUrl = user.currentAvatarImageUrl,
                    currentAvatarTags = user.currentAvatarTags,
                    currentAvatarThumbnailImageUrl = user.currentAvatarThumbnailImageUrl,
                    displayName = user.displayName,
                    lastActivity = user.lastActivity,
                    lastLogin = user.lastLogin,
                    lastPlatform = user.lastPlatform,
                    profilePicOverride = user.profilePicOverride,
                    state = user.state,
                    status = user.status,
                    statusDescription = user.statusDescription,
                    tags = user.tags,
                    userIcon = user.userIcon,
                    pronouns = user.pronouns,
                )
            )
        }
    }

    fun applySocketUserLocation(location: String, travelingToLocation: String) {
        synchronized(currentUserLock) {
            val existing = currentUser ?: return@synchronized
            val (world, instance) = socketLocationToPresenceParts(location)
            val (travelingToWorld, travelingToInstance) = socketLocationToPresenceParts(travelingToLocation)
            val updatedPresence = existing.presence.copy(
                world = world,
                instance = instance,
                travelingToWorld = travelingToWorld,
                travelingToInstance = travelingToInstance,
            )
            socketPresence = updatedPresence
            socketPresenceRevision++
            publishCurrentUserLocked(existing.copy(presence = updatedPresence))
        }
    }

    fun applyOwnProfileRefresh(user: UserData) {
        synchronized(currentUserLock) {
            val existing = currentUser ?: return@synchronized
            if (existing.id != user.id) return@synchronized
            val (world, instance) = socketLocationToPresenceParts(user.location)
            val (travelingToWorld, travelingToInstance) =
                socketLocationToPresenceParts(user.travelingToLocation.orEmpty())
            val updatedPresence = existing.presence.copy(
                world = world,
                instance = instance,
                travelingToWorld = travelingToWorld,
                travelingToInstance = travelingToInstance,
                platform = user.lastPlatform.ifBlank { existing.presence.platform },
            )
            socketPresence = updatedPresence
            socketPresenceRevision++
            publishCurrentUserLocked(
                existing.copy(
                    currentAvatarImageUrl = user.currentAvatarImageUrl,
                    currentAvatarTags = user.currentAvatarTags,
                    currentAvatarThumbnailImageUrl = user.currentAvatarThumbnailImageUrl,
                    displayName = user.displayName,
                    lastActivity = user.lastActivity,
                    lastLogin = user.lastLogin,
                    lastPlatform = user.lastPlatform,
                    profilePicOverride = user.profilePicOverride,
                    state = user.state.value,
                    status = user.status,
                    statusDescription = user.statusDescription,
                    tags = user.tags,
                    userIcon = user.userIcon,
                    pronouns = user.pronouns,
                    presence = updatedPresence,
                )
            )
        }
    }


    suspend fun verify(
        password: String,
        verifyCode: String,
        authCardPage: AuthCardPage,
    ): Result<Unit> = authMutex.withLock {
        val authType = when (authCardPage) {
            AuthCardPage.EmailCode -> AuthType.Email
            AuthCardPage.TFACode -> AuthType.TFA
            AuthCardPage.TTFACode -> AuthType.TTFA
            else -> error("not supported")
        }
        return authApi.verify(verifyCode, authType)
            .let { if (it.isSuccess) emitAuthed(password) else it }
    }

    private suspend fun emitAuthed(
        password: String? = null,
        response: HttpResponse? = null,
    ): Result<Unit> = runCatching {
        (response ?: authApi.userRes()).let {
            val userData = it.checkSuccess<CurrentUserData>()
            val accountDto = AccountDto(
                userId = userData.id,
                username = userData.username,
                password = password,
                iconUrl = userData.iconUrl,
                authCookie = cookiesStorage.cookieValue(AUTH_COOKIE),
                twoFactorAuthCookie = cookiesStorage.cookieValue(TWO_FACTOR_AUTH_COOKIE),
            )
            synchronized(currentUserLock) {
                publishCurrentUserLocked(
                    userData.copy(
                        presence = selectCurrentPresence(userData.presence, socketPresence),
                    )
                )
            }
            currentAccountDto = accountDto
            accountDao.saveAccountInfo(accountDto)
            SharedFlowCentre.emitAuthenticated(accountDto)
        }
    }

    suspend fun login(username: String, password: String): AuthState = authMutex.withLock {
        loginLocked(username, password)
    }

    private suspend fun loginLocked(
        username: String,
        password: String,
        restoreStoredCookies: Boolean = true,
        invalidateOnIncompleteAuth: Boolean = true,
    ): AuthState {
        val activeUserId = SharedFlowCentre.currentSession.value?.account?.userId
        val targetUserId = accountDao.accountDtoByUserName(username)?.userId
        val isAccountSwitch = activeUserId != null && activeUserId != targetUserId
        if (restoreStoredCookies) applyAuthCookie(username)
        return try {
            authApi.login(username, password).also {
                if (it is AuthState.Authed) {
                    emitAuthed(password).getOrThrow()
                } else if (invalidateOnIncompleteAuth) {
                    invalidateCurrentSessionLocked()
                }
            }
        } catch (error: Throwable) {
            if (isAccountSwitch) invalidateCurrentSessionLocked()
            throw error
        }
    }

    private suspend fun invalidateCurrentSessionLocked() {
        if (SharedFlowCentre.currentSession.value == null) return
        synchronized(currentUserLock) { clearCurrentUserLocked() }
        currentAccountDto = accountDao.currentAccountDtoOrNull()
        SharedFlowCentre.emitLogout()
    }


    /**
     * 如果是失败的结果则会判断是否是验证失效了
     * 如果是则尝试重新登陆
     */
    private suspend fun <T> Result<T>.recoverLogin(callback: suspend () -> Result<T>): Result<T> {
        return when (val exception = exceptionOrNull()) {
            null -> this
            else -> if (exception is VRCApiException && exception.code == HttpStatusCode.Unauthorized.value && doReTryAuth()) {
                callback()
            } else {
                this
            }
        }
    }

    suspend fun doReTryAuth(): Boolean = authMutex.withLock {
        doReTryAuthLocked()
    }

    private suspend fun doReTryAuthLocked(expectedUserId: String? = null): Boolean {
        val accountInfo = accountDao.currentAccountDtoOrNull() ?: return false
        if (expectedUserId != null && accountInfo.userId != expectedUserId) return false
        val password = accountInfo.password ?: return false
        return loginLocked(accountInfo.username, password) is AuthState.Authed
    }

    override suspend fun recoverExpiredSession(sessionToken: AccountSessionToken) = authMutex.withLock {
        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@withLock

        val account = accountDao.currentAccountDtoOrNull()
            ?.takeIf { it.userId == sessionToken.userId }
        val password = account?.password
        if (account == null || password == null) {
            clearAuthCookie(sessionToken.userId)
            invalidateCurrentSessionLocked()
            return@withLock
        }

        // Pipeline rejected this cookie. Keep the two-factor cookie available for the
        // credential request, but do not send the rejected auth cookie again.
        val rejectedAuthCookie = cookiesStorage.cookieValue(AUTH_COOKIE)
        cookiesStorage.removeCookie(AUTH_COOKIE)
        val authState = try {
            loginLocked(
                username = account.username,
                password = password,
                restoreStoredCookies = false,
                invalidateOnIncompleteAuth = false,
            )
        } catch (error: Throwable) {
            // A temporary HTTP or I/O failure must leave the current session intact and
            // allow the next WebSocket attempt to use the existing authenticated state.
            rejectedAuthCookie?.let { cookiesStorage.addCookie(AUTH_COOKIE, it) }
            throw error
        }

        if (authState !is AuthState.Authed && SharedFlowCentre.isCurrentSession(sessionToken)) {
            clearAuthCookie(account.userId)
            invalidateCurrentSessionLocked()
        }
    }

    internal suspend fun <T> runSessionBoundCatching(
        sessionToken: AccountSessionToken,
        callback: suspend () -> T,
    ): SessionBoundResponse<T>? = authMutex.withLock {
        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@withLock null

        val first = runRequestCatching(callback)
        val firstError = first.exceptionOrNull()
        if (firstError !is VRCApiException ||
            firstError.code != HttpStatusCode.Unauthorized.value
        ) {
            return@withLock SessionBoundResponse(first, sessionToken)
        }
        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@withLock null
        val reauthenticated = runRequestCatching {
            doReTryAuthLocked(sessionToken.userId)
        }
        reauthenticated.exceptionOrNull()?.let { error ->
            return@withLock SessionBoundResponse(Result.failure(error), sessionToken)
        }
        if (!reauthenticated.getOrThrow()) {
            return@withLock SessionBoundResponse(first, sessionToken)
        }

        val refreshedSession = SharedFlowCentre.currentSession.value
        if (refreshedSession?.account?.userId != sessionToken.userId ||
            !SharedFlowCentre.isCurrentSession(refreshedSession.token)
        ) {
            return@withLock null
        }
        val retried = runRequestCatching(callback)
        if (!SharedFlowCentre.isCurrentSession(refreshedSession.token)) return@withLock null
        SessionBoundResponse(retried, refreshedSession.token)
    }

    private suspend fun <T> runRequestCatching(callback: suspend () -> T): Result<T> = try {
        Result.success(callback())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    /**
     * 如果验证过期了尝试登陆后再请求一次
     */
    suspend fun <T> reTryAuth(callback: suspend () -> Result<T>): Result<T> =
        callback().recoverLogin(callback)

    suspend fun <T> reTryAuthCatching(callback: suspend () -> T): Result<T> =
        reTryAuth {
            runCatching { callback() }
        }

    suspend fun logout() = authMutex.withLock {
        val userId = SharedFlowCentre.currentSession.value?.account?.userId
            ?: synchronized(currentUserLock) { currentUser?.id }
            ?: accountDto().userId
        clearAuthCookie(userId)
        synchronized(currentUserLock) { clearCurrentUserLocked() }
        currentAccountDto = accountDao.currentAccountDtoOrNull()
        SharedFlowCentre.emitLogout()
    }

    private fun clearAuthCookie(userId: String) {
        cookiesStorage.removeCookie(AUTH_COOKIE)
        cookiesStorage.removeCookie(TWO_FACTOR_AUTH_COOKIE)
        if (userId.isNotEmpty()) accountDao.logout(userId)
    }

    suspend fun removeAccount(userId: String) = runCatching {
        accountCacheManager.clearAccount(userId)
        accountDao.removeAccount(userId)
    }

    private fun publishCurrentUserLocked(user: CurrentUserData): CurrentUserData {
        currentUser = user
        _currentUserState.value = user
        return user
    }

    private fun clearCurrentUserLocked() {
        currentUser = null
        socketPresence = null
        socketPresenceRevision++
        _currentUserState.value = null
    }

}

internal fun socketLocationToPresenceParts(location: String): Pair<String, String> =
    if (location.startsWith("wrld_") && location.contains(':')) {
        location.substringBefore(':') to location.substringAfter(':')
    } else {
        "" to location
    }

internal fun selectCurrentPresence(restPresence: Presence, socketPresence: Presence?): Presence =
    socketPresence ?: restPresence
