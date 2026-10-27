package com.example.adhigam.ui.notices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.Notice
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.repository.NoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoticeForm(
    val title: String = "",
    val body: String = "",
    val audience: String = "All",
    val isPosting: Boolean = false,
    val showErrors: Boolean = false
)

data class NoticeBoardUiState(
    val canManage: Boolean,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val notices: List<Notice> = emptyList(),
    val error: String? = null,
    val audienceFilter: String? = null,
    val query: String = "",
    val formOpen: Boolean = false,
    val form: NoticeForm = NoticeForm(),
    val confirmDelete: Notice? = null,
    val deletingId: Int? = null,
    val message: String? = null
) {
    val visibleNotices: List<Notice>
        get() = notices
            .filter { audienceFilter == null || it.targetRole == audienceFilter }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true) }
}

// Mirrors notice-board.html: everyone reads the notices meant for them; staff (Admin / Principal)
// also post new notices for an audience and delete old ones.
class NoticeBoardViewModel(
    private val repository: NoticeRepository,
    role: UserRole
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoticeBoardUiState(canManage = role == UserRole.Staff))
    val uiState: StateFlow<NoticeBoardUiState> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun setAudienceFilter(audience: String?) = _uiState.update { it.copy(audienceFilter = audience) }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun toggleForm() = _uiState.update { it.copy(formOpen = !it.formOpen) }

    fun updateForm(change: (NoticeForm) -> NoticeForm) = _uiState.update { it.copy(form = change(it.form)) }

    fun post() {
        val form = _uiState.value.form
        if (form.isPosting) return
        if (form.title.isBlank() || form.body.isBlank()) {
            // Same message as the web form.
            _uiState.update { it.copy(form = form.copy(showErrors = true), message = "Title and message are required") }
            return
        }
        updateForm { it.copy(isPosting = true) }
        viewModelScope.launch {
            try {
                repository.postNotice(form.title.trim(), form.body.trim(), form.audience)
                // Reload so the new notice shows with the id, time and author the server gave it.
                val notices = repository.getNotices()
                _uiState.update {
                    it.copy(
                        notices = notices,
                        form = NoticeForm(),
                        formOpen = false,
                        message = "Notice posted"
                    )
                }
            } catch (e: Exception) {
                updateForm { it.copy(isPosting = false) }
                _uiState.update { it.copy(message = e.message ?: "Could not post notice") }
            }
        }
    }

    fun askDelete(notice: Notice) = _uiState.update { it.copy(confirmDelete = notice) }

    fun cancelDelete() = _uiState.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val notice = _uiState.value.confirmDelete ?: return
        _uiState.update { it.copy(confirmDelete = null, deletingId = notice.id) }
        viewModelScope.launch {
            try {
                repository.deleteNotice(notice.id)
                _uiState.update {
                    it.copy(notices = it.notices.filterNot { n -> n.id == notice.id }, deletingId = null, message = "Notice deleted")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingId = null, message = e.message ?: "Could not delete notice") }
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.notices.isEmpty(), isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val notices = repository.getNotices()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, notices = notices) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Could not load notices") }
            }
        }
    }
}
