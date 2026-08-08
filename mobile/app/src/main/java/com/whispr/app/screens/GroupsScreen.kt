package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whispr.app.data.Group
import com.whispr.app.ui.components.PersonaAvatar
import com.whispr.app.ui.components.TagChip
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    viewModel: WhisprViewModel,
    onBack: () -> Unit,
    onGroupClick: (String) -> Unit = {}
) {
    val groups by viewModel.groups.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    var query by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadGroups() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = PrimaryPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Create Group")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(46.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(VioletBright),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Search groups", color = TextTertiary, fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val filtered = groups.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    (it.topic ?: "").contains(query, ignoreCase = true) ||
                    (it.description ?: "").contains(query, ignoreCase = true)
            }

            when {
                loading && groups.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VioletBright)
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Groups, null, tint = TextTertiary,
                                modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (query.isBlank()) "No groups yet" else "No groups found",
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (query.isBlank()) "Create a group and bring people together!"
                                else "Try a different search",
                                color = TextTertiary, fontSize = 13.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { group ->
                            GroupCard(
                                group = group,
                                onClick = { onGroupClick(group.id) },
                                onJoin = { viewModel.joinGroup(group.id) },
                                onLeave = { viewModel.leaveGroup(group.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Group Dialog
    if (showDialog) {
        CreateGroupDialog(
            onDismiss = { showDialog = false },
            onCreate = { name, description, topic ->
                viewModel.createGroup(name, description, topic)
                showDialog = false
            }
        )
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onClick: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    val memberLabel = when (group.memberCount) {
        0 -> "No members"
        1 -> "1 member"
        else -> "${group.memberCount} members"
    }

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(group.name.ifBlank { "Group" }, size = 48)

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        group.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Group, null, tint = TextTertiary,
                            modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(memberLabel, color = TextTertiary, fontSize = 12.sp)
                        if (group.createdAt != null) {
                            Text("  ·  ", color = TextTertiary, fontSize = 12.sp)
                            Text(relativeTime(group.createdAt), color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!group.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    group.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!group.topic.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(group.topic, selected = true)
                }
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = ChipBg, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group.isMember) {
                    Surface(
                        color = ChipBg,
                        shape = RoundedCornerShape(50),
                        onClick = onLeave
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Logout, null, tint = ErrorRed,
                                modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Leave", color = ErrorRed, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Surface(
                        color = PrimaryPurple.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                        onClick = onJoin
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.GroupAdd, null, tint = VioletBright,
                                modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Join", color = VioletBright, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (group.isMember) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Joined", color = SuccessGreen, fontSize = 11.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, topic: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    val nameValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New Group", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedContainerColor = CardBg,
                        focusedContainerColor = Color.Transparent,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedContainerColor = CardBg,
                        focusedContainerColor = Color.Transparent,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedContainerColor = CardBg,
                        focusedContainerColor = Color.Transparent,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nameValid) {
                        onCreate(
                            name.trim(),
                            description.trim().ifBlank { null },
                            topic.trim().ifBlank { null }
                        )
                    }
                },
                enabled = nameValid
            ) {
                Text("Create", color = VioletBright, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val t = java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        val diff = System.currentTimeMillis() - t
        val m = diff / 60000
        when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            m < 1440 -> "${m / 60}h"
            else -> "${m / 1440}d"
        }
    } catch (e: Exception) { "" }
}
