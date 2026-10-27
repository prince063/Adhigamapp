package com.example.adhigam.data.repository

import com.example.adhigam.data.model.ClassPeriod
import com.example.adhigam.data.model.TeacherOption
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek

// Class timetables as staff manage them (timetable.html): view a class, add / change / remove periods.
interface TimetableRepository {
    suspend fun getPeriods(classType: String, section: String): List<ClassPeriod>
    suspend fun savePeriod(classType: String, section: String, period: ClassPeriod)
    suspend fun deletePeriod(classType: String, section: String, day: DayOfWeek, periodNo: Int)
    suspend fun getTeachers(): List<TeacherOption>
}

class ApiTimetableRepository(private val api: ApiClient) : TimetableRepository {

    // GET /academics/timetable?class_type&section (GET_TIMETABLE: t.* plus teacher_name).
    override suspend fun getPeriods(classType: String, section: String): List<ClassPeriod> {
        val rows = api.get("academics/timetable?class_type=${classType.encoded()}&section=${section.encoded()}") as? JSONArray
            ?: JSONArray()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            ClassPeriod(
                day = row.str("DAY_OF_WEEK", "day_of_week")
                    ?.let { d -> DayOfWeek.entries.firstOrNull { it.name.equals(d, ignoreCase = true) } }
                    ?: return@mapNotNull null,
                periodNo = row.int("PERIOD_NO", "period_no") ?: return@mapNotNull null,
                subject = row.str("SUBJECT", "subject").orEmpty(),
                startTime = row.str("START_TIME", "start_time").orEmpty().take(5),
                endTime = row.str("END_TIME", "end_time").orEmpty().take(5),
                teacherId = row.int("USERID", "user_id") ?: 0,
                teacherName = row.str("teacher_name", "TEACHER_NAME")
            )
        }
    }

    // POST /academics/timetable — MANAGE_TIMETABLE inserts or overwrites the (class, section, day, period) cell.
    override suspend fun savePeriod(classType: String, section: String, period: ClassPeriod) {
        val body = JSONObject()
            .put("class_type", classType)
            .put("section", section)
            .put("day_of_week", period.day.dayName())
            .put("period_no", period.periodNo)
            .put("start_time", period.startTime)
            .put("end_time", period.endTime)
            .put("subject", period.subject)
            .put("user_id", period.teacherId)
        api.post("academics/timetable", body)
    }

    // DELETE /academics/timetable?class_type&section&day_of_week&period_no
    override suspend fun deletePeriod(classType: String, section: String, day: DayOfWeek, periodNo: Int) {
        api.delete(
            "academics/timetable?class_type=${classType.encoded()}&section=${section.encoded()}" +
                "&day_of_week=${day.dayName()}&period_no=$periodNo"
        )
    }

    // GET /auth/teachers → [{id, name}]; the backend allows it for admins only.
    override suspend fun getTeachers(): List<TeacherOption> {
        val rows = api.get("auth/teachers") as? JSONArray ?: JSONArray()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            TeacherOption(
                id = row.int("id", "ID") ?: return@mapNotNull null,
                name = row.str("name", "FullName", "USERNAME").orEmpty()
            )
        }.sortedBy { it.name.lowercase() }
    }

    // DAY_OF_WEEK is stored as "Monday".."Saturday".
    private fun DayOfWeek.dayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
