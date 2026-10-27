package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.AttendanceEntry
import com.example.adhigam.data.repository.StaffRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MarkAttendanceUiState(
    val classType: String? = null,
    val section: String? = null,
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val students: List<AttendanceEntry> = emptyList(),
    // Status as loaded, to know whether anything changed since the last load/save.
    val original: Map<String, Boolean> = emptyMap(),
    val query: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val isReady: Boolean get() = classType != null && section != null
    val presentCount: Int get() = students.count { it.isPresent }
    val absentCount: Int get() = students.size - presentCount
    val alreadyMarked: Boolean get() = students.any { it.attendanceId != 0 }
    val hasChanges: Boolean get() = students.any { original[it.admissionNo] != it.isPresent }
    val visibleStudents: List<AttendanceEntry>
        get() = if (query.isBlank()) students
        else students.filter { it.studentName.contains(query, ignoreCase = true) || it.admissionNo.contains(query) }
}

// Mirrors student-attendance.html: pick class, section and date to load the roster,
// mark each student present/absent (or all at once) and save.
class MarkAttendanceViewModel(private val repository: StaffRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MarkAttendanceUiState())
    val uiState: StateFlow<MarkAttendanceUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun selectClass(classType: String) {
        _uiState.update { it.copy(classType = classType) }
        load()
    }

    fun selectSection(section: String) {
        _uiState.update { it.copy(section = section) }
        load()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
        load()
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setPresent(admissionNo: String, present: Boolean) = _uiState.update { state ->
        state.copy(students = state.students.map { if (it.admissionNo == admissionNo) it.copy(isPresent = present) else it })
    }

    fun markAll(present: Boolean) = _uiState.update { state ->
        state.copy(students = state.students.map { it.copy(isPresent = present) })
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun retry() = load()

    fun save() {
        val state = _uiState.value
        if (state.students.isEmpty() || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val saved = repository.saveClassAttendance(state.date, state.students)
                _uiState.update { it.copy(isSaving = false, message = "Attendance saved for $saved students") }
                // Reload so every row carries the record id the server created; saving again then updates
                // those records instead of adding duplicates.
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = e.message ?: "Could not save attendance") }
            }
        }
    }

    private fun load() {
        val state = _uiState.value
        val classType = state.classType ?: return
        val section = state.section ?: return
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, query = "") }
        loadJob = viewModelScope.launch {
            try {
                val students = repository.getClassAttendance(classType, section, state.date)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        students = students,
                        original = students.associate { s -> s.admissionNo to s.isPresent }
                    )
                }
            } catch (e: CancellationException) {
                // A newer class/section/date selection replaced this load.
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, students = emptyList(), error = e.message ?: "Could not load students") }
            }
        }
    }
}
