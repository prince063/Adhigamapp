package com.example.adhigam.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.AttendanceDay
import com.example.adhigam.data.model.AttendanceStatus
import com.example.adhigam.data.model.AttendanceSummary
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.Indigo700
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val HeaderDark = Color(0xFF1E1B4B)

// The web portal flags attendance below this in red.
private const val MIN_ATTENDANCE = 75

// Mirrors the web portal's attendance summary + heatmap, laid out as a month calendar.
@Composable
fun AttendanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = viewModel { AttendanceViewModel(AppContainer.studentRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.summary == null -> ErrorState(
            message = state.error ?: "Could not load attendance",
            onRetry = viewModel::refresh,
            modifier = modifier
        )
        else -> AttendanceContent(
            summary = state.summary!!,
            month = state.month,
            canGoBack = state.canGoBack,
            canGoForward = state.canGoForward,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceContent(
    summary: AttendanceSummary,
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthSummary = remember(summary, month) { summary.forMonth(month) }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { SessionCard(summary) }
            item {
                MonthCard(
                    monthSummary = monthSummary,
                    month = month,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth
                )
            }
            item {
                Text("Absent days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                AbsencesCard(monthSummary)
            }
        }
    }
}

@Composable
private fun percentColor(percentage: Int): Color = when {
    percentage >= 85 -> SuccessGreen
    percentage >= MIN_ATTENDANCE -> Amber600
    else -> ErrorRed
}

@Composable
private fun SessionCard(summary: AttendanceSummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeaderDark, Indigo700)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { summary.percentage / 100f },
                    modifier = Modifier.size(84.dp),
                    color = Color.White,
                    strokeWidth = 8.dp,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${summary.percentage}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Session attendance", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                Text(
                    "${summary.presentDays} of ${summary.totalDays} days present",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (summary.percentage >= MIN_ATTENDANCE) "Above $MIN_ATTENDANCE% minimum"
                    else "Below $MIN_ATTENDANCE% minimum",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (summary.percentage >= MIN_ATTENDANCE) SuccessGreen.copy(alpha = 0.6f)
                            else ErrorRed.copy(alpha = 0.7f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MonthCard(
    monthSummary: AttendanceSummary,
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNextMonth, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthStat("Present", monthSummary.presentDays.toString(), SuccessGreen, Modifier.weight(1f))
                MonthStat("Absent", monthSummary.absentDays.toString(), ErrorRed, Modifier.weight(1f))
                MonthStat(
                    "Attendance",
                    if (monthSummary.totalDays == 0) "—" else "${monthSummary.percentage}%",
                    percentColor(monthSummary.percentage),
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            CalendarGrid(monthSummary, month)
            Spacer(Modifier.height(12.dp))
            Legend(showLeave = monthSummary.leaveDays > 0)
        }
    }
}

@Composable
private fun MonthStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun statusColor(status: AttendanceStatus): Color = when (status) {
    AttendanceStatus.Present -> SuccessGreen
    AttendanceStatus.Absent -> ErrorRed
    AttendanceStatus.Leave -> Amber600
    AttendanceStatus.Holiday, AttendanceStatus.NotMarked -> MaterialTheme.colorScheme.outline
}

@Composable
private fun CalendarGrid(monthSummary: AttendanceSummary, month: YearMonth) {
    val statusByDate = remember(monthSummary) { monthSummary.days.associate { it.date to it.status } }
    val today = LocalDate.now()
    // Weeks start on Monday; leading blanks pad the first week.
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        if (date != null) DayCell(date, statusByDate[date], isToday = date == today, isFuture = date.isAfter(today))
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, status: AttendanceStatus?, isToday: Boolean, isFuture: Boolean) {
    val color = status?.let { statusColor(it) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(color?.copy(alpha = 0.15f) ?: Color.Transparent)
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (status != null) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    color != null -> color
                    isFuture || date.dayOfWeek == DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (status != null) {
                Text(status.shortLabel, style = MaterialTheme.typography.labelSmall, color = color!!)
            }
        }
    }
}

@Composable
private fun Legend(showLeave: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem("Present", SuccessGreen)
        LegendItem("Absent", ErrorRed)
        if (showLeave) LegendItem("Leave", Amber600)
        LegendItem("No record", MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AbsencesCard(monthSummary: AttendanceSummary) {
    val absences = monthSummary.days
        .filter { it.status == AttendanceStatus.Absent || it.status == AttendanceStatus.Leave }
        .sortedByDescending { it.date }
    ListCard {
        if (absences.isEmpty()) {
            EmptyText(if (monthSummary.totalDays == 0) "No attendance logged this month" else "No absences this month")
        }
        absences.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatFullDate(day.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(day.status.label, statusColor(day.status))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun AttendanceContentPreview() {
    // Preview-only sample; the app loads /student/my-attendance.
    val today = LocalDate.now()
    val summary = AttendanceSummary(
        (0L until 20L).map { today.minusDays(it) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY }
            .map { AttendanceDay(it, if (it.dayOfMonth % 7 == 3) AttendanceStatus.Absent else AttendanceStatus.Present) }
            .sortedBy { it.date }
    )
    AdhigamTheme {
        AttendanceContent(
            summary = summary,
            month = YearMonth.now(),
            canGoBack = true,
            canGoForward = false,
            isRefreshing = false,
            onRefresh = {},
            onPreviousMonth = {},
            onNextMonth = {}
        )
    }
}
