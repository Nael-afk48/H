package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.data.HavenCircle
import com.example.data.Post
import com.example.util.ImageUtils
import com.example.util.formatRelativeTime

@Composable
fun rememberDecodedBitmap(base64: String?): Bitmap? =
    remember(base64) { ImageUtils.decodeBase64ToBitmap(base64) }

@Composable
fun rememberRelativeTimeTicker(intervalMillis: Long = 30_000L): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMillis) {
        while (true) {
            delay(intervalMillis)
            nowMillis = System.currentTimeMillis()
        }
    }
    return nowMillis
}

fun HavenCircle.icon(): ImageVector = when (this) {
    HavenCircle.DAILY_CALM -> Icons.Rounded.Contrast
    HavenCircle.PHOTOGRAPHY -> Icons.Rounded.PhotoCamera
    HavenCircle.BOOKS_MUSIC -> Icons.Rounded.MenuBook
    HavenCircle.MAKERS -> Icons.Rounded.Handyman
    HavenCircle.OUTDOORS -> Icons.Rounded.Landscape
}

@Composable
fun AvatarBadge(
    alias: String,
    avatarBase64: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberDecodedBitmap(avatarBase64)
    val initial = alias.trim().removePrefix("@").firstOrNull()?.uppercaseChar()?.toString() ?: "H"

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "@$alias avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    isLiked: Boolean,
    isOwnPost: Boolean,
    isReported: Boolean = false,
    onPostClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onToggleReaction: () -> Unit,
    onCommentClick: () -> Unit,
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditCaption: ((String) -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val heartPulse = remember(post.id) { Animatable(1f) }
    val commentPulse = remember(post.id) { Animatable(1f) }
    var previousCommentCount by remember(post.id) { mutableLongStateOf(post.commentCount) }
    LaunchedEffect(post.commentCount) {
        if (post.commentCount > previousCommentCount) {
            commentPulse.snapTo(1f)
            commentPulse.animateTo(
                targetValue = 1.2f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)
            )
            commentPulse.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f)
            )
        }
        previousCommentCount = post.commentCount
    }
    var previousLiked by remember(post.id) { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked != previousLiked) {
            previousLiked = isLiked
            if (isLiked) {
                heartPulse.snapTo(1f)
                heartPulse.animateTo(
                    targetValue = 1.24f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
                )
                heartPulse.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 360f)
                )
            } else {
                heartPulse.snapTo(0.88f)
                heartPulse.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                )
            }
        }
    }

    val likeContainerColor by animateColorAsState(
        targetValue = if (isLiked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "like_container_color"
    )
    val likeContentColor by animateColorAsState(
        targetValue = if (isLiked) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 220),
        label = "like_content_color"
    )
    val likeBorderColor by animateColorAsState(
        targetValue = if (isLiked) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "like_border_color"
    )
    val postBitmap = rememberDecodedBitmap(post.imageBase64)
    val circle = post.parsedCircle()

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPostClick)
            .testTag("post_card_${post.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onAuthorClick)
                        .padding(vertical = 4.dp)
                        .testTag("post_author_${post.id}")
                ) {
                    AvatarBadge(
                        alias = post.authorAlias,
                        avatarBase64 = post.authorAvatarBase64,
                        size = 42.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "@${post.authorAlias}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isOwnPost) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            } else if (isReported) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = "Reported",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = circle.icon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = circle.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            val relativeTime = formatRelativeTime(post.createdAt, nowMillis)
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = relativeTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                modifier = Modifier
                                    .semantics { contentDescription = "Posted $relativeTime" }
                                    .testTag("post_timestamp_${post.id}")
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("post_menu_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Post options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (isOwnPost) {
                            if (onEditCaption != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit caption") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showEditDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete post") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (isReported) "Already reported" else "Report post") },
                                enabled = !isReported,
                                leadingIcon = {
                                    Icon(Icons.Rounded.Flag, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onReportClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block @${post.authorAlias}") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Block, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onBlockClick()
                                }
                            )
                        }
                    }
                }
            }

            if (postBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = postBitmap.asImageBitmap(),
                    contentDescription = post.caption.ifBlank { "Shared photo by @${post.authorAlias}" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            if (post.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = likeContainerColor,
                    border = BorderStroke(1.dp, likeBorderColor),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleReaction()
                        }
                        .semantics {
                            selected = isLiked
                            contentDescription = if (isLiked) {
                                "Liked, ${post.reactionCount} likes. Tap to unlike"
                            } else {
                                "Like post, ${post.reactionCount} likes"
                            }
                        }
                        .testTag("react_button_${post.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        AnimatedContent(
                            targetState = isLiked,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(initialScale = 0.78f, animationSpec = tween(160)))
                                    .togetherWith(
                                        fadeOut(tween(110)) + scaleOut(targetScale = 0.78f, animationSpec = tween(110))
                                    )
                            },
                            label = "heart_icon_transition"
                        ) { liked ->
                            Icon(
                                imageVector = if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = likeContentColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .scale(heartPulse.value)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = post.reactionCount,
                            transitionSpec = {
                                if (targetState >= initialState) {
                                    (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(150)))
                                        .togetherWith(
                                            slideOutVertically(tween(150)) { -it / 2 } + fadeOut(tween(120))
                                        )
                                } else {
                                    (slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(150)))
                                        .togetherWith(
                                            slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(120))
                                        )
                                }
                            },
                            label = "like_count_transition"
                        ) { count ->
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isLiked) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onCommentClick)
                        .testTag("comment_button_${post.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Open replies",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .scale(commentPulse.value)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = post.commentCount,
                            transitionSpec = {
                                if (targetState >= initialState) {
                                    (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(150)))
                                        .togetherWith(
                                            slideOutVertically(tween(150)) { -it / 2 } + fadeOut(tween(120))
                                        )
                                } else {
                                    (slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(150)))
                                        .togetherWith(
                                            slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(120))
                                        )
                                }
                            },
                            label = "comment_count_transition"
                        ) { count ->
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && onEditCaption != null) {
        var editedText by remember(post.caption) { mutableStateOf(post.caption) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit caption") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { if (it.length <= 280) editedText = it },
                    label = { Text("Caption") },
                    minLines = 2,
                    maxLines = 5,
                    supportingText = { Text("${editedText.length}/280") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_caption_input")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editedText.trim().isNotEmpty(),
                    onClick = {
                        onEditCaption(editedText.trim())
                        showEditDialog = false
                    },
                    modifier = Modifier.testTag("save_caption_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReportPostDialog(
    targetAlias: String,
    postPreview: String = "",
    onDismiss: () -> Unit,
    onSubmit: (reason: String, alsoBlockAuthor: Boolean) -> Unit
) {
    val presetReasons = listOf(
        "Spam or unwanted promotion",
        "Harassment or unkind behavior",
        "Sensitive or inappropriate content",
        "Misleading or impersonation",
        "Other"
    )
    var selectedReason by remember { mutableStateOf(presetReasons.first()) }
    var customNote by remember { mutableStateOf("") }
    var alsoBlockAuthor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report @$targetAlias's post",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (postPreview.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"$postPreview\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                Text(
                    text = "Reported posts are hidden from your feed. Choose a reason:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                presetReasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { selectedReason = reason }
                            .padding(vertical = 3.dp)
                            .testTag("report_reason_${reason.take(8).lowercase().replace(" ", "_")}")
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (selectedReason == "Other") {
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { if (it.length <= 200) customNote = it },
                        label = { Text("Brief reason") },
                        supportingText = { Text("${customNote.length}/200") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("report_custom_reason_input")
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { alsoBlockAuthor = !alsoBlockAuthor }
                        .padding(vertical = 4.dp)
                        .testTag("report_also_block_toggle")
                ) {
                    Checkbox(
                        checked = alsoBlockAuthor,
                        onCheckedChange = { alsoBlockAuthor = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Also block @$targetAlias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hide all their posts and replies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val finalReason = if (selectedReason == "Other") customNote.trim() else selectedReason
            TextButton(
                enabled = finalReason.length >= 3,
                onClick = { onSubmit(finalReason, alsoBlockAuthor) },
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BlockUserConfirmDialog(
    targetAlias: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Block @$targetAlias?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "You won't see posts, replies, or alerts from @$targetAlias. You can unblock them anytime in Privacy or Safety settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_block_user_button")
            ) {
                Text("Block")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptySanctuaryCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Diamond,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActionClick,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag("empty_state_action_button")
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
