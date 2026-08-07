package com.whispr.app.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.whispr.app.data.Post
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: WhisprViewModel,
    onCreatePost: () -> Unit,
    onPostClick: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.loading.collectAsState()
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val tags = listOf("general", "confession", "question", "meme", "story", "advice", "hot", "new")

    LaunchedEffect(Unit) { viewModel.loadPosts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feed", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = { onNavigate("links") }) {
                        Icon(Icons.Default.Link, "Links", tint = PrimaryPurple)
                    }
                    IconButton(onClick = { onNavigate("accounts") }) {
                        Icon(Icons.Default.SwitchAccount, "Accounts", tint = PrimaryPurple)
                    }
                    IconButton(onClick = { onNavigate("profile") }) {
                        Icon(Icons.Default.Person, "Profile", tint = PrimaryPurple)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePost,
                containerColor = PrimaryPink,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Create Post")
            }
        },
        containerColor = Background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tags filter
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null; viewModel.loadPosts() },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(tags) { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = tag; viewModel.loadPosts(tag) },
                        label = { Text("#$tag") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryPurple)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(posts) { post ->
                        PostCard(
                            post = post,
                            onUpvote = { viewModel.upvotePost(post.id) },
                            onDelete = { viewModel.deletePost(post.id) },
                            onClick = { if (post.hasOnceView) onPostClick(post.id) },
                            currentUserId = viewModel.currentUser.collectAsState().value?.id ?: ""
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onUpvote: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    currentUserId: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .then(if (post.hasOnceView) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(PrimaryPurple, PrimaryPink))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        post.author?.displayName?.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        post.author?.displayName ?: "Anonymous",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    post.createdAt?.let {
                        Text(formatTime(it), color = TextSecondary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (post.hasOnceView) {
                    Icon(Icons.Default.Visibility, null, tint = PrimaryPink, modifier = Modifier.size(16.dp))
                }
                if (post.authorId == currentUserId) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Content
            Text(post.content, color = TextPrimary, lineHeight = 22.sp)

            // Tags
            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(post.tags) { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryPurple.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "#$tag",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = PrimaryPurple,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Upvote
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUpvote, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        "Upvote",
                        tint = if (post.isUpvoted) UpvoteColor else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "${post.upvoteCount}",
                    color = if (post.isUpvoted) UpvoteColor else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatTime(isoTime: String): String {
    return try {
        val instant = Instant.parse(isoTime)
        val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        isoTime.take(16)
    }
}
