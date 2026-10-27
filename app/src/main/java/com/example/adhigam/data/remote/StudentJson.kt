package com.example.adhigam.data.remote

import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.FeeInstallment
import com.example.adhigam.data.model.FeeReceipt
import com.example.adhigam.data.model.ReceiptItem
import com.example.adhigam.data.model.FeePayment
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.model.StudentProfile
import com.example.adhigam.data.model.StudentStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// Parsers for the student record and fee ledger, shared by the student's own screens and the staff screens.

// One row of GET_ASSIGNEMT_LIST, as returned by /student/download-assignments and /student/assignments.
fun JSONObject.toAssignment(): Assignment? {
    val title = str("assignment_title", "ASSIGNMENTTITLE") ?: return null
    return Assignment(
        title = title,
        subject = str("subject", "SUBJECT").orEmpty(),
        classType = str("class_type", "CLASS").orEmpty(),
        fileUrl = str("file_url", "FILEURL").orEmpty(),
        section = str("section", "SECTION").orEmpty(),
        createdDate = date("created_date", "CreatedDate", "CREATEDDATE")
    )
}

// A STUDENTS row from GET_DATA_DETAILS('STUDENT') (lower-case aliases), as returned by /student/profile,
// /student/view/{id} and /student/edit/{id}.
fun JSONObject.toStudentProfile(): StudentProfile? {
    val name = str("student_name", "STUDENTNAME") ?: return null
    return StudentProfile(
        name = name,
        classType = str("class_type", "CLASS").orEmpty(),
        section = str("section", "SECTION").orEmpty(),
        admissionNo = str("admission_no", "ADMISSIONNO").orEmpty(),
        gender = str("gender", "GENDER").orEmpty(),
        dob = date("dob", "DOB"),
        bloodGroup = str("blood_group", "BLOODGROUP"),
        mobile = str("mobile", "MOBILE").orEmpty(),
        email = str("email", "EMAIL"),
        fatherName = str("father_name", "FATHERNAME"),
        motherName = str("mother_name", "MOTHERNAME"),
        address = str("address", "ADDRESS"),
        photoPath = str("profile_image_url", "PROFILEIMAGE"),
        // Only fee_group_id comes back; the group's name needs a lookup that isn't wired yet.
        feeGroup = str("fee_group_name", "FEEGROUPNAME"),
        district = str("district", "DISTRICT"),
        state = str("state", "STATE"),
        religion = str("religion", "RELIGION"),
        community = str("community", "COMMUNITY"),
        nationality = str("nationality", "NATIONALITY"),
        motherTongue = str("mother_tongue", "MOTHERTONGUE"),
        fatherOccupation = str("father_occupation", "FATHEROCCUPATION"),
        motherOccupation = str("mother_occupation", "MOTHEROCCUPATION"),
        emergencyMobile1 = str("emergency_mobile1", "EMERGENCYMOBILE1"),
        emergencyMobile2 = str("emergency_mobile2", "EMERGENCYMOBILE2"),
        enrollmentNo = str("enrollment_no", "ENROLLMENTNO"),
        admissionYear = str("admission_year", "ADMISSIONYEAR"),
        previousSchool = str("previous_school_name", "PREVIOUSSCHOOLNAME"),
        medicalInformation = str("medical_information", "MEDICALINFORMATION"),
        allergies = str("allergies", "ALLERGIES")
    )
}

// The same row with the staff-only fields (bank, identity, previous marks, transport).
fun JSONObject.toStaffStudentDetail(encryptedId: String): StaffStudentDetail? {
    val profile = toStudentProfile() ?: return null
    return StaffStudentDetail(
        encryptedId = encryptedId,
        profile = profile,
        status = str("status", "STATUS")
            ?.let { s -> StudentStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } }
            ?: StudentStatus.Pending,
        caste = str("caste", "CASTE"),
        aaparId = str("aapar_id", "AAPARID"),
        isSibling = str("is_sibling", "ISSIBLING").let { it == "1" || it.equals("true", ignoreCase = true) },
        previousClass = str("previous_class", "PREVIOUSCLASS"),
        previousMaxMarks = str("previous_max_marks", "PREVIOUSMAXMARKS"),
        previousObtainMarks = str("previous_obtain_marks", "PREVIOUSOBTAINMARKS"),
        specialCategory = str("special_category", "SPECIALCATEGORY"),
        bankName = str("bank_name", "BANKNAME"),
        ifscCode = str("ifsc_code", "IFSCCODE"),
        accountNumber = str("account_number", "ACCOUNTNUMBER"),
        aadharNumber = str("adhar_number", "ADHARNUMBER"),
        panNumber = str("pan_number", "PANNUMBER"),
        medicalReportUrl = str("medical_report_url", "MEDICALREPORTURL"),
        previousMarksheetUrl = str("previous_marksheet_url", "PREVIOUSMARKSHEETURL"),
        identityOne = str("identity_one", "IDENTITYONE"),
        identityTwo = str("identity_two", "IDENTITYTWO"),
        oralMaxMarks = str("oral_max_marks", "ORALMAXMARKS"),
        oralObtainMarks = str("oral_obtain_marks", "ORALOBTAINMARKS"),
        writtenMaxMarks = str("written_max_marks", "WRITTENMAXMARKS"),
        writtenObtainMarks = str("written_obtain_marks", "WRITTENOBTAINMARKS"),
        feeGroupId = int("fee_group_id", "FEEGROUPID")?.takeIf { it > 0 },
        vehicleId = int("vehicle_id", "VEHICLEID")?.takeIf { it > 0 },
        pickupPointId = int("pickup_point_id", "PICKUPPOINTID")?.takeIf { it > 0 },
        vehicleStartMonth = str("vehicle_start_month", "VEHICLESTARTMONTH"),
        vehicleStopMonth = str("vehicle_stop_month", "VEHICLESTOPMONTH")
    )
}

// GET_STUDENT_FEE_LEDGER flattened into one list: the summary row (total_due…), payment rows (invoice_no,
// no fee_month) and installment rows (fee_month, no invoice_no), installments in April–March order.
fun JSONArray.toFeeSummary(): FeeSummary {
    var totalDue = 0L
    var totalPaid = 0L
    var balance = 0L
    var concession = 0L
    val payments = mutableListOf<FeePayment>()
    val installments = mutableListOf<FeeInstallment>()

    for (i in 0 until length()) {
        val row = optJSONObject(i) ?: continue
        val month = row.str("fee_month", "FEE_MONTH")
        val invoice = row.str("invoice_no", "INVOICE_NO")
        when {
            row.has("total_due") || row.has("TOTAL_DUE") -> {
                totalDue = row.amount("total_due", "TOTAL_DUE")
                totalPaid = row.amount("total_paid", "TOTAL_PAID")
                balance = row.amount("balance", "BALANCE")
                concession = row.amount("total_concession", "TOTAL_CONCESSION")
            }
            invoice != null && month == null -> payments += FeePayment(
                invoiceNo = invoice,
                paidDate = row.date("paid_date", "PAID_DATE") ?: continue,
                paymentMode = row.str("payment_mode", "PAYMENT_MODE").orEmpty(),
                amount = row.amount("amount", "AMOUNT"),
                session = row.str("session", "SESSION").orEmpty(),
                transactionId = row.str("transaction_id", "TRANSACTION_ID")
            )
            month != null -> installments += FeeInstallment(
                month = month,
                feeName = row.str("fee_name", "FEE_NAME").orEmpty(),
                amountDue = row.amount("amount_due", "AMOUNT_DUE"),
                amountPaid = row.amount("amount_paid", "AMOUNT_PAID"),
                concession = row.amount("concession_amount", "CONCESSION_AMOUNT"),
                isPaid = row.str("is_paid", "ISPAID").let { it == "1" || it.equals("true", ignoreCase = true) },
                id = row.int("id", "ID")
            )
        }
    }
    return FeeSummary(
        totalDue = totalDue,
        totalPaid = totalPaid,
        balance = balance,
        totalConcession = concession,
        installments = installments,
        payments = payments.sortedByDescending { it.paidDate }
    )
}

// GET /student/payment-receipt/{id}?invoice_no&session: receipt header (upper-case columns), its items
// and the school name / address from School Settings.
fun JSONObject.toFeeReceipt(invoiceNo: String, session: String): FeeReceipt {
    val items = optJSONArray("items") ?: JSONArray()
    return FeeReceipt(
        schoolName = str("school_name") ?: "School",
        schoolAddress = str("school_address").orEmpty(),
        studentName = str("STUDENTNAME", "student_name").orEmpty(),
        admissionNo = str("ADMISSIONNO", "admission_no").orEmpty(),
        classType = str("CLASS", "class_type").orEmpty(),
        section = str("SECTION", "section").orEmpty(),
        fatherName = str("FATHERNAME", "father_name"),
        invoiceNo = str("INVOICENO", "invoice_no") ?: invoiceNo,
        session = str("SESSION", "session") ?: session,
        paidDate = date("PAIDDATE", "paid_date") ?: LocalDate.now(),
        paymentMode = str("PAYMENTMODE", "payment_mode").orEmpty(),
        transactionId = str("TRANSACTIONID", "transaction_id"),
        remarks = str("REMARKS", "remarks"),
        items = (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            ReceiptItem(
                feeName = item.str("FEE_NAME", "FEENAME").orEmpty(),
                feeGroup = item.str("FEE_GROUP_NAME").orEmpty(),
                month = item.str("FEE_MONTH").orEmpty(),
                amount = item.amount("AMOUNT")
            )
        }
    )
}

// Money comes as numbers like 2500.0 (or null when there are no rows); the app shows whole rupees.
private fun JSONObject.amount(vararg keys: String): Long = str(*keys)?.toDoubleOrNull()?.let { Math.round(it) } ?: 0L
