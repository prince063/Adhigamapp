package com.example.adhigam.data.model

// Who is using the app. Student and Parent log in via /auth/student-login and /auth/parent-login;
// Teacher and Staff both use the staff login (/auth/login), whose role_type is Teacher or
// Superadmin/Admin/Principal (shown here as Staff).
enum class UserRole(val label: String) {
    Student("Student"),
    Parent("Parent"),
    Teacher("Teacher"),
    Staff("Staff");

    val isSchoolStaff: Boolean get() = this == Teacher || this == Staff
}
