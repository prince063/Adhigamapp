package com.example.adhigam.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.HomeData
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val data: HomeData? = null,
    val error: String? = null
)

class HomeViewModel(
    private val repository: StudentRepository,
    private val role: UserRole
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.data == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val data = repository.getHomeData(role)
                _uiState.update { HomeUiState(isLoading = false, data = data) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}
