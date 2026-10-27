package com.example.adhigam.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.StudentProfile
import com.example.adhigam.data.repository.StudentProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val profile: StudentProfile? = null,
    val error: String? = null
)

class ProfileViewModel(private val repository: StudentProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.profile == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val profile = repository.getProfile()
                _uiState.update { ProfileUiState(isLoading = false, profile = profile) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}
