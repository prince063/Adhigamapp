package com.example.adhigam.ui.staff

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.ClassPeriod
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.TIMETABLE_PERIODS
import com.example.adhigam.data.model.TeacherOption
import com.example.adhigam.data.model.TimetableDays
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.data.repository.TimetableRepository
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class TimetableManageUiState(
    val classType: String? = null,
    val section: String = "A",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val periods: List<ClassPeriod>? = null,
    val error: String? = null,
    val teachers: List<TeacherOption> = emptyList(),
    val isSaving: Boolean = false
)

// canPickTeacher: /auth/teachers is admin-only, so teachers save periods without choosing one (as on the web).
class TimetableManageViewModel(
    private val repository: TimetableRepository,
    canPickTeacher: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableManageUiState())
    val uiState: StateFlow<TimetableManageUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (canPickTeacher) {
            viewModelScope.launch {
                runCatching { repository.getTeachers() }.onSuccess { list -> _uiState.update { it.copy(teachers = list) } }
            }
        }
    }

    fun setClass(classType: String) {
        _uiState.update { it.copy(classType = classType, periods = null) }
        load(refresh = false)
    }

    fun setSection(section: String) {
        _uiState.update { it.copy(section = section, periods = null) }
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    // onResult gets null on success or the error to show.
    fun save(period: ClassPeriod, onResult: (error: String?) -> Unit) =
        run({ cls, sec -> repository.savePeriod(cls, sec, period) }, onResult)

    fun delete(period: ClassPeriod, onResult: (error: String?) -> Unit) =
        run({ cls, sec -> repository.deletePeriod(cls, sec, period.day, period.periodNo) }, onResult)

    private fun run(action: suspend (classType: String, section: String) -> Unit, onResult: (error: String?) -> Unit) {
        val state = _uiState.value
        val classType = state.classType ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                action(classType, state.section)
                onResult(null)
                refresh()
            } catch (e: Exception) {
                onResult(e.message ?: "Request failed")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun load(refresh: Boolean) {
        val state = _uiState.value
        val classType = state.classType ?: return
        val section = state.section
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = !refresh, isRefreshing = refresh, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val periods = repository.getPeriods(classType, section)
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, periods = periods) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}

private fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun DayOfWeek.full(): String = getDisplayName(TextStyle.FULL, Locale.ENGLISH)

// Mirrors the web portal's timetable.html for staff: pick a class and section, then add a subject to an empty
// period or remove one. The web grid (days × P1..P8) is shown one day at a time; tapping a filled period
// edits it (the backend overwrites the cell), which the web page has no button for.
@Composable
fun TimetableManageScreen(
    canPickTeacher: Boolean,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimetableManageViewModel = viewModel {
        TimetableManageViewModel(AppContainer.timetableRepository, canPickTeacher)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now().dayOfWeek
    var day by rememberSaveable { mutableStateOf(if (today in TimetableDays) today else DayOfWeek.MONDAY) }
    var editingPeriod by rememberSaveable { mutableStateOf<Int?>(null) }
    var deletingPeriod by rememberSaveable { mutableStateOf<Int?>(null) }
    val periods = state.periods.orEmpty()

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectField(
                label = "Class",
                placeholder = "Select Class",
                value = state.classType?.let(::classDisplayName),
                options = SchoolClasses,
                optionLabel = ::classDisplayName,
                onSelect = viewModel::setClass,
                modifier = Modifier.weight(1f)
            )
            SelectField(
                label = "Section",
                placeholder = "Section",
                value = state.section,
                options = SchoolSections,
                optionLabel = { it },
                onSelect = viewModel::setSection,
                modifier = Modifier.weight(1f)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TimetableDays) { d ->
                val count = periods.count { it.day == d }
                FilterChip(
                    selected = d == day,
                    onClick = { day = d },
                    label = { Text(if (count > 0) "${d.short()} ($count)" else d.short()) }
                )
            }
        }
        when {
            state.classType == null -> ListCard(Modifier.padding(16.dp)) { EmptyText("Select a class to view its timetable") }
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.periods == null -> ErrorState(message = state.error ?: "Could not load the timetable", onRetry = viewModel::refresh)
            else -> DayPeriods(
                day = day,
                periods = periods.filter { it.day == day },
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                onEdit = { editingPeriod = it },
                onDelete = { deletingPeriod = it }
            )
        }
    }

    editingPeriod?.let { periodNo ->
        PeriodDialog(
            title = "${day.full()} — Period $periodNo",
            initial = periods.firstOrNull { it.day == day && it.periodNo == periodNo },
            teachers = state.teachers,
            isSaving = state.isSaving,
            onDismiss = { if (!state.isSaving) editingPeriod = null },
            onSave = { subject, start, end, teacherId ->
                val period = ClassPeriod(day, periodNo, subject, start, end, teacherId, teacherName = null)
                viewModel.save(period) { error ->
                    if (error == null) editingPeriod = null
                    showMessage(error ?: "Period saved")
                }
            }
        )
    }
    deletingPeriod?.let { periodNo ->
        val period = periods.firstOrNull { it.day == day && it.periodNo == periodNo }
        if (period == null) {
            deletingPeriod = null
        } else {
            AlertDialog(
                onDismissRequest = { if (!state.isSaving) deletingPeriod = null },
                title = { Text("Remove this period?") },
                text = { Text("${day.full()}, Period $periodNo: ${period.subject}") },
                confirmButton = {
                    TextButton(enabled = !state.isSaving, onClick = {
                        viewModel.delete(period) { error ->
                            deletingPeriod = null
                            showMessage(error ?: "Period removed")
                        }
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deletingPeriod = null }, enabled = !state.isSaving) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPeriods(
    day: DayOfWeek,
    periods: List<ClassPeriod>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEdit: (periodNo: Int) -> Unit,
    onDelete: (periodNo: Int) -> Unit
) {
    // P1..P8 like the web grid, plus any later period already saved.
    val lastPeriod = maxOf(TIMETABLE_PERIODS, periods.maxOfOrNull { it.periodNo } ?: 0)
    val byPeriod = periods.associateBy { it.periodNo }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "${day.full()} • ${periods.size} of $lastPeriod periods set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items((1..lastPeriod).toList(), key = { it }) { periodNo ->
                val period = byPeriod[periodNo]
                if (period == null) {
                    EmptyPeriodRow(periodNo, onAdd = { onEdit(periodNo) })
                } else {
                    PeriodCard(period, onEdit = { onEdit(periodNo) }, onDelete = { onDelete(periodNo) })
                }
            }
        }
    }
}

@Composable
private fun PeriodLabel(periodNo: Int) {
    Text(
        "P$periodNo",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.width(36.dp)
    )
}

@Composable
private fun PeriodCard(period: ClassPeriod, onEdit: () -> Unit, onDelete: () -> Unit) {
    ListCard {
        Row(
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PeriodLabel(period.periodNo)
            Column(Modifier.weight(1f)) {
                Text(period.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        timeRange(period.startTime, period.endTime),
                        period.teacherName?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ").ifEmpty { "Tap to add time" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove period", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun timeRange(start: String, end: String): String? = when {
    start.isBlank() && end.isBlank() -> null
    else -> "${start.ifBlank { "--:--" }} – ${end.ifBlank { "--:--" }}"
}

@Composable
private fun EmptyPeriodRow(periodNo: Int, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeriodLabel(periodNo)
        Text(
            "Free",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add period", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

// The web "Add Period" modal (start / end time, subject, optional teacher); also edits a filled period.
@Composable
private fun PeriodDialog(
    title: String,
    initial: ClassPeriod?,
    teachers: List<TeacherOption>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (subject: String, start: String, end: String, teacherId: Int) -> Unit
) {
    var start by rememberSaveable { mutableStateOf(initial?.startTime.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(initial?.endTime.orEmpty()) }
    var subject by rememberSaveable { mutableStateOf(initial?.subject.orEmpty()) }
    // Kept as-is when the teacher list isn't available, so editing doesn't drop the assigned teacher.
    var teacherId by rememberSaveable { mutableStateOf(initial?.teacherId ?: 0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { TimeField("Start time", start) { start = it } }
                    Box(Modifier.weight(1f)) { TimeField("End time", end) { end = it } }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("e.g. Mathematics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachers.isNotEmpty()) {
                    SelectField(
                        label = "Teacher (optional)",
                        placeholder = "-- None --",
                        value = teachers.firstOrNull { it.id == teacherId }?.name,
                        options = listOf<TeacherOption?>(null) + teachers,
                        optionLabel = { it?.name ?: "-- None --" },
                        onSelect = { teacherId = it?.id ?: 0 }
                    )
                } else if (initial?.teacherName != null) {
                    Text(
                        "Teacher: ${initial.teacherName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val from = runCatching { LocalTime.parse(start) }.getOrNull()
                    val to = runCatching { LocalTime.parse(end) }.getOrNull()
                    error = when {
                        subject.isBlank() -> "Enter subject"
                        // The student timetable needs both times to place the period.
                        from == null || to == null -> "Select start and end time"
                        !to.isAfter(from) -> "End time must be after start time"
                        else -> null
                    }
                    if (error == null) onSave(subject.trim(), start, end, teacherId)
                }
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}
