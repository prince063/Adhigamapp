package com.example.adhigam.data.model

import java.time.LocalDate

// One line of a fee receipt: which fee head and month the payment (or part of it) went to.
data class ReceiptItem(
    val feeName: String,   // FEE_NAME
    val feeGroup: String,  // FEE_GROUP_NAME
    val month: String,     // FEE_MONTH
    val amount: Long       // amount allocated from this payment
)

// GET /student/payment-receipt/{encId}?invoice_no&session, plus the school name/address from
// /settings/school-config (payment-receipt.html).
data class FeeReceipt(
    val schoolName: String,
    val schoolAddress: String,
    val studentName: String,      // STUDENTNAME
    val admissionNo: String,      // ADMISSIONNO
    val classType: String,        // CLASS
    val section: String,          // SECTION
    val fatherName: String?,      // FATHERNAME
    val invoiceNo: String,        // INVOICENO
    val session: String,          // SESSION
    val paidDate: LocalDate,      // PAIDDATE
    val paymentMode: String,      // PAYMENTMODE
    val transactionId: String?,   // TRANSACTIONID
    val remarks: String?,         // REMARKS
    val items: List<ReceiptItem>
) {
    val total: Long get() = items.sumOf { it.amount }
}
