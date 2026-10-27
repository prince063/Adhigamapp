package com.example.adhigam.data.repository

import com.example.adhigam.data.model.AdminHomeData
import com.example.adhigam.data.model.MonthlyCollection
import com.example.adhigam.data.model.StaffProfile
import com.example.adhigam.data.model.StudentStats
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.session.Session
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

// Data of the Admin / Principal / Superadmin dashboard.
interface AdminDashboardRepository {
    suspend fun getAdminHome(): AdminHomeData
}

// GET /student/dashboard (status counts), GET /student/dashboard-charts (fee trend) and the notices,
// fetched in parallel. The header shows the logged-in user from the session.
class ApiAdminDashboardRepository(
    private val api: ApiClient,
    private val noticeRepository: NoticeRepository,
    private val session: () -> Session?
) : AdminDashboardRepository {

    override suspend fun getAdminHome(): AdminHomeData = coroutineScope {
        val counts = async { api.get("student/dashboard") as? JSONObject }
        val charts = async { api.get("student/dashboard-charts") as? JSONObject }
        val notices = async { noticeRepository.getNotices() }

        val user = session()
        AdminHomeData(
            profile = StaffProfile(
                name = user?.name.orEmpty().ifBlank { "Admin" },
                roleType = user?.backendRole.orEmpty().ifBlank { "Admin" },
                mobile = user?.mobile.orEmpty(),
                email = null, address = null, city = null, state = null, pincode = null, dob = null, photoPath = null
            ),
            stats = counts.await().toStats(),
            feeTrend = charts.await()?.optJSONArray("fee_trend").toSessionTrend(),
            notices = notices.await()
        )
    }

    // {"TOTALSTUDENTS":0,"APPROVEDSTUDENTS":0,"PENDINGSTUDENTS":0,"REJECTEDSTUDENTS":0}
    private fun JSONObject?.toStats(): StudentStats = StudentStats(
        total = this?.int("TOTALSTUDENTS", "totalstudents") ?: 0,
        approved = this?.int("APPROVEDSTUDENTS", "approvedstudents") ?: 0,
        pending = this?.int("PENDINGSTUDENTS", "pendingstudents") ?: 0,
        rejected = this?.int("REJECTEDSTUDENTS", "rejectedstudents") ?: 0
    )

    // fee_trend rows {month_name, month_num, year_num, total_collected} for the current Apr–Mar session;
    // months with no payments are missing, so the list is filled out to all twelve months.
    private fun JSONArray?.toSessionTrend(): List<MonthlyCollection> {
        val collected = mutableMapOf<String, Long>()
        if (this != null) {
            for (i in 0 until length()) {
                val row = optJSONObject(i) ?: continue
                val month = row.str("month_name", "MONTH_NAME") ?: continue
                val amount = row.str("total_collected", "TOTAL_COLLECTED")?.toDoubleOrNull() ?: 0.0
                collected[month] = (collected[month] ?: 0L) + Math.round(amount)
            }
        }
        return SESSION_MONTHS.map { MonthlyCollection(it, collected[it] ?: 0L) }
    }

    private companion object {
        val SESSION_MONTHS = listOf(
            "April", "May", "June", "July", "August", "September",
            "October", "November", "December", "January", "February", "March"
        )
    }
}
