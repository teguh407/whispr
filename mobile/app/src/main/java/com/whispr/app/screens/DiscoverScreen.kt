package com.whispr.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.whispr.app.data.DiscoverUser
import com.whispr.app.ui.components.PersonaAvatar
import com.whispr.app.ui.components.TagChip
import com.whispr.app.ui.components.WhisprBottomBar
import com.whispr.app.ui.theme.*
import com.whispr.app.viewmodel.WhisprViewModel

// ── Filter option catalogs ──

private data class RadiusOption(val label: String, val km: Int?)
private val radiusOptions = listOf(
    RadiusOption("5 km", 5),
    RadiusOption("25 km", 25),
    RadiusOption("100 km", 100),
    RadiusOption("Anywhere", null)
)

private data class KarmaOption(val label: String, val min: Int?)
private val karmaOptions = listOf(
    KarmaOption("Any karma", null),
    KarmaOption("100+", 100),
    KarmaOption("500+", 500),
    KarmaOption("1k+", 1000)
)

private val genderOptions = listOf("All", "Male", "Female", "Other")
private val interestOptions = listOf(
    "Music", "Gaming", "Movies", "Tech", "Travel",
    "Art", "Food", "Fitness", "Books", "Anime"
)

private const val AGE_MIN = 18f
private const val AGE_MAX = 80f

// Simple city-to-coords lookup for major Indonesian cities
private val cityCoords = mapOf(
    "jakarta" to Pair(-6.2088, 106.8456),
    "surabaya" to Pair(-7.2575, 112.7521),
    "bandung" to Pair(-6.9175, 107.6191),
    "medan" to Pair(3.5952, 98.6722),
    "semarang" to Pair(-6.9666, 110.4196),
    "makassar" to Pair(-5.1477, 119.4327),
    "palembang" to Pair(-2.9761, 104.7754),
    "tangerang" to Pair(-6.1781, 106.6319),
    "depok" to Pair(-6.4025, 106.8186),
    "bekasi" to Pair(-6.2349, 106.9896),
    "yogyakarta" to Pair(-7.7956, 110.3695),
    "malang" to Pair(-7.9666, 112.6326),
    "solo" to Pair(-7.5755, 110.8243),
    "denpasar" to Pair(-8.6500, 115.2167),
    "balikpapan" to Pair(-1.2654, 116.8311),
    "manado" to Pair(1.4748, 124.8421),
    "pekanbaru" to Pair(0.5071, 101.4478),
    "lampung" to Pair(-5.3971, 105.2668),
    "banjarmasin" to Pair(-3.3186, 114.5944),
    "jayapura" to Pair(-2.5916, 140.6690),
    "mataram" to Pair(-8.5833, 116.1167),
    "batam" to Pair(1.0456, 104.0305),
    "bogor" to Pair(-6.5971, 106.8060),
    "cirebon" to Pair(-6.7320, 108.5523),
    "kediri" to Pair(-7.8167, 112.0167),
    "tasikmalaya" to Pair(-7.3467, 108.2067),
    "serang" to Pair(-6.1103, 106.1503),
    "pontianak" to Pair(-0.0263, 109.3425),
    "ambon" to Pair(-3.6954, 128.1814),
    "ternate" to Pair(0.7907, 127.3840)
)

private fun cityToCoords(cityName: String): Pair<Double, Double> {
    val key = cityName.trim().lowercase()
    return cityCoords[key] ?: cityCoords["jakarta"]!!
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: WhisprViewModel,
    onMessage: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onCreate: () -> Unit = {}
) {
    val users by viewModel.discoverUsers.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && !isRefreshing) {
                    pullOffset = (pullOffset + available.y * 0.3f).coerceAtMost(150f)
                    if (pullOffset > 100f) {
                        isRefreshing = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            applyFilters()
            delay(800)
            isRefreshing = false
            pullOffset = 0f
        }
    }

    // Filter state
    var selectedRadius by remember { mutableStateOf(radiusOptions.last()) }   // Anywhere
    var selectedInterest by remember { mutableStateOf<String?>(null) }
    var selectedKarma by remember { mutableStateOf(karmaOptions.first()) }   // Any
    var selectedGender by remember { mutableStateOf("All") }
    var ageRange by remember { mutableStateOf(AGE_MIN..AGE_MAX) }
    var query by remember { mutableStateOf("") }

    // Location setting state
    var showLocationDialog by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf("") }
    var currentCity by remember { mutableStateOf<String?>(null) }

    fun applyFilters() {
        val minAge = if (ageRange.start <= AGE_MIN) null else ageRange.start.toInt()
        val maxAge = if (ageRange.endInclusive >= AGE_MAX) null else ageRange.endInclusive.toInt()
        viewModel.loadDiscoverUsers(
            radiusKm = selectedRadius.km,
            interests = selectedInterest,
            minKarma = selectedKarma.min,
            gender = if (selectedGender == "All") null else selectedGender.lowercase(),
            minAge = minAge,
            maxAge = maxAge
        )
    }

    fun resetFilters() {
        selectedRadius = radiusOptions.last()
        selectedInterest = null
        selectedKarma = karmaOptions.first()
        selectedGender = "All"
        ageRange = AGE_MIN..AGE_MAX
        query = ""
        applyFilters()
    }

    val handleMessage: (String) -> Unit = { userId ->
        viewModel.createChat(userId)
        onMessage(userId)
    }

    // Client-side name search layered over server-filtered results
    val filtered = users.filter {
        query.isBlank() ||
            it.displayName?.contains(query, ignoreCase = true) == true ||
            it.username.contains(query, ignoreCase = true)
    }

    LaunchedEffect(Unit) { applyFilters() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WhisprBottomBar(current = "explore", onNavigate = onNavigate, onCreate = onCreate)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(nestedScrollConnection)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Header ──
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Discover",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Find ghosts nearby who share your vibe",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = { showLocationDialog = true }) {
                            Icon(Icons.Outlined.LocationOn, "Set Location", tint = VioletBright)
                        }
                    }
                    currentCity?.let { city ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Place, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Location: $city", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Search bar ──
            item {
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
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search by name"
                        )
                    }
                }
            }

            // ── Distance ──
            item {
                ChipRow(
                    label = "Distance",
                    options = radiusOptions.map { it.label },
                    selected = selectedRadius.label,
                    onSelect = { label ->
                        radiusOptions.firstOrNull { it.label == label }?.let {
                            selectedRadius = it
                            applyFilters()
                        }
                    }
                )
            }

            // ── Interests ──
            item {
                ChipRow(
                    label = "Interests",
                    options = interestOptions,
                    selected = selectedInterest,
                    onSelect = { opt ->
                        selectedInterest = if (selectedInterest == opt) null else opt
                        applyFilters()
                    }
                )
            }

            // ── Minimum karma ──
            item {
                ChipRow(
                    label = "Minimum karma",
                    options = karmaOptions.map { it.label },
                    selected = selectedKarma.label,
                    onSelect = { label ->
                        karmaOptions.firstOrNull { it.label == label }?.let {
                            selectedKarma = it
                            applyFilters()
                        }
                    }
                )
            }

            // ── Gender ──
            item {
                ChipRow(
                    label = "Gender",
                    options = genderOptions,
                    selected = selectedGender,
                    onSelect = { selectedGender = it; applyFilters() }
                )
            }

            // ── Age range ──
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Age range", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${ageRange.start.toInt()} – ${ageRange.endInclusive.toInt()}",
                            color = VioletBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    RangeSlider(
                        value = ageRange,
                        onValueChange = { ageRange = it },
                        onValueChangeFinished = { applyFilters() },
                        valueRange = AGE_MIN..AGE_MAX,
                        colors = SliderDefaults.colors(
                            thumbColor = VioletBright,
                            activeTrackColor = PrimaryPurple,
                            inactiveTrackColor = ChipBg
                        )
                    )
                }
            }

            // ── Results header ──
            item {
                HorizontalDivider(color = ChipBg, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${filtered.size} ghosts found",
                            color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        if (loading) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                color = VioletBright,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    TextButton(onClick = { resetFilters() }, contentPadding = PaddingValues(0.dp)) {
                        Text("Reset", color = VioletBright, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Results ──
            if (loading && filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VioletBright)
                    }
                }
            } else if (filtered.isEmpty()) {
                item { EmptyDiscover() }
            } else {
                items(filtered, key = { it.id }) { user ->
                    DiscoverUserCard(user, onMessage = handleMessage)
                }
            }
        }

        // Pull-to-refresh indicator
        if (isRefreshing || pullOffset > 10f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(24.dp)
                    .alpha(if (isRefreshing) 1f else (pullOffset / 100f).coerceIn(0f, 1f)),
                color = VioletBright,
                strokeWidth = 2.dp
            )
        }
        } // end Box

        // ── Set Location dialog ──
        if (showLocationDialog) {
            AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                containerColor = Surface,
                title = { Text("Set Location", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter your city name. We'll use Jakarta coordinates (-6.2088, 106.8456) as the default location.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cityInput,
                            onValueChange = { cityInput = it },
                            label = { Text("City name") },
                            placeholder = { Text("e.g. Jakarta") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                unfocusedContainerColor = CardBg, focusedContainerColor = Color.Transparent,
                                focusedBorderColor = PrimaryPurple, unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val city = cityInput.trim().ifBlank { null }
                        val coords = cityToCoords(city ?: "Jakarta")
                        viewModel.updateLocation(coords.first, coords.second, city)
                        currentCity = city ?: "Jakarta"
                        showLocationDialog = false
                        cityInput = ""
                        applyFilters()
                    }) { Text("Set", color = PrimaryPurple, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationDialog = false }) { Text("Cancel", color = TextSecondary) }
                }
            )
        }
    }
}

// ── User card ──

@Composable
private fun DiscoverUserCard(user: DiscoverUser, onMessage: (String) -> Unit) {
    val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.username
    val meta = buildList {
        add("@${user.username}")
        user.age?.let { add("$it yrs") }
        user.gender?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
        user.distanceKm?.let { add(formatDistance(it)) }
    }.joinToString("  ·  ")

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(name, size = 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = VioletBright, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${user.karma}",
                                color = VioletBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        meta,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            user.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Spacer(Modifier.height(8.dp))
                Text(
                    bio,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (user.interests.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    user.interests.take(4).forEach { TagChip(it) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                onClick = { onMessage(user.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Say hi", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Filter chip row (wrapping) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                SelectionChip(
                    label = opt,
                    selected = selected == opt,
                    onClick = { onSelect(opt) }
                )
            }
        }
    }
}

@Composable
private fun SelectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) PrimaryPurple.copy(alpha = 0.22f) else ChipBg
    val fg = if (selected) VioletBright else TextSecondary
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(50))
    ) {
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

// ── Search field ──

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(VioletBright),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = TextTertiary, fontSize = 14.sp)
            inner()
        }
    )
}

// ── Empty state ──

@Composable
private fun EmptyDiscover() {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Explore, null, tint = TextTertiary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No ghosts found", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Try widening your radius or adjusting filters.", color = TextTertiary, fontSize = 13.sp)
    }
}

// ── helpers ──

private fun formatDistance(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()} m away"
    km < 10.0 -> String.format("%.1f km away", km)
    else -> "${km.toInt()} km away"
}
