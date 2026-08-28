package com.scoreboard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    onEditMatch: (MatchItem) -> Unit,
    onRefresh: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    var squadSearch by remember { mutableStateOf("") }
    var playerSearch by remember { mutableStateOf("") }
    var sportFilter by remember { mutableStateOf("") }

    var editingSquad by remember { mutableStateOf<Team?>(null) }
    var showSquadDialog by remember { mutableStateOf(false) }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var showPlayerDialog by remember { mutableStateOf(false) }
    var showMatchDialog by remember { mutableStateOf(false) }

    var selectedPlayerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget>(DeleteTarget.None) }

    val confirmDialogTitle = when (deleteTarget) {
        is DeleteTarget.Team -> "Confirm Delete"
        is DeleteTarget.Player -> "Confirm Delete"
        is DeleteTarget.Players -> "Confirm Bulk Delete"
        is DeleteTarget.Match -> "Confirm Delete"
        DeleteTarget.None -> ""
    }

    fun confirmMessage(): String = "This action cannot be undone."

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

    val filteredSquads = remember(squads, squadSearch) {
        if (squadSearch.isBlank()) squads
        else squads.filter { it.name.contains(squadSearch.trim(), ignoreCase = true) }
    }
    val filteredPlayers = remember(players, playerSearch) {
        if (playerSearch.isBlank()) players
        else players.filter { it.name.contains(playerSearch.trim(), ignoreCase = true) || (it.rollNumber ?: "").contains(playerSearch.trim()) }
    }
    val filteredMatches = remember(matches, sportFilter) {
        if (sportFilter.isBlank()) matches
        else matches.filter { it.sportId == sportFilter }
    }

    val allDisplayedSelected = remember(filteredPlayers, selectedPlayerIds) {
        filteredPlayers.isNotEmpty() && filteredPlayers.all { selectedPlayerIds.contains(it.id) }
    }

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
                WebButton("Add Squad", onClick = {
                    editingSquad = null
                    showSquadDialog = true
                }, icon = ScoreBoardIcons.Plus)
                WebButton("Add Player", onClick = {
                    editingPlayer = null
                    showPlayerDialog = true
                }, icon = ScoreBoardIcons.Plus)
                WebButton("New Match", onClick = { showMatchDialog = true }, icon = ScoreBoardIcons.Plus, secondary = true)
            }
        }

        item { SectionDivider(title = "House Squad Roster", count = squads.size) }

        item {
            AppTextField(value = squadSearch, onValueChange = { squadSearch = it }, placeholder = "Search squads…")
        }

        if (filteredSquads.isEmpty()) {
            item { EmptyState("No squads", "Tap “Add Squad” to register your first house squad.", Modifier) }
        } else {
            items(filteredSquads, key = { it.id }) { team ->
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
        }

        item {
            SectionDivider(title = "Registered Players", count = players.size)
        }

        item {
            AppTextField(value = playerSearch, onValueChange = { playerSearch = it }, placeholder = "Search players…")
        }

        if (filteredPlayers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = allDisplayedSelected, onCheckedChange = { checked ->
                        selectedPlayerIds = if (checked) {
                            filteredPlayers.map { it.id }.toSet()
                        } else {
                            selectedPlayerIds - filteredPlayers.map { it.id }.toSet()
                        }
                    })
                    Text("Select All", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                    Text("${selectedPlayerIds.size} selected", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        if (selectedPlayerIds.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = ErrorBgDark,
                    border = BorderStroke(1.dp, ErrorBorderDark)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delete selected (${selectedPlayerIds.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                        WebButton("Delete", onClick = { deleteTarget = DeleteTarget.Players(selectedPlayerIds.toList()) })
                    }
                }
            }
        }

        if (filteredPlayers.isEmpty()) {
            item { EmptyState("No players", "Tap “Add Player” to register the house rosters.", Modifier) }
        } else {
            items(filteredPlayers, key = { it.id }) { player ->
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
        }

        item {
            SectionDivider(title = "Fixture Score Management", count = matches.size)
        }

        item {
            FilterChipRow(
                options = listOf("") + sports.map { it.id },
                selected = sportFilter,
                onSelect = { sportFilter = it },
                labelOf = { value -> if (value.isEmpty()) "All Sports" else resolveSportName(value, sports) }
            )
        }

        if (filteredMatches.isEmpty()) {
            item { EmptyState("No fixtures", "Schedule a match with “New Match”.", Modifier) }
        } else {
            items(filteredMatches, key = { it.id }) { match ->
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
        }
    }

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
            onSubmit = { sportId, gender, teamAId, teamBId, stage, level, roundInfo ->
                scope.launch {
                    busy = true
                    try {
                        ApiRepository.createMatch(sportId, gender, teamAId, teamBId, stage, level, roundInfo)
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

    if (deleteTarget != DeleteTarget.None) {
        ConfirmDialog(
            title = confirmDialogTitle,
            message = confirmMessage(),
            busy = busy,
            destructiveLabel = "Delete",
            onConfirm = { runDelete() },
            onDismiss = { deleteTarget = DeleteTarget.None }
        )
    }
}

@Composable
private fun SectionDivider(title: String, count: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(KarnaliGreen, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (count > 0) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("$count", modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalRule()
        Spacer(Modifier.height(12.dp))
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(houseColor, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
                        if (isCompleted(match)) "FT" else "Scheduled",
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
    onSubmit: (sportId: String, gender: String, teamAId: String, teamBId: String, stage: String, level: String, roundInfo: String) -> Unit
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
                        onSubmit(sportId, gender, teamAId, teamBId, stage, "HS", roundInfo.trim().ifEmpty { "League Game" })
                    },
                    enabled = !busy && sportId.isNotBlank() && teamAId.isNotBlank() && teamBId.isNotBlank() && teamAId != teamBId
                )
            }
        }
    }
}
