package com.example.adhigam.data.repository

import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.str
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// The staff Student List (student-list.html): list with filters, approve / reject, reset password.
interface StudentListRepository {
    // classType / section null = all; search matches name, mobile or admission no. Every status is
    // returned so the screen can count them; it filters by status itself.
    suspend fun getStudentList(classType: String?, section: String?, search: String): List<StudentListItem>
    suspend fun updateStudentStatus(encryptedId: String, status: StudentStatus)
    suspend fun resetStudentPassword(admissionNo: String, password: String)
}

class ApiStudentListRepository(private val api: ApiClient) : StudentListRepository {

    // GET /student/list?class_value&section_value&search (GET_STUDENT_LIST)
    override suspend fun getStudentList(classType: String?, section: String?, search: String): List<StudentListItem> {
        val query = listOfNotNull(
            classType?.let { "class_value=${it.encoded()}" },
            section?.let { "section_value=${it.encoded()}" },
            search.trim().takeIf { it.isNotEmpty() }?.let { "search=${it.encoded()}" }
        ).joinToString("&")
        val rows = api.get(if (query.isEmpty()) "student/list" else "student/list?$query") as? JSONArray ?: return emptyList()
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.toListItem() }
    }

    // POST /student/update-status?encrypt_id&status
    override suspend fun updateStudentStatus(encryptedId: String, status: StudentStatus) {
        api.post("student/update-status?encrypt_id=${encryptedId.encoded()}&status=${status.name}", JSONObject())
    }

    // POST /student/reset-password {admission_no, password} — Superadmin / Principal only.
    override suspend fun resetStudentPassword(admissionNo: String, password: String) {
        val number = admissionNo.toIntOrNull() ?: throw IllegalArgumentException("Invalid admission number")
        api.post("student/reset-password", JSONObject().put("admission_no", number).put("password", password))
    }

    private fun JSONObject.toListItem(): StudentListItem? {
        val encryptedId = str("encrypted_id") ?: return null
        return StudentListItem(
            encryptedId = encryptedId,
            admissionNo = str("ADMISSIONNO", "admission_no").orEmpty(),
            name = str("STUDENTNAME", "student_name").orEmpty(),
            fatherName = str("FATHERNAME", "father_name"),
            mobile = str("MOBILE", "mobile").orEmpty(),
            dueFee = str("DUE_FEE_AMOUNT", "due_fee_amount")?.toDoubleOrNull()?.let { Math.round(it) } ?: 0L,
            classType = str("CLASS", "class_type").orEmpty(),
            section = str("SECTION", "section").orEmpty(),
            status = str("STATUS", "status")
                ?.let { s -> StudentStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } }
                ?: StudentStatus.Pending
        )
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")
}
