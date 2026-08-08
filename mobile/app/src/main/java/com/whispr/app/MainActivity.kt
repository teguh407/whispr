package com.whispr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.whispr.app.navigation.WhisprNavigation
import com.whispr.app.network.TokenStore
import com.whispr.app.ui.theme.ThemeMode
import com.whispr.app.ui.theme.WhisprTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(ThemeMode.System) }

            LaunchedEffect(Unit) {
                themeMode = TokenStore.getThemeMode(context)
            }

            WhisprTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhisprNavigation(
                        onThemeChange = { newMode ->
                            themeMode = newMode
                            scope.launch { TokenStore.saveThemeMode(context, newMode) }
                        }
                    )
                }
            }
        }
    }
}
