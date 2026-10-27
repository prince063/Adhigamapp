package com.example.adhigam.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.adhigam.data.model.UserRole

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main/{role}"
    const val ARG_ROLE = "role"

    fun main(role: String) = "main/$role"

    // Detail screens opened over the staff tabs.
    const val ARG_STUDENT_ID = "studentId"
    const val STUDENT_PROFILE = "student-profile/{$ARG_STUDENT_ID}"
    const val STUDENT_FEES = "student-fees/{$ARG_STUDENT_ID}"
    const val STUDENT_EDIT = "student-edit/{$ARG_STUDENT_ID}"
    const val STUDENT_NEW = "student-new"
    const val STUDENT_MARKS = "student-marks/{$ARG_STUDENT_ID}"
    const val ARG_INVOICE = "invoice"
    const val ARG_SESSION = "session"
    const val RECEIPT = "receipt/{$ARG_STUDENT_ID}/{$ARG_INVOICE}?$ARG_SESSION={$ARG_SESSION}"  // staff: any student's receipt
    const val MY_RECEIPT = "my-receipt/{$ARG_INVOICE}?session={session}"  // student / parent: own receipt
    const val ARG_EXAM_TYPE = "examTypeId"
    const val MARKSHEET = "marksheet/{$ARG_STUDENT_ID}/{$ARG_EXAM_TYPE}"
    const val RESULTS = "results"  // student / parent
    const val TIMETABLE = "timetable"  // student / parent
    const val ASSIGNMENTS = "assignments"  // student / parent
    const val NOTICES = "notices"  // every role
    const val LIBRARY = "library"  // student / parent
    const val LAB = "lab"  // student / parent
    const val QUIZZES = "quizzes"  // student only, like the web portal
    const val CLASS_ATTENDANCE = "class-attendance"  // staff: mark / summary / heatmap
    const val STAFF_ATTENDANCE = "staff-attendance"  // staff
    const val EXAM_MARKS = "exam-marks"  // staff (teachers have the Marks tab)
    const val MANAGE_ASSIGNMENTS = "manage-assignments"  // teacher: upload / list
    const val QUIZ_MANAGE = "quiz-manage"  // teacher / admin: quiz list
    const val LEAVE = "leave"  // teacher: apply / cancel, admin: also approve / reject
    const val MANAGE_TIMETABLE = "manage-timetable"  // teacher / admin: any class, add / edit / remove periods
    const val ARG_QUIZ_ID = "quizId"
    const val ARG_TITLE = "title"
    const val QUIZ_DETAIL = "quiz-manage/{$ARG_QUIZ_ID}?$ARG_TITLE={$ARG_TITLE}"  // questions + results

    // Encrypted ids from the backend can contain characters like '/' or '+', so they are URL-encoded.
    fun studentProfile(encryptedId: String) = "student-profile/${Uri.encode(encryptedId)}"
    fun studentFees(encryptedId: String) = "student-fees/${Uri.encode(encryptedId)}"
    fun studentEdit(encryptedId: String) = "student-edit/${Uri.encode(encryptedId)}"
    fun studentMarks(encryptedId: String) = "student-marks/${Uri.encode(encryptedId)}"
    fun receipt(encryptedId: String, invoiceNo: String, session: String) =
        "receipt/${Uri.encode(encryptedId)}/${Uri.encode(invoiceNo)}?$ARG_SESSION=${Uri.encode(session)}"
    fun myReceipt(invoiceNo: String, session: String) = "my-receipt/${Uri.encode(invoiceNo)}?session=${Uri.encode(session)}"
    fun marksheet(encryptedId: String, examTypeId: Int) = "marksheet/${Uri.encode(encryptedId)}/$examTypeId"
    fun quizDetail(quizId: Int, title: String) = "quiz-manage/$quizId?$ARG_TITLE=${Uri.encode(title)}"
}

// Tabs shown in the bottom navigation bar after login. Which ones appear depends on the role.
enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Students("students", "Students", Icons.Outlined.Groups),
    Fees("fees", "Fees", Icons.Outlined.CurrencyRupee),
    Attendance("attendance", "Attendance", Icons.Outlined.CalendarMonth),
    Marks("marks", "Marks", Icons.Outlined.EditNote),
    Profile("profile", "Profile", Icons.Outlined.AccountCircle);

    companion object {
        fun tabsFor(role: UserRole): List<BottomTab> = when (role) {
            UserRole.Student, UserRole.Parent -> listOf(Home, Fees, Attendance, Profile)
            UserRole.Teacher -> listOf(Home, Attendance, Marks, Profile)
            UserRole.Staff -> listOf(Home, Students, Fees, Profile)
        }
    }
}
