package com.example.adhigam.data.repository

import com.example.adhigam.data.model.FeeReceipt
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.FeePaymentRequest
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.remote.toFeeReceipt
import com.example.adhigam.data.remote.toFeeSummary
import com.example.adhigam.data.remote.toStaffStudentDetail
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// A recorded payment: the invoice number and session the receipt is filed under.
data class RecordedPayment(val invoiceNo: String, val session: String)

// Staff fee collection (fee-collection.html) and fee receipts (payment-receipt.html).
interface FeeCollectionRepository {
    // GET /student/search: query matches name, admission no or mobile; class / section narrow it.
    suspend fun searchStudents(query: String, classType: String?, section: String?): List<StudentListItem>
    suspend fun getStudent(encryptedId: String): StudentListItem
    suspend fun getFeeLedger(encryptedId: String): FeeSummary
    suspend fun recordPayment(encryptedId: String, request: FeePaymentRequest): RecordedPayment
    suspend fun getReceipt(encryptedId: String, invoiceNo: String, session: String): FeeReceipt
}

class ApiFeeCollectionRepository(private val api: ApiClient) : FeeCollectionRepository {

    override suspend fun searchStudents(query: String, classType: String?, section: String?): List<StudentListItem> {
        val params = listOfNotNull(
            "query=${query.trim().encoded()}",
            classType?.let { "class_type=${it.encoded()}" },
            section?.let { "section=${it.encoded()}" }
        ).joinToString("&")
        val rows = api.get("student/search?$params") as? JSONArray ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            StudentListItem(
                encryptedId = row.str("encrypted_id") ?: return@mapNotNull null,
                admissionNo = row.str("admission_no").orEmpty(),
                name = row.str("student_name").orEmpty(),
                fatherName = null,
                mobile = row.str("mobile").orEmpty(),
                dueFee = 0, // not part of the search result; the ledger shows it once selected
                classType = row.str("class_type").orEmpty(),
                section = row.str("section").orEmpty(),
                status = row.status()
            )
        }
    }

    // GET /student/view/{id} — gives the father's name and status shown on the student card.
    override suspend fun getStudent(encryptedId: String): StudentListItem {
        val json = api.get("student/view/${encryptedId.encoded()}") as? JSONObject
        val detail = json?.toStaffStudentDetail(encryptedId) ?: throw IllegalStateException("Student not found")
        val p = detail.profile
        return StudentListItem(
            encryptedId = encryptedId,
            admissionNo = p.admissionNo,
            name = p.name,
            fatherName = p.fatherName,
            mobile = p.mobile,
            dueFee = 0,
            classType = p.classType,
            section = p.section,
            status = detail.status,
            feeGroup = p.feeGroup
        )
    }

    // GET /student/fee-ledger/{id}
    override suspend fun getFeeLedger(encryptedId: String): FeeSummary =
        (api.get("student/fee-ledger/${encryptedId.encoded()}") as? JSONArray ?: JSONArray()).toFeeSummary()

    // POST /student/pay-fee-partial — answers {"invoiceno": …, "session": …}.
    override suspend fun recordPayment(encryptedId: String, request: FeePaymentRequest): RecordedPayment {
        val body = JSONObject()
            .put("encrypt_student_id", encryptedId)
            .put("amount", request.amount)
            .put("payment_mode", request.mode.value)
            .put("remarks", request.remarks.orEmpty())
            .put("bank_name", request.bankName.orEmpty())
            .put("netbanking_type", request.netbankingType.orEmpty())
            .put("transaction_id", request.transactionId.orEmpty())
            .put("cheque_no", request.chequeNo.orEmpty())
            // The web form sends the UPI id in account_no when there is no account number.
            .put("account_no", request.accountNo ?: request.upiId.orEmpty())
            .put("card_type", request.cardType.orEmpty())
            .put("name_of_payee", request.payeeName.orEmpty())
            // Plain row ids are accepted (safe_decrypt_id falls back to int parsing).
            .put("fee_ids", if (request.feeIds.isEmpty()) JSONObject.NULL else JSONArray(request.feeIds.map { it.toString() }))
        val response = api.post("student/pay-fee-partial", body) as? JSONObject
        val invoice = response?.str("invoiceno", "INVOICENO", "invoice_no")
            ?: throw IllegalStateException("Payment was sent but no invoice number came back — check the ledger before retrying")
        return RecordedPayment(invoice, response.str("session", "SESSION").orEmpty())
    }

    // GET /student/payment-receipt/{id}?invoice_no&session
    override suspend fun getReceipt(encryptedId: String, invoiceNo: String, session: String): FeeReceipt {
        val number = invoiceNo.toIntOrNull() ?: throw IllegalArgumentException("Invalid invoice number")
        val json = api.get("student/payment-receipt/${encryptedId.encoded()}?invoice_no=$number&session=${session.encoded()}") as? JSONObject
            ?: throw IllegalStateException("Receipt not found")
        return json.toFeeReceipt(invoiceNo, session)
    }

    private fun JSONObject.status(): StudentStatus =
        str("status", "STATUS")?.let { s -> StudentStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } }
            ?: StudentStatus.Pending

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
