package com.whispr.app.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.network.ApiClient
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    var saved by remember { mutableStateOf(false) }

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
            Text("Server", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; saved = false },
                label = { Text("API Base URL") },
                placeholder = { Text("http://43.153.207.36:8004/") },
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

            Text("About", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text("Whispr v1.1.0", color = TextSecondary)
            Text("Anonymous. Real. You.", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
