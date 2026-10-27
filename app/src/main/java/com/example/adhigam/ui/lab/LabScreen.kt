package com.example.adhigam.ui.lab

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.LabAllocation
import com.example.adhigam.data.model.LabSessionState
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.staff.LoadStateContent
import com.example.adhigam.ui.staff.LoadViewModel
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatClock
import com.example.adhigam.ui.util.formatShortDate
import java.time.LocalDate
import java.time.LocalDateTime

class LabViewModel : LoadViewModel<List<LabAllocation>>({ AppContainer.resultsLabRepository.getLabSessions() })

// Mirrors the parent portal's "Laboratory Session Allocations": experiment, lab, equipment unit,
// slot and whether the equipment has been returned.
@Composable
fun LabScreen(
    modifier: Modifier = Modifier,
    viewModel: LabViewModel = viewModel { LabViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { sessions ->
        LabContent(sessions, state.isRefreshing, viewModel::refresh, modifier)
    }
}

@Composable
private fun stateColor(state: LabSessionState): Color = when (state) {
    LabSessionState.InSession -> SuccessGreen
    LabSessionState.Scheduled -> MaterialTheme.colorScheme.primary
    LabSessionState.ReturnPending -> ErrorRed
    LabSessionState.Returned -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabContent(
    sessions: List<LabAllocation>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = LocalDateTime.now()
    val byState = sessions.groupBy { it.state(now) }
    val active = (byState[LabSessionState.InSession].orEmpty() + byState[LabSessionState.ReturnPending].orEmpty())
        .sortedBy { it.startTime }
    val upcoming = byState[LabSessionState.Scheduled].orEmpty().sortedBy { it.startTime }
    val completed = byState[LabSessionState.Returned].orEmpty().sortedByDescending { it.startTime }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountBox("In session", byState[LabSessionState.InSession].orEmpty().size, SuccessGreen, Modifier.weight(1f))
                    CountBox("Upcoming", upcoming.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    CountBox("To return", byState[LabSessionState.ReturnPending].orEmpty().size, ErrorRed, Modifier.weight(1f))
                }
            }
            if (sessions.isEmpty()) {
                item { ListCard { EmptyText("No laboratory experiments checked out.") } }
            }
            if (active.isNotEmpty()) {
                item { GroupTitle("Now") }
                items(active, key = { it.id }) { LabCard(it, now) }
            }
            if (upcoming.isNotEmpty()) {
                item { GroupTitle("Upcoming") }
                items(upcoming, key = { it.id }) { LabCard(it, now) }
            }
            if (completed.isNotEmpty()) {
                item { GroupTitle("Completed") }
                items(completed, key = { it.id }) { LabCard(it, now) }
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
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> formatShortDate(date)
    }
}

@Composable
private fun LabCard(lab: LabAllocation, now: LocalDateTime) {
    val state = lab.state(now)
    val color = stateColor(state)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Science, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lab.experimentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(state.label, color)
                }
                Text(lab.labName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${dayLabel(lab.startTime.toLocalDate())} • ${formatClock(lab.startTime.toLocalTime())} – ${formatClock(lab.endTime.toLocalTime())}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                // Equipment unit, as on the web ("Microscope (MIC-07)").
                Text(
                    "${lab.equipmentName} (${lab.internalUid})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                val details = buildList {
                    lab.issuedAt?.let { add("Issued ${formatClock(it.toLocalTime())}") }
                    lab.returnedAt?.let { add("Returned ${formatClock(it.toLocalTime())}") }
                    lab.conditionPre?.let { add("Condition: $it" + (lab.conditionPost?.let { post -> " → $post" } ?: "")) }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (state == LabSessionState.ReturnPending) {
                    Text(
                        "Session has ended — please return the equipment to the lab assistant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (lab.conditionPost != null && lab.conditionPre != null && lab.conditionPost != lab.conditionPre) {
                    Text(
                        "Equipment condition changed during the session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber600,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
