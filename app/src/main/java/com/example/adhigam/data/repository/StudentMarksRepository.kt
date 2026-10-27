package com.example.adhigam.data.repository

import com.example.adhigam.data.model.Marksheet
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentMarkRow
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.remote.toStaffStudentDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// One student's marks (student-marks.html) and the marksheet of one exam (marksheet-preview.html), for staff.
interface StudentMarksRepository {
    suspend fun getStudentMarks(encryptedId: String): Pair<StudentListItem, List<StudentMarkRow>>
    suspend fun getMarksheet(encryptedId: String, examTypeId: Int): Marksheet
}

class ApiStudentMarksRepository(private val api: ApiClient) : StudentMarksRepository {

    // GET /student/view/{id} for the student, then GET /exam/student-marks?admission_no=… (GET_STUDENT_MARKS_BY_ADMNO).
    override suspend fun getStudentMarks(encryptedId: String): Pair<StudentListItem, List<StudentMarkRow>> {
        val detail = studentDetail(encryptedId)
        val admissionNo = detail.profile.admissionNo.toIntOrNull()
            ?: throw IllegalStateException("This student has no admission number yet")
        val rows = api.get("exam/student-marks?admission_no=$admissionNo") as? JSONArray ?: JSONArray()
        val marks = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            StudentMarkRow(
                examTypeId = row.int("EXAMTYPEID", "exam_type_id") ?: 0,
                examType = row.str("EXAMTYPE", "exam_type").orEmpty(),
                subject = row.str("SUBJECT", "subject") ?: return@mapNotNull null,
                writtenObtained = row.number("WRITTENOBTAINMARKS", "written_obtain_marks"),
                writtenMax = row.number("WRITTENMAXMARKS", "written_max_marks"),
                practicalObtained = row.number("PRACTICALOBTAINMARKS", "practical_obtain_marks"),
                practicalMax = row.number("PRACTICALMAXMARKS", "practical_max_marks")
            )
        }
        return detail.toListItem() to marks
    }

    // GET /student/marksheet-preview?admission_no&class_id&exam_type_id. Its header has no father's name or
    // section, so those come from the student record.
    override suspend fun getMarksheet(encryptedId: String, examTypeId: Int): Marksheet {
        val detail = studentDetail(encryptedId)
        val p = detail.profile
        val admissionNo = p.admissionNo.toIntOrNull() ?: throw IllegalStateException("This student has no admission number yet")
        if (p.classType.isBlank()) throw IllegalStateException("This student has no class assigned")
        val (json, school) = coroutineScope {
            val sheet = async {
                api.get(
                    "student/marksheet-preview?admission_no=$admissionNo&class_id=${p.classType.encoded()}&exam_type_id=$examTypeId"
                ) as? JSONObject
            }
            // GET /settings/school-config for the letterhead.
            val config = async { runCatching { api.get("settings/school-config") as? JSONObject }.getOrNull() }
            sheet.await() to config.await()
        }
        if (json == null) throw IllegalStateException("No marksheet found for this exam")

        val items = json.optJSONArray("marks") ?: JSONArray()
        val examName = json.str("exam_type", "EXAMTYPE").orEmpty()
        val rows = (0 until items.length()).mapNotNull { i ->
            val row = items.optJSONObject(i) ?: return@mapNotNull null
            StudentMarkRow(
                examTypeId = examTypeId,
                examType = examName,
                subject = row.str("subject", "SUBJECT") ?: return@mapNotNull null,
                writtenObtained = row.number("written_obtain_marks", "WRITTENOBTAINMARKS"),
                writtenMax = row.number("written_max_marks", "WRITTENMAXMARKS"),
                practicalObtained = row.number("practical_obtain_marks", "PRACTICALOBTAINMARKS"),
                practicalMax = row.number("practical_max_marks", "PRACTICALMAXMARKS"),
                backendPass = row.str("result_status", "RESULT_STATUS")?.let { it.equals("PASS", ignoreCase = true) }
            )
        }
        if (rows.isEmpty()) throw IllegalStateException("No marks have been entered for this exam yet")
        return Marksheet(
            studentName = json.str("student_name", "STUDENTNAME") ?: p.name,
            fatherName = json.str("father_name", "FATHERNAME") ?: p.fatherName,
            admissionNo = json.str("admission_no", "ADMISSIONNO") ?: p.admissionNo,
            classType = json.str("class_type", "CLASS") ?: p.classType,
            section = json.str("section", "SECTION") ?: p.section,
            academicYear = json.str("year", "YEAR").orEmpty(),
            examName = examName,
            rows = rows,
            remarks = json.str("remarks", "REMARKS"),
            backendPass = json.str("overall_result_status")?.let { it.equals("PASS", ignoreCase = true) },
            schoolName = school?.str("school_name").orEmpty(),
            schoolAddress = school?.str("school_address").orEmpty()
        )
    }

    private suspend fun studentDetail(encryptedId: String): StaffStudentDetail {
        val json = api.get("student/view/${encryptedId.encoded()}") as? JSONObject
        return json?.toStaffStudentDetail(encryptedId) ?: throw IllegalStateException("Student not found")
    }

    private fun StaffStudentDetail.toListItem() = StudentListItem(
        encryptedId = encryptedId,
        admissionNo = profile.admissionNo,
        name = profile.name,
        fatherName = profile.fatherName,
        mobile = profile.mobile,
        dueFee = 0,
        classType = profile.classType,
        section = profile.section,
        status = status
    )

    private fun JSONObject.number(vararg keys: String): Double = str(*keys)?.toDoubleOrNull() ?: 0.0

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
