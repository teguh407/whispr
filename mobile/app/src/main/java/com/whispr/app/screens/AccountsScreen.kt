package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val currentUserId = viewModel.currentUser.collectAsState().value?.id

    LaunchedEffect(Unit) { viewModel.loadAccounts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }, containerColor = PrimaryPurple) {
                Icon(Icons.Default.Add, "Add Account", tint = Color.White)
            }
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts) { account ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (account.isActive) PrimaryPurple.copy(alpha = 0.2f) else CardBg
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(PrimaryPurple, PrimaryPink))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                account.displayName?.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.displayName ?: account.username, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("@${account.username}", color = TextSecondary, fontSize = 12.sp)
                        }
                        if (account.isActive) {
                            Surface(shape = CircleShape, color = SuccessGreen, modifier = Modifier.size(12.dp)) {}
                        }
                    }
                }
            }
        }

        // Create Account Dialog
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = Surface,
                title = { Text("New Account", color = TextPrimary) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newUsername, onValueChange = { newUsername = it },
                            label = { Text("Username") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                unfocusedContainerColor = CardBg, focusedContainerColor = Color.Transparent,
                                focusedBorderColor = PrimaryPurple, unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newDisplayName, onValueChange = { newDisplayName = it },
                            label = { Text("Display Name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                unfocusedContainerColor = CardBg, focusedContainerColor = Color.Transparent,
                                focusedBorderColor = PrimaryPurple, unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword, onValueChange = { newPassword = it },
                            label = { Text("Password") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                unfocusedContainerColor = CardBg, focusedContainerColor = Color.Transparent,
                                focusedBorderColor = PrimaryPurple, unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.createAccount(newUsername, newPassword, newDisplayName)
                        showDialog = false
                        newUsername = ""; newDisplayName = ""; newPassword = ""
                    }) { Text("Create", color = PrimaryPurple) }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel", color = TextSecondary) }
                }
            )
        }
    }
}
