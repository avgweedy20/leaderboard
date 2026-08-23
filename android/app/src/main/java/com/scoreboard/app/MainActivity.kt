package com.scoreboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreboard.app.models.BracketItem
import com.scoreboard.app.models.HealthInfo
import com.scoreboard.app.models.LeaderboardItem
import com.scoreboard.app.models.MatchItem
import com.scoreboard.app.models.VersionInfo
import com.scoreboard.app.network.RetrofitClient
import com.scoreboard.app.ui.theme.ScoreBoardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScoreBoardTheme {
                ScoreBoardMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreBoardMainScreen() {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
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

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                healthInfo = RetrofitClient.instance.getHealth()
                leaderboardList = RetrofitClient.instance.getLeaderboard()
                matchesList = RetrofitClient.instance.getMatches()
                bracketsList = RetrofitClient.instance.getBrackets()
                versionInfo = RetrofitClient.instance.getVersion()
                if (versionInfo != null && versionInfo!!.versionCode > 1) {
                    showOtaDialog = true
                }
            } catch (e: Exception) {
                leaderboardList = listOf(
                    LeaderboardItem("t1", "Lions", "s1", "Cricket", "HS", 5, 4, 0, 1, 12),
                    LeaderboardItem("t2", "Eagles", "s2", "Football", "HS", 5, 3, 1, 1, 10)
                )
                bracketsList = listOf(
                    BracketItem("b1", "s1", "HS", "single_elimination")
                )
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ScoreBoard Live", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val modeText = if (healthInfo?.supabaseConnected == true || healthInfo?.mode == "supabase") {
                            "Connected: Supabase Postgres DB"
                        } else {
                            "Development: In-Memory Mock DB"
                        }
                        Text(modeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Leaderboard") },
                    icon = { Text("#") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Matches") },
                    icon = { Text("M") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Brackets") },
                    icon = { Text("B") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("Admin") },
                    icon = { Text("A") }
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (selectedTab) {
                    0 -> LeaderboardScreen(leaderboardList)
                    1 -> MatchesScreen(matchesList)
                    2 -> BracketsScreen(bracketsList)
                    3 -> AdminScreen(
                        adminToken = adminToken,
                        email = loginEmail,
                        onEmailChange = { loginEmail = it },
                        password = loginPassword,
                        onPasswordChange = { loginPassword = it },
                        error = loginError,
                        healthInfo = healthInfo,
                        matches = matchesList,
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
                        onLogout = { adminToken = null }
                    )
                }
            }

            if (showOtaDialog && versionInfo != null) {
                AlertDialog(
                    onDismissRequest = { showOtaDialog = false },
                    title = { Text("Update Available: v${versionInfo!!.versionName}") },
                    text = { Text(versionInfo!!.releaseNotes) },
                    confirmButton = {
                        Button(onClick = { showOtaDialog = false }) {
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

@Composable
fun LeaderboardScreen(list: List<LeaderboardItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Live Leaderboard",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        itemsIndexed(list) { index, item ->
            LeaderboardCard(rank = index + 1, item = item)
        }
    }
}

@Composable
fun MatchesScreen(matches: List<MatchItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Tournament Matches",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (matches.isEmpty()) {
            item {
                Text(
                    text = "No scheduled matches found.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            itemsIndexed(matches) { _, item ->
                MatchCard(item = item)
            }
        }
    }
}

@Composable
fun BracketsScreen(brackets: List<BracketItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Tie Sheet / Brackets",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (brackets.isEmpty()) {
            item {
                Text(
                    text = "No brackets generated yet.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            itemsIndexed(brackets) { _, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (item.type == "single_elimination") "Single Elimination Bracket" else "Round Robin Bracket",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Level: ${item.level}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
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
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Admin Management",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (adminToken == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Admin Login", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign In")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Admin Authenticated", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    val dbStatus = if (healthInfo?.supabaseConnected == true || healthInfo?.mode == "supabase") {
                        "Database: Connected to Supabase Postgres DB"
                    } else {
                        "Database: Using Development In-Memory Mock DB"
                    }
                    Text(dbStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Admin Write Access Enabled", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Matches to Score (${matches.size}):", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    matches.forEach { m ->
                        Text("• ${m.roundInfo ?: "Match"} (${m.level}) - ${m.status}", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign Out")
                    }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#$rank",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
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
                    Text(
                        text = "${item.sportName} • ${item.level}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                text = "${item.points} pts",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MatchCard(item: MatchItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = item.level, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    text = item.status.uppercase(),
                    color = if (item.status == "completed") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.roundInfo ?: "Match", fontWeight = FontWeight.Bold)
            if (item.scoreSummary != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Score: ${item.scoreSummary}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
