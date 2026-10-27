package com.example.adhigam.data.repository

import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.AttendanceEntry
import com.example.adhigam.data.model.AttendanceSummaryRow
import com.example.adhigam.data.model.HeatmapStudent
import com.example.adhigam.data.model.StaffAttendanceEntry
import com.example.adhigam.data.model.ExamType
import com.example.adhigam.data.model.MarksEntry
import com.example.adhigam.data.model.StaffProfile
import com.example.adhigam.data.model.TeacherHomeData
import com.example.adhigam.data.model.TeacherSubject
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.UploadPart
import com.example.adhigam.data.remote.date
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.remote.toAssignment
import com.example.adhigam.data.session.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

// Data for the Teacher and Staff (Superadmin/Admin/Principal) side of the app.
interface StaffRepository {
    suspend fun getTeacherHome(): TeacherHomeData
    // The logged-in staff member (the backend has no self-profile endpoint, so this is the session).
    suspend fun getStaffProfile(): StaffProfile
    suspend fun getClassAttendance(classType: String, section: String, date: LocalDate): List<AttendanceEntry>
    // Returns how many students were saved.
    suspend fun saveClassAttendance(date: LocalDate, entries: List<AttendanceEntry>): Int
    suspend fun getExamTypes(): List<ExamType>
    // For a Teacher only their own assignments (the backend filters by the logged-in user).
    suspend fun getTeacherSubjects(): List<TeacherSubject>
    suspend fun getMarks(examTypeId: Int, classType: String, section: String, subject: String): List<MarksEntry>
    // Returns how many students were saved.
    suspend fun saveMarks(examTypeId: Int, classType: String, section: String, subject: String, entries: List<MarksEntry>): Int
    // Totals for the month of the given date (the backend ignores the year).
    suspend fun getAttendanceSummary(classType: String, section: String, date: LocalDate): List<AttendanceSummaryRow>
    suspend fun getAttendanceHeatmap(classType: String, section: String, month: Int, year: Int): List<HeatmapStudent>
    suspend fun getStaffAttendance(date: LocalDate): List<StaffAttendanceEntry>
    suspend fun saveStaffAttendance(date: LocalDate, entries: List<StaffAttendanceEntry>)
    // assignment.html / upload-assignment.html. For a Teacher the backend lists only their own uploads.
    suspend fun getAssignments(): List<Assignment>
    suspend fun uploadAssignment(classType: String, section: String, subject: String, title: String, file: UploadPart)
}

class ApiStaffRepository(
    private val api: ApiClient,
    private val noticeRepository: NoticeRepository,
    private val session: () -> Session?
) : StaffRepository {

    override suspend fun getTeacherHome(): TeacherHomeData = coroutineScope {
        val notices = async { noticeRepository.getNotices() }
        TeacherHomeData(profile = getStaffProfile(), notices = notices.await())
    }

    override suspend fun getStaffProfile(): StaffProfile {
        val user = session() ?: throw IllegalStateException("Please log in again")
        return StaffProfile(
            name = user.name.ifBlank { "Staff" },
            roleType = user.backendRole.ifBlank { user.role.label },
            mobile = user.mobile,
            email = null, address = null, city = null, state = null, pincode = null, dob = null, photoPath = null
        )
    }

    // GET /student/attendance?class_value&section_value&date (GET_STUDENT_ATTENDENCE): approved students of
    // the class with that day's record; ID is 0 and the status defaults to present when nothing is saved yet.
    override suspend fun getClassAttendance(classType: String, section: String, date: LocalDate): List<AttendanceEntry> {
        val rows = api.get(
            "student/attendance?class_value=${classType.encoded()}&section_value=${section.encoded()}&date=${date}T00:00:00"
        ) as? JSONArray ?: return emptyList()
        return rows.objects().mapNotNull { row ->
            AttendanceEntry(
                attendanceId = row.int("ID", "id") ?: 0,
                admissionNo = row.str("ADMISSIONNO", "admission_no") ?: return@mapNotNull null,
                studentName = row.str("STUDENTNAME", "student_name").orEmpty(),
                isPresent = row.flag("ATTENDANCESTATUS", "attendance_status", default = true)
            )
        }
    }

    // POST /student/attendance {attendance_date, attendances:[{admission_no, attendance_status, attendance_id}]};
    // a non-zero attendance_id updates that day's existing record.
    override suspend fun saveClassAttendance(date: LocalDate, entries: List<AttendanceEntry>): Int {
        val attendances = JSONArray()
        entries.forEach { e ->
            val admissionNo = e.admissionNo.toIntOrNull() ?: return@forEach
            attendances.put(
                JSONObject()
                    .put("admission_no", admissionNo)
                    .put("attendance_status", e.isPresent)
                    .put("attendance_id", e.attendanceId)
            )
        }
        api.post("student/attendance", JSONObject().put("attendance_date", "${date}T00:00:00").put("attendances", attendances))
        return attendances.length()
    }

    // GET /master/exam-types
    override suspend fun getExamTypes(): List<ExamType> {
        val rows = api.get("master/exam-types") as? JSONArray ?: return emptyList()
        return rows.objects().mapNotNull { row ->
            ExamType(
                id = row.int("id", "ID") ?: return@mapNotNull null,
                name = row.str("name", "NAME").orEmpty(),
                isResultDeclared = row.flag("is_result_declared", "IS_RESULT_DECLARED", default = false)
            )
        }
    }

    // GET /exam/teacher-subjects
    override suspend fun getTeacherSubjects(): List<TeacherSubject> {
        val rows = api.get("exam/teacher-subjects") as? JSONArray ?: return emptyList()
        return rows.objects().mapNotNull { row ->
            TeacherSubject(
                classType = row.str("class_type", "CLASS") ?: return@mapNotNull null,
                section = row.str("section", "SECTION").orEmpty(),
                subject = row.str("subject", "SUBJECT") ?: return@mapNotNull null
            )
        }
    }

    // GET /exam/marks?exam_type_id&class_id&section&subject (GET_STUDENT_EXAM_MARKS). id is 0 for students
    // with no marks saved yet; their obtained marks are shown as not entered rather than 0.
    override suspend fun getMarks(examTypeId: Int, classType: String, section: String, subject: String): List<MarksEntry> {
        val rows = api.get(
            "exam/marks?exam_type_id=$examTypeId&class_id=${classType.encoded()}&section=${section.encoded()}&subject=${subject.encoded()}"
        ) as? JSONArray ?: return emptyList()
        return rows.objects().mapNotNull { row ->
            val id = row.int("id", "ID") ?: 0
            MarksEntry(
                id = id,
                studentId = row.int("student_id", "STUDENTID") ?: return@mapNotNull null,
                admissionNo = row.str("ADMISSIONNO", "admission_no").orEmpty(),
                studentName = row.str("STUDENTNAME", "student_name").orEmpty(),
                writtenMax = row.number("WRITTENMAXMARKS", "written_max_marks") ?: 0.0,
                writtenObtained = if (id == 0) null else row.number("WRITTENOBTAINMARKS", "written_obtain_marks"),
                practicalMax = row.number("PRACTICALMAXMARKS", "practical_max_marks") ?: 0.0,
                practicalObtained = if (id == 0) null else row.number("PRACTICALOBTAINMARKS", "practical_obtain_marks")
            )
        }
    }

    // POST /exam/marks/batch with one StudentMarks per student.
    override suspend fun saveMarks(
        examTypeId: Int,
        classType: String,
        section: String,
        subject: String,
        entries: List<MarksEntry>
    ): Int {
        val body = JSONArray()
        entries.forEach { e ->
            body.put(
                JSONObject()
                    .put("id", e.id)
                    .put("student_id", e.studentId)
                    .put("admission_no", e.admissionNo.toIntOrNull() ?: 0)
                    .put("student_name", e.studentName)
                    .put("exam_type_id", examTypeId)
                    .put("class_type", classType)
                    .put("section", section)
                    .put("subject", subject)
                    .put("written_max_marks", e.writtenMax)
                    .put("written_obtain_marks", e.writtenObtained ?: 0.0)
                    .put("practical_max_marks", e.practicalMax)
                    .put("practical_obtain_marks", e.practicalObtained ?: 0.0)
            )
        }
        api.post("exam/marks/batch", body)
        return entries.size
    }

    // GET /student/attendance-summary?class_value&section_value&date
    override suspend fun getAttendanceSummary(classType: String, section: String, date: LocalDate): List<AttendanceSummaryRow> {
        val rows = api.get(
            "student/attendance-summary?class_value=${classType.encoded()}&section_value=${section.encoded()}&date=${date}T00:00:00"
        ) as? JSONArray ?: return emptyList()
        return rows.objects().map { row ->
            val total = row.int("TOTAL_DAYS", "total_days") ?: 0
            val present = row.int("PRESENT_DAYS", "present_days") ?: 0
            AttendanceSummaryRow(
                admissionNo = row.str("ADMISSIONNO", "admission_no").orEmpty(),
                studentName = row.str("STUDENTNAME", "student_name").orEmpty(),
                totalDays = total,
                presentDays = present,
                absentDays = row.int("ABSENT_DAYS", "absent_days") ?: (total - present)
            )
        }
    }

    // GET /hr/attendance-heatmap?class_type&section&month&year — one row per student per marked day
    // (and one row with no date for students with nothing marked), pivoted per student like the web page.
    override suspend fun getAttendanceHeatmap(classType: String, section: String, month: Int, year: Int): List<HeatmapStudent> {
        val rows = api.get(
            "hr/attendance-heatmap?class_type=${classType.encoded()}&section=${section.encoded()}&month=$month&year=$year"
        ) as? JSONArray ?: return emptyList()
        val byStudent = linkedMapOf<String, Pair<String, MutableMap<Int, Char>>>()
        rows.objects().forEach { row ->
            val admissionNo = row.str("admission_no", "ADMISSIONNO") ?: return@forEach
            val entry = byStudent.getOrPut(admissionNo) { row.str("student_name", "STUDENTNAME").orEmpty() to mutableMapOf() }
            val date = row.date("attn_date", "ATTN_DATE")
            val status = row.str("status", "STATUS")?.firstOrNull()?.uppercaseChar()
            if (date != null && status != null && date.monthValue == month && date.year == year) {
                entry.second[date.dayOfMonth] = status
            }
        }
        return byStudent.map { (admissionNo, value) -> HeatmapStudent(admissionNo, value.first, value.second) }
            .sortedBy { it.studentName }
    }

    // GET /hr/staff-attendance?attn_date
    override suspend fun getStaffAttendance(date: LocalDate): List<StaffAttendanceEntry> {
        val rows = api.get("hr/staff-attendance?attn_date=$date") as? JSONArray ?: return emptyList()
        return rows.objects().mapNotNull { row ->
            StaffAttendanceEntry(
                userId = row.int("user_id", "USER_ID") ?: return@mapNotNull null,
                staffName = row.str("staff_name").orEmpty(),
                designation = row.str("designation").orEmpty(),
                status = row.str("STATUS", "status") ?: "Present",
                inTime = row.str("IN_TIME", "in_time")?.take(5).orEmpty(),
                outTime = row.str("OUT_TIME", "out_time")?.take(5).orEmpty(),
                remarks = row.str("REMARKS", "remarks").orEmpty()
            )
        }
    }

    // POST /hr/staff-attendance/bulk {entries:[{user_id, attn_date, status, in_time, out_time, remarks}]}
    override suspend fun saveStaffAttendance(date: LocalDate, entries: List<StaffAttendanceEntry>) {
        val list = JSONArray()
        entries.forEach { e ->
            list.put(
                JSONObject()
                    .put("user_id", e.userId)
                    .put("attn_date", date.toString())
                    .put("status", e.status)
                    .put("in_time", e.inTime)
                    .put("out_time", e.outTime)
                    .put("remarks", e.remarks)
            )
        }
        api.post("hr/staff-attendance/bulk", JSONObject().put("entries", list))
    }

    // GET /student/assignments (no class / section / subject filter)
    override suspend fun getAssignments(): List<Assignment> {
        val rows = api.get("student/assignments") as? JSONArray ?: return emptyList()
        return rows.objects().mapNotNull { it.toAssignment() }
    }

    // POST /student/upload-assignment (multipart: class_type, section, subject, assignment_title, file).
    // The backend accepts only PDF, PNG and JPG files.
    override suspend fun uploadAssignment(classType: String, section: String, subject: String, title: String, file: UploadPart) {
        api.postMultipart(
            "student/upload-assignment",
            mapOf(
                "class_type" to classType,
                "section" to section,
                "subject" to subject,
                "assignment_title" to title
            ),
            file
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.number(vararg keys: String): Double? = str(*keys)?.toDoubleOrNull()

    // MySQL booleans arrive as 1/0 or true/false.
    private fun JSONObject.flag(vararg keys: String, default: Boolean): Boolean {
        val value = str(*keys) ?: return default
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
