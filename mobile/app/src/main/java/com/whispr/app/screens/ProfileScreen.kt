package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.data.KarmaLogEntry
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onBlocks: () -> Unit,
    onChats: () -> Unit,
    onSettings: () -> Unit = {}
) {
    val user by viewModel.currentUser.collectAsState()
    val karmaResp by viewModel.karma.collectAsState()
    val karmaLog by viewModel.karmaLog.collectAsState()
    var displayName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var bio by remember(user) { mutableStateOf(user?.bio ?: "") }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.clearError()
        viewModel.loadKarma()
        viewModel.loadKarmaLog()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearError() }
    }

    // Derived "ghost" identity from real backend fields.
    val karma = karmaResp?.karma ?: user?.karma ?: 0
    val days = user?.daysActive ?: 0
    val posts = user?.postsCount ?: 0
    val level = karmaResp?.level ?: "Newcomer"
    val trustScore = 500 + karma * 2 + days * 5
    val whisprId = "#" + (user?.id?.take(7)?.uppercase() ?: "0000000")

    var showInNearby by remember { mutableStateOf(true) }
    var readReceipts by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { editing = !editing }) {
                        Icon(if (editing) Icons.Default.Close else Icons.Default.Edit,
                            "Edit", tint = VioletBright)
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Avatar + identity
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White,
                    modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(12.dp))

            if (editing) {
                OutlinedTextField(
                    value = displayName, onValueChange = { displayName = it },
                    label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio, onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.updateProfile(displayName, bio); editing = false },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            } else {
                Text(user?.displayName ?: "Anonymous", fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Whispr ID $whisprId", color = TextSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))

                // Level + Trust badges
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BadgePill("Level: $level", Icons.Default.Shield, PrimaryPurple)
                    BadgePill("Trust $trustScore", Icons.Default.Verified, AccentTeal)
                }
                Spacer(Modifier.height(8.dp))
                if (!user?.bio.isNullOrBlank()) {
                    Text(user!!.bio!!, color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(12.dp))

                // Stats row
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Karma", "$karma")
                        StatItem("Posts", "$posts")
                        StatItem("Days", "$days")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Ghost Reputation
                SectionHeader("Ghost Reputation")
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        RepRow("Posts shared", "$posts", Icons.Default.Article)
                        RepRow("Karma earned", "$karma", Icons.Default.Star)
                        RepRow("Trust score", "$trustScore", Icons.Default.Verified)
                        RepRow("Level", level, Icons.Default.Shield, last = true)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Karma Log
                SectionHeader("Karma Log")
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (karmaLog.isEmpty()) {
                            Text("No karma activity yet", color = TextSecondary, fontSize = 13.sp)
                        } else {
                            karmaLog.forEachIndexed { index, entry ->
                                KarmaLogRow(entry)
                                if (index < karmaLog.lastIndex) {
                                    Divider(color = Background, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Settings & Privacy
                SectionHeader("Settings & Privacy")
                Surface(color = CardBg, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow("Show me in Nearby", Icons.Default.LocationOn, showInNearby) {
                            showInNearby = it
                        }
                        Divider(color = Background, thickness = 1.dp)
                        ToggleRow("Read Receipts", Icons.Default.DoneAll, readReceipts) {
                            readReceipts = it
                        }
                        Divider(color = Background, thickness = 1.dp)
                        NavRow("My Chats", Icons.Default.Chat, onChats)
                        Divider(color = Background, thickness = 1.dp)
                        NavRow("Block List", Icons.Default.Block, onBlocks)
                        Divider(color = Background, thickness = 1.dp)
                        NavRow("App Settings", Icons.Default.Settings, onSettings)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Whispr Premium
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(
                        Modifier
                            .background(Brush.linearGradient(listOf(VioletDeep, PrimaryPink)))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Whispr Premium", color = Color.White, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        PremiumPerk("See who liked your whispers")
                        PremiumPerk("Unlimited once-view photos")
                        PremiumPerk("Longer voice messages")
                        PremiumPerk("Exclusive premium badge")
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Upgrade", color = VioletDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                TextButton(
                    onClick = { viewModel.logout(); onLogout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Logout, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Logout", color = ErrorRed, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryPurple,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
    unfocusedContainerColor = CardBg,
    focusedContainerColor = Color.Transparent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = VioletBright,
    unfocusedLabelColor = TextSecondary
)

@Composable
private fun SectionHeader(title: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BadgePill(text: String, icon: ImageVector, tint: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VioletBright)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun RepRow(label: String, value: String, icon: ImageVector, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = VioletBright, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleRow(label: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryPurple,
                uncheckedTrackColor = ChipBg
            )
        )
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
        }
    }
}

@Composable
private fun PremiumPerk(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp)
    }
}

@Composable
private fun KarmaLogRow(entry: KarmaLogEntry) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.amount >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
            null,
            tint = if (entry.amount >= 0) SuccessGreen else ErrorRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.reason, color = TextPrimary, fontSize = 14.sp)
            entry.createdAt?.let {
                Text(formatKarmaTime(it), color = TextTertiary, fontSize = 11.sp)
            }
        }
        Text(
            (if (entry.amount >= 0) "+" else "") + entry.amount,
            color = if (entry.amount >= 0) SuccessGreen else ErrorRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatKarmaTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val date = java.util.Date(
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        )
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)
    } catch (e: Exception) { "" }
}
