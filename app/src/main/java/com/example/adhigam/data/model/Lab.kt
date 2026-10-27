package com.example.adhigam.data.model

import java.time.LocalDateTime

enum class LabSessionState(val label: String) {
    InSession("In session"),
    Scheduled("Scheduled"),
    ReturnPending("Return pending"),
    Returned("Returned")
}

// One row of GET /lab/reservations/self (LAB_SESSION_ALLOCATIONS joined with session, unit, equipment,
// experiment and lab).
data class LabAllocation(
    val id: String,                  // ID
    val experimentName: String,      // EXPERIMENT_NAME
    val labName: String,             // LAB_NAME
    val equipmentName: String,       // EQUIPMENT_NAME
    val internalUid: String,         // INTERNAL_UID (unit tag)
    val startTime: LocalDateTime,    // START_TIME
    val endTime: LocalDateTime,      // END_TIME
    val issuedAt: LocalDateTime?,    // ISSUED_AT
    val returnedAt: LocalDateTime?,  // RETURNED_AT
    val conditionPre: String?,       // CONDITION_PRE
    val conditionPost: String?       // CONDITION_POST
) {
    fun state(now: LocalDateTime = LocalDateTime.now()): LabSessionState = when {
        returnedAt != null -> LabSessionState.Returned
        now.isBefore(startTime) -> LabSessionState.Scheduled
        now.isBefore(endTime) -> LabSessionState.InSession
        else -> LabSessionState.ReturnPending
    }
}
