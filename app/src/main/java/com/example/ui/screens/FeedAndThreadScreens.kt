package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HavenCircle
import com.example.data.Post
import com.example.data.PostComment
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.components.BlockUserConfirmDialog
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.components.PostCard
import com.example.ui.components.ReportPostDialog
import com.example.ui.components.icon
import com.example.ui.components.rememberRelativeTimeTicker
import com.example.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    currentProfile: UserProfile,
    feedState: UiState<List<Post>>,
    selectedCircle: HavenCircle?,
    searchQuery: String,
    matchingFriends: List<UserProfile>,
    likedPostIds: Set<String>,
    reportedPostIds: Set<String> = emptySet(),
    isRefreshing: Boolean,
    listState: LazyListState = rememberLazyListState(),
    onRefresh: () -> Unit,
    onSelectCircle: (HavenCircle?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreateClick: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onToggleReaction: (Post, Boolean) -> Unit,
    onReportPost: (Post, String, Boolean) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onEditPostCaption: (String, String) -> Unit,
    onDeletePost: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = searchQuery.isNotBlank()) {
        onSearchQueryChange("")
    }

    var reportTargetPost by remember { mutableStateOf<Post?>(null) }
    var blockTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    val nowMillis = rememberRelativeTimeTicker()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = selectedCircle?.label ?: "Chronological",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .minimumInteractiveComponentSize()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onOpenProfile(currentProfile.uid) }
                            .testTag("feed_top_avatar")
                    ) {
                        AvatarBadge(
                            alias = currentProfile.alias,
                            avatarBase64 = currentProfile.avatarBase64,
                            size = 34.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("feed_create_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Create new moment"
                )
            }
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
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                DiscreetSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    FilterChip(
                        selected = selectedCircle == null,
                        onClick = { onSelectCircle(null) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("All Circles") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("filter_circle_ALL")
                    )
                    HavenCircle.entries.forEach { circle ->
                        FilterChip(
                            selected = selectedCircle == circle,
                            onClick = { onSelectCircle(circle) },
                            leadingIcon = {
                                Icon(
                                    imageVector = circle.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(circle.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_circle_${circle.name}")
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("feed_pull_to_refresh")
                ) {
                    when (feedState) {
                        is UiState.Loading -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(20.dp)
                            ) {
                                EmptySanctuaryCard(
                                    title = "Unable to refresh feed",
                                    subtitle = feedState.message,
                                    actionLabel = "Try Again",
                                    onActionClick = onRefresh
                                )
                            }
                        }

                        is UiState.Success -> {
                            val posts = feedState.data
                            val trimmedQuery = searchQuery.trim()
                            val isSearching = trimmedQuery.isNotBlank()
                            if (posts.isEmpty() && (!isSearching || matchingFriends.isEmpty())) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(20.dp)
                                ) {
                                    EmptySanctuaryCard(
                                        title = if (isSearching) "No matches found" else stringResource(R.string.feed_empty_title),
                                        subtitle = when {
                                            isSearching -> "Nothing matched \"$trimmedQuery\". Try another keyword, circle, or @alias."
                                            selectedCircle != null -> "No moments in ${selectedCircle.label} yet. Share one or view all circles."
                                            else -> stringResource(R.string.feed_empty_body)
                                        },
                                        actionLabel = when {
                                            isSearching -> "Clear Search"
                                            selectedCircle != null -> "View All Circles"
                                            else -> "Share a Moment"
                                        },
                                        onActionClick = {
                                            when {
                                                isSearching -> onSearchQueryChange("")
                                                selectedCircle != null -> onSelectCircle(null)
                                                else -> onCreateClick()
                                            }
                                        }
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 12.dp,
                                        bottom = 96.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (!isSearching) {
                                        item(key = "quick_share_bar") {
                                            QuickSharePromptBar(
                                                profile = currentProfile,
                                                circleLabel = selectedCircle?.label,
                                                onClick = onCreateClick
                                            )
                                        }
                                    }

                                    if (isSearching && matchingFriends.isNotEmpty()) {
                                        item(key = "matching_friends_strip") {
                                            FriendsSearchSection(
                                                friends = matchingFriends,
                                                onFriendClick = onOpenProfile
                                            )
                                        }
                                    }

                                    if (isSearching && posts.isEmpty()) {
                                        item(key = "search_no_posts_hint") {
                                            Text(
                                                text = "No matching posts for \"$trimmedQuery\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    items(posts, key = { it.id }) { post ->
                                        val isLiked = post.id in likedPostIds
                                        PostCard(
                                            post = post,
                                            isLiked = isLiked,
                                            isOwnPost = post.authorId == currentProfile.uid,
                                            isReported = post.id in reportedPostIds,
                                            onPostClick = { onOpenThread(post.id) },
                                            onAuthorClick = { onOpenProfile(post.authorId) },
                                            onToggleReaction = { onToggleReaction(post, isLiked) },
                                            onCommentClick = { onOpenThread(post.id) },
                                            onReportClick = { reportTargetPost = post },
                                            onBlockClick = { blockTarget = post.authorId to post.authorAlias },
                                            onDeleteClick = { onDeletePost(post.id) },
                                            onEditCaption = { newCaption ->
                                                onEditPostCaption(post.id, newCaption)
                                            },
                                            nowMillis = nowMillis,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
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
private fun QuickSharePromptBar(
    profile: UserProfile,
    circleLabel: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("feed_quick_share_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            AvatarBadge(
                alias = profile.alias,
                avatarBase64 = profile.avatarBase64,
                size = 34.dp
            )
            Text(
                text = if (circleLabel != null) {
                    "Share a moment in $circleLabel…"
                } else {
                    "Share a quiet moment, @${profile.alias}…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = "Share photo or note",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscreetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (isFocused || query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search posts or @friends…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { if (it.length <= 60) onQueryChange(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag("feed_search_input")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("feed_search_clear")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun FriendsSearchSection(
    friends: List<UserProfile>,
    onFriendClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(friends, key = { it.uid }) { friend ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(min = 140.dp, max = 210.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onFriendClick(friend.uid) }
                        .testTag("search_friend_card_${friend.uid}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        AvatarBadge(
                            alias = friend.alias,
                            avatarBase64 = friend.avatarBase64,
                            size = 34.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "@${friend.alias}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (friend.displayName.isNotBlank() &&
                                !friend.displayName.equals(friend.alias, ignoreCase = true)
                            ) {
                                Text(
                                    text = friend.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    currentProfile: UserProfile,
    postState: UiState<Post?>,
    commentsState: UiState<List<PostComment>>,
    likedPostIds: Set<String>,
    reportedPostIds: Set<String> = emptySet(),
    isBusy: Boolean,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onToggleReaction: (Post, Boolean) -> Unit,
    onSendReply: (Post, String, onSent: () -> Unit) -> Unit,
    onDeleteComment: (Post, PostComment) -> Unit,
    onReportPost: (Post, String, Boolean) -> Unit,
    onBlockUser: (String, String) -> Unit,
    onEditPostCaption: (String, String) -> Unit,
    onDeletePost: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var replyText by remember { mutableStateOf("") }
    var reportTargetPost by remember { mutableStateOf<Post?>(null) }
    var blockTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var seenInitialCommentIds by remember { mutableStateOf<Set<String>?>(null) }
    var newlyArrivedCommentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val nowMillis = rememberRelativeTimeTicker()
    val threadListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val currentComments = (commentsState as? UiState.Success)?.data
    LaunchedEffect(currentComments) {
        if (currentComments != null) {
            val currentIds = currentComments.map { it.id }.toSet()
            val baseline = seenInitialCommentIds
            if (baseline == null) {
                seenInitialCommentIds = currentIds
            } else {
                val added = currentIds - baseline - newlyArrivedCommentIds
                if (added.isNotEmpty()) {
                    newlyArrivedCommentIds = newlyArrivedCommentIds + added
                    seenInitialCommentIds = baseline + added
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.thread_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("thread_back_button")
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
        modifier = modifier.imePadding()
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                when (postState) {
                    is UiState.Loading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            EmptySanctuaryCard(
                                title = "Thread unavailable",
                                subtitle = postState.message
                            )
                        }
                    }

                    is UiState.Success -> {
                        val post = postState.data
                        if (post == null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                            ) {
                                EmptySanctuaryCard(
                                    title = "Moment removed",
                                    subtitle = "This post is no longer available."
                                )
                            }
                        } else {
                            LazyColumn(
                                state = threadListState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                item(key = "header_${post.id}") {
                                    val isLiked = post.id in likedPostIds
                                    PostCard(
                                        post = post,
                                        isLiked = isLiked,
                                        isOwnPost = post.authorId == currentProfile.uid,
                                        isReported = post.id in reportedPostIds,
                                        onPostClick = {},
                                        onAuthorClick = { onOpenProfile(post.authorId) },
                                        onToggleReaction = { onToggleReaction(post, isLiked) },
                                        onCommentClick = {},
                                        onReportClick = { reportTargetPost = post },
                                        onBlockClick = { blockTarget = post.authorId to post.authorAlias },
                                        onDeleteClick = { onDeletePost(post.id) },
                                        onEditCaption = { newCaption ->
                                            onEditPostCaption(post.id, newCaption)
                                        },
                                        nowMillis = nowMillis
                                    )
                                }

                                item(key = "replies_header") {
                                    val replyCount = (commentsState as? UiState.Success)?.data?.size
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "Replies",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (replyCount != null) {
                                            AnimatedContent(
                                                targetState = replyCount,
                                                transitionSpec = {
                                                    if (targetState >= initialState) {
                                                        (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(150)))
                                                            .togetherWith(
                                                                slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120))
                                                            )
                                                    } else {
                                                        (slideInVertically(tween(180)) { -it / 2 } + fadeIn(tween(150)))
                                                            .togetherWith(
                                                                slideOutVertically(tween(140)) { it / 2 } + fadeOut(tween(120))
                                                            )
                                                    }
                                                },
                                                label = "reply_header_count"
                                            ) { count ->
                                                Surface(
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = "$count",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                when (commentsState) {
                                    is UiState.Loading -> {
                                        item(key = "comments_loading") {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }

                                    is UiState.Error -> {
                                        item(key = "comments_error") {
                                            Text(
                                                text = commentsState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    is UiState.Success -> {
                                        val comments = commentsState.data
                                        if (comments.isEmpty()) {
                                            item(key = "comments_empty") {
                                                Surface(
                                                    shape = MaterialTheme.shapes.medium,
                                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateItem(
                                                            fadeInSpec = tween(220),
                                                            fadeOutSpec = tween(160)
                                                        )
                                                ) {
                                                    Text(
                                                        text = "No replies yet. Start a thoughtful conversation below.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            items(comments, key = { it.id }) { comment ->
                                                CommentRow(
                                                    comment = comment,
                                                    isNewlyAdded = comment.id in newlyArrivedCommentIds,
                                                    canDelete = comment.authorId == currentProfile.uid ||
                                                        post.authorId == currentProfile.uid,
                                                    nowMillis = nowMillis,
                                                    onAuthorClick = { onOpenProfile(comment.authorId) },
                                                    onDeleteClick = { onDeleteComment(post, comment) },
                                                    modifier = Modifier.animateItem(
                                                        fadeInSpec = tween(durationMillis = 220),
                                                        placementSpec = spring(
                                                            dampingRatio = 0.78f,
                                                            stiffness = 420f
                                                        ),
                                                        fadeOutSpec = tween(durationMillis = 180)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val canSend = replyText.isNotBlank() && !isBusy
                            val sendScale by animateFloatAsState(
                                targetValue = if (canSend) 1.06f else 0.94f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 460f),
                                label = "send_button_scale"
                            )
                            val sendContainerColor by animateColorAsState(
                                targetValue = if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                animationSpec = tween(200),
                                label = "send_container_color"
                            )
                            val sendIconTint by animateColorAsState(
                                targetValue = if (canSend) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = tween(200),
                                label = "send_icon_tint"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = replyText,
                                        onValueChange = { if (it.length <= 280) replyText = it },
                                        placeholder = { Text(stringResource(R.string.thread_reply_hint)) },
                                        maxLines = 3,
                                        shape = MaterialTheme.shapes.large,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("thread_reply_input")
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = sendContainerColor,
                                        modifier = Modifier
                                            .scale(sendScale)
                                            .minimumInteractiveComponentSize()
                                            .clip(CircleShape)
                                            .clickable(enabled = canSend) {
                                                val trimmed = replyText.trim()
                                                if (trimmed.isNotEmpty()) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSendReply(post, trimmed) {
                                                        replyText = ""
                                                        coroutineScope.launch {
                                                            val lastIndex = (threadListState.layoutInfo.totalItemsCount - 1)
                                                                .coerceAtLeast(0)
                                                            threadListState.animateScrollToItem(lastIndex)
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("send_reply_button")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            if (isBusy) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                                    contentDescription = "Send reply",
                                                    tint = sendIconTint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = replyText.isNotEmpty(),
                                    enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                                    exit = shrinkVertically(tween(150)) + fadeOut(tween(120)),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        text = "${replyText.length}/280",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 52.dp, top = 4.dp)
                                    )
                                }
                            }
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
private fun CommentRow(
    comment: PostComment,
    isNewlyAdded: Boolean,
    canDelete: Boolean,
    nowMillis: Long,
    onAuthorClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entranceScale = remember(comment.id) { Animatable(if (isNewlyAdded) 0.96f else 1f) }
    val entranceAlpha = remember(comment.id) { Animatable(if (isNewlyAdded) 0.35f else 1f) }
    var highlightAccent by remember(comment.id) { mutableStateOf(isNewlyAdded) }

    LaunchedEffect(comment.id, isNewlyAdded) {
        if (isNewlyAdded) {
            launch {
                entranceAlpha.animateTo(1f, tween(durationMillis = 220))
            }
            launch {
                entranceScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 460f)
                )
            }
            delay(850L)
            highlightAccent = false
        }
    }

    val borderColor by animateColorAsState(
        targetValue = if (highlightAccent) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 480),
        label = "comment_highlight_border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (highlightAccent) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(durationMillis = 480),
        label = "comment_highlight_bg"
    )

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
                alpha = entranceAlpha.value
            }
            .testTag("comment_row_${comment.id}")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            AvatarBadge(
                alias = comment.authorAlias,
                avatarBase64 = comment.authorAvatarBase64,
                size = 34.dp,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAuthorClick)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "@${comment.authorAlias}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onAuthorClick)
                    )
                    Text(
                        text = formatRelativeTime(comment.createdAt, nowMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (canDelete) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_comment_${comment.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
