package com.scoreboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

val Context.dataStore by preferencesDataStore(name = "dss_prefs")
val THEME_KEY = stringPreferencesKey("theme_mode")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    var housesList by remember { mutableStateOf<List<House>>(emptyList()) }
    var houseStandingsList by remember { mutableStateOf<List<HouseOverallStanding>>(emptyList()) }
    var sportsList by remember { mutableStateOf<List<Sport>>(emptyList()) }
    var leaderboardList by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var matchesList by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var registeredTeams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var registeredPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var adminToken by remember { mutableStateOf<String?>(SupabaseRepository.adminAuthToken) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    var selectedSportId by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("") }

    fun showSnackbar(msg: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun refreshData() {
        isLoading = true
        coroutineScope.launch {
            try {
                housesList = SupabaseRepository.getHouses()
                houseStandingsList = SupabaseRepository.getHouseOverallStandings()
                sportsList = SupabaseRepository.getSports()
                leaderboardList = SupabaseRepository.getLeaderboard(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    gender = if (selectedGender.isEmpty()) null else selectedGender
                )
                matchesList = SupabaseRepository.getMatches(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    gender = if (selectedGender.isEmpty()) null else selectedGender
                )
                registeredTeams = SupabaseRepository.getTeams()
                registeredPlayers = SupabaseRepository.getPlayers()
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Database Connection Error: Missing credentials or network failure"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedSportId, selectedGender) {
        refreshData()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ScoreBoardIcons.Trophy,
                            contentDescription = "Brand Icon",
                            tint = CourtGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("DSS Inter-House Meet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val modeText = if (SupabaseRepository.isConfigured) "Connected: Supabase Postgres DB" else "Disconnected: Credentials Missing"
                            Text(modeText, style = MonoLabelStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(
                    Triple("House Standings", ScoreBoardIcons.Trophy, 0),
                    Triple("Sport Standings", ScoreBoardIcons.Calendar, 1),
                    Triple("Fixtures", ScoreBoardIcons.Calendar, 2),
                    Triple("Admin", ScoreBoardIcons.Shield, 3),
                    Triple("Settings", ScoreBoardIcons.Settings, 4)
                ).forEach { (label, icon, tabIdx) ->
                    val isSelected = selectedTab == tabIdx
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tabIdx },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) CourtGreen else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
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
                        clipboard.setPrimaryClip(ClipData.newPlainText("Error", msg))
                        showSnackbar("Error copied to clipboard")
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    FlatLoadingSkeleton()
                } else {
                    when (selectedTab) {
                        0 -> HouseOverallStandingsScreen(standings = houseStandingsList)
                        1 -> PerSportStandingsScreen(
                            sports = sportsList,
                            selectedSportId = selectedSportId,
                            onSelectSport = { selectedSportId = it },
                            selectedGender = selectedGender,
                            onSelectGender = { selectedGender = it },
                            list = leaderboardList
                        )
                        2 -> MatchesScreen(
                            matches = matchesList,
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
                            teams = registeredTeams,
                            players = registeredPlayers,
                            onLogin = {
                                coroutineScope.launch {
                                    try {
                                        adminToken = SupabaseRepository.login(loginEmail, loginPassword)
                                        loginError = null
                                        showSnackbar("Admin authenticated!")
                                        refreshData()
                                    } catch (e: Exception) {
                                        loginError = "Login failed: ${e.message}"
                                    }
                                }
                            },
                            onLogout = {
                                SupabaseRepository.logout()
                                adminToken = null
                                showSnackbar("Signed out.")
                            }
                        )
                        4 -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            adminToken = adminToken,
                            onLogout = {
                                SupabaseRepository.logout()
                                adminToken = null
                                showSnackbar("Signed out.")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HouseOverallStandingsScreen(standings: List<HouseOverallStanding>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Overall House Standings", style = MaterialTheme.typography.headlineLarge)
        }

        items(standings) { h ->
            val color = parseHexColor(h.colorHex)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, color),
                shape = CardCornerRadius
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = color,
                            shape = ComponentCornerRadius
                        ) {
                            Text(
                                text = "${h.shortCode} HOUSE",
                                color = Color.White,
                                style = MonoLabelStyle.copy(fontSize = 12.sp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text("#${h.rank}", style = DisplayScoreStyle.copy(fontSize = 32.sp, color = color))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(h.houseName, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL POINTS", style = MonoLabelStyle.copy(fontSize = 10.sp))
                            Text("${h.totalPoints} PTS", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("W-D-L", style = MonoLabelStyle.copy(fontSize = 10.sp))
                            Text("${h.totalWins}-${h.totalDraws}-${h.totalLosses}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerSportStandingsScreen(
    sports: List<Sport>,
    selectedSportId: String,
    onSelectSport: (String) -> Unit,
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    list: List<LeaderboardItem>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Per-Sport Standings", style = MaterialTheme.typography.headlineLarge)
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedSportId.isEmpty(),
                        onClick = { onSelectSport("") },
                        label = { Text("ALL SPORTS") }
                    )
                }
                items(sports) { s ->
                    FilterChip(
                        selected = selectedSportId == s.id,
                        onClick = { onSelectSport(s.id) },
                        label = { Text(s.name.uppercase()) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "All Genders", "Boys" to "Boys", "Girls" to "Girls").forEach { (valG, label) ->
                    FilterChip(
                        selected = selectedGender == valG,
                        onClick = { onSelectGender(valG) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        items(list) { item ->
            val color = parseHexColor(item.houseColor)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.teamName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "${item.houseName ?: "House"} • ${item.sportName} (${item.gender ?: "Boys"})",
                            fontSize = 12.sp,
                            color = color
                        )
                        Text("${item.played}P | ${item.wins}W-${item.draws}D-${item.losses}L", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("${item.points} PTS", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
                }
            }
        }
    }
}

@Composable
fun MatchesScreen(
    matches: List<MatchItem>,
    adminToken: String?,
    onRefresh: () -> Unit,
    onToast: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Fixtures & Results", style = MaterialTheme.typography.headlineLarge)
        }

        items(matches) { m ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(m.roundInfo ?: "League Match", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Stage: ${m.stage?.uppercase() ?: "LEAGUE"} • Gender: ${m.gender ?: "Boys"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    m.scoreSummary?.let { summary ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("RESULT: $summary", fontWeight = FontWeight.Bold, color = CourtGreen, fontSize = 18.sp)
                    }
                }
            }
        }
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
    teams: List<Team>,
    players: List<Player>,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Admin Control", style = MaterialTheme.typography.headlineLarge)
        }

        if (adminToken == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(value = email, onValueChange = onEmailChange, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Sign In") }
                    }
                }
            }
        } else {
            item {
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Sign Out Session")
                }
            }
            item {
                Text("Registered Squads (${teams.size})", fontWeight = FontWeight.Bold)
            }
            items(teams) { t ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(t.name, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Theme Mode", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System").forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlatLoadingSkeleton() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CourtGreen)
    }
}

@Composable
fun ErrorBannerWithRetry(message: String, onRetry: () -> Unit, onCopy: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onCopy() }
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
