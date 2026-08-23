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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreboard.app.models.*
import com.scoreboard.app.network.SupabaseRepository
import com.scoreboard.app.ui.ScoreBoardIcons
import com.scoreboard.app.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var sportsList by remember { mutableStateOf<List<Sport>>(emptyList()) }
    var leaderboardList by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var matchesList by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var bracketsList by remember { mutableStateOf<List<BracketItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var adminToken by remember { mutableStateOf<String?>(null) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    var selectedSportId by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("ALL") }

    fun triggerError(msg: String) {
        errorMessage = msg
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                errorMessage = null
            } catch (e: Exception) {
                val err = "Database Connection Error: Missing credentials or network failure"
                sportsList = emptyList()
                leaderboardList = emptyList()
                matchesList = emptyList()
                bracketsList = emptyList()
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
                            Text("ScoreBoard Live", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val modeText = if (SupabaseRepository.isConfigured) {
                                "Connected: Supabase Postgres DB"
                            } else {
                                "Disconnected: Supabase Credentials Missing"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            errorMessage?.let { msg ->
                ErrorBanner(
                    message = msg,
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
                            sports = sportsList,
                            onLogin = {
                                coroutineScope.launch {
                                    try {
                                        adminToken = SupabaseRepository.login(loginEmail, loginPassword)
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
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = ComponentCornerRadius,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable { onCopy() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
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
                    text = "Tap to copy error text",
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
            // Grouped per-sport view for ALL SPORTS
            val grouped = list.groupBy { it.sportName }
            if (grouped.isEmpty()) {
                item {
                    EmptyLeaderboardCard()
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
                    EmptyLeaderboardCard()
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
fun EmptyLeaderboardCard() {
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
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
                    adminToken = adminToken
                )
            }
        }
    }
}

@Composable
fun MatchCard(
    item: MatchItem,
    adminToken: String?
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
        }
    }
}

@Composable
fun BracketsScreen(bracketsList: List<BracketItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
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
    sports: List<Sport>,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onRefreshData: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
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
        }
    }
}
