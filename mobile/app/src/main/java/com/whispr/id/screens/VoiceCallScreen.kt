package com.whispr.id.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.whispr.id.ui.components.PersonaAvatar
import com.whispr.id.ui.theme.*
import com.whispr.id.util.CallManager
import com.whispr.id.viewmodel.WhisprViewModel

@Composable
fun VoiceCallScreen(
    viewModel: WhisprViewModel,
    onEndCall: () -> Unit,
    peerName: String = "Anonymous",
    peerId: String? = null,
    isIncoming: Boolean = false,
    routeCallId: String = ""
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    ) }

    val status by viewModel.callStatus.collectAsState()
    val incomingCall by viewModel.incomingCall.collectAsState()
    val callId: String = routeCallId.ifBlank {
        viewModel.activeCall.value?.id ?: incomingCall?.callId ?: ""
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted && callId.isNotBlank() && peerId != null) {
            runCatching {
                if (isIncoming) {
                    CallManager.answerCall(context, callId, peerId, null)
                    viewModel.answerCall()
                } else {
                    CallManager.startCall(context, callId, peerId)
                }
            }.onFailure { e ->
                viewModel.setCallStatus("idle")
                viewModel.setError(e.message ?: "Call failed to start")
                onEndCall()
            }
        }
    }

    // Start/answer the WebRTC call once we have an id + permission
    LaunchedEffect(callId, peerId, hasPermission, isIncoming) {
        if (callId.isNotBlank() && peerId != null && hasPermission && !CallManager.isInCall()) {
            runCatching {
                if (isIncoming) {
                    CallManager.answerCall(context, callId, peerId, null)
                    viewModel.answerCall()
                } else {
                    CallManager.startCall(context, callId, peerId)
                }
            }.onFailure { e ->
                viewModel.setCallStatus("idle")
                viewModel.setError(e.message ?: "Call failed to start")
                onEndCall()
            }
        }
    }

    // Request mic permission on first entry
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Mirror CallManager events into the ViewModel status
    LaunchedEffect(Unit) {
        CallManager.listener = object : CallManager.Listener {
            override fun onConnecting() { viewModel.setCallStatus("connecting") }
            override fun onRinging() { viewModel.setCallStatus("ringing") }
            override fun onConnected() { viewModel.setCallStatus("active") }
            override fun onRemoteIce() {}
            override fun onCallEnded() {
                viewModel.setCallStatus("idle")
                onEndCall()
            }
            override fun onError(msg: String) {
                viewModel.setCallStatus("idle")
                onEndCall()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (CallManager.isInCall()) {
                CallManager.endCall(context, notifyPeer = true)
            }
            CallManager.listener = null
        }
    }

    // Timer when active
    LaunchedEffect(status) {
        if (status == "active") {
            while (true) {
                kotlinx.coroutines.delay(1000)
                callDuration++
            }
        }
    }

    val isActive = status == "active"

    // Subtle pulsing ring behind the avatar.
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Background, VioletDeep.copy(alpha = 0.35f), Background)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            Text("Voice Call", color = TextSecondary, fontSize = 14.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(peerName, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    isActive -> formatDuration(callDuration)
                    status == "ringing" -> "Ringing..."
                    status == "connecting" -> "Connecting..."
                    isIncoming -> "Incoming call..."
                    else -> "Call ended"
                },
                color = if (isActive) VioletBright else TextTertiary,
                fontSize = 18.sp, fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(56.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(PrimaryPurple.copy(alpha = 0.12f))
                )
                PersonaAvatar(peerName, size = 132)
            }

            Spacer(Modifier.height(72.dp))

            // Control buttons
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                // Mute
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) ErrorRed else TextPrimary,
                    onClick = {
                        isMuted = !isMuted
                        CallManager.setMuted(isMuted)
                    }
                )
                // End / Decline
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = if (isIncoming && !isActive) "Decline" else "End",
                    tint = ErrorRed,
                    big = true,
                    onClick = {
                        viewModel.endCall()
                        CallManager.endCall(context, notifyPeer = true)
                        onEndCall()
                    }
                )
                // Speaker
                CallControlButton(
                    icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = if (isSpeaker) "Speaker" else "Earpiece",
                    tint = if (isSpeaker) AccentTeal else TextPrimary,
                    onClick = {
                        isSpeaker = !isSpeaker
                        CallManager.setSpeaker(isSpeaker)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Accept button for incoming calls
            if (isIncoming && !isActive) {
                CallControlButton(
                    icon = Icons.Default.Call,
                    label = "Accept",
                    tint = OnlineGreen,
                    big = true,
                    onClick = {
                        viewModel.answerCall()
                        if (callId.isNotBlank() && peerId != null && hasPermission) {
                            CallManager.answerCall(context, callId, peerId, null)
                        }
                    }
                )
            }

            if (!hasPermission) {
                Spacer(Modifier.height(16.dp))
                Text("Microphone permission needed", color = TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    big: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (big) 72.dp else 58.dp)
                .clip(CircleShape)
                .background(if (big) tint.copy(alpha = 0.18f) else Surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(if (big) 30.dp else 24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextTertiary, fontSize = 11.sp)
    }
}

fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
