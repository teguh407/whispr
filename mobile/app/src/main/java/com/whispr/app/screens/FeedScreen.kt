package com.whispr.app.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.whispr.app.data.Post
import com.whispr.app.data.Story
import com.whispr.app.data.TrendingTag
import com.whispr.app.data.User
import com.whispr.app.ui.components.*
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

private val feedTabs = listOf("Hot", "Global", "Local", "Confessions")

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
    val error by viewModel.error.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val trending by viewModel.trending.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    var reportTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadPosts(tab = "hot") }
    LaunchedEffect(Unit) { viewModel.loadStories() }
    LaunchedEffect(Unit) { viewModel.loadTrending() }

    LaunchedEffect(selectedTab) {
        viewModel.loadPosts(tab = when (selectedTab) {
            0 -> "hot"
            1 -> "global"
            2 -> "local"
            3 -> "confessions"
            else -> null
        })
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val greeting = greetingForNow()
    val displayName = currentUser?.displayName ?: "Stranger"

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            IconButton(onClick = { onNavigate("games") }) {
                                Icon(Icons.Outlined.SportsEsports, "Games", tint = TextSecondary)
                            }
                            IconButton(onClick = { onNavigate("stories") }) {
                                Icon(Icons.Outlined.PhotoCamera, "Stories", tint = TextSecondary)
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

            // Trending hashtags bar
            if (trending.isNotEmpty()) {
                item {
                    TrendingHashtagsBar(
                        trending = trending,
                        onTagClick = { tag -> viewModel.loadPosts(tag = tag) }
                    )
                }
            }

            // Story bar (only when stories exist)
            if (stories.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(stories, key = { it.id }) { story ->
                            StoryBarItem(story) { viewModel.viewStory(story.id) }
                        }
                    }
                }
            }

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
                        onReply = { onPostClick(post.id) },
                        onEdit = { newContent -> viewModel.editPost(post.id, newContent) },
                        onDelete = { viewModel.deletePost(post.id) },
                        onReport = { reportTargetId = post.id }
                    )
                }
            }
        }

        // Report post dialog
        reportTargetId?.let { id ->
            ReportReasonDialog(
                onDismiss = { reportTargetId = null },
                onConfirm = { reason ->
                    viewModel.reportPost(id, reason)
                    reportTargetId = null
                }
            )
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
private fun StoryBarItem(story: Story, onClick: () -> Unit) {
    val authorName = story.author?.displayName?.takeIf { it.isNotBlank() }
        ?: story.author?.username?.takeIf { it.isNotBlank() } ?: "Ghost"
    val ringBrush = if (story.viewed) {
        Brush.linearGradient(listOf(TextTertiary, TextTertiary))
    } else {
        Brush.linearGradient(listOf(GradientStart, GradientEnd, PrimaryPink))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ringBrush)
                .padding(3.dp)
                .clip(CircleShape)
                .background(Background)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (story.mediaUrl != null) {
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = "Story",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                PersonaAvatar(authorName, size = 52)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            authorName,
            color = TextPrimary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 68.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onUpvote: () -> Unit,
    onReply: () -> Unit,
    onEdit: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onReport: () -> Unit = {}
) {
    val authorName = post.author?.displayName ?: post.author?.username ?: "Anonymous"
    val bg = postBackgroundById(if (post.bgType == "gradient") post.bgValue else null)
    val typeMeta = typeMetaFor(post.postType)
    val moodE = moodEmoji(post.mood)
    val isConfession = post.postType == "confession"
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reportMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { reportMenuOpen = true }
            )
    ) {
        Box(Modifier.fillMaxWidth()) {
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
                if (post.isMine) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.MoreVert, "More", tint = TextTertiary,
                                modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.background(CardBgAlt)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, null, tint = TextSecondary,
                                        modifier = Modifier.size(18.dp))
                                },
                                onClick = { menuOpen = false; showEdit = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = ErrorRed) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteOutline, null, tint = ErrorRed,
                                        modifier = Modifier.size(18.dp))
                                },
                                onClick = { menuOpen = false; showDeleteConfirm = true }
                            )
                        }
                    }
                }
            }

            // Type badge + mood chip row
            if (typeMeta != null || moodE != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (typeMeta != null) TypeBadge(typeMeta)
                    if (moodE != null) MoodChip(post.mood ?: "", moodE)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Content — gradient hero OR plain text
            if (bg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Text(
                    post.content,
                    color = if (isConfession) TextSecondary else TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontStyle = if (isConfession) androidx.compose.ui.text.font.FontStyle.Italic
                                else androidx.compose.ui.text.font.FontStyle.Normal
                )
            }

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
        } // end Column

            // Report menu (long-press)
            DropdownMenu(
                expanded = reportMenuOpen,
                onDismissRequest = { reportMenuOpen = false },
                modifier = Modifier.background(CardBgAlt)
            ) {
                DropdownMenuItem(
                    text = { Text("Report", color = ErrorRed) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Report, null, tint = ErrorRed,
                            modifier = Modifier.size(18.dp))
                    },
                    onClick = { reportMenuOpen = false; onReport() }
                )
            }
        } // end Box
    }

    // Edit dialog
    if (showEdit) {
        var editText by remember { mutableStateOf(post.content) }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            containerColor = CardBgAlt,
            title = { Text("Edit whisper", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Surface(color = CardBg, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(VioletBright),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editText.isNotBlank()) onEdit(editText.trim())
                        showEdit = false
                    },
                    enabled = editText.isNotBlank()
                ) { Text("Save", color = VioletBright, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardBgAlt,
            title = { Text("Delete whisper?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This can't be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ── Post type + mood visual identity ──

private data class TypeMeta(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private fun typeMetaFor(type: String?): TypeMeta? = when (type) {
    "question"   -> TypeMeta("QUESTION",   Icons.Outlined.HelpOutline,     VioletBright)
    "confession" -> TypeMeta("CONFESSION", Icons.Outlined.Lock,            PrimaryPink)
    "poll"       -> TypeMeta("POLL",       Icons.Outlined.BarChart,        AccentTeal)
    "nearby"     -> TypeMeta("NEARBY",     Icons.Outlined.LocationOn,      SuccessGreen)
    "voice"      -> TypeMeta("VOICE",      Icons.Outlined.Mic,             Color(0xFFFF9F4D))
    "photo"      -> TypeMeta("PHOTO",      Icons.Outlined.Image,           Color(0xFF4D8CFF))
    else         -> null   // "anonymous" = default, no badge
}

private fun moodEmoji(mood: String?): String? = when (mood) {
    "Happy"       -> "😊"
    "Lonely"      -> "😔"
    "Need Advice" -> "🤔"
    "Venting"     -> "😤"
    else          -> null
}

@Composable
private fun TypeBadge(meta: TypeMeta) {
    Surface(
        color = meta.color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(meta.icon, null, tint = meta.color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(meta.label, color = meta.color, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun MoodChip(mood: String, emoji: String) {
    Surface(
        color = ChipBg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50))
    ) {
        Text("$emoji $mood", color = TextSecondary, fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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

// ── Trending hashtags bar ──
@Composable
fun TrendingHashtagsBar(
    trending: List<TrendingTag>,
    onTagClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        item {
            Surface(
                color = CardBgAlt,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clip(RoundedCornerShape(50))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.TrendingUp, null, tint = PrimaryPink,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trending", color = TextSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        items(trending) { tag ->
            Surface(
                color = ChipBg,
                shape = RoundedCornerShape(50),
                onClick = { onTagClick(tag.tag) },
                modifier = Modifier.clip(RoundedCornerShape(50))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${tag.tag}", color = VioletBright, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium)
                    if (tag.count > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("${tag.count}", color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ── Report reason dialog ──
@Composable
fun ReportReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val reasons = listOf("Spam", "Harassment", "Inappropriate", "Other")
    var selectedReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBgAlt,
        title = { Text("Report this whisper?", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                reasons.forEach { reason ->
                    Surface(
                        color = if (selectedReason == reason) CardBg else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        onClick = { selectedReason = reason },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = VioletBright)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(reason, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (selectedReason.isNotBlank()) onConfirm(selectedReason.lowercase()) },
                enabled = selectedReason.isNotBlank()
            ) { Text("Report", color = ErrorRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
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
