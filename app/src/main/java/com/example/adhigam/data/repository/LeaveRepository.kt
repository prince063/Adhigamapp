package com.example.adhigam.data.repository

import com.example.adhigam.data.model.LeaveRequest
import com.example.adhigam.data.model.LeaveStatus
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.date
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// Leave requests of the web portal's leave-management.html (HR).
interface LeaveRepository {
    suspend fun getLeaves(status: LeaveStatus?): List<LeaveRequest>
    suspend fun apply(from: LocalDate, to: LocalDate, leaveType: String, reason: String)
    suspend fun cancel(leaveId: Int)
    suspend fun decide(leaveId: Int, status: LeaveStatus, remarks: String)
}

class ApiLeaveRepository(private val api: ApiClient) : LeaveRepository {

    // GET /hr/leave-requests?status= — the backend limits non-admins to their own requests.
    override suspend fun getLeaves(status: LeaveStatus?): List<LeaveRequest> {
        val rows = api.get("hr/leave-requests?status=${status?.value.orEmpty()}") as? JSONArray ?: JSONArray()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val from = row.date("FROM_DATE", "from_date") ?: return@mapNotNull null
            val to = row.date("TO_DATE", "to_date") ?: from
            LeaveRequest(
                id = row.int("ID", "id") ?: return@mapNotNull null,
                staffName = row.str("staff_name", "STAFF_NAME").orEmpty(),
                designation = row.str("designation", "DESIGNATION").orEmpty(),
                leaveType = row.str("LEAVE_TYPE", "leave_type").orEmpty(),
                fromDate = from,
                toDate = to,
                days = row.int("days", "DAYS") ?: 1,
                reason = row.str("REASON", "reason").orEmpty(),
                status = LeaveStatus.from(row.str("STATUS", "status")),
                approvedBy = row.str("approved_by_name", "APPROVED_BY_NAME"),
                remarks = row.str("REMARKS", "remarks")
            )
        }
    }

    // POST /hr/leave-requests {id: 0, from_date, to_date, leave_type, reason} — always for the logged-in user.
    override suspend fun apply(from: LocalDate, to: LocalDate, leaveType: String, reason: String) {
        val body = JSONObject()
            .put("id", 0)
            .put("from_date", from.toString())
            .put("to_date", to.toString())
            .put("leave_type", leaveType)
            .put("reason", reason)
        api.post("hr/leave-requests", body)
    }

    // DELETE /hr/leave-requests/{id} — a teacher can only cancel their own pending request.
    override suspend fun cancel(leaveId: Int) {
        api.delete("hr/leave-requests/$leaveId")
    }

    // POST /hr/leave-requests/approve {id, status: Approved | Rejected, remarks} (admins only).
    override suspend fun decide(leaveId: Int, status: LeaveStatus, remarks: String) {
        val body = JSONObject()
            .put("id", leaveId)
            .put("status", status.value)
            .put("remarks", remarks)
        api.post("hr/leave-requests/approve", body)
    }
}
