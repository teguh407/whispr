package com.whispr.app.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.data.GameMode
import com.whispr.app.data.GamePrompt
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

/**
 * Games screen wired to the real backend API:
 *   GET /api/games/modes            → [{key, title, emoji}]
 *   GET /api/games/{mode}/prompt    → {mode, prompt}
 *   POST /api/games/answer          → {mode, prompt, answer}
 *
 * Flow: pick a mode → load prompt → type & submit answer → confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onMatch: () -> Unit = {}
) {
    val modes by viewModel.gameModes.collectAsState()
    val currentPrompt by viewModel.currentPrompt.collectAsState()
    val loading by viewModel.loading.collectAsState()

    // Local UI state drives the 3-step flow.
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

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
                    // While inside a mode, offer a quick "back to modes" action.
                    if (selectedMode != null) {
                        IconButton(onClick = {
                            selectedMode = null
                            submitted = false
                            answer = ""
                        }) {
                            Icon(Icons.Default.Apps, "All modes")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
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
                        "Pick a mode, answer the prompt, see how others think.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Match with Stranger button
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMatch() },
                color = PrimaryPurple.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryPurple.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PersonSearch, null, tint = PrimaryPurple, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Match with a Stranger", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Play 1-on-1 anonymously", color = TextTertiary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = PrimaryPurple, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            val mode = selectedMode
            if (mode == null) {
                // ── Step 1: mode picker ──
                SectionLabel("Choose a Game Mode")
                Spacer(Modifier.height(8.dp))
                if (modes.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = PrimaryPurple,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No game modes available", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    modes.forEach { m ->
                        GameModeCard(mode = m, loading = loading) {
                            selectedMode = m.key
                            submitted = false
                            answer = ""
                            viewModel.loadGamePrompt(m.key)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else if (submitted) {
                // ── Step 4: answer submitted confirmation ──
                SubmittedCard(
                    onAnotherPrompt = {
                        submitted = false
                        answer = ""
                        viewModel.loadGamePrompt(mode)
                    },
                    onBackToModes = {
                        selectedMode = null
                        submitted = false
                        answer = ""
                    }
                )
            } else {
                // ── Steps 2 & 3: prompt + answer input ──
                GamePromptCard(
                    mode = mode,
                    prompt = currentPrompt,
                    answer = answer,
                    onAnswerChange = { answer = it },
                    onSubmit = {
                        val prompt = currentPrompt
                        if (prompt != null && answer.isNotBlank()) {
                            viewModel.submitGameAnswer(prompt.mode, prompt.prompt, answer)
                            submitted = true
                            answer = ""
                        }
                    },
                    onBackToModes = {
                        selectedMode = null
                        answer = ""
                    },
                    loading = loading
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Sub-components
// ──────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GameModeCard(mode: GameMode, loading: Boolean, onStart: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clickable(enabled = !loading, onClick = onStart),
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
                    mode.emoji.ifBlank { "🎮" },
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    mode.title.ifBlank { mode.key.replaceFirstChar { it.uppercase() } },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (mode.key.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        mode.key,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onStart,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple),
                modifier = Modifier.size(40.dp),
                enabled = !loading
            ) {
                Icon(Icons.Default.PlayArrow, "Start", tint = Color.White)
            }
        }
    }
}

@Composable
private fun GamePromptCard(
    mode: String,
    prompt: GamePrompt?,
    answer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToModes: () -> Unit,
    loading: Boolean
) {
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
                        mode.replaceFirstChar { it.uppercase() },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text("Prompt", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (prompt == null) {
                // Loading the prompt
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = PrimaryPurple,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Loading prompt…", color = TextSecondary, fontSize = 13.sp)
                        } else {
                            Icon(
                                Icons.Default.ErrorOutline,
                                null,
                                tint = TextTertiary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No prompt loaded", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = onBackToModes) {
                                Text("Back to modes", color = VioletBright)
                            }
                        }
                    }
                }
            } else {
                Text("Prompt", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBgAlt,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        prompt.prompt.ifBlank { "No prompt" },
                        color = TextPrimary,
                        fontSize = 15.sp,
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

                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = answer.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryPurple,
                        disabledContainerColor = PrimaryPurple.copy(alpha = 0.4f)
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text("Submit", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = onBackToModes,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                ) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Back to modes", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SubmittedCard(
    onAnotherPrompt: () -> Unit,
    onBackToModes: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SuccessGreen, AccentTeal))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Answer submitted!",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Want to try another prompt?",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onAnotherPrompt,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Another prompt", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBackToModes,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Apps, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to modes", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
