package com.example.adhigam.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.StaffProfile
import com.example.adhigam.data.model.TeacherHomeData
import com.example.adhigam.navigation.BottomTab
import com.example.adhigam.ui.components.ActionCardItem
import com.example.adhigam.ui.components.ActionCardsGrid
import com.example.adhigam.ui.components.NoticesCard
import com.example.adhigam.ui.components.SectionTitle
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.theme.Teal600

// Mirrors the Teacher section of the web dashboard: welcome banner, the quick actions
// (Mark Attendance, Exam Marks, Assignments, Quizzes, Leave, Timetable) and the notice board.
@Composable
fun TeacherHomeScreen(
    onOpenTab: (BottomTab) -> Unit,
    onOpenNotices: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenLeave: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeacherHomeViewModel = viewModel { TeacherHomeViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Pick up notices posted (or other changes made) on another screen when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadIfLoaded() }
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { data ->
        TeacherHomeContent(
            data = data,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onOpenTab = onOpenTab,
            onOpenNotices = onOpenNotices,
            onOpenAssignments = onOpenAssignments,
            onOpenQuizzes = onOpenQuizzes,
            onOpenLeave = onOpenLeave,
            onOpenTimetable = onOpenTimetable,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherHomeContent(
    data: TeacherHomeData,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenTab: (BottomTab) -> Unit,
    onOpenNotices: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenLeave: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        ActionCardItem(
            "Mark Attendance", "Record today's attendance",
            Icons.Outlined.HowToReg, MaterialTheme.colorScheme.primary
        ) { onOpenTab(BottomTab.Attendance) },
        ActionCardItem(
            "Exam Marks", "Enter subject marks",
            Icons.Outlined.EditNote, SuccessGreen
        ) { onOpenTab(BottomTab.Marks) },
        ActionCardItem(
            "Assignments", "Manage class assignments",
            Icons.AutoMirrored.Outlined.Assignment, Teal600
        ) { onOpenAssignments() },
        ActionCardItem(
            "Quizzes", "Create quizzes & see results",
            Icons.Outlined.Quiz, Amber600
        ) { onOpenQuizzes() },
        ActionCardItem(
            "Leave", "Apply & track your leave",
            Icons.Outlined.EventBusy, ErrorRed
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
            item { StaffBanner(data.profile, title = "Welcome back!", subtitle = "Select an action to get started.") }
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

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun TeacherHomePreview() {
    // Preview-only sample; the app loads the session user and /notice.
    val data = TeacherHomeData(
        profile = StaffProfile("Teacher Name", "Teacher", "9000000000", null, null, null, null, null, null, null),
        notices = emptyList()
    )
    AdhigamTheme {
        TeacherHomeContent(data, isRefreshing = false, onRefresh = {}, onOpenTab = {}, onOpenNotices = {}, onOpenAssignments = {}, onOpenQuizzes = {}, onOpenLeave = {}, onOpenTimetable = {})
    }
}
