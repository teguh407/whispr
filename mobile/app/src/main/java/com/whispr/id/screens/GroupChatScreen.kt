package com.whispr.id.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.whispr.id.data.GroupMessage
import com.whispr.id.network.ApiClient
import com.whispr.id.network.TokenStore
import com.whispr.id.ui.components.PersonaAvatar
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.groupMessages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val error by viewModel.error.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var ws by remember { mutableStateOf<WebSocket?>(null) }

    LaunchedEffect(groupId) {
        viewModel.loadGroupMessages(groupId)
        // Connect WebSocket for group chat
        val token = TokenStore.getToken(context)
        if (token != null) {
            val wsUrl = ApiClient.getWsUrl("/ws/group/$groupId/$token")
            val client = ApiClient.okHttpClient
            val request = Request.Builder().url(wsUrl).build()
            ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = Gson().fromJson(text, Map::class.java)
                        val senderId = msg["sender_id"] as? String ?: ""
                        val content = msg["content"] as? String ?: ""
                        val senderName = msg["sender_name"] as? String ?: ""
                        val createdAt = msg["created_at"] as? String
                        if (senderId != currentUser?.id) {
                            viewModel.addGroupMessage(
                                GroupMessage(
                                    senderId = senderId,
                                    senderName = senderName,
                                    content = content,
                                    createdAt = createdAt
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose { ws?.close(1000, "bye"); ws = null }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group chat", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (loading && messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            CircularProgressIndicator(color = VioletBright)
                        }
                    }
                } else if (messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Text("No messages yet", color = TextTertiary, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(messages, key = { it.id ?: it.createdAt ?: it.content }) { msg ->
                        GroupMessageBubble(msg, isMine = msg.senderId == currentUser?.id)
                    }
                }
            }

            // Input row (display-only: no send endpoint yet)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message…", color = TextTertiary) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = VioletBright
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary, fontSize = 15.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotBlank()) {
                            val json = Gson().toJson(mapOf("content" to text, "type" to "text"))
                            ws?.send(json)
                            viewModel.addGroupMessage(
                                GroupMessage(
                                    senderId = currentUser?.id ?: "",
                                    senderName = currentUser?.displayName ?: currentUser?.username ?: "You",
                                    content = text,
                                    createdAt = java.time.OffsetDateTime.now().toString()
                                )
                            )
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) PrimaryPurple else ChipBg)
                ) {
                    Icon(
                        Icons.Filled.Send, "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(msg: GroupMessage, isMine: Boolean) {
    val senderName = msg.senderName.ifBlank { "Ghost" }
    val time = msg.createdAt?.let { fmtTime(it) } ?: ""
    val bg = if (isMine) PrimaryPurple else CardBg
    val align = if (isMine) Alignment.End else Alignment.Start

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        if (!isMine) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(senderName, size = 24)
                Spacer(Modifier.width(6.dp))
                Text(senderName, color = VioletBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(2.dp))
        }
        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    msg.content,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                if (time.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        time,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun fmtTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(iso)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date ?: return "")
    } catch (e: Exception) { "" }
}
