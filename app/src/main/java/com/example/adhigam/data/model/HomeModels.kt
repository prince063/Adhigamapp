package com.example.adhigam.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

// Field names follow the backend responses used by parent-dashboard.html
// (/student/profile, /student/my-attendance, /student/fee-ledger/self/me, /academics/timetable, ...).

// GET /student/profile
data class StudentProfile(
    val name: String,              // STUDENTNAME
    val classType: String,         // CLASSTYPE: Nursery, LKG, UKG, Primary1, Primary2, I..XII
    val section: String,           // SECTION ("N" = no section)
    val admissionNo: String,       // ADMISSIONNO
    val gender: String,            // GENDER
    val dob: LocalDate?,           // DOB
    val bloodGroup: String?,       // BLOODGROUP
    val mobile: String,            // MOBILENO
    val email: String?,            // EMAIL
    val fatherName: String?,       // FATHERNAME
    val motherName: String?,       // MOTHERNAME
    val address: String?,          // ADDRESS
    val photoPath: String?,        // PROFILEIMAGE, relative to /static/
    val feeGroup: String?,         // FEEGROUPNAME
    // Remaining columns of GET_DATA_DETAILS('STUDENT'), shown on the Profile tab.
    val district: String? = null,
    val state: String? = null,
    val religion: String? = null,
    val community: String? = null,
    val nationality: String? = null,
    val motherTongue: String? = null,
    val fatherOccupation: String? = null,
    val motherOccupation: String? = null,
    val emergencyMobile1: String? = null,
    val emergencyMobile2: String? = null,
    val enrollmentNo: String? = null,
    val admissionYear: String? = null,
    val previousSchool: String? = null,
    val medicalInformation: String? = null,
    val allergies: String? = null
) {
    val fullAddress: String
        get() = listOf(address, district, state).filterNot { it.isNullOrBlank() }.joinToString(", ")

    val classLabel: String
        get() {
            val cls = when (classType) {
                "Nursery", "LKG", "UKG" -> classType
                "Primary1" -> "Primary 1"
                "Primary2" -> "Primary 2"
                else -> "Class $classType"
            }
            return if (section.isBlank() || section == "N") cls else "$cls — Section $section"
        }
}

enum class AttendanceStatus(val label: String, val shortLabel: String) {
    Present("Present", "P"),
    Absent("Absent", "A"),
    Leave("On leave", "L"),
    Holiday("Holiday", "H"),
    NotMarked("Not marked", "–")
}

// One row of GET /student/my-attendance/{encId}
data class AttendanceDay(val date: LocalDate, val status: AttendanceStatus)

data class AttendanceSummary(val days: List<AttendanceDay>) {
    // Same rule as the web portal: present days / all logged days.
    val presentDays: Int get() = days.count { it.status == AttendanceStatus.Present }
    val absentDays: Int get() = days.count { it.status == AttendanceStatus.Absent }
    val leaveDays: Int get() = days.count { it.status == AttendanceStatus.Leave }
    val totalDays: Int get() = days.size
    val firstMonth: YearMonth? get() = days.minOfOrNull { it.date }?.let(YearMonth::from)
    fun forMonth(month: YearMonth): AttendanceSummary =
        AttendanceSummary(days.filter { YearMonth.from(it.date) == month })
    fun statusOn(date: LocalDate): AttendanceStatus? = days.firstOrNull { it.date == date }?.status
    val percentage: Int get() = if (totalDays == 0) 0 else Math.round(presentDays * 100f / totalDays)
    val todayStatus: AttendanceStatus
        get() = days.firstOrNull { it.date == LocalDate.now() }?.status ?: AttendanceStatus.NotMarked
    val recent: List<AttendanceDay> get() = days.sortedBy { it.date }.takeLast(30)
}

enum class InstallmentStatus(val label: String) { Paid("Paid"), Partial("Partial"), Unpaid("Unpaid") }

// Installment row of GET /student/fee-ledger/self/me (one per fee type per month).
data class FeeInstallment(
    val month: String,          // fee_month
    val feeName: String,        // fee_name
    val amountDue: Long,        // amount_due (net of concession)
    val amountPaid: Long,       // amount_paid
    val concession: Long = 0,   // concession_amount
    val isPaid: Boolean,        // is_paid
    val id: Int? = null         // id (STUDENT_FEES row), used to pay specific heads
) {
    val balance: Long get() = (amountDue - amountPaid).coerceAtLeast(0)
}

// All fee types of one month, shown as a single installment like the web portal's month pills.
data class FeeMonth(val month: String, val items: List<FeeInstallment>) {
    val amountDue: Long get() = items.sumOf { it.amountDue }
    val amountPaid: Long get() = items.sumOf { it.amountPaid }
    val balance: Long get() = items.sumOf { it.balance }
    val status: InstallmentStatus
        get() = when {
            items.all { it.isPaid } -> InstallmentStatus.Paid
            amountPaid > 0 -> InstallmentStatus.Partial
            else -> InstallmentStatus.Unpaid
        }
}

// Payment transaction row of GET /student/fee-ledger/self/me
data class FeePayment(
    val invoiceNo: String,        // invoice_no
    val paidDate: LocalDate,      // paid_date
    val paymentMode: String,      // payment_mode: Cash, Cheque, UPI, Card, NetBanking
    val amount: Long,             // amount
    val session: String,          // session
    val transactionId: String?    // transaction_id
)

// GET /student/fee-ledger/self/me: summary row, payment transactions and installment breakdown.
data class FeeSummary(
    val totalDue: Long,             // total_due
    val totalPaid: Long,            // total_paid
    val balance: Long,              // balance
    val totalConcession: Long = 0,  // total_concession
    val installments: List<FeeInstallment> = emptyList(),
    val payments: List<FeePayment> = emptyList()
) {
    // Keeps the session order (April..March) the installments arrive in.
    val months: List<FeeMonth>
        get() = installments.groupBy { it.month }.map { (month, items) -> FeeMonth(month, items) }
    val pendingMonths: List<FeeMonth> get() = months.filter { it.status != InstallmentStatus.Paid }
    val paidFraction: Float get() = if (totalDue == 0L) 0f else (totalPaid.toFloat() / totalDue).coerceIn(0f, 1f)
}

// One row of GET /academics/timetable
data class TimetableSlot(
    val dayOfWeek: DayOfWeek,  // DAYOFWEEK
    val periodNo: Int,         // PERIODNO
    val subject: String,       // SUBJECT
    val startTime: LocalTime,  // START_TIME
    val endTime: LocalTime,    // END_TIME
    val teacher: String? = null // teacher_name
)

// One row of GET /student/download-assignments
data class Assignment(
    val title: String,               // ASSIGNMENTTITLE
    val subject: String,             // SUBJECT
    val classType: String,           // CLASS
    val fileUrl: String,             // FILEURL, relative to /static/ (blank when no file)
    val section: String = "",        // SECTION
    val createdDate: LocalDate? = null // CreatedDate
) {
    val fileExtension: String get() = fileUrl.substringAfterLast('.', "").uppercase()
}

// One row of GET /notice
data class Notice(
    val title: String,              // title
    val body: String,               // body
    val targetRole: String,         // target_role: All | Student | Teacher | Parent
    val createdAt: LocalDateTime,   // created_at
    val id: Int = 0,                // id
    val createdBy: String? = null   // created_by
)

// First row of GET /parent/bus-tracking -> tracking_data
data class BusTracking(
    val busName: String,            // BUS_NAME
    val vehicleNum: String,         // VEHICLE_NUM
    val routeName: String,          // ROUTE_NAME
    val tripType: String,           // TRIP_TYPE
    val tripStatus: String,         // TRIP_STATUS, card shows only for In_Progress
    val pickupStatus: String?,      // PICKUP_STATUS
    val lastUpdated: LocalDateTime?,// LAST_UPDATED
    val latitude: Double?,          // CURRENT_LAT
    val longitude: Double?          // CURRENT_LNG
) {
    val isLive: Boolean get() = tripStatus == "In_Progress" && latitude != null && longitude != null
}

data class HomeData(
    val profile: StudentProfile,
    val attendance: AttendanceSummary,
    val fees: FeeSummary,
    val timetable: List<TimetableSlot>,
    val assignments: List<Assignment>,
    val notices: List<Notice>,
    val bus: BusTracking?
) {
    val todaySlots: List<TimetableSlot>
        get() = timetable.filter { it.dayOfWeek == LocalDate.now().dayOfWeek }.sortedBy { it.periodNo }
}

// GET /master/exam-types + GET /student/my-marksheet?exam_type_id=… for every declared exam.
data class StudentResults(
    val examTypes: List<ExamType>,     // all exams; only declared ones have results
    val marks: List<StudentMarkRow>    // rows of declared exams only
)
