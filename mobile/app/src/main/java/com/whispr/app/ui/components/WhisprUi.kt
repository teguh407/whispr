package com.whispr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import kotlin.math.abs

/** Deterministic gradient for an anonymous persona name. */
fun personaGradient(seed: String): Brush {
    val idx = abs(seed.hashCode()) % PersonaColors.size
    val (a, b) = PersonaColors[idx]
    return Brush.linearGradient(listOf(a, b))
}

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Round gradient avatar with initials + optional online dot. */
@Composable
fun PersonaAvatar(
    name: String,
    size: Int = 44,
    online: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(personaGradient(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.38f).sp
            )
        }
        if (online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size * 0.28f).dp)
                    .clip(CircleShape)
                    .background(Background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}

/** Small pill chip for tags like #curhat. */
@Composable
fun TagChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg = if (selected) PrimaryPurple.copy(alpha = 0.22f) else ChipBg
    val fg = if (selected) VioletBright else TextSecondary
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(50)) else Modifier)
    ) {
        Text(
            text = if (text.startsWith("#")) text else "#$text",
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

data class NavItem(val route: String, val icon: ImageVector, val label: String)

/** Whispr bottom navigation bar with center Create FAB. */
@Composable
fun WhisprBottomBar(
    current: String,
    onNavigate: (String) -> Unit,
    onCreate: () -> Unit
) {
    val left = listOf(
        NavItem("feed", Icons.Filled.Home, "Home"),
        NavItem("chats", Icons.Filled.ChatBubbleOutline, "Chat"),
    )
    val right = listOf(
        NavItem("explore", Icons.Filled.Explore, "Explore"),
        NavItem("profile", Icons.Filled.Person, "Profile"),
    )
    Surface(color = Surface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            left.forEach { NavCell(it, current, onNavigate, Modifier.weight(1f)) }

            // Center create button
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = "Create", tint = Color.White)
                    }
                }
            }

            right.forEach { NavCell(it, current, onNavigate, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun NavCell(
    item: NavItem,
    current: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = current == item.route
    val tint = if (active) VioletBright else TextTertiary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onNavigate(item.route) }, modifier = Modifier.size(30.dp)) {
            Icon(item.icon, contentDescription = item.label, tint = tint)
        }
        Text(item.label, color = tint, fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Section header row: bold title + optional "See all" action. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (action != null) {
            TextButton(onClick = { onAction?.invoke() }, contentPadding = PaddingValues(0.dp)) {
                Text(action, color = VioletBright, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
