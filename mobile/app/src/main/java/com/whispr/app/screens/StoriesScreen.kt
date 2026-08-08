package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.whispr.app.data.Story
import com.whispr.app.ui.components.PersonaAvatar
import com.whispr.app.ui.components.WhisprBottomBar
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesScreen(
    viewModel: WhisprViewModel,
    onNavigate: (String) -> Unit = {},
    onCreate: () -> Unit = {}
) {
    val stories by viewModel.stories.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { viewModel.loadStories() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WhisprBottomBar(current = "stories", onNavigate = onNavigate, onCreate = onCreate)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Header ──
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Stories",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        " ephemeral moments from ghosts nearby",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // ── Stories tray (horizontal) ──
            item {
                if (loading && stories.isEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(6) {
                            StoryTraySkeleton()
                        }
                    }
                } else if (stories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(stories, key = { _, s -> s.id }) { index, story ->
                            StoryTrayItem(story = story) {
                                viewerIndex = index
                                viewModel.viewStory(story.id)
                            }
                        }
                    }
                }
            }

            // ── Section divider ──
            if (stories.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "All stories",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${stories.size} total",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ── Story cards ──
            if (loading && stories.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VioletBright)
                    }
                }
            } else if (stories.isEmpty()) {
                item { EmptyStories() }
            } else {
                items(stories, key = { it.id }) { story ->
                    StoryCard(
                        story = story,
                        onClick = {
                            val idx = stories.indexOfFirst { it.id == story.id }
                            if (idx >= 0) {
                                viewerIndex = idx
                                viewModel.viewStory(story.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Fullscreen story viewer ──
    viewerIndex?.let { idx ->
        if (idx in stories.indices) {
            StoryViewer(
                stories = stories,
                initialIndex = idx,
                onClose = { viewerIndex = null },
                onNavigateNext = { id -> viewModel.viewStory(id) }
            )
        } else {
            viewerIndex = null
        }
    }
}

// ── Stories tray item (circular avatar with ring) ──

@Composable
private fun StoryTrayItem(story: Story, onClick: () -> Unit) {
    val authorName = story.author?.displayName?.takeIf { it.isNotBlank() }
        ?: story.author?.username?.takeIf { it.isNotBlank() } ?: "Ghost"
    val ringBrush = if (story.viewed) {
        Brush.linearGradient(listOf(TextTertiary, TextTertiary))
    } else {
        Brush.linearGradient(listOf(GradientStart, GradientEnd, PrimaryPink))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
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
                PersonaAvatar(authorName, size = 60)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            authorName,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun StoryTraySkeleton() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ChipBg)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ChipBg)
        )
    }
}

// ── Story card (list item) ──

@Composable
private fun StoryCard(story: Story, onClick: () -> Unit) {
    val authorName = story.author?.displayName?.takeIf { it.isNotBlank() }
        ?: story.author?.username?.takeIf { it.isNotBlank() } ?: "Anonymous"

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column {
            // Media preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (story.mediaUrl != null) {
                    AsyncImage(
                        model = story.mediaUrl,
                        contentDescription = "Story media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Gradient placeholder when no media
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Text story",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Type badge overlay
                if (story.mediaType == "video") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("Video", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Viewed overlay
                if (story.viewed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text("Viewed", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Info section
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonaAvatar(authorName, size = 36)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            authorName,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        story.createdAt?.let {
                            Text(
                                relativeTime(it),
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    // View count
                    if (story.viewCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${story.viewCount}",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Caption
                story.caption?.takeIf { it.isNotBlank() }?.let { cap ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        cap,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                // Expiry
                story.expiresAt?.let {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Expires ${relativeTime(it)}",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Fullscreen story viewer ──

@Composable
private fun StoryViewer(
    stories: List<Story>,
    initialIndex: Int,
    onClose: () -> Unit,
    onNavigateNext: (String) -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, stories.lastIndex)) }
    val story = stories.getOrNull(currentIndex)
    if (story == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val authorName = story.author?.displayName?.takeIf { it.isNotBlank() }
        ?: story.author?.username?.takeIf { it.isNotBlank() } ?: "Ghost"

    // Auto-advance progress
    var progress by remember(currentIndex) { mutableStateOf(0f) }
    LaunchedEffect(currentIndex) {
        progress = 0f
        val durationMs = if (story.mediaType == "video") 8000L else 5000L
        val stepMs = 50L
        val step = stepMs.toFloat() / durationMs
        while (progress < 1f) {
            progress = (progress + step).coerceAtMost(1f)
            kotlinx.coroutines.delay(stepMs)
        }
        // Advance to next or close
        if (currentIndex < stories.lastIndex) {
            currentIndex++
            onNavigateNext(stories[currentIndex].id)
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Media
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (story.mediaUrl != null) {
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = "Story",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    story.caption?.let {
                        Text(
                            it,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
            }
        }

        // Tap zones: left = previous, right = next
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (currentIndex > 0) {
                            currentIndex--
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (currentIndex < stories.lastIndex) {
                            currentIndex++
                            onNavigateNext(stories[currentIndex].id)
                        } else {
                            onClose()
                        }
                    }
            )
        }

        // Progress bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { i, _ ->
                val segmentProgress = when {
                    i < currentIndex -> 1f
                    i == currentIndex -> progress
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = segmentProgress,
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // Header: author + close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonaAvatar(authorName, size = 36)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    authorName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                story.createdAt?.let {
                    Text(
                        relativeTime(it),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Caption overlay
        story.caption?.takeIf { it.isNotBlank() && story.mediaUrl != null }?.let { cap ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
                    .padding(20.dp)
            ) {
                Text(
                    cap,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // View count badge
        if (story.viewCount > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${story.viewCount}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Empty state ──

@Composable
private fun EmptyStories() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.PhotoLibrary, null, tint = TextTertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No stories yet", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Stories from nearby ghosts will appear here.", color = TextTertiary, fontSize = 13.sp)
    }
}

// ── helpers ──

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
