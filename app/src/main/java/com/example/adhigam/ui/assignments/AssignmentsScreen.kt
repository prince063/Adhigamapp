package com.example.adhigam.ui.assignments

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.staff.LoadStateContent
import com.example.adhigam.ui.staff.LoadViewModel
import com.example.adhigam.ui.util.formatShortDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AssignmentsViewModel : LoadViewModel<List<Assignment>>({ AppContainer.studentRepository.getAssignments() })

// Mirrors download-assignment.html ("My Assignments"): class assignments, newest first, with download.
@Composable
fun AssignmentsScreen(
    onDownload: (Assignment) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssignmentsViewModel = viewModel { AssignmentsViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { assignments ->
        AssignmentsContent(assignments, state.isRefreshing, viewModel::refresh, onDownload, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentsContent(
    assignments: List<Assignment>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDownload: (Assignment) -> Unit,
    modifier: Modifier = Modifier
) {
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val subjects = assignments.map { it.subject }.distinct()
    val today = LocalDate.now()
    val shown = assignments
        .filter { subject == null || it.subject == subject }
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.subject.contains(query, ignoreCase = true) }
        .sortedByDescending { it.createdDate }
    // Split into this week and earlier so new work stands out.
    val (recent, earlier) = shown.partition { a ->
        a.createdDate?.let { ChronoUnit.DAYS.between(it, today) < 7 } ?: false
    }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search assignments") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            if (subjects.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = subject == null, onClick = { subject = null }, label = { Text("All") }) }
                        items(subjects) { s ->
                            FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    ListCard {
                        EmptyText(if (assignments.isEmpty()) "No assignments found for this section." else "No assignment matches your filter")
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { GroupTitle("This week") }
                items(recent, key = { "r-" + it.title + it.createdDate }) { AssignmentCard(it, today, onDownload) }
            }
            if (earlier.isNotEmpty()) {
                item { GroupTitle("Earlier") }
                items(earlier, key = { "e-" + it.title + it.createdDate }) { AssignmentCard(it, today, onDownload) }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

private fun uploadedLabel(date: LocalDate?, today: LocalDate): String = when {
    date == null -> ""
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    else -> formatShortDate(date)
}

@Composable
internal fun AssignmentCard(assignment: Assignment, today: LocalDate, onDownload: (Assignment) -> Unit) {
    val hasFile = assignment.fileUrl.isNotBlank()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // File type badge (PDF, DOCX, JPG …)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (hasFile) assignment.fileExtension.take(4).ifBlank { "FILE" } else "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StatusPill(assignment.subject, MaterialTheme.colorScheme.primary)
                Text(
                    assignment.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val classLabel = classDisplayName(assignment.classType) + if (assignment.section.isNotBlank()) "-${assignment.section}" else ""
                val uploaded = uploadedLabel(assignment.createdDate, today)
                Text(
                    if (uploaded.isEmpty()) classLabel else "$classLabel • Uploaded $uploaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (hasFile) {
                OutlinedButton(onClick = { onDownload(assignment) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download")
                }
            } else {
                Text("No file", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
