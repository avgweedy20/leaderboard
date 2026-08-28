package com.scoreboard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.*
import com.scoreboard.app.models.*
import com.scoreboard.app.ui.theme.*

// ---------------------------------------------------------------------------
// Pure resolvers — same client-side resolution the web app performs
// ---------------------------------------------------------------------------

fun resolveHouseName(houseId: String?, houses: List<House>): String =
    houses.firstOrNull { it.id == houseId }?.name ?: "House"

fun resolveHouseColor(houseId: String?, houses: List<House>): Color =
    parseHexColor(houses.firstOrNull { it.id == houseId }?.colorHex)

fun resolveSportName(sportId: String?, sports: List<Sport>): String =
    sports.firstOrNull { it.id == sportId }?.name ?: "Unknown Sport"

fun sportIcon(sportId: String?, sports: List<Sport>): ImageVector {
    val sport = sports.firstOrNull { it.id == sportId }
    return when ((sport?.name ?: "").lowercase()) {
        "basketball" -> ScoreBoardIcons.Basketball
        "futsal" -> ScoreBoardIcons.Football
        else -> ScoreBoardIcons.Cricket
    }
}

fun resolveTeamName(teamId: String?, squads: List<Team>, houses: List<House>): String {
    if (teamId.isNullOrBlank()) return "TBD"
    val team = squads.firstOrNull { it.id == teamId } ?: return "TBD"
    val houseName = resolveHouseName(team.houseId, houses)
    val duplicates = squads.count {
        it.houseId == team.houseId &&
            it.sportId == team.sportId &&
            it.gender == team.gender
    }
    val label = team.squadLabel?.takeIf { it.isNotBlank() }
    return if (duplicates > 1) "$houseName ${label ?: "A"}" else houseName
}

fun resolveTeamColor(teamId: String?, squads: List<Team>, houses: List<House>): Color {
    val team = squads.firstOrNull { it.id == teamId } ?: return KarnaliGreen
    return resolveHouseColor(team.houseId, houses)
}

fun genderLabel(gender: String?): String =
    gender?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "All"

data class FixtureGroup(
    val sportId: String,
    val sportName: String,
    val sportIcon: ImageVector,
    val gender: String,
    val matches: List<MatchItem>
)

fun groupFixtures(matches: List<MatchItem>, sports: List<Sport>): List<FixtureGroup> {
    val groups = mutableListOf<FixtureGroup>()
    for (sport in sports) {
        val sportMatches = matches.filter { it.sportId == sport.id }
        if (sportMatches.isEmpty()) continue
        for (gender in listOf("Girls", "Boys")) {
            val gm = sportMatches.filter { (it.gender ?: "").equals(gender, ignoreCase = true) }
            if (gm.isNotEmpty()) {
                groups += FixtureGroup(
                    sportId = sport.id,
                    sportName = sport.name,
                    sportIcon = sportIcon(sport.id, sports),
                    gender = gender,
                    matches = gm
                )
            }
        }
    }
    return groups
}

fun isCompleted(match: MatchItem): Boolean = match.status?.equals("completed", ignoreCase = true) == true

fun numeric(value: Int): String = value.toString()

// ---------------------------------------------------------------------------
// Shared building blocks modeled on the web design system
// ---------------------------------------------------------------------------

@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.7).sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.tertiary
    )
}

enum class BadgeVariant { STAGE, SUCCESS, PENDING }

@Composable
fun StatusBadge(text: String, variant: BadgeVariant, modifier: Modifier = Modifier) {
    val (bg, fg, borderColor) = when (variant) {
        BadgeVariant.SUCCESS -> Triple(SuccessBg, SuccessText, SuccessBorder)
        BadgeVariant.PENDING -> Triple(PendingBg, PendingText, PendingBorder)
        BadgeVariant.STAGE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.outline
        )
    }
    Text(
        text = text.uppercase(),
        modifier = modifier
            .background(bg, RoundedCornerShape(5.dp))
            .border(1.dp, borderColor, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1
    )
}

@Composable
fun WebChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun FilterChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String = { it },
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { value ->
            WebChip(label = labelOf(value), selected = value == selected, onClick = { onSelect(value) })
        }
    }
}

@Composable
fun LabeledDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String = { it },
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
                        text = labelOf(if (selected.isEmpty()) "" else selected),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected.isEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Icon(
                        ScoreBoardIcons.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(labelOf(if (value.isEmpty()) "" else value), fontSize = 13.sp) },
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
fun WebButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = true
) {
    val bg = if (secondary) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val fg = if (secondary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .height(if (compact) 34.dp else 42.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
    destructive: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (destructive) LossRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
        modifier = modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = if (destructive) LossRed else tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "0"
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(3)) },
        modifier = modifier,
        label = { label?.let { Text(it, fontSize = 12.sp) } },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Bold
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { label?.let { Text(it, fontSize = 12.sp) } },
        placeholder = { placeholder?.let { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    IconBtn(icon = ScoreBoardIcons.Close, contentDescription = "Close", onClick = onDismiss)
                }
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse)
    )
    Box(
        modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
            RoundedCornerShape(4.dp)
        )
    )
}

@Composable
fun SkeletonTable(modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(12.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(10.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(10.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, desc: String? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                ScoreBoardIcons.Empty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ErrorBgDark,
        border = BorderStroke(1.dp, ErrorBorderDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(ScoreBoardIcons.Alert, contentDescription = null, tint = ErrorText, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorText,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WebButton("Copy Error", onClick = onCopy, secondary = true, compact = false)
                WebButton("Retry", onClick = onRetry, compact = false)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small table primitives
// ---------------------------------------------------------------------------

@Composable
fun TeamSide(name: String, color: Color, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Box(Modifier.width(3.dp).height(22.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
fun TableLabel(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.tertiary,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
    number: Boolean = false,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFeatureSettings = if (number) "tnum" else null
        )
    )
}