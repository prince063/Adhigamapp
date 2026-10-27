package com.example.adhigam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.AttendanceStatus
import com.example.adhigam.data.model.AttendanceSummary
import com.example.adhigam.data.model.BusTracking
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.HomeData
import com.example.adhigam.data.model.Notice
import com.example.adhigam.data.model.StudentProfile
import com.example.adhigam.data.model.TimetableSlot
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.navigation.BottomTab
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.HeaderChip
import com.example.adhigam.ui.components.NoticesCard
import com.example.adhigam.ui.components.QuickActionItem
import com.example.adhigam.ui.components.QuickActionsGrid
import com.example.adhigam.ui.components.SectionTitle
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.Indigo700
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.theme.WarningOrange
import com.example.adhigam.ui.util.formatClock
import com.example.adhigam.ui.util.formatDateTime
import com.example.adhigam.ui.util.formatFullDate
import com.example.adhigam.ui.util.formatRupees
import com.example.adhigam.ui.util.greetingForNow
import com.example.adhigam.ui.util.openAssignmentFile
import com.example.adhigam.ui.util.openBusLocation
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val HeaderDark = Color(0xFF1E1B4B)

// Mirrors the parent portal sections: Finance, Academics (attendance, results, timetable, assignments),
// notices and Library & Lab facilities.
private enum class QuickAction(val label: String, val icon: ImageVector, val tab: BottomTab? = null) {
    Fees("Fees", Icons.Outlined.CurrencyRupee, BottomTab.Fees),
    Attendance("Attendance", Icons.Outlined.CalendarMonth, BottomTab.Attendance),
    Results("Results", Icons.Outlined.EmojiEvents),
    Timetable("Timetable", Icons.Outlined.Schedule),
    Assignments("Assignments", Icons.AutoMirrored.Outlined.Assignment),
    Notices("Notices", Icons.Outlined.Campaign),
    Library("Library", Icons.AutoMirrored.Outlined.MenuBook),
    Lab("Lab", Icons.Outlined.Science),
    Quizzes("Quizzes", Icons.Outlined.Quiz)  // Student only, like the web portal
}

@Composable
fun HomeScreen(
    role: UserRole,
    onOpenTab: (BottomTab) -> Unit,
    onOpenResults: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenQuizzes: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel { HomeViewModel(AppContainer.studentRepository, role) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.data == null -> ErrorState(
            message = state.error ?: "Could not load data",
            onRetry = viewModel::refresh,
            modifier = modifier
        )
        else -> HomeContent(
            data = state.data!!,
            role = role,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onOpenTab = onOpenTab,
            onOpenResults = onOpenResults,
            onOpenTimetable = onOpenTimetable,
            onOpenAssignments = onOpenAssignments,
            onOpenNotices = onOpenNotices,
            onOpenLibrary = onOpenLibrary,
            onOpenLab = onOpenLab,
            onOpenQuizzes = onOpenQuizzes,
            showMessage = showMessage,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    data: HomeData,
    role: UserRole,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenTab: (BottomTab) -> Unit,
    onOpenResults: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenQuizzes: () -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { StudentBanner(data.profile, role) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AttendanceCard(
                        summary = data.attendance,
                        onClick = { onOpenTab(BottomTab.Attendance) },
                        modifier = Modifier.weight(1f)
                    )
                    FeeCard(
                        summary = data.fees,
                        onClick = { onOpenTab(BottomTab.Fees) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            data.bus?.takeIf { it.isLive }?.let { bus ->
                item {
                    BusTrackingCard(bus, onClick = {
                        if (!context.openBusLocation(bus)) showMessage("No maps app found to show the bus location")
                    })
                }
            }
            item {
                val actions = QuickAction.entries.filter { it != QuickAction.Quizzes || role == UserRole.Student }
                QuickActionsGrid(actions.map { action ->
                    QuickActionItem(action.label, action.icon) {
                        when {
                            action == QuickAction.Results -> onOpenResults()
                            action == QuickAction.Timetable -> onOpenTimetable()
                            action == QuickAction.Assignments -> onOpenAssignments()
                            action == QuickAction.Notices -> onOpenNotices()
                            action == QuickAction.Library -> onOpenLibrary()
                            action == QuickAction.Lab -> onOpenLab()
                            action == QuickAction.Quizzes -> onOpenQuizzes()
                            action.tab != null -> onOpenTab(action.tab)
                        }
                    }
                })
            }
            item {
                val today = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                SectionTitle("Today's timetable", badge = today, actionLabel = "Full week") { onOpenTimetable() }
                Spacer(Modifier.height(8.dp))
                TodayTimetable(data.todaySlots, today)
            }
            item {
                SectionTitle("Assignments", actionLabel = "View all") { onOpenAssignments() }
                Spacer(Modifier.height(8.dp))
                AssignmentsCard(data.assignments.take(3), onDownload = {
                    if (!context.openAssignmentFile(it)) showMessage("Could not open the assignment file")
                })
            }
            item {
                SectionTitle("Latest notices", actionLabel = "View all") { onOpenNotices() }
                Spacer(Modifier.height(8.dp))
                NoticesCard(data.notices.take(3))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentBanner(profile: StudentProfile, role: UserRole) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeaderDark, Indigo700)))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Indigo700
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (role == UserRole.Parent) "${greetingForNow()}, Parent" else "${greetingForNow()},",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile.classLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Admission No: ${profile.admissionNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (profile.gender.isNotBlank()) HeaderChip(profile.gender)
                profile.dob?.let { HeaderChip("DOB: ${formatFullDate(it)}") }
                profile.bloodGroup?.let { HeaderChip("Blood Group: $it") }
                if (profile.mobile.isNotBlank()) HeaderChip("Mob: ${profile.mobile}")
            }
        }
    }
}

@Composable
private fun AttendanceCard(summary: AttendanceSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Web portal flags anything under 75% in red.
    val color = when {
        summary.percentage >= 85 -> SuccessGreen
        summary.percentage >= 75 -> WarningOrange
        else -> ErrorRed
    }
    StatCard(onClick = onClick, modifier = modifier) {
        Text("Attendance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { summary.percentage / 100f },
                modifier = Modifier.size(64.dp),
                color = color,
                strokeWidth = 6.dp,
                trackColor = color.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
            Text("${summary.percentage}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${summary.presentDays}/${summary.totalDays} days present",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        StatusPill(
            text = "Today: ${summary.todayStatus.label}",
            color = attendanceColor(summary.todayStatus)
        )
    }
}

@Composable
private fun attendanceColor(status: AttendanceStatus): Color = when (status) {
    AttendanceStatus.Present -> SuccessGreen
    AttendanceStatus.Absent -> ErrorRed
    AttendanceStatus.Leave -> Amber600
    AttendanceStatus.Holiday, AttendanceStatus.NotMarked -> MaterialTheme.colorScheme.outline
}

@Composable
private fun FeeCard(summary: FeeSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hasDue = summary.balance > 0
    StatCard(onClick = onClick, modifier = modifier) {
        Text("Fee balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background((if (hasDue) ErrorRed else SuccessGreen).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CurrencyRupee,
                contentDescription = null,
                tint = if (hasDue) ErrorRed else SuccessGreen,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasDue) formatRupees(summary.balance) else "All paid",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (hasDue) ErrorRed else SuccessGreen
        )
        Text(
            "Paid ${formatRupees(summary.totalPaid)} of ${formatRupees(summary.totalDue)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hasDue) {
            Spacer(Modifier.height(6.dp))
            val pending = summary.pendingMonths
            StatusPill(
                text = pending.firstOrNull()?.let { "${it.month} pending" } ?: "Balance due",
                color = WarningOrange
            )
        }
    }
}

@Composable
private fun StatCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
}

@Composable
private fun BusTrackingCard(bus: BusTracking, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Amber600),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DirectionsBus, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${bus.busName} (${bus.vehicleNum})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill("Live", SuccessGreen)
                }
                Text(
                    "Route: ${bus.routeName} (${bus.tripType})",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append("Status: ${bus.pickupStatus ?: "En Route"}")
                        bus.lastUpdated?.let { append(" • Updated ${formatClock(it.toLocalTime())}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodayTimetable(slots: List<TimetableSlot>, dayName: String) {
    if (slots.isEmpty()) {
        ListCard { EmptyText("No period slots scheduled for today ($dayName)") }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(slots, key = { it.periodNo }) { slot ->
            Card(
                modifier = Modifier.width(150.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "P${slot.periodNo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        slot.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${formatClock(slot.startTime)} - ${formatClock(slot.endTime)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentsCard(assignments: List<Assignment>, onDownload: (Assignment) -> Unit) {
    ListCard {
        if (assignments.isEmpty()) {
            EmptyText("No assignments found for this section")
        }
        assignments.forEachIndexed { index, assignment ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        assignment.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Subject: ${assignment.subject} • Class: ${assignment.classType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDownload(assignment) }) {
                    Icon(Icons.Outlined.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun HomeContentPreview() {
    // Preview-only sample; the app loads the student's real data.
    val data = HomeData(
        profile = StudentProfile(
            name = "Student Name", classType = "VIII", section = "A", admissionNo = "1001", gender = "Male",
            dob = null, bloodGroup = "B+", mobile = "9000000000", email = null, fatherName = "Father Name",
            motherName = null, address = null, photoPath = null, feeGroup = null
        ),
        attendance = AttendanceSummary(emptyList()),
        fees = FeeSummary(totalDue = 6000, totalPaid = 3000, balance = 3000),
        timetable = emptyList(),
        assignments = emptyList(),
        notices = emptyList(),
        bus = null
    )
    AdhigamTheme {
        HomeContent(
            data = data,
            role = UserRole.Parent,
            isRefreshing = false,
            onRefresh = {},
            onOpenTab = {},
            onOpenResults = {},
            onOpenTimetable = {},
            onOpenAssignments = {},
            onOpenNotices = {},
            onOpenLibrary = {},
            onOpenLab = {},
            onOpenQuizzes = {},
            showMessage = {}
        )
    }
}
