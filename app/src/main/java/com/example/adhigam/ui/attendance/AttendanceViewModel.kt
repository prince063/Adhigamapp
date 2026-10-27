package com.example.adhigam.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.AttendanceSummary
import com.example.adhigam.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class AttendanceUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val summary: AttendanceSummary? = null,
    val month: YearMonth = YearMonth.now(),
    val error: String? = null
) {
    // Months can be browsed from the first logged day up to the current month.
    val canGoBack: Boolean get() = summary?.firstMonth?.let { month.isAfter(it) } ?: false
    val canGoForward: Boolean get() = month.isBefore(YearMonth.now())
}

class AttendanceViewModel(private val repository: StudentRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun previousMonth() = _uiState.update { if (it.canGoBack) it.copy(month = it.month.minusMonths(1)) else it }

    fun nextMonth() = _uiState.update { if (it.canGoForward) it.copy(month = it.month.plusMonths(1)) else it }

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.summary == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val summary = repository.getAttendance()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, summary = summary) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}
