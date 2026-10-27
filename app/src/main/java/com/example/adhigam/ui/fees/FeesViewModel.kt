package com.example.adhigam.ui.fees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.FeeMonth
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.PaymentMode
import com.example.adhigam.data.repository.StudentRepository
import com.example.adhigam.ui.util.formatRupees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val fees: FeeSummary? = null,
    val selectedMonth: String? = null,
    val error: String? = null,
    // Mock payment dialog (web "Secure Fee Portal Mock Payment Gateway")
    val payingMonth: FeeMonth? = null,
    val payMode: PaymentMode = PaymentMode.UPI,
    val isPaying: Boolean = false,
    val payError: String? = null
)

// Online methods offered by the web checkout modal.
val OnlinePaymentModes = listOf(PaymentMode.UPI, PaymentMode.Card, PaymentMode.NetBanking)

class FeesViewModel(private val repository: StudentRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FeesUiState())
    val uiState: StateFlow<FeesUiState> = _uiState.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    // Only one unpaid month can be picked for payment at a time; tapping it again clears the pick.
    fun toggleMonth(month: String) {
        _uiState.update { it.copy(selectedMonth = if (it.selectedMonth == month) null else month) }
    }

    fun openPayment() {
        val month = _uiState.value.run { fees?.months?.firstOrNull { it.month == selectedMonth } } ?: return
        _uiState.update { it.copy(payingMonth = month, payMode = PaymentMode.UPI, payError = null) }
    }

    fun setPayMode(mode: PaymentMode) = _uiState.update { it.copy(payMode = mode) }

    fun dismissPayment() {
        if (!_uiState.value.isPaying) _uiState.update { it.copy(payingMonth = null, payError = null) }
    }

    // onPaid gets the confirmation text; the ledger is reloaded so the month shows as paid and the invoice appears.
    fun confirmPayment(onPaid: (String) -> Unit) {
        val state = _uiState.value
        val month = state.payingMonth ?: return
        if (state.isPaying) return
        _uiState.update { it.copy(isPaying = true, payError = null) }
        viewModelScope.launch {
            try {
                repository.payFee(month.month, state.payMode)
                _uiState.update { it.copy(isPaying = false, payingMonth = null, selectedMonth = null) }
                onPaid("Payment of ${formatRupees(month.balance)} for ${month.month} confirmed!")
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(isPaying = false, payError = e.message ?: "Payment simulation failed") }
            }
        }
    }

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.fees == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val fees = repository.getFeeDetails()
                _uiState.update { FeesUiState(isLoading = false, fees = fees) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}
