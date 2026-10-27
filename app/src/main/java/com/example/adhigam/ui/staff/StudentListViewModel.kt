package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.repository.StudentListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentListUiState(
    // Like the web page: class, section and search go to the backend; status filters the loaded rows,
    // so the four counts always cover every status. Status starts on Approved.
    val status: StudentStatus? = StudentStatus.Approved,
    val classType: String? = null,
    val section: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    // Any request in flight, including filter changes while a list is already showing.
    val isFetching: Boolean = false,
    val students: List<StudentListItem> = emptyList(),
    val error: String? = null,
    val updatingIds: Set<String> = emptySet(),
    val resetTarget: StudentListItem? = null,
    val newPassword: String = "",
    val isResetting: Boolean = false,
    val message: String? = null
) {
    fun count(status: StudentStatus?): Int =
        if (status == null) students.size else students.count { it.status == status }
    val visibleStudents: List<StudentListItem>
        get() = if (status == null) students else students.filter { it.status == status }
}

// Mirrors student-list.html: status cards, search + filters, and per-student actions
// (approve / reject pending admissions, reset password, links to profile, fees and marks).
class StudentListViewModel(private val repository: StudentListRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentListUiState())
    val uiState: StateFlow<StudentListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(refresh = false, debounce = false)
    }

    fun refresh() = load(refresh = true, debounce = false)

    // Reload without the pull-to-refresh spinner, e.g. when coming back from an edit.
    fun reload() = load(refresh = false, debounce = false)

    fun setStatus(status: StudentStatus?) = _uiState.update { it.copy(status = status) }

    fun setClass(classType: String?) {
        _uiState.update { it.copy(classType = classType) }
        load(refresh = false, debounce = false)
    }

    fun setSection(section: String?) {
        _uiState.update { it.copy(section = section) }
        load(refresh = false, debounce = false)
    }

    // Debounced like the web search box (300 ms).
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        load(refresh = false, debounce = true)
    }

    fun updateStatus(student: StudentListItem, status: StudentStatus) {
        if (student.encryptedId in _uiState.value.updatingIds) return
        _uiState.update { it.copy(updatingIds = it.updatingIds + student.encryptedId) }
        viewModelScope.launch {
            try {
                repository.updateStudentStatus(student.encryptedId, status)
                _uiState.update { state ->
                    state.copy(
                        students = state.students.map { if (it.encryptedId == student.encryptedId) it.copy(status = status) else it },
                        message = "Status updated to ${status.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Could not update status") }
            } finally {
                _uiState.update { it.copy(updatingIds = it.updatingIds - student.encryptedId) }
            }
        }
    }

    fun openResetPassword(student: StudentListItem) =
        _uiState.update { it.copy(resetTarget = student, newPassword = "") }

    fun dismissResetPassword() = _uiState.update { if (it.isResetting) it else it.copy(resetTarget = null, newPassword = "") }

    fun setNewPassword(password: String) = _uiState.update { it.copy(newPassword = password) }

    // Same character set and length as the web "Generate" button.
    fun generatePassword() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$&*!"
        _uiState.update { it.copy(newPassword = (1..12).map { chars.random() }.joinToString("")) }
    }

    fun submitResetPassword() {
        val state = _uiState.value
        val target = state.resetTarget ?: return
        if (state.newPassword.isBlank()) {
            _uiState.update { it.copy(message = "Generate a password first") }
            return
        }
        _uiState.update { it.copy(isResetting = true) }
        viewModelScope.launch {
            try {
                repository.resetStudentPassword(target.admissionNo, state.newPassword)
                _uiState.update {
                    it.copy(isResetting = false, resetTarget = null, newPassword = "", message = "Password reset successfully")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isResetting = false, message = e.message ?: "Could not reset password") }
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun load(refresh: Boolean, debounce: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(300)
            _uiState.update {
                it.copy(isLoading = !refresh && it.students.isEmpty(), isRefreshing = refresh, isFetching = true, error = null)
            }
            val state = _uiState.value
            try {
                val students = repository.getStudentList(state.classType, state.section, state.query)
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, isFetching = false, students = students) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, isFetching = false, error = e.message ?: "Could not load students")
                }
            }
        }
    }
}
