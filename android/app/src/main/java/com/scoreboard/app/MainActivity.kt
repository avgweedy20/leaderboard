package com.scoreboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreboard.app.models.*
import com.scoreboard.app.network.RetrofitClient
import com.scoreboard.app.ui.ScoreBoardIcons
import com.scoreboard.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkThemePreference by remember { mutableStateOf<Boolean?>(null) }
            val isDark = darkThemePreference ?: isSystemInDarkTheme()

            ScoreBoardTheme(darkTheme = isDark) {
                ScoreBoardMainScreen(
                    isDarkTheme = isDark,
                    onToggleTheme = { darkThemePreference = !isDark }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreBoardMainScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var sportsList by remember { mutableStateOf<List<Sport>>(emptyList()) }
    var leaderboardList by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var matchesList by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var bracketsList by remember { mutableStateOf<List<BracketItem>>(emptyList()) }
    var healthInfo by remember { mutableStateOf<HealthInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var versionInfo by remember { mutableStateOf<VersionInfo?>(null) }
    var showOtaDialog by remember { mutableStateOf(false) }

    var adminToken by remember { mutableStateOf<String?>(null) }
    var loginEmail by remember { mutableStateOf("admin@scoreboard.com") }
    var loginPassword by remember { mutableStateOf("admin123") }
    var loginError by remember { mutableStateOf<String?>(null) }

    var selectedSportId by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("ALL") }

    fun refreshData() {
        coroutineScope.launch {
            try {
                sportsList = RetrofitClient.instance.getSports()
                leaderboardList = RetrofitClient.instance.getLeaderboard(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    level = if (selectedLevel == "ALL") null else selectedLevel
                )
                matchesList = RetrofitClient.instance.getMatches(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    level = if (selectedLevel == "ALL") null else selectedLevel
                )
                bracketsList = RetrofitClient.instance.getBrackets(
                    sportId = if (selectedSportId.isEmpty()) null else selectedSportId,
                    level = if (selectedLevel == "ALL") null else selectedLevel
                )
            } catch (e: Exception) {
                if (sportsList.isEmpty()) {
                    sportsList = listOf(
                        Sport("s1", "Cricket", "cricket", "ALL", 3, 1, 0, false),
                        Sport("s2", "Football", "football", "ALL", 3, 1, 0, false),
                        Sport("s3", "Basketball", "basketball", "ALL", 2, 0, 0, false)
                    )
                }
                if (leaderboardList.isEmpty()) {
                    leaderboardList = listOf(
                        LeaderboardItem("t1", "Lions", "s1", "Cricket", "HS", 5, 4, 0, 1, 12),
                        LeaderboardItem("t2", "Eagles", "s2", "Football", "HS", 5, 3, 1, 1, 10)
                    )
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedSportId, selectedLevel) {
        refreshData()
    }

    LaunchedEffect(Unit) {
        try {
            healthInfo = RetrofitClient.instance.getHealth()
            versionInfo = RetrofitClient.instance.getVersion()
            if (versionInfo != null && versionInfo!!.versionCode > 1) {
                showOtaDialog = true
            }
        } catch (_: Exception) {}
        refreshData()
    }

    Scaffold(
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
                            Text("ScoreBoard Live", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val modeText = if (healthInfo?.supabaseConnected == true || healthInfo?.mode == "supabase") {
                                "Connected: Supabase Postgres DB"
                            } else {
                                "Development: In-Memory Mock DB"
                            }
                            Text(modeText, style = MonoLabelStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) ScoreBoardIcons.Sun else ScoreBoardIcons.Moon,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Leaderboard") },
                    icon = { Icon(ScoreBoardIcons.Trophy, contentDescription = "Leaderboard", modifier = Modifier.size(20.dp)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Matches") },
                    icon = { Icon(ScoreBoardIcons.Calendar, contentDescription = "Matches", modifier = Modifier.size(20.dp)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Brackets") },
                    icon = { Icon(ScoreBoardIcons.BracketTree, contentDescription = "Brackets", modifier = Modifier.size(20.dp)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("Admin") },
                    icon = { Icon(ScoreBoardIcons.Shield, contentDescription = "Admin", modifier = Modifier.size(20.dp)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CourtGreen
                )
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
                        matches = matchesList,
                        adminToken = adminToken,
                        onRefresh = { refreshData() }
                    )
                    2 -> BracketsScreen(bracketsList = bracketsList)
                    3 -> AdminScreen(
                        adminToken = adminToken,
                        email = loginEmail,
                        onEmailChange = { loginEmail = it },
                        password = loginPassword,
                        onPasswordChange = { loginPassword = it },
                        error = loginError,
                        healthInfo = healthInfo,
                        matches = matchesList,
                        sports = sportsList,
                        onLogin = {
                            coroutineScope.launch {
                                try {
                                    val res = RetrofitClient.instance.login(mapOf("email" to loginEmail, "password" to loginPassword))
                                    adminToken = res.accessToken
                                    loginError = null
                                } catch (e: Exception) {
                                    loginError = "Login failed: ${e.message}"
                                }
                            }
                        },
                        onLogout = { adminToken = null },
                        onRefreshData = { refreshData() }
                    )
                }
            }

            if (showOtaDialog && versionInfo != null) {
                AlertDialog(
                    onDismissRequest = { showOtaDialog = false },
                    title = { Text("Update Available: v${versionInfo!!.versionName}", fontWeight = FontWeight.Bold) },
                    text = { Text(versionInfo!!.releaseNotes) },
                    confirmButton = {
                        Button(
                            onClick = { showOtaDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                        ) {
                            Text("Download OTA")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOtaDialog = false }) {
                            Text("Later")
                        }
                    }
                )
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
            .padding(16.dp),
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

        if (list.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = ScoreBoardIcons.Trophy,
                            contentDescription = "Empty Leaderboard",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Tournament Results Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "There are no completed matches for the selected filters.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        } else {
            itemsIndexed(list) { index, item ->
                LeaderboardCard(rank = index + 1, item = item)
            }
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
    matches: List<MatchItem>,
    adminToken: String?,
    onRefresh: () -> Unit
) {
    var scoringMatchId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Tournament Matches",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (matches.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = ScoreBoardIcons.Calendar,
                            contentDescription = "Empty Matches",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Scheduled Matches",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No tournament matches match the selected criteria.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
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

    if (scoringMatchId != null) {
        val match = matches.find { it.id == scoringMatchId }
        val sport = sports.find { it.id == match?.sportId }
        val sportType = sport?.type ?: "generic"

        when (sportType) {
            "cricket" -> CricketScoringDialog(
                match = match!!,
                adminToken = adminToken,
                onDismiss = { scoringMatchId = null; onRefresh() }
            )
            "football" -> FootballScoringDialog(
                match = match!!,
                adminToken = adminToken,
                onDismiss = { scoringMatchId = null; onRefresh() }
            )
            "basketball" -> BasketballScoringDialog(
                match = match!!,
                adminToken = adminToken,
                onDismiss = { scoringMatchId = null; onRefresh() }
            )
            else -> GenericScoringDialog(
                match = match!!,
                adminToken = adminToken,
                onDismiss = { scoringMatchId = null; onRefresh() }
            )
        }
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
                    modifier = Modifier.fillMaxWidth(),
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
fun BracketsScreen(bracketsList: List<BracketItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Tie Sheet / Brackets",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (bracketsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = ScoreBoardIcons.BracketTree,
                            contentDescription = "Empty Brackets",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Brackets Generated Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Generate tournament brackets from the Admin Dashboard.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
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
}

@Composable
fun AdminScreen(
    adminToken: String?,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String?,
    healthInfo: HealthInfo?,
    matches: List<MatchItem>,
    sports: List<Sport>,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRefreshData: () -> Unit
) {
    var showCsvImport by remember { mutableStateOf(false) }
    var showAddSport by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Admin Management Center",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (adminToken == null) {
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Text("Sign In")
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            Text("Admin Authenticated", fontWeight = FontWeight.Bold, color = CourtGreen)
                            Button(
                                onClick = onLogout,
                                shape = ComponentCornerRadius,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Sign Out")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val dbStatus = if (healthInfo?.supabaseConnected == true || healthInfo?.mode == "supabase") {
                            "Database: Connected to Supabase Postgres DB"
                        } else {
                            "Database: Using Development In-Memory Mock DB"
                        }
                        Text(dbStatus, style = MonoLabelStyle.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showAddSport = true },
                        modifier = Modifier.weight(1f),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Icon(ScoreBoardIcons.Plus, contentDescription = "Add Sport", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Sport")
                    }

                    Button(
                        onClick = { showCsvImport = true },
                        modifier = Modifier.weight(1f),
                        shape = ComponentCornerRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
                    ) {
                        Icon(ScoreBoardIcons.Upload, contentDescription = "CSV Import", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import CSV")
                    }
                }
            }
        }
    }

    if (showCsvImport) {
        CsvImportDialog(
            adminToken = adminToken,
            onDismiss = { showCsvImport = false; onRefreshData() }
        )
    }

    if (showAddSport) {
        AddSportDialog(
            adminToken = adminToken,
            onDismiss = { showAddSport = false; onRefreshData() }
        )
    }
}

// SCORING DIALOG COMPOSABLES FOR CRICKET, FOOTBALL, BASKETBALL, GENERIC
@Composable
fun CricketScoringDialog(
    match: MatchItem,
    adminToken: String?,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var r1 by remember { mutableStateOf("0") }
    var w1 by remember { mutableStateOf("0") }
    var o1 by remember { mutableStateOf("20.0") }
    var r2 by remember { mutableStateOf("0") }
    var w2 by remember { mutableStateOf("0") }
    var o2 by remember { mutableStateOf("20.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cricket Scoring", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LIVE SCOREBOARD PREVIEW", style = MonoLabelStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary))
                        Text(
                            text = "$r1/$w1 vs $r2/$w2",
                            style = DisplayScoreStyle.copy(fontSize = 36.sp, color = CourtGreen)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Innings 1", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = r1, onValueChange = { r1 = it }, label = { Text("Runs") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = w1, onValueChange = { w1 = it }, label = { Text("Wickets") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = o1, onValueChange = { o1 = it }, label = { Text("Overs") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Innings 2", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = r2, onValueChange = { r2 = it }, label = { Text("Runs") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = w2, onValueChange = { w2 = it }, label = { Text("Wickets") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = o2, onValueChange = { o2 = it }, label = { Text("Overs") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            onDismiss()
                        } catch (_: Exception) {
                            onDismiss()
                        }
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

@Composable
fun FootballScoringDialog(
    match: MatchItem,
    adminToken: String?,
    onDismiss: () -> Unit
) {
    var gA by remember { mutableStateOf("0") }
    var gB by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Football Scoring", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RUNNING SCOREBOARD", style = MonoLabelStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary))
                        Text(
                            text = "$gA - $gB",
                            style = DisplayScoreStyle.copy(fontSize = 36.sp, color = CourtGreen)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = gA, onValueChange = { gA = it }, label = { Text("Team A Goals") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = gB, onValueChange = { gB = it }, label = { Text("Team B Goals") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("CARD EVENTS (Square Markers)", style = MonoLabelStyle)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 16.dp).background(AmberWarning))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yellow Card", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp, 16.dp).background(ErrorLight))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Red Card", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)) {
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
    adminToken: String?,
    onDismiss: () -> Unit
) {
    var q1a by remember { mutableStateOf("0") }
    var q1b by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Basketball Scoring", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("PER-QUARTER ENTRY & FOULS", style = MonoLabelStyle)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = q1a, onValueChange = { q1a = it }, label = { Text("Q1 Team A") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = q1b, onValueChange = { q1b = it }, label = { Text("Q1 Team B") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)) {
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
    adminToken: String?,
    onDismiss: () -> Unit
) {
    var sA by remember { mutableStateOf("0") }
    var sB by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generic Sport Scoring", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = sA, onValueChange = { sA = it }, label = { Text("Team A Result") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = sB, onValueChange = { sB = it }, label = { Text("Team B Result") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)) {
                Text("Save Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CsvImportDialog(
    adminToken: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Player & Team CSV Import", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("CSV columns: player_name, team_name, sport_name, grade, level", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
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
                        Text("Select CSV File to Import", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AddSportDialog(
    adminToken: String?,
    onDismiss: () -> Unit
) {
    var sportName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Sport", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = sportName,
                    onValueChange = { sportName = it },
                    label = { Text("Sport Name", style = MonoLabelStyle) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)) {
                Text("Create Sport")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
