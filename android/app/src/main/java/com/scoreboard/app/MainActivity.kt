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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreboard.app.models.*
import com.scoreboard.app.network.ApiException
import com.scoreboard.app.network.ApiRepository
import com.scoreboard.app.ui.*
import com.scoreboard.app.ui.theme.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val Context.dataStore by preferencesDataStore(name = "dss_prefs")
val THEME_KEY = stringPreferencesKey("theme_mode")
val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
val AUTH_EXPIRY_KEY = stringPreferencesKey("auth_expires_at")
val AUTH_ROLE_KEY = stringPreferencesKey("auth_role")

enum class ThemeMode { LIGHT, DARK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initial = runBlocking {
            val prefs = dataStore.data.first()
            val theme = try {
                ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.DARK.name)
            } catch (_: Exception) {
                ThemeMode.DARK
            }
            val token = prefs[AUTH_TOKEN_KEY]
            val expiresAt = prefs[AUTH_EXPIRY_KEY]?.toLongOrNull() ?: 0L
            val role = prefs[AUTH_ROLE_KEY]
            if (token != null && expiresAt > System.currentTimeMillis()) {
                ApiRepository.restoreSession(token, expiresAt, role)
            }
            theme
        }

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(initial) }
            var authToken by remember { mutableStateOf(ApiRepository.adminToken) }
            val isDark = themeMode == ThemeMode.DARK

            ScoreBoardTheme(darkTheme = isDark) {
                ScoreBoardApp(
                    isDark = isDark,
                    onToggleTheme = {
                        themeMode = if (isDark) ThemeMode.LIGHT else ThemeMode.DARK
                        scope.launch { context.dataStore.edit { it[THEME_KEY] = themeMode.name } }
                    },
                    authToken = authToken,
                    onSessionChange = { token, expiresAtMillis, role ->
                        if (token == null) {
                            ApiRepository.logout()
                        } else {
                            ApiRepository.setSession(token, expiresAtMillis, role)
                        }
                        authToken = token
                        scope.launch {
                            context.dataStore.edit { prefs ->
                                if (token == null) {
                                    prefs.remove(AUTH_TOKEN_KEY)
                                    prefs.remove(AUTH_EXPIRY_KEY)
                                    prefs.remove(AUTH_ROLE_KEY)
                                } else {
                                    prefs[AUTH_TOKEN_KEY] = token
                                    prefs[AUTH_EXPIRY_KEY] = expiresAtMillis.toString()
                                    if (role != null) prefs[AUTH_ROLE_KEY] = role
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

enum class AppTab(val label: String, val icon: ImageVector) {
    OVERALL("Overall Standings", ScoreBoardIcons.Trophy),
    FIXTURES("Fixtures", ScoreBoardIcons.Calendar),
    SPORTS("Per-Sport Standings", ScoreBoardIcons.Football),
    ADMIN("Admin", ScoreBoardIcons.Shield)
}

@Composable
fun ScoreBoardApp(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    authToken: String?,
    onSessionChange: (String?, Long, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun toast(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    fun copyText(value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", value))
        toast("Copied to clipboard")
    }

    var activeTab by remember { mutableStateOf(AppTab.OVERALL) }

    var houses by remember { mutableStateOf<List<House>>(emptyList()) }
    var sports by remember { mutableStateOf<List<Sport>>(emptyList()) }
    var squads by remember { mutableStateOf<List<Team>>(emptyList()) }
    var players by remember { mutableStateOf<List<Player>>(emptyList()) }
    var matches by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var overall by remember { mutableStateOf<List<HouseOverallStanding>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }

    var baseLoaded by remember { mutableStateOf(false) }
    var baseError by remember { mutableStateOf<String?>(null) }

    var publicLoading by remember { mutableStateOf(true) }
    var publicError by remember { mutableStateOf<String?>(null) }

    var selectedSportId by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("") }
    var selectedOverallGender by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("") }

    var showLogin by remember { mutableStateOf(false) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    var adminRole by remember { mutableStateOf(ApiRepository.adminRole) }

    var editingMatch by remember { mutableStateOf<MatchItem?>(null) }
    var scoreA by remember { mutableStateOf("") }
    var scoreB by remember { mutableStateOf("") }
    var scoreBusy by remember { mutableStateOf(false) }
    var scoreError by remember { mutableStateOf<String?>(null) }

    fun loadPublic() {
        publicLoading = true
        publicError = null
        scope.launch {
            try {
                val sportQ = selectedSportId.ifEmpty { null }
                val genderQ = selectedGender.ifEmpty { null }
                overall = ApiRepository.getOverallStandings(selectedOverallGender.ifEmpty { null })
                leaderboard = ApiRepository.getLeaderboard(sportQ, genderQ)
                matches = ApiRepository.getMatches(sportQ, genderQ)
            } catch (e: ApiException) {
                publicError = e.message
            } catch (e: Exception) {
                publicError = e.message ?: "Failed to load standings"
            } finally {
                publicLoading = false
            }
        }
    }

    fun loadBase() {
        scope.launch {
            baseError = null
            try {
                houses = ApiRepository.getHouses()
                sports = ApiRepository.getSports()
                squads = ApiRepository.getTeams()
                players = ApiRepository.getPlayers()
            } catch (e: ApiException) {
                baseError = e.message
            } catch (e: Exception) {
                baseError = e.message ?: "Failed to load base data"
            } finally {
                baseLoaded = true
            }
        }
    }

    fun handleLogin() {
        if (loginEmail.isBlank() || loginPassword.isBlank()) {
            loginError = "Email and password required"
            return
        }
        loginBusy = true
        loginError = null
        scope.launch {
            try {
                val res = ApiRepository.login(loginEmail.trim(), loginPassword)
                val expiresAt = System.currentTimeMillis() + (res.expiresIn * 1000)
                val role = res.user?.role
                onSessionChange(res.accessToken, expiresAt, role)
                adminRole = role
                toast("Admin authenticated!")
                showLogin = false
                loginPassword = ""
                activeTab = AppTab.ADMIN
            } catch (e: ApiException) {
                loginError = e.message ?: "Login failed"
            } catch (e: Exception) {
                loginError = e.message ?: "Login failed"
            } finally {
                loginBusy = false
            }
        }
    }

    fun handleLogout() {
        onSessionChange(null, 0, null)
        adminRole = null
        toast("Signed out.")
        if (activeTab == AppTab.ADMIN) activeTab = AppTab.OVERALL
    }

    fun saveScore(match: MatchItem) {
        scoreBusy = true
        scoreError = null
        scope.launch {
            try {
                ApiRepository.updateMatch(
                    id = match.id,
                    teamAId = match.teamAId,
                    teamBId = match.teamBId,
                    stage = match.stage,
                    roundInfo = match.roundInfo,
                    winnerTeamId = match.winnerTeamId,
                    isDraw = match.isDraw,
                    scoreTeamA = scoreA.toIntOrNull() ?: 0,
                    scoreTeamB = scoreB.toIntOrNull() ?: 0,
                    scoreSummary = null,
                    status = "completed"
                )
                toast("Score saved")
                editingMatch = null
                loadPublic()
            } catch (e: ApiException) {
                scoreError = e.message
            } catch (e: Exception) {
                scoreError = e.message ?: "Save failed"
            } finally {
                scoreBusy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!baseLoaded) loadBase()
    }

    LaunchedEffect(selectedSportId, selectedGender, selectedOverallGender) {
        loadPublic()
    }

    LaunchedEffect(editingMatch) {
        editingMatch?.let { m ->
            scoreA = m.scoreTeamA.toString()
            scoreB = m.scoreTeamB.toString()
            scoreError = null
        }
    }

    LaunchedEffect(authToken) {
        if (authToken != null) {
            while (true) {
                if (!ApiRepository.isAuthenticated) {
                    handleLogout()
                    break
                }
                delay(30_000)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AppHeader(
                isDark = isDark,
                isLoggedIn = authToken != null,
                onToggleTheme = onToggleTheme,
                onAuthClick = { if (authToken == null) showLogin = true else handleLogout() }
            )
            AppTabBar(
                isLoggedIn = authToken != null,
                selected = activeTab,
                onSelect = { activeTab = it }
            )
            if (baseError != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ErrorState(
                        message = baseError ?: "",
                        onRetry = { loadBase() },
                        onCopy = { copyText(baseError ?: "") }
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (activeTab) {
                    AppTab.OVERALL -> OverallScreen(
                        standings = overall,
                        selectedGender = selectedOverallGender,
                        onSelectGender = { selectedOverallGender = it },
                        loading = publicLoading && overall.isEmpty(),
                        error = publicError,
                        onRetry = { loadPublic() },
                        onCopy = { copyText(it) }
                    )
                    AppTab.FIXTURES -> FixturesScreen(
                        sports = sports,
                        squads = squads,
                        houses = houses,
                        matches = matches,
                        selectedSportId = selectedSportId,
                        onSelectSport = { selectedSportId = it },
                        selectedGender = selectedGender,
                        onSelectGender = { selectedGender = it },
                        selectedStatus = selectedStatus,
                        onSelectStatus = { selectedStatus = it },
                        isAdmin = authToken != null,
                        onEditMatch = { editingMatch = it },
                        onRefresh = { loadPublic() },
                        loading = publicLoading && matches.isEmpty(),
                        error = publicError,
                        onCopy = { copyText(it) }
                    )
                    AppTab.SPORTS -> PerSportScreen(
                        sports = sports,
                        selectedSportId = selectedSportId,
                        onSelectSport = { selectedSportId = it },
                        selectedGender = selectedGender,
                        onSelectGender = { selectedGender = it },
                        standings = leaderboard,
                        loading = publicLoading && leaderboard.isEmpty(),
                        error = publicError,
                        onRefresh = { loadPublic() },
                        onCopy = { copyText(it) }
                    )
                    AppTab.ADMIN -> AdminScreen(
                        houses = houses,
                        sports = sports,
                        squads = squads,
                        players = players,
                        matches = matches,
                        isSuperAdmin = adminRole == "superadmin",
                        onEditMatch = { editingMatch = it },
                        onRefresh = {
                            loadBase()
                            loadPublic()
                        },
                        onToast = { toast(it) }
                    )
                }
            }
        }
    }

    if (showLogin) {
        AppDialog(title = "Admin Login", onDismiss = { showLogin = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sign in to manage squads, players, and match scores.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                AppTextField(value = loginEmail, onValueChange = { loginEmail = it }, label = "Email", placeholder = "admin@scoreboard.com")
                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = { loginPassword = it },
                    label = { Text("Password", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                loginError?.let {
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WebButton("Cancel", onClick = { showLogin = false }, secondary = true, enabled = !loginBusy)
                    Spacer(Modifier.width(8.dp))
                    WebButton(
                        if (loginBusy) "Signing in…" else "Sign In",
                        onClick = { handleLogin() },
                        enabled = !loginBusy
                    )
                }
            }
        }
    }

    editingMatch?.let { match ->
        AppDialog(title = "Update Score", onDismiss = { editingMatch = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${resolveTeamName(match.teamAId, squads, houses)} vs ${resolveTeamName(match.teamBId, squads, houses)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                scoreError?.let {
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(value = scoreA, onValueChange = { scoreA = it }, Modifier.weight(1f), label = "Team A")
                    Text("–", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    NumberField(value = scoreB, onValueChange = { scoreB = it }, Modifier.weight(1f), label = "Team B")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WebButton("Cancel", onClick = { editingMatch = null }, secondary = true, enabled = !scoreBusy)
                    Spacer(Modifier.width(8.dp))
                    WebButton(
                        if (scoreBusy) "Saving…" else "Save Score",
                        onClick = { saveScore(match) },
                        enabled = !scoreBusy
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    isDark: Boolean,
    isLoggedIn: Boolean,
    onToggleTheme: () -> Unit,
    onAuthClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                ScoreBoardIcons.Trophy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "DSS League Games",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onToggleTheme() }
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (isDark) ScoreBoardIcons.Sun else ScoreBoardIcons.Moon,
                    contentDescription = "Toggle theme",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAuthClick() }
        ) {
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (isLoggedIn) ScoreBoardIcons.Logout else ScoreBoardIcons.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (isLoggedIn) "Sign Out" else "Admin Login",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AppTabBar(
    isLoggedIn: Boolean,
    selected: AppTab,
    onSelect: (AppTab) -> Unit
) {
    val tabs = if (isLoggedIn) AppTab.entries else AppTab.entries.filter { it != AppTab.ADMIN }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(1.dp))
                        Text(
                            tab.label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.tertiary,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
        HorizontalRule()
    }
}

// ---------------------------------------------------------------------------
// Overall House Standings
// ---------------------------------------------------------------------------

@Composable
fun OverallScreen(
    standings: List<HouseOverallStanding>,
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageHeader("Overall House Standings", "Season points across all sports — running after each completed match.")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("GENDER")
                    FilterChipRow(
                        options = listOf("", "Girls", "Boys"),
                        selected = selectedGender,
                        onSelect = onSelectGender,
                        labelOf = { value -> if (value.isEmpty()) "All" else value }
                    )
                }
            }
        }
        when {
            loading -> item(span = { GridItemSpan(maxLineSpan) }) { SkeletonTable() }
            error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(error, onRetry, { onCopy(error) }, Modifier)
            }
            standings.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("No standings yet", "Scores will appear after league matches are completed.", Modifier)
            }
            else -> {
                items(standings, key = { it.houseId }) { house ->
                    HouseHeroCard(house)
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
                item(span = { GridItemSpan(maxLineSpan) }) { OverallTable(standings) }
            }
        }
    }
}

@Composable
fun HouseHeroCard(standing: HouseOverallStanding) {
    val color = parseHexColor(standing.colorHex)
    val isLeader = standing.rank == 1
    val borderColor = if (isLeader) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isLeader) 1.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = color.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
                ) {
                    Text(
                        "RANK #${standing.rank}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = color
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    standing.houseName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${standing.totalSquads} squad${if (standing.totalSquads != 1) "s" else ""} registered",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color.Transparent,
                    border = null
                ) {
                    Column {
                        SectionLabel("TOTAL POINTS")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${standing.totalPoints}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${standing.totalWins}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WinGreen,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
                    Text(" - ", fontSize = 15.sp, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                    Text(
                        "${standing.totalDraws}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
                    Text(" - ", fontSize = 15.sp, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                    Text(
                        "${standing.totalLosses}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LossRed,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}

@Composable
fun OverallTable(standings: List<HouseOverallStanding>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(474.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableLabel("Rank", Modifier.width(52.dp))
                    TableLabel("House", Modifier.width(150.dp))
                    TableLabel("Squads", Modifier.width(56.dp), align = TextAlign.Center)
                    TableLabel("Played", Modifier.width(48.dp), align = TextAlign.Center)
                    TableLabel("W", Modifier.width(38.dp), align = TextAlign.Center)
                    TableLabel("D", Modifier.width(38.dp), align = TextAlign.Center)
                    TableLabel("L", Modifier.width(38.dp), align = TextAlign.Center)
                    TableLabel("Pts", Modifier.width(60.dp), align = TextAlign.End)
                }
                HorizontalRule()
                standings.forEach { house ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val color = parseHexColor(house.colorHex)
                        Box(
                            Modifier
                                .width(52.dp)
                                .height(22.dp)
                                .background(color, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "#${house.rank}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                            )
                        }
                        Row(Modifier.width(150.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                house.houseName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TableCell(house.totalSquads.toString(), Modifier.width(56.dp), align = TextAlign.Center, number = true)
                        TableCell(house.matchesPlayed.toString(), Modifier.width(48.dp), align = TextAlign.Center, number = true)
                        TableCell(house.totalWins.toString(), Modifier.width(38.dp), align = TextAlign.Center, number = true, color = WinGreen, bold = true)
                        TableCell(house.totalDraws.toString(), Modifier.width(38.dp), align = TextAlign.Center, number = true, color = MaterialTheme.colorScheme.tertiary)
                        TableCell(house.totalLosses.toString(), Modifier.width(38.dp), align = TextAlign.Center, number = true, color = LossRed, bold = true)
                        TableCell(house.totalPoints.toString(), Modifier.width(60.dp), align = TextAlign.End, number = true, bold = true, color = color)
                    }
                    HorizontalRule()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Per-Sport Standings
// ---------------------------------------------------------------------------

@Composable
fun PerSportScreen(
    sports: List<Sport>,
    selectedSportId: String,
    onSelectSport: (String) -> Unit,
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    standings: List<LeaderboardItem>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader("Per-Sport Standings", "Rankings per sport — switch sports and genders to drill down.")
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChipRow(
                        options = listOf("") + sports.map { it.id },
                        selected = selectedSportId,
                        onSelect = onSelectSport,
                        labelOf = { value -> if (value.isEmpty()) "All Sports" else resolveSportName(value, sports) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledDropdown(
                            label = "Gender",
                            options = listOf("", "Boys", "Girls"),
                            selected = selectedGender,
                            onSelect = onSelectGender,
                            labelOf = { value -> if (value.isEmpty()) "All genders" else value },
                            modifier = Modifier.weight(1f)
                        )
                        IconBtn(ScoreBoardIcons.Refresh, "Refresh", onRefresh)
                    }
                }
            }
        }
        when {
            loading -> item { SkeletonTable() }
            error != null -> item { ErrorState(error, onRefresh, { onCopy(error) }, Modifier) }
            standings.isEmpty() -> item { EmptyState("No standings", "Standings will appear once results are entered.", Modifier) }
            else -> item { SportStatTable(standings) }
        }
    }
}

@Composable
fun SportStatTable(rows: List<LeaderboardItem>) {
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(712.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableLabel("Rank", Modifier.width(40.dp))
                TableLabel("Squad", Modifier.width(150.dp))
                TableLabel("House", Modifier.width(110.dp))
                TableLabel("Sport", Modifier.width(90.dp))
                TableLabel("Gender", Modifier.width(60.dp))
                TableLabel("P", Modifier.width(38.dp), align = TextAlign.Center)
                TableLabel("W", Modifier.width(34.dp), align = TextAlign.Center)
                TableLabel("D", Modifier.width(34.dp), align = TextAlign.Center)
                TableLabel("L", Modifier.width(34.dp), align = TextAlign.Center)
                TableLabel("Diff", Modifier.width(62.dp), align = TextAlign.Center)
                TableLabel("Pts", Modifier.width(60.dp), align = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            rows.forEach { row ->
                SportStatRow(row)
                HorizontalRule()
            }
        }
    }
}

@Composable
fun SportStatRow(row: LeaderboardItem) {
    val color = parseHexColor(row.houseColor)
    val diff = row.scoreDifference
    val diffText = if (diff > 0) "+$diff" else diff.toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(row.rank.toString(), Modifier.width(40.dp), align = TextAlign.Center, number = true, color = MaterialTheme.colorScheme.tertiary)
        TableCell(row.teamName, Modifier.width(150.dp), bold = true)
        TableCell(row.houseName ?: "House", Modifier.width(110.dp), color = color, maxLines = 1)
        TableCell(row.sportName, Modifier.width(90.dp), color = MaterialTheme.colorScheme.secondary, maxLines = 1)
        TableCell(genderLabel(row.gender), Modifier.width(60.dp), color = MaterialTheme.colorScheme.secondary)
        TableCell(row.played.toString(), Modifier.width(38.dp), align = TextAlign.Center, number = true)
        TableCell(row.wins.toString(), Modifier.width(34.dp), align = TextAlign.Center, number = true, color = WinGreen)
        TableCell(row.draws.toString(), Modifier.width(34.dp), align = TextAlign.Center, number = true, color = MaterialTheme.colorScheme.secondary)
        TableCell(row.losses.toString(), Modifier.width(34.dp), align = TextAlign.Center, number = true, color = LossRed)
        TableCell(diffText, Modifier.width(62.dp), align = TextAlign.Center, number = true, bold = true)
        TableCell(row.points.toString(), Modifier.width(60.dp), align = TextAlign.Center, number = true, bold = true, color = color)
    }
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

@Composable
fun FixturesScreen(
    sports: List<Sport>,
    squads: List<Team>,
    houses: List<House>,
    matches: List<MatchItem>,
    selectedSportId: String,
    onSelectSport: (String) -> Unit,
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    selectedStatus: String,
    onSelectStatus: (String) -> Unit,
    isAdmin: Boolean,
    onEditMatch: (MatchItem) -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
    error: String?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = remember(matches, selectedStatus) {
        if (selectedStatus.isEmpty()) matches
        else matches.filter { it.status?.equals(selectedStatus, ignoreCase = true) == true }
    }
    val groups = remember(visible, sports) { groupFixtures(visible, sports) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader("Fixtures", "All matches across the sports — updated as scores roll in.")
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChipRow(
                        options = listOf("") + sports.map { it.id },
                        selected = selectedSportId,
                        onSelect = onSelectSport,
                        labelOf = { value -> if (value.isEmpty()) "All Sports" else resolveSportName(value, sports) }
                    )
                    FilterChipRow(
                        options = listOf("", "completed", "scheduled"),
                        selected = selectedStatus,
                        onSelect = onSelectStatus,
                        labelOf = { value ->
                            when (value) {
                                "" -> "All statuses"
                                "completed" -> "FT (Completed)"
                                else -> "SCH (Scheduled)"
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledDropdown(
                            label = "Gender",
                            options = listOf("", "Boys", "Girls"),
                            selected = selectedGender,
                            onSelect = onSelectGender,
                            labelOf = { value -> if (value.isEmpty()) "All genders" else value },
                            modifier = Modifier.weight(1f)
                        )
                        IconBtn(ScoreBoardIcons.Refresh, "Refresh", onRefresh)
                    }
                }
            }
        }
        when {
            loading -> item { SkeletonTable() }
            error != null -> item { ErrorState(error, onRefresh, { onCopy(error) }, Modifier) }
            groups.isEmpty() -> item { EmptyState("No fixtures", "Schedule a match from the Admin tab to get started.", Modifier) }
            else -> groups.forEach { group ->
                item(key = "${group.sportId}-${group.gender}") { FixtureGroupHeader(group) }
                items(group.matches, key = { it.id }) { match ->
                    FixtureMatchCard(
                        match = match,
                        squads = squads,
                        houses = houses,
                        isAdmin = isAdmin,
                        onEdit = { onEditMatch(match) }
                    )
                }
            }
        }
    }
}

@Composable
fun FixtureGroupHeader(group: FixtureGroup) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            group.sportIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${group.sportName} — ${genderLabel(group.gender)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        StatusBadge("${group.matches.count { isCompleted(it) }}/${group.matches.size} played", BadgeVariant.PENDING)
    }
}

@Composable
fun FixtureMatchCard(
    match: MatchItem,
    squads: List<Team>,
    houses: List<House>,
    isAdmin: Boolean,
    onEdit: () -> Unit
) {
    val aName = resolveTeamName(match.teamAId, squads, houses)
    val bName = resolveTeamName(match.teamBId, squads, houses)
    val aColor = resolveTeamColor(match.teamAId, squads, houses)
    val bColor = resolveTeamColor(match.teamBId, squads, houses)
    val hasTeams = match.teamAId != null && match.teamBId != null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusBadge(match.stage ?: "league", BadgeVariant.STAGE)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isAdmin) {
                        IconBtn(icon = ScoreBoardIcons.Edit, contentDescription = "Edit match", onClick = onEdit)
                    }
                    StatusBadge(
                        if (isCompleted(match)) "FT" else "Scheduled",
                        if (isCompleted(match)) BadgeVariant.SUCCESS else BadgeVariant.PENDING
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                match.roundInfo ?: "League Game",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamSide(aName, aColor, Modifier.weight(1f))
                Text(
                    "VS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                TeamSide(bName, bColor, Modifier.weight(1f), alignEnd = true)
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    when {
                        !hasTeams -> Text("TBD — awaiting qualifiers", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        isCompleted(match) -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Final Score", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                match.scoreSummary ?: "${match.scoreTeamA} - ${match.scoreTeamB}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                            )
                        }
                        else -> Text("Not yet played", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}