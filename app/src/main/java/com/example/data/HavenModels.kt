package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class HavenCircle(val label: String) {
    DAILY_CALM("Daily Calm"),
    PHOTOGRAPHY("Photography"),
    BOOKS_MUSIC("Books & Music"),
    MAKERS("Makers"),
    OUTDOORS("Outdoors");

    companion object {
        fun fromRaw(raw: String): HavenCircle =
            entries.firstOrNull { it.name == raw } ?: DAILY_CALM
    }
}

enum class TagPermission(val label: String, val subtitle: String) {
    EVERYONE("Everyone", "Anyone on haven can mention your @alias"),
    CIRCLES("My Circles", "Only people sharing active circle threads"),
    NOBODY("No One", "Disable @alias tagging across posts and replies");

    companion object {
        fun fromRaw(raw: String): TagPermission =
            entries.firstOrNull { it.name == raw } ?: EVERYONE
    }
}

enum class AlertType {
    REACTION,
    COMMENT;

    companion object {
        fun fromRaw(raw: String): AlertType =
            entries.firstOrNull { it.name == raw } ?: REACTION
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val alias: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarBase64: String? = null,
    val profileHidden: Boolean = false,
    val tagPermission: String = TagPermission.EVERYONE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorAlias: String = "",
    val authorAvatarBase64: String? = null,
    val authorHidden: Boolean = false,
    val circle: String = HavenCircle.DAILY_CALM.name,
    val caption: String = "",
    val imageBase64: String? = null,
    val reactionCount: Long = 0L,
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedCircle(): HavenCircle = HavenCircle.fromRaw(circle)

    fun previewText(): String = caption.trim().take(80).ifBlank { "Shared a photo" }

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "authorId" to authorId,
        "authorAlias" to authorAlias,
        "authorAvatarBase64" to authorAvatarBase64,
        "authorHidden" to authorHidden,
        "circle" to circle,
        "caption" to caption,
        "imageBase64" to imageBase64,
        "reactionCount" to 0L,
        "commentCount" to 0L,
        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val postAuthorId: String = "",
    val postHidden: Boolean = false,
    val authorId: String = "",
    val authorAlias: String = "",
    val authorAvatarBase64: String? = null,
    val body: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserAlert(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val actorUid: String = "",
    val actorAlias: String = "",
    val type: String = AlertType.REACTION.name,
    val postId: String = "",
    val postPreview: String = "",
    val commentPreview: String? = null,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockedUid: String = "",
    val blockedAlias: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PostReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetPostId: String = "",
    val targetAuthorId: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class PersonalDataSummary(
    val alias: String,
    val email: String,
    val bio: String,
    val postCount: Int,
    val blockedCount: Int,
    val profileHidden: Boolean,
    val tagPermission: TagPermission
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
