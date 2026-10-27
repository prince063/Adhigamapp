package com.example.adhigam.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.AttendanceSummaryRow
import com.example.adhigam.data.model.HeatmapStudent
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Class attendance as on the web: student-attendance.html (Mark), attendance-summary.html (Summary) and
// attendance-heatmap.html (Heatmap). The heatmap API is Superadmin/Admin/Principal only, so Teachers get two tabs.
@Composable
fun ClassAttendanceScreen(
    showHeatmap: Boolean,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = if (showHeatmap) listOf("Mark", "Summary", "Heatmap") else listOf("Mark", "Summary")
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        when (selected) {
            0 -> MarkAttendanceScreen(showMessage = showMessage)
            1 -> AttendanceSummaryScreen()
            else -> AttendanceHeatmapScreen()
        }
    }
}

// ---- Summary -------------------------------------------------------------------------------------

@Composable
fun AttendanceSummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceSummaryViewModel = viewModel { AttendanceSummaryViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AttendanceSummaryContent(
        state = state,
        onSelectClass = viewModel::selectClass,
        onSelectSection = viewModel::selectSection,
        onSelectMonth = viewModel::selectMonth,
        onQueryChange = viewModel::setQuery,
        onRetry = viewModel::load,
        modifier = modifier
    )
}

@Composable
private fun AttendanceSummaryContent(
    state: AttendanceSummaryUiState,
    onSelectClass: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ClassMonthFilters(
                classType = state.classType,
                section = state.section,
                month = state.month,
                onSelectClass = onSelectClass,
                onSelectSection = onSelectSection,
                onSelectMonth = onSelectMonth
            )
        }
        when {
            state.classType == null || state.section == null -> item { Hint("Select Class and Section to view the summary") }
            state.isLoading -> item { Loading() }
            state.error != null -> item {
                Box(Modifier.heightIn(min = 240.dp)) { ErrorState(state.error, onRetry = onRetry) }
            }
            state.loaded && state.rows.isEmpty() -> item { Hint("No attendance records for this month") }
            else -> {
                item {
                    ListCard {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Students", state.rows.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StatBox(
                                "Class Avg",
                                state.classAverage?.let { "%.1f%%".format(Locale.US, it) } ?: "—",
                                percentColor(state.classAverage ?: 0.0),
                                Modifier.weight(1f)
                            )
                            StatBox(
                                "Below 75%",
                                state.rows.count { it.totalDays > 0 && it.percent < 75 }.toString(),
                                ErrorRed,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name or admission no") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                if (state.visibleRows.isEmpty()) item { Hint("No student matches \"${state.query}\"") }
                items(state.visibleRows, key = { it.admissionNo }) { SummaryRow(it) }
            }
        }
    }
}

@Composable
private fun SummaryRow(row: AttendanceSummaryRow) {
    val color = percentColor(row.percent)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Adm No: ${row.admissionNo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Total ${row.totalDays}  •  Present ${row.presentDays}  •  Absent ${row.absentDays}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            // Same badge as the web: green at 75% and above, red below.
            Text(
                "%.1f%%".format(Locale.US, row.percent),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private fun percentColor(percent: Double): Color = if (percent >= 75) SuccessGreen else ErrorRed

// ---- Heatmap -------------------------------------------------------------------------------------

@Composable
fun AttendanceHeatmapScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceHeatmapViewModel = viewModel { AttendanceHeatmapViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AttendanceHeatmapContent(
        state = state,
        onSelectClass = viewModel::selectClass,
        onSelectSection = viewModel::selectSection,
        onSelectMonth = viewModel::selectMonth,
        onRetry = viewModel::load,
        modifier = modifier
    )
}

@Composable
private fun AttendanceHeatmapContent(
    state: HeatmapUiState,
    onSelectClass: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ClassMonthFilters(
                classType = state.classType,
                section = state.section,
                month = state.month,
                onSelectClass = onSelectClass,
                onSelectSection = onSelectSection,
                onSelectMonth = onSelectMonth
            )
        }
        when {
            state.classType == null -> item { Hint("Select a Class to load the heatmap") }
            state.isLoading -> item { Loading() }
            state.error != null -> item {
                Box(Modifier.heightIn(min = 240.dp)) { ErrorState(state.error, onRetry = onRetry) }
            }
            state.loaded && state.students.isEmpty() -> item { Hint("No attendance data for this month") }
            else -> {
                item {
                    ListCard {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Students", state.students.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StatBox("Present", state.presentDays.toString(), SuccessGreen, Modifier.weight(1f))
                            StatBox("Absent", state.absentDays.toString(), ErrorRed, Modifier.weight(1f))
                            StatBox("Leave", state.leaveDays.toString(), Amber600, Modifier.weight(1f))
                        }
                    }
                }
                item { HeatmapLegend() }
                item { HeatmapGrid(state.students, state.month) }
            }
        }
    }
}

private val NameColumnWidth = 116.dp
private val CellSize = 26.dp
private val CellGap = 2.dp

// Student × day grid; names stay pinned while the days scroll sideways.
@Composable
private fun HeatmapGrid(students: List<HeatmapStudent>, month: YearMonth) {
    val days = 1..month.lengthOfMonth()
    val scroll = rememberScrollState()
    ListCard {
        Row(Modifier.padding(vertical = 12.dp)) {
            Column(Modifier.width(NameColumnWidth).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(CellGap)) {
                Box(Modifier.height(CellSize), contentAlignment = Alignment.CenterStart) {
                    Text("Student", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                students.forEach { student ->
                    Box(Modifier.height(CellSize), contentAlignment = Alignment.CenterStart) {
                        Text(
                            student.studentName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scroll)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(CellGap)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    days.forEach { day ->
                        Box(Modifier.size(CellSize), contentAlignment = Alignment.Center) {
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                students.forEach { student ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                        days.forEach { day -> HeatmapCell(student.days[day]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(status: Char?) {
    val color = heatmapColor(status)
    Box(
        Modifier
            .size(CellSize)
            .clip(RoundedCornerShape(4.dp))
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (status != null) {
            Text(
                status.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val HalfDayBlue = Color(0xFF2563EB)

private fun heatmapColor(status: Char?): Color? = when (status) {
    'P' -> SuccessGreen
    'A' -> ErrorRed
    'L' -> Amber600
    'H' -> HalfDayBlue
    else -> null
}

@Composable
private fun HeatmapLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        listOf('P' to "Present", 'A' to "Absent", 'L' to "Leave", 'H' to "Half Day", null to "No Data").forEach { (status, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(heatmapColor(status) ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Shared pieces -------------------------------------------------------------------------------

@Composable
private fun ClassMonthFilters(
    classType: String?,
    section: String?,
    month: YearMonth,
    onSelectClass: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit
) {
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = "Class",
                    placeholder = "Select Class",
                    value = classType?.let(::classDisplayName),
                    options = SchoolClasses,
                    optionLabel = ::classDisplayName,
                    onSelect = onSelectClass,
                    modifier = Modifier.weight(1.3f)
                )
                SelectField(
                    label = "Section",
                    placeholder = "Select",
                    value = section,
                    options = SchoolSections,
                    optionLabel = { it },
                    onSelect = onSelectSection,
                    modifier = Modifier.weight(1f)
                )
            }
            MonthStepper(month = month, onSelectMonth = onSelectMonth)
        }
    }
}

private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

// Month / year chooser; future months can't be picked.
@Composable
private fun MonthStepper(month: YearMonth, onSelectMonth: (YearMonth) -> Unit) {
    val canGoNext = month < YearMonth.now()
    Column {
        Text(
            "Month",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectMonth(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(MonthFormat),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onSelectMonth(month.plusMonths(1)) }, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Hint(text: String) {
    ListCard { EmptyText(text) }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AttendanceSummaryPreview() {
    AdhigamTheme {
        AttendanceSummaryContent(
            state = AttendanceSummaryUiState(
                classType = "VIII",
                section = "A",
                loaded = true,
                rows = listOf(
                    AttendanceSummaryRow("1801", "Aarav Sharma", 22, 20, 2),
                    AttendanceSummaryRow("1802", "Ananya Verma", 22, 14, 8)
                )
            ),
            onSelectClass = {}, onSelectSection = {}, onSelectMonth = {}, onQueryChange = {}, onRetry = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AttendanceHeatmapPreview() {
    AdhigamTheme {
        AttendanceHeatmapContent(
            state = HeatmapUiState(
                classType = "VIII",
                loaded = true,
                students = listOf(
                    HeatmapStudent("1801", "Aarav Sharma", mapOf(1 to 'P', 2 to 'P', 3 to 'A', 5 to 'L')),
                    HeatmapStudent("1802", "Ananya Verma", mapOf(1 to 'P', 2 to 'H', 3 to 'P'))
                )
            ),
            onSelectClass = {}, onSelectSection = {}, onSelectMonth = {}, onRetry = {}
        )
    }
}
