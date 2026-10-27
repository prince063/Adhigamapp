package com.example.adhigam.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.LibraryBook
import com.example.adhigam.data.model.LibraryCategory
import com.example.adhigam.data.model.MyLibrary
import com.example.adhigam.data.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) { MyBooks("My Books"), Reservations("Reservations"), Catalog("Catalog") }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.MyBooks,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val library: MyLibrary? = null,
    val categories: List<LibraryCategory> = emptyList(),
    val categoryId: String? = null,
    val search: String = "",
    val books: List<LibraryBook> = emptyList(),
    val isSearching: Boolean = false,
    val busyIds: Set<String> = emptySet(),   // books being reserved / reservations being cancelled
    val message: String? = null
) {
    // Books the student already holds or has an active reservation for can't be reserved again.
    fun reservedBookIds(): Set<String> = library?.reservations.orEmpty().filter { it.isActive }.map { it.bookId }.toSet()
}

// The parent portal's "Library" facilities (checked-out books + reservations) plus the catalog to reserve from.
class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun selectTab(tab: LibraryTab) = _uiState.update { it.copy(tab = tab) }

    fun setSearch(text: String) {
        _uiState.update { it.copy(search = text) }
        searchBooks(debounce = true)
    }

    fun setCategory(categoryId: String?) {
        _uiState.update { it.copy(categoryId = categoryId) }
        searchBooks(debounce = false)
    }

    fun reserve(book: LibraryBook) {
        if (book.id in _uiState.value.busyIds) return
        busy(book.id, true)
        viewModelScope.launch {
            try {
                repository.reserve(book)
                val library = repository.getMyLibrary()
                _uiState.update { it.copy(library = library, message = "Reservation placed for \"${book.title}\"") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Could not reserve this book") }
            } finally {
                busy(book.id, false)
            }
        }
    }

    fun cancelReservation(reservationId: String) {
        if (reservationId in _uiState.value.busyIds) return
        val reservation = _uiState.value.library?.reservations?.firstOrNull { it.id == reservationId } ?: return
        busy(reservationId, true)
        viewModelScope.launch {
            try {
                repository.cancelReservation(reservation)
                val library = repository.getMyLibrary()
                _uiState.update { it.copy(library = library, message = "Reservation cancelled") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Could not cancel the reservation") }
            } finally {
                busy(reservationId, false)
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun busy(id: String, isBusy: Boolean) =
        _uiState.update { it.copy(busyIds = if (isBusy) it.busyIds + id else it.busyIds - id) }

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.library == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val library = repository.getMyLibrary()
                val categories = repository.getCategories()
                val state = _uiState.value
                val books = repository.getBooks(state.search, state.categoryId)
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, library = library, categories = categories, books = books)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Could not load library") }
            }
        }
    }

    private fun searchBooks(debounce: Boolean) {
        searchJob?.cancel()
        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            val state = _uiState.value
            try {
                val books = repository.getBooks(state.search, state.categoryId)
                _uiState.update { it.copy(isSearching = false, books = books) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, message = e.message ?: "Search failed") }
            }
        }
    }
}
