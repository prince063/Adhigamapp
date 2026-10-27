package com.example.adhigam.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Fine charged per overdue day (LIB circulation rules, fine_per_day).
const val LIBRARY_FINE_PER_DAY = 5L

// One row of GET_LIB_CIRCULATION_HISTORY (checkouts.self → checkouts).
data class LibraryCheckout(
    val id: String,              // ID (encrypted)
    val accessionNo: String,     // ACCESSION_NO
    val bookTitle: String,       // BOOK_TITLE
    val issueDate: LocalDate,    // ISSUE_DATE
    val dueDate: LocalDate,      // DUE_DATE
    val returnDate: LocalDate?,  // RETURN_DATE
    val fineAmount: Long,        // FINE_AMOUNT (charged at return)
    val status: String           // STATUS: issued | returned
) {
    val isReturned: Boolean get() = status == "returned" || returnDate != null
    fun daysOverdue(today: LocalDate = LocalDate.now()): Long =
        if (isReturned) 0 else ChronoUnit.DAYS.between(dueDate, today).coerceAtLeast(0)
    // Fine so far: the recorded fine once returned, otherwise what has accrued.
    fun currentFine(today: LocalDate = LocalDate.now()): Long =
        if (isReturned) fineAmount else daysOverdue(today) * LIBRARY_FINE_PER_DAY
}

// One row of GET_LIB_RESERVATIONS. Status: pending | notified | fulfilled | cancelled.
data class LibraryReservation(
    val id: String,              // ID (encrypted)
    val bookId: String,          // BOOK_ID (encrypted)
    val bookTitle: String,       // BOOK_TITLE
    val requestDate: LocalDate,  // REQUEST_DATE
    val status: String,          // STATUS
    val readyDate: LocalDate?    // READY_DATE
) {
    val isActive: Boolean get() = status == "pending" || status == "notified"
}

// One row of GET_LIB_BOOKS.
data class LibraryBook(
    val id: String,              // ID (encrypted)
    val isbn: String?,           // ISBN
    val title: String,           // TITLE
    val author: String?,         // AUTHOR
    val publisher: String?,      // PUBLISHER
    val categoryId: String?,     // CATEGORY_ID (encrypted)
    val categoryName: String?,   // CATEGORY_NAME
    val location: String?,       // LOCATION (shelf)
    val totalCopies: Int,        // TOTAL_COPIES
    val availableCopies: Int     // AVAILABLE_COPIES
)

// One row of GET_LIB_CATEGORIES.
data class LibraryCategory(val id: String, val name: String)

// GET /library/checkouts/self
data class MyLibrary(
    val checkouts: List<LibraryCheckout>,
    val reservations: List<LibraryReservation>
) {
    val current: List<LibraryCheckout> get() = checkouts.filter { !it.isReturned }
    val history: List<LibraryCheckout> get() = checkouts.filter { it.isReturned }
}
