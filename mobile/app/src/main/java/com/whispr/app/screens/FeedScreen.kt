package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import com.whispr.app.network.ApiClient
import com.whispr.app.network.PostResponse
import com.whispr.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    token: String,
    onLogout: () -> Unit
) {
    var posts by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var newPostContent by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    fun loadPosts() {
        scope.launch {
            isLoading = true
            try {
                posts = ApiClient.api.getPosts("Bearer $token")
            } catch (e: Exception) {
                println("Failed to load posts: ${e.message}")
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPosts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Whispr",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                actions = {
                    IconButton(onClick = { loadPosts() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Text("🚪", fontSize = 18.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Create post
            item {
                CreatePostCard(
                    content = newPostContent,
                    onContentChange = { newPostContent = it },
                    isCreating = isCreating,
                    onCreatePost = {
                        scope.launch {
                            isCreating = true
                            try {
                                ApiClient.api.createPost(
                                    "Bearer $token",
                                    com.whispr.app.network.PostCreate(newPostContent)
                                )
                                newPostContent = ""
                                loadPosts()
                            } catch (e: Exception) {
                                println("Failed to create post: ${e.message}")
                            }
                            isCreating = false
                        }
                    }
                )
            }

            // Posts
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(posts) { post ->
                    PostCard(
                        post = post,
                        onUpvote = {
                            scope.launch {
                                try {
                                    ApiClient.api.upvotePost("Bearer $token", post.id)
                                    loadPosts()
                                } catch (e: Exception) {
                                    println("Failed to upvote: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CreatePostCard(
    content: String,
    onContentChange: (String) -> Unit,
    isCreating: Boolean,
    onCreatePost: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                placeholder = { Text("What's on your mind?", color = Muted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF2a2a3a),
                    focusedContainerColor = SurfaceVariant,
                    unfocusedContainerColor = SurfaceVariant
                )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                        Text("📷", fontSize = 16.sp)
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                        Text("🎤", fontSize = 16.sp)
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                        Text("📊", fontSize = 16.sp)
                    }
                }
                
                Button(
                    onClick = onCreatePost,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp),
                    enabled = content.isNotBlank() && !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Post", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: PostResponse,
    onUpvote: () -> Unit
) {
    val colors = listOf(Accent, Pink80, Green, Color(0xFFfb923c), Color(0xFF60a5fa))
    val avatarColor = colors[post.author.username.hashCode().toInt() % colors.size]
    val initials = post.author.username.take(2).uppercase()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(avatarColor, avatarColor.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = post.author.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${timeAgo(post.created_at)} · ${post.author.karma_level}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Content
            Text(
                text = post.content,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            // Tags
            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            color = Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(AccentSoft, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(SurfaceVariant, RoundedCornerShape(0.dp)),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reply", fontWeight = FontWeight.SemiBold)
                }
                
                OutlinedButton(
                    onClick = onUpvote,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Pink80
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.upvotes}")
                }
                
                IconButton(onClick = { }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Muted
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔮", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No posts yet",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Be the first to share something anonymously!",
                color = Muted,
                fontSize = 14.sp
            )
        }
    }
}

fun timeAgo(dateString: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = format.parse(dateString) ?: return ""
        val now = Date()
        val diff = now.time - date.time
        
        when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000}m"
            diff < 86_400_000 -> "${diff / 3_600_000}h"
            else -> "${diff / 86_400_000}d"
        }
    } catch (e: Exception) {
        ""
    }
}
