package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.FeeHeadKey
import com.example.adhigam.data.model.FeeInstallment
import com.example.adhigam.data.model.FeePaymentRequest
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.PaymentMode
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.repository.FeeCollectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The "Collect Fee" sheet. month == null is the web's Bulk Pay ("Multiple Months").
data class PayFormState(
    val month: String?,
    val heads: List<FeeInstallment>,
    val selected: Set<FeeHeadKey>,
    val amount: String,
    val mode: PaymentMode? = null,
    val transactionId: String = "",
    val bankName: String = "",
    val netbankingType: String = "",
    val chequeNo: String = "",
    val accountNo: String = "",
    val upiId: String = "",
    val cardType: String = "",
    val payeeName: String = "",
    val remarks: String = "",
    val isSubmitting: Boolean = false
) {
    val selectedBalance: Long
        get() = heads.filter { FeeHeadKey(it.month, it.feeName) in selected }.sumOf { it.balance }
}

data class FeeCollectionUiState(
    val classType: String? = null,
    val section: String? = null,
    val query: String = "",
    val isSearching: Boolean = false,
    val searched: Boolean = false,
    val results: List<StudentListItem> = emptyList(),
    val student: StudentListItem? = null,
    val fees: FeeSummary? = null,
    val isLoadingFees: Boolean = false,
    val feesError: String? = null,
    val expandedMonths: Set<String> = emptySet(),
    val pay: PayFormState? = null,
    val message: String? = null
) {
    // Web rule: search once 2+ characters are typed or a class/section filter is chosen.
    val canSearch: Boolean get() = query.trim().length >= 2 || classType != null || section != null
    val searchHint: String
        get() = when {
            !canSearch -> "Type at least 2 characters or select filters to search"
            isSearching -> "Searching…"
            searched && results.isEmpty() -> "No students found."
            searched -> "${results.size} result(s) found — tap to select"
            else -> ""
        }
}

// Mirrors fee-collection.html: find a student, see their fee summary, outstanding dues and past
// collections, and record full, partial or bulk payments with the payment-mode details.
// initialStudentId opens the screen with that student already selected (Fees from a student's profile).
class FeeCollectionViewModel(
    private val repository: FeeCollectionRepository,
    initialStudentId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeeCollectionUiState(isLoadingFees = initialStudentId != null))
    val uiState: StateFlow<FeeCollectionUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        if (initialStudentId != null) {
            viewModelScope.launch {
                try {
                    selectStudent(repository.getStudent(initialStudentId))
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoadingFees = false, message = e.message ?: "Could not load student") }
                }
            }
        }
    }

    fun setClass(classType: String?) {
        _uiState.update { it.copy(classType = classType) }
        search(debounce = false)
    }

    fun setSection(section: String?) {
        _uiState.update { it.copy(section = section) }
        search(debounce = false)
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        search(debounce = true)
    }

    fun selectStudent(student: StudentListItem) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(student = student, results = emptyList(), searched = false, isSearching = false, fees = null, expandedMonths = emptySet())
        }
        loadFees()
    }

    fun changeStudent() = _uiState.update { it.copy(student = null, fees = null, feesError = null, pay = null) }

    fun loadFees() {
        val student = _uiState.value.student ?: return
        _uiState.update { it.copy(isLoadingFees = true, feesError = null) }
        viewModelScope.launch {
            try {
                val fees = repository.getFeeLedger(student.encryptedId)
                _uiState.update { it.copy(isLoadingFees = false, fees = fees) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingFees = false, feesError = e.message ?: "Could not load fees") }
            }
        }
    }

    fun toggleMonth(month: String) = _uiState.update {
        it.copy(expandedMonths = if (month in it.expandedMonths) it.expandedMonths - month else it.expandedMonths + month)
    }

    // Opens the pay sheet for one month, or for every unpaid head when month is null (Bulk Pay).
    fun openPay(month: String?) {
        val fees = _uiState.value.fees ?: return
        val heads = fees.installments.filter { it.balance > 0 && (month == null || it.month == month) }
        if (heads.isEmpty()) {
            _uiState.update { it.copy(message = "No outstanding fee heads found.") }
            return
        }
        val selected = heads.map { FeeHeadKey(it.month, it.feeName) }.toSet()
        _uiState.update {
            it.copy(pay = PayFormState(month = month, heads = heads, selected = selected, amount = heads.sumOf { h -> h.balance }.toString()))
        }
    }

    fun dismissPay() = _uiState.update { if (it.pay?.isSubmitting == true) it else it.copy(pay = null) }

    // Ticking a head re-totals the amount, like the web checklist.
    fun toggleHead(key: FeeHeadKey) = updatePay { pay ->
        val selected = if (key in pay.selected) pay.selected - key else pay.selected + key
        val updated = pay.copy(selected = selected)
        updated.copy(amount = updated.selectedBalance.toString())
    }

    fun updatePayForm(change: (PayFormState) -> PayFormState) = updatePay(change)

    fun setAmount(text: String) = updatePay { it.copy(amount = text.filter(Char::isDigit).take(7)) }

    fun submitPayment() {
        val state = _uiState.value
        val student = state.student ?: return
        val pay = state.pay ?: return
        if (pay.isSubmitting) return
        val amount = pay.amount.toLongOrNull()
        // Same checks and messages as the web modal.
        when {
            pay.mode == null -> return message("Please select a payment mode")
            amount == null || amount <= 0 -> return message("Enter a valid amount")
            amount > (state.fees?.balance ?: 0) -> return message("Amount is more than the outstanding balance")
        }
        val request = FeePaymentRequest(
            amount = amount!!,
            mode = pay.mode!!,
            // Row ids of the ticked heads, so the backend applies the money to exactly those rows.
            feeIds = pay.heads.filter { FeeHeadKey(it.month, it.feeName) in pay.selected }.mapNotNull { it.id },
            transactionId = pay.transactionId.ifBlank { null },
            bankName = pay.bankName.ifBlank { null },
            netbankingType = pay.netbankingType.ifBlank { null },
            chequeNo = pay.chequeNo.ifBlank { null },
            accountNo = pay.accountNo.ifBlank { null },
            upiId = pay.upiId.ifBlank { null },
            cardType = pay.cardType.ifBlank { null },
            payeeName = pay.payeeName.ifBlank { null },
            remarks = pay.remarks.ifBlank { null }
        )
        updatePay { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                val payment = repository.recordPayment(student.encryptedId, request)
                _uiState.update { it.copy(pay = null, message = "Payment recorded! Invoice #${payment.invoiceNo}") }
                loadFees()
            } catch (e: Exception) {
                updatePay { it.copy(isSubmitting = false) }
                message(e.message ?: "Could not record payment")
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun message(text: String) = _uiState.update { it.copy(message = text) }

    private fun updatePay(change: (PayFormState) -> PayFormState) = _uiState.update { state ->
        state.pay?.let { state.copy(pay = change(it)) } ?: state
    }

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        if (!_uiState.value.canSearch) {
            _uiState.update { it.copy(results = emptyList(), searched = false, isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(320)
            val state = _uiState.value
            try {
                val results = repository.searchStudents(state.query, state.classType, state.section)
                _uiState.update { it.copy(isSearching = false, searched = true, results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, searched = true, results = emptyList(), message = "Search error: ${e.message}") }
            }
        }
    }
}
