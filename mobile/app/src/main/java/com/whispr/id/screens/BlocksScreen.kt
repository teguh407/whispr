package com.whispr.id.screens

import androidx.compose.foundation.background
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
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    val blocks by viewModel.blocks.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var unblockTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    LaunchedEffect(Unit) { viewModel.loadBlocks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Users", fontWeight = FontWeight.Bold) },
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
        if (loading && blocks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TextSecondary)
            }
        } else if (blocks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Block, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No blocked users", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(blocks) { blocked ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(ErrorRed, PrimaryPink))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    blocked.user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(blocked.user?.displayName ?: "User", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("@${blocked.user?.username}", color = TextSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = { unblockTarget = blocked.id to (blocked.user?.displayName) }) {
                                Text("Unblock", color = PrimaryPurple)
                            }
                        }
                    }
                }
            }
        }

        // Unblock confirmation dialog
        unblockTarget?.let { (userId, displayName) ->
            AlertDialog(
                onDismissRequest = { unblockTarget = null },
                containerColor = Surface,
                title = { Text("Unblock user?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to unblock ${displayName ?: "this user"}? They will be able to message you again.",
                        color = TextSecondary, fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.unblockUser(userId)
                        unblockTarget = null
                    }) { Text("Unblock", color = PrimaryPurple) }
                },
                dismissButton = {
                    TextButton(onClick = { unblockTarget = null }) { Text("Cancel", color = TextSecondary) }
                }
            )
        }
    }
}
