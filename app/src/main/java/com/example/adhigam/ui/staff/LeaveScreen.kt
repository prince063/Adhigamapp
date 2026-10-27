package com.example.adhigam.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.LeaveRequest
import com.example.adhigam.data.model.LeaveStatus
import com.example.adhigam.data.model.LeaveTypes
import com.example.adhigam.data.repository.LeaveRepository
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import com.example.adhigam.ui.util.formatShortDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LeaveViewModel(
    private val repository: LeaveRepository,
    private val statusFilter: MutableStateFlow<LeaveStatus?> = MutableStateFlow(null)
) : LoadViewModel<List<LeaveRequest>>({ repository.getLeaves(statusFilter.value) }) {

    val status: StateFlow<LeaveStatus?> = statusFilter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun setStatus(status: LeaveStatus?) {
        if (status == statusFilter.value) return
        statusFilter.value = status
        refresh()
    }

    // onResult gets null on success or the error to show.
    fun apply(from: LocalDate, to: LocalDate, type: String, reason: String, onResult: (error: String?) -> Unit) =
        run({ repository.apply(from, to, type, reason) }, onResult)

    fun cancel(leave: LeaveRequest, onResult: (error: String?) -> Unit) = run({ repository.cancel(leave.id) }, onResult)

    fun decide(leave: LeaveRequest, status: LeaveStatus, remarks: String, onResult: (error: String?) -> Unit) =
        run({ repository.decide(leave.id, status, remarks) }, onResult)

    private fun run(action: suspend () -> Unit, onResult: (error: String?) -> Unit) {
        if (_isSaving.value) return
        _isSaving.update { true }
        viewModelScope.launch {
            try {
                action()
                onResult(null)
                refresh()
            } catch (e: Exception) {
                onResult(e.message ?: "Request failed")
            } finally {
                _isSaving.update { false }
            }
        }
    }
}

// What the person is doing with a request: cancelling it (teacher) or approving / rejecting it (admin).
private enum class LeaveAction { Cancel, Approve, Reject }

// Mirrors the web portal's leave-management.html. Everyone can apply for leave; a teacher sees and can cancel
// their own pending requests, an admin sees everyone's and approves or rejects the pending ones.
@Composable
fun LeaveScreen(
    isAdmin: Boolean,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveViewModel = viewModel { LeaveViewModel(AppContainer.leaveRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var showApply by rememberSaveable { mutableStateOf(false) }
    var actionId by rememberSaveable { mutableStateOf<Int?>(null) }
    var action by rememberSaveable { mutableStateOf<LeaveAction?>(null) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(selected = status == null, onClick = { viewModel.setStatus(null) }, label = { Text("All") }) }
                items(LeaveStatus.entries) { s ->
                    FilterChip(selected = status == s, onClick = { viewModel.setStatus(s) }, label = { Text(s.value) })
                }
            }
            LoadStateContent(state, onRetry = viewModel::refresh) { leaves ->
                LeaveList(
                    leaves = leaves,
                    isAdmin = isAdmin,
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onAction = { leave, a -> actionId = leave.id; action = a }
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showApply = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Apply Leave") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (showApply) {
        ApplyLeaveDialog(
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showApply = false },
            onSubmit = { from, to, type, reason ->
                viewModel.apply(from, to, type, reason) { error ->
                    if (error == null) showApply = false
                    showMessage(error ?: "Leave applied")
                }
            }
        )
    }

    val leave = state.data?.firstOrNull { it.id == actionId }
    val pending = action
    if (leave != null && pending != null) {
        val close = { actionId = null; action = null }
        when (pending) {
            LeaveAction.Cancel -> AlertDialog(
                onDismissRequest = { if (!isSaving) close() },
                title = { Text("Cancel this leave request?") },
                text = { Text("${leave.leaveType} leave, ${leave.dateRange()}") },
                confirmButton = {
                    TextButton(enabled = !isSaving, onClick = {
                        viewModel.cancel(leave) { error ->
                            close()
                            showMessage(error ?: "Leave request cancelled")
                        }
                    }) { Text("Cancel request", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = close, enabled = !isSaving) { Text("Keep") } }
            )
            LeaveAction.Approve, LeaveAction.Reject -> DecideLeaveDialog(
                leave = leave,
                approve = pending == LeaveAction.Approve,
                isSaving = isSaving,
                onDismiss = { if (!isSaving) close() },
                onConfirm = { remarks ->
                    val decision = if (pending == LeaveAction.Approve) LeaveStatus.Approved else LeaveStatus.Rejected
                    viewModel.decide(leave, decision, remarks) { error ->
                        close()
                        showMessage(error ?: if (decision == LeaveStatus.Approved) "Leave approved" else "Leave rejected")
                    }
                }
            )
        }
    }
}

private fun LeaveRequest.dateRange(): String =
    if (fromDate == toDate) formatFullDate(fromDate) else "${formatShortDate(fromDate)} – ${formatFullDate(toDate)}"

@Composable
private fun statusColor(status: LeaveStatus): Color = when (status) {
    LeaveStatus.Pending -> Amber600
    LeaveStatus.Approved -> SuccessGreen
    LeaveStatus.Rejected -> ErrorRed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveList(
    leaves: List<LeaveRequest>,
    isAdmin: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAction: (LeaveRequest, LeaveAction) -> Unit
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Room at the bottom so the last card isn't hidden behind the Apply Leave button.
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leaves.isEmpty()) {
                item { ListCard { EmptyText("No leave requests") } }
            }
            items(leaves, key = { it.id }) { leave ->
                LeaveCard(leave, isAdmin, onAction = { onAction(leave, it) })
            }
        }
    }
}

@Composable
private fun LeaveCard(leave: LeaveRequest, isAdmin: Boolean, onAction: (LeaveAction) -> Unit) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Admins see everyone's requests, so lead with the name; a teacher only sees their own.
                    Text(
                        if (isAdmin) leave.staffName else "${leave.leaveType} leave",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isAdmin) listOf(leave.designation, "${leave.leaveType} leave").filter { it.isNotBlank() }.joinToString(" • ")
                        else leave.dateRange(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(leave.status.value, statusColor(leave.status))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                (if (isAdmin) leave.dateRange() + " • " else "") + if (leave.days == 1) "1 day" else "${leave.days} days",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (leave.reason.isNotBlank()) {
                Text(leave.reason, style = MaterialTheme.typography.bodyMedium)
            }
            if (leave.status != LeaveStatus.Pending && (leave.approvedBy != null || leave.remarks != null)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        leave.approvedBy?.let { "${leave.status.value} by $it" },
                        leave.remarks?.let { "\"$it\"" }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Same buttons as the web table: admins decide pending requests, others can cancel them.
            if (leave.status == LeaveStatus.Pending) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isAdmin) {
                        Button(
                            onClick = { onAction(LeaveAction.Approve) },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.weight(1f)
                        ) { Text("Approve") }
                        Button(
                            onClick = { onAction(LeaveAction.Reject) },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            modifier = Modifier.weight(1f)
                        ) { Text("Reject") }
                    } else {
                        OutlinedButton(onClick = { onAction(LeaveAction.Cancel) }) { Text("Cancel request") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecideLeaveDialog(
    leave: LeaveRequest,
    approve: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (remarks: String) -> Unit
) {
    var remarks by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (approve) "Approve Leave" else "Reject Leave") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${leave.staffName}: ${leave.leaveType} leave, ${leave.dateRange()} (${leave.days} day(s))")
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = { onConfirm(remarks.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = if (approve) SuccessGreen else ErrorRed)
            ) { Text(if (isSaving) "Saving…" else if (approve) "Approve" else "Reject") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

private enum class DateTarget { From, To }

// The web "Apply for Leave" modal.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyLeaveDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (from: LocalDate, to: LocalDate, type: String, reason: String) -> Unit
) {
    var from by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var to by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var type by rememberSaveable { mutableStateOf(LeaveTypes.first()) }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf<DateTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply for Leave") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PickerField(
                    label = "From date",
                    value = from?.let(::formatFullDate) ?: "Select date",
                    icon = Icons.Outlined.Event,
                    onClick = { picking = DateTarget.From },
                    isPlaceholder = from == null
                )
                PickerField(
                    label = "To date",
                    value = to?.let(::formatFullDate) ?: "Select date",
                    icon = Icons.Outlined.Event,
                    onClick = { picking = DateTarget.To },
                    isPlaceholder = to == null
                )
                SelectField(
                    label = "Leave type",
                    placeholder = "Select type",
                    value = type,
                    options = LeaveTypes,
                    optionLabel = { it },
                    onSelect = { type = it }
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    placeholder = { Text("Brief reason...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val start = from
                    val end = to
                    // Same checks as leave-management.html.
                    error = when {
                        start == null || end == null -> "Select dates"
                        end < start -> "To date must be after From date"
                        else -> null
                    }
                    if (start != null && end != null && error == null) onSubmit(start, end, type, reason.trim())
                }
            ) { Text(if (isSaving) "Submitting…" else "Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )

    picking?.let { target ->
        val current = if (target == DateTarget.From) from else to ?: from
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (current ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == DateTarget.From) {
                            from = date
                            // Keep the range valid: a single-day leave is the common case.
                            val end = to
                            if (end == null || end < date) to = date
                        } else {
                            to = date
                        }
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
