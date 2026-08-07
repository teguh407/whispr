package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.whispr.app.data.Post
import com.whispr.app.data.User
import com.whispr.app.ui.components.*
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

private val feedTabs = listOf("For You", "Nearby", "Following")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: WhisprViewModel,
    onCreatePost: () -> Unit,
    onPostClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val posts by viewModel.posts.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadPosts() }

    val greeting = greetingForNow()
    val displayName = currentUser?.displayName ?: "Stranger"

    Scaffold(
        containerColor = Background,
        bottomBar = {
            WhisprBottomBar(
                current = "feed",
                onNavigate = onNavigate,
                onCreate = onCreatePost
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Header
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Whispr",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VioletBright
                        )
                        Row {
                            IconButton(onClick = { onNavigate("explore") }) {
                                Icon(Icons.Outlined.Search, "Search", tint = TextSecondary)
                            }
                            IconButton(onClick = { onNavigate("notifications") }) {
                                Icon(Icons.Outlined.Notifications, "Notifications", tint = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$greeting, $displayName",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn, null,
                            tint = VioletBright, modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Nearby · 2 km away", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }

            // Daily Question card
            item { DailyQuestionCard(onJoin = onCreatePost) }

            // Tabs
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    feedTabs.forEachIndexed { i, label ->
                        FeedTab(label, selectedTab == i) { selectedTab = i }
                    }
                }
            }

            // Posts
            if (loading && posts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                        CircularProgressIndicator(color = VioletBright)
                    }
                }
            } else if (posts.isEmpty()) {
                item { EmptyFeed() }
            } else {
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onClick = { onPostClick(post.id) },
                        onUpvote = { viewModel.upvotePost(post.id) },
                        onReply = { onPostClick(post.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) PrimaryPurple else ChipBg
    val fg = if (selected) Color.White else TextSecondary
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(50))
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DailyQuestionCard(onJoin: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("DAILY QUESTION", color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "If you could start over anywhere, where would you go?",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
                onClick = onJoin
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Join the conversation", color = Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.ArrowForward, null, tint = Color.White,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onUpvote: () -> Unit,
    onReply: () -> Unit
) {
    val authorName = post.author?.displayName ?: post.author?.username ?: "Anonymous"
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Author row
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(authorName, size = 40)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(authorName, color = TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("· 2 km away", color = TextTertiary, fontSize = 11.sp)
                }
                Text(relativeTime(post.createdAt), color = TextTertiary, fontSize = 11.sp)
            }

            Spacer(Modifier.height(10.dp))

            // Content
            Text(post.content, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp)

            if (post.hasOnceView) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Visibility, null, tint = VioletBright,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Once-view", color = VioletBright, fontSize = 11.sp)
                }
            }

            // Tags
            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.take(3).forEach { TagChip(it) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = ChipBg, thickness = 1.dp)
            Spacer(Modifier.height(6.dp))

            // Actions
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    text = "Reply${if (post.repliesCount > 0) " ${post.repliesCount}" else ""}",
                    tint = TextSecondary,
                    onClick = onReply
                )
                Spacer(Modifier.width(20.dp))
                ActionButton(
                    icon = if (post.isUpvoted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    text = if (post.upvotes > 0) "${post.upvotes}" else "",
                    tint = if (post.isUpvoted) PrimaryPink else TextSecondary,
                    onClick = onUpvote
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Share, "Share", tint = TextTertiary,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50))
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        if (text.isNotEmpty()) {
            Text(text, color = tint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyFeed() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Forum, null, tint = TextTertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No whispers yet", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Be the first to share something.", color = TextTertiary, fontSize = 13.sp)
    }
}

// ── helpers ──
private fun greetingForNow(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val t = java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        val diff = System.currentTimeMillis() - t
        val m = diff / 60000
        when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            m < 1440 -> "${m / 60}h"
            else -> "${m / 1440}d"
        }
    } catch (e: Exception) { "" }
}
