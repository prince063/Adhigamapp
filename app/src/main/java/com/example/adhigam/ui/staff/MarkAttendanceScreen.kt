package com.example.adhigam.ui.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.AttendanceEntry
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun MarkAttendanceScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MarkAttendanceViewModel = viewModel { MarkAttendanceViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    MarkAttendanceContent(
        state = state,
        onSelectClass = viewModel::selectClass,
        onSelectSection = viewModel::selectSection,
        onSelectDate = viewModel::selectDate,
        onQueryChange = viewModel::setQuery,
        onSetPresent = viewModel::setPresent,
        onMarkAll = viewModel::markAll,
        onRetry = viewModel::retry,
        onSave = viewModel::save,
        modifier = modifier
    )
}

@Composable
private fun MarkAttendanceContent(
    state: MarkAttendanceUiState,
    onSelectClass: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onQueryChange: (String) -> Unit,
    onSetPresent: (String, Boolean) -> Unit,
    onMarkAll: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FiltersCard(
                    classType = state.classType,
                    section = state.section,
                    date = state.date,
                    onSelectClass = onSelectClass,
                    onSelectSection = onSelectSection,
                    onSelectDate = onSelectDate
                )
            }
            when {
                !state.isReady -> item { HintCard("Select Class and Section to load students") }
                state.isLoading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                state.error != null -> item {
                    Box(Modifier.heightIn(min = 240.dp)) { ErrorState(state.error, onRetry = onRetry) }
                }
                state.students.isEmpty() -> item { HintCard("No students found") }
                else -> {
                    item {
                        SummaryCard(
                            total = state.students.size,
                            present = state.presentCount,
                            absent = state.absentCount,
                            alreadyMarked = state.alreadyMarked,
                            onMarkAll = onMarkAll
                        )
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
                    if (state.visibleStudents.isEmpty()) {
                        item { HintCard("No student matches \"${state.query}\"") }
                    }
                    items(state.visibleStudents, key = { it.admissionNo }) { student ->
                        StudentRow(
                            number = state.students.indexOf(student) + 1,
                            student = student,
                            onSetPresent = { present -> onSetPresent(student.admissionNo, present) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.isReady && !state.isLoading && state.students.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            SaveBar(
                present = state.presentCount,
                absent = state.absentCount,
                hasChanges = state.hasChanges || !state.alreadyMarked,
                isSaving = state.isSaving,
                onSave = onSave
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersCard(
    classType: String?,
    section: String?,
    date: LocalDate,
    onSelectClass: (String) -> Unit,
    onSelectSection: (String) -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

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
            PickerField(
                label = "Date",
                value = if (date == LocalDate.now()) "Today, ${formatFullDate(date)}" else formatFullDate(date),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showDatePicker = true }
            )
        }
    }

    if (showDatePicker) {
        val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // Attendance can't be marked for future days.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onSelectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// Read-only field that opens a dropdown, like the web's <select>.
@Composable
fun <T> SelectField(
    label: String,
    placeholder: String,
    value: String?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        PickerField(
            label = label,
            value = value ?: placeholder,
            isPlaceholder = value == null,
            icon = Icons.Outlined.KeyboardArrowDown,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isPlaceholder: Boolean = false
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HintCard(text: String) {
    ListCard { EmptyText(text) }
}

@Composable
private fun SummaryCard(total: Int, present: Int, absent: Int, alreadyMarked: Boolean, onMarkAll: (Boolean) -> Unit) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBox("Total", total, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                CountBox("Present", present, SuccessGreen, Modifier.weight(1f))
                CountBox("Absent", absent, ErrorRed, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onMarkAll(true) }, modifier = Modifier.weight(1f)) {
                    Text("All Present", color = SuccessGreen)
                }
                OutlinedButton(onClick = { onMarkAll(false) }, modifier = Modifier.weight(1f)) {
                    Text("All Absent", color = ErrorRed)
                }
            }
            if (alreadyMarked) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Attendance already saved for this day — changes will update it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CountBox(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StudentRow(number: Int, student: AttendanceEntry, onSetPresent: (Boolean) -> Unit) {
    val tint = if (student.isPresent) SuccessGreen else ErrorRed
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            // Light wash of the row's status, like the web's present-row / absent-row.
            Box(
                Modifier
                    .matchParentSize()
                    .background(tint.copy(alpha = 0.06f))
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(number.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        student.studentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Adm No: ${student.admissionNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusToggle(isPresent = student.isPresent, onSetPresent = onSetPresent)
            }
        }
    }
}

// Present / Absent pair; the chosen side is filled with its colour.
@Composable
private fun StatusToggle(isPresent: Boolean, onSetPresent: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
    ) {
        ToggleSide("Present", selected = isPresent, color = SuccessGreen) { onSetPresent(true) }
        ToggleSide("Absent", selected = !isPresent, color = ErrorRed) { onSetPresent(false) }
    }
}

@Composable
private fun ToggleSide(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SaveBar(present: Int, absent: Int, hasChanges: Boolean, isSaving: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$present present • $absent absent",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (hasChanges) "Not saved yet" else "All changes saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasChanges) MaterialTheme.colorScheme.error else SuccessGreen
                )
            }
            Button(onClick = onSave, enabled = !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Text("Save Attendance")
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun MarkAttendancePreview() {
    val students = listOf(
        AttendanceEntry(0, "1801", "Aarav Sharma", true),
        AttendanceEntry(0, "1802", "Ananya Verma", false),
        AttendanceEntry(0, "1803", "Kabir Khan", true)
    )
    AdhigamTheme {
        MarkAttendanceContent(
            state = MarkAttendanceUiState(
                classType = "VIII",
                section = "A",
                students = students,
                original = students.associate { it.admissionNo to true }
            ),
            onSelectClass = {}, onSelectSection = {}, onSelectDate = {}, onQueryChange = {},
            onSetPresent = { _, _ -> }, onMarkAll = {}, onRetry = {}, onSave = {}
        )
    }
}
