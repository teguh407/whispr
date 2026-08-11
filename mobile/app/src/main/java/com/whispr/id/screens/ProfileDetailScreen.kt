package com.whispr.id.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.whispr.id.network.ApiClient
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    userId: String,
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val profile by viewModel.publicProfile.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    LaunchedEffect(userId) {
        viewModel.clearPublicProfile()
        viewModel.loadPublicProfile(userId)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearPublicProfile() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        val p = profile
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VioletBright)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                if (!p.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ApiClient.buildMediaUrl(p.avatarUrl),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        (p.displayName ?: p.username).firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Text(p.displayName ?: p.username, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("@${p.username}", color = TextSecondary, fontSize = 14.sp)
            if (!p.city.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = VioletBright, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p.city, color = TextSecondary, fontSize = 13.sp)
                }
            }
            if (!p.bio.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(p.bio, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            Spacer(Modifier.height(20.dp))

            // Stats
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Karma", "${p.karma}")
                    StatItem("Posts", "${p.postsCount}")
                    StatItem("Upvotes", "${p.totalUpvotes}")
                    StatItem("Days", "${p.daysActive}")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Message button (unless self)
            if (!p.isSelf) {
                Button(
                    onClick = { onMessage(p.id) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Message", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
