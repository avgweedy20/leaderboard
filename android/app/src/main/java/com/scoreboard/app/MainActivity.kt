package com.scoreboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreboard.app.models.*
import com.scoreboard.app.network.SupabaseRepository
import com.scoreboard.app.ui.ScoreBoardIcons
import com.scoreboard.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader

val Context.dataStore by preferencesDataStore(name = "dss_prefs")
val THEME_KEY = stringPreferencesKey("theme_mode")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read theme mode before first composition to prevent light-mode flash
        val initialTheme = runBlocking {
            val prefs = dataStore.data.first()
            val saved = prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name
            try { ThemeMode.valueOf(saved) } catch (_: Exception) { ThemeMode.SYSTEM }
        }

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(initialTheme) }

            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            ScoreBoardTheme(darkTheme = isDark) {
                ScoreBoardMainScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        coroutineScope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[THEME_KEY] = newMode.name
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreBoardMainScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(0) }
    var sportsList by remember { mutableStateOf<List<Sport>>(emptyList()) }
    var leaderboardList by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var matchesList by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var bracketsList by remember { mutableStateOf<List<BracketItem>>(emptyList()) }
    var registeredTeams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var registeredPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var adminToken by remember { mutableStateOf<String?>(SupabaseRepository.adminAuthToken) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    var selectedSportId by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("ALL") }

    fun showSnackbar(msg: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun refreshData() {
        isLoading = true
        coroutineScope.launch {
            try {
                sportsList = SupabaseRepository.getSports()
                leaderboardList = SupabaseRepository.getLeaderboard(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    level = if (selectedLevel == "ALL") null else selectedLevel
                )
                matchesList = SupabaseRepository.getMatches(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    level = if (selectedLevel == "ALL") null else selectedLevel
                )
                bracketsList = SupabaseRepository.getBrackets(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    level = if (selectedLevel == "ALL") null else selectedLevel
                )
                registeredTeams = SupabaseRepository.getTeams()
                registeredPlayers = SupabaseRepository.getPlayers()
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Database Connection Error: Missing credentials or network failure"
                sportsList = emptyList()
                leaderboardList = emptyList()
                matchesList = emptyList()
                bracketsList = emptyList()
                registeredTeams = emptyList()
                registeredPlayers = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedSportId, selectedLevel) {
        refreshData()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), ComponentCornerRadius),
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = CourtGreen,
                    shape = ComponentCornerRadius
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ScoreBoardIcons.Stopwatch,
                            contentDescription = "Brand Icon",
                            tint = CourtGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("DSS Sports", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val modeText = if (SupabaseRepository.isConfigured) {
                                "Connected: Supabase Postgres DB"
                            } else {
                                "Disconnected: Supabase Credentials Missing"
                            }
                            Text(
                                modeText,
                                style = MonoLabelStyle.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                listOf(
                    Triple("Leaderboard", ScoreBoardIcons.Trophy, 0),
                    Triple("Matches", ScoreBoardIcons.Calendar, 1),
                    Triple("Brackets", ScoreBoardIcons.BracketTree, 2),
                    Triple("Admin", ScoreBoardIcons.Shield, 3),
                    Triple("Settings", ScoreBoardIcons.Settings, 4)
                ).forEach { (label, icon, tabIdx) ->
                    val isSelected = selectedTab == tabIdx
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tabIdx },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = if (isSelected) {
                                    Modifier
                                        .border(BorderStroke(2.dp, CourtGreen), ComponentCornerRadius)
                                        .padding(4.dp)
                                } else {
                                    Modifier.padding(4.dp)
                                }
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) CourtGreen else MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CourtGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            errorMessage?.let { msg ->
                ErrorBannerWithRetry(
                    message = msg,
                    onRetry = { refreshData() },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Error Message", msg)
                        clipboard.setPrimaryClip(clip)
                        showSnackbar("Error copied to clipboard")
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    FlatLoadingSkeleton()
                } else {
                    when (selectedTab) {
                        0 -> LeaderboardScreen(
                            sports = sportsList,
                            selectedSportId = selectedSportId,
                            onSelectSport = { selectedSportId = it },
                            selectedLevel = selectedLevel,
                            onSelectLevel = { selectedLevel = it },
                            list = leaderboardList
                        )
                        1 -> MatchesScreen(
                            sports = sportsList,
                            teams = registeredTeams,
                            matches = matchesList,
                            adminToken = adminToken,
                            onRefresh = { refreshData() },
                            onToast = { showSnackbar(it) }
                        )
                        2 -> BracketsScreen(
                            bracketsList = bracketsList,
                            sports = sportsList,
                            adminToken = adminToken,
                            onRefresh = { refreshData() },
                            onToast = { showSnackbar(it) }
                        )
                        3 -> AdminScreen(
                            adminToken = adminToken,
                            email = loginEmail,
                            onEmailChange = { loginEmail = it },
                            password = loginPassword,
                            onPasswordChange = { loginPassword = it },
                            error = loginError,
                            sports = sportsList,
                            teams = registeredTeams,
                            players = registeredPlayers,
                            onLogin = {
                                coroutineScope.launch {
                                    try {
                                        adminToken = SupabaseRepository.login(loginEmail, loginPassword)
                                        loginError = null
                                        showSnackbar("Admin authenticated successfully!")
                                        refreshData()
                                    } catch (e: Exception) {
                                        loginError = "Login failed: ${e.message}"
                                    }
                                }
                            },
                            onLogout = {
                                SupabaseRepository.logout()
                                adminToken = null
                                showSnackbar("Signed out successfully.")
                            },
                            onRefreshData = { refreshData() },
                            onToast = { showSnackbar(it) }
                        )
                        4 -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            adminToken = adminToken,
                            onLogout = {
                                SupabaseRepository.logout()
                                adminToken = null
                                showSnackbar("Signed out successfully.")
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    adminToken: String?,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Theme Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = CardCornerRadius
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("APPEARANCE THEME", style = MonoLabelStyle)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(ThemeMode.LIGHT, "Light", 1f),
                            Triple(ThemeMode.DARK, "Dark", 1f),
                            Triple(ThemeMode.SYSTEM, "System", 1f)
                        ).forEach { (mode, label, weight) ->
                            val isSelected = themeMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(label) },
                                shape = ComponentCornerRadius,
                                modifier = Modifier.weight(weight),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CourtGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // Credits Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = CardCornerRadius
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ABOUT/CREDITS", style = MonoLabelStyle)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Created by Samir Ghimire, President, STEM Club",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // App Version Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = CardCornerRadius
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ABOUT DSS SPORTS", style = MonoLabelStyle)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Application Version: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Min SDK: 24 (Android 7.0+) | Target SDK: 34", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        if (adminToken != null) {
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = ComponentCornerRadius,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out Admin Session")
                }
            }
        }
    }
}

@Composable
fun FlatLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator(
            color = CourtGreen,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "FETCHING LIVE TOURNAMENT DATA...",
            style = MonoLabelStyle.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        )
    }
}

@Composable
fun ErrorBannerWithRetry(
    message: String,
    onRetry: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = ComponentCornerRadius,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap message to copy error text",
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = ScoreBoardIcons.Upload,
                    contentDescription = "Copy Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Retry Connection", fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    sports: List<Sport>,
    selectedSportId: String,
    onSelectSport: (String) -> Unit,
    selectedLevel: String,
    onSelectLevel: (String) -> Unit,
    list: List<LeaderboardItem>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Live Leaderboard",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Sport Chips Filter Bar
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedSportId.isEmpty(),
                        onClick = { onSelectSport("") },
                        label = { Text("ALL SPORTS", style = MonoLabelStyle.copy(fontSize = 12.sp)) },
                        shape = ComponentCornerRadius,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CourtGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(sports) { sport ->
                    val isSelected = selectedSportId == sport.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSport(sport.id) },
                        label = { Text(sport.name.uppercase(), style = MonoLabelStyle.copy(fontSize = 12.sp)) },
                        shape = ComponentCornerRadius,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CourtGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Level Chips Filter Bar
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "ES", "MS", "HS").forEach { lvl ->
                    val isSelected = selectedLevel == lvl
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectLevel(lvl) },
                        label = { Text(lvl, style = MonoLabelStyle.copy(fontSize = 11.sp)) },
                        shape = ComponentCornerRadius,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CourtGreen,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (selectedSportId.isEmpty()) {
            val grouped = list.groupBy { it.sportName }
            if (grouped.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = ScoreBoardIcons.Trophy,
                        title = "No Tournament Results Yet",
                        description = "There are no completed matches for the selected filters."
                    )
                }
            } else {
                grouped.forEach { (sportName, items) ->
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = ComponentCornerRadius,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = sportName.uppercase(),
                                    style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    itemsIndexed(items) { index, item ->
                        LeaderboardCard(rank = index + 1, item = item)
                    }
                }
            }
        } else {
            if (list.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = ScoreBoardIcons.Trophy,
                        title = "No Tournament Results Yet",
                        description = "There are no completed matches for the selected filters."
                    )
                }
            } else {
                itemsIndexed(list) { index, item ->
                    LeaderboardCard(rank = index + 1, item = item)
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CardCornerRadius
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = ComponentCornerRadius,
                    colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun LeaderboardCard(rank: Int, item: LeaderboardItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CardCornerRadius
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = ComponentCornerRadius,
                    color = if (rank == 1) CourtGreen else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$rank",
                            color = if (rank == 1) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.teamName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.sportName} • ${item.level} (${item.played}P | ${item.wins}W-${item.draws}D-${item.losses}L)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                text = "${item.points} PTS",
                style = MonoLabelStyle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CourtGreen
                )
            )
        }
    }
}

@Composable
fun MatchesScreen(
    sports: List<Sport>,
    teams: List<Team>,
    matches: List<MatchItem>,
    adminToken: String?,
    onRefresh: () -> Unit,
    onToast: (String) -> Unit
) {
    var showCreateMatchDialog by remember { mutableStateOf(false) }
    var scoringMatchId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tournament Matches",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (adminToken != null) {
                    Button(
                        onClick = { showCreateMatchDialog = true },
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Icon(ScoreBoardIcons.Plus, contentDescription = "New Match", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Match")
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = ScoreBoardIcons.Calendar,
                    title = "No Scheduled Matches",
                    description = "No tournament matches match the selected criteria."
                )
            }
        } else {
            itemsIndexed(matches) { _, item ->
                MatchCard(
                    item = item,
                    adminToken = adminToken,
                    onScoreClick = { scoringMatchId = item.id }
                )
            }
        }
    }

    if (showCreateMatchDialog) {
        CreateMatchDialog(
            sports = sports,
            teams = teams,
            onDismiss = { showCreateMatchDialog = false },
            onSuccess = {
                showCreateMatchDialog = false
                onRefresh()
                onToast("Match scheduled successfully!")
            }
        )
    }

    if (scoringMatchId != null) {
        val match = matches.find { it.id == scoringMatchId }
        val sport = sports.find { it.id == match?.sportId }
        val sportType = sport?.type ?: "generic"

        ScoringDialog(
            match = match!!,
            sport = sport!!,
            teams = teams,
            onDismiss = { scoringMatchId = null },
            onSuccess = {
                scoringMatchId = null
                onRefresh()
                onToast("Match score saved successfully!")
            }
        )
    }
}

@Composable
fun MatchCard(
    item: MatchItem,
    adminToken: String?,
    onScoreClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = CardCornerRadius
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ComponentCornerRadius,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = item.level,
                        style = MonoLabelStyle.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val statusText = if (item.status == "completed") "FINAL" else item.status.uppercase()
                val statusBg = if (item.status == "live") AmberLight else MaterialTheme.colorScheme.surfaceVariant
                val statusFg = if (item.status == "live") Color.Black else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = StatusPillShape,
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        style = MonoLabelStyle.copy(fontSize = 11.sp, color = statusFg),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.roundInfo ?: "Match", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            if (item.scoreSummary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ComponentCornerRadius,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("SCORE SUMMARY", style = MonoLabelStyle.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary))
                        Text(
                            text = item.scoreSummary,
                            style = DisplayScoreStyle.copy(
                                fontSize = 24.sp,
                                color = CourtGreen
                            )
                        )
                    }
                }
            }

            if (adminToken != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onScoreClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = ComponentCornerRadius,
                    colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                ) {
                    Text(if (item.status == "completed") "Edit Score" else "Score Match")
                }
            }
        }
    }
}

@Composable
fun BracketsScreen(
    bracketsList: List<BracketItem>,
    sports: List<Sport>,
    adminToken: String?,
    onRefresh: () -> Unit,
    onToast: (String) -> Unit
) {
    var showGenerateBracketDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tie Sheet / Brackets",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (adminToken != null) {
                    Button(
                        onClick = { showGenerateBracketDialog = true },
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Icon(ScoreBoardIcons.BracketTree, contentDescription = "Generate Bracket", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generate Bracket")
                    }
                }
            }
        }

        if (bracketsList.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = ScoreBoardIcons.BracketTree,
                    title = "No Brackets Generated Yet",
                    description = "Generate tournament brackets from the Admin Dashboard."
                )
            }
        } else {
            itemsIndexed(bracketsList) { _, item ->
                val sport = sports.find { it.id == item.sportId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = CardCornerRadius
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${sport?.name ?: "Unknown Sport"} Bracket",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Surface(
                                shape = ComponentCornerRadius,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${item.level} • ${if (item.type == "single_elimination") "Single Elim" else "Round Robin"}",
                                    style = MonoLabelStyle.copy(fontSize = 11.sp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGenerateBracketDialog) {
        GenerateBracketDialog(
            sports = sports,
            onDismiss = { showGenerateBracketDialog = false },
            onSuccess = {
                showGenerateBracketDialog = false
                onRefresh()
                onToast("Bracket generated successfully!")
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    adminToken: String?,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String?,
    sports: List<Sport>,
    teams: List<Team>,
    players: List<Player>,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRefreshData: () -> Unit,
    onToast: (String) -> Unit
) {
    var showAddSportDialog by remember { mutableStateOf(false) }
    var showAddTeamDialog by remember { mutableStateOf(false) }
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var editingTeamId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Admin Center",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (adminToken == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = CardCornerRadius
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Admin Sign In", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = onEmailChange,
                            label = { Text("Email", style = MonoLabelStyle) },
                            shape = ComponentCornerRadius,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = onPasswordChange,
                            label = { Text("Password", style = MonoLabelStyle) },
                            shape = ComponentCornerRadius,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = ComponentCornerRadius,
                            colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                        ) {
                            Text("Sign In")
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = CardCornerRadius
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Admin Mode Active", fontWeight = FontWeight.Bold, color = CourtGreen)
                            Button(
                                onClick = onLogout,
                                modifier = Modifier.height(40.dp),
                                shape = ComponentCornerRadius,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddSportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Icon(ScoreBoardIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Sport")
                    }
                    Button(
                        onClick = { showAddTeamDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Text("Add Team")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddPlayerDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Text("Add Player")
                    }
                    Button(
                        onClick = { showCsvImportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Icon(ScoreBoardIcons.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV Import")
                    }
                }
            }

            item {
                Text("Registered Teams (${teams.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (teams.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = ScoreBoardIcons.Shield,
                        title = "No Teams Registered",
                        description = "Add teams manually or via CSV import."
                    )
                }
            } else {
                items(teams) { team ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(team.name, fontWeight = FontWeight.Bold)
                                val sportsStr = team.sportIds.map { id ->
                                    sports.find { it.id == id }?.name ?: "Unknown"
                                }.joinToString(", ")
                                Text(
                                    "Sports: $sportsStr",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(team.level, style = MonoLabelStyle)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = ScoreBoardIcons.Edit,
                                    contentDescription = "Edit Sports",
                                    tint = CourtGreen,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { editingTeamId = team.id }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Registered Players (${players.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (players.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = ScoreBoardIcons.Shield,
                        title = "No Players Registered",
                        description = "Add players manually or via CSV import."
                    )
                }
            } else {
                items(players) { player ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(player.name, fontWeight = FontWeight.Bold)
                            val playerTeam = teams.find { it.id == player.teamId }?.name ?: "Unknown Team"
                            Text(
                                "$playerTeam • ${player.grade ?: "-"} • ${player.level}",
                                style = MonoLabelStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSportDialog) {
        AddSportDialog(
            onDismiss = { showAddSportDialog = false },
            onSuccess = {
                showAddSportDialog = false
                onRefreshData()
                onToast("Sport created successfully!")
            }
        )
    }

    if (showAddTeamDialog) {
        AddTeamDialog(
            sports = sports,
            onDismiss = { showAddTeamDialog = false },
            onSuccess = {
                showAddTeamDialog = false
                onRefreshData()
                onToast("Team created successfully!")
            }
        )
    }

    if (showAddPlayerDialog) {
        AddPlayerDialog(
            teams = teams,
            onDismiss = { showAddPlayerDialog = false },
            onSuccess = {
                showAddPlayerDialog = false
                onRefreshData()
                onToast("Player created successfully!")
            }
        )
    }

    if (showCsvImportDialog) {
        CsvImportDialog(
            sports = sports,
            teams = teams,
            onDismiss = { showCsvImportDialog = false },
            onSuccess = {
                showCsvImportDialog = false
                onRefreshData()
                onToast("CSV import committed successfully!")
            }
        )
    }

    if (editingTeamId != null) {
        val team = teams.find { it.id == editingTeamId }
        if (team != null) {
            EditTeamSportsDialog(
                team = team,
                sports = sports,
                onDismiss = { editingTeamId = null },
                onSuccess = {
                    editingTeamId = null
                    onRefreshData()
                    onToast("Team sports updated successfully!")
                }
            )
        }
    }
}

// DIALOGS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSportDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("generic") }
    var winPoints by remember { mutableStateOf("3") }
    var drawPoints by remember { mutableStateOf("1") }
    var lossPoints by remember { mutableStateOf("0") }
    var isLowerBetter by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Sport", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sport Name") },
                    shape = ComponentCornerRadius,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sport Type", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("generic", "cricket", "football", "basketball").forEach { t ->
                        val isSelected = type == t
                        FilterChip(
                            selected = isSelected,
                            onClick = { type = t },
                            label = { Text(t.uppercase()) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = winPoints,
                        onValueChange = { winPoints = it },
                        label = { Text("Win Pts") },
                        shape = ComponentCornerRadius,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = drawPoints,
                        onValueChange = { drawPoints = it },
                        label = { Text("Draw Pts") },
                        shape = ComponentCornerRadius,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lossPoints,
                        onValueChange = { lossPoints = it },
                        label = { Text("Loss Pts") },
                        shape = ComponentCornerRadius,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isLowerBetter,
                        onCheckedChange = { isLowerBetter = it }
                    )
                    Text("Lower score is better (e.g. races)", fontSize = 13.sp)
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && name.isNotBlank(),
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createSport(
                                name = name,
                                type = type,
                                pointWin = winPoints.toIntOrNull() ?: 3,
                                pointDraw = drawPoints.toIntOrNull() ?: 1,
                                pointLoss = lossPoints.toIntOrNull() ?: 0,
                                isLowerBetter = isLowerBetter
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to create sport"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Sport")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTeamDialog(
    sports: List<Sport>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val selectedSportIds = remember { mutableStateListOf<String>() }
    var level by remember { mutableStateOf("HS") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Team", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Team Name") },
                    shape = ComponentCornerRadius,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sports Plays", style = MonoLabelStyle)
                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(sports) { sport ->
                        val isChecked = selectedSportIds.contains(sport.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedSportIds.remove(sport.id)
                                    else selectedSportIds.add(sport.id)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked == true) selectedSportIds.add(sport.id)
                                    else selectedSportIds.remove(sport.id)
                                }
                            )
                            Text(sport.name)
                        }
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && name.isNotBlank() && selectedSportIds.isNotEmpty(),
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createTeam(
                                name = name,
                                sportIds = selectedSportIds.toList(),
                                level = level
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to create team"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Team")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTeamSportsDialog(
    team: Team,
    sports: List<Sport>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val selectedSportIds = remember { mutableStateListOf<String>().apply { addAll(team.sportIds) } }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Sports for ${team.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select sports the team plays:", fontSize = 14.sp)
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(sports) { sport ->
                        val isChecked = selectedSportIds.contains(sport.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedSportIds.remove(sport.id)
                                    else selectedSportIds.add(sport.id)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked == true) selectedSportIds.add(sport.id)
                                    else selectedSportIds.remove(sport.id)
                                }
                            )
                            Text(sport.name)
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.updateTeamSports(team.id, selectedSportIds.toList())
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to update sports"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlayerDialog(
    teams: List<Team>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedTeamId by remember { mutableStateOf(teams.firstOrNull()?.id ?: "") }
    var grade by remember { mutableStateOf("Grade 10") }
    var level by remember { mutableStateOf("HS") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Player", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Player Name") },
                    shape = ComponentCornerRadius,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Grade") },
                    shape = ComponentCornerRadius,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Team", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(teams) { team ->
                        FilterChip(
                            selected = selectedTeamId == team.id,
                            onClick = { selectedTeamId = team.id },
                            label = { Text(team.name) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && name.isNotBlank() && selectedTeamId.isNotEmpty(),
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createPlayer(
                                name = name,
                                teamId = selectedTeamId,
                                grade = grade,
                                level = level
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to create player"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Player")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

data class ParsedPlayerRow(
    val name: String,
    val teamName: String,
    val sportName: String,
    val grade: String,
    val level: String,
    val isValid: Boolean,
    val errorMessage: String?,
    val resolvedTeamId: String? = null
)

@Composable
fun CsvImportDialog(
    sports: List<Sport>,
    teams: List<Team>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var parsedRows = remember { mutableStateListOf<ParsedPlayerRow>() }
    var importStatusMessage by remember { mutableStateOf("No CSV file loaded.") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val list = mutableListOf<ParsedPlayerRow>()
                    var lineNum = 0
                    reader.use { r ->
                        var line = r.readLine()
                        // Skip header if matches format
                        if (line != null && (line.contains("player_name") || line.contains("team_name"))) {
                            line = r.readLine()
                        }
                        while (line != null) {
                            lineNum++
                            val tokens = line.split(",").map { it.trim() }
                            if (tokens.size >= 3) {
                                val name = tokens.getOrNull(0) ?: ""
                                val teamName = tokens.getOrNull(1) ?: ""
                                val sportName = tokens.getOrNull(2) ?: ""
                                val grade = tokens.getOrNull(3) ?: "Grade 10"
                                val level = tokens.getOrNull(4) ?: "HS"

                                // Validation
                                val matchingSport = sports.find { it.name.equals(sportName, ignoreCase = true) }
                                val matchingTeam = teams.find {
                                    it.name.equals(teamName, ignoreCase = true) &&
                                            it.level.equals(level, ignoreCase = true) &&
                                            (matchingSport == null || it.sportIds.contains(matchingSport.id))
                                }

                                var valid = true
                                var errMsg: String? = null
                                var teamId: String? = null

                                if (name.isBlank()) {
                                    valid = false
                                    errMsg = "Empty player name"
                                } else if (matchingSport == null) {
                                    valid = false
                                    errMsg = "Sport '$sportName' not found"
                                } else if (matchingTeam == null) {
                                    valid = false
                                    errMsg = "Team '$teamName' playing '$sportName' at level '$level' not found"
                                } else {
                                    teamId = matchingTeam.id
                                }

                                list.add(
                                    ParsedPlayerRow(
                                        name = name,
                                        teamName = teamName,
                                        sportName = sportName,
                                        grade = grade,
                                        level = level,
                                        isValid = valid,
                                        errorMessage = errMsg,
                                        resolvedTeamId = teamId
                                    )
                                )
                            }
                            line = r.readLine()
                        }
                    }
                    parsedRows.clear()
                    parsedRows.addAll(list)
                    importStatusMessage = "Loaded ${list.size} rows (${list.count { it.isValid }} valid)."
                } catch (e: Exception) {
                    importStatusMessage = "Error reading CSV: ${e.message}"
                }
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CSV Bulk Player Import", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Format required: player_name, team_name, sport_name, grade, level",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("text/comma-separated-values", "text/csv")) },
                    shape = ComponentCornerRadius,
                    colors = ButtonDefaults.buttonColors(containerColor = CourtGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select CSV File")
                }

                Text(importStatusMessage, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                if (parsedRows.isNotEmpty()) {
                    Text("PREVIEW TABLE", style = MonoLabelStyle)
                    LazyColumn(modifier = Modifier.height(150.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, ComponentCornerRadius)) {
                        items(parsedRows) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${row.teamName} (${row.level})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (row.isValid) {
                                    Text("Valid", color = CourtGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text(row.errorMessage ?: "Invalid", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                            Divider()
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            val validCount = parsedRows.count { it.isValid }
            Button(
                enabled = !isSubmitting && validCount > 0,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val bulkData = parsedRows.filter { it.isValid }.map { row ->
                                mapOf<String, Any>(
                                    "name" to row.name,
                                    "team_id" to row.resolvedTeamId!!,
                                    "grade" to row.grade,
                                    "level" to row.level
                                )
                            }
                            SupabaseRepository.createPlayersBulk(bulkData)
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to import rows"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Commit Import ($validCount)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMatchDialog(
    sports: List<Sport>,
    teams: List<Team>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedSportId by remember { mutableStateOf(sports.firstOrNull()?.id ?: "") }
    var level by remember { mutableStateOf("HS") }

    val filteredTeams = teams.filter { team -> team.sportIds.contains(selectedSportId) && team.level == level }
    var teamAId by remember { mutableStateOf("") }
    var teamBId by remember { mutableStateOf("") }
    var roundInfo by remember { mutableStateOf("Regular Match") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedSportId, level) {
        val list = teams.filter { it.sportIds.contains(selectedSportId) && it.level == level }
        teamAId = list.getOrNull(0)?.id ?: ""
        teamBId = list.getOrNull(1)?.id ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule New Match", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sport", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sports) { sport ->
                        FilterChip(
                            selected = selectedSportId == sport.id,
                            onClick = { selectedSportId = sport.id },
                            label = { Text(sport.name) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Text("Team A", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredTeams) { team ->
                        FilterChip(
                            selected = teamAId == team.id,
                            onClick = { teamAId = team.id },
                            label = { Text(team.name) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Text("Team B", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredTeams) { team ->
                        FilterChip(
                            selected = teamBId == team.id,
                            onClick = { teamBId = team.id },
                            label = { Text(team.name) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                OutlinedTextField(
                    value = roundInfo,
                    onValueChange = { roundInfo = it },
                    label = { Text("Round Info") },
                    shape = ComponentCornerRadius,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && teamAId.isNotEmpty() && teamBId.isNotEmpty() && teamAId != teamBId,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createMatch(
                                sportId = selectedSportId,
                                teamAId = teamAId,
                                teamBId = teamBId,
                                level = level,
                                roundInfo = roundInfo
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to schedule match"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Schedule Match")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ScoringDialog(
    match: MatchItem,
    sport: Sport,
    teams: List<Team>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val teamAName = teams.find { it.id == match.teamAId }?.name ?: "Team A"
    val teamBName = teams.find { it.id == match.teamBId }?.name ?: "Team B"

    when (sport.type) {
        "cricket" -> CricketScoringDialog(match, sport, teamAName, teamBName, onDismiss, onSuccess)
        "football" -> FootballScoringDialog(match, sport, teamAName, teamBName, onDismiss, onSuccess)
        "basketball" -> BasketballScoringDialog(match, sport, teamAName, teamBName, onDismiss, onSuccess)
        else -> GenericScoringDialog(match, sport, teamAName, teamBName, onDismiss, onSuccess)
    }
}

@Composable
fun CricketScoringDialog(
    match: MatchItem,
    sport: Sport,
    teamAName: String,
    teamBName: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var runsA by remember { mutableStateOf("0") }
    var wicketsA by remember { mutableStateOf("0") }
    var oversA by remember { mutableStateOf("0.0") }
    var extrasA by remember { mutableStateOf("0") }

    var runsB by remember { mutableStateOf("0") }
    var wicketsB by remember { mutableStateOf("0") }
    var oversB by remember { mutableStateOf("0.0") }
    var extrasB by remember { mutableStateOf("0") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Score Cricket Match", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(teamAName, fontWeight = FontWeight.Bold, color = CourtGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = runsA, onValueChange = { runsA = it }, label = { Text("Runs") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = wicketsA, onValueChange = { wicketsA = it }, label = { Text("Wickets") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = oversA, onValueChange = { oversA = it }, label = { Text("Overs") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = extrasA, onValueChange = { extrasA = it }, label = { Text("Extras") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(teamBName, fontWeight = FontWeight.Bold, color = CourtGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = runsB, onValueChange = { runsB = it }, label = { Text("Runs") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = wicketsB, onValueChange = { wicketsB = it }, label = { Text("Wickets") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = oversB, onValueChange = { oversB = it }, label = { Text("Overs") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = extrasB, onValueChange = { extrasB = it }, label = { Text("Extras") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val rA = runsA.toIntOrNull() ?: 0
                            val rB = runsB.toIntOrNull() ?: 0
                            val winnerId = if (rA > rB) match.teamAId else if (rB > rA) match.teamBId else null
                            val isDraw = rA == rB
                            val summary = "$teamAName: $runsA/$wicketsA ($oversA ov) v $teamBName: $runsB/$wicketsB ($oversB ov)"

                            SupabaseRepository.updateMatchScore(
                                matchId = match.id,
                                winnerTeamId = winnerId,
                                isDraw = isDraw,
                                scoreSummary = summary
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to save score"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Save Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FootballScoringDialog(
    match: MatchItem,
    sport: Sport,
    teamAName: String,
    teamBName: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var goalsA by remember { mutableStateOf("0") }
    var ycA by remember { mutableStateOf("0") }
    var rcA by remember { mutableStateOf("0") }

    var goalsB by remember { mutableStateOf("0") }
    var ycB by remember { mutableStateOf("0") }
    var rcB by remember { mutableStateOf("0") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Score Football Match", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(teamAName, fontWeight = FontWeight.Bold, color = CourtGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = goalsA, onValueChange = { goalsA = it }, label = { Text("Goals") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = ycA, onValueChange = { ycA = it }, label = { Text("Yellow Cards") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rcA, onValueChange = { rcA = it }, label = { Text("Red Cards") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(teamBName, fontWeight = FontWeight.Bold, color = CourtGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = goalsB, onValueChange = { goalsB = it }, label = { Text("Goals") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = ycB, onValueChange = { ycB = it }, label = { Text("Yellow Cards") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rcB, onValueChange = { rcB = it }, label = { Text("Red Cards") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val gA = goalsA.toIntOrNull() ?: 0
                            val gB = goalsB.toIntOrNull() ?: 0
                            val winnerId = if (gA > gB) match.teamAId else if (gB > gA) match.teamBId else null
                            val isDraw = gA == gB
                            val summary = "$goalsA - $goalsB (YC: $ycA-$ycB, RC: $rcA-$rcB)"

                            SupabaseRepository.updateMatchScore(
                                matchId = match.id,
                                winnerTeamId = winnerId,
                                isDraw = isDraw,
                                scoreSummary = summary
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to save score"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Save Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BasketballScoringDialog(
    match: MatchItem,
    sport: Sport,
    teamAName: String,
    teamBName: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var ptsA by remember { mutableStateOf("0") }
    var foulsA by remember { mutableStateOf("0") }

    var ptsB by remember { mutableStateOf("0") }
    var foulsB by remember { mutableStateOf("0") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Score Basketball Match", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(teamAName, fontWeight = FontWeight.Bold, color = CourtGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = ptsA, onValueChange = { ptsA = it }, label = { Text("Score Points") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = foulsA, onValueChange = { foulsA = it }, label = { Text("Fouls") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(teamBName, fontWeight = FontWeight.Bold, color = CourtGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = ptsB, onValueChange = { ptsB = it }, label = { Text("Score Points") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = foulsB, onValueChange = { foulsB = it }, label = { Text("Fouls") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val pA = ptsA.toIntOrNull() ?: 0
                            val pB = ptsB.toIntOrNull() ?: 0
                            val winnerId = if (pA > pB) match.teamAId else if (pB > pA) match.teamBId else null
                            val isDraw = pA == pB
                            val summary = "$ptsA - $ptsB (Fouls: $foulsA-$foulsB)"

                            SupabaseRepository.updateMatchScore(
                                matchId = match.id,
                                winnerTeamId = winnerId,
                                isDraw = isDraw,
                                scoreSummary = summary
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to save score"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Save Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GenericScoringDialog(
    match: MatchItem,
    sport: Sport,
    teamAName: String,
    teamBName: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var val1 by remember { mutableStateOf("0") }
    var val2 by remember { mutableStateOf("0") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Score Match (${sport.name.uppercase()})", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LIVE SCOREBOARD PREVIEW", style = MonoLabelStyle.copy(fontSize = 11.sp))
                        Text("$val1 - $val2", style = DisplayScoreStyle.copy(fontSize = 32.sp, color = CourtGreen))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = val1, onValueChange = { val1 = it }, label = { Text("$teamAName Score") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = val2, onValueChange = { val2 = it }, label = { Text("$teamBName Score") }, shape = ComponentCornerRadius, modifier = Modifier.weight(1f))
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val scoreA = val1.toIntOrNull() ?: 0
                            val scoreB = val2.toIntOrNull() ?: 0
                            val isDraw = scoreA == scoreB

                            val winnerId = if (scoreA == scoreB) {
                                null
                            } else {
                                val teamAWins = if (sport.isLowerScoreBetter) scoreA < scoreB else scoreA > scoreB
                                if (teamAWins) match.teamAId else match.teamBId
                            }

                            val summary = "$val1 - $val2"

                            SupabaseRepository.updateMatchScore(
                                matchId = match.id,
                                winnerTeamId = winnerId,
                                isDraw = isDraw,
                                scoreSummary = summary
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to save score"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Save Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateBracketDialog(
    sports: List<Sport>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedSportId by remember { mutableStateOf(sports.firstOrNull()?.id ?: "") }
    var level by remember { mutableStateOf("HS") }
    var type by remember { mutableStateOf("single_elimination") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Tournament Bracket", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sport", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sports) { sport ->
                        FilterChip(
                            selected = selectedSportId == sport.id,
                            onClick = { selectedSportId = sport.id },
                            label = { Text(sport.name) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) },
                            shape = ComponentCornerRadius
                        )
                    }
                }

                Text("Tournament Type", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "single_elimination",
                        onClick = { type = "single_elimination" },
                        label = { Text("Single Elimination") },
                        shape = ComponentCornerRadius
                    )
                    FilterChip(
                        selected = type == "round_robin",
                        onClick = { type = "round_robin" },
                        label = { Text("Round Robin") },
                        shape = ComponentCornerRadius
                    )
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && selectedSportId.isNotEmpty(),
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createBracket(sportId = selectedSportId, level = level, type = type)
                            onSuccess()
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to generate bracket"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                shape = ComponentCornerRadius,
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Generate Bracket")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
