package com.scoreboard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreboard.app.models.*
import com.scoreboard.app.network.ApiException
import com.scoreboard.app.network.ApiRepository
import com.scoreboard.app.ui.theme.*
import kotlinx.coroutines.launch

private sealed class DeleteTarget {
    object None : DeleteTarget()
    data class Team(val id: String) : DeleteTarget()
    data class Player(val id: String) : DeleteTarget()
    data class Players(val ids: List<String>) : DeleteTarget()
    data class Match(val id: String) : DeleteTarget()
}

@Composable
fun AdminScreen(
    houses: List<House>,
    sports: List<Sport>,
    squads: List<Team>,
    players: List<Player>,
    matches: List<MatchItem>,
    isSuperAdmin: Boolean,
    onEditMatch: (MatchItem) -> Unit,
    onRefresh: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    // ---- squad table state (mirrors web adminSquadsState) ----
    var squadSearch by remember { mutableStateOf("") }
    var squadHouseFilter by remember { mutableStateOf("") }
    var squadSportFilter by remember { mutableStateOf("") }
    var squadGenderFilter by remember { mutableStateOf("") }
    var squadSortCol by remember { mutableStateOf("name") }
    var squadSortAsc by remember { mutableStateOf(true) }
    var squadPage by remember { mutableStateOf(1) }
    var squadPageSize by remember { mutableStateOf("all") }

    // ---- player table state (mirrors web adminPlayersState) ----
    var playerSearch by remember { mutableStateOf("") }
    var playerHouseFilter by remember { mutableStateOf("") }
    var playerSportFilter by remember { mutableStateOf("") }
    var playerGradeFilter by remember { mutableStateOf("") }
    var playerGenderFilter by remember { mutableStateOf("") }
    var playerSortCol by remember { mutableStateOf("name") }
    var playerSortAsc by remember { mutableStateOf(true) }
    var playerPage by remember { mutableStateOf(1) }
    var playerPageSize by remember { mutableStateOf("all") }
    var selectedPlayerIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // ---- fixture table state (mirrors web adminFixturesState) ----
    var fixtureSearch by remember { mutableStateOf("") }
    var fixtureSportFilter by remember { mutableStateOf("") }
    var fixtureGenderFilter by remember { mutableStateOf("") }
    var fixtureStageFilter by remember { mutableStateOf("") }
    var fixtureStatusFilter by remember { mutableStateOf("") }
    var fixtureSortCol by remember { mutableStateOf("id") }
    var fixtureSortAsc by remember { mutableStateOf(true) }
    var fixturePage by remember { mutableStateOf(1) }
    var fixturePageSize by remember { mutableStateOf("all") }

    // ---- CRUD dialog state ----
    var editingSquad by remember { mutableStateOf<Team?>(null) }
    var showSquadDialog by remember { mutableStateOf(false) }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var showPlayerDialog by remember { mutableStateOf(false) }
    var showMatchDialog by remember { mutableStateOf(false) }

    // ---- superadmin state (mirrors web loadAdminAccounts / loadAdminAuditLog) ----
    var admins by remember { mutableStateOf<List<AdminAccount>>(emptyList()) }
    var adminsError by remember { mutableStateOf(false) }
    var auditEntries by remember { mutableStateOf<List<AuditEntry>>(emptyList()) }
    var auditTotal by remember { mutableStateOf(0) }
    var auditOffset by remember { mutableStateOf(0) }
    var auditReady by remember { mutableStateOf(false) }
    var showAddAdminDialog by remember { mutableStateOf(false) }
    var resetPasswordTarget by remember { mutableStateOf<String?>(null) }

    var deleteTarget by remember { mutableStateOf<DeleteTarget>(DeleteTarget.None) }

    val confirmDialogTitle = when (deleteTarget) {
        is DeleteTarget.Team -> "Confirm Delete"
        is DeleteTarget.Player -> "Confirm Delete"
        is DeleteTarget.Players -> "Confirm Bulk Delete"
        is DeleteTarget.Match -> "Confirm Delete"
        DeleteTarget.None -> ""
    }

    fun runDelete() {
        when (val target = deleteTarget) {
            is DeleteTarget.Team -> scope.launch {
                busy = true
                try {
                    ApiRepository.deleteTeam(target.id)
                    deleteTarget = DeleteTarget.None
                    onToast("Squad deleted")
                    onRefresh()
                } catch (e: ApiException) {
                    onToast(e.message ?: "Delete failed")
                } finally {
                    busy = false
                }
            }
            is DeleteTarget.Player -> scope.launch {
                busy = true
                try {
                    ApiRepository.deletePlayer(target.id)
                    deleteTarget = DeleteTarget.None
                    onToast("Player deleted")
                    onRefresh()
                } catch (e: ApiException) {
                    onToast(e.message ?: "Delete failed")
                } finally {
                    busy = false
                }
            }
            is DeleteTarget.Players -> scope.launch {
                busy = true
                try {
                    ApiRepository.bulkDeletePlayers(target.ids)
                    selectedPlayerIds = emptySet()
                    deleteTarget = DeleteTarget.None
                    onToast("Deleted ${target.ids.size} players")
                    onRefresh()
                } catch (e: ApiException) {
                    onToast(e.message ?: "Bulk delete failed")
                } finally {
                    busy = false
                }
            }
            is DeleteTarget.Match -> scope.launch {
                busy = true
                try {
                    ApiRepository.deleteMatch(target.id)
                    deleteTarget = DeleteTarget.None
                    onToast("Match deleted")
                    onRefresh()
                } catch (e: ApiException) {
                    onToast(e.message ?: "Delete failed")
                } finally {
                    busy = false
                }
            }
            DeleteTarget.None -> Unit
        }
    }

    fun loadAdmins() {
        scope.launch {
            try {
                admins = ApiRepository.getAdmins()
                adminsError = false
            } catch (e: ApiException) {
                adminsError = true
                onToast(e.message ?: "Failed to load admins")
            }
        }
    }

    fun loadAudit() {
        scope.launch {
            try {
                val res = ApiRepository.getAuditLog(offset = auditOffset, limit = 50)
                auditEntries = res.entries
                auditTotal = res.total
                auditReady = true
            } catch (e: ApiException) {
                auditReady = true
            }
        }
    }

    LaunchedEffect(isSuperAdmin) {
        if (isSuperAdmin) {
            loadAdmins()
            auditOffset = 0
            auditEntries = emptyList()
            auditReady = false
            loadAudit()
        }
    }

    // ---------- FILTERING / SORTING / PAGINATION (mirrors web client logic) ----------

    fun sortSquads(list: List<Team>): List<Team> {
        val dir = if (squadSortAsc) 1 else -1
        val sorted = when (squadSortCol) {
            "house" -> list.sortedBy { resolveHouseName(it.houseId, houses).lowercase() }
            "sport" -> list.sortedBy { resolveSportName(it.sportId, sports).lowercase() }
            "gender" -> list.sortedBy { (it.gender ?: "").lowercase() }
            else -> list.sortedBy { it.name.lowercase() }
        }
        return if (dir == 1) sorted else sorted.reversed()
    }

    val filteredSquads = remember(squads, squadSearch, squadHouseFilter, squadSportFilter, squadGenderFilter) {
        var list = squads
        if (squadSearch.isNotBlank()) {
            val q = squadSearch.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                    resolveHouseName(it.houseId, houses).lowercase().contains(q) ||
                    resolveSportName(it.sportId, sports).lowercase().contains(q)
            }
        }
        if (squadHouseFilter.isNotBlank()) list = list.filter { it.houseId == squadHouseFilter }
        if (squadSportFilter.isNotBlank()) list = list.filter { it.sportId == squadSportFilter }
        if (squadGenderFilter.isNotBlank()) list = list.filter { it.gender == squadGenderFilter }
        list
    }

    val visibleSquads = remember(filteredSquads, squadSortCol, squadSortAsc, squadPage, squadPageSize) {
        val sorted = sortSquads(filteredSquads)
        if (squadPageSize == "all") sorted
        else {
            val size = squadPageSize.toIntOrNull() ?: sorted.size
            val start = (squadPage - 1) * size
            if (start >= sorted.size) emptyList()
            else sorted.subList(start, minOf(start + size, sorted.size))
        }
    }

    fun sortPlayers(list: List<Player>): List<Player> {
        val dir = if (playerSortAsc) 1 else -1
        return when (playerSortCol) {
            "roll" -> {
                val sorted = list.sortedBy { it.rollNumber?.toIntOrNull() ?: 0 }
                if (dir == 1) sorted else sorted.reversed()
            }
            "squad" -> {
                val sorted = list.sortedBy { resolveTeamName(it.teamId, squads, houses).lowercase() }
                if (dir == 1) sorted else sorted.reversed()
            }
            "grade" -> {
                val sorted = list.sortedBy { (it.grade ?: "").lowercase() }
                if (dir == 1) sorted else sorted.reversed()
            }
            else -> {
                val sorted = list.sortedBy { it.name.lowercase() }
                if (dir == 1) sorted else sorted.reversed()
            }
        }
    }

    val filteredPlayers = remember(players, playerSearch, playerHouseFilter, playerSportFilter, playerGradeFilter, playerGenderFilter) {
        var list = players
        if (playerSearch.isNotBlank()) {
            val q = playerSearch.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                    (it.rollNumber ?: "").lowercase().contains(q) ||
                    (it.section ?: "").lowercase().contains(q) ||
                    resolveTeamName(it.teamId, squads, houses).lowercase().contains(q)
            }
        }
        if (playerHouseFilter.isNotBlank()) {
            list = list.filter { p -> squads.firstOrNull { it.id == p.teamId }?.houseId == playerHouseFilter }
        }
        if (playerSportFilter.isNotBlank()) {
            list = list.filter { p -> squads.firstOrNull { it.id == p.teamId }?.sportId == playerSportFilter }
        }
        if (playerGradeFilter.isNotBlank()) list = list.filter { (it.grade ?: "") == playerGradeFilter }
        if (playerGenderFilter.isNotBlank()) list = list.filter { it.gender == playerGenderFilter }
        list
    }

    val visiblePlayers = remember(filteredPlayers, playerSortCol, playerSortAsc, playerPage, playerPageSize) {
        val sorted = sortPlayers(filteredPlayers)
        if (playerPageSize == "all") sorted
        else {
            val size = playerPageSize.toIntOrNull() ?: sorted.size
            val start = (playerPage - 1) * size
            if (start >= sorted.size) emptyList()
            else sorted.subList(start, minOf(start + size, sorted.size))
        }
    }

    val gradeOptions = remember(players) {
        players.map { (it.grade ?: "").toString() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun sortFixtures(list: List<MatchItem>): List<MatchItem> {
        val dir = if (fixtureSortAsc) 1 else -1
        val sorted = when (fixtureSortCol) {
            "stage" -> list.sortedBy { (it.stage ?: "").lowercase() }
            "status" -> list.sortedBy { (it.status ?: "").lowercase() }
            "sport" -> list.sortedBy { resolveSportName(it.sportId, sports).lowercase() }
            else -> list.sortedBy { it.id.lowercase() }
        }
        return if (dir == 1) sorted else sorted.reversed()
    }

    val filteredMatches = remember(matches, fixtureSearch, fixtureSportFilter, fixtureGenderFilter, fixtureStageFilter, fixtureStatusFilter) {
        var list = matches
        if (fixtureSearch.isNotBlank()) {
            val q = fixtureSearch.trim().lowercase()
            list = list.filter {
                resolveTeamName(it.teamAId, squads, houses).lowercase().contains(q) ||
                    resolveTeamName(it.teamBId, squads, houses).lowercase().contains(q) ||
                    resolveSportName(it.sportId, sports).lowercase().contains(q) ||
                    (it.roundInfo ?: "").lowercase().contains(q)
            }
        }
        if (fixtureSportFilter.isNotBlank()) list = list.filter { it.sportId == fixtureSportFilter }
        if (fixtureGenderFilter.isNotBlank()) list = list.filter { it.gender == fixtureGenderFilter }
        if (fixtureStageFilter.isNotBlank()) list = list.filter { it.stage == fixtureStageFilter }
        if (fixtureStatusFilter.isNotBlank()) list = list.filter { it.status == fixtureStatusFilter }
        list
    }

    val visibleMatches = remember(filteredMatches, fixtureSortCol, fixtureSortAsc, fixturePage, fixturePageSize) {
        val sorted = sortFixtures(filteredMatches)
        if (fixturePageSize == "all") sorted
        else {
            val size = fixturePageSize.toIntOrNull() ?: sorted.size
            val start = (fixturePage - 1) * size
            if (start >= sorted.size) emptyList()
            else sorted.subList(start, minOf(start + size, sorted.size))
        }
    }

    val allDisplayedSelected = remember(visiblePlayers, selectedPlayerIds) {
        visiblePlayers.isNotEmpty() && visiblePlayers.all { selectedPlayerIds.contains(it.id) }
    }

    val genders = listOf("Boys", "Girls")
    val allSportsOptions = listOf("") + sports.map { it.id }
    val allHousesOptions = listOf("") + houses.map { it.id }
    val stageOptions = listOf("", "league", "semifinal", "final")
    val statusOptions = listOf("", "completed", "scheduled")
    val genderOptions = listOf("") + genders

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader("Admin Control", "Manage house squads, registered players, and match scores.")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WebButton("New Match", onClick = { showMatchDialog = true }, icon = ScoreBoardIcons.Plus, secondary = true)
                WebButton("Add Squad", onClick = {
                    editingSquad = null
                    showSquadDialog = true
                }, icon = ScoreBoardIcons.Plus)
                WebButton("Add Player", onClick = {
                    editingPlayer = null
                    showPlayerDialog = true
                }, icon = ScoreBoardIcons.Plus)
            }
        }

        if (isSuperAdmin) {
            item {
                SectionDivider(title = "Admin Accounts", count = admins.size, dotColor = MahakaliPurple)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WebButton("Add Admin", onClick = { showAddAdminDialog = true }, icon = ScoreBoardIcons.Plus, secondary = true)
                }
            }
            if (adminsError) {
                item {
                    ErrorState("Failed to load admin accounts.", onRetry = { loadAdmins() }, onCopy = {})
                }
            } else if (admins.isEmpty()) {
                item { EmptyState("No admins registered yet", "", Modifier) }
            } else {
                items(admins, key = { it.email }) { admin ->
                    AdminAccountRow(
                        account = admin,
                        onResetPassword = { resetPasswordTarget = admin.email },
                        onRemove = {
                            onToast("Removing ${admin.email}…")
                            scope.launch {
                                busy = true
                                try {
                                    ApiRepository.removeAdmin(admin.email)
                                    onToast("${admin.email} removed")
                                    loadAdmins()
                                } catch (e: ApiException) {
                                    onToast(e.message ?: "Failed to remove admin")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
                item {
                    PaginationBar(
                        totalItems = admins.size,
                        page = 1,
                        totalPages = 1,
                        onPrev = {}, onNext = {}, pageSize = "all", onPageSize = {}
                    )
                }
            }
        }

        item {
            SectionDivider(title = "House Squad Roster", count = squads.size, dotColor = KarnaliGreen)
        }

        item {
            FilterPanel {
                AppTextField(value = squadSearch, onValueChange = { squadSearch = it; squadPage = 1 }, placeholder = "Search squads, houses, sports…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = "House",
                        options = listOf("") + houses.map { it.id },
                        selected = squadHouseFilter,
                        onSelect = { squadHouseFilter = it; squadPage = 1 },
                        labelOf = { id -> if (id.isEmpty()) "All Houses" else resolveHouseName(id, houses) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Sport",
                        options = allSportsOptions,
                        selected = squadSportFilter,
                        onSelect = { squadSportFilter = it; squadPage = 1 },
                        labelOf = { id -> if (id.isEmpty()) "All Sports" else resolveSportName(id, sports) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Gender",
                        options = genderOptions,
                        selected = squadGenderFilter,
                        onSelect = { squadGenderFilter = it; squadPage = 1 },
                        labelOf = { v -> if (v.isEmpty()) "All Genders" else v },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortSelect(
                        label = "Sort",
                        options = listOf("name", "house", "sport", "gender"),
                        selected = squadSortCol,
                        descending = !squadSortAsc,
                        onSelect = { col ->
                            if (squadSortCol == col) squadSortAsc = !squadSortAsc
                            else { squadSortCol = col; squadSortAsc = true }
                        },
                        labelOf = { it.replaceFirstChar { c -> c.uppercase() } },
                        modifier = Modifier.weight(1f)
                    )
                    ResultCount("${filteredSquads.size} result${if (filteredSquads.size != 1) "s" else ""}")
                }
            }
        }

        if (filteredSquads.isEmpty()) {
            item { EmptyState("No squads", "Tap “Add Squad” to register your first house squad.", Modifier) }
        } else {
            items(visibleSquads, key = { it.id }) { team ->
                SquadRow(
                    team = team,
                    houses = houses,
                    sports = sports,
                    onEdit = {
                        editingSquad = team
                        showSquadDialog = true
                    },
                    onDelete = { deleteTarget = DeleteTarget.Team(team.id) }
                )
            }
            item {
                PaginationBar(
                    totalItems = filteredSquads.size,
                    page = squadPage,
                    totalPages = totalPagesOf(filteredSquads.size, squadPageSize),
                    onPrev = { squadPage = (squadPage - 1).coerceAtLeast(1) },
                    onNext = { squadPage = (squadPage + 1).coerceAtMost(totalPagesOf(filteredSquads.size, squadPageSize)) },
                    pageSize = squadPageSize,
                    onPageSize = { squadPageSize = it; squadPage = 1 }
                )
            }
        }

        item {
            SectionDivider(title = "Registered Players", count = players.size, dotColor = KoshiBlue)
        }

        item {
            FilterPanel {
                AppTextField(value = playerSearch, onValueChange = { playerSearch = it; playerPage = 1 }, placeholder = "Search name, roll #, squad…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = "House",
                        options = listOf("") + houses.map { it.id },
                        selected = playerHouseFilter,
                        onSelect = { playerHouseFilter = it; playerPage = 1 },
                        labelOf = { id -> if (id.isEmpty()) "All Houses" else resolveHouseName(id, houses) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Sport",
                        options = allSportsOptions,
                        selected = playerSportFilter,
                        onSelect = { playerSportFilter = it; playerPage = 1 },
                        labelOf = { id -> if (id.isEmpty()) "All Sports" else resolveSportName(id, sports) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Grade",
                        options = listOf("") + gradeOptions,
                        selected = playerGradeFilter,
                        onSelect = { playerGradeFilter = it; playerPage = 1 },
                        labelOf = { v -> if (v.isEmpty()) "All Grades" else "Grade $v" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = "Gender",
                        options = genderOptions,
                        selected = playerGenderFilter,
                        onSelect = { playerGenderFilter = it; playerPage = 1 },
                        labelOf = { v -> if (v.isEmpty()) "All Genders" else v },
                        modifier = Modifier.weight(1f)
                    )
                    SortSelect(
                        label = "Sort",
                        options = listOf("name", "roll", "squad", "grade"),
                        selected = playerSortCol,
                        descending = !playerSortAsc,
                        onSelect = { col ->
                            if (playerSortCol == col) playerSortAsc = !playerSortAsc
                            else { playerSortCol = col; playerSortAsc = true }
                        },
                        labelOf = { it.replaceFirstChar { c -> c.uppercase() } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allDisplayedSelected, onCheckedChange = { checked ->
                            selectedPlayerIds = if (checked) {
                                visiblePlayers.map { it.id }.toSet()
                            } else {
                                selectedPlayerIds - visiblePlayers.map { it.id }.toSet()
                            }
                        })
                        Text("Select All", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    ResultCount("${filteredPlayers.size} result${if (filteredPlayers.size != 1) "s" else ""}")
                }
            }
        }

        if (selectedPlayerIds.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorBgDark,
                    border = BorderStroke(1.dp, ErrorBorderDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${selectedPlayerIds.size} player${if (selectedPlayerIds.size != 1) "s" else ""} selected", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                        WebButton("Delete Selected", onClick = { deleteTarget = DeleteTarget.Players(selectedPlayerIds.toList()) })
                    }
                }
            }
        }

        if (filteredPlayers.isEmpty()) {
            item { EmptyState("No players", "Tap “Add Player” to register the house rosters.", Modifier) }
        } else {
            items(visiblePlayers, key = { it.id }) { player ->
                PlayerRow(
                    player = player,
                    houses = houses,
                    squads = squads,
                    selected = selectedPlayerIds.contains(player.id),
                    onToggle = { checked ->
                        selectedPlayerIds = if (checked) selectedPlayerIds + player.id else selectedPlayerIds - player.id
                    },
                    onEdit = {
                        editingPlayer = player
                        showPlayerDialog = true
                    },
                    onDelete = { deleteTarget = DeleteTarget.Player(player.id) }
                )
            }
            item {
                PaginationBar(
                    totalItems = filteredPlayers.size,
                    page = playerPage,
                    totalPages = totalPagesOf(filteredPlayers.size, playerPageSize),
                    onPrev = { playerPage = (playerPage - 1).coerceAtLeast(1) },
                    onNext = { playerPage = (playerPage + 1).coerceAtMost(totalPagesOf(filteredPlayers.size, playerPageSize)) },
                    pageSize = playerPageSize,
                    onPageSize = { playerPageSize = it; playerPage = 1 }
                )
            }
        }

        item {
            SectionDivider(title = "Fixture Score Management", count = matches.size, dotColor = MaterialTheme.colorScheme.tertiary)
        }

        item {
            FilterPanel {
                AppTextField(value = fixtureSearch, onValueChange = { fixtureSearch = it; fixturePage = 1 }, placeholder = "Search teams, round…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = "Sport",
                        options = allSportsOptions,
                        selected = fixtureSportFilter,
                        onSelect = { fixtureSportFilter = it; fixturePage = 1 },
                        labelOf = { id -> if (id.isEmpty()) "All Sports" else resolveSportName(id, sports) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Gender",
                        options = genderOptions,
                        selected = fixtureGenderFilter,
                        onSelect = { fixtureGenderFilter = it; fixturePage = 1 },
                        labelOf = { v -> if (v.isEmpty()) "All Genders" else v },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = "Stage",
                        options = stageOptions,
                        selected = fixtureStageFilter,
                        onSelect = { fixtureStageFilter = it; fixturePage = 1 },
                        labelOf = { v ->
                            when (v) {
                                "" -> "All Stages"
                                "league" -> "League"
                                "semifinal" -> "Semifinal"
                                "final" -> "Final"
                                else -> v
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Status",
                        options = statusOptions,
                        selected = fixtureStatusFilter,
                        onSelect = { fixtureStatusFilter = it; fixturePage = 1 },
                        labelOf = { v ->
                            when (v) {
                                "" -> "All Statuses"
                                "completed" -> "Completed"
                                "scheduled" -> "Scheduled"
                                else -> v
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SortSelect(
                        label = "Sort",
                        options = listOf("id", "sport", "stage", "status"),
                        selected = fixtureSortCol,
                        descending = !fixtureSortAsc,
                        onSelect = { col ->
                            if (fixtureSortCol == col) fixtureSortAsc = !fixtureSortAsc
                            else { fixtureSortCol = col; fixtureSortAsc = true }
                        },
                        labelOf = { it.replaceFirstChar { c -> c.uppercase() } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultCount("${filteredMatches.size} match${if (filteredMatches.size != 1) "es" else ""}")
                    Spacer(Modifier.weight(1f))
                    WebButton("New Match", onClick = { showMatchDialog = true }, icon = ScoreBoardIcons.Plus, secondary = true)
                }
            }
        }

        if (filteredMatches.isEmpty()) {
            item { EmptyState("No fixtures", "Schedule a match with “New Match”.", Modifier) }
        } else {
            items(visibleMatches, key = { it.id }) { match ->
                AdminMatchRow(
                    match = match,
                    squads = squads,
                    houses = houses,
                    sports = sports,
                    onEdit = { onEditMatch(match) },
                    onDelete = { deleteTarget = DeleteTarget.Match(match.id) },
                    onSaved = { onToast(it) }
                )
            }
            item {
                PaginationBar(
                    totalItems = filteredMatches.size,
                    page = fixturePage,
                    totalPages = totalPagesOf(filteredMatches.size, fixturePageSize),
                    onPrev = { fixturePage = (fixturePage - 1).coerceAtLeast(1) },
                    onNext = { fixturePage = (fixturePage + 1).coerceAtMost(totalPagesOf(filteredMatches.size, fixturePageSize)) },
                    pageSize = fixturePageSize,
                    onPageSize = { fixturePageSize = it; fixturePage = 1 }
                )
            }
        }

        if (isSuperAdmin) {
            item {
                SectionDivider(title = "Audit Log", count = auditTotal, dotColor = MechiOrange)
            }
            if (!auditReady) {
                item { SkeletonTable() }
            } else if (auditEntries.isEmpty()) {
                item { EmptyState("No audit events match the current filters.", "", Modifier) }
            } else {
                items(auditEntries, key = { it.id ?: (it.createdAt ?: "") }) { entry ->
                    AuditEntryRow(entry)
                }
            }
            if (auditReady && auditEntries.isNotEmpty()) {
                item {
                    val totalPages = ((auditTotal + 49) / 50).coerceAtLeast(1)
                    val currentPage = (auditOffset / 50) + 1
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("$auditTotal event${if (auditTotal != 1) "s" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Page $currentPage / ${totalPages.coerceAtLeast(1)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            WebButton("Prev", onClick = { auditOffset = (auditOffset - 50).coerceAtLeast(0); loadAudit() }, secondary = true, enabled = auditOffset > 0)
                            WebButton("Next", onClick = { auditOffset += 50; loadAudit() }, secondary = true, enabled = auditOffset + 50 < auditTotal)
                        }
                    }
                }
            }
        }
    }

    // ---------- DIALOGS ----------

    if (showSquadDialog) {
        SquadDialog(
            houses = houses,
            sports = sports,
            initial = editingSquad,
            busy = busy,
            onDismiss = { showSquadDialog = false },
            onSubmit = { name, houseId, sportId, gender, label ->
                scope.launch {
                    busy = true
                    try {
                        val existing = editingSquad
                        if (existing == null) {
                            ApiRepository.createTeam(name, houseId, sportId, gender, label)
                            onToast("Squad added")
                        } else {
                            ApiRepository.updateTeam(existing.id, name, houseId, sportId, gender, label)
                            onToast("Squad updated")
                        }
                        showSquadDialog = false
                        onRefresh()
                    } catch (e: ApiException) {
                        onToast(e.message ?: "Request failed")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    if (showPlayerDialog) {
        PlayerDialog(
            squads = squads,
            houses = houses,
            initial = editingPlayer,
            busy = busy,
            onDismiss = { showPlayerDialog = false },
            onSubmit = { name, teamId, rollNumber, grade, section, gender ->
                scope.launch {
                    busy = true
                    try {
                        val existing = editingPlayer
                        if (existing == null) {
                            ApiRepository.createPlayer(name, teamId, rollNumber, grade, section, gender)
                            onToast("Player added")
                        } else {
                            ApiRepository.updatePlayer(existing.id, name, teamId, rollNumber, grade, section, gender)
                            onToast("Player updated")
                        }
                        showPlayerDialog = false
                        onRefresh()
                    } catch (e: ApiException) {
                        onToast(e.message ?: "Request failed")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    if (showMatchDialog) {
        MatchDialog(
            sports = sports,
            squads = squads,
            houses = houses,
            busy = busy,
            onDismiss = { showMatchDialog = false },
            onSubmit = { sportId, gender, teamAId, teamBId, stage, roundInfo ->
                scope.launch {
                    busy = true
                    try {
                        ApiRepository.createMatch(sportId, gender, teamAId, teamBId, stage, "HS", roundInfo)
                        showMatchDialog = false
                        onToast("Match scheduled")
                        onRefresh()
                    } catch (e: ApiException) {
                        onToast(e.message ?: "Request failed")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    if (showAddAdminDialog) {
        AddAdminDialog(
            busy = busy,
            onDismiss = { showAddAdminDialog = false },
            onSubmit = { email, password, role ->
                scope.launch {
                    busy = true
                    try {
                        ApiRepository.addAdmin(email, password, role)
                        onToast("$email added")
                        showAddAdminDialog = false
                        loadAdmins()
                    } catch (e: ApiException) {
                        onToast(e.message ?: "Failed to add admin")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    resetPasswordTarget?.let { email ->
        ResetPasswordDialog(
            email = email,
            busy = busy,
            onDismiss = { resetPasswordTarget = null },
            onSubmit = { password ->
                scope.launch {
                    busy = true
                    try {
                        ApiRepository.resetAdminPassword(email, password)
                        onToast("Password reset for $email")
                        resetPasswordTarget = null
                    } catch (e: ApiException) {
                        onToast(e.message ?: "Password reset failed")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }

    if (deleteTarget != DeleteTarget.None) {
        ConfirmDialog(
            title = confirmDialogTitle,
            message = "This action cannot be undone.",
            busy = busy,
            destructiveLabel = "Delete",
            onConfirm = { runDelete() },
            onDismiss = { deleteTarget = DeleteTarget.None }
        )
    }
}

private fun totalPagesOf(totalItems: Int, pageSize: String): Int {
    if (pageSize == "all") return 1
    val size = pageSize.toIntOrNull() ?: totalItems
    if (size <= 0) return 1
    val pages = (totalItems + size - 1) / size
    return pages.coerceAtLeast(1)
}

@Composable
private fun SectionDivider(title: String, count: Int, dotColor: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("$count", modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalRule()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FilterPanel(content: @Composable Column.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    LabeledDropdown(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        labelOf = labelOf,
        modifier = modifier
    )
}

@Composable
private fun SortSelect(
    label: String,
    options: List<String>,
    selected: String,
    descending: Boolean,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selected.isEmpty()) "Select…" else "${labelOf(selected)}${if (descending) " ↓" else " ↑"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Icon(ScoreBoardIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { value ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (value == selected) "${labelOf(value)}${if (descending) " ↓" else " ↑"}" else labelOf(value),
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCount(text: String) {
    Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun PaginationBar(
    totalItems: Int,
    page: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    pageSize: String,
    onPageSize: (String) -> Unit
) {
    val pageSizeNum = pageSize.toIntOrNull()
    val start = if (totalItems == 0) 0 else ((page - 1) * (pageSizeNum ?: totalItems)) + 1
    val end = minOf(start - 1 + (pageSizeNum ?: totalItems), totalItems)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (totalItems == 0) "Showing 0 of 0" else "Showing $start-$end of $totalItems",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebButton("Prev", onClick = onPrev, secondary = true, enabled = page > 1)
                    Text("Page $page of $totalPages", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    WebButton("Next", onClick = onNext, secondary = true, enabled = page < totalPages)
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            when (pageSize) {
                                "10" -> "10 / page"
                                "25" -> "25 / page"
                                else -> "Show All"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("10", "25", "all").forEach { size ->
                            DropdownMenuItem(
                                text = { Text(if (size == "all") "Show All" else "$size / page", fontSize = 13.sp) },
                                onClick = { onPageSize(size); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAccountRow(
    account: AdminAccount,
    onResetPassword: () -> Unit,
    onRemove: () -> Unit
) {
    val isSuper = account.role == "superadmin"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(account.email, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (!account.createdAt.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(account.createdAt, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                    }
                }
                StatusBadge(
                    if (isSuper) "SUPER ADMIN" else "ADMIN",
                    if (isSuper) BadgeVariant.SUCCESS else BadgeVariant.PENDING
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                WebButton("Reset Password", onClick = onResetPassword, secondary = true)
                WebButton("Remove", onClick = onRemove, secondary = true)
            }
        }
    }
}

@Composable
private fun AuditEntryRow(entry: AuditEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(entry.createdAt ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                StatusBadge(entry.action ?: "-", BadgeVariant.STAGE)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Actor", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.tertiary)
                Text(entry.actorEmail ?: "-", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (!entry.targetEmail.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.tertiary)
                    Text(entry.targetEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (!entry.details.isNullOrBlank()) {
                Text(entry.details, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            if (!entry.ipAddress.isNullOrBlank()) {
                Text("IP: ${entry.ipAddress}", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun SquadRow(
    team: Team,
    houses: List<House>,
    sports: List<Sport>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val houseColor = resolveHouseColor(team.houseId, houses)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(houseColor, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(resolveHouseName(team.houseId, houses))
                        append(" • ")
                        append(resolveSportName(team.sportId, sports))
                        append(" • ")
                        append(genderLabel(team.gender))
                        if (!team.squadLabel.isNullOrBlank()) append(" (${team.squadLabel})")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            IconBtn(icon = ScoreBoardIcons.Edit, contentDescription = "Edit squad", onClick = onEdit)
            Spacer(Modifier.width(6.dp))
            IconBtn(icon = ScoreBoardIcons.Trash, contentDescription = "Delete squad", onClick = onDelete, destructive = true)
        }
    }
}

@Composable
private fun PlayerRow(
    player: Player,
    houses: List<House>,
    squads: List<Team>,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val squad = squads.firstOrNull { it.id == player.teamId }
    val squadName = if (squad != null) resolveTeamName(squad.id, squads, houses) else "—"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) KarnaliGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        squadName.let { append(it) }
                        if (!player.rollNumber.isNullOrBlank()) append(" • ${player.rollNumber}")
                        listOf(player.grade, player.section).filter { !it.isNullOrBlank() }.joinToString("-").let { if (it.isNotEmpty()) append(" • $it") }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
            IconBtn(icon = ScoreBoardIcons.Edit, contentDescription = "Edit player", onClick = onEdit)
            Spacer(Modifier.width(6.dp))
            IconBtn(icon = ScoreBoardIcons.Trash, contentDescription = "Delete player", onClick = onDelete, destructive = true)
        }
    }
}

@Composable
private fun AdminMatchRow(
    match: MatchItem,
    squads: List<Team>,
    houses: List<House>,
    sports: List<Sport>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var scoreA by remember(match.id) { mutableStateOf(match.scoreTeamA.toString()) }
    var scoreB by remember(match.id) { mutableStateOf(match.scoreTeamB.toString()) }
    var saving by remember(match.id) { mutableStateOf(false) }

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
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(match.stage ?: "league", BadgeVariant.STAGE)
                    StatusBadge(
                        if (isCompleted(match)) "FT" else "Sched.",
                        if (isCompleted(match)) BadgeVariant.SUCCESS else BadgeVariant.PENDING
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconBtn(icon = ScoreBoardIcons.Edit, contentDescription = "Edit match", onClick = onEdit)
                    IconBtn(icon = ScoreBoardIcons.Trash, contentDescription = "Delete match", onClick = onDelete, destructive = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamSide(aName, aColor, Modifier.weight(1f))
                Text("VS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 10.dp))
                TeamSide(bName, bColor, Modifier.weight(1f), alignEnd = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${resolveSportName(match.sportId, sports)} • ${genderLabel(match.gender)} • ${match.roundInfo ?: "League Game"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(10.dp))
            if (!hasTeams) {
                Text("TBD — awaiting qualifiers", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberField(value = scoreA, onValueChange = { scoreA = it }, Modifier.weight(1f), placeholder = match.scoreTeamA.toString())
                    Text("–", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    NumberField(value = scoreB, onValueChange = { scoreB = it }, Modifier.weight(1f), placeholder = match.scoreTeamB.toString())
                    WebButton(
                        if (saving) "Saving…" else "Save",
                        onClick = {
                            scope.launch {
                                saving = true
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
                                    onSaved("Score saved")
                                } catch (e: ApiException) {
                                    onSaved(e.message ?: "Save failed")
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    busy: Boolean,
    destructiveLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(title = title, onDismiss = onDismiss) {
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            WebButton("Cancel", onClick = onDismiss, secondary = true, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            WebButton(if (busy) "Deleting…" else destructiveLabel, onClick = onConfirm, enabled = !busy)
        }
    }
}

@Composable
private fun AddAdminDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String, role: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("admin") }

    AppDialog(title = "Add Admin", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(value = email, onValueChange = { email = it }, label = "Email", placeholder = "admin@scoreboard.com")
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            LabeledDropdown(
                label = "Role",
                options = listOf("admin", "superadmin"),
                selected = role,
                onSelect = { role = it },
                labelOf = { it.replaceFirstChar { c -> c.uppercase() } }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WebButton("Cancel", onClick = onDismiss, secondary = true, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                WebButton(
                    if (busy) "Adding…" else "Add Admin",
                    onClick = {
                        if (email.isBlank() || password.isBlank()) return@WebButton
                        onSubmit(email.trim(), password, role)
                    },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun ResetPasswordDialog(
    email: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AppDialog(title = "Reset Password", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(email, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("New Password", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WebButton("Cancel", onClick = onDismiss, secondary = true, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                WebButton(
                    if (busy) "Resetting…" else "Reset Password",
                    onClick = {
                        if (password.isBlank()) return@WebButton
                        onSubmit(password)
                    },
                    enabled = !busy && password.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun SquadDialog(
    houses: List<House>,
    sports: List<Sport>,
    initial: Team?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, houseId: String, sportId: String, gender: String, label: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var houseId by remember(initial) { mutableStateOf(initial?.houseId ?: "") }
    var sportId by remember(initial) { mutableStateOf(initial?.sportId ?: "") }
    var gender by remember(initial) { mutableStateOf(initial?.gender ?: "Boys") }
    var label by remember(initial) { mutableStateOf(initial?.squadLabel ?: "A") }

    AppDialog(title = if (initial == null) "Add Squad" else "Edit Squad", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(value = name, onValueChange = { name = it }, label = "Squad Name (optional)")
            LabeledDropdown(
                label = "House",
                options = houses.map { it.id },
                selected = houseId,
                onSelect = { houseId = it },
                labelOf = { id -> if (id.isEmpty()) "Select house…" else resolveHouseName(id, houses) }
            )
            LabeledDropdown(
                label = "Sport",
                options = sports.map { it.id },
                selected = sportId,
                onSelect = { sportId = it },
                labelOf = { id -> if (id.isEmpty()) "Select sport…" else resolveSportName(id, sports) }
            )
            LabeledDropdown(
                label = "Gender",
                options = listOf("Boys", "Girls"),
                selected = gender,
                onSelect = { gender = it }
            )
            AppTextField(value = label, onValueChange = { label = it.take(2) }, label = "Squad Label (e.g. A, B)")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WebButton("Cancel", onClick = onDismiss, secondary = true, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                WebButton(
                    if (busy) "Saving…" else if (initial == null) "Create" else "Save",
                    onClick = {
                        if (houseId.isBlank() || sportId.isBlank()) return@WebButton
                        onSubmit(name.trim(), houseId, sportId, gender, label.trim().ifEmpty { "A" })
                    },
                    enabled = !busy && houseId.isNotBlank() && sportId.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun PlayerDialog(
    squads: List<Team>,
    houses: List<House>,
    initial: Player?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, teamId: String, rollNumber: String, grade: String, section: String, gender: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var teamId by remember(initial) { mutableStateOf(initial?.teamId ?: "") }
    var rollNumber by remember(initial) { mutableStateOf(initial?.rollNumber ?: "") }
    var grade by remember(initial) { mutableStateOf(initial?.grade ?: "") }
    var section by remember(initial) { mutableStateOf(initial?.section ?: "") }
    var gender by remember(initial) { mutableStateOf(initial?.gender ?: "Boys") }

    AppDialog(title = if (initial == null) "Add Player" else "Edit Player", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(value = name, onValueChange = { name = it }, label = "Player Name")
            LabeledDropdown(
                label = "Squad",
                options = squads.map { it.id },
                selected = teamId,
                onSelect = { teamId = it },
                labelOf = { id ->
                    if (id.isEmpty()) "Select squad…"
                    else resolveTeamName(id, squads, houses)
                }
            )
            AppTextField(value = rollNumber, onValueChange = { rollNumber = it.filter { c -> c.isDigit() || c.isLetter() }.take(12) }, label = "Roll Number (optional)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(value = grade, onValueChange = { grade = it }, label = "Grade (optional)", modifier = Modifier.weight(1f))
                AppTextField(value = section, onValueChange = { section = it }, label = "Section (optional)", modifier = Modifier.weight(1f))
            }
            LabeledDropdown(
                label = "Gender",
                options = listOf("Boys", "Girls"),
                selected = gender,
                onSelect = { gender = it }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WebButton("Cancel", onClick = onDismiss, secondary = true, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                WebButton(
                    if (busy) "Saving…" else if (initial == null) "Create" else "Save",
                    onClick = {
                        if (name.isBlank() || teamId.isBlank()) return@WebButton
                        onSubmit(name.trim(), teamId, rollNumber.trim().ifEmpty { "" }, grade.trim().ifEmpty { "" }, section.trim().ifEmpty { "" }, gender)
                    },
                    enabled = !busy && name.isNotBlank() && teamId.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun MatchDialog(
    sports: List<Sport>,
    squads: List<Team>,
    houses: List<House>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (sportId: String, gender: String, teamAId: String, teamBId: String, stage: String, roundInfo: String) -> Unit
) {
    var sportId by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Boys") }
    var teamAId by remember { mutableStateOf("") }
    var teamBId by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("league") }
    var roundInfo by remember { mutableStateOf("League Game") }

    val available = remember(sportId, gender, squads) {
        if (sportId.isBlank()) emptyList()
        else squads.filter { it.sportId == sportId && (it.gender ?: "").equals(gender, ignoreCase = true) }
    }

    AppDialog(title = "Schedule Match", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledDropdown(
                label = "Sport",
                options = sports.map { it.id },
                selected = sportId,
                onSelect = {
                    sportId = it
                    teamAId = ""
                    teamBId = ""
                },
                labelOf = { id -> if (id.isEmpty()) "Select sport…" else resolveSportName(id, sports) }
            )
            LabeledDropdown(
                label = "Gender",
                options = listOf("Boys", "Girls"),
                selected = gender,
                onSelect = {
                    gender = it
                    teamAId = ""
                    teamBId = ""
                }
            )
            LabeledDropdown(
                label = "Team A (Home)",
                options = available.map { it.id },
                selected = teamAId,
                onSelect = { teamAId = it },
                labelOf = { id -> if (id.isEmpty()) "Select team…" else resolveTeamName(id, squads, houses) }
            )
            LabeledDropdown(
                label = "Team B (Away)",
                options = available.filter { it.id != teamAId }.map { it.id },
                selected = teamBId,
                onSelect = { teamBId = it },
                labelOf = { id -> if (id.isEmpty()) "Select team…" else resolveTeamName(id, squads, houses) }
            )
            LabeledDropdown(
                label = "Stage",
                options = listOf("league", "semifinal", "final"),
                selected = stage,
                onSelect = { stage = it },
                labelOf = { value -> value.replaceFirstChar { it.uppercase() } }
            )
            AppTextField(value = roundInfo, onValueChange = { roundInfo = it }, label = "Round Info (e.g. League Game)")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WebButton("Cancel", onClick = onDismiss, secondary = true, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                WebButton(
                    if (busy) "Creating…" else "Schedule",
                    onClick = {
                        if (sportId.isBlank() || teamAId.isBlank() || teamBId.isBlank() || teamAId == teamBId) return@WebButton
                        onSubmit(sportId, gender, teamAId, teamBId, stage, roundInfo.trim().ifEmpty { "League Game" })
                    },
                    enabled = !busy && sportId.isNotBlank() && teamAId.isNotBlank() && teamBId.isNotBlank() && teamAId != teamBId
                )
            }
        }
    }
}