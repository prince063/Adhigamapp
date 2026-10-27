package com.example.adhigam.data.repository

import com.example.adhigam.data.model.StudentProfile
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.toStudentProfile
import org.json.JSONObject

// Profile of the logged-in student (or, for a parent, their child).
interface StudentProfileRepository {
    suspend fun getProfile(): StudentProfile
}

// GET /student/profile — the backend finds the student from the token's mobile for both Student
// and Parent and returns the STUDENTS record (GET_DATA_DETAILS 'STUDENT', lower-case column names).
class ApiStudentProfileRepository(private val api: ApiClient) : StudentProfileRepository {

    override suspend fun getProfile(): StudentProfile {
        val json = api.get("student/profile") as? JSONObject
        // The backend answers {} when no approved student matches the account.
        return json?.toStudentProfile()
            ?: throw IllegalStateException("No approved student profile found for this account")
    }
}
