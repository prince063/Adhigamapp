package com.example.adhigam.ui.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.StaffAttendanceEntry
import com.example.adhigam.data.model.StaffAttendanceStatuses
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// staff-attendance.html: pick a date, set each staff member's status, in / out time and remarks, then Save All.
@Composable
fun StaffAttendanceScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaffAttendanceViewModel = viewModel { StaffAttendanceViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    StaffAttendanceContent(
        state = state,
        onSelectDate = viewModel::selectDate,
        onUpdate = viewModel::update,
        onMarkAll = viewModel::markAll,
        onRetry = viewModel::load,
        onSave = viewModel::save,
        modifier = modifier
    )
}

@Composable
private fun StaffAttendanceContent(
    state: StaffAttendanceUiState,
    onSelectDate: (LocalDate) -> Unit,
    onUpdate: (Int, (StaffAttendanceEntry) -> StaffAttendanceEntry) -> Unit,
    onMarkAll: (String) -> Unit,
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
            item { DateCard(state.date, onSelectDate) }
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> item {
                    Box(Modifier.heightIn(min = 240.dp)) { ErrorState(state.error, onRetry = onRetry) }
                }
                state.entries.isEmpty() -> item { ListCard { EmptyText("No staff found") } }
                else -> {
                    item {
                        ListCard {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StaffAttendanceStatuses.forEach { status ->
                                        StatBox(status, state.count(status).toString(), statusColor(status), Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.size(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onMarkAll("Present") }, modifier = Modifier.weight(1f)) {
                                        Text("All Present", color = SuccessGreen)
                                    }
                                    OutlinedButton(onClick = { onMarkAll("Absent") }, modifier = Modifier.weight(1f)) {
                                        Text("All Absent", color = ErrorRed)
                                    }
                                }
                            }
                        }
                    }
                    items(state.entries, key = { it.userId }) { entry ->
                        StaffRow(entry = entry, onChange = { change -> onUpdate(entry.userId, change) })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !state.isLoading && state.error == null && state.entries.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${state.entries.size} staff",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (state.hasChanges) "Not saved yet" else "No unsaved changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.hasChanges) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onSave, enabled = !state.isSaving) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…")
                        } else {
                            Text("Save All")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCard(date: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    ListCard {
        Column(Modifier.padding(16.dp)) {
            PickerField(
                label = "Date",
                value = if (date == LocalDate.now()) "Today, ${formatFullDate(date)}" else formatFullDate(date),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showPicker = true }
            )
        }
    }
    if (showPicker) {
        val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onSelectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun StaffRow(entry: StaffAttendanceEntry, onChange: ((StaffAttendanceEntry) -> StaffAttendanceEntry) -> Unit) {
    ListCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(
                    entry.staffName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.designation.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StaffAttendanceStatuses.forEach { status ->
                    StatusChip(status, selected = entry.status == status) { onChange { it.copy(status = status) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    TimeField("In Time", entry.inTime) { time -> onChange { it.copy(inTime = time) } }
                }
                Box(Modifier.weight(1f)) {
                    TimeField("Out Time", entry.outTime) { time -> onChange { it.copy(outTime = time) } }
                }
            }
            OutlinedTextField(
                value = entry.remarks,
                onValueChange = { text -> onChange { it.copy(remarks = text) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Remarks") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun StatusChip(status: String, selected: Boolean, onClick: () -> Unit) {
    val color = statusColor(status)
    Text(
        status,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// "HH:mm" like the web's <input type="time">; blank until picked. Also used by the timetable editor.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    PickerField(
        label = label,
        value = value.ifBlank { "--:--" },
        isPlaceholder = value.isBlank(),
        icon = Icons.Outlined.Schedule,
        onClick = { showPicker = true }
    )
    if (showPicker) {
        val parts = value.split(":")
        val pickerState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (value.isNotBlank()) {
                        TextButton(onClick = { onChange(""); showPicker = false }) { Text("Clear") }
                    }
                    TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}

private fun statusColor(status: String): Color = when (status) {
    "Present" -> SuccessGreen
    "Absent" -> ErrorRed
    "Late" -> Amber600
    else -> Color(0xFF2563EB) // Half Day
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun StaffAttendancePreview() {
    val entries = listOf(
        StaffAttendanceEntry(1, "Rakesh Gupta", "Teacher", "Present", "08:55", "", ""),
        StaffAttendanceEntry(2, "Sunita Rao", "Admin", "Late", "09:40", "", "Traffic")
    )
    AdhigamTheme {
        StaffAttendanceContent(
            state = StaffAttendanceUiState(isLoading = false, entries = entries, original = entries),
            onSelectDate = {}, onUpdate = { _, _ -> }, onMarkAll = {}, onRetry = {}, onSave = {}
        )
    }
}
