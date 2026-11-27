package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HavenRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing haven.")

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "profiles/$userId"
        emitAll(
            db.collection("profiles").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDiscoverableProfiles(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "profiles"
        emitAll(
            db.collection("profiles")
                .whereEqualTo("profileHidden", false)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.alias }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicFeed(): Flow<List<Post>> = flow {
        val uid = requireUserId()
        val path = "posts"
        emitAll(
            db.collection("posts")
                .whereEqualTo("authorHidden", false)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchPublicFeedFromServer(): Result<List<Post>> {
        val path = "posts"
        return try {
            requireUserId()
            val snapshot = db.collection("posts")
                .whereEqualTo("authorHidden", false)
                .limit(100)
                .get(Source.SERVER)
                .await()
            val posts = snapshot.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            Result.success(posts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeUserPosts(targetUserId: String, includeHidden: Boolean): Flow<List<Post>> = flow {
        val uid = requireUserId()
        require(targetUserId.isNotBlank()) { "Target user ID must not be blank" }
        val path = "posts"
        val query = if (includeHidden && targetUserId == uid) {
            db.collection("posts").whereEqualTo("authorId", targetUserId)
        } else {
            db.collection("posts")
                .whereEqualTo("authorId", targetUserId)
                .whereEqualTo("authorHidden", false)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePost(postId: String): Flow<Post?> = flow {
        val uid = requireUserId()
        require(postId.isNotBlank()) { "Post ID must not be blank" }
        val path = "posts/$postId"
        emitAll(
            db.collection("posts").document(postId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeComments(postId: String, postAuthorId: String, isPostHidden: Boolean): Flow<List<PostComment>> = flow {
        val uid = requireUserId()
        require(postId.isNotBlank()) { "Post ID must not be blank" }
        val path = "posts/$postId/comments"
        val query = if (isPostHidden && postAuthorId == uid) {
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("postAuthorId", uid)
        } else {
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("postHidden", false)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PostComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeLikedPostIds(userId: String): Flow<Set<String>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user likedPosts listener" }
        val path = "users/$uid/likedPosts"
        emitAll(
            db.collection("users").document(uid).collection("likedPosts")
                .snapshots()
                .map { snapshot -> snapshot.documents.map { it.id }.toSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user blocks listener" }
        val path = "users/$uid/blocks"
        emitAll(
            db.collection("users").document(uid).collection("blocks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(BlockedUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserReports(userId: String): Flow<List<PostReport>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user reports listener" }
        val path = "reports"
        emitAll(
            db.collection("reports")
                .whereEqualTo("reporterId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PostReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAlerts(userId: String): Flow<List<UserAlert>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user alerts listener" }
        val path = "users/$uid/alerts"
        emitAll(
            db.collection("users").document(uid).collection("alerts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun checkAliasAvailable(rawAlias: String): Result<Boolean> {
        val alias = rawAlias.trim().lowercase()
        val path = "aliases/$alias"
        return try {
            requireUserId()
            require(isValidAliasFormat(alias)) { "Alias must be 3–20 lowercase letters, numbers, or underscores." }
            val doc = db.collection("aliases").document(alias).get().await()
            Result.success(!doc.exists())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createInitialProfile(
        rawAlias: String,
        displayName: String,
        email: String,
        bio: String = ""
    ): Result<Unit> {
        val alias = rawAlias.trim().lowercase()
        var path = "profiles"
        return try {
            val uid = requireUserId()
            require(isValidAliasFormat(alias)) { "Alias must be 3–20 lowercase letters, numbers, or underscores." }
            val cleanBio = bio.trim().take(120)
            val cleanDisplayName = displayName.trim().ifBlank { alias }.take(60)
            val cleanEmail = email.trim().take(200)

            val aliasDoc = db.collection("aliases").document(alias).get().await()
            if (aliasDoc.exists() && aliasDoc.getString("uid") != uid) {
                return Result.failure(IllegalStateException("That @alias is already taken. Try another one."))
            }

            path = "profiles/$uid"
            val batch = db.batch()
            if (!aliasDoc.exists()) {
                batch.set(
                    db.collection("aliases").document(alias),
                    mapOf(
                        "uid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                db.collection("users").document(uid),
                mapOf(
                    "uid" to uid,
                    "email" to cleanEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("profiles").document(uid),
                mapOf(
                    "uid" to uid,
                    "alias" to alias,
                    "displayName" to cleanDisplayName,
                    "bio" to cleanBio,
                    "profileHidden" to false,
                    "tagPermission" to TagPermission.EVERYONE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(bio: String, avatarBase64: String?): Result<Unit> {
        var path = "profiles"
        return try {
            val uid = requireUserId()
            path = "profiles/$uid"
            val cleanBio = bio.trim().take(120)
            val updates = mutableMapOf<String, Any>(
                "bio" to cleanBio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarBase64 != null) {
                require(avatarBase64.length <= 120_000) { "Avatar image is too large." }
                updates["avatarBase64"] = avatarBase64
            }
            db.collection("profiles").document(uid).update(updates).await()

            if (avatarBase64 != null) {
                val ownPosts = db.collection("posts").whereEqualTo("authorId", uid).get().await()
                if (!ownPosts.isEmpty) {
                    val batch = db.batch()
                    ownPosts.documents.forEach { doc ->
                        batch.update(
                            doc.reference,
                            mapOf(
                                "authorAvatarBase64" to avatarBase64,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createPost(
        authorProfile: UserProfile,
        circle: HavenCircle,
        caption: String,
        imageBase64: String?
    ): Result<String> {
        var path = "posts"
        return try {
            val uid = requireUserId()
            check(authorProfile.uid == uid) { "Profile mismatch" }
            val cleanCaption = caption.trim().take(280)
            require(cleanCaption.isNotEmpty() || !imageBase64.isNullOrBlank()) {
                "Add a photo or a brief note before sharing."
            }
            if (imageBase64 != null) {
                require(imageBase64.length <= 400_000) { "Photo is too large to share." }
            }
            val docRef = db.collection("posts").document()
            path = docRef.path

            val newPost = Post(
                authorId = uid,
                authorAlias = authorProfile.alias,
                authorAvatarBase64 = authorProfile.avatarBase64,
                authorHidden = authorProfile.profileHidden,
                circle = circle.name,
                caption = cleanCaption,
                imageBase64 = imageBase64
            )

            docRef.set(newPost.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePostCaption(postId: String, rawCaption: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            requireUserId()
            require(postId.isNotBlank()) { "Post ID required" }
            val cleanCaption = rawCaption.trim().take(280)
            require(cleanCaption.isNotEmpty()) { "Caption cannot be empty." }
            db.collection("posts").document(postId).update(
                mapOf(
                    "caption" to cleanCaption,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleReaction(
        post: Post,
        actorProfile: UserProfile,
        currentlyLiked: Boolean
    ): Result<Unit> {
        var path = "posts/${post.id}"
        return try {
            val uid = requireUserId()
            require(post.id.isNotBlank()) { "Post ID required" }
            val postRef = db.collection("posts").document(post.id)
            val reactionRef = postRef.collection("reactions").document(uid)
            val likedRef = db.collection("users").document(uid).collection("likedPosts").document(post.id)
            path = reactionRef.path

            val targetLiked = !currentlyLiked
            db.runTransaction { transaction ->
                val reactionSnap = transaction.get(reactionRef)
                val postSnap = transaction.get(postRef)
                if (!postSnap.exists()) {
                    throw IllegalStateException("This post is no longer available.")
                }

                val alreadyLiked = reactionSnap.exists()
                if (alreadyLiked == targetLiked) {
                    return@runTransaction null
                }

                val currentCount = (postSnap.getLong("reactionCount") ?: 0L).coerceAtLeast(0L)
                if (alreadyLiked) {
                    transaction.delete(reactionRef)
                    transaction.delete(likedRef)
                    val nextCount = (currentCount - 1L).coerceAtLeast(0L)
                    if (nextCount != currentCount) {
                        transaction.update(
                            postRef,
                            mapOf(
                                "reactionCount" to nextCount,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                } else {
                    transaction.set(
                        reactionRef,
                        mapOf(
                            "uid" to uid,
                            "alias" to actorProfile.alias,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.set(
                        likedRef,
                        mapOf(
                            "postId" to post.id,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    transaction.update(
                        postRef,
                        mapOf(
                            "reactionCount" to (currentCount + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    if (post.authorId != uid) {
                        val alertRef = db.collection("users").document(post.authorId).collection("alerts").document()
                        transaction.set(
                            alertRef,
                            mapOf(
                                "recipientUid" to post.authorId,
                                "actorUid" to uid,
                                "actorAlias" to actorProfile.alias,
                                "type" to AlertType.REACTION.name,
                                "postId" to post.id,
                                "postPreview" to post.caption.take(80).ifBlank { "Shared a photo" },
                                "read" to false,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addComment(
        post: Post,
        actorProfile: UserProfile,
        rawBody: String
    ): Result<Unit> {
        var path = "posts/${post.id}/comments"
        return try {
            val uid = requireUserId()
            val body = rawBody.trim()
            require(body.isNotEmpty() && body.length <= 280) { "Reply must be 1–280 characters." }

            val postRef = db.collection("posts").document(post.id)
            val commentRef = postRef.collection("comments").document()
            path = commentRef.path

            val commentPayload = mapOf(
                "postId" to post.id,
                "postAuthorId" to post.authorId,
                "postHidden" to post.authorHidden,
                "authorId" to uid,
                "authorAlias" to actorProfile.alias,
                "authorAvatarBase64" to actorProfile.avatarBase64,
                "body" to body,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.set(commentRef, commentPayload)
            batch.update(
                postRef,
                mapOf(
                    "commentCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (post.authorId != uid) {
                val alertRef = db.collection("users").document(post.authorId).collection("alerts").document()
                batch.set(
                    alertRef,
                    mapOf(
                        "recipientUid" to post.authorId,
                        "actorUid" to uid,
                        "actorAlias" to actorProfile.alias,
                        "type" to AlertType.COMMENT.name,
                        "postId" to post.id,
                        "postPreview" to post.caption.take(80).ifBlank { "Shared a photo" },
                        "commentPreview" to body.take(100),
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(post: Post, comment: PostComment): Result<Unit> {
        var path = "posts/${post.id}/comments/${comment.id}"
        return try {
            val uid = requireUserId()
            require(post.id.isNotBlank() && comment.id.isNotBlank()) { "Post and comment IDs required" }
            check(comment.authorId == uid || post.authorId == uid) {
                "You can only delete your own comments or comments on your posts."
            }
            val postRef = db.collection("posts").document(post.id)
            val commentRef = postRef.collection("comments").document(comment.id)
            path = commentRef.path

            db.runTransaction { transaction ->
                val commentSnap = transaction.get(commentRef)
                if (!commentSnap.exists()) return@runTransaction null
                val postSnap = transaction.get(postRef)
                transaction.delete(commentRef)
                if (postSnap.exists()) {
                    val currentCount = (postSnap.getLong("commentCount") ?: 0L).coerceAtLeast(0L)
                    val nextCount = (currentCount - 1L).coerceAtLeast(0L)
                    if (nextCount != currentCount) {
                        transaction.update(
                            postRef,
                            mapOf(
                                "commentCount" to nextCount,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            requireUserId()
            db.collection("posts").document(postId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun reportPost(post: Post, reason: String): Result<Unit> {
        var path = "reports"
        return try {
            val uid = requireUserId()
            require(post.id.isNotBlank()) { "Post ID required" }
            val cleanReason = reason.trim().take(200)
            require(cleanReason.length >= 3) { "Please provide a brief reason for reporting." }

            val existing = db.collection("reports")
                .whereEqualTo("reporterId", uid)
                .whereEqualTo("targetPostId", post.id)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) {
                return Result.failure(IllegalStateException("You have already reported this post."))
            }

            val reportRef = db.collection("reports").document()
            path = reportRef.path
            reportRef.set(
                mapOf(
                    "reporterId" to uid,
                    "targetPostId" to post.id,
                    "targetAuthorId" to post.authorId,
                    "reason" to cleanReason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUid: String, targetAlias: String): Result<Unit> {
        var path = "users/blocks"
        return try {
            val uid = requireUserId()
            require(targetUid.isNotBlank() && targetUid != uid) { "Cannot block yourself." }
            val blockRef = db.collection("users").document(uid).collection("blocks").document(targetUid)
            path = blockRef.path
            blockRef.set(
                mapOf(
                    "blockedUid" to targetUid,
                    "blockedAlias" to targetAlias.trim().take(30).ifBlank { "user" },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> {
        var path = "users/blocks"
        return try {
            val uid = requireUserId()
            val blockRef = db.collection("users").document(uid).collection("blocks").document(targetUid)
            path = blockRef.path
            blockRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun markAlertRead(alertId: String): Result<Unit> {
        var path = "users/alerts"
        return try {
            val uid = requireUserId()
            val alertRef = db.collection("users").document(uid).collection("alerts").document(alertId)
            path = alertRef.path
            alertRef.update(mapOf("read" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(profileHidden: Boolean, tagPermission: TagPermission): Result<Unit> {
        var path = "profiles"
        return try {
            val uid = requireUserId()
            path = "profiles/$uid"
            db.collection("profiles").document(uid).update(
                mapOf(
                    "profileHidden" to profileHidden,
                    "tagPermission" to tagPermission.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val ownPosts = db.collection("posts").whereEqualTo("authorId", uid).get().await()
            if (!ownPosts.isEmpty) {
                val batch = db.batch()
                ownPosts.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "authorHidden" to profileHidden,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchPersonalDataSummary(profile: UserProfile): Result<PersonalDataSummary> {
        val path = "users"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid).get().await()
            val postsSnap = db.collection("posts").whereEqualTo("authorId", uid).get().await()
            val blocksSnap = db.collection("users").document(uid).collection("blocks").get().await()
            Result.success(
                PersonalDataSummary(
                    alias = profile.alias,
                    email = userDoc.getString("email") ?: (auth.currentUser?.email ?: ""),
                    bio = profile.bio,
                    postCount = postsSnap.size(),
                    blockedCount = blocksSnap.size(),
                    profileHidden = profile.profileHidden,
                    tagPermission = TagPermission.fromRaw(profile.tagPermission)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(alias: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val ownPosts = db.collection("posts").whereEqualTo("authorId", uid).get().await()
            val blocks = db.collection("users").document(uid).collection("blocks").get().await()
            val liked = db.collection("users").document(uid).collection("likedPosts").get().await()
            val alerts = db.collection("users").document(uid).collection("alerts").get().await()

            val refs = buildList {
                ownPosts.documents.forEach { add(it.reference) }
                blocks.documents.forEach { add(it.reference) }
                liked.documents.forEach { add(it.reference) }
                alerts.documents.forEach { add(it.reference) }
                if (alias.isNotBlank()) add(db.collection("aliases").document(alias))
                add(db.collection("profiles").document(uid))
                add(db.collection("users").document(uid))
            }

            refs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        private val ALIAS_REGEX = Regex("^[a-z0-9_]{3,20}$")
        fun isValidAliasFormat(alias: String): Boolean = ALIAS_REGEX.matches(alias.trim().lowercase())
    }
}
