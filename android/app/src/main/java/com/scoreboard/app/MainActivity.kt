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
import com.scoreboard.app.models.LeaderboardItem
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
    var leaderboardList by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var versionInfo by remember { mutableStateOf<VersionInfo?>(null) }
    var showOtaDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                leaderboardList = RetrofitClient.instance.getLeaderboard()
                versionInfo = RetrofitClient.instance.getVersion()
                if (versionInfo != null && versionInfo!!.versionCode > 1) {
                    showOtaDialog = true
                }
            } catch (e: Exception) {
                // Fallback mock data if server isn't reached
                leaderboardList = listOf(
                    LeaderboardItem("t1", "Lions", "s1", "Cricket", "HS", 5, 4, 0, 1, 12),
                    LeaderboardItem("t2", "Eagles", "s2", "Football", "HS", 5, 3, 1, 1, 10),
                    LeaderboardItem("t3", "Tigers", "s1", "Cricket", "HS", 5, 2, 1, 2, 7)
                )
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ScoreBoard Live", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Tournament Leaderboard",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    itemsIndexed(leaderboardList) { index, item ->
                        LeaderboardCard(rank = index + 1, item = item)
                    }
                }
            }

            // OTA Dialog
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
