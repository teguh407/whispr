package com.whispr.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.whispr.app.screens.*
import com.whispr.app.viewmodel.WhisprViewModel

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
    object VoiceCall : Screen("voice_call")
    object GifPicker : Screen("gif_picker")
    object Settings : Screen("settings")
    object Blocks : Screen("blocks")
    object Discover : Screen("discover")
    object Groups : Screen("groups")
    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }
    object Games : Screen("games")
    object Stories : Screen("stories")
}

@Composable
fun WhisprNavigation(viewModel: WhisprViewModel = viewModel()) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isAuthReady by viewModel.isAuthReady.collectAsState()

    if (!isAuthReady) {
        // Splash/loading screen while checking auth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B14)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF7C4DFF), strokeWidth = 3.dp)
        }
        return
    }

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
                onPostClick = { /* Handle once-view */ },
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
                onCall = { navController.navigate(Screen.VoiceCall.route) }
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
                onChats = { navController.navigate(Screen.ChatList.route) }
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

        composable(Screen.VoiceCall.route) {
            VoiceCallScreen(
                viewModel = viewModel,
                onEndCall = { navController.popBackStack() }
            )
        }

        composable(Screen.GifPicker.route) {
            GifPickerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onGifSelected = { url ->
                    // Send GIF URL back to chat
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
