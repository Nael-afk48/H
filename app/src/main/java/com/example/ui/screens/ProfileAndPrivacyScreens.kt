package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BlockedUser
import com.example.data.PersonalDataSummary
import com.example.data.Post
import com.example.data.TagPermission
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.components.BlockUserConfirmDialog
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.components.PostCard
import com.example.ui.components.ReportPostDialog
import com.example.ui.components.icon
import com.example.ui.components.rememberDecodedBitmap
import com.example.ui.components.rememberRelativeTimeTicker
import com.example.util.ImageUtils
import com.example.util.formatRelativeTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isOwnProfile: Boolean,
    profileState: UiState<UserProfile?>,
    postsState: UiState<List<Post>>,
    likedPostIds: Set<String>,
    reportedPostIds: Set<String> = emptySet(),
    isBlockedUser: Boolean,
    onBack: (() -> Unit)? = null,
    onOpenPrivacySettings: () -> Unit,
    onUpdateProfile: (bio: String, avatarBase64: String?) -> Unit,
    onOpenThread: (String) -> Unit,
    onToggleReaction: (Post, Boolean) -> Unit,
    onReportPost: (Post, String, Boolean) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onUnblockUser: (String, String) -> Unit,
    onEditPostCaption: (String, String) -> Unit,
    onDeletePost: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGridView by remember { mutableStateOf(true) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var reportTargetPost by remember { mutableStateOf<Post?>(null) }
    var blockTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val nowMillis = rememberRelativeTimeTicker()

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && profileState is UiState.Success) {
            val currentBio = profileState.data?.bio ?: ""
            scope.launch {
                val encoded = ImageUtils.compressUriToBase64(
                    context = context,
                    uri = uri,
                    maxDimension = 320,
                    maxBase64Length = 90_000
                )
                if (encoded != null) {
                    onUpdateProfile(currentBio, encoded)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (profileState is UiState.Success && profileState.data != null) {
                        "@${profileState.data.alias}"
                    } else {
                        "Profile"
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(
                            onClick = onOpenPrivacySettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("privacy_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Privacy settings"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (profileState) {
                is UiState.Loading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        EmptySanctuaryCard(
                            title = "Profile hidden or unavailable",
                            subtitle = profileState.message
                        )
                    }
                }

                is UiState.Success -> {
                    val profile = profileState.data
                    if (profile == null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            EmptySanctuaryCard(
                                title = "Profile not visible",
                                subtitle = "This user's profile is private or does not exist."
                            )
                        }
                    } else {
                        val posts = (postsState as? UiState.Success)?.data ?: emptyList()

                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxSize()
                        ) {
                            item(key = "profile_header") {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(20.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.extraLarge)
                                                .then(
                                                    if (isOwnProfile) {
                                                        Modifier.clickable {
                                                            avatarPicker.launch(
                                                                PickVisualMediaRequest(
                                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                                )
                                                            )
                                                        }
                                                    } else Modifier
                                                )
                                                .testTag("profile_avatar")
                                        ) {
                                            AvatarBadge(
                                                alias = profile.alias,
                                                avatarBase64 = profile.avatarBase64,
                                                size = 84.dp
                                            )
                                        }

                                        if (isOwnProfile) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Tap photo to update",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "@${profile.alias}",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (profile.profileHidden && isOwnProfile) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .clickable(onClick = onOpenPrivacySettings)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Lock,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Profile hidden · Privacy",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = profile.bio.ifBlank {
                                                if (isOwnProfile) "Tap to add a one-line bio about yourself." else "Quietly sharing on haven."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (profile.bio.isBlank()) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            textAlign = TextAlign.Center,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .then(
                                                    if (isOwnProfile) {
                                                        Modifier
                                                            .clickable { showEditProfileDialog = true }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    } else Modifier
                                                )
                                                .testTag("profile_bio_text")
                                        )
                                        val tagSetting = TagPermission.fromRaw(profile.tagPermission)
                                        if (isOwnProfile || tagSetting == TagPermission.NOBODY) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = if (tagSetting == TagPermission.NOBODY) {
                                                    "Mentions disabled"
                                                } else {
                                                    "Mentions: ${tagSetting.label}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        if (isOwnProfile) {
                                            OutlinedButton(
                                                onClick = { showEditProfileDialog = true },
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("edit_bio_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Edit profile")
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    if (isBlockedUser) {
                                                        onUnblockUser(profile.uid, profile.alias)
                                                    } else {
                                                        blockTarget = profile.uid to profile.alias
                                                    }
                                                },
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("block_profile_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Block,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (isBlockedUser) "Unblock @${profile.alias}" else "Block @${profile.alias}")
                                            }
                                        }
                                    }
                                }
                            }

                            if (!isOwnProfile && isBlockedUser) {
                                item(key = "blocked_user_notice") {
                                    EmptySanctuaryCard(
                                        title = "@${profile.alias} is blocked",
                                        subtitle = "Their posts and replies are hidden while blocked.",
                                        actionLabel = "Unblock @${profile.alias}",
                                        onActionClick = { onUnblockUser(profile.uid, profile.alias) }
                                    )
                                }
                            } else {
                                item(key = "layout_toggle") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${posts.size} ${if (posts.size == 1) "moment" else "moments"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        SingleChoiceSegmentedButtonRow {
                                            SegmentedButton(
                                                selected = isGridView,
                                                onClick = { isGridView = true },
                                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                                modifier = Modifier.testTag("profile_grid_toggle")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.GridView,
                                                    contentDescription = "Grid view",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            SegmentedButton(
                                                selected = !isGridView,
                                                onClick = { isGridView = false },
                                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                                modifier = Modifier.testTag("profile_list_toggle")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ViewAgenda,
                                                    contentDescription = "List view",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (postsState is UiState.Loading) {
                                    item(key = "posts_loading") {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp)
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                } else if (posts.isEmpty()) {
                                    item(key = "posts_empty") {
                                        EmptySanctuaryCard(
                                            title = "No moments shared yet",
                                            subtitle = if (isOwnProfile) {
                                                "Tap + to share your first photo or thought."
                                            } else {
                                                "@${profile.alias} hasn't shared publicly yet."
                                            }
                                        )
                                    }
                                } else if (isGridView) {
                                    val rows = posts.chunked(3)
                                    items(rows.size, key = { idx -> "grid_row_$idx" }) { rowIdx ->
                                        val rowItems = rows[rowIdx]
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            rowItems.forEach { post ->
                                                ProfileGridTile(
                                                    post = post,
                                                    nowMillis = nowMillis,
                                                    onClick = { onOpenThread(post.id) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat(3 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    items(posts, key = { it.id }) { post ->
                                        val isLiked = post.id in likedPostIds
                                        PostCard(
                                            post = post,
                                            isLiked = isLiked,
                                            isOwnPost = isOwnProfile,
                                            isReported = post.id in reportedPostIds,
                                            onPostClick = { onOpenThread(post.id) },
                                            onAuthorClick = {},
                                            onToggleReaction = { onToggleReaction(post, isLiked) },
                                            onCommentClick = { onOpenThread(post.id) },
                                            onReportClick = { reportTargetPost = post },
                                            onBlockClick = { blockTarget = post.authorId to post.authorAlias },
                                            onDeleteClick = { onDeletePost(post.id) },
                                            onEditCaption = if (isOwnProfile) {
                                                { newCaption -> onEditPostCaption(post.id, newCaption) }
                                            } else null,
                                            nowMillis = nowMillis
                                        )
                                    }
                                }
                            }
                        }

                        if (showEditProfileDialog) {
                            EditProfileDialog(
                                alias = profile.alias,
                                avatarBase64 = profile.avatarBase64,
                                initialBio = profile.bio,
                                onDismiss = { showEditProfileDialog = false },
                                onPickPhoto = {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                onSave = { newBio ->
                                    onUpdateProfile(newBio, null)
                                    showEditProfileDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    reportTargetPost?.let { target ->
        ReportPostDialog(
            targetAlias = target.authorAlias,
            postPreview = target.previewText(),
            onDismiss = { reportTargetPost = null },
            onSubmit = { reason, alsoBlockAuthor ->
                onReportPost(target, reason, alsoBlockAuthor)
                reportTargetPost = null
            }
        )
    }

    blockTarget?.let { (targetUid, targetAlias) ->
        BlockUserConfirmDialog(
            targetAlias = targetAlias,
            onDismiss = { blockTarget = null },
            onConfirm = {
                onBlockUser(targetUid, targetAlias)
                blockTarget = null
            }
        )
    }
}

@Composable
private fun ProfileGridTile(
    post: Post,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberDecodedBitmap(post.imageBase64)
    val circle = post.parsedCircle()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("profile_grid_tile_${post.id}")
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = post.caption.ifBlank { "Moment thumbnail" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = circle.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatRelativeTime(post.createdAt, nowMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    alias: String,
    avatarBase64: String?,
    initialBio: String,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onSave: (String) -> Unit
) {
    var bioText by remember(initialBio) { mutableStateOf(initialBio) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarBadge(
                    alias = alias,
                    avatarBase64 = avatarBase64,
                    size = 64.dp
                )
                OutlinedButton(
                    onClick = onPickPhoto,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("edit_profile_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change profile photo")
                }
                OutlinedTextField(
                    value = bioText,
                    onValueChange = { if (it.length <= 120) bioText = it.replace("\n", " ") },
                    label = { Text("One-line bio") },
                    placeholder = { Text("Share a brief line about yourself…") },
                    singleLine = true,
                    trailingIcon = {
                        if (bioText.isNotEmpty()) {
                            IconButton(onClick = { bioText = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear bio",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    supportingText = { Text("${bioText.length}/120 characters") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_bio_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(bioText.trim()) },
                modifier = Modifier.testTag("save_bio_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    currentProfile: UserProfile,
    blockedUsers: List<BlockedUser>,
    submittedReportsCount: Int = 0,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSavePrivacy: (profileHidden: Boolean, tagPermission: TagPermission) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onUnblockUser: (BlockedUser) -> Unit,
    onLoadDataSummary: (onLoaded: (PersonalDataSummary) -> Unit) -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var profileHidden by remember(currentProfile.profileHidden) { mutableStateOf(currentProfile.profileHidden) }
    var tagPermission by remember(currentProfile.tagPermission) {
        mutableStateOf(TagPermission.fromRaw(currentProfile.tagPermission))
    }
    var dataSummary by remember { mutableStateOf<PersonalDataSummary?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.privacy_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("privacy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = if (profileHidden) Icons.Rounded.Lock else Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (profileHidden) "Private · Hidden from public feed" else "Visible in public circles",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mentions: ${tagPermission.label} · ${blockedUsers.size} blocked · $submittedReportsCount reported",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide profile from public feed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "When enabled, your profile and posts are hidden from other people's feeds and search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profileHidden,
                            enabled = !isBusy,
                            onCheckedChange = { next ->
                                profileHidden = next
                                onSavePrivacy(next, tagPermission)
                            },
                            modifier = Modifier.testTag("hide_profile_switch")
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Who can tag or mention you",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TagPermission.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable(enabled = !isBusy) {
                                        tagPermission = option
                                        onSavePrivacy(profileHidden, option)
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("tag_permission_${option.name}")
                            ) {
                                RadioButton(
                                    selected = tagPermission == option,
                                    enabled = !isBusy,
                                    onClick = {
                                        tagPermission = option
                                        onSavePrivacy(profileHidden, option)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Blocked accounts (${blockedUsers.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = onOpenBlockedUsers,
                                modifier = Modifier.testTag("privacy_manage_blocked_button")
                            ) {
                                Text("Manage")
                            }
                        }
                        if (blockedUsers.isEmpty()) {
                            Text(
                                text = "No blocked accounts. Open Manage to block an account by @alias anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            blockedUsers.take(4).forEach { blocked ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "@${blocked.blockedAlias}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(
                                        onClick = { onUnblockUser(blocked) },
                                        modifier = Modifier.testTag("privacy_unblock_${blocked.blockedUid}")
                                    ) {
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Manage personal data",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Review what haven stores for your account or erase your posts, profile, and activity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { onLoadDataSummary { summary -> dataSummary = summary } },
                            enabled = !isBusy,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("view_personal_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View my data summary")
                        }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isBusy,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("delete_personal_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete account data & sign out")
                        }
                    }
                }
            }
        }
    }

    dataSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { dataSummary = null },
            title = { Text("Your haven data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Alias: @${summary.alias}", style = MaterialTheme.typography.bodyMedium)
                    Text("Email: ${summary.email.ifBlank { "Google account" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Bio: ${summary.bio.ifBlank { "(none)" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Published posts: ${summary.postCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("Blocked accounts: ${summary.blockedCount}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Visibility: ${if (summary.profileHidden) "Hidden" else "Visible"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Tagging limit: ${summary.tagPermission.label}", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { dataSummary = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all personal data?") },
            text = {
                Text(
                    "This permanently removes your @${currentProfile.alias} profile, posts, alerts, and block list from haven, then signs you out."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAllData()
                    },
                    modifier = Modifier.testTag("confirm_delete_data_button")
                ) {
                    Text("Delete & Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    blockedUsers: List<BlockedUser>,
    blockableProfiles: List<UserProfile>,
    onBack: () -> Unit,
    onBlockUser: (uid: String, alias: String) -> Unit,
    onUnblockUser: (BlockedUser) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var pendingBlockProfile by remember { mutableStateOf<UserProfile?>(null) }
    val nowMillis = rememberRelativeTimeTicker()

    val normalizedQuery = searchQuery.trim().removePrefix("@")
    val matchingPeople = remember(blockableProfiles, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            blockableProfiles.filter { profile ->
                profile.alias.contains(normalizedQuery, ignoreCase = true) ||
                    profile.displayName.contains(normalizedQuery, ignoreCase = true)
            }.take(6)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Blocked users (${blockedUsers.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("blocked_users_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                item(key = "block_search_card") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Block an account",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Search by @alias to hide someone's posts, replies, and alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { if (it.length <= 40) searchQuery = it },
                                placeholder = { Text("Search @alias…") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear search",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("block_user_search_input")
                            )

                            if (normalizedQuery.isNotBlank()) {
                                if (matchingPeople.isEmpty()) {
                                    Text(
                                        text = "No matching discoverable accounts found for \"$normalizedQuery\".",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    matchingPeople.forEach { person ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            AvatarBadge(
                                                alias = person.alias,
                                                avatarBase64 = person.avatarBase64,
                                                size = 34.dp
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "@${person.alias}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (person.bio.isNotBlank()) {
                                                    Text(
                                                        text = person.bio,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { pendingBlockProfile = person },
                                                modifier = Modifier.testTag("block_candidate_${person.uid}")
                                            ) {
                                                Text("Block")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "blocked_list_heading") {
                    Text(
                        text = "Blocked list",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (blockedUsers.isEmpty()) {
                    item(key = "blocked_list_empty") {
                        EmptySanctuaryCard(
                            title = "No blocked accounts",
                            subtitle = "Accounts you block appear here, and you can unblock them anytime."
                        )
                    }
                } else {
                    items(blockedUsers, key = { it.blockedUid }) { blocked ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .testTag("blocked_user_card_${blocked.blockedUid}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                AvatarBadge(
                                    alias = blocked.blockedAlias,
                                    avatarBase64 = null,
                                    size = 38.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "@${blocked.blockedAlias}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "Blocked ${formatRelativeTime(blocked.createdAt, nowMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onUnblockUser(blocked) },
                                    modifier = Modifier.testTag("blocked_screen_unblock_${blocked.blockedUid}")
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingBlockProfile?.let { person ->
        BlockUserConfirmDialog(
            targetAlias = person.alias,
            onDismiss = { pendingBlockProfile = null },
            onConfirm = {
                onBlockUser(person.uid, person.alias)
                pendingBlockProfile = null
                searchQuery = ""
            }
        )
    }
}
