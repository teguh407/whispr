package com.whispr.id

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.whispr.id.navigation.WhisprNavigation
import com.whispr.id.network.TokenStore
import com.whispr.id.ui.theme.ThemeMode
import com.whispr.id.ui.theme.WhisprTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notifPermLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* granted or not — non-fatal */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Notification channels + runtime permission (Android 13+)
        com.whispr.id.util.WhisprMessagingService.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
