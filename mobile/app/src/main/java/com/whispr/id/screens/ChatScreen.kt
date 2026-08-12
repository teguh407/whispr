package com.whispr.id.screens

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import androidx.core.content.ContextCompat
import com.whispr.id.data.ChatMessage
import com.whispr.id.data.UploadResponse
import com.whispr.id.data.WsMessage
import com.whispr.id.network.ApiClient
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel
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
    onCall: (String) -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var ws by remember { mutableStateOf<WebSocket?>(null) }
    var wsConnected by remember { mutableStateOf(false) }
    val reconnectScope = rememberCoroutineScope()
    var shouldReconnect by remember { mutableStateOf(true) }

    // Anti-screenshot: FLAG_SECURE on the hosting Activity window
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        activity?.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Attachment state
    var showAttachMenu by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // ── Upload helpers ────────────────────────────────────────────────
    fun uploadPickedPhoto(uri: Uri, isOnceView: Boolean, destructSeconds: Int? = null, caption: String = "") {
        val file = uriToFile(context, uri, "jpg") ?: return
        viewModel.uploadPhoto(file, isOnceView) { resp ->
            if (resp != null) {
                val wsMsg = WsMessage(
                    type = "photo",
                    chatId = chatId,
                    content = caption.ifBlank { null },
                    mediaUrl = resp.url,
                    isOnceView = isOnceView,
                    destructSeconds = destructSeconds
                )
                ws?.send(Gson().toJson(wsMsg))
                viewModel.addMessage(
                    ChatMessage(
                        senderId = currentUser?.id ?: "",
                        content = caption,
                        type = "photo",
                        mediaUrl = resp.url,
                        isOnceView = isOnceView,
                        ttlSeconds = destructSeconds
                    )
                )
            }
        }
    }

    fun uploadPickedDocument(uri: Uri) {
        val file = uriToFile(context, uri, "bin") ?: return
        viewModel.uploadDocument(file) { resp ->
            if (resp != null) {
                val wsMsg = WsMessage(
                    type = "document",
                    chatId = chatId,
                    content = null,
                    mediaUrl = resp.url,
                    filename = resp.filename
                )
                ws?.send(Gson().toJson(wsMsg))
                viewModel.addMessage(
                    ChatMessage(
                        senderId = currentUser?.id ?: "",
                        content = "",
                        type = "document",
                        mediaUrl = resp.url,
                        filename = resp.filename,
                        fileSize = resp.size
                    )
                )
            }
        }
    }

    // ── Pickers ───────────────────────────────────────────────────────
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) pendingPhotoUri = uri
    }
    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) uploadPickedDocument(uri)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setError("Microphone ready — tap mic to record")
        }
    }

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
        // Connect WebSocket with auto-reconnect
        val token = com.whispr.id.network.TokenStore.getToken(context)
        val wsUrl = ApiClient.getWsUrl("/ws/chat/$token")
        val client = ApiClient.okHttpClient

        fun connect() {
            val request = Request.Builder().url(wsUrl).build()
            ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    wsConnected = true
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = Gson().fromJson(text, WsMessage::class.java)
                        if (msg.type in listOf("message", "photo", "document", "voice", "gif")) {
                            viewModel.addMessage(
                                ChatMessage(
                                    id = msg.id,
                                    senderId = msg.senderId ?: "",
                                    content = msg.content ?: "",
                                    type = msg.type,
                                    mediaUrl = msg.mediaUrl,
                                    isOnceView = msg.isOnceView ?: false,
                                    filename = msg.filename,
                                    createdAt = msg.timestamp
                                )
                            )
                        } else if (msg.type == "incoming_call") {
                            // Server pushed an incoming call on the chat channel
                            val call = Gson().fromJson(text, com.whispr.id.data.IncomingCall::class.java)
                            if (call.callId.isNotBlank()) viewModel.setIncomingCall(call)
                        } else if (msg.type == "call_ended" || msg.type == "call_answered") {
                            viewModel.setCallStatus(if (msg.type == "call_answered") "active" else "idle")
                        }
                    } catch (_: Exception) {}
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    wsConnected = false
                    // Auto-reconnect after 3s (unless screen was disposed)
                    reconnectScope.launch {
                        kotlinx.coroutines.delay(3000)
                        if (shouldReconnect) connect()
                    }
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    wsConnected = false
                }
            })
        }
        connect()
    }

    // Pick up any GIF staged by GifPickerScreen and send it via WS
    LaunchedEffect(chatId) {
        viewModel.pendingGifUrl.collect { gifUrl ->
            if (gifUrl != null) {
                val url = viewModel.consumePendingGif()
                if (url != null) {
                    val wsMsg = WsMessage(type = "gif", chatId = chatId, mediaUrl = url)
                    ws?.send(Gson().toJson(wsMsg))
                    viewModel.addMessage(
                        ChatMessage(
                            senderId = currentUser?.id ?: "",
                            content = "",
                            type = "gif",
                            mediaUrl = url
                        )
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            shouldReconnect = false
            ws?.close(1000, "bye"); ws = null
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val peerName = viewModel.chats.collectAsState().value
        .firstOrNull { it.id == chatId }?.user?.let { it.displayName ?: it.username } ?: "Anonymous"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.whispr.id.ui.components.PersonaAvatar(peerName, size = 36, online = true)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = TextPrimary)
                            Text(
                                if (wsConnected) "Online" else "Connecting...",
                                fontSize = 11.sp,
                                color = if (wsConnected) OnlineGreen else TextTertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Find peer id from chat list to start a call
                        val peer = viewModel.chats.value
                            .firstOrNull { it.id == chatId }?.user
                        if (peer?.id != null && peer.id.isNotBlank()) onCall(peer.id)
                    }) {
                        Icon(Icons.Default.Call, "Call", tint = VioletBright)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = VioletBright
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
                    MessageBubble(msg, isMe, onMarkViewed = { msgId ->
                        viewModel.markMessageViewed(msgId)
                    }) { messageId, ttl ->
                        viewModel.setMessageTtl(messageId, ttl)
                    }
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

                // Attachment (+) button with Photo / Document menu
                Box {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(Icons.Default.Add, "Attach", tint = PrimaryPurple)
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PhotoLibrary, null,
                                    tint = VioletBright, modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showAttachMenu = false
                                photoPicker.launch("image/*")
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Document") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.InsertDriveFile, null,
                                    tint = AccentTeal, modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showAttachMenu = false
                                docPicker.launch("*/*")
                            }
                        )
                    }
                }

                IconButton(onClick = {
                    if (isRecording) {
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            mediaRecorder = null
                            isRecording = false
                            // Upload recorded voice note and send via WebSocket
                            recordedFile?.let { vfile ->
                                if (vfile.exists() && vfile.length() > 0) {
                                    viewModel.uploadVoice(vfile) { resp ->
                                        if (resp != null) {
                                            val wsMsg = WsMessage(
                                                type = "voice",
                                                chatId = chatId,
                                                mediaUrl = resp.url
                                            )
                                            ws?.send(Gson().toJson(wsMsg))
                                            viewModel.addMessage(
                                                ChatMessage(
                                                    senderId = currentUser?.id ?: "",
                                                    type = "voice",
                                                    mediaUrl = resp.url
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) { mediaRecorder = null; isRecording = false }
                    } else {
                        // Request mic permission first (MediaRecorder silently fails without it)
                        val micGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!micGranted) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@IconButton
                        }
                        try {
                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            mr.setAudioEncodingBitRate(128000)
                            mr.setAudioSamplingRate(44100)
                            mr.setOutputFile(file.absolutePath)
                            mr.prepare()
                            mr.start()
                            mediaRecorder = mr
                            recordedFile = file
                            isRecording = true
                        } catch (e: Exception) {
                            viewModel.setError("Mic error: ${e.message}")
                        }
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
                        val wsMsg = WsMessage(type = "message", chatId = chatId, content = inputText)
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

    // Telegram-style photo send preview dialog (shown after a photo is picked)
    pendingPhotoUri?.let { uri ->
        TelegramPhotoSendDialog(
            uri = uri,
            onDismiss = { pendingPhotoUri = null },
            onSend = { isOnceView, destructSeconds, caption ->
                uploadPickedPhoto(uri, isOnceView, destructSeconds, caption)
                pendingPhotoUri = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    isMe: Boolean,
    onMarkViewed: (String) -> Unit = {},
    onSetTtl: (String, Int) -> Unit
) {
    var showTtlMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isMe) 18.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 18.dp
                ),
                color = if (isMe) PrimaryPurple else CardBg,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        enabled = isMe && msg.id != null,
                        onLongClick = { showTtlMenu = true },
                        onClick = { }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    when (msg.type) {
                        "voice" -> VoiceBubbleContent(msg, isMe)
                        "photo" -> PhotoBubbleContent(msg, isMe, onMarkViewed)
                        "document" -> DocumentBubbleContent(msg, isMe)
                        "gif" -> GifBubbleContent(msg, isMe)
                        else -> Text(msg.content,
                            color = if (isMe) Color.White else TextPrimary, lineHeight = 20.sp,
                            fontSize = 15.sp)
                    }
                    if (msg.ttlSeconds != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null,
                                tint = if (isMe) Color.White else AccentTeal,
                                modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Auto-destruct ${msg.ttlSeconds}s",
                                color = if (isMe) Color.White else AccentTeal,
                                fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            // Auto-destruct timer selector popup
            DropdownMenu(
                expanded = showTtlMenu,
                onDismissRequest = { showTtlMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Auto-destruct in 5s") },
                    onClick = { msg.id?.let { onSetTtl(it, 5) }; showTtlMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Auto-destruct in 10s") },
                    onClick = { msg.id?.let { onSetTtl(it, 10) }; showTtlMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Auto-destruct in 30s") },
                    onClick = { msg.id?.let { onSetTtl(it, 30) }; showTtlMenu = false }
                )
            }
        }
        // Timestamp + small timer indicator for TTL messages
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(formatBubbleTime(msg.createdAt), color = TextTertiary, fontSize = 10.sp)
            if (msg.ttlSeconds != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Timer, null, tint = AccentTeal, modifier = Modifier.size(11.dp))
                Text("${msg.ttlSeconds}s", color = AccentTeal, fontSize = 10.sp)
            }
        }
    }
}

/**
 * Photo bubble: renders the image via Coil. Once-view photos show a
 * blurred/locked overlay ("Tap to view once"); after viewing they collapse
 * into an "Photo expired" placeholder.
 */
@Composable
private fun PhotoBubbleContent(
    msg: ChatMessage,
    isMe: Boolean,
    onMarkViewed: (String) -> Unit = {}
) {
    val fullUrl = ApiClient.buildMediaUrl(msg.mediaUrl)
    var showFullScreen by remember { mutableStateOf(false) }
    // Server-side once-view: is_viewed persists across chat re-entries
    val expired = msg.isOnceView && msg.isViewed

    // Full-screen photo viewer (once-view = secure, no screenshots)
    if (showFullScreen) {
        FullScreenPhotoViewer(
            url = fullUrl,
            secure = msg.isOnceView,
            onDismiss = {
                showFullScreen = false
                if (msg.isOnceView && msg.id != null) {
                    onMarkViewed(msg.id!!)
                }
            }
        )
    }

    if (msg.isOnceView) {
        when {
            expired -> Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBgAlt),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HideImage, "Expired",
                        tint = TextTertiary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Photo expired", color = TextTertiary, fontSize = 13.sp)
                }
            }
            else -> Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBgAlt)
                    .clickable { showFullScreen = true },
                contentAlignment = Alignment.Center
            ) {
                // Blurred preview behind the lock overlay
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(fullUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(18.dp),
                    contentScale = ContentScale.Crop,
                    loading = { Box(Modifier.fillMaxSize().background(CardBgAlt)) },
                    error = { Box(Modifier.fillMaxSize().background(CardBgAlt)) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, "Locked",
                            tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to view once",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    } else {
        // Regular photo — tap to view full screen
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(fullUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Photo",
            modifier = Modifier
                .size(width = 260.dp, height = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { showFullScreen = true },
            contentScale = ContentScale.Crop,
            loading = {
                Box(Modifier.fillMaxSize().background(CardBgAlt), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TextTertiary, modifier = Modifier.size(24.dp))
                }
            },
            error = {
                Box(Modifier.fillMaxSize().background(CardBgAlt), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BrokenImage, "Error", tint = TextTertiary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to retry", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }
        )
        // Caption below photo if present
        if (!msg.content.isNullOrBlank()) {
            Text(
                msg.content,
                color = if (isMe) Color.White.copy(alpha = 0.9f) else TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

/** GIF bubble — renders the GIF image with rounded corners, tap to view full screen */
@Composable
private fun GifBubbleContent(msg: ChatMessage, isMe: Boolean) {
    var showFullScreen by remember { mutableStateOf(false) }
    if (showFullScreen) {
        FullScreenPhotoViewer(
            url = msg.mediaUrl ?: "",
            onDismiss = { showFullScreen = false }
        )
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(msg.mediaUrl)
            .crossfade(true)
            .build(),
        contentDescription = "GIF",
        modifier = Modifier
            .widthIn(max = 240.dp)
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showFullScreen = true },
        contentScale = ContentScale.Fit,
        loading = {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryPurple, modifier = Modifier.size(24.dp))
            }
        },
        error = {
            Box(
                Modifier
                    .size(width = 200.dp, height = 120.dp)
                    .background(CardBgAlt)
                    .clickable { showFullScreen = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BrokenImage, "Error", tint = TextTertiary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to open", color = TextTertiary, fontSize = 11.sp)
                }
            }
        }
    )
}

/** Full-screen photo viewer — black background, tap to dismiss */
@Composable
private fun FullScreenPhotoViewer(
    url: String,
    onDismiss: () -> Unit,
    secure: Boolean = false
) {
    // FLAG_SECURE blocks screenshots & screen recording.
    // Unwrap ContextWrapper chain so we always reach the hosting Activity window.
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    if (secure) {
        LaunchedEffect(Unit) {
            activity?.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        DisposableEffect(Unit) {
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "Full photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                },
                error = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BrokenImage, "Error", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Failed to load", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                }
            )
            // Close button at top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Walk the ContextWrapper chain to find the hosting Activity (FLAG_SECURE needs its window). */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Telegram-style photo send preview dialog.
 * Shows the selected photo full-screen with timer options + caption before sending.
 */
@Composable
private fun TelegramPhotoSendDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onSend: (isOnceView: Boolean, destructSeconds: Int?, caption: String) -> Unit
) {
    data class TimerOption(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val isOnceView: Boolean, val destructSeconds: Int?)

    val timerOptions = listOf(
        TimerOption("View Once", Icons.Default.Visibility, true, null),
        TimerOption("3s", Icons.Default.Timer, false, 3),
        TimerOption("10s", Icons.Default.Timer, false, 10),
        TimerOption("30s", Icons.Default.Timer, false, 30),
        TimerOption("Keep", Icons.Default.AllInclusive, false, null)
    )
    var selectedTimer by remember { mutableStateOf(4) } // default "Keep"
    var caption by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Photo preview
            AsyncImage(
                model = uri,
                contentDescription = "Selected photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Top bar: close button + timer selector
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        "Send Photo",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
                // Timer options row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timerOptions.forEachIndexed { index, option ->
                        val isSelected = selectedTimer == index
                        Surface(
                            color = if (isSelected) PrimaryPurple else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50),
                            onClick = { selectedTimer = index },
                            modifier = Modifier.clip(RoundedCornerShape(50))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    option.icon,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    option.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Bottom bar: caption + send button
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Add a caption...", color = Color.White.copy(alpha = 0.5f)) },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = PrimaryPurple
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        val opt = timerOptions[selectedTimer]
                        onSend(opt.isOnceView, opt.destructSeconds, caption)
                    },
                    containerColor = PrimaryPurple,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Send, "Send", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Voice message bubble: play/pause button + waveform-style bars + duration. */
@Composable
private fun VoiceBubbleContent(msg: ChatMessage, isMe: Boolean) {
    val context = LocalContext.current
    val fullUrl = ApiClient.buildMediaUrl(msg.mediaUrl)
    var mediaPlayer by remember(msg.id) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember(msg.id) { mutableStateOf(false) }
    var duration by remember(msg.id) { mutableStateOf(0) }
    var position by remember(msg.id) { mutableStateOf(0) }

    // Cleanup MediaPlayer when bubble leaves composition
    DisposableEffect(msg.id) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(200.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    try {
                        if (mediaPlayer == null) {
                            val mp = android.media.MediaPlayer().apply {
                                setDataSource(fullUrl)
                                setOnPreparedListener {
                                    duration = it.duration / 1000
                                    it.start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    position = 0
                                }
                                prepareAsync()
                            }
                            mediaPlayer = mp
                        } else {
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                    } catch (_: Exception) { isPlaying = false }
                }
            }
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play/Pause",
                tint = if (isMe) Color.White else PrimaryPurple,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        // Fake waveform bars (random-ish heights based on message id hash)
        val bars = remember(msg.id) {
            val seed = msg.id?.hashCode() ?: 0
            (0 until 28).map { i ->
                val h = (Math.sin((i + seed).toDouble()) * 0.5 + 0.5) * 0.7 + 0.3
                h.toFloat()
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bars.forEachIndexed { i, h ->
                Box(
                    Modifier
                        .size(width = 3.dp, height = (h * 28).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isPlaying && i < (position.toFloat() / maxOf(duration, 1) * bars.size).toInt())
                                if (isMe) Color.White else PrimaryPurple
                            else
                                if (isMe) Color.White.copy(alpha = 0.4f) else TextTertiary
                        )
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (duration > 0) "${duration / 60}:${String.format("%02d", duration % 60)}" else "0:00",
            color = if (isMe) Color.White else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }

    // Update position while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    position = mp.currentPosition / 1000
                }
            }
            kotlinx.coroutines.delay(200)
        }
    }
}

/** Document bubble: file icon + filename + human-readable file size. */
@Composable
private fun DocumentBubbleContent(msg: ChatMessage, isMe: Boolean) {
    val textColor = if (isMe) Color.White else TextPrimary
    val subColor = if (isMe) Color.White.copy(alpha = 0.8f) else TextSecondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isMe) Color.White.copy(alpha = 0.18f) else CardBgAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InsertDriveFile, "File",
                tint = textColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.widthIn(max = 180.dp)) {
            Text(
                msg.filename ?: "Document",
                color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(formatFileSize(msg.fileSize), color = subColor, fontSize = 12.sp)
        }
    }
}

private fun formatBubbleTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val date = java.util.Date(
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        )
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (e: Exception) { "" }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/** Copies a content Uri into a cache temp File, preserving the display name when available. */
private fun uriToFile(context: Context, uri: Uri, fallbackExt: String): File? {
    return try {
        val displayName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
            }
        } catch (_: Exception) { null } ?: "upload_${System.currentTimeMillis()}.$fallbackExt"

        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.cacheDir, displayName)
        file.outputStream().use { out -> inputStream.copyTo(out) }
        inputStream.close()
        file
    } catch (_: Exception) { null }
}
