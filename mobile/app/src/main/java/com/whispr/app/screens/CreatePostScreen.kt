package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

private data class PostType(val id: String, val label: String, val icon: ImageVector)

private val postTypes = listOf(
    PostType("anonymous", "Anonymous", Icons.Filled.VisibilityOff),
    PostType("question", "Question", Icons.Filled.HelpOutline),
    PostType("confession", "Confession", Icons.Filled.Lock),
    PostType("poll", "Poll", Icons.Filled.BarChart),
    PostType("voice", "Voice", Icons.Filled.Mic),
    PostType("photo", "Photo", Icons.Filled.PhotoCamera),
    PostType("nearby", "Nearby Chat", Icons.Filled.LocationOn),
)

private val moods = listOf(
    "Happy" to "😊",
    "Lonely" to "😔",
    "Need Advice" to "🤔",
    "Venting" to "😤"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var onceView by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("anonymous") }
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var selectedBg by remember { mutableStateOf<String?>(null) } // gradient preset id, null = plain
    val activeBg = postBackgroundById(selectedBg)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    Button(
                        onClick = {
                            val tagList = tags.split(",", " ").map { it.trim().removePrefix("#") }
                                .filter { it.isNotBlank() }
                            viewModel.createPost(
                                content, tagList, onceView,
                                bgType = if (selectedBg != null) "gradient" else "none",
                                bgValue = selectedBg,
                                postType = selectedType,
                                mood = selectedMood
                            )
                            onBack()
                        },
                        enabled = content.isNotBlank(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            disabledContainerColor = ChipBg
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Post", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (content.isNotBlank()) Color.White else TextTertiary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
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
            // Post type selector
            Text("Post type", color = TextSecondary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            FlowGrid(postTypes.size, columns = 4) { i ->
                val t = postTypes[i]
                TypeChip(t.label, t.icon, selectedType == t.id) { selectedType = t.id }
            }

            Spacer(Modifier.height(20.dp))

            // Content field — plain card OR gradient background
            if (activeBg == null) {
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(VioletBright),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .padding(16.dp),
                        decorationBox = { inner ->
                            if (content.isEmpty()) {
                                Text("What's on your mind?", color = TextTertiary, fontSize = 16.sp)
                            }
                            inner()
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(activeBg.colors)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White, fontSize = 22.sp, lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        decorationBox = { inner ->
                            if (content.isEmpty()) {
                                Text("What's on your mind?",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Background picker
            Text("Background", color = TextSecondary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "None" swatch
                BgSwatch(
                    selected = selectedBg == null,
                    label = "None",
                    brush = Brush.linearGradient(listOf(CardBg, CardBgAlt))
                ) { selectedBg = null }
                PostBackgrounds.forEach { bg ->
                    BgSwatch(
                        selected = selectedBg == bg.id,
                        label = bg.label,
                        brush = Brush.linearGradient(bg.colors)
                    ) { selectedBg = bg.id }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Mood selector
            Text("How are you feeling?", color = TextSecondary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moods.forEach { (name, emoji) ->
                    MoodChip(name, emoji, selectedMood == name) {
                        selectedMood = if (selectedMood == name) null else name
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Tags
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tag, null, tint = VioletBright, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(VioletBright),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (tags.isEmpty()) Text("Add tags: curhat, random…",
                                color = TextTertiary, fontSize = 14.sp)
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Once-view toggle
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Visibility, null, tint = PrimaryPink)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Once-view media", color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("Disappears after viewing", color = TextTertiary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = onceView,
                        onCheckedChange = { onceView = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryPurple,
                            uncheckedTrackColor = ChipBg
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BgSwatch(selected: Boolean, label: String, brush: Brush, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
                .then(
                    if (selected)
                        Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = Color.White,
                    modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) TextPrimary else TextTertiary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TypeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) Brush.linearGradient(listOf(GradientStart, GradientEnd))
                    else Brush.linearGradient(listOf(CardBg, CardBg))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (selected) Color.White else TextSecondary,
                modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) TextPrimary else TextTertiary, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun MoodChip(name: String, emoji: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) PrimaryPurple.copy(alpha = 0.25f) else ChipBg,
        shape = RoundedCornerShape(50),
        onClick = onClick,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, VioletBright) else null
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            Text(name, color = if (selected) VioletBright else TextSecondary, fontSize = 12.sp)
        }
    }
}

/** Simple wrapping grid without external deps. */
@Composable
private fun FlowGrid(count: Int, columns: Int, item: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var i = 0
        while (i < count) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (c in 0 until columns) {
                    if (i < count) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { item(i) }
                        i++
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
