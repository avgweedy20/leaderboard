package com.scoreboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("dss_prefs", Context.MODE_PRIVATE) }
            var themeMode by remember {
                val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
                mutableStateOf(try { ThemeMode.valueOf(saved!!) } catch (_: Exception) { ThemeMode.SYSTEM })
            }

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
                        prefs.edit().putString("theme_mode", newMode.name).apply()
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

    fun triggerError(msg: String) {
        errorMessage = msg
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                val err = "Database Connection Error: Missing credentials or network failure"
                sportsList = emptyList()
                leaderboardList = emptyList()
                matchesList = emptyList()
                bracketsList = emptyList()
                registeredTeams = emptyList()
                registeredPlayers = emptyList()
                triggerError(err)
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
                            Text(modeText, style = MonoLabelStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                    Triple("Settings", ScoreBoardIcons.Sun, 4)
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
                                    Modifier.border(BorderStroke(2.dp, CourtGreen), ComponentCornerRadius).padding(4.dp)
                                } else {
                                    Modifier.padding(4.dp)
                                }
                            ) {
                                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
                            }
                        }
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
                        Toast.makeText(context, "Error message copied to clipboard", Toast.LENGTH_SHORT).show()
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
                            onToast = { showToast(it) }
                        )
                        2 -> BracketsScreen(
                            bracketsList = bracketsList,
                            sports = sportsList,
                            adminToken = adminToken,
                            onRefresh = { refreshData() },
                            onToast = { showToast(it) }
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
                                        showToast("Admin authenticated successfully!")
                                    } catch (e: Exception) {
                                        loginError = "Login failed: ${e.message}"
                                    }
                                }
                            },
                            onLogout = {
                                SupabaseRepository.logout()
                                adminToken = null
                                showToast("Signed out successfully.")
                            },
                            onRefreshData = { refreshData() },
                            onToast = { showToast(it) }
                        )
                        4 -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            adminToken = adminToken,
                            onLogout = {
                                SupabaseRepository.logout()
                                adminToken = null
                                showToast("Signed out successfully.")
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
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Theme Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = CardCornerRadius
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("APPEARANCE THEME", style = MonoLabelStyle)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text("Light") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text("Dark") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            label = { Text("System") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Credits Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = CardCornerRadius
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("THEME CREDITS", style = MonoLabelStyle)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Theme by Samir Ghimire, President, STEM Club",
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                style = MaterialTheme.typography.headlineMedium,
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
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
        }
    }
}

@Composable
fun LeaderboardCard(rank: Int, item: LeaderboardItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    style = MaterialTheme.typography.headlineMedium,
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
            sportType = sportType,
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = item.level,
                        style = MonoLabelStyle.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val statusText = if (item.status == "completed") "FINAL" else item.status.uppercase()
                val statusBg = if (item.status == "live") AmberWarning else MaterialTheme.colorScheme.surfaceVariant
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("SCORE SUMMARY", style = MonoLabelStyle.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary))
                        Text(
                            text = item.scoreSummary,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = CourtGreen,
                                fontFamily = FontFamily.Monospace
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
                    style = MaterialTheme.typography.headlineMedium,
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = CardCornerRadius
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (item.type == "single_elimination") "Single Elimination Bracket" else "Round Robin Bracket",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Surface(
                                shape = ComponentCornerRadius,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = item.level,
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Admin Management Center",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (adminToken == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = CardCornerRadius
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Admin Authenticated via Supabase", fontWeight = FontWeight.Bold, color = CourtGreen)
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
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(team.name, fontWeight = FontWeight.Bold)
                            Text(team.level, style = MonoLabelStyle)
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
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(player.name, fontWeight = FontWeight.Bold)
                            Text("${player.grade ?: "-"} • ${player.level}", style = MonoLabelStyle)
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
            onDismiss = { showCsvImportDialog = false },
            onSuccess = {
                showCsvImportDialog = false
                onRefreshData()
                onToast("CSV import committed successfully!")
            }
        )
    }
}

// DIALOG COMPOSABLES FOR ADMIN AND MATCH ACTIONS

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Sport", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sport Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sport Type", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "generic",
                        onClick = { type = "generic" },
                        label = { Text("GENERIC") }
                    )
                    FilterChip(
                        selected = type == "cricket",
                        onClick = { type = "cricket" },
                        label = { Text("CRICKET") }
                    )
                    FilterChip(
                        selected = type == "football",
                        onClick = { type = "football" },
                        label = { Text("FOOTBALL") }
                    )
                    FilterChip(
                        selected = type == "basketball",
                        onClick = { type = "basketball" },
                        label = { Text("BASKETBALL") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = winPoints,
                        onValueChange = { winPoints = it },
                        label = { Text("Win Pts") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = drawPoints,
                        onValueChange = { drawPoints = it },
                        label = { Text("Draw Pts") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lossPoints,
                        onValueChange = { lossPoints = it },
                        label = { Text("Loss Pts") },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
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
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Create Sport")
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
    var selectedSportId by remember { mutableStateOf(sports.firstOrNull()?.id ?: "") }
    var level by remember { mutableStateOf("HS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Team", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Team Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Sport", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sports) { sport ->
                        FilterChip(
                            selected = selectedSportId == sport.id,
                            onClick = { selectedSportId = sport.id },
                            label = { Text(sport.name) }
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createTeam(name = name, sportId = selectedSportId, level = level)
                            onSuccess()
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Create Team")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Player", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Player Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Grade") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Team", style = MonoLabelStyle)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(teams) { team ->
                        FilterChip(
                            selected = selectedTeamId == team.id,
                            onClick = { selectedTeamId = team.id },
                            label = { Text(team.name) }
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createPlayer(name = name, teamId = selectedTeamId, grade = grade, level = level)
                            onSuccess()
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Create Player")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CsvImportDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk CSV Import", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Expected CSV format columns: player_name, team_name, sport_name, grade, level", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(ScoreBoardIcons.Upload, contentDescription = "CSV File", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ready to import CSV data", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSuccess, colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)) {
                Text("Commit Import")
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

    val filteredTeams = teams.filter { it.sportId == selectedSportId && it.level == level }
    var teamAId by remember { mutableStateOf(filteredTeams.getOrNull(0)?.id ?: "") }
    var teamBId by remember { mutableStateOf(filteredTeams.getOrNull(1)?.id ?: "") }
    var roundInfo by remember { mutableStateOf("Regular Match") }

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
                            onClick = {
                                selectedSportId = sport.id
                                val ft = teams.filter { it.sportId == sport.id && it.level == level }
                                teamAId = ft.getOrNull(0)?.id ?: ""
                                teamBId = ft.getOrNull(1)?.id ?: ""
                            },
                            label = { Text(sport.name) }
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = {
                                level = l
                                val ft = teams.filter { it.sportId == selectedSportId && it.level == l }
                                teamAId = ft.getOrNull(0)?.id ?: ""
                                teamBId = ft.getOrNull(1)?.id ?: ""
                            },
                            label = { Text(l) }
                        )
                    }
                }

                OutlinedTextField(
                    value = roundInfo,
                    onValueChange = { roundInfo = it },
                    label = { Text("Round Info") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
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
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Schedule Match")
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
    sportType: String,
    teams: List<Team>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var val1 by remember { mutableStateOf("0") }
    var val2 by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Score Match (${sportType.uppercase()})", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LIVE SCOREBOARD PREVIEW", style = MonoLabelStyle.copy(fontSize = 11.sp))
                        Text("$val1 - $val2", style = DisplayScoreStyle.copy(fontSize = 32.sp, color = CourtGreen))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = val1, onValueChange = { val1 = it }, label = { Text("Team A Result") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = val2, onValueChange = { val2 = it }, label = { Text("Team B Result") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val scoreA = val1.toIntOrNull() ?: 0
                            val scoreB = val2.toIntOrNull() ?: 0
                            val winnerId = if (scoreA > scoreB) match.teamAId else if (scoreB > scoreA) match.teamBId else null
                            val isDraw = scoreA == scoreB
                            val summary = "$val1 - $val2"

                            SupabaseRepository.updateMatchScore(
                                matchId = match.id,
                                winnerTeamId = winnerId,
                                isDraw = isDraw,
                                scoreSummary = summary
                            )
                            onSuccess()
                        } catch (_: Exception) {}
                    }
                },
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
                            label = { Text(sport.name) }
                        )
                    }
                }

                Text("Level", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ES", "MS", "HS").forEach { l ->
                        FilterChip(
                            selected = level == l,
                            onClick = { level = l },
                            label = { Text(l) }
                        )
                    }
                }

                Text("Tournament Type", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "single_elimination",
                        onClick = { type = "single_elimination" },
                        label = { Text("Single Elimination") }
                    )
                    FilterChip(
                        selected = type == "round_robin",
                        onClick = { type = "round_robin" },
                        label = { Text("Round Robin") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            SupabaseRepository.createBracket(sportId = selectedSportId, level = level, type = type)
                            onSuccess()
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("Generate Bracket")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
