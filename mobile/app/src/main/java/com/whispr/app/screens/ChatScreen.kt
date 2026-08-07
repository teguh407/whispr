package com.whispr.app.screens

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.whispr.app.data.ChatMessage
import com.whispr.app.data.WsMessage
import com.whispr.app.network.ApiClient
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onGifPicker: () -> Unit,
    onCall: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var ws by remember { mutableStateOf<WebSocket?>(null) }

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
        // Connect WebSocket
        val token = com.whispr.app.network.TokenStore.getToken(context)
        val wsUrl = ApiClient.getWsUrl("/ws/chat/$token")
        val client = ApiClient.okHttpClient
        val request = Request.Builder().url(wsUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = Gson().fromJson(text, WsMessage::class.java)
                    if (msg.type == "message" && msg.content != null) {
                        viewModel.addMessage(
                            ChatMessage(
                                senderId = msg.senderId ?: "",
                                content = msg.content,
                                type = msg.type,
                                mediaUrl = msg.mediaUrl,
                                createdAt = msg.timestamp
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose { ws?.close(1000, "bye"); ws = null }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCall) {
                        Icon(Icons.Default.Phone, "Call", tint = SuccessGreen)
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    val isMe = msg.senderId == (currentUser?.id ?: "")
                    MessageBubble(msg, isMe)
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onGifPicker) {
                    Icon(Icons.Default.Gif, "GIF", tint = PrimaryPurple)
                }

                IconButton(onClick = {
                    if (isRecording) {
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            mediaRecorder = null
                            isRecording = false
                        } catch (_: Exception) { mediaRecorder = null; isRecording = false }
                    } else {
                        try {
                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.3gp")
                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                            mr.setOutputFile(file.absolutePath)
                            mr.prepare()
                            mr.start()
                            mediaRecorder = mr
                            isRecording = true
                        } catch (_: Exception) {}
                    }
                }) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        "Voice",
                        tint = if (isRecording) ErrorRed else PrimaryPurple
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Message...", color = TextSecondary) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = CardBg,
                        focusedContainerColor = CardBg,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        val wsMsg = WsMessage(type = "message", content = inputText)
                        ws?.send(Gson().toJson(wsMsg))
                        viewModel.addMessage(
                            ChatMessage(
                                senderId = currentUser?.id ?: "",
                                content = inputText,
                                type = "text"
                            )
                        )
                        inputText = ""
                    }
                }) {
                    Icon(Icons.Default.Send, "Send", tint = PrimaryPurple)
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage, isMe: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) PrimaryPurple else CardBg,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (msg.type) {
                    "voice" -> {
                        Icon(Icons.Default.Mic, null, tint = if (isMe) Color.White else PrimaryPurple)
                        Text("Voice message", color = if (isMe) Color.White else TextPrimary, fontSize = 13.sp)
                    }
                    "photo" -> {
                        Text("📷 Photo", color = if (isMe) Color.White else TextPrimary)
                    }
                    "gif" -> {
                        Text("🎬 GIF", color = if (isMe) Color.White else TextPrimary)
                    }
                    else -> {
                        Text(msg.content, color = if (isMe) Color.White else TextPrimary, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
