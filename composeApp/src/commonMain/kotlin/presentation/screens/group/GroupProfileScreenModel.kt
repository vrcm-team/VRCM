package io.github.vrcmteam.vrcm.presentation.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupGalleryImage
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupMember
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupPost
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.storage.GroupProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.GroupProfileCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal class GroupProfileInitialLoadGate {
    private var initializedGroupId: String? = null

    suspend fun runIfNeeded(groupId: String, load: suspend () -> Unit): Boolean {
        if (initializedGroupId == groupId) return false
        initializedGroupId = groupId
        load()
        return true
    }
}

@OptIn(ExperimentalTime::class)
class GroupProfileScreenModel(
    private val groupsApi: GroupsApi,
    private val usersApi: UsersApi,
    private val authService: AuthService,
    private val logger: Logger,
    private val groupProfileCacheStore: GroupProfileCacheStore,
) : ViewModel() {

    private val _groupProfileState = MutableStateFlow<GroupProfileVo?>(null)
    val groupProfileState: StateFlow<GroupProfileVo?> = _groupProfileState.asStateFlow()

    private val _members = MutableStateFlow<List<GroupMember>>(emptyList())
    val members: StateFlow<List<GroupMember>> = _members.asStateFlow()

    private val _owner = MutableStateFlow<UserData?>(null)
    val owner: StateFlow<UserData?> = _owner.asStateFlow()

    private val _galleryImages = MutableStateFlow<Map<String, List<GroupGalleryImage>>>(emptyMap())
    val galleryImages: StateFlow<Map<String, List<GroupGalleryImage>>> = _galleryImages.asStateFlow()

    private val _posts = MutableStateFlow<List<GroupPost>>(emptyList())
    val posts: StateFlow<List<GroupPost>> = _posts.asStateFlow()

    private val _postAuthors = MutableStateFlow<Map<String, String>>(emptyMap())
    val postAuthors: StateFlow<Map<String, String>> = _postAuthors.asStateFlow()

    private val _postsLoading = MutableStateFlow(false)
    val postsLoading: StateFlow<Boolean> = _postsLoading.asStateFlow()

    private val _postsLoadingMore = MutableStateFlow(false)
    val postsLoadingMore: StateFlow<Boolean> = _postsLoadingMore.asStateFlow()

    private val _postsEndReached = MutableStateFlow(false)
    val postsEndReached: StateFlow<Boolean> = _postsEndReached.asStateFlow()

    private var postsNextOffset = 0

    private val _membersLoading = MutableStateFlow(false)
    val membersLoading: StateFlow<Boolean> = _membersLoading.asStateFlow()

    private val _groupInstances = MutableStateFlow<List<InstanceData>>(emptyList())
    val groupInstances: StateFlow<List<InstanceData>> = _groupInstances.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private val _isRepresentationUpdating = MutableStateFlow(false)
    val isRepresentationUpdating: StateFlow<Boolean> = _isRepresentationUpdating.asStateFlow()

    private val initialLoadGate = GroupProfileInitialLoadGate()
    private val representationCoordinator = GroupRepresentationCoordinator(
        request = NetworkGroupRepresentationRequest(
            groupsApi = groupsApi,
            authService = authService,
        ),
    )
    private var representationJob: Job? = null
    private var representationGeneration = 0L
    private var representationSessionUserId = SharedFlowCentre.currentSession.value?.token?.userId

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextUserId = session?.token?.userId
                if (representationSessionUserId != nextUserId) {
                    cancelRepresentationUpdate()
                }
                representationSessionUserId = nextUserId
            }
        }
    }

    fun loadGroupData(groupProfileVo: GroupProfileVo) {
        val groupId = groupProfileVo.groupId
        val currentGroupId = _groupProfileState.value?.groupId
        if (currentGroupId != null && currentGroupId != groupId) {
            cancelRepresentationUpdate()
        }
        if (groupId.isBlank()) {
            _groupProfileState.value = groupProfileVo
            return
        }
        viewModelScope.launch {
            initialLoadGate.runIfNeeded(groupId) {
                _groupProfileState.value = groupProfileVo

                val cached = groupProfileCacheStore.load(groupId)
                if (cached != null) {
                    _groupProfileState.value = GroupProfileVo(cached.group)
                }
                refreshGroupData(
                    refreshProfile = cached == null || cached.isExpired(nowMilliseconds()),
                )
            }
        }
    }

    fun refreshGroupData() {
        refreshGroupData(refreshProfile = true)
    }

    fun loadMorePosts() {
        val groupId = _groupProfileState.value?.groupId ?: return
        if (_postsLoading.value || _postsLoadingMore.value || _postsEndReached.value) return
        _postsLoadingMore.value = true
        viewModelScope.launch(Dispatchers.IO) {
            loadPosts(groupId, reset = false)
        }
    }

    private fun refreshGroupData(refreshProfile: Boolean) {
        _members.value = emptyList()
        _owner.value = null
        _galleryImages.value = emptyMap()
        _posts.value = emptyList()
        _postAuthors.value = emptyMap()
        _postsLoadingMore.value = false
        _postsEndReached.value = false
        postsNextOffset = 0
        _postsLoading.value = true
        _membersLoading.value = true
        _groupInstances.value = emptyList()
        val groupId = _groupProfileState.value?.groupId.orEmpty()
        if (_isLoading.value || groupId.isBlank()) {
            _postsLoading.value = false
            _membersLoading.value = false
            return
        }
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (refreshProfile) {
                    authService.reTryAuthCatching {
                        groupsApi.fetchGroup(groupId, includeRoles = true)
                    }.onSuccess { groupData ->
                        groupProfileCacheStore.save(
                            GroupProfileCache(
                                group = groupData,
                                cachedAtEpochMilliseconds = nowMilliseconds(),
                            )
                        )
                        if (_groupProfileState.value?.groupId == groupId) {
                            _groupProfileState.value = GroupProfileVo(groupData)
                        }
                    }.onFailure {
                        handleError("GroupProfile", it)
                    }
                }
                val group = _groupProfileState.value
                if (group?.ownerId != null) {
                    loadOwner(group.ownerId)
                }
                if (group != null) {
                    coroutineScope {
                        listOf(
                            async { loadMembers(groupId) },
                        async { loadPosts(groupId, reset = true) },
                            async { loadGroupInstances(groupId) },
                            async { loadGalleryImages(groupId, group.galleries) }
                        ).awaitAll()
                    }
                } else {
                    _postsLoading.value = false
                    _membersLoading.value = false
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun nowMilliseconds(): Long = Clock.System.now().toEpochMilliseconds()

    fun joinGroup() {
        val groupId = _groupProfileState.value?.groupId ?: return
        if (_isActionLoading.value || _isRepresentationUpdating.value) return
        _isActionLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                groupsApi.joinGroup(groupId)
            }.onFailure {
                handleError("GroupJoin", it)
            }.onSuccess {
                refreshGroupData()
            }
            _isActionLoading.value = false
        }
    }

    fun leaveGroup() {
        val groupId = _groupProfileState.value?.groupId ?: return
        if (_isActionLoading.value || _isRepresentationUpdating.value) return
        _isActionLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                groupsApi.leaveGroup(groupId)
            }.onFailure {
                handleError("GroupLeave", it)
            }.onSuccess {
                refreshGroupData()
            }
            _isActionLoading.value = false
        }
    }

    fun updateRepresentation(
        isRepresenting: Boolean,
        failureMessage: String,
        sessionChangedMessage: String,
    ) {
        val group = _groupProfileState.value ?: return
        val sessionToken = SharedFlowCentre.currentSession.value?.token ?: return
        if (_isActionLoading.value ||
            _isRepresentationUpdating.value ||
            !group.hasActiveMembership(sessionToken)
        ) {
            return
        }

        val groupId = group.groupId
        val generation = ++representationGeneration
        _isRepresentationUpdating.value = true
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                when (
                    val result = representationCoordinator.update(
                        group = group,
                        isRepresenting = isRepresenting,
                        sessionToken = sessionToken,
                    )
                ) {
                    is GroupRepresentationUpdateResult.Updated -> {
                        if (acceptsRepresentationResult(groupId, generation, result.sessionToken)) {
                            _groupProfileState.value = GroupProfileVo(result.group)
                            saveRepresentationCache(
                                groupId = groupId,
                                generation = generation,
                                sessionToken = result.sessionToken,
                                group = result.group,
                            )
                        }
                    }

                    is GroupRepresentationUpdateResult.Failed -> {
                        if (acceptsRepresentationResult(groupId, generation, result.sessionToken)) {
                            logger.error("GroupRepresentation: ${result.error.message.orEmpty()}")
                            SharedFlowCentre.toastText.emit(ToastText.Error(failureMessage))
                        }
                    }

                    GroupRepresentationUpdateResult.SessionChanged -> {
                        if (acceptsRepresentationRequest(groupId, generation, sessionToken.userId)) {
                            SharedFlowCentre.toastText.emit(ToastText.Error(sessionChangedMessage))
                        }
                    }

                    GroupRepresentationUpdateResult.InFlight,
                    GroupRepresentationUpdateResult.NotAllowed,
                    GroupRepresentationUpdateResult.Unchanged -> Unit
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                if (representationGeneration == generation) {
                    _isRepresentationUpdating.value = false
                    representationJob = null
                }
            }
        }
        representationJob = job
        job.start()
    }

    private fun acceptsRepresentationResult(
        groupId: String,
        generation: Long,
        sessionToken: AccountSessionToken,
    ): Boolean = representationGeneration == generation &&
        _groupProfileState.value?.groupId == groupId &&
        SharedFlowCentre.isCurrentSession(sessionToken)

    private fun acceptsRepresentationRequest(
        groupId: String,
        generation: Long,
        userId: String,
    ): Boolean = representationGeneration == generation &&
        _groupProfileState.value?.groupId == groupId &&
        SharedFlowCentre.currentSession.value?.token?.userId == userId

    private fun cancelRepresentationUpdate() {
        representationGeneration++
        representationJob?.cancel()
        representationJob = null
        _isRepresentationUpdating.value = false
    }

    private suspend fun saveRepresentationCache(
        groupId: String,
        generation: Long,
        sessionToken: AccountSessionToken,
        group: GroupData,
    ) {
        if (!acceptsRepresentationResult(groupId, generation, sessionToken)) return
        try {
            groupProfileCacheStore.save(
                GroupProfileCache(
                    group = group,
                    cachedAtEpochMilliseconds = nowMilliseconds(),
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logger.error("GroupRepresentationCache: ${error.message.orEmpty()}")
        }
    }

    private suspend fun loadMembers(groupId: String) {
        _membersLoading.value = true
        authService.reTryAuthCatching {
            groupsApi.getGroupMembers(groupId = groupId, n = 24, offset = 0)
        }.onSuccess {
            _members.value = it
        }.onFailure {
            logger.error(it.message.orEmpty())
        }
        _membersLoading.value = false
    }

    private suspend fun loadOwner(ownerId: String) {
        authService.reTryAuthCatching {
            usersApi.fetchUser(ownerId)
        }.onSuccess {
            _owner.value = it
        }.onFailure {
            logger.error(it.message.orEmpty())
        }
    }

    private suspend fun loadPosts(groupId: String, reset: Boolean) {
        if (reset) {
            _postsLoading.value = true
            postsNextOffset = 0
            _postsEndReached.value = false
        } else {
            if (_postsLoading.value || _postsEndReached.value) {
                _postsLoadingMore.value = false
                return
            }
            _postsLoadingMore.value = true
        }
        val offset = postsNextOffset
        authService.reTryAuthCatching {
            groupsApi.getGroupPosts(groupId = groupId, n = POSTS_PAGE_SIZE, offset = offset)
        }.onSuccess { postData ->
            _posts.value = (_posts.value + postData.posts).distinctBy(GroupPost::id)
            postsNextOffset = offset + postData.posts.size
            _postsEndReached.value = postData.posts.isEmpty() ||
                postData.posts.size < POSTS_PAGE_SIZE ||
                (postData.total > 0 && postsNextOffset >= postData.total)
            val authorMap = _postAuthors.value.toMutableMap()
            val authorIds = postData.posts
                .mapNotNull { it.authorId.takeIf { id -> id.isNotBlank() } }
                .filterNot { authorMap.containsKey(it) }
                .distinct()
            // 用有限并发请求获取作者名（避免 429）
            coroutineScope {
                authorIds.chunked(5).forEach { chunk ->
                    chunk.map { userId ->
                        async {
                            authService.reTryAuthCatching {
                                usersApi.fetchUser(userId)
                            }.getOrNull()?.displayName?.let { name -> userId to name }
                        }
                    }.awaitAll().forEach { result ->
                        result?.let { (id, name) -> authorMap[id] = name }
                    }
                    // 增量更新 UI
                    _postAuthors.value = authorMap.toMap()
                }
            }
        }.onFailure {
            logger.error(it.message.orEmpty())
        }
        _postsLoading.value = false
        _postsLoadingMore.value = false
    }

    private suspend fun loadGroupInstances(groupId: String) {
        val userId = authService.currentUser().id
        if (userId.isBlank()) return
        authService.reTryAuthCatching {
            groupsApi.getGroupInstances(userId = userId, groupId = groupId)
        }.onSuccess {
            _groupInstances.value = it.instances
        }.onFailure {
            logger.error(it.message.orEmpty())
        }
    }

    private suspend fun loadGalleryImages(groupId: String, galleries: List<io.github.vrcmteam.vrcm.network.api.groups.data.Gallery>) {
        if (galleries.isEmpty()) return
        val imagesMap = mutableMapOf<String, List<GroupGalleryImage>>()
        galleries.forEach { gallery ->
            authService.reTryAuthCatching {
                groupsApi.getGroupGalleryImages(groupId = groupId, groupGalleryId = gallery.id, n = 30, offset = 0)
            }.onSuccess {
                imagesMap[gallery.id] = it
            }.onFailure {
                logger.error(it.message.orEmpty())
            }
        }
        _galleryImages.value = imagesMap
    }

    private suspend fun handleError(tag: String, error: Throwable) {
        logger.error("$tag: ${error.message}")
        SharedFlowCentre.toastText.emit(ToastText.Error(error.message ?: "Unknown error"))
    }

    private companion object {
        const val POSTS_PAGE_SIZE = 20
    }
}
