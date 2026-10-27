package com.example.adhigam.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.TimetableSlot
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.staff.LoadStateContent
import com.example.adhigam.ui.staff.LoadViewModel
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatClock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class TimetableViewModel : LoadViewModel<List<TimetableSlot>>({ AppContainer.studentRepository.getTimetable() })

private fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
private fun DayOfWeek.full(): String = getDisplayName(TextStyle.FULL, Locale.ENGLISH)

// Gaps of this length or more between periods are shown as a break.
private const val BREAK_MINUTES = 10L

// The web timetable grid (timetable.html, read-only for students) as a day list plus a week grid.
@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    viewModel: TimetableViewModel = viewModel { TimetableViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { slots ->
        TimetableContent(slots, isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableContent(
    slots: List<TimetableSlot>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val days = slots.map { it.dayOfWeek }.distinct().sorted()
    val today = LocalDate.now().dayOfWeek
    // Start on today, or the next school day (e.g. Monday when opened on a Sunday).
    val defaultDay = days.firstOrNull { it >= today } ?: days.firstOrNull() ?: DayOfWeek.MONDAY
    var selectedDay by rememberSaveable { mutableStateOf(defaultDay) }
    var weekView by rememberSaveable { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !weekView,
                        onClick = { weekView = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Day") }
                    SegmentedButton(
                        selected = weekView,
                        onClick = { weekView = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Week") }
                }
            }
            if (slots.isEmpty()) {
                item { ListCard { EmptyText("Timetable has not been published for your class yet") } }
            } else if (weekView) {
                item { WeekGrid(slots, days, today) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(days) { day ->
                            FilterChip(
                                selected = day == selectedDay,
                                onClick = { selectedDay = day },
                                label = { Text(if (day == today) "${day.short()} • Today" else day.short()) }
                            )
                        }
                    }
                }
                item {
                    Text(
                        if (selectedDay == today) "${selectedDay.full()} (today)" else selectedDay.full(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val daySlots = slots.filter { it.dayOfWeek == selectedDay }.sortedBy { it.periodNo }
                if (daySlots.isEmpty()) {
                    item { ListCard { EmptyText("No period slots scheduled for ${selectedDay.full()}") } }
                }
                daySlots.forEachIndexed { i, slot ->
                    val previous = daySlots.getOrNull(i - 1)
                    if (previous != null && Duration.between(previous.endTime, slot.startTime).toMinutes() >= BREAK_MINUTES) {
                        item(key = "break-${slot.periodNo}") { BreakRow(previous.endTime, slot.startTime) }
                    }
                    item(key = "period-${slot.periodNo}") {
                        PeriodRow(slot, isNow = selectedDay == today && isNow(slot))
                    }
                }
            }
        }
    }
}

private fun isNow(slot: TimetableSlot, now: LocalTime = LocalTime.now()): Boolean =
    !now.isBefore(slot.startTime) && now.isBefore(slot.endTime)

@Composable
private fun PeriodRow(slot: TimetableSlot, isNow: Boolean) {
    val accent = if (isNow) SuccessGreen else MaterialTheme.colorScheme.primary
    Row(Modifier.height(IntrinsicSize.Min)) {
        // Time column
        Column(Modifier.width(56.dp).padding(top = 12.dp), horizontalAlignment = Alignment.End) {
            Text(formatClock(slot.startTime), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(formatClock(slot.endTime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isNow) SuccessGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Period ${slot.periodNo}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                if (isNow) StatusPill("Now", SuccessGreen)
            }
            Text(slot.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            slot.teacher?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BreakRow(from: LocalTime, to: LocalTime) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(56.dp + 12.dp + 3.dp + 12.dp))
        Text(
            "Break • ${formatClock(from)} – ${formatClock(to)} (${Duration.between(from, to).toMinutes()} min)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// Days as columns and periods as rows, like the web grid; scrolls sideways on a phone.
@Composable
private fun WeekGrid(slots: List<TimetableSlot>, days: List<DayOfWeek>, today: DayOfWeek) {
    val periods = slots.map { it.periodNo }.distinct().sorted()
    val bySlot = slots.associateBy { it.dayOfWeek to it.periodNo }
    val cellWidth = 112.dp
    val border = MaterialTheme.colorScheme.outlineVariant

    ListCard {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            // Period labels
            Column {
                GridHeader("Period", Modifier.width(56.dp))
                periods.forEach { p ->
                    Box(Modifier.width(56.dp).height(76.dp), contentAlignment = Alignment.CenterStart) {
                        Text("P$p", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            days.forEach { day ->
                val isToday = day == today
                Column(
                    Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                ) {
                    GridHeader(if (isToday) "${day.short()} • Today" else day.full(), Modifier.width(cellWidth), highlight = isToday)
                    periods.forEach { p ->
                        val slot = bySlot[day to p]
                        Box(
                            Modifier
                                .width(cellWidth)
                                .height(76.dp)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, border, RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp)
                        ) {
                            if (slot == null) {
                                Text("Free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.Center))
                            } else {
                                Column {
                                    Text(
                                        slot.subject,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    slot.teacher?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${formatClock(slot.startTime)}–${formatClock(slot.endTime)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridHeader(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .heightIn(min = 32.dp)
            .padding(vertical = 8.dp)
    )
}
