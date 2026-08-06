package com.whispr.app.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.whispr.app.screens.FeedScreen
import com.whispr.app.screens.LoginScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Feed : Screen("feed/{token}") {
        fun createRoute(token: String) = "feed/$token"
    }
}

@Composable
fun WhisprNavigation() {
    val navController = rememberNavController()
    var token by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { newToken ->
                    token = newToken
                    navController.navigate(Screen.Feed.createRoute(newToken)) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { }
            )
        }

        composable(
            route = Screen.Feed.route,
            arguments = listOf(navArgument("token") { type = NavType.StringType })
        ) { backStackEntry ->
            val feedToken = backStackEntry.arguments?.getString("token") ?: token ?: ""
            FeedScreen(
                token = feedToken,
                onLogout = {
                    token = null
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
