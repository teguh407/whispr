package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.network.ApiClient
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    var serverUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf(false) }
    var showEditBaseUrl by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    val deleteResult by viewModel.deleteAccountResult.collectAsState()

    // Close dialog + reset on success (logout already fired → app restarts to login)
    LaunchedEffect(deleteResult) {
        if (deleteResult == "ok") showDeleteConfirm = false
    }
    var selectedTheme by remember { mutableStateOf(ThemeMode.System) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // ── Appearance / Theme ──
            Text("Appearance", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))

            Text("Theme", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            // Theme selector cards
            ThemeOptionCard(
                title = "System",
                subtitle = "Follow device setting",
                icon = Icons.Default.BrightnessAuto,
                selected = selectedTheme == ThemeMode.System,
                onClick = {
                    selectedTheme = ThemeMode.System
                    onThemeChange(ThemeMode.System)
                }
            )
            Spacer(Modifier.height(8.dp))
            ThemeOptionCard(
                title = "Dark",
                subtitle = "Whispr classic dark-violet",
                icon = Icons.Default.DarkMode,
                selected = selectedTheme == ThemeMode.Dark,
                onClick = {
                    selectedTheme = ThemeMode.Dark
                    onThemeChange(ThemeMode.Dark)
                }
            )
            Spacer(Modifier.height(8.dp))
            ThemeOptionCard(
                title = "Light",
                subtitle = "Clean white + violet accents",
                icon = Icons.Default.LightMode,
                selected = selectedTheme == ThemeMode.Light,
                onClick = {
                    selectedTheme = ThemeMode.Light
                    onThemeChange(ThemeMode.Light)
                }
            )

            Spacer(Modifier.height(32.dp))
            Divider(color = TextSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(24.dp))

            // ── Server ──
            Text("Server", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; saved = false },
                label = { Text("API Base URL") },
                placeholder = { Text("https://whispr.tdsign.app/") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    unfocusedContainerColor = CardBg,
                    focusedContainerColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.setBaseUrl(serverUrl)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                if (saved) Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(if (saved) "Saved!" else "Save Server URL")
            }

            Spacer(Modifier.height(32.dp))
            Divider(color = TextSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(24.dp))

            // ── About ──
            Text("About", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text("Whispr v1.3.0", color = TextSecondary)
            Text("Anonymous. Real. You.", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Privacy Policy",
                color = PrimaryPurple,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://whispr.tdsign.app/privacy.html")
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    androidx.core.content.ContextCompat.startActivity(
                        context, intent, null
                    )
                }
            )

            Spacer(Modifier.height(32.dp))
            Divider(color = TextSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(24.dp))

            // ── Danger Zone (Google Play requires in-app account deletion) ──
            Text("Danger Zone", fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ErrorRed.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Delete Account", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Permanently delete your account and all data. This cannot be undone.",
                        color = TextSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showDeleteConfirm = true; viewModel.resetDeleteAccountResult() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete My Account")
                    }
                }
            }
        }
    }

    // ── Delete confirmation dialog ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardBg,
            title = { Text("Delete account?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "All your posts, chats, stories, and karma will be permanently deleted.",
                        color = TextSecondary, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deleteResult != null && deleteResult != "ok") {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteResult!!, color = ErrorRed, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(deletePassword.ifBlank { null }) },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PrimaryPurple.copy(alpha = 0.12f) else CardBg,
        border = androidx.compose.foundation.BorderStroke(
            2.dp, if (selected) PrimaryPurple else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (selected) PrimaryPurple else TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextTertiary, fontSize = 12.sp)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = PrimaryPurple, modifier = Modifier.size(24.dp))
            }
        }
    }
}
