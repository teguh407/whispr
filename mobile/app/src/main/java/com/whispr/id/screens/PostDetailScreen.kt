package com.whispr.id.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.whispr.id.data.Post
import com.whispr.id.data.Reply
import com.whispr.id.data.User
import com.whispr.id.ui.components.PersonaAvatar
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {}
) {
    val post by viewModel.postDetail.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var replyText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Reply?>(null) }  // null = reply to post

    LaunchedEffect(postId) {
        viewModel.clearPostDetail()
        viewModel.loadPostDetail(postId)
        viewModel.loadReplies(postId)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearPostDetail() }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whisper", fontWeight = FontWeight.Bold) },
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
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                val current = post
                if (current != null) {
                    item { PostDetailHeader(current, onAuthorClick = { onAuthorClick(current.author?.id ?: "") }) }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VioletBright)
                        }
                    }
                }

                // Replies section header
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Replies", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${post?.repliesCount ?: replies.size}", color = TextTertiary, fontSize = 13.sp)
                    }
                }

                if (replies.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TextTertiary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No replies yet", color = TextSecondary, fontSize = 14.sp)
                            Text("Be the first to respond", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                } else {
                    // Flatten nested replies: top-level first, children indented under parent
                    val roots = replies.filter { it.parentId == null }
                    items(roots, key = { it.id }) { root ->
                        ReplyThread(root, replies, currentUser) { target ->
                            replyingTo = target
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            // Reply input bar
            Surface(color = Surface, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (replyingTo != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = ChipBg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "Replying to @${replyingTo?.author?.username ?: ""}",
                                    color = VioletBright,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Cancel reply", tint = TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Add a reply...", color = TextTertiary) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                unfocusedContainerColor = CardBg,
                                focusedContainerColor = CardBg,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = replyText.trim()
                                if (text.isNotBlank()) {
                                    viewModel.createReply(
                                        postId,
                                        text,
                                        parentId = replyingTo?.id
                                    ) {
                                        replyText = ""
                                        replyingTo = null
                                    }
                                }
                            },
                            enabled = replyText.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (replyText.isNotBlank()) PrimaryPurple else ChipBg)
                        ) {
                            Icon(Icons.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Post header with location ──
@Composable
private fun PostDetailHeader(post: Post, onAuthorClick: () -> Unit) {
    val authorName = post.author?.displayName ?: post.author?.username ?: "Anonymous"
    val bg = postBackgroundById(if (post.bgType == "gradient") post.bgValue else null)
    val isConfession = post.postType == "confession"

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Author row (clickable avatar)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonaAvatar(authorName, size = 44, onClick = onAuthorClick)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(authorName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = VioletBright, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        locationLabel(post),
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }
            Text(relativeTime(post.createdAt), color = TextTertiary, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Content (reuse PostCard visuals)
        if (bg != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(bg.colors))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    post.content,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                post.content,
                color = if (isConfession) TextSecondary else TextPrimary,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontStyle = if (isConfession) androidx.compose.ui.text.font.FontStyle.Italic
                            else androidx.compose.ui.text.font.FontStyle.Normal
            )
        }

        if (post.hasOnceView) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, null, tint = VioletBright, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Once-view", color = VioletBright, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider(color = ChipBg, thickness = 1.dp)
        Spacer(Modifier.height(4.dp))

        // Action row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${post.upvotes} ♥", color = if (post.isUpvoted) PrimaryPink else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Text("${post.repliesCount} replies", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (post.postType != "anonymous" && post.postType != "confession") {
                Text(post.postType.uppercase(), color = VioletBright, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

private fun locationLabel(post: Post): String {
    val city = post.author?.city
    val dist = post.distanceKm
    val parts = mutableListOf<String>()
    if (!city.isNullOrBlank()) parts.add(city)
    if (dist != null) parts.add("${dist} km away")
    if (parts.isEmpty()) parts.add("Somewhere")
    return parts.joinToString(" · ")
}

// ── Nested reply thread ──
@Composable
private fun ReplyThread(
    root: Reply,
    all: List<Reply>,
    currentUser: User?,
    onReply: (Reply) -> Unit
) {
    val children = all.filter { it.parentId == root.id }
    val depth = 0

    ReplyBubble(root, depth, currentUser, onReply)
    children.forEach { child ->
        ReplyBubble(child, depth + 1, currentUser, onReply)
        // grand-children (level 3)
        all.filter { it.parentId == child.id }.forEach { grand ->
            ReplyBubble(grand, depth + 2, currentUser, onReply)
        }
    }
}

@Composable
private fun ReplyBubble(
    reply: Reply,
    depth: Int,
    currentUser: User?,
    onReply: (Reply) -> Unit
) {
    val authorName = reply.author?.displayName ?: reply.author?.username ?: "Ghost"
    val indent = (depth * 20).dp
    val isMine = reply.author?.id == currentUser?.id

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // Indent guide
        if (depth > 0) {
            Spacer(Modifier.width(indent))
        }
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonaAvatar(authorName, size = 26)
                    Spacer(Modifier.width(8.dp))
                    Text(authorName, color = if (isMine) VioletBright else TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(relativeTime(reply.createdAt), color = TextTertiary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(reply.content, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onReply(reply) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Outlined.Reply, "Reply", tint = TextTertiary, modifier = Modifier.size(14.dp))
                    }
                    Text("Reply", color = TextTertiary, fontSize = 11.sp)
                }
            }
        }
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
