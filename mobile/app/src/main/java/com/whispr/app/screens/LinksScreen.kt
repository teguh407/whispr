package com.whispr.app.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.data.ShareableLink
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    val links by viewModel.links.collectAsState()
    var newUrl by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadLinks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shareable Links", fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Create new link
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("Link title") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        unfocusedContainerColor = CardBg,
                        focusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (newUrl.isNotBlank()) {
                            viewModel.createLink(newUrl)
                            newUrl = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple)
                ) {
                    Icon(Icons.Default.Add, "Create Link")
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(links) { link ->
                    LinkItem(link = link, context = context)
                }
            }
        }
    }
}

@Composable
fun LinkItem(link: ShareableLink, context: Context) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Link, null, tint = PrimaryPurple)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(link.code, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(link.url, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                Text("${link.messageCount} messages", color = AccentTeal, fontSize = 11.sp)
            }
            IconButton(onClick = {
                val clip = ClipData.newPlainText("link", link.url)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(clip)
            }) {
                Icon(Icons.Default.ContentCopy, "Copy", tint = PrimaryPurple)
            }
        }
    }
}
