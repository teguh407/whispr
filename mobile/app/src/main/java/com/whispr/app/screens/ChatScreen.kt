package com.whispr.app.screens

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.whispr.app.data.ChatMessage
import com.whispr.app.data.UploadResponse
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
    fun uploadPickedPhoto(uri: Uri, isOnceView: Boolean) {
        val file = uriToFile(context, uri, "jpg") ?: return
        viewModel.uploadPhoto(file, isOnceView) { resp ->
            if (resp != null) {
                val wsMsg = WsMessage(
                    type = "photo",
                    content = null,
                    mediaUrl = resp.url,
                    isOnceView = isOnceView
                )
                ws?.send(Gson().toJson(wsMsg))
                viewModel.addMessage(
                    ChatMessage(
                        senderId = currentUser?.id ?: "",
                        content = "",
                        type = "photo",
                        mediaUrl = resp.url,
                        isOnceView = isOnceView
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
                    if (msg.type in listOf("message", "photo", "document", "voice", "gif")) {
                        viewModel.addMessage(
                            ChatMessage(
                                senderId = msg.senderId ?: "",
                                content = msg.content ?: "",
                                type = msg.type,
                                mediaUrl = msg.mediaUrl,
                                isOnceView = msg.isOnceView ?: false,
                                filename = msg.filename,
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

    val peerName = viewModel.chats.collectAsState().value
        .firstOrNull { it.id == chatId }?.user?.let { it.displayName ?: it.username } ?: "Anonymous"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.whispr.app.ui.components.PersonaAvatar(peerName, size = 36, online = true)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = TextPrimary)
                            Text("Active now", fontSize = 11.sp, color = OnlineGreen)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCall) {
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
                    MessageBubble(msg, isMe) { messageId, ttl ->
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

    // Once-view prompt dialog after a photo is picked
    if (pendingPhotoUri != null) {
        AlertDialog(
            onDismissRequest = { pendingPhotoUri = null },
            title = { Text("Sekali liat?") },
            text = { Text("Foto hanya bisa dilihat sekali oleh penerima.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPhotoUri?.let { uploadPickedPhoto(it, true) }
                    pendingPhotoUri = null
                }) { Text("Ya") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPhotoUri?.let { uploadPickedPhoto(it, false) }
                    pendingPhotoUri = null
                }) { Text("Tidak") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(msg: ChatMessage, isMe: Boolean, onSetTtl: (String, Int) -> Unit) {
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
                        "voice" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, null,
                                    tint = if (isMe) Color.White else VioletBright,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Voice message",
                                    color = if (isMe) Color.White else TextPrimary, fontSize = 13.sp)
                            }
                        }
                        "photo" -> PhotoBubbleContent(msg, isMe)
                        "document" -> DocumentBubbleContent(msg, isMe)
                        "gif" -> Text("🎬 GIF", color = if (isMe) Color.White else TextPrimary)
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
private fun PhotoBubbleContent(msg: ChatMessage, isMe: Boolean) {
    val fullUrl = ApiClient.getBaseUrl() + (msg.mediaUrl ?: "")
    var revealed by remember(msg.id) { mutableStateOf(false) }
    var expired by remember(msg.id) { mutableStateOf(false) }

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
            revealed -> Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { revealed = false; expired = true }
            ) {
                AsyncImage(
                    model = fullUrl,
                    contentDescription = "Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tap to close", color = Color.White, fontSize = 12.sp)
                }
            }
            else -> Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBgAlt)
                    .clickable { revealed = true },
                contentAlignment = Alignment.Center
            ) {
                // Blurred preview behind the lock overlay
                AsyncImage(
                    model = fullUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(18.dp),
                    contentScale = ContentScale.Crop
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
        AsyncImage(
            model = fullUrl,
            contentDescription = "Photo",
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
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
