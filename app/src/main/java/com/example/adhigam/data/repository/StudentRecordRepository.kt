package com.example.adhigam.data.repository

import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.toFeeSummary
import com.example.adhigam.data.remote.toStaffStudentDetail
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// One student's record as staff see it (student-view.html) and their fee ledger.
interface StudentRecordRepository {
    suspend fun getStudentDetail(encryptedId: String): StaffStudentDetail
    suspend fun getFeeLedger(encryptedId: String): FeeSummary
}

class ApiStudentRecordRepository(private val api: ApiClient) : StudentRecordRepository {

    // GET /student/view/{encrypt_id}
    override suspend fun getStudentDetail(encryptedId: String): StaffStudentDetail {
        val json = api.get("student/view/${encryptedId.encoded()}") as? JSONObject
        return json?.toStaffStudentDetail(encryptedId) ?: throw IllegalStateException("Student not found")
    }

    // GET /student/fee-ledger/{encrypt_id}
    override suspend fun getFeeLedger(encryptedId: String): FeeSummary =
        (api.get("student/fee-ledger/${encryptedId.encoded()}") as? JSONArray ?: JSONArray()).toFeeSummary()

    // Encrypted ids can contain '/', '+' or '='; encode them for use in the path.
    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
