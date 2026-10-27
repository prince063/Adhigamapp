package com.example.adhigam.data.repository

import com.example.adhigam.data.model.FeeGroupOption
import com.example.adhigam.data.model.PickupPoint
import com.example.adhigam.data.model.SiblingInfo
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.model.StudentFormOptions
import com.example.adhigam.data.model.VehicleRoute
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import com.example.adhigam.data.remote.toStaffStudentDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// The student registration / edit form (student-registration.html).
interface StudentFormRepository {
    suspend fun getFormOptions(): StudentFormOptions
    suspend fun getPickupPoints(route: VehicleRoute): List<PickupPoint>
    suspend fun getStudentForEdit(encryptedId: String): StaffStudentDetail
    // Creates (encryptedId == null) or updates a student; returns the student's encrypted id.
    suspend fun saveStudent(encryptedId: String?, detail: StaffStudentDetail): String
    suspend fun findSibling(admissionNo: String): SiblingInfo?
}

class ApiStudentFormRepository(private val api: ApiClient) : StudentFormRepository {

    // /master/occupations, /master/castes, /master/fee-groups and /master/vehicles, in parallel.
    override suspend fun getFormOptions(): StudentFormOptions = coroutineScope {
        val occupations = async { rows("master/occupations") }
        val castes = async { rows("master/castes") }
        val feeGroups = async { rows("master/fee-groups") }
        val vehicles = async { rows("master/vehicles") }
        StudentFormOptions(
            occupations = occupations.await().mapNotNull { it.str("occupation_name") },
            castes = castes.await().mapNotNull { it.str("caste_name") },
            feeGroups = feeGroups.await().mapNotNull { row ->
                val id = row.int("id") ?: return@mapNotNull null
                FeeGroupOption(id, row.str("name") ?: return@mapNotNull null)
            },
            vehicles = vehicles.await().mapNotNull { row ->
                val id = row.int("id") ?: return@mapNotNull null
                VehicleRoute(id, row.str("route_name") ?: "Route $id", row.str("encrypted_id") ?: return@mapNotNull null)
            }
        )
    }

    // GET /master/vehicle/{encrypted_id}/pickups
    override suspend fun getPickupPoints(route: VehicleRoute): List<PickupPoint> =
        rows("master/vehicle/${route.encryptedId.encoded()}/pickups").mapNotNull { row ->
            val id = row.int("id") ?: return@mapNotNull null
            PickupPoint(id, row.str("pickup_point") ?: return@mapNotNull null)
        }

    // GET /student/edit/{encrypt_id}
    override suspend fun getStudentForEdit(encryptedId: String): StaffStudentDetail {
        val json = api.get("student/edit/${encryptedId.encoded()}") as? JSONObject
        return json?.toStaffStudentDetail(encryptedId) ?: throw IllegalStateException("Student not found")
    }

    // POST /student/manage with the StudentDetail body the web form sends; answers {"encrypted_id": …}.
    override suspend fun saveStudent(encryptedId: String?, detail: StaffStudentDetail): String {
        val p = detail.profile
        val body = JSONObject()
            .put("encrypted_id", encryptedId.orEmpty())
            .put("admission_no", p.admissionNo.toIntOrNull() ?: JSONObject.NULL)
            .put("student_name", p.name)
            // father_name, mother_name, address, mobile and gender are required strings in the model.
            .put("father_name", p.fatherName.orEmpty())
            .put("father_occupation", p.fatherOccupation.orEmpty())
            .put("mother_name", p.motherName.orEmpty())
            .put("mother_occupation", p.motherOccupation.orEmpty())
            .put("address", p.address.orEmpty())
            .put("district", p.district.orEmpty())
            .put("state", p.state.orEmpty())
            .put("identity_one", detail.identityOne.orEmpty())
            .put("identity_two", detail.identityTwo.orEmpty())
            .put("aapar_id", detail.aaparId.orEmpty())
            .put("dob", p.dob?.toString() ?: JSONObject.NULL)
            .put("religion", p.religion ?: JSONObject.NULL)
            .put("community", p.community ?: JSONObject.NULL)
            .put("caste", detail.caste.orEmpty())
            .put("nationality", p.nationality ?: "Indian")
            .put("mother_tongue", p.motherTongue.orEmpty())
            .put("mobile", p.mobile)
            .put("email", p.email.orEmpty())
            .put("gender", p.gender)
            .put("previous_school_name", p.previousSchool.orEmpty())
            .put("pan_number", detail.panNumber.orEmpty())
            .put("bank_name", detail.bankName.orEmpty())
            .put("ifsc_code", detail.ifscCode.orEmpty())
            .put("account_number", detail.accountNumber.orEmpty())
            .put("adhar_number", detail.aadharNumber.orEmpty())
            .put("fee_group_id", detail.feeGroupId ?: 0)
            .put("class_type", p.classType.ifBlank { null } ?: JSONObject.NULL)
            .put("section", p.section.ifBlank { null } ?: JSONObject.NULL)
            .put("vehicle_id", detail.vehicleId ?: 0)
            .put("pickup_point_id", detail.pickupPointId ?: 0)
            .put("vehicle_start_month", detail.vehicleStartMonth ?: "April")
            .put("vehicle_stop_month", detail.vehicleStopMonth.orEmpty())
            .put("is_sibling", detail.isSibling)
            .put("enrollment_no", p.enrollmentNo.orEmpty())
            .put("blood_group", p.bloodGroup.orEmpty())
            .put("emergency_mobile1", p.emergencyMobile1.orEmpty())
            .put("emergency_mobile2", p.emergencyMobile2.orEmpty())
            .put("medical_information", p.medicalInformation.orEmpty())
            .put("allergies", p.allergies.orEmpty())
            .put("oral_max_marks", detail.oralMaxMarks.number())
            .put("oral_obtain_marks", detail.oralObtainMarks.number())
            .put("written_max_marks", detail.writtenMaxMarks.number())
            .put("written_obtain_marks", detail.writtenObtainMarks.number())
            .put("previous_max_marks", detail.previousMaxMarks.number())
            .put("previous_obtain_marks", detail.previousObtainMarks.number())
            .put("previous_class", detail.previousClass ?: JSONObject.NULL)
            .put("special_category_list", JSONArray(detail.specialCategory.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }))
            // Keep files uploaded earlier from the web portal; the app doesn't upload documents yet.
            .put("profile_image_url", p.photoPath ?: JSONObject.NULL)
            .put("medical_report_url", detail.medicalReportUrl ?: JSONObject.NULL)
            .put("previous_marksheet_url", detail.previousMarksheetUrl ?: JSONObject.NULL)

        val response = api.post("student/manage", body) as? JSONObject
        return response?.str("encrypted_id") ?: encryptedId.orEmpty()
    }

    // GET /student/sibling/{admission_no}; the backend answers null when there is no such student.
    override suspend fun findSibling(admissionNo: String): SiblingInfo? {
        val number = admissionNo.trim().toIntOrNull() ?: return null
        val json = api.get("student/sibling/$number") as? JSONObject ?: return null
        val name = json.str("student_name", "STUDENTNAME") ?: return null
        return SiblingInfo(
            name = name,
            fatherName = json.str("father_name", "FATHERNAME"),
            motherName = json.str("mother_name", "MOTHERNAME"),
            mobile = json.str("mobile", "MOBILE")
        )
    }

    private suspend fun rows(path: String): List<JSONObject> {
        val array = api.get(path) as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun String?.number(): Double = this?.toDoubleOrNull() ?: 0.0

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
