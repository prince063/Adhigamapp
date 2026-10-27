package com.example.adhigam.data.model

import java.time.DayOfWeek

// Days and periods of the web timetable grid (timetable.html: Monday..Saturday, P1..P8).
val TimetableDays = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
)
const val TIMETABLE_PERIODS = 8

// One cell of a class timetable as staff edit it (GET /academics/timetable row). Times stay as the "HH:mm"
// text the backend stores, because the web lets them be left blank.
data class ClassPeriod(
    val day: DayOfWeek,             // DAY_OF_WEEK
    val periodNo: Int,              // PERIOD_NO
    val subject: String,            // SUBJECT
    val startTime: String,          // START_TIME
    val endTime: String,            // END_TIME
    val teacherId: Int,             // USERID (0 = none)
    val teacherName: String?        // teacher_name
)

// Row of GET /auth/teachers (admins only).
data class TeacherOption(val id: Int, val name: String)
