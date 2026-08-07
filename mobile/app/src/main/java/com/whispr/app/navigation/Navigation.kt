package com.whispr.app.navigation

import androidx.compose.runtime.*
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
}

@Composable
fun WhisprNavigation(viewModel: WhisprViewModel = viewModel()) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

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
                        "links" -> navController.navigate(Screen.Links.route)
                        "accounts" -> navController.navigate(Screen.Accounts.route)
                        "profile" -> navController.navigate(Screen.Profile.route)
                        "chats" -> navController.navigate(Screen.ChatList.route)
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
                onCreateChat = { /* Show user picker */ },
                onBack = { navController.popBackStack() }
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

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
