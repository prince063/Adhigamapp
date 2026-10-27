package com.example.adhigam.ui.staff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.AdminHomeData
import com.example.adhigam.data.model.MonthlyCollection
import com.example.adhigam.data.model.StaffProfile
import com.example.adhigam.data.model.StudentStats
import com.example.adhigam.navigation.BottomTab
import com.example.adhigam.ui.components.ActionCardItem
import com.example.adhigam.ui.components.ActionCardsGrid
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.NoticesCard
import com.example.adhigam.ui.components.SectionTitle
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.theme.Teal600
import com.example.adhigam.ui.util.formatRupees
import com.example.adhigam.ui.util.formatRupeesCompact
import com.example.adhigam.ui.util.greetingForNow
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

// Mirrors the Admin / Principal / Superadmin section of the web dashboard: student status counts,
// "Fee Collection — Current Year" bar chart, "Student Status" doughnut, quick actions and notices.
@Composable
fun AdminHomeScreen(
    onOpenTab: (BottomTab) -> Unit,
    onOpenNotices: () -> Unit,
    onNewStudent: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenStaffAttendance: () -> Unit,
    onOpenExamMarks: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenLeave: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminHomeViewModel = viewModel { AdminHomeViewModel(AppContainer.adminDashboardRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Pick up notices posted (or other changes made) on another screen when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadIfLoaded() }
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { data ->
        AdminHomeContent(
            data = data,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onOpenTab = onOpenTab,
            onOpenNotices = onOpenNotices,
            onNewStudent = onNewStudent,
            onOpenAttendance = onOpenAttendance,
            onOpenStaffAttendance = onOpenStaffAttendance,
            onOpenExamMarks = onOpenExamMarks,
            onOpenQuizzes = onOpenQuizzes,
            onOpenLeave = onOpenLeave,
            onOpenTimetable = onOpenTimetable,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHomeContent(
    data: AdminHomeData,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenTab: (BottomTab) -> Unit,
    onOpenNotices: () -> Unit,
    onNewStudent: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenStaffAttendance: () -> Unit,
    onOpenExamMarks: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenLeave: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        ActionCardItem(
            "Student List", "View & manage all students",
            Icons.Outlined.Groups, MaterialTheme.colorScheme.primary
        ) { onOpenTab(BottomTab.Students) },
        ActionCardItem(
            "New Student", "Register a new admission",
            Icons.Outlined.PersonAdd, SuccessGreen
        ) { onNewStudent() },
        ActionCardItem(
            "Attendance", "Mark daily attendance",
            Icons.Outlined.CalendarMonth, Amber600
        ) { onOpenAttendance() },
        ActionCardItem(
            "Staff Attendance", "Daily staff register",
            Icons.Outlined.Badge, ErrorRed
        ) { onOpenStaffAttendance() },
        ActionCardItem(
            "Exam Marks", "Enter & view marks",
            Icons.Outlined.EditNote, Teal600
        ) { onOpenExamMarks() },
        ActionCardItem(
            "Quizzes", "Create quizzes & see results",
            Icons.Outlined.Quiz, MaterialTheme.colorScheme.tertiary
        ) { onOpenQuizzes() },
        ActionCardItem(
            "Leave Requests", "Approve or reject staff leave",
            Icons.Outlined.EventBusy, Amber600
        ) { onOpenLeave() },
        ActionCardItem(
            "Timetable", "View & edit class timetables",
            Icons.Outlined.Schedule, Teal600
        ) { onOpenTimetable() }
    )

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                StaffBanner(
                    data.profile,
                    title = "Dashboard",
                    subtitle = "${greetingForNow()}, ${data.profile.name.substringBefore(" ")}"
                )
            }
            item { StatsGrid(data.stats) }
            item { FeeCollectionCard(data) }
            item { StudentStatusCard(data.stats) }
            item {
                SectionTitle("Quick Actions")
                Spacer(Modifier.height(8.dp))
                ActionCardsGrid(actions)
            }
            item {
                SectionTitle("Notice Board", actionLabel = "View all") { onOpenNotices() }
                Spacer(Modifier.height(8.dp))
                NoticesCard(data.notices.take(5))
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: StudentStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Total Students", stats.total, Icons.Outlined.People, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile("Approved", stats.approved, Icons.Outlined.HowToReg, SuccessGreen, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Pending", stats.pending, Icons.Outlined.PendingActions, Amber600, Modifier.weight(1f))
            StatTile("Rejected", stats.rejected, Icons.Outlined.Cancel, ErrorRed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChartCardTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.6.sp
    )
}

@Composable
private fun FeeCollectionCard(data: AdminHomeData) {
    // Default to the latest month that has any collection.
    val lastWithData = data.feeTrend.indexOfLast { it.amount > 0 }.coerceAtLeast(0)
    var selected by rememberSaveable(data) { mutableIntStateOf(lastWithData) }
    val selectedMonth = data.feeTrend.getOrNull(selected)

    ListCard {
        Column(Modifier.padding(16.dp)) {
            ChartCardTitle("Fee Collection — Current Year")
            Spacer(Modifier.height(8.dp))
            Text(formatRupees(data.totalCollected), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "collected this session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (selectedMonth != null) {
                Text(
                    "${selectedMonth.month}: ${formatRupees(selectedMonth.amount)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap a bar to see that month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            FeeBarChart(data.feeTrend, selected = selected, onSelect = { selected = it })
        }
    }
}

// Rounds the axis maximum up to 1, 2 or 5 × 10^n so the gridlines land on readable amounts.
private fun niceCeiling(value: Long): Long {
    if (value <= 0) return 1
    val magnitude = 10.0.pow(log10(value.toDouble()).toInt())
    val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= value }
    return ceil(step * magnitude).toLong()
}

@Composable
private fun FeeBarChart(trend: List<MonthlyCollection>, selected: Int, onSelect: (Int) -> Unit) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val selectedStyle = axisStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val axisMax = remember(trend) { niceCeiling(trend.maxOfOrNull { it.amount } ?: 0L) }
    val description = trend.joinToString { "${it.month} ${formatRupees(it.amount)}" }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = "Fee collection by month: $description" }
            .pointerInput(trend) {
                detectTapGestures { offset ->
                    val left = 44.dp.toPx()
                    val slot = (size.width - left) / trend.size
                    val index = ((offset.x - left) / slot).toInt()
                    if (index in trend.indices) onSelect(index)
                }
            }
    ) {
        val left = 44.dp.toPx()
        val bottomLabels = 18.dp.toPx()
        val plotTop = 6.dp.toPx()
        val plotBottom = size.height - bottomLabels
        val plotHeight = plotBottom - plotTop
        val slot = (size.width - left) / trend.size
        val barWidth = (slot * 0.6f).coerceAtMost(18.dp.toPx())
        val radius = 4.dp.toPx()

        // Recessive gridlines at 0, half and the axis max, labelled on the left.
        listOf(0L, axisMax / 2, axisMax).forEach { value ->
            val y = plotBottom - plotHeight * (value.toFloat() / axisMax)
            drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val label = textMeasurer.measure(formatRupeesCompact(value), axisStyle)
            drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
        }

        trend.forEachIndexed { i, month ->
            val centerX = left + slot * i + slot / 2
            val barHeight = plotHeight * (month.amount.toFloat() / axisMax)
            val isSelected = i == selected
            if (barHeight > 0f) {
                // Rounded data end, square at the baseline.
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = centerX - barWidth / 2,
                            top = plotBottom - barHeight,
                            right = centerX + barWidth / 2,
                            bottom = plotBottom,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero,
                            bottomRightCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, color = if (isSelected) barColor else barColor.copy(alpha = 0.55f))
            }
            val label = textMeasurer.measure(month.month.take(3), if (isSelected) selectedStyle else axisStyle)
            drawText(label, topLeft = Offset(centerX - label.size.width / 2f, plotBottom + 4.dp.toPx()))
        }
    }
}

@Composable
private fun StudentStatusCard(stats: StudentStats) {
    val segments = listOf(
        Triple("Approved", stats.approved, SuccessGreen),
        Triple("Pending", stats.pending, Amber600),
        Triple("Rejected", stats.rejected, ErrorRed)
    )
    val sum = segments.sumOf { it.second }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    ListCard {
        Column(Modifier.padding(16.dp)) {
            ChartCardTitle("Student Status")
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Canvas(
                        Modifier
                            .size(120.dp)
                            .semantics {
                                contentDescription = segments.joinToString { "${it.first} ${it.second}" }
                            }
                    ) {
                        val stroke = 16.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        if (sum == 0) {
                            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                            return@Canvas
                        }
                        // 2° of surface between segments so neighbouring colours never touch.
                        val gap = if (segments.count { it.second > 0 } > 1) 2f else 0f
                        var start = -90f
                        segments.forEach { (_, count, color) ->
                            if (count == 0) return@forEach
                            val sweep = 360f * count / sum
                            drawArc(
                                color = color,
                                startAngle = start + gap / 2,
                                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Butt)
                            )
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(sum.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("students", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
                // Legend: identity is never colour alone — every segment is named with its count and share.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    segments.forEach { (label, count, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (sum == 0) "$count" else "$count · ${Math.round(count * 100f / sum)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun AdminHomePreview() {
    // Preview-only sample; the app loads the real numbers from /student/dashboard and /student/dashboard-charts.
    val data = AdminHomeData(
        profile = StaffProfile("Admin Name", "Principal", "9000000000", null, null, null, null, null, null, null),
        stats = StudentStats(total = 120, approved = 100, pending = 15, rejected = 5),
        feeTrend = listOf("April", "May", "June", "July", "August", "September", "October", "November", "December", "January", "February", "March")
            .mapIndexed { i, month -> MonthlyCollection(month, if (i < 7) (i + 2) * 25_000L else 0L) },
        notices = emptyList()
    )
    AdhigamTheme {
        AdminHomeContent(data, isRefreshing = false, onRefresh = {}, onOpenTab = {}, onOpenNotices = {}, onNewStudent = {}, onOpenAttendance = {}, onOpenStaffAttendance = {}, onOpenExamMarks = {}, onOpenQuizzes = {}, onOpenLeave = {}, onOpenTimetable = {})
    }
}
