package com.example.adhigam.data.repository

import com.example.adhigam.data.model.ExamType
import com.example.adhigam.data.model.LabAllocation
import com.example.adhigam.data.model.StudentMarkRow
import com.example.adhigam.data.model.StudentResults
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.dateTime
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

// Results and lab sessions of the logged-in student (for a parent: their child).
interface ResultsLabRepository {
    suspend fun getResults(): StudentResults
    suspend fun getLabSessions(): List<LabAllocation>
}

class ApiResultsLabRepository(private val api: ApiClient) : ResultsLabRepository {

    // GET /master/exam-types, then GET /student/my-marksheet?exam_type_id=… for every declared exam (in parallel).
    // Each marksheet answer is [header, subject rows…], or [{"result_declared": 0}] when not declared yet.
    override suspend fun getResults(): StudentResults = coroutineScope {
        val typesJson = api.get("master/exam-types") as? JSONArray ?: JSONArray()
        val examTypes = (0 until typesJson.length()).mapNotNull { i ->
            val row = typesJson.optJSONObject(i) ?: return@mapNotNull null
            ExamType(
                id = row.int("id", "ID") ?: return@mapNotNull null,
                name = row.str("name", "NAME").orEmpty(),
                isResultDeclared = row.str("is_result_declared", "IS_RESULT_DECLARED").let { it == "1" || it.equals("true", true) }
            )
        }
        val marks = examTypes.filter { it.isResultDeclared }.map { exam ->
            async {
                val rows = api.get("student/my-marksheet?exam_type_id=${exam.id}") as? JSONArray ?: JSONArray()
                (0 until rows.length()).mapNotNull { i ->
                    val row = rows.optJSONObject(i) ?: return@mapNotNull null
                    val subject = row.str("subject", "SUBJECT") ?: return@mapNotNull null // header / flag rows have no subject
                    StudentMarkRow(
                        examTypeId = exam.id,
                        examType = exam.name,
                        subject = subject,
                        writtenObtained = row.number("written_obtain_marks", "WRITTENOBTAINMARKS"),
                        writtenMax = row.number("written_max_marks", "WRITTENMAXMARKS"),
                        practicalObtained = row.number("practical_obtain_marks", "PRACTICALOBTAINMARKS"),
                        practicalMax = row.number("practical_max_marks", "PRACTICALMAXMARKS"),
                        backendPass = row.str("result_status", "RESULT_STATUS")?.equals("PASS", ignoreCase = true)
                    )
                }
            }
        }.awaitAll().flatten()
        StudentResults(examTypes, marks)
    }

    // GET /lab/reservations/self (LAB_SESSION_ALLOCATIONS joined with session, unit, equipment, experiment, lab).
    override suspend fun getLabSessions(): List<LabAllocation> {
        val rows = api.get("lab/reservations/self") as? JSONArray ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            LabAllocation(
                id = row.str("ID", "id") ?: i.toString(),
                experimentName = row.str("EXPERIMENT_NAME").orEmpty(),
                labName = row.str("LAB_NAME").orEmpty(),
                equipmentName = row.str("EQUIPMENT_NAME").orEmpty(),
                internalUid = row.str("INTERNAL_UID").orEmpty(),
                startTime = row.dateTime("START_TIME") ?: return@mapNotNull null,
                endTime = row.dateTime("END_TIME") ?: return@mapNotNull null,
                issuedAt = row.dateTime("ISSUED_AT"),
                returnedAt = row.dateTime("RETURNED_AT"),
                conditionPre = row.str("CONDITION_PRE"),
                conditionPost = row.str("CONDITION_POST")
            )
        }
    }

    private fun JSONObject.number(vararg keys: String): Double = str(*keys)?.toDoubleOrNull() ?: 0.0
}
