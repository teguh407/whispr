package com.whispr.id.navigation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.id.R
import com.whispr.id.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.whispr.id.screens.*
import com.whispr.id.ui.theme.ThemeMode
import com.whispr.id.viewmodel.WhisprViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Feed : Screen("feed")
    object CreatePost : Screen("create_post")
    object ChatList : Screen("chats")
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    object Profile : Screen("profile")
    object Accounts : Screen("accounts")
    object Links : Screen("links")
    object VoiceCall : Screen("voice_call/{peerId}/{incoming}") {
        fun createRoute(peerId: String, incoming: Boolean = false) = "voice_call/$peerId/$incoming"
    }
    object GifPicker : Screen("gif_picker")
    object Settings : Screen("settings")
    object Blocks : Screen("blocks")
    object Discover : Screen("discover")
    object Groups : Screen("groups")
    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }
    object Games : Screen("games")
    object Match : Screen("match")
    object Stories : Screen("stories")
    object PostDetail : Screen("post/{postId}") {
        fun createRoute(postId: String) = "post/$postId"
    }
    object ProfileDetail : Screen("profile_detail/{userId}") {
        fun createRoute(userId: String) = "profile_detail/$userId"
    }
}

@Composable
fun WhisprNavigation(
    viewModel: WhisprViewModel = viewModel(),
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isAuthReady by viewModel.isAuthReady.collectAsState()

    if (!isAuthReady) {
        // Branded splash screen while checking auth
        val infiniteTransition = rememberInfiniteTransition(label = "splash")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logoPulse"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A0A2E),
                            Color(0xFF0D0517)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.whispr_logo),
                    contentDescription = "Whispr",
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Whispr",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Connecting...",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = PrimaryPurple,
                trackColor = PrimaryPurple.copy(alpha = 0.2f)
            )
        }
        return
    }

    val globalError by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Keep a global call-signaling listener open while logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.connectCallListener()
        } else {
            viewModel.disconnectCallListener()
        }
    }

    // Auto-open incoming call UI when server pushes incoming_call
    val incomingCall by viewModel.incomingCall.collectAsState()
    LaunchedEffect(incomingCall) {
        incomingCall?.let { call ->
            val callerId = call.caller?.id ?: ""
            if (callerId.isNotBlank() && currentRoute != Screen.VoiceCall.route) {
                navController.navigate(Screen.VoiceCall.createRoute(callerId, incoming = true))
                // Preserve callId in ViewModel for the screen to consume
                viewModel.setActiveCallId(call.callId)
            }
            viewModel.setIncomingCall(null)
        }
    }

    LaunchedEffect(globalError) {
        globalError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    androidx.compose.material3.Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Feed.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onGoToRegister = { navController.navigate(Screen.Register.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = viewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Feed.route) {
            FeedScreen(
                viewModel = viewModel,
                onCreatePost = { navController.navigate(Screen.CreatePost.route) },
                onPostClick = { postId ->
                    navController.navigate(Screen.PostDetail.createRoute(postId))
                },
                onAuthorClick = { userId ->
                    navController.navigate(Screen.ProfileDetail.createRoute(userId))
                },
                onNavigate = { route ->
                    when (route) {
                        "feed" -> { /* already here */ }
                        "explore" -> navController.navigate(Screen.Discover.route) { launchSingleTop = true }
                        "discover" -> navController.navigate(Screen.Discover.route) { launchSingleTop = true }
                        "groups" -> navController.navigate(Screen.Groups.route) { launchSingleTop = true }
                        "games" -> navController.navigate(Screen.Games.route) { launchSingleTop = true }
                        "stories" -> navController.navigate(Screen.Stories.route) { launchSingleTop = true }
                        "links" -> navController.navigate(Screen.Links.route) { launchSingleTop = true }
                        "accounts" -> navController.navigate(Screen.Accounts.route) { launchSingleTop = true }
                        "profile" -> navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                        "chats" -> navController.navigate(Screen.ChatList.route) { launchSingleTop = true }
                    }
                }
            )
        }

        composable(Screen.CreatePost.route) {
            CreatePostScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                viewModel = viewModel,
                onChatClick = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                },
                onCreateChat = { navController.navigate(Screen.CreatePost.route) },
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    when (route) {
                        "chats" -> { /* already here */ }
                        "feed" -> navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Feed.route) { inclusive = true }
                            launchSingleTop = true
                        }
                        "explore" -> navController.navigate(Screen.Discover.route) { launchSingleTop = true }
                        "discover" -> navController.navigate(Screen.Discover.route) { launchSingleTop = true }
                        "profile" -> navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                    }
                }
            )
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onGifPicker = { navController.navigate(Screen.GifPicker.route) },
                onCall = { peerId ->
                    viewModel.startCall(peerId)
                    navController.navigate(Screen.VoiceCall.createRoute(peerId, incoming = false))
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBlocks = { navController.navigate(Screen.Blocks.route) },
                onChats = { navController.navigate(Screen.ChatList.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onSwitchAccount = { navController.navigate(Screen.Accounts.route) }
            )
        }

        composable(Screen.Accounts.route) {
            AccountsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Links.route) {
            LinksScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.VoiceCall.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("incoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val incoming = backStackEntry.arguments?.getBoolean("incoming") ?: false
            // Peer display name from chats list (fallback: Anonymous)
            val peerName = viewModel.chats.value
                .firstOrNull { it.user?.id == peerId }
                ?.user?.let { it.displayName ?: it.username } ?: "Anonymous"
            VoiceCallScreen(
                viewModel = viewModel,
                onEndCall = { navController.popBackStack() },
                peerName = peerName,
                peerId = peerId,
                isIncoming = incoming
            )
        }

        composable(Screen.GifPicker.route) {
            GifPickerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onGifSelected = { url ->
                    // Stage the URL; ChatScreen picks it up on resume
                    viewModel.setPendingGif(url)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Blocks.route) {
            BlocksScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Discover.route) {
            DiscoverScreen(
                viewModel = viewModel,
                onMessage = { userId ->
                    navController.navigate(Screen.Chat.createRoute(userId))
                },
                onNavigate = { route ->
                    when (route) {
                        "feed" -> navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Feed.route) { inclusive = true }
                            launchSingleTop = true
                        }
                        "chats" -> navController.navigate(Screen.ChatList.route) { launchSingleTop = true }
                        "profile" -> navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                    }
                },
                onCreate = { navController.navigate(Screen.CreatePost.route) }
            )
        }

        composable(Screen.Groups.route) {
            GroupsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onGroupClick = { groupId ->
                    navController.navigate(Screen.GroupChat.createRoute(groupId))
                }
            )
        }

        composable(
            Screen.GroupChat.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupChatScreen(
                groupId = groupId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Games.route) {
            GamesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMatch = { navController.navigate(Screen.Match.route) }
            )
        }

        composable(Screen.Match.route) {
            MatchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stories.route) {
            StoriesScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    when (route) {
                        "feed" -> navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Feed.route) { inclusive = true }
                            launchSingleTop = true
                        }
                        "explore" -> navController.navigate(Screen.Discover.route) { launchSingleTop = true }
                        "chats" -> navController.navigate(Screen.ChatList.route) { launchSingleTop = true }
                        "profile" -> navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                    }
                },
                onCreate = { navController.navigate(Screen.CreatePost.route) }
            )
        }

        composable(
            Screen.PostDetail.route,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            PostDetailScreen(
                postId = postId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAuthorClick = { userId ->
                    navController.navigate(Screen.ProfileDetail.createRoute(userId))
                }
            )
        }

        composable(
            Screen.ProfileDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            ProfileDetailScreen(
                userId = userId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMessage = { targetId ->
                    navController.navigate(Screen.Chat.createRoute(targetId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onThemeChange = onThemeChange
            )
        }
    }
    }
}
