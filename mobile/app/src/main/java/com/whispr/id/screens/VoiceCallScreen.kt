package com.whispr.id.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.id.ui.components.PersonaAvatar
import com.whispr.id.ui.theme.*
import com.whispr.id.viewmodel.WhisprViewModel

@Composable
fun VoiceCallScreen(
    viewModel: WhisprViewModel,
    onEndCall: () -> Unit,
    peerName: String = "Anonymous"
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var callDuration by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            callDuration++
        }
    }

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
            Text(formatDuration(callDuration), color = VioletBright, fontSize = 18.sp,
                fontWeight = FontWeight.Medium)

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

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CallControl(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    active = isMuted
                ) { isMuted = !isMuted }

                FloatingActionButton(
                    onClick = { viewModel.endCall(); onEndCall() },
                    containerColor = ErrorRed,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.CallEnd, "End", tint = Color.White,
                        modifier = Modifier.size(32.dp))
                }

                CallControl(
                    icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = "Speaker",
                    active = isSpeaker
                ) { isSpeaker = !isSpeaker }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = if (active) VioletBright else CardBg,
            shape = CircleShape,
            modifier = Modifier.size(60.dp)
        ) {
            Icon(icon, label, tint = if (active) Color.White else TextPrimary,
                modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
