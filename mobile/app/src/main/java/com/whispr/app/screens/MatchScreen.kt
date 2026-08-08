package com.whispr.app.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.data.MatchJoinResponse
import com.whispr.app.data.MatchStatus
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    val matchJoin by viewModel.matchJoin.collectAsState()
    val matchStatus by viewModel.matchStatus.collectAsState()

    // Local UI state
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var screenState by remember { mutableStateOf("mode_select") } // mode_select|searching|playing|reveal
    var answer by remember { mutableStateOf("") }
    var matchId by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    // Poll match status when we have a matchId
    LaunchedEffect(matchId) {
        val id = matchId
        if (id != null) {
            while (matchId != null && screenState in listOf("searching", "playing", "reveal")) {
                viewModel.matchStatus(id)
                delay(2000) // poll every 2s
            }
        }
    }

    // React to match join result
    LaunchedEffect(matchJoin) {
        matchJoin?.let { mj ->
            if (mj.status == "matched" && mj.matchId != null) {
                matchId = mj.matchId
                screenState = "playing"
            }
        }
    }

    // React to match status changes
    LaunchedEffect(matchStatus) {
        matchStatus?.let { ms ->
            when (ms.phase) {
                "reveal" -> if (screenState != "reveal") screenState = "reveal"
                "done" -> { /* match ended, stay on reveal */ }
            }
        }
    }

    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose {
            matchId?.let { viewModel.matchLeave(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Match with Stranger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        matchId?.let { viewModel.matchLeave(it) }
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (screenState) {
                "mode_select" -> ModeSelectStep(
                    selectedMode = selectedMode,
                    onModeSelect = { selectedMode = it },
                    onFindMatch = {
                        viewModel.matchJoin(selectedMode)
                        screenState = "searching"
                    }
                )
                "searching" -> SearchingStep(
                    onCancel = {
                        matchId?.let { viewModel.matchLeave(it) }
                        screenState = "mode_select"
                        matchId = null
                    }
                )
                "playing" -> PlayingStep(
                    matchJoin = matchJoin,
                    matchStatus = matchStatus,
                    answer = answer,
                    onAnswerChange = { answer = it },
                    submitted = submitted,
                    onSubmit = {
                        matchId?.let { mid ->
                            viewModel.matchAnswer(mid, answer) {
                                submitted = true
                            }
                        }
                    }
                )
                "reveal" -> RevealStep(
                    matchStatus = matchStatus,
                    matchId = matchId,
                    onReact = { emoji ->
                        matchId?.let { viewModel.matchReact(it, emoji) }
                    },
                    onNextRound = {
                        matchId?.let { viewModel.matchLeave(it) }
                        matchId = null
                        answer = ""
                        submitted = false
                        screenState = "mode_select"
                        viewModel.matchJoin(selectedMode)
                        screenState = "searching"
                    },
                    onLeave = {
                        matchId?.let { viewModel.matchLeave(it) }
                        matchId = null
                        answer = ""
                        submitted = false
                        screenState = "mode_select"
                    }
                )
            }
        }
    }
}

@Composable
private fun ModeSelectStep(
    selectedMode: String?,
    onModeSelect: (String?) -> Unit,
    onFindMatch: () -> Unit
) {
    val modes = listOf(
        Triple("never_have_i_ever", "Never Have I Ever", "🙈"),
        Triple("three_words", "3 Words", "✏️"),
        Triple("would_you_rather", "Would You Rather", "🤔")
    )

    Text(
        "Play with a random stranger",
        color = TextSecondary,
        fontSize = 15.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Get matched, answer the same prompt,\nthen react to each other!",
        color = TextTertiary,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(28.dp))

    Text("Choose a mode (optional)", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(12.dp))

    // Random option
    ModeCard(
        title = "Random",
        emoji = "🎲",
        subtitle = "Surprise me",
        selected = selectedMode == null,
        onClick = { onModeSelect(null) }
    )
    Spacer(Modifier.height(10.dp))

    modes.forEach { (key, title, emoji) ->
        ModeCard(
            title = title,
            emoji = emoji,
            subtitle = "Anonymous 1-on-1",
            selected = selectedMode == key,
            onClick = { onModeSelect(key) }
        )
        Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onFindMatch,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
    ) {
        Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Find a Stranger", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeCard(title: String, emoji: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PrimaryPurple.copy(alpha = 0.15f) else CardBg,
        border = androidx.compose.foundation.BorderStroke(
            2.dp, if (selected) PrimaryPurple else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextTertiary, fontSize = 12.sp)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = PrimaryPurple, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SearchingStep(onCancel: () -> Unit) {
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val rotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "rotate"
    )

    Spacer(Modifier.height(80.dp))

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            Modifier
                .size(120.dp)
                .alpha(pulse)
                .clip(CircleShape)
                .background(PrimaryPurple.copy(alpha = 0.2f))
        )
        // Middle ring
        Box(
            Modifier
                .size(80.dp)
                .alpha(pulse * 0.8f)
                .clip(CircleShape)
                .background(PrimaryPurple.copy(alpha = 0.3f))
        )
        // Center icon
        Icon(
            Icons.Default.Search,
            null,
            tint = PrimaryPurple,
            modifier = Modifier.size(36.dp)
        )
    }

    Spacer(Modifier.height(32.dp))
    Text("Finding a stranger...", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text("We're matching you with someone anonymous", color = TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center)

    Spacer(Modifier.height(40.dp))
    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
    ) {
        Text("Cancel")
    }
}

@Composable
private fun PlayingStep(
    matchJoin: MatchJoinResponse?,
    matchStatus: MatchStatus?,
    answer: String,
    onAnswerChange: (String) -> Unit,
    submitted: Boolean,
    onSubmit: () -> Unit
) {
    val prompt = matchJoin?.prompt ?: matchStatus?.let { "" } ?: ""
    val timeLeft = matchStatus?.timeLeft ?: 30
    val opponentId = matchJoin?.opponentId ?: "Stranger"

    // Timer bar
    val timerProgress = timeLeft.toFloat() / 30f

    Spacer(Modifier.height(8.dp))

    // Opponent + timer
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("VS $opponentId", color = VioletBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            "${timeLeft}s",
            color = if (timeLeft <= 10) ErrorRed else AccentTeal,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = timerProgress,
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = if (timeLeft <= 10) ErrorRed else AccentTeal,
        trackColor = ChipBg
    )

    Spacer(Modifier.height(24.dp))

    // Prompt card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBgAlt,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, tint = VioletBright, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Your prompt", color = VioletBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                prompt,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    // Answer input
    Text("Your answer", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = { if (!submitted) onAnswerChange(it) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        placeholder = { Text("Type your answer...", color = TextTertiary) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryPurple,
            unfocusedBorderColor = ChipBg,
            cursorColor = PrimaryPurple,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        enabled = !submitted,
        maxLines = 6
    )

    Spacer(Modifier.height(16.dp))

    if (!submitted) {
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
            enabled = answer.isNotBlank()
        ) {
            Text("Submit Answer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    } else {
        // Waiting for opponent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = PrimaryPurple
            )
            Spacer(Modifier.width(10.dp))
            Text("Waiting for stranger's answer...", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RevealStep(
    matchStatus: MatchStatus?,
    matchId: String?,
    onReact: (String) -> Unit,
    onNextRound: () -> Unit,
    onLeave: () -> Unit
) {
    val myAnswer = matchStatus?.myAnswer ?: ""
    val oppAnswer = matchStatus?.opponentAnswer ?: "..."
    val myReacted = matchStatus?.myReacted

    Spacer(Modifier.height(8.dp))

    Text("Answers Revealed!", color = VioletBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))

    // My answer
    AnswerCard(title = "You", answer = myAnswer, isMe = true)
    Spacer(Modifier.height(12.dp))

    // Opponent's answer
    AnswerCard(title = "Stranger", answer = oppAnswer, isMe = false)

    // Opponent's reaction to me (if any)
    matchStatus?.opponentReacted?.let { react ->
        Spacer(Modifier.height(10.dp))
        Text("Stranger reacted $react to your answer", color = TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(24.dp))

    // Reaction buttons
    if (myReacted == null) {
        Text("React to their answer:", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val reactions = listOf("🔥", "😂", "👍", "😍")
            reactions.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
    } else {
        Text("You reacted $myReacted ✅", color = AccentTeal, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(28.dp))

    // Next round / leave buttons
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onNextRound,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Next", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onLeave,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
        ) {
            Text("Leave", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AnswerCard(title: String, answer: String, isMe: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isMe) PrimaryPurple.copy(alpha = 0.12f) else CardBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isMe) PrimaryPurple.copy(alpha = 0.3f) else ChipBg
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (isMe) "You" else "Stranger",
                color = if (isMe) PrimaryPurple else VioletBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                answer,
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
    }
}
