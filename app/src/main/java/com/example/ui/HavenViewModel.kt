package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BlockedUser
import com.example.data.HavenCircle
import com.example.data.HavenRepository
import com.example.data.PersonalDataSummary
import com.example.data.Post
import com.example.data.PostComment
import com.example.data.PostReport
import com.example.data.TagPermission
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HavenViewModel(
    private val repository: HavenRepository,
    val currentUserId: String
) : ViewModel() {

    private val _selectedCircle = MutableStateFlow<HavenCircle?>(null)
    val selectedCircle: StateFlow<HavenCircle?> = _selectedCircle.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshGeneration = MutableStateFlow(0L)
    private val _manualFeedSnapshot = MutableSharedFlow<List<Post>>(extraBufferCapacity = 1)

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _optimisticLikes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _optimisticCountDeltas = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val pendingReactionPostIds = mutableSetOf<String>()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val currentProfileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .asUiState("Failed to load profile")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val blockedUsersState: StateFlow<List<BlockedUser>> = repository.observeBlockedUsers(currentUserId)
        .catch { error ->
            if (isPureCancellation(error)) throw error
            Log.w(TAG, "Failed observing blocks", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val reportedPostsState: StateFlow<List<PostReport>> = repository.observeUserReports(currentUserId)
        .catch { error ->
            if (isPureCancellation(error)) throw error
            Log.w(TAG, "Failed observing user reports", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val reportedPostIdsState: StateFlow<Set<String>> = reportedPostsState
        .map { reports -> reports.map { it.targetPostId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val likedPostIdsState: StateFlow<Set<String>> = combine(
        repository.observeLikedPostIds(currentUserId)
            .catch { error ->
                if (isPureCancellation(error)) throw error
                Log.w(TAG, "Failed observing liked posts", error)
                emit(emptySet())
            },
        _optimisticLikes
    ) { serverLiked, overrides ->
        if (overrides.isEmpty()) {
            serverLiked
        } else {
            val merged = serverLiked.toMutableSet()
            overrides.forEach { (postId, liked) ->
                if (liked) merged.add(postId) else merged.remove(postId)
            }
            merged
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val alertsState: StateFlow<UiState<List<UserAlert>>> = combine(
        repository.observeAlerts(currentUserId),
        blockedUsersState
    ) { alerts, blocks ->
        val blockedIds = blocks.map { it.blockedUid }.toSet()
        alerts.filterNot { it.actorUid in blockedIds }
    }
        .asUiState("Failed to load alerts")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val publicFeedStream: Flow<List<Post>> = _refreshGeneration.flatMapLatest {
        merge(
            repository.observePublicFeed(),
            _manualFeedSnapshot
        )
    }

    private val discoverableProfilesState: StateFlow<List<UserProfile>> = repository.observeDiscoverableProfiles()
        .catch { error ->
            if (isPureCancellation(error)) throw error
            Log.w(TAG, "Failed observing discoverable profiles", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val blockableProfilesState: StateFlow<List<UserProfile>> = combine(
        discoverableProfilesState,
        blockedUsersState
    ) { profiles, blocks ->
        val blockedIds = blocks.map { it.blockedUid }.toSet()
        profiles.filter { it.uid != currentUserId && it.uid !in blockedIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val matchingFriendsState: StateFlow<List<UserProfile>> = combine(
        discoverableProfilesState,
        blockedUsersState,
        _searchQuery
    ) { profiles, blocks, rawQuery ->
        val normalized = rawQuery.trim().removePrefix("@")
        if (normalized.isBlank()) {
            emptyList()
        } else {
            val blockedIds = blocks.map { it.blockedUid }.toSet()
            profiles.filter { profile ->
                profile.uid != currentUserId &&
                    profile.uid !in blockedIds &&
                    (profile.alias.contains(normalized, ignoreCase = true) ||
                        profile.displayName.contains(normalized, ignoreCase = true))
            }.take(8)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    private val safetyFiltersFlow: Flow<Pair<Set<String>, Set<String>>> = combine(
        blockedUsersState,
        reportedPostIdsState
    ) { blocks, reportedIds ->
        blocks.map { it.blockedUid }.toSet() to reportedIds
    }

    val feedState: StateFlow<UiState<List<Post>>> = combine(
        publicFeedStream,
        safetyFiltersFlow,
        _selectedCircle,
        _searchQuery,
        _optimisticCountDeltas
    ) { posts, (blockedIds, reportedIds), circleFilter, rawQuery, countDeltas ->
        val query = rawQuery.trim()
        val aliasQuery = query.removePrefix("@")
        posts.filter { post ->
            val circleMatch = circleFilter == null || post.circle == circleFilter.name
            val searchMatch = if (query.isBlank()) {
                true
            } else {
                post.caption.contains(query, ignoreCase = true) ||
                    (aliasQuery.isNotBlank() && post.authorAlias.contains(aliasQuery, ignoreCase = true)) ||
                    post.parsedCircle().label.contains(query, ignoreCase = true)
            }
            post.authorId !in blockedIds && post.id !in reportedIds && circleMatch && searchMatch
        }.map { post ->
            val delta = countDeltas[post.id] ?: 0
            if (delta == 0) post else post.copy(reactionCount = (post.reactionCount + delta).coerceAtLeast(0))
        }
    }
        .asUiState("Failed to load feed")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myPostsState: StateFlow<UiState<List<Post>>> = combine(
        repository.observeUserPosts(currentUserId, includeHidden = true),
        _optimisticCountDeltas
    ) { posts, countDeltas ->
        if (countDeltas.isEmpty()) posts
        else posts.map { post ->
            val delta = countDeltas[post.id] ?: 0
            if (delta == 0) post else post.copy(reactionCount = (post.reactionCount + delta).coerceAtLeast(0))
        }
    }
        .asUiState("Failed to load your posts")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun selectCircle(circle: HavenCircle?) {
        _selectedCircle.value = circle
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query.take(60)
    }

    fun refreshFeed() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val hadError = feedState.value is UiState.Error
                val result = repository.fetchPublicFeedFromServer()
                result.onSuccess { freshPosts ->
                    if (hadError) {
                        _refreshGeneration.value += 1L
                    }
                    _manualFeedSnapshot.tryEmit(freshPosts)
                }.onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Could not refresh feed")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    suspend fun checkAliasAvailability(alias: String): Result<Boolean> =
        repository.checkAliasAvailable(alias)

    fun observeTargetProfile(userId: String): Flow<UiState<UserProfile?>> =
        repository.observeProfile(userId).asUiState("Unable to load user profile")

    fun observeTargetUserPosts(userId: String): Flow<UiState<List<Post>>> =
        combine(
            repository.observeUserPosts(userId, includeHidden = userId == currentUserId),
            _optimisticCountDeltas
        ) { posts, countDeltas ->
            if (countDeltas.isEmpty()) posts
            else posts.map { post ->
                val delta = countDeltas[post.id] ?: 0
                if (delta == 0) post else post.copy(reactionCount = (post.reactionCount + delta).coerceAtLeast(0))
            }
        }
            .asUiState("Unable to load user posts")

    fun observePostDetail(postId: String): Flow<UiState<Post?>> =
        combine(
            repository.observePost(postId),
            _optimisticCountDeltas
        ) { post, countDeltas ->
            val delta = countDeltas[postId] ?: 0
            if (post == null || delta == 0) post
            else post.copy(reactionCount = (post.reactionCount + delta).coerceAtLeast(0))
        }.asUiState("Unable to load post")

    fun observeThreadComments(postId: String): Flow<UiState<List<PostComment>>> =
        repository.observePost(postId)
            .flatMapLatest { post ->
                if (post == null) flowOf(emptyList())
                else repository.observeComments(post.id, post.authorId, post.authorHidden)
            }
            .combine(blockedUsersState) { comments, blocks ->
                val blockedIds = blocks.map { it.blockedUid }.toSet()
                comments.filterNot { it.authorId in blockedIds }
            }
            .asUiState("Unable to load replies")

    fun completeAliasSetup(
        alias: String,
        displayName: String,
        email: String,
        bio: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createInitialProfile(
                rawAlias = alias,
                displayName = displayName,
                email = email,
                bio = bio
            )
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Welcome to haven, @${alias.trim().lowercase()}")
                onSuccess()
            }.onFailure { err ->
                onError(err.localizedMessage ?: "Could not save your @alias")
            }
        }
    }

    fun updateProfile(bio: String, avatarBase64: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(bio, avatarBase64)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Profile updated")
                onDone()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to update profile")
            }
        }
    }

    fun publishPost(
        profile: UserProfile,
        circle: HavenCircle,
        caption: String,
        imageBase64: String?,
        onPublished: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createPost(profile, circle, caption, imageBase64)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Moment shared in ${circle.label}")
                onPublished()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not publish post")
            }
        }
    }

    fun toggleReaction(post: Post, actorProfile: UserProfile, currentlyLiked: Boolean) {
        if (post.id.isBlank() || post.id in pendingReactionPostIds) return
        pendingReactionPostIds.add(post.id)
        val targetLiked = !currentlyLiked
        _optimisticLikes.value = _optimisticLikes.value + (post.id to targetLiked)
        _optimisticCountDeltas.value = _optimisticCountDeltas.value + (post.id to if (targetLiked) 1 else -1)

        viewModelScope.launch {
            try {
                val result = repository.toggleReaction(post, actorProfile, currentlyLiked)
                result.onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Unable to update reaction")
                }
            } finally {
                _optimisticLikes.value = _optimisticLikes.value - post.id
                _optimisticCountDeltas.value = _optimisticCountDeltas.value - post.id
                pendingReactionPostIds.remove(post.id)
            }
        }
    }

    fun submitComment(post: Post, actorProfile: UserProfile, body: String, onSent: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.addComment(post, actorProfile, body)
            _isBusy.value = false
            result.onSuccess {
                onSent()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not post reply")
            }
        }
    }

    fun deleteComment(post: Post, comment: PostComment) {
        viewModelScope.launch {
            val result = repository.deleteComment(post, comment)
            result.onSuccess {
                _events.tryEmit("Reply removed")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not remove reply")
            }
        }
    }

    fun updatePostCaption(postId: String, newCaption: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updatePostCaption(postId, newCaption)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Caption updated")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not update caption")
            }
        }
    }

    fun deletePost(postId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deletePost(postId)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Post deleted")
                onDeleted()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not delete post")
            }
        }
    }

    fun reportPost(
        post: Post,
        reason: String,
        alsoBlockAuthor: Boolean = false,
        onReported: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.reportPost(post, reason)
            if (result.isSuccess) {
                var blockedOk = false
                if (alsoBlockAuthor && post.authorId.isNotBlank() && post.authorId != currentUserId) {
                    blockedOk = repository.blockUser(post.authorId, post.authorAlias).isSuccess
                }
                _isBusy.value = false
                val msg = if (blockedOk) {
                    "Report submitted and @${post.authorAlias} blocked."
                } else {
                    "Report submitted. Hidden from your feed."
                }
                _events.tryEmit(msg)
                onReported()
            } else {
                _isBusy.value = false
                _events.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Could not submit report")
            }
        }
    }

    fun blockUser(targetUid: String, targetAlias: String, onBlocked: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.blockUser(targetUid, targetAlias)
            result.onSuccess {
                _events.tryEmit("Blocked @$targetAlias")
                onBlocked()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not block user")
            }
        }
    }

    fun unblockUser(targetUid: String, targetAlias: String) {
        viewModelScope.launch {
            val result = repository.unblockUser(targetUid)
            result.onSuccess {
                _events.tryEmit("Unblocked @$targetAlias")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not unblock user")
            }
        }
    }

    fun markAlertRead(alertId: String) {
        viewModelScope.launch {
            repository.markAlertRead(alertId)
        }
    }

    fun updatePrivacySettings(profileHidden: Boolean, tagPermission: TagPermission) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updatePrivacySettings(profileHidden, tagPermission)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Privacy settings saved")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to update privacy settings")
            }
        }
    }

    fun loadPersonalDataSummary(profile: UserProfile, onLoaded: (PersonalDataSummary) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.fetchPersonalDataSummary(profile)
            _isBusy.value = false
            result.onSuccess(onLoaded).onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not load data summary")
            }
        }
    }

    fun deleteAllDataAndSignOut(alias: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteAllUserData(alias)
            _isBusy.value = false
            result.onSuccess {
                onDeleted()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Failed to delete account data")
            }
        }
    }

    private fun <T> Flow<T>.asUiState(fallbackError: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackError, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackError))
            }

    private fun isPureCancellation(error: Throwable): Boolean {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return firestoreError == null && error is CancellationException
    }

    private companion object {
        const val TAG = "HavenViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
