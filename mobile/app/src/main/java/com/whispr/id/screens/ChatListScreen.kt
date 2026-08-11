package com.whispr.id.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.whispr.id.data.Chat
import com.whispr.id.ui.components.PersonaAvatar
import com.whispr.id.ui.components.WhisprBottomBar
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: WhisprViewModel,
    onChatClick: (String) -> Unit,
    onCreateChat: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val chats by viewModel.chats.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var query by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && !isRefreshing) {
                    pullOffset = (pullOffset + available.y * 0.3f).coerceAtMost(150f)
                    if (pullOffset > 100f) {
                        isRefreshing = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadChats()
            delay(800)
            isRefreshing = false
            pullOffset = 0f
        }
    }

    LaunchedEffect(Unit) { viewModel.loadChats() }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            WhisprBottomBar(current = "chats", onNavigate = onNavigate, onCreate = onCreateChat)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(nestedScrollConnection)
        ) {
        Column(Modifier.fillMaxSize()) {
            // Title
            Text(
                "Chats",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )

            // Search bar
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(46.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search chats"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val filtered = chats.filter {
                query.isBlank() || (it.user?.displayName ?: "").contains(query, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                if (loading && !isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VioletBright)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TextTertiary,
                                modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No conversations yet", color = TextSecondary,
                                fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Start a new chat and make someone's day!",
                                color = TextTertiary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatListItem(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                }
            }
        }

        // Pull-to-refresh indicator
        if (isRefreshing || pullOffset > 10f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(24.dp)
                    .alpha(if (isRefreshing) 1f else (pullOffset / 100f).coerceIn(0f, 1f)),
                color = VioletBright,
                strokeWidth = 2.dp
            )
        }
        } // end Box
    }
}

@Composable
private fun BasicSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(VioletBright),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = TextTertiary, fontSize = 14.sp)
            }
            inner()
        }
    )
}

@Composable
fun ChatListItem(chat: Chat, onClick: () -> Unit) {
    val name = chat.user?.displayName ?: chat.user?.username ?: "Anonymous"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonaAvatar(name, size = 52, online = chat.unreadCount > 0)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(relativeShort(chat.lastMessageAt), color = TextTertiary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                chat.lastMessage ?: "Say hi 👋",
                color = if (chat.unreadCount > 0) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (chat.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chat.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = PrimaryPurple, modifier = Modifier.size(20.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${chat.unreadCount}", color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun relativeShort(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val t = java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        val m = (System.currentTimeMillis() - t) / 60000
        when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            m < 1440 -> "${m / 60}h"
            else -> "${m / 1440}d"
        }
    } catch (e: Exception) { "" }
}
