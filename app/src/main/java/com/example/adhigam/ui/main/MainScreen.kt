package com.example.adhigam.ui.main

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.navigation.BottomTab
import com.example.adhigam.navigation.Routes
import com.example.adhigam.ui.assignments.AssignmentsScreen
import com.example.adhigam.ui.attendance.AttendanceScreen
import com.example.adhigam.ui.fees.FeesScreen
import com.example.adhigam.ui.home.HomeScreen
import com.example.adhigam.ui.lab.LabScreen
import com.example.adhigam.ui.library.LibraryScreen
import com.example.adhigam.ui.notices.NoticeBoardScreen
import com.example.adhigam.ui.profile.ProfileScreen
import com.example.adhigam.ui.quiz.QuizScreen
import com.example.adhigam.ui.receipt.ReceiptScreen
import com.example.adhigam.ui.results.ResultsScreen
import com.example.adhigam.ui.timetable.TimetableScreen
import com.example.adhigam.ui.staff.AdminHomeScreen
import com.example.adhigam.ui.staff.ExamMarksScreen
import com.example.adhigam.ui.staff.FeeCollectionScreen
import com.example.adhigam.ui.staff.LeaveScreen
import com.example.adhigam.ui.staff.ManageAssignmentsScreen
import com.example.adhigam.ui.staff.QuizDetailScreen
import com.example.adhigam.ui.staff.QuizManageScreen
import com.example.adhigam.ui.staff.MarksheetScreen
import com.example.adhigam.ui.staff.ClassAttendanceScreen
import com.example.adhigam.ui.staff.StaffAttendanceScreen
import com.example.adhigam.ui.staff.StaffProfileScreen
import com.example.adhigam.ui.staff.StudentDetailScreen
import com.example.adhigam.ui.staff.TimetableManageScreen
import com.example.adhigam.ui.staff.StudentFormScreen
import com.example.adhigam.ui.staff.StudentMarksScreen
import com.example.adhigam.ui.staff.StudentListScreen
import com.example.adhigam.ui.staff.TeacherHomeScreen
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.util.openAssignmentFile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(role: UserRole, onLogout: () -> Unit) {
    val tabNavController = rememberNavController()
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val tabs = remember(role) { BottomTab.tabsFor(role) }
    val currentTab = tabs.firstOrNull { it.route == currentDestination?.route } ?: BottomTab.Home
    // Detail screens get a back arrow and their own title; they belong to the Students tab.
    val detailTitle = when (currentDestination?.route) {
        Routes.STUDENT_PROFILE -> "Student Profile"
        Routes.STUDENT_FEES -> "Student Fees"
        Routes.STUDENT_EDIT -> "Edit Student"
        Routes.STUDENT_NEW -> "New Student"
        Routes.STUDENT_MARKS -> "Student Marks"
        Routes.RECEIPT, Routes.MY_RECEIPT -> "Fee Receipt"
        Routes.MARKSHEET -> "Marksheet"
        Routes.RESULTS -> "Results"
        Routes.TIMETABLE -> "Timetable"
        Routes.ASSIGNMENTS -> "My Assignments"
        Routes.NOTICES -> "Notice Board"
        Routes.LIBRARY -> "Library"
        Routes.LAB -> "Lab Sessions"
        Routes.QUIZZES -> "Quizzes"
        Routes.CLASS_ATTENDANCE -> "Attendance"
        Routes.STAFF_ATTENDANCE -> "Staff Attendance"
        Routes.EXAM_MARKS -> "Exam Marks"
        Routes.MANAGE_ASSIGNMENTS -> "Assignments"
        Routes.QUIZ_MANAGE -> "Quiz Management"
        Routes.QUIZ_DETAIL -> "Quiz"
        Routes.LEAVE -> "Leave Management"
        Routes.MANAGE_TIMETABLE -> "Timetable"
        else -> null
    }
    // Tab to keep highlighted while a detail screen is open.
    val detailTab = when (currentDestination?.route) {
        null -> null
        Routes.RECEIPT, Routes.MY_RECEIPT -> BottomTab.Fees
        Routes.RESULTS, Routes.TIMETABLE, Routes.ASSIGNMENTS, Routes.NOTICES, Routes.LIBRARY, Routes.LAB, Routes.QUIZZES,
        Routes.CLASS_ATTENDANCE, Routes.STAFF_ATTENDANCE, Routes.EXAM_MARKS, Routes.MANAGE_ASSIGNMENTS,
        Routes.QUIZ_MANAGE, Routes.QUIZ_DETAIL, Routes.LEAVE, Routes.MANAGE_TIMETABLE -> BottomTab.Home
        else -> if (detailTitle != null) BottomTab.Students else null
    }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun openTab(tab: BottomTab) {
        // Only tabs of this role exist in the graph.
        if (tab !in tabs) return
        tabNavController.navigate(tab.route) {
            popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun showMessage(text: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    fun showComingSoon(feature: String) = showMessage("$feature is coming soon")

    // Quiz list + one quiz's questions / results (quiz-manage.html), for teachers and admins.
    fun NavGraphBuilder.quizManageDestinations() {
        composable(Routes.QUIZ_MANAGE) {
            QuizManageScreen(
                onOpenQuiz = { tabNavController.navigate(Routes.quizDetail(it.id, it.title)) },
                showMessage = ::showMessage
            )
        }
        composable(
            route = Routes.QUIZ_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_QUIZ_ID) { type = NavType.IntType },
                navArgument(Routes.ARG_TITLE) { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            QuizDetailScreen(
                quizId = entry.arguments?.getInt(Routes.ARG_QUIZ_ID) ?: 0,
                title = entry.arguments?.getString(Routes.ARG_TITLE).orEmpty(),
                showMessage = ::showMessage
            )
        }
    }

    // The top-bar arrow goes through the back dispatcher so a screen's BackHandler (e.g. leaving a quiz) applies.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(detailTitle ?: if (currentTab == BottomTab.Home) "Adhigam" else currentTab.label) },
                navigationIcon = {
                    if (detailTitle != null) {
                        IconButton(onClick = { backDispatcher?.onBackPressed() ?: tabNavController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true ||
                            tab == detailTab,
                        onClick = { openTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = BottomTab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            val openLogout = { showLogoutDialog = true }
            composable(Routes.NOTICES) {
                NoticeBoardScreen(role = role, showMessage = ::showMessage)
            }
            when (role) {
                UserRole.Student, UserRole.Parent -> {
                    composable(BottomTab.Home.route) {
                        HomeScreen(
                            role = role,
                            onOpenTab = ::openTab,
                            onOpenResults = { tabNavController.navigate(Routes.RESULTS) },
                            onOpenTimetable = { tabNavController.navigate(Routes.TIMETABLE) },
                            onOpenAssignments = { tabNavController.navigate(Routes.ASSIGNMENTS) },
                            onOpenNotices = { tabNavController.navigate(Routes.NOTICES) },
                            onOpenLibrary = { tabNavController.navigate(Routes.LIBRARY) },
                            onOpenLab = { tabNavController.navigate(Routes.LAB) },
                            onOpenQuizzes = { tabNavController.navigate(Routes.QUIZZES) },
                            showMessage = ::showMessage
                        )
                    }
                    composable(Routes.LAB) {
                        LabScreen()
                    }
                    composable(Routes.QUIZZES) {
                        QuizScreen(showMessage = ::showMessage)
                    }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(showMessage = ::showMessage)
                    }
                    composable(Routes.ASSIGNMENTS) {
                        // Files are served by the school server (/static/<FILEURL>) and open in the browser / a viewer.
                        AssignmentsScreen(onDownload = {
                            if (!context.openAssignmentFile(it)) showMessage("Could not open the assignment file")
                        })
                    }
                    composable(Routes.TIMETABLE) {
                        TimetableScreen()
                    }
                    composable(Routes.RESULTS) {
                        ResultsScreen()
                    }
                    composable(BottomTab.Fees.route) {
                        FeesScreen(
                            showMessage = ::showMessage,
                            onOpenReceipt = { invoice, session -> tabNavController.navigate(Routes.myReceipt(invoice, session)) }
                        )
                    }
                    composable(
                        route = Routes.MY_RECEIPT,
                        arguments = listOf(
                            navArgument(Routes.ARG_INVOICE) { type = NavType.StringType },
                            navArgument(Routes.ARG_SESSION) { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val invoice = entry.arguments?.getString(Routes.ARG_INVOICE).orEmpty()
                        val session = entry.arguments?.getString(Routes.ARG_SESSION).orEmpty()
                        ReceiptScreen(
                            key = "my-$invoice-$session",
                            loader = { AppContainer.studentRepository.getReceipt(invoice, session) }
                        )
                    }
                    composable(BottomTab.Attendance.route) {
                        AttendanceScreen()
                    }
                    composable(BottomTab.Profile.route) {
                        ProfileScreen(role = role, showMessage = ::showMessage, onLogout = openLogout)
                    }
                }
                UserRole.Teacher -> {
                    composable(BottomTab.Home.route) {
                        TeacherHomeScreen(
                            onOpenTab = ::openTab,
                            onOpenNotices = { tabNavController.navigate(Routes.NOTICES) },
                            onOpenAssignments = { tabNavController.navigate(Routes.MANAGE_ASSIGNMENTS) },
                            onOpenQuizzes = { tabNavController.navigate(Routes.QUIZ_MANAGE) },
                            onOpenLeave = { tabNavController.navigate(Routes.LEAVE) },
                            onOpenTimetable = { tabNavController.navigate(Routes.MANAGE_TIMETABLE) }
                        )
                    }
                    composable(Routes.LEAVE) {
                        LeaveScreen(isAdmin = false, showMessage = ::showMessage)
                    }
                    composable(Routes.MANAGE_TIMETABLE) {
                        TimetableManageScreen(canPickTeacher = false, showMessage = ::showMessage)
                    }
                    composable(Routes.MANAGE_ASSIGNMENTS) {
                        ManageAssignmentsScreen(showMessage = ::showMessage)
                    }
                    quizManageDestinations()
                    composable(BottomTab.Attendance.route) {
                        ClassAttendanceScreen(showHeatmap = false, showMessage = ::showMessage)
                    }
                    composable(BottomTab.Marks.route) {
                        ExamMarksScreen(showMessage = ::showMessage)
                    }
                    composable(BottomTab.Profile.route) {
                        StaffProfileScreen(showMessage = ::showMessage, onLogout = openLogout)
                    }
                }
                UserRole.Staff -> {
                    composable(BottomTab.Home.route) {
                        AdminHomeScreen(
                            onOpenTab = ::openTab,
                            onOpenNotices = { tabNavController.navigate(Routes.NOTICES) },
                            onNewStudent = { tabNavController.navigate(Routes.STUDENT_NEW) },
                            onOpenAttendance = { tabNavController.navigate(Routes.CLASS_ATTENDANCE) },
                            onOpenStaffAttendance = { tabNavController.navigate(Routes.STAFF_ATTENDANCE) },
                            onOpenExamMarks = { tabNavController.navigate(Routes.EXAM_MARKS) },
                            onOpenQuizzes = { tabNavController.navigate(Routes.QUIZ_MANAGE) },
                            onOpenLeave = { tabNavController.navigate(Routes.LEAVE) },
                            onOpenTimetable = { tabNavController.navigate(Routes.MANAGE_TIMETABLE) }
                        )
                    }
                    quizManageDestinations()
                    composable(Routes.LEAVE) {
                        LeaveScreen(isAdmin = true, showMessage = ::showMessage)
                    }
                    composable(Routes.MANAGE_TIMETABLE) {
                        TimetableManageScreen(canPickTeacher = true, showMessage = ::showMessage)
                    }
                    composable(Routes.EXAM_MARKS) {
                        ExamMarksScreen(showMessage = ::showMessage)
                    }
                    composable(Routes.CLASS_ATTENDANCE) {
                        ClassAttendanceScreen(showHeatmap = true, showMessage = ::showMessage)
                    }
                    composable(Routes.STAFF_ATTENDANCE) {
                        StaffAttendanceScreen(showMessage = ::showMessage)
                    }
                    composable(BottomTab.Students.route) {
                        StudentListScreen(
                            onOpenProfile = { tabNavController.navigate(Routes.studentProfile(it)) },
                            onOpenFees = { tabNavController.navigate(Routes.studentFees(it)) },
                            onEdit = { tabNavController.navigate(Routes.studentEdit(it)) },
                            onOpenMarks = { tabNavController.navigate(Routes.studentMarks(it)) },
                            showMessage = ::showMessage
                        )
                    }
                    composable(
                        route = Routes.STUDENT_PROFILE,
                        arguments = listOf(navArgument(Routes.ARG_STUDENT_ID) { type = NavType.StringType })
                    ) { entry ->
                        StudentDetailScreen(
                            encryptedId = entry.arguments?.getString(Routes.ARG_STUDENT_ID).orEmpty(),
                            onOpenFees = { tabNavController.navigate(Routes.studentFees(it)) },
                            onEdit = { tabNavController.navigate(Routes.studentEdit(it)) },
                            onOpenMarks = { tabNavController.navigate(Routes.studentMarks(it)) }
                        )
                    }
                    composable(
                        route = Routes.STUDENT_FEES,
                        arguments = listOf(navArgument(Routes.ARG_STUDENT_ID) { type = NavType.StringType })
                    ) { entry ->
                        FeeCollectionScreen(
                            initialStudentId = entry.arguments?.getString(Routes.ARG_STUDENT_ID),
                            onComingSoon = ::showComingSoon,
                            onOpenReceipt = { id, invoice, session -> tabNavController.navigate(Routes.receipt(id, invoice, session)) },
                            showMessage = ::showMessage
                        )
                    }
                    composable(
                        route = Routes.STUDENT_EDIT,
                        arguments = listOf(navArgument(Routes.ARG_STUDENT_ID) { type = NavType.StringType })
                    ) { entry ->
                        StudentFormScreen(
                            encryptedId = entry.arguments?.getString(Routes.ARG_STUDENT_ID),
                            onSaved = { message ->
                                showMessage(message)
                                tabNavController.popBackStack()
                            },
                            showMessage = ::showMessage
                        )
                    }
                    composable(
                        route = Routes.STUDENT_MARKS,
                        arguments = listOf(navArgument(Routes.ARG_STUDENT_ID) { type = NavType.StringType })
                    ) { entry ->
                        val studentId = entry.arguments?.getString(Routes.ARG_STUDENT_ID).orEmpty()
                        StudentMarksScreen(
                            encryptedId = studentId,
                            onOpenMarksheet = { examId -> tabNavController.navigate(Routes.marksheet(studentId, examId)) }
                        )
                    }
                    composable(
                        route = Routes.MARKSHEET,
                        arguments = listOf(
                            navArgument(Routes.ARG_STUDENT_ID) { type = NavType.StringType },
                            navArgument(Routes.ARG_EXAM_TYPE) { type = NavType.IntType }
                        )
                    ) { entry ->
                        MarksheetScreen(
                            encryptedId = entry.arguments?.getString(Routes.ARG_STUDENT_ID).orEmpty(),
                            examTypeId = entry.arguments?.getInt(Routes.ARG_EXAM_TYPE) ?: 0
                        )
                    }
                    composable(Routes.STUDENT_NEW) {
                        StudentFormScreen(
                            encryptedId = null,
                            onSaved = { message ->
                                showMessage(message)
                                tabNavController.popBackStack()
                            },
                            showMessage = ::showMessage
                        )
                    }
                    composable(BottomTab.Fees.route) {
                        FeeCollectionScreen(
                            onComingSoon = ::showComingSoon,
                            onOpenReceipt = { id, invoice, session -> tabNavController.navigate(Routes.receipt(id, invoice, session)) },
                            showMessage = ::showMessage
                        )
                    }
                    composable(
                        route = Routes.RECEIPT,
                        arguments = listOf(
                            navArgument(Routes.ARG_STUDENT_ID) { type = NavType.StringType },
                            navArgument(Routes.ARG_INVOICE) { type = NavType.StringType },
                            navArgument(Routes.ARG_SESSION) { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val studentId = entry.arguments?.getString(Routes.ARG_STUDENT_ID).orEmpty()
                        val invoice = entry.arguments?.getString(Routes.ARG_INVOICE).orEmpty()
                        val session = entry.arguments?.getString(Routes.ARG_SESSION).orEmpty()
                        ReceiptScreen(
                            key = "$studentId-$invoice-$session",
                            loader = { AppContainer.feeCollectionRepository.getReceipt(studentId, invoice, session) }
                        )
                    }
                    composable(BottomTab.Profile.route) {
                        StaffProfileScreen(showMessage = ::showMessage, onLogout = openLogout)
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    AdhigamTheme {
        MainScreen(role = UserRole.Student, onLogout = {})
    }
}
