package com.example.adhigam.data.model

import java.time.LocalDate

// Field names follow the staff side of the backend (USERS table, /student/dashboard,
// /student/dashboard-charts, /notice).

// USERS row (GET_DATA_DETAILS 'USER'); roleType is Superadmin | Admin | Principal | Teacher.
data class StaffProfile(
    val name: String,          // full_name
    val roleType: String,      // role_type
    val mobile: String,        // mobile_no
    val email: String?,        // email
    val address: String?,      // address
    val city: String?,         // city
    val state: String?,        // state
    val pincode: String?,      // pincode
    val dob: LocalDate?,       // dob
    val photoPath: String?     // profile image, relative to /static/
) {
    val fullAddress: String
        get() = listOf(address, city, state, pincode).filterNot { it.isNullOrBlank() }.joinToString(", ")
}

data class TeacherHomeData(
    val profile: StaffProfile,
    val notices: List<Notice>
)

// GET /student/dashboard
data class StudentStats(
    val total: Int,     // TOTALSTUDENTS
    val approved: Int,  // APPROVEDSTUDENTS
    val pending: Int,   // PENDINGSTUDENTS
    val rejected: Int   // REJECTEDSTUDENTS
)

// fee_trend row of GET /student/dashboard-charts
data class MonthlyCollection(
    val month: String,  // month_name
    val amount: Long    // total_collected
)

data class AdminHomeData(
    val profile: StaffProfile,
    val stats: StudentStats,
    val feeTrend: List<MonthlyCollection>,   // April..March, months without payments are 0
    val notices: List<Notice>
) {
    val totalCollected: Long get() = feeTrend.sumOf { it.amount }
}

// ClassType and SectionEnum of the backend (app/models/enums.py), in the order the web dropdowns list them.
val SchoolClasses = listOf(
    "Nursery", "LKG", "UKG", "Primary1", "Primary2",
    "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"
)
val SchoolSections = listOf("A", "B", "C", "D", "E", "F", "G", "H", "N")

fun classDisplayName(classType: String): String = when (classType) {
    "Primary1" -> "Primary 1"
    "Primary2" -> "Primary 2"
    "Nursery", "LKG", "UKG" -> classType
    else -> "Class $classType"
}

// One row of GET /student/attendance?class_value&section_value&date.
// attendanceId is 0 when attendance has not been saved for that day yet (status then defaults to present).
data class AttendanceEntry(
    val attendanceId: Int,   // ID
    val admissionNo: String, // ADMISSIONNO
    val studentName: String, // STUDENTNAME
    val isPresent: Boolean   // ATTENDANCESTATUS
)

// One row of GET /master/exam-types (EXAM_TYPE).
data class ExamType(
    val id: Int,                    // id
    val name: String,               // name
    val isResultDeclared: Boolean   // IS_RESULT_DECLARED
)

// One row of GET /exam/teacher-subjects (TEACHER_SUBJECT: USERID -> CLASS + SECTION + SUBJECT).
data class TeacherSubject(
    val classType: String,  // class_type
    val section: String,    // section
    val subject: String     // subject
)

// One row of GET /exam/marks?exam_type_id&class_id&section&subject. Obtained marks are null
// until entered; max marks default to the subject's WRITTENMAXMARKS / PRACTICALMAXMARKS.
data class MarksEntry(
    val id: Int,                     // id (0 = not saved yet)
    val studentId: Int,              // student_id
    val admissionNo: String,         // ADMISSIONNO
    val studentName: String,         // STUDENTNAME
    val writtenMax: Double,          // WRITTENMAXMARKS
    val writtenObtained: Double?,    // WRITTENOBTAINMARKS
    val practicalMax: Double,        // PRACTICALMAXMARKS
    val practicalObtained: Double?   // PRACTICALOBTAINMARKS
)

// Enrollment status of a student (STUDENTS.STATUS).
enum class StudentStatus { Approved, Pending, Rejected }

// One row of GET /student/list?class_value&section_value&search
data class StudentListItem(
    val encryptedId: String,      // encrypted_id
    val admissionNo: String,      // ADMISSIONNO
    val name: String,             // STUDENTNAME
    val fatherName: String?,      // FATHERNAME
    val mobile: String,           // MOBILE
    val dueFee: Long,             // DUE_FEE_AMOUNT
    val classType: String,        // CLASS
    val section: String,          // SECTION
    val status: StudentStatus,    // STATUS
    val feeGroup: String? = null  // FEEGROUPNAME (from /student/edit/{encId})
)

// Payment modes of the web "Collect Fee" modal; the value is what /student/pay-fee-partial expects.
enum class PaymentMode(val value: String, val label: String) {
    Cash("Cash", "Cash"),
    UPI("UPI", "UPI"),
    Cheque("Cheque", "Cheque"),
    Card("Card", "Card"),
    NetBanking("NetBanking", "Net Banking")
}

// One fee head of one month (a STUDENT_FEES row).
data class FeeHeadKey(val month: String, val feeName: String)

// Body of POST /student/pay-fee-partial. The amount may be less than the selected heads' balance (partial
// payment); it is applied to the selected rows (fee_ids) oldest first, or to the oldest unpaid rows if none.
data class FeePaymentRequest(
    val amount: Long,
    val mode: PaymentMode,
    val feeIds: List<Int>,
    val transactionId: String? = null,
    val bankName: String? = null,
    val netbankingType: String? = null,
    val chequeNo: String? = null,
    val accountNo: String? = null,
    val upiId: String? = null,
    val cardType: String? = null,
    val payeeName: String? = null,
    val remarks: String? = null
)

// GET /student/view/{encId}: the full STUDENTS record as staff see it (student-view.html).
// Fields shared with the student's own profile live in [profile].
data class StaffStudentDetail(
    val encryptedId: String,
    val profile: StudentProfile,
    val status: StudentStatus,           // status
    val caste: String? = null,           // caste
    val aaparId: String? = null,         // aapar_id
    val isSibling: Boolean = false,      // is_sibling
    val previousClass: String? = null,   // previous_class
    val previousMaxMarks: String? = null,    // previous_max_marks
    val previousObtainMarks: String? = null, // previous_obtain_marks
    val specialCategory: String? = null, // special_category
    val bankName: String? = null,        // bank_name
    val ifscCode: String? = null,        // ifsc_code
    val accountNumber: String? = null,   // account_number
    val aadharNumber: String? = null,    // adhar_number
    val panNumber: String? = null,       // pan_number
    val medicalReportUrl: String? = null, // medical_report_url
    val previousMarksheetUrl: String? = null, // previous_marksheet_url
    // Remaining registration-form fields (student-registration.html).
    val identityOne: String? = null,     // identity_one
    val identityTwo: String? = null,     // identity_two
    val oralMaxMarks: String? = null,    // oral_max_marks
    val oralObtainMarks: String? = null, // oral_obtain_marks
    val writtenMaxMarks: String? = null, // written_max_marks
    val writtenObtainMarks: String? = null, // written_obtain_marks
    val feeGroupId: Int? = null,         // fee_group_id
    val vehicleId: Int? = null,          // vehicle_id
    val pickupPointId: Int? = null,      // pickup_point_id
    val vehicleStartMonth: String? = null, // vehicle_start_month
    val vehicleStopMonth: String? = null,  // vehicle_stop_month
    val siblingAdmissionNos: List<String> = emptyList() // sibling admission numbers
)

// GET /master/fee-groups (FEE_GROUP).
data class FeeGroupOption(val id: Int, val name: String)

// GET /master/vehicles (ROUTE). Its pickup points come from /master/vehicle/{encrypted_id}/pickups.
data class VehicleRoute(val id: Int, val name: String, val encryptedId: String)

// GET /master/vehicle/{encrypted_id}/pickups (ROUTE_PICKUP).
data class PickupPoint(val id: Int, val name: String)

// GET /student/sibling/{admission_no} (GET_BASIC_DETAILS).
data class SiblingInfo(val name: String, val fatherName: String?, val motherName: String?, val mobile: String?)

// Dropdown data for the registration form: /master/occupations, /master/castes, /master/fee-groups, /master/vehicles.
data class StudentFormOptions(
    val occupations: List<String>,
    val castes: List<String>,
    val feeGroups: List<FeeGroupOption>,
    val vehicles: List<VehicleRoute>
)

// Fixed lists of the registration form (backend enums / web <select> options).
object StudentFormLists {
    val genders = listOf("Male", "Female")
    val bloodGroups = listOf("A+", "B+", "AB+", "O+", "A-", "B-", "AB-", "O-")
    val religions = listOf("Hinduism", "Islam", "Christianity", "Sikhism", "Buddhism", "Jainism")
    val communities = listOf("GENERAL", "OBC", "SC", "ST")
    val specialCategories = listOf("Music", "Sports", "Etc")
    val sessionMonths = listOf(
        "April", "May", "June", "July", "August", "September",
        "October", "November", "December", "January", "February", "March"
    )
}

// One row of GET /exam/student-marks?admission_no=… (one subject of one exam).
data class StudentMarkRow(
    val examTypeId: Int,          // EXAMTYPEID
    val examType: String,         // EXAMTYPE
    val subject: String,          // SUBJECT
    val writtenObtained: Double,  // WRITTENOBTAINMARKS
    val writtenMax: Double,       // WRITTENMAXMARKS
    val practicalObtained: Double,// PRACTICALOBTAINMARKS
    val practicalMax: Double,     // PRACTICALMAXMARKS
    val backendPass: Boolean? = null // result_status from the marksheet API, when it sends one
) {
    val total: Double get() = writtenObtained + practicalObtained
    val max: Double get() = writtenMax + practicalMax
    // The backend's verdict when given (written and practical each ≥ 33%); otherwise the web
    // student-marks rule: pass at 33% of the subject's total.
    val isPass: Boolean get() = backendPass ?: (total >= max * 0.33)
}

// GET /student/marksheet-preview?admission_no&class_id&exam_type_id (marksheet-preview.html).
data class Marksheet(
    val studentName: String,      // STUDENTNAME
    val fatherName: String?,      // FATHERNAME
    val admissionNo: String,      // ADMISSIONNO
    val classType: String,        // CLASS
    val section: String,          // SECTION
    val academicYear: String,     // YEAR
    val examName: String,
    val rows: List<StudentMarkRow>,
    val remarks: String?,         // remarks
    val backendPass: Boolean? = null, // overall_result_status
    val schoolName: String = "",      // /settings/school-config
    val schoolAddress: String = ""
) {
    val grandTotal: Double get() = rows.sumOf { it.total }
    val grandMax: Double get() = rows.sumOf { it.max }
    val percent: Double get() = if (grandMax > 0) grandTotal * 100 / grandMax else 0.0
    // The backend's overall result when given; otherwise the web fallback, 33% of the grand total.
    val isPass: Boolean get() = backendPass ?: (grandTotal >= grandMax * 0.33)
}

// One row of GET /student/attendance-summary (GET_STUDENT_ATTENDENCE_SUMMERY): a student's days in the month.
data class AttendanceSummaryRow(
    val admissionNo: String,  // ADMISSIONNO
    val studentName: String,  // STUDENTNAME
    val totalDays: Int,       // TOTAL_DAYS
    val presentDays: Int,     // PRESENT_DAYS
    val absentDays: Int       // ABSENT_DAYS
) {
    val percent: Double get() = if (totalDays == 0) 0.0 else presentDays * 100.0 / totalDays
}

// GET /hr/attendance-heatmap pivoted to one row per student: day of month → status letter (P / A / L / H).
data class HeatmapStudent(
    val admissionNo: String,
    val studentName: String,
    val days: Map<Int, Char>
)

// Status options of the web staff attendance page.
val StaffAttendanceStatuses = listOf("Present", "Absent", "Half Day", "Late")

// One row of GET /hr/staff-attendance (GET_STAFF_ATTENDANCE): every staff user with that day's record, if any.
data class StaffAttendanceEntry(
    val userId: Int,          // user_id
    val staffName: String,    // staff_name
    val designation: String,  // designation (ROLETYPE)
    val status: String,       // STATUS, Present when nothing is saved yet
    val inTime: String,       // IN_TIME "HH:mm"
    val outTime: String,      // OUT_TIME "HH:mm"
    val remarks: String       // REMARKS
)
