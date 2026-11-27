package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.auth.signOutWithCleanup
import com.example.data.HavenRepository
import com.example.data.UiState
import com.example.ui.HavenViewModel
import com.example.ui.components.EmptySanctuaryCard
import com.example.ui.screens.AlertsAndSafetyScreen
import com.example.ui.screens.AliasSetupScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BlockedUsersScreen
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.PrivacySettingsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ThreadScreen
import com.example.ui.theme.HavenTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object HavenRoutes {
    const val FEED = "feed"
    const val CREATE = "create"
    const val ALERTS = "alerts"
    const val MY_PROFILE = "my_profile"
    const val PRIVACY = "privacy"
    const val BLOCKED_USERS = "blocked_users"
    const val THREAD = "thread/{postId}"
    const val USER_PROFILE = "user_profile/{userId}"

    fun thread(postId: String) = "thread/$postId"
    fun userProfile(userId: String) = "user_profile/$userId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HavenTheme {
                HavenRootNavigation()
            }
        }
    }
}

@Composable
fun HavenRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            HavenAuthenticatedShell(user = user)
        }
    }
}

@Composable
private fun HavenAuthenticatedShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: HavenViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HavenViewModel(HavenRepository(db), user.uid)
            }
        }
    )

    val profileState by viewModel.currentProfileState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val performSignOut: () -> Unit = {
        signOutWithCleanup(credentialManager, scope)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val state = profileState) {
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
                    .padding(24.dp)
            ) {
                EmptySanctuaryCard(
                    title = "Unable to load haven",
                    subtitle = state.message,
                    actionLabel = "Sign Out",
                    onActionClick = performSignOut
                )
            }
        }

        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                AliasSetupScreen(
                    suggestedName = user.displayName ?: "",
                    email = user.email ?: "",
                    isBusy = isBusy,
                    onCheckAvailability = viewModel::checkAliasAvailability,
                    onSubmitAlias = { alias, bio, onError ->
                        viewModel.completeAliasSetup(
                            alias = alias,
                            displayName = user.displayName ?: alias,
                            email = user.email ?: "",
                            bio = bio,
                            onSuccess = {},
                            onError = onError
                        )
                    },
                    onSwitchAccount = performSignOut
                )
            } else {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val feedListState = rememberLazyListState()

                val feedState by viewModel.feedState.collectAsStateWithLifecycle()
                val selectedCircle by viewModel.selectedCircle.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val matchingFriends by viewModel.matchingFriendsState.collectAsStateWithLifecycle()
                val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val likedPostIds by viewModel.likedPostIdsState.collectAsStateWithLifecycle()
                val reportedPosts by viewModel.reportedPostsState.collectAsStateWithLifecycle()
                val reportedPostIds by viewModel.reportedPostIdsState.collectAsStateWithLifecycle()
                val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
                val blockedUsers by viewModel.blockedUsersState.collectAsStateWithLifecycle()
                val blockableProfiles by viewModel.blockableProfilesState.collectAsStateWithLifecycle()
                val myPostsState by viewModel.myPostsState.collectAsStateWithLifecycle()

                val unreadAlertsCount = remember(alertsState) {
                    (alertsState as? UiState.Success)?.data?.count { !it.read } ?: 0
                }

                val showChrome = currentRoute in setOf(
                    HavenRoutes.FEED,
                    HavenRoutes.ALERTS,
                    HavenRoutes.MY_PROFILE
                )

                val navigateToTab: (String) -> Unit = { route ->
                    if (currentRoute == route && route == HavenRoutes.FEED) {
                        scope.launch { feedListState.animateScrollToItem(0) }
                    } else {
                        navController.navigate(route) {
                            popUpTo(HavenRoutes.FEED) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showChrome && useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                NavigationRailItem(
                                    selected = currentRoute == HavenRoutes.FEED,
                                    onClick = { navigateToTab(HavenRoutes.FEED) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == HavenRoutes.FEED) Icons.Rounded.Home else Icons.Outlined.Home,
                                            contentDescription = "Feed"
                                        )
                                    },
                                    label = { Text("Feed") }
                                )
                                NavigationRailItem(
                                    selected = false,
                                    onClick = {
                                        navController.navigate(HavenRoutes.CREATE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.AddCircle,
                                            contentDescription = "Create"
                                        )
                                    },
                                    label = { Text("Create") }
                                )
                                NavigationRailItem(
                                    selected = currentRoute == HavenRoutes.ALERTS,
                                    onClick = { navigateToTab(HavenRoutes.ALERTS) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (unreadAlertsCount > 0) {
                                                    Badge { Text("$unreadAlertsCount") }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (currentRoute == HavenRoutes.ALERTS) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                                contentDescription = "Alerts"
                                            )
                                        }
                                    },
                                    label = { Text("Alerts") }
                                )
                                NavigationRailItem(
                                    selected = currentRoute == HavenRoutes.MY_PROFILE,
                                    onClick = { navigateToTab(HavenRoutes.MY_PROFILE) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == HavenRoutes.MY_PROFILE) Icons.Rounded.Person else Icons.Outlined.Person,
                                            contentDescription = "Profile"
                                        )
                                    },
                                    label = { Text("Profile") }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        Scaffold(
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            bottomBar = {
                                if (showChrome && !useRail) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        windowInsets = WindowInsets.navigationBars
                                    ) {
                                        NavigationBarItem(
                                            selected = currentRoute == HavenRoutes.FEED,
                                            onClick = { navigateToTab(HavenRoutes.FEED) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (currentRoute == HavenRoutes.FEED) Icons.Rounded.Home else Icons.Outlined.Home,
                                                    contentDescription = "Feed"
                                                )
                                            },
                                            label = { Text("Feed") },
                                            modifier = Modifier.testTag("nav_feed")
                                        )
                                        NavigationBarItem(
                                            selected = false,
                                            onClick = {
                                                navController.navigate(HavenRoutes.CREATE) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.AddCircle,
                                                    contentDescription = "Create"
                                                )
                                            },
                                            label = { Text("Create") },
                                            modifier = Modifier.testTag("nav_create")
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == HavenRoutes.ALERTS,
                                            onClick = { navigateToTab(HavenRoutes.ALERTS) },
                                            icon = {
                                                BadgedBox(
                                                    badge = {
                                                        if (unreadAlertsCount > 0) {
                                                            Badge { Text("$unreadAlertsCount") }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (currentRoute == HavenRoutes.ALERTS) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                                        contentDescription = "Alerts"
                                                    )
                                                }
                                            },
                                            label = { Text("Alerts") },
                                            modifier = Modifier.testTag("nav_alerts")
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == HavenRoutes.MY_PROFILE,
                                            onClick = { navigateToTab(HavenRoutes.MY_PROFILE) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (currentRoute == HavenRoutes.MY_PROFILE) Icons.Rounded.Person else Icons.Outlined.Person,
                                                    contentDescription = "Profile"
                                                )
                                            },
                                            label = { Text("Profile") },
                                            modifier = Modifier.testTag("nav_profile")
                                        )
                                    }
                                }
                            },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.weight(1f)
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = HavenRoutes.FEED,
                                enterTransition = { fadeIn(animationSpec = tween(200)) },
                                exitTransition = { fadeOut(animationSpec = tween(160)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                                popExitTransition = { fadeOut(animationSpec = tween(160)) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                composable(HavenRoutes.FEED) {
                                    FeedScreen(
                                        currentProfile = profile,
                                        feedState = feedState,
                                        selectedCircle = selectedCircle,
                                        searchQuery = searchQuery,
                                        matchingFriends = matchingFriends,
                                        likedPostIds = likedPostIds,
                                        reportedPostIds = reportedPostIds,
                                        isRefreshing = isRefreshing,
                                        listState = feedListState,
                                        onRefresh = viewModel::refreshFeed,
                                        onSelectCircle = viewModel::selectCircle,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onCreateClick = {
                                            navController.navigate(HavenRoutes.CREATE) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenThread = { postId ->
                                            navController.navigate(HavenRoutes.thread(postId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenProfile = { uid ->
                                            if (uid == profile.uid) {
                                                navigateToTab(HavenRoutes.MY_PROFILE)
                                            } else {
                                                navController.navigate(HavenRoutes.userProfile(uid)) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        onToggleReaction = { post, liked ->
                                            viewModel.toggleReaction(post, profile, liked)
                                        },
                                        onReportPost = { post, reason, alsoBlockAuthor ->
                                            viewModel.reportPost(post, reason, alsoBlockAuthor)
                                        },
                                        onBlockUser = { uid, alias -> viewModel.blockUser(uid, alias) },
                                        onEditPostCaption = viewModel::updatePostCaption,
                                        onDeletePost = { postId -> viewModel.deletePost(postId) }
                                    )
                                }

                                composable(
                                    route = HavenRoutes.CREATE,
                                    enterTransition = {
                                        slideInVertically(animationSpec = tween(240)) { it / 4 } + fadeIn(tween(200))
                                    },
                                    popExitTransition = {
                                        slideOutVertically(animationSpec = tween(200)) { it / 4 } + fadeOut(tween(180))
                                    }
                                ) {
                                    CreatePostScreen(
                                        currentProfile = profile,
                                        isBusy = isBusy,
                                        onBack = { navController.popBackStack() },
                                        onPublish = { circle, caption, imageBase64 ->
                                            viewModel.publishPost(profile, circle, caption, imageBase64) {
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }

                                composable(
                                    route = HavenRoutes.THREAD,
                                    arguments = listOf(navArgument("postId") { type = NavType.StringType })
                                ) { entry ->
                                    val postId = entry.arguments?.getString("postId") ?: ""
                                    val postDetailState by remember(postId) {
                                        viewModel.observePostDetail(postId)
                                    }.collectAsStateWithLifecycle(initialValue = UiState.Loading)
                                    val commentsState by remember(postId) {
                                        viewModel.observeThreadComments(postId)
                                    }.collectAsStateWithLifecycle(initialValue = UiState.Loading)

                                    ThreadScreen(
                                        currentProfile = profile,
                                        postState = postDetailState,
                                        commentsState = commentsState,
                                        likedPostIds = likedPostIds,
                                        reportedPostIds = reportedPostIds,
                                        isBusy = isBusy,
                                        onBack = { navController.popBackStack() },
                                        onOpenProfile = { uid ->
                                            if (uid == profile.uid) {
                                                navigateToTab(HavenRoutes.MY_PROFILE)
                                            } else {
                                                navController.navigate(HavenRoutes.userProfile(uid)) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        onToggleReaction = { post, liked ->
                                            viewModel.toggleReaction(post, profile, liked)
                                        },
                                        onSendReply = { post, body, onSent ->
                                            viewModel.submitComment(post, profile, body, onSent)
                                        },
                                        onDeleteComment = viewModel::deleteComment,
                                        onReportPost = { post, reason, alsoBlockAuthor ->
                                            viewModel.reportPost(post, reason, alsoBlockAuthor) {
                                                navController.popBackStack()
                                            }
                                        },
                                        onBlockUser = { uid, alias ->
                                            viewModel.blockUser(uid, alias) {
                                                navController.popBackStack()
                                            }
                                        },
                                        onEditPostCaption = viewModel::updatePostCaption,
                                        onDeletePost = { id ->
                                            viewModel.deletePost(id) {
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }

                                composable(HavenRoutes.ALERTS) {
                                    AlertsAndSafetyScreen(
                                        alertsState = alertsState,
                                        blockedUsers = blockedUsers,
                                        reportedPosts = reportedPosts,
                                        onAlertClick = { alert ->
                                            if (!alert.read) viewModel.markAlertRead(alert.id)
                                            navController.navigate(HavenRoutes.thread(alert.postId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenBlockedUsers = {
                                            navController.navigate(HavenRoutes.BLOCKED_USERS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onUnblockUser = { blocked ->
                                            viewModel.unblockUser(blocked.blockedUid, blocked.blockedAlias)
                                        },
                                        onSignOut = performSignOut
                                    )
                                }

                                composable(HavenRoutes.MY_PROFILE) {
                                    ProfileScreen(
                                        isOwnProfile = true,
                                        profileState = UiState.Success(profile),
                                        postsState = myPostsState,
                                        likedPostIds = likedPostIds,
                                        reportedPostIds = reportedPostIds,
                                        isBlockedUser = false,
                                        onBack = null,
                                        onOpenPrivacySettings = {
                                            navController.navigate(HavenRoutes.PRIVACY) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onUpdateProfile = { bio, avatar ->
                                            viewModel.updateProfile(bio, avatar)
                                        },
                                        onOpenThread = { postId ->
                                            navController.navigate(HavenRoutes.thread(postId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onToggleReaction = { post, liked ->
                                            viewModel.toggleReaction(post, profile, liked)
                                        },
                                        onReportPost = { post, reason, alsoBlockAuthor ->
                                            viewModel.reportPost(post, reason, alsoBlockAuthor)
                                        },
                                        onBlockUser = { _, _ -> },
                                        onUnblockUser = { _, _ -> },
                                        onEditPostCaption = viewModel::updatePostCaption,
                                        onDeletePost = { postId -> viewModel.deletePost(postId) }
                                    )
                                }

                                composable(
                                    route = HavenRoutes.USER_PROFILE,
                                    arguments = listOf(navArgument("userId") { type = NavType.StringType })
                                ) { entry ->
                                    val targetUid = entry.arguments?.getString("userId") ?: ""
                                    val targetProfileState by remember(targetUid) {
                                        viewModel.observeTargetProfile(targetUid)
                                    }.collectAsStateWithLifecycle(initialValue = UiState.Loading)
                                    val targetPostsState by remember(targetUid) {
                                        viewModel.observeTargetUserPosts(targetUid)
                                    }.collectAsStateWithLifecycle(initialValue = UiState.Loading)
                                    val isBlocked = blockedUsers.any { it.blockedUid == targetUid }

                                    ProfileScreen(
                                        isOwnProfile = false,
                                        profileState = targetProfileState,
                                        postsState = targetPostsState,
                                        likedPostIds = likedPostIds,
                                        reportedPostIds = reportedPostIds,
                                        isBlockedUser = isBlocked,
                                        onBack = { navController.popBackStack() },
                                        onOpenPrivacySettings = {},
                                        onUpdateProfile = { _, _ -> },
                                        onOpenThread = { postId ->
                                            navController.navigate(HavenRoutes.thread(postId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onToggleReaction = { post, liked ->
                                            viewModel.toggleReaction(post, profile, liked)
                                        },
                                        onReportPost = { post, reason, alsoBlockAuthor ->
                                            viewModel.reportPost(post, reason, alsoBlockAuthor)
                                        },
                                        onBlockUser = { uid, alias ->
                                            viewModel.blockUser(uid, alias) {
                                                navController.popBackStack()
                                            }
                                        },
                                        onUnblockUser = { uid, alias ->
                                            viewModel.unblockUser(uid, alias)
                                        },
                                        onEditPostCaption = { _, _ -> },
                                        onDeletePost = {}
                                    )
                                }

                                composable(HavenRoutes.PRIVACY) {
                                    PrivacySettingsScreen(
                                        currentProfile = profile,
                                        blockedUsers = blockedUsers,
                                        submittedReportsCount = reportedPosts.size,
                                        isBusy = isBusy,
                                        onBack = { navController.popBackStack() },
                                        onSavePrivacy = viewModel::updatePrivacySettings,
                                        onOpenBlockedUsers = {
                                            navController.navigate(HavenRoutes.BLOCKED_USERS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onUnblockUser = { blocked ->
                                            viewModel.unblockUser(blocked.blockedUid, blocked.blockedAlias)
                                        },
                                        onLoadDataSummary = { onLoaded ->
                                            viewModel.loadPersonalDataSummary(profile, onLoaded)
                                        },
                                        onDeleteAllData = {
                                            viewModel.deleteAllDataAndSignOut(profile.alias) {
                                                performSignOut()
                                            }
                                        }
                                    )
                                }

                                composable(HavenRoutes.BLOCKED_USERS) {
                                    BlockedUsersScreen(
                                        blockedUsers = blockedUsers,
                                        blockableProfiles = blockableProfiles,
                                        onBack = { navController.popBackStack() },
                                        onBlockUser = { uid, alias -> viewModel.blockUser(uid, alias) },
                                        onUnblockUser = { blocked ->
                                            viewModel.unblockUser(blocked.blockedUid, blocked.blockedAlias)
                                        }
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
