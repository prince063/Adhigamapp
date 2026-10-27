package com.example.adhigam.data.model

import java.time.LocalDate

// Row of GET /hr/leave-requests (GET_LEAVE_REQUESTS). Teachers only get their own requests, admins everyone's.
data class LeaveRequest(
    val id: Int,                    // ID
    val staffName: String,          // staff_name
    val designation: String,        // designation (USERS.ROLETYPE)
    val leaveType: String,          // LEAVE_TYPE
    val fromDate: LocalDate,        // FROM_DATE
    val toDate: LocalDate,          // TO_DATE
    val days: Int,                  // days
    val reason: String,             // REASON
    val status: LeaveStatus,        // STATUS
    val approvedBy: String?,        // approved_by_name
    val remarks: String?            // REMARKS (from the approver)
)

enum class LeaveStatus(val value: String) {
    Pending("Pending"), Approved("Approved"), Rejected("Rejected");

    companion object {
        fun from(value: String?): LeaveStatus = entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: Pending
    }
}

// Leave types of the web "Apply for Leave" modal.
val LeaveTypes = listOf("Casual", "Medical", "Privilege", "Maternity", "Other")
