package com.example.adhigam.data.repository

import com.example.adhigam.data.model.LibraryBook
import com.example.adhigam.data.model.LibraryCategory
import com.example.adhigam.data.model.LibraryCheckout
import com.example.adhigam.data.model.LibraryReservation
import com.example.adhigam.data.model.MyLibrary
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.date
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.session.Session
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

// Library for the logged-in student (or a parent's child): /library/checkouts/self, /library/books,
// /library/categories and POST /library/reservations.
interface LibraryRepository {
    suspend fun getMyLibrary(): MyLibrary
    suspend fun getCategories(): List<LibraryCategory>
    // categoryId null = every category; search matches title, author or ISBN.
    suspend fun getBooks(search: String, categoryId: String?): List<LibraryBook>
    suspend fun reserve(book: LibraryBook)
    suspend fun cancelReservation(reservation: LibraryReservation)
}

// Library rows carry both the plain ID and an ENCRYPTED_ID; the backend accepts either.
class ApiLibraryRepository(
    private val api: ApiClient,
    private val session: () -> Session?
) : LibraryRepository {

    // GET /library/checkouts/self → {"checkouts": [...], "reservations": [...]}
    override suspend fun getMyLibrary(): MyLibrary {
        val json = api.get("library/checkouts/self") as? JSONObject ?: return MyLibrary(emptyList(), emptyList())
        val checkouts = json.optJSONArray("checkouts").objects().mapNotNull { row ->
            LibraryCheckout(
                id = row.str("ID", "id") ?: return@mapNotNull null,
                accessionNo = row.str("ACCESSION_NO", "COPY_BARCODE").orEmpty(),
                bookTitle = row.str("BOOK_TITLE").orEmpty(),
                issueDate = row.date("ISSUE_DATE") ?: return@mapNotNull null,
                dueDate = row.date("DUE_DATE") ?: return@mapNotNull null,
                returnDate = row.date("RETURN_DATE"),
                fineAmount = row.str("FINE_AMOUNT")?.toDoubleOrNull()?.let { Math.round(it) } ?: 0L,
                status = row.str("STATUS").orEmpty().lowercase()
            )
        }
        val reservations = json.optJSONArray("reservations").objects().mapNotNull { it.toReservation() }
        return MyLibrary(checkouts, reservations.sortedByDescending { it.requestDate })
    }

    // GET /library/categories
    override suspend fun getCategories(): List<LibraryCategory> =
        (api.get("library/categories") as? JSONArray).objects().mapNotNull { row ->
            LibraryCategory(row.str("ID", "id") ?: return@mapNotNull null, row.str("NAME", "name") ?: return@mapNotNull null)
        }

    // GET /library/books?search&category_id
    override suspend fun getBooks(search: String, categoryId: String?): List<LibraryBook> {
        val params = listOfNotNull(
            "search=${search.trim().encoded()}",
            categoryId?.let { "category_id=${it.encoded()}" }
        ).joinToString("&")
        return (api.get("library/books?$params") as? JSONArray).objects().mapNotNull { row ->
            LibraryBook(
                id = row.str("ID", "id") ?: return@mapNotNull null,
                isbn = row.str("ISBN"),
                title = row.str("TITLE").orEmpty(),
                author = row.str("AUTHOR"),
                publisher = row.str("PUBLISHER"),
                categoryId = row.str("CATEGORY_ID"),
                categoryName = row.str("CATEGORY_NAME"),
                location = row.str("LOCATION"),
                totalCopies = row.int("TOTAL_COPIES") ?: 0,
                availableCopies = row.int("AVAILABLE_COPIES") ?: 0
            )
        }
    }

    // POST /library/reservations {book_id, user_id, status: "pending"}
    override suspend fun reserve(book: LibraryBook) {
        api.post("library/reservations", reservationBody(reservationId = null, bookId = book.id, status = "pending"))
    }

    // Cancelling is the same call with the reservation's id and status "cancelled".
    override suspend fun cancelReservation(reservation: LibraryReservation) {
        api.post("library/reservations", reservationBody(reservation.id, reservation.bookId, "cancelled"))
    }

    // user_id must be the logged-in student's id (the backend checks it against the token).
    private fun reservationBody(reservationId: String?, bookId: String, status: String): JSONObject {
        val userId = session()?.userId ?: throw IllegalStateException("Please log in again")
        return JSONObject()
            .put("encrypted_id", reservationId ?: JSONObject.NULL)
            .put("book_id", bookId)
            .put("user_id", userId.toString())
            .put("status", status)
    }

    private fun JSONObject.toReservation(): LibraryReservation? {
        val id = str("ID", "id") ?: return null
        return LibraryReservation(
            id = id,
            bookId = str("BOOK_ID").orEmpty(),
            bookTitle = str("BOOK_TITLE").orEmpty(),
            requestDate = date("REQUEST_DATE") ?: LocalDate.now(),
            status = str("STATUS").orEmpty().lowercase(),
            readyDate = date("READY_DATE")
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
