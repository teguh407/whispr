package com.whispr.app.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.data.GameMode
import com.whispr.app.data.GameSession
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    val modes by viewModel.gameModes.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var answer by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadGameModes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Games", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (activeGame != null) {
                        IconButton(onClick = {
                            activeGame?.let { viewModel.endGame(it.id) }
                        }) {
                            Icon(Icons.Default.Close, "End game", tint = ErrorRed)
                        }
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
                .padding(16.dp)
        ) {
            // Hero header
            Surface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .padding(20.dp)
                ) {
                    Icon(Icons.Default.SportsEsports, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Whispr Games", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Answer prompts, earn karma, and discover how others think.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val session = activeGame
            if (session != null) {
                // Active session view
                SectionLabel("Current Session")
                Spacer(Modifier.height(8.dp))
                GameSessionCard(
                    session = session,
                    answer = answer,
                    onAnswerChange = { answer = it },
                    onSubmit = {
                        val prompt = session.currentPrompt
                        if (prompt != null && answer.isNotBlank()) {
                            viewModel.submitGameAnswer(session.id, prompt.id, answer)
                            answer = ""
                        }
                    },
                    onNext = { viewModel.nextPrompt(session.id) },
                    onEnd = { viewModel.endGame(session.id) },
                    loading = loading
                )
            } else {
                // Mode picker
                SectionLabel("Choose a Game Mode")
                Spacer(Modifier.height(8.dp))
                if (modes.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (loading) {
                                CircularProgressIndicator(color = PrimaryPurple, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(Icons.Default.SportsEsports, null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No game modes available", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    modes.forEach { mode ->
                        GameModeCard(mode = mode, onStart = { viewModel.startGame(mode.id) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GameModeCard(mode: GameMode, onStart: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.icon.ifBlank { "?" }.take(1).uppercase(),
                    color = Color.White, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (mode.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        mode.description,
                        color = TextSecondary, fontSize = 12.sp, maxLines = 2
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onStart,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.PlayArrow, "Start", tint = Color.White)
            }
        }
    }
}

@Composable
private fun GameSessionCard(
    session: GameSession,
    answer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    loading: Boolean
) {
    val prompt = session.currentPrompt
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(PrimaryPink, VioletBright))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.HelpOutline, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        session.gameMode.ifBlank { "Game" }.replaceFirstChar { it.uppercase() },
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Text("${session.answers.size} answers", color = AccentTeal, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (prompt != null) {
                Text("Prompt", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBgAlt,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        prompt.text.ifBlank { "No prompt" },
                        color = TextPrimary, fontSize = 15.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text("Your answer", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = onAnswerChange,
                    placeholder = { Text("Type your answer…", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        unfocusedContainerColor = CardBgAlt,
                        focusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = VioletBright
                    )
                )

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = answer.isNotBlank() && !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            disabledContainerColor = PrimaryPurple.copy(alpha = 0.4f)
                        )
                    ) { Text("Submit", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !loading
                    ) {
                        Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Next", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = onEnd,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Icon(Icons.Default.Stop, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("End game", color = ErrorRed, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Session complete", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = onEnd,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) { Text("Finish", fontWeight = FontWeight.Bold) }
            }

            // Previous answers
            if (session.answers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Divider(color = Background, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                Text("Your answers", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                session.answers.forEach { a ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardBgAlt,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            a.text,
                            color = TextPrimary, fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
