package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.AdminHomeData
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentMarkRow
import com.example.adhigam.data.model.StaffProfile
import com.example.adhigam.data.model.TeacherHomeData
import com.example.adhigam.data.repository.AdminDashboardRepository
import com.example.adhigam.data.repository.StaffRepository
import com.example.adhigam.data.repository.StudentMarksRepository
import com.example.adhigam.data.repository.StudentRecordRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadUiState<T>(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val data: T? = null,
    val error: String? = null
)

// Load-once + pull-to-refresh, shared by the read-only staff screens.
abstract class LoadViewModel<T>(private val loader: suspend () -> T) : ViewModel() {

    private val _uiState = MutableStateFlow(LoadUiState<T>())
    val uiState: StateFlow<LoadUiState<T>> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    // Quietly reloads data that is already showing, e.g. when returning from an edit screen.
    fun reloadIfLoaded() {
        if (_uiState.value.data != null && !_uiState.value.isRefreshing) load(refresh = false)
    }

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.data == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val data = loader()
                _uiState.update { LoadUiState(isLoading = false, data = data) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}

class TeacherHomeViewModel(repository: StaffRepository) :
    LoadViewModel<TeacherHomeData>({ repository.getTeacherHome() })

class AdminHomeViewModel(repository: AdminDashboardRepository) :
    LoadViewModel<AdminHomeData>({ repository.getAdminHome() })

class StaffProfileViewModel(repository: StaffRepository) :
    LoadViewModel<StaffProfile>({ repository.getStaffProfile() })

data class StudentDetailData(val detail: StaffStudentDetail, val fees: FeeSummary)

class StudentDetailViewModel(repository: StudentRecordRepository, encryptedId: String) :
    LoadViewModel<StudentDetailData>({
        coroutineScope {
            val detail = async { repository.getStudentDetail(encryptedId) }
            val fees = async { repository.getFeeLedger(encryptedId) }
            StudentDetailData(detail.await(), fees.await())
        }
    })

data class StudentMarksData(val student: StudentListItem, val rows: List<StudentMarkRow>)

class StudentMarksViewModel(repository: StudentMarksRepository, encryptedId: String) :
    LoadViewModel<StudentMarksData>({
        val (student, rows) = repository.getStudentMarks(encryptedId)
        StudentMarksData(student, rows)
    })
