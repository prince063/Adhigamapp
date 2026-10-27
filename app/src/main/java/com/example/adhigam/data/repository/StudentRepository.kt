package com.example.adhigam.data.repository

import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.AttendanceDay
import com.example.adhigam.data.model.AttendanceStatus
import com.example.adhigam.data.model.AttendanceSummary
import com.example.adhigam.data.model.BusTracking
import com.example.adhigam.data.model.FeeReceipt
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.HomeData
import com.example.adhigam.data.model.PaymentMode
import com.example.adhigam.data.model.TimetableSlot
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.date
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.remote.toAssignment
import com.example.adhigam.data.remote.toFeeReceipt
import com.example.adhigam.data.remote.toFeeSummary
import com.example.adhigam.data.remote.toStudentProfile
import com.example.adhigam.data.session.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

// The logged-in student's own data (for a parent: their child's). The backend finds the student from
// the token's mobile for both roles.
interface StudentRepository {
    // role decides which notices the backend returns (Student or Parent audience) and whether bus tracking applies.
    suspend fun getHomeData(role: UserRole = UserRole.Student): HomeData
    suspend fun getFeeDetails(): FeeSummary
    suspend fun getAttendance(): AttendanceSummary
    suspend fun getReceipt(invoiceNo: String, session: String): FeeReceipt
    suspend fun payFee(month: String, mode: PaymentMode): FeePaid
    suspend fun getTimetable(): List<TimetableSlot>
    suspend fun getAssignments(): List<Assignment>
}

// Result of POST /student/pay-fee (UPDATE_FEE_STATUS): the invoice the payment was logged under.
data class FeePaid(val invoiceNo: String, val session: String)

class ApiStudentRepository(
    private val api: ApiClient,
    private val noticeRepository: NoticeRepository,
    private val session: () -> Session?
) : StudentRepository {

    // /student/profile is needed for the student's encrypted id, class and section; it is cached per login.
    private val recordLock = Mutex()
    private var cachedRecord: Pair<String?, JSONObject>? = null

    private suspend fun record(): JSONObject = recordLock.withLock {
        val token = session()?.token
        cachedRecord?.takeIf { it.first == token }?.let { return@withLock it.second }
        val json = api.get("student/profile") as? JSONObject
        if (json?.str("student_name", "STUDENTNAME") == null) {
            throw IllegalStateException("No approved student profile found for this account")
        }
        cachedRecord = token to json
        json
    }

    private suspend fun encryptedId(): String =
        record().str("encrypted_id") ?: throw IllegalStateException("Student record has no id")

    override suspend fun getHomeData(role: UserRole): HomeData = coroutineScope {
        val json = record()
        val profile = json.toStudentProfile() ?: throw IllegalStateException("No approved student profile found")
        val attendance = async { getAttendance() }
        val fees = async { getFeeDetails() }
        val timetable = async { timetableFor(profile.classType, profile.section) }
        val assignments = async { getAssignments() }
        val notices = async { noticeRepository.getNotices() }
        // Bus tracking is a parent-only endpoint; a failure there shouldn't break the home screen.
        val bus = async { if (role == UserRole.Parent) runCatching { busTracking() }.getOrNull() else null }
        HomeData(
            profile = profile,
            attendance = attendance.await(),
            fees = fees.await(),
            timetable = timetable.await(),
            assignments = assignments.await().take(3),
            notices = notices.await().take(3),
            bus = bus.await()
        )
    }

    // GET /student/fee-ledger/self/me
    override suspend fun getFeeDetails(): FeeSummary =
        (api.get("student/fee-ledger/self/me") as? JSONArray ?: JSONArray()).toFeeSummary()

    // GET /student/my-attendance/{id}: one row per marked day; ATTENDANCESTATUS 1 = present, 0 = absent.
    override suspend fun getAttendance(): AttendanceSummary {
        val rows = api.get("student/my-attendance/${encryptedId().encoded()}") as? JSONArray ?: JSONArray()
        val days = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val date = row.date("attendance_date", "ATTENDANCEDATE") ?: return@mapNotNull null
            val present = row.str("attendance_status", "ATTENDANCESTATUS").let { it == "1" || it.equals("true", ignoreCase = true) }
            AttendanceDay(date, if (present) AttendanceStatus.Present else AttendanceStatus.Absent)
        }
        return AttendanceSummary(days.distinctBy { it.date }.sortedBy { it.date })
    }

    // GET /student/payment-receipt/{id}?invoice_no&session — the backend allows a student their own receipts.
    override suspend fun getReceipt(invoiceNo: String, session: String): FeeReceipt {
        val number = invoiceNo.toIntOrNull() ?: throw IllegalArgumentException("Invalid invoice number")
        val json = api.get("student/payment-receipt/${encryptedId().encoded()}?invoice_no=$number&session=${session.encoded()}") as? JSONObject
            ?: throw IllegalStateException("Receipt not found")
        return json.toFeeReceipt(invoiceNo, session)
    }

    // The web parent portal's mock checkout: no gateway, POST /student/pay-fee marks every unpaid fee of the
    // month as paid. It takes the encrypted id of any STUDENT_FEES row of that month, which only
    // /student/fees/self returns (Yearly = all fee types, so months with only one-time fees are covered too).
    override suspend fun payFee(month: String, mode: PaymentMode): FeePaid {
        val fees = api.get("student/fees/self?month_type=Yearly") as? JSONArray ?: JSONArray()
        val feeId = (0 until fees.length()).asSequence()
            .mapNotNull { fees.optJSONObject(it) }
            .firstOrNull { it.str("FEE_MONTH", "fee_month") == month && it.str("encrypted_id") != null }
            ?.str("encrypted_id")
            ?: throw IllegalStateException("No pending fee found for $month")
        val body = JSONObject()
            .put("encrypt_id", feeId)
            .put("encrypt_student_id", encryptedId())
            // TransactionType on the backend spells it "Netbanking".
            .put("payment_mode", if (mode == PaymentMode.NetBanking) "Netbanking" else mode.value)
            .put("transaction_id", "TXN-${(100_000_000..999_999_999).random()}")
            .put("remarks", "Simulated Online Fee Payment via Parent Portal")
        val json = api.post("student/pay-fee", body) as? JSONObject
        val invoice = json?.str("invoiceno", "INVOICENO") ?: throw IllegalStateException("Payment was not recorded")
        return FeePaid(invoice, json.str("session", "SESSION").orEmpty())
    }

    override suspend fun getTimetable(): List<TimetableSlot> {
        val json = record()
        return timetableFor(json.str("class_type", "CLASS").orEmpty(), json.str("section", "SECTION").orEmpty())
    }

    // GET /academics/timetable?class_type&section (GET_TIMETABLE: t.* plus teacher_name).
    private suspend fun timetableFor(classType: String, section: String): List<TimetableSlot> {
        if (classType.isBlank()) return emptyList()
        val rows = api.get(
            "academics/timetable?class_type=${classType.encoded()}&section=${section.ifBlank { "A" }.encoded()}"
        ) as? JSONArray ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val day = row.str("DAY_OF_WEEK", "day_of_week")
                ?.let { d -> DayOfWeek.entries.firstOrNull { it.name.equals(d, ignoreCase = true) } }
                ?: return@mapNotNull null
            TimetableSlot(
                dayOfWeek = day,
                periodNo = row.int("PERIOD_NO", "period_no") ?: return@mapNotNull null,
                subject = row.str("SUBJECT", "subject").orEmpty(),
                startTime = parseTime(row.str("START_TIME", "start_time")) ?: return@mapNotNull null,
                endTime = parseTime(row.str("END_TIME", "end_time")) ?: return@mapNotNull null,
                teacher = row.str("teacher_name", "TEACHER_NAME")
            )
        }
    }

    // GET /student/download-assignments: assignments for the student's class and section, newest first.
    override suspend fun getAssignments(): List<Assignment> {
        val rows = api.get("student/download-assignments") as? JSONArray ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.toAssignment() }
    }

    // GET /parent/bus-tracking → {"tracking_data": [...]}; only an in-progress trip is shown.
    private suspend fun busTracking(): BusTracking? {
        val json = api.get("parent/bus-tracking") as? JSONObject ?: return null
        val row = json.optJSONArray("tracking_data")?.optJSONObject(0) ?: return null
        return BusTracking(
            busName = row.str("BUS_NAME").orEmpty(),
            vehicleNum = row.str("VEHICLE_NUM").orEmpty(),
            routeName = row.str("ROUTE_NAME").orEmpty(),
            tripType = row.str("TRIP_TYPE").orEmpty(),
            tripStatus = row.str("TRIP_STATUS").orEmpty(),
            pickupStatus = row.str("PICKUP_STATUS"),
            lastUpdated = row.str("LAST_UPDATED")
                ?.let { runCatching { LocalDateTime.parse(it.replace(' ', 'T').take(19)) }.getOrNull() },
            latitude = row.str("CURRENT_LAT")?.toDoubleOrNull(),
            longitude = row.str("CURRENT_LNG")?.toDoubleOrNull()
        )
    }

    // TIMETABLE stores times as text: "8:00", "08:00" or "08:00:00".
    private fun parseTime(text: String?): LocalTime? {
        val parts = text?.trim()?.split(':') ?: return null
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
