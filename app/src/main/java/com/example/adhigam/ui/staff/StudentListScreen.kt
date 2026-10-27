package com.example.adhigam.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatRupees

@Composable
fun StudentListScreen(
    onOpenProfile: (String) -> Unit,
    onOpenFees: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenMarks: (String) -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentListViewModel = viewModel { StudentListViewModel(AppContainer.studentListRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refresh after returning from a profile / edit screen; the first resume is the initial load.
    var resumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumed) viewModel.reload() else resumed = true
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    StudentListContent(
        state = state,
        onRefresh = viewModel::refresh,
        onStatus = viewModel::setStatus,
        onClass = viewModel::setClass,
        onSection = viewModel::setSection,
        onQuery = viewModel::setQuery,
        onApprove = { viewModel.updateStatus(it, StudentStatus.Approved) },
        onReject = { viewModel.updateStatus(it, StudentStatus.Rejected) },
        onResetPassword = viewModel::openResetPassword,
        onOpenProfile = onOpenProfile,
        onOpenFees = onOpenFees,
        onEdit = onEdit,
        onOpenMarks = onOpenMarks,
        modifier = modifier
    )

    state.resetTarget?.let { student ->
        ResetPasswordDialog(
            student = student,
            password = state.newPassword,
            isResetting = state.isResetting,
            onPasswordChange = viewModel::setNewPassword,
            onGenerate = viewModel::generatePassword,
            onConfirm = viewModel::submitResetPassword,
            onDismiss = viewModel::dismissResetPassword
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentListContent(
    state: StudentListUiState,
    onRefresh: () -> Unit,
    onStatus: (StudentStatus?) -> Unit,
    onClass: (String?) -> Unit,
    onSection: (String?) -> Unit,
    onQuery: (String) -> Unit,
    onApprove: (StudentListItem) -> Unit,
    onReject: (StudentListItem) -> Unit,
    onResetPassword: (StudentListItem) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenFees: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenMarks: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatusCards(state, onStatus) }
            item { StudentFilters(state, onClass, onSection, onQuery) }
            item {
                Column {
                    Text(
                        if (state.status == null) "${state.visibleStudents.size} students"
                        else "${state.visibleStudents.size} ${state.status.name.lowercase()} students",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    // Thin bar while a filter change or search is loading over the current list.
                    if (state.isFetching && !state.isLoading && !state.isRefreshing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            when {
                state.isLoading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                state.error != null && state.students.isEmpty() -> item {
                    Box(Modifier.height(260.dp)) { ErrorState(state.error, onRetry = onRefresh) }
                }
                state.visibleStudents.isEmpty() -> item { ListCard { EmptyText("No students found") } }
                else -> items(state.visibleStudents, key = { it.encryptedId }) { student ->
                    StudentCard(
                        student = student,
                        isUpdating = student.encryptedId in state.updatingIds,
                        onApprove = { onApprove(student) },
                        onReject = { onReject(student) },
                        onResetPassword = { onResetPassword(student) },
                        onOpenProfile = { onOpenProfile(student.encryptedId) },
                        onOpenFees = { onOpenFees(student.encryptedId) },
                        onEdit = { onEdit(student.encryptedId) },
                        onOpenMarks = { onOpenMarks(student.encryptedId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: StudentStatus?): Color = when (status) {
    StudentStatus.Approved -> SuccessGreen
    StudentStatus.Pending -> Amber600
    StudentStatus.Rejected -> ErrorRed
    null -> MaterialTheme.colorScheme.primary
}

// Total / Approved / Pending / Rejected; tapping one filters the list, like the web stat cards.
@Composable
private fun StatusCards(state: StudentListUiState, onStatus: (StudentStatus?) -> Unit) {
    val options = listOf<StudentStatus?>(null, StudentStatus.Approved, StudentStatus.Pending, StudentStatus.Rejected)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val selected = state.status == option
            val color = statusColor(option)
            Card(
                onClick = { onStatus(option) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (selected) BorderStroke(1.5.dp, color) else null
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        state.count(option).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        if (option == null) "Total" else option.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentFilters(
    state: StudentListUiState,
    onClass: (String?) -> Unit,
    onSection: (String?) -> Unit,
    onQuery: (String) -> Unit
) {
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name, mobile, admission no…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = "Class",
                    placeholder = "All Classes",
                    value = state.classType?.let(::classDisplayName),
                    options = listOf<String?>(null) + SchoolClasses,
                    optionLabel = { it?.let(::classDisplayName) ?: "All Classes" },
                    onSelect = onClass,
                    modifier = Modifier.weight(1.3f)
                )
                SelectField(
                    label = "Section",
                    placeholder = "All Sections",
                    value = state.section,
                    options = listOf<String?>(null) + SchoolSections,
                    optionLabel = { it ?: "All Sections" },
                    onSelect = onSection,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StudentCard(
    student: StudentListItem,
    isUpdating: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onResetPassword: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFees: () -> Unit,
    onEdit: () -> Unit,
    onOpenMarks: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpenProfile,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initialsOf(student.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            student.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(student.status.name, statusColor(student.status))
                    }
                    Text(
                        "Father: ${student.fatherName ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            student.admissionNo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${classDisplayName(student.classType)}-${student.section} • ${student.mobile}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${student.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Same actions as the web row; Marks and Reset Password only for approved students.
                        DropdownMenuItem(text = { Text("View Profile") }, onClick = { menuOpen = false; onOpenProfile() })
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Fees") }, onClick = { menuOpen = false; onOpenFees() })
                        if (student.status == StudentStatus.Approved) {
                            DropdownMenuItem(text = { Text("Marks") }, onClick = { menuOpen = false; onOpenMarks() })
                            DropdownMenuItem(text = { Text("Reset Password") }, onClick = { menuOpen = false; onResetPassword() })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Due fees ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatRupees(student.dueFee),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (student.dueFee > 0) ErrorRed else SuccessGreen
                )
                Spacer(Modifier.weight(1f))
                if (student.status == StudentStatus.Pending) {
                    if (isUpdating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(onClick = onReject, contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Text("Reject", color = ErrorRed)
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = onApprove, contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Text("Approve")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetPasswordDialog(
    student: StudentListItem,
    password: String,
    isResetting: Boolean,
    onPasswordChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Student Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = student.name,
                    onValueChange = {},
                    label = { Text("Student Name") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = student.admissionNo,
                    onValueChange = {},
                    label = { Text("Admission No.") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("New Password") },
                    placeholder = { Text("Tap Generate →") },
                    singleLine = true,
                    trailingIcon = { TextButton(onClick = onGenerate) { Text("Generate") } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isResetting) {
                Text(if (isResetting) "Resetting…" else "Reset Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isResetting) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun StudentListPreview() {
    val students = listOf(
        StudentListItem("e1", "1801", "Aarav Sharma", "Rakesh Sharma", "9876543210", 4500, "VIII", "A", StudentStatus.Approved),
        StudentListItem("e2", "1802", "Ananya Verma", "Suresh Verma", "9812345678", 0, "VIII", "A", StudentStatus.Pending),
        StudentListItem("e3", "1803", "Kabir Khan", "Anil Khan", "9898989898", 1500, "VIII", "B", StudentStatus.Rejected)
    )
    AdhigamTheme {
        StudentListContent(
            state = StudentListUiState(status = null, isLoading = false, students = students),
            onRefresh = {}, onStatus = {}, onClass = {}, onSection = {}, onQuery = {},
            onApprove = {}, onReject = {}, onResetPassword = {}, onOpenProfile = {}, onOpenFees = {}, onEdit = {}, onOpenMarks = {}
        )
    }
}
