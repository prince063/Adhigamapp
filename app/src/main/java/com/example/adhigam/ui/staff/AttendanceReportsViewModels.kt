package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.AttendanceSummaryRow
import com.example.adhigam.data.model.HeatmapStudent
import com.example.adhigam.data.model.StaffAttendanceEntry
import com.example.adhigam.data.repository.StaffRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// ---- Attendance Summary (attendance-summary.html) ------------------------------------------------

data class AttendanceSummaryUiState(
    val classType: String? = null,
    val section: String? = null,
    val month: YearMonth = YearMonth.now(),
    val isLoading: Boolean = false,
    val rows: List<AttendanceSummaryRow> = emptyList(),
    val loaded: Boolean = false,
    val query: String = "",
    val error: String? = null
) {
    val visibleRows: List<AttendanceSummaryRow>
        get() = if (query.isBlank()) rows
        else rows.filter { it.studentName.contains(query, ignoreCase = true) || it.admissionNo.contains(query) }
    // Average over students who have at least one marked day.
    val classAverage: Double?
        get() = rows.filter { it.totalDays > 0 }.takeIf { it.isNotEmpty() }?.map { it.percent }?.average()
}

class AttendanceSummaryViewModel(private val repository: StaffRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AttendanceSummaryUiState())
    val uiState: StateFlow<AttendanceSummaryUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun selectClass(classType: String) { _uiState.update { it.copy(classType = classType) }; load() }
    fun selectSection(section: String) { _uiState.update { it.copy(section = section) }; load() }
    fun selectMonth(month: YearMonth) { _uiState.update { it.copy(month = month) }; load() }
    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun load() {
        val state = _uiState.value
        val classType = state.classType ?: return
        val section = state.section ?: return
        job?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            try {
                // The backend groups by the month of this date.
                val rows = repository.getAttendanceSummary(classType, section, state.month.atDay(1))
                _uiState.update { it.copy(isLoading = false, rows = rows, loaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load summary") }
            }
        }
    }
}

// ---- Attendance Heatmap (attendance-heatmap.html) -------------------------------------------------

data class HeatmapUiState(
    val classType: String? = null,
    val section: String = "A",
    val month: YearMonth = YearMonth.now(),
    val isLoading: Boolean = false,
    val students: List<HeatmapStudent> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null
) {
    private fun count(status: Char) = students.sumOf { s -> s.days.values.count { it == status } }
    val presentDays: Int get() = count('P')
    val absentDays: Int get() = count('A')
    val leaveDays: Int get() = count('L')
}

class AttendanceHeatmapViewModel(private val repository: StaffRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HeatmapUiState())
    val uiState: StateFlow<HeatmapUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun selectClass(classType: String) { _uiState.update { it.copy(classType = classType) }; load() }
    fun selectSection(section: String) { _uiState.update { it.copy(section = section) }; load() }
    fun selectMonth(month: YearMonth) { _uiState.update { it.copy(month = month) }; load() }

    fun load() {
        val state = _uiState.value
        val classType = state.classType ?: return
        job?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val students = repository.getAttendanceHeatmap(classType, state.section, state.month.monthValue, state.month.year)
                _uiState.update { it.copy(isLoading = false, students = students, loaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load heatmap") }
            }
        }
    }
}

// ---- Staff Attendance (staff-attendance.html) -----------------------------------------------------

data class StaffAttendanceUiState(
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val entries: List<StaffAttendanceEntry> = emptyList(),
    val original: List<StaffAttendanceEntry> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null
) {
    val hasChanges: Boolean get() = entries != original
    fun count(status: String) = entries.count { it.status == status }
}

class StaffAttendanceViewModel(private val repository: StaffRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StaffAttendanceUiState())
    val uiState: StateFlow<StaffAttendanceUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun selectDate(date: LocalDate) { _uiState.update { it.copy(date = date) }; load() }

    fun update(userId: Int, change: (StaffAttendanceEntry) -> StaffAttendanceEntry) = _uiState.update { state ->
        state.copy(entries = state.entries.map { if (it.userId == userId) change(it) else it })
    }

    fun markAll(status: String) = _uiState.update { state -> state.copy(entries = state.entries.map { it.copy(status = status) }) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun load() {
        val date = _uiState.value.date
        job?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val entries = repository.getStaffAttendance(date)
                _uiState.update { it.copy(isLoading = false, entries = entries, original = entries) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load staff attendance") }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.entries.isEmpty()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.saveStaffAttendance(state.date, state.entries)
                // Same message as the web page.
                _uiState.update { it.copy(isSaving = false, original = it.entries, message = "Attendance saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = e.message ?: "Could not save attendance") }
            }
        }
    }
}
