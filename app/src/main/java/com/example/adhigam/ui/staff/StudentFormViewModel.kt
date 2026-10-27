package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.PickupPoint
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.model.StudentFormLists
import com.example.adhigam.data.model.StudentFormOptions
import com.example.adhigam.data.model.StudentProfile
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.repository.StudentFormRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SiblingEntry(
    val admissionNo: String = "",
    val name: String? = null,      // filled by "Fetch"
    val notFound: Boolean = false,
    val isLoading: Boolean = false
)

// Every field of student-registration.html, as text the user edits.
data class StudentForm(
    // General Information
    val admissionNo: String = "",
    val name: String = "",
    val fatherName: String = "",
    val fatherOccupation: String = "",
    val motherName: String = "",
    val motherOccupation: String = "",
    val mobile: String = "",
    val email: String = "",
    val gender: String = "",
    val dob: LocalDate? = null,
    val bloodGroup: String = "",
    val religion: String = "",
    val community: String = "",
    val caste: String = "",
    val nationality: String = "Indian",
    val motherTongue: String = "",
    val aaparId: String = "",
    val specialCategory: String = "",
    // Address & Identity
    val address: String = "",
    val district: String = "",
    val state: String = "",
    val identityOne: String = "",
    val identityTwo: String = "",
    // Emergency & Medical
    val emergencyMobile1: String = "",
    val emergencyMobile2: String = "",
    val medicalInformation: String = "",
    val allergies: String = "",
    // Previous School
    val previousSchool: String = "",
    val previousClass: String = "",
    val previousMaxMarks: String = "",
    val previousObtainMarks: String = "",
    val oralMaxMarks: String = "",
    val oralObtainMarks: String = "",
    val writtenMaxMarks: String = "",
    val writtenObtainMarks: String = "",
    // Bank Information
    val panNumber: String = "",
    val aadharNumber: String = "",
    val bankName: String = "",
    val ifscCode: String = "",
    val accountNumber: String = "",
    // School / Admission
    val feeGroupId: Int? = null,
    val classType: String = "",
    val section: String = "",
    val enrollmentNo: String = "",
    val vehicleId: Int? = null,
    val pickupPointId: Int? = null,
    val vehicleStartMonth: String = "",
    val vehicleStopMonth: String = "",
    // Siblings
    val isSibling: Boolean = false,
    val siblings: List<SiblingEntry> = emptyList()
)

enum class FormField { Name, Mobile, Email, EmergencyMobile1, EmergencyMobile2, ClassType, Section, VehicleMonths }

data class StudentFormUiState(
    val isEdit: Boolean,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val options: StudentFormOptions? = null,
    // Pickup points of the selected route, loaded when the route is chosen.
    val pickups: List<PickupPoint> = emptyList(),
    val isLoadingPickups: Boolean = false,
    val form: StudentForm = StudentForm(),
    val status: StudentStatus = StudentStatus.Pending,
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val message: String? = null
) {
    val errors: Map<FormField, String>
        get() = buildMap {
            val f = form
            if (f.name.isBlank()) put(FormField.Name, "Student name is required")
            when {
                f.mobile.isBlank() -> put(FormField.Mobile, "Mobile number is required")
                f.mobile.length != 10 -> put(FormField.Mobile, "Enter a 10-digit mobile number")
            }
            if (f.email.isNotBlank() && !f.email.contains('@')) put(FormField.Email, "Enter a valid email")
            if (f.emergencyMobile1.isNotBlank() && f.emergencyMobile1.length != 10) put(FormField.EmergencyMobile1, "Enter a 10-digit number")
            if (f.emergencyMobile2.isNotBlank() && f.emergencyMobile2.length != 10) put(FormField.EmergencyMobile2, "Enter a 10-digit number")
            if (f.classType.isBlank()) put(FormField.ClassType, "Select a class")
            if (f.section.isBlank()) put(FormField.Section, "Select a section")
            val months = StudentFormLists.sessionMonths
            val start = months.indexOf(f.vehicleStartMonth)
            val stop = months.indexOf(f.vehicleStopMonth)
            if (start >= 0 && stop >= 0 && start > stop) {
                put(FormField.VehicleMonths, "Vehicle Start Month cannot be later than Stop Month")
            }
        }

    fun errorFor(field: FormField): String? = if (showErrors) errors[field] else null
}

// Mirrors student-registration.html: one form for registering a new student (encryptedId == null)
// and for editing an existing one.
class StudentFormViewModel(
    private val repository: StudentFormRepository,
    private val encryptedId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentFormUiState(isEdit = encryptedId != null))
    val uiState: StateFlow<StudentFormUiState> = _uiState.asStateFlow()

    // Kept so fields the form doesn't show (medical report, etc.) survive a save.
    private var original: StaffStudentDetail? = null

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val options = repository.getFormOptions()
                val detail = encryptedId?.let { repository.getStudentForEdit(it) }
                original = detail
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        options = options,
                        form = detail?.toForm() ?: StudentForm(),
                        status = detail?.status ?: StudentStatus.Pending
                    )
                }
                detail?.vehicleId?.let { loadPickups(it, keepSelection = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "Could not load student") }
            }
        }
    }

    fun update(change: (StudentForm) -> StudentForm) = _uiState.update { it.copy(form = change(it.form)) }

    // Changing the route clears the pickup point and loads the new route's pickup points.
    fun setVehicle(vehicleId: Int?) {
        _uiState.update { it.copy(form = it.form.copy(vehicleId = vehicleId, pickupPointId = null), pickups = emptyList()) }
        if (vehicleId != null) loadPickups(vehicleId, keepSelection = false)
    }

    private fun loadPickups(vehicleId: Int, keepSelection: Boolean) {
        val route = _uiState.value.options?.vehicles?.firstOrNull { it.id == vehicleId } ?: return
        _uiState.update { it.copy(isLoadingPickups = true) }
        viewModelScope.launch {
            try {
                val pickups = repository.getPickupPoints(route)
                _uiState.update { state ->
                    // Ignore a late answer for a route that is no longer selected.
                    if (state.form.vehicleId != vehicleId) return@update state.copy(isLoadingPickups = false)
                    val keep = keepSelection && pickups.any { it.id == state.form.pickupPointId }
                    state.copy(
                        pickups = pickups,
                        isLoadingPickups = false,
                        form = if (keep) state.form else state.form.copy(pickupPointId = null)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingPickups = false, message = e.message ?: "Could not load pickup points") }
            }
        }
    }

    fun setSibling(isSibling: Boolean) = update {
        it.copy(isSibling = isSibling, siblings = if (isSibling && it.siblings.isEmpty()) listOf(SiblingEntry()) else it.siblings)
    }

    fun addSibling() = update { it.copy(siblings = it.siblings + SiblingEntry()) }

    fun removeSibling(index: Int) = update { it.copy(siblings = it.siblings.filterIndexed { i, _ -> i != index }) }

    fun setSiblingAdmission(index: Int, text: String) = updateSibling(index) {
        SiblingEntry(admissionNo = text.filter(Char::isDigit).take(8))
    }

    // Looks up the sibling and fills their name, like the web "fetch" button.
    fun fetchSibling(index: Int) {
        val admissionNo = _uiState.value.form.siblings.getOrNull(index)?.admissionNo.orEmpty()
        if (admissionNo.isBlank()) return
        updateSibling(index) { it.copy(isLoading = true, notFound = false) }
        viewModelScope.launch {
            val found = try {
                repository.findSibling(admissionNo)
            } catch (e: Exception) {
                null
            }
            updateSibling(index) {
                if (found == null) it.copy(isLoading = false, notFound = true, name = null)
                else it.copy(isLoading = false, notFound = false, name = "${found.name} (Father: ${found.fatherName ?: "—"})")
            }
            if (found == null) _uiState.update { it.copy(message = "Student not found") }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(showErrors = true) }
        val firstError = state.errors.values.firstOrNull()
        if (firstError != null) {
            _uiState.update { it.copy(message = firstError) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.saveStudent(encryptedId, state.form.toDetail(original, state.status))
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedMessage = if (encryptedId != null) "Student updated successfully" else "Student registered successfully"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = e.message ?: "Could not save student") }
            }
        }
    }

    private fun updateSibling(index: Int, change: (SiblingEntry) -> SiblingEntry) = update { form ->
        form.copy(siblings = form.siblings.mapIndexed { i, s -> if (i == index) change(s) else s })
    }
}

private fun StaffStudentDetail.toForm(): StudentForm {
    val p = profile
    return StudentForm(
        admissionNo = p.admissionNo,
        name = p.name,
        fatherName = p.fatherName.orEmpty(),
        fatherOccupation = p.fatherOccupation.orEmpty(),
        motherName = p.motherName.orEmpty(),
        motherOccupation = p.motherOccupation.orEmpty(),
        mobile = p.mobile,
        email = p.email.orEmpty(),
        gender = p.gender,
        dob = p.dob,
        bloodGroup = p.bloodGroup.orEmpty(),
        religion = p.religion.orEmpty(),
        community = p.community.orEmpty(),
        caste = caste.orEmpty(),
        nationality = p.nationality.orEmpty(),
        motherTongue = p.motherTongue.orEmpty(),
        aaparId = aaparId.orEmpty(),
        specialCategory = specialCategory.orEmpty(),
        address = p.address.orEmpty(),
        district = p.district.orEmpty(),
        state = p.state.orEmpty(),
        identityOne = identityOne.orEmpty(),
        identityTwo = identityTwo.orEmpty(),
        emergencyMobile1 = p.emergencyMobile1.orEmpty(),
        emergencyMobile2 = p.emergencyMobile2.orEmpty(),
        medicalInformation = p.medicalInformation.orEmpty(),
        allergies = p.allergies.orEmpty(),
        previousSchool = p.previousSchool.orEmpty(),
        previousClass = previousClass.orEmpty(),
        previousMaxMarks = previousMaxMarks.orEmpty(),
        previousObtainMarks = previousObtainMarks.orEmpty(),
        oralMaxMarks = oralMaxMarks.orEmpty(),
        oralObtainMarks = oralObtainMarks.orEmpty(),
        writtenMaxMarks = writtenMaxMarks.orEmpty(),
        writtenObtainMarks = writtenObtainMarks.orEmpty(),
        panNumber = panNumber.orEmpty(),
        aadharNumber = aadharNumber.orEmpty(),
        bankName = bankName.orEmpty(),
        ifscCode = ifscCode.orEmpty(),
        accountNumber = accountNumber.orEmpty(),
        feeGroupId = feeGroupId,
        classType = p.classType,
        section = p.section,
        enrollmentNo = p.enrollmentNo.orEmpty(),
        vehicleId = vehicleId,
        pickupPointId = pickupPointId,
        vehicleStartMonth = vehicleStartMonth.orEmpty(),
        vehicleStopMonth = vehicleStopMonth.orEmpty(),
        isSibling = isSibling,
        siblings = siblingAdmissionNos.map { SiblingEntry(admissionNo = it) }
    )
}

private fun String.orNull(): String? = trim().ifBlank { null }

private fun StudentForm.toDetail(original: StaffStudentDetail?, status: StudentStatus): StaffStudentDetail {
    val base = original?.profile
    return StaffStudentDetail(
        encryptedId = original?.encryptedId.orEmpty(),
        profile = StudentProfile(
            name = name.trim(),
            classType = classType,
            section = section,
            admissionNo = admissionNo,
            gender = gender,
            dob = dob,
            bloodGroup = bloodGroup.orNull(),
            mobile = mobile,
            email = email.orNull(),
            fatherName = fatherName.orNull(),
            motherName = motherName.orNull(),
            address = address.orNull(),
            photoPath = base?.photoPath,
            feeGroup = base?.feeGroup,
            district = district.orNull(),
            state = state.orNull(),
            religion = religion.orNull(),
            community = community.orNull(),
            nationality = nationality.orNull(),
            motherTongue = motherTongue.orNull(),
            fatherOccupation = fatherOccupation.orNull(),
            motherOccupation = motherOccupation.orNull(),
            emergencyMobile1 = emergencyMobile1.orNull(),
            emergencyMobile2 = emergencyMobile2.orNull(),
            enrollmentNo = enrollmentNo.orNull(),
            admissionYear = base?.admissionYear ?: LocalDate.now().year.toString(),
            previousSchool = previousSchool.orNull(),
            medicalInformation = medicalInformation.orNull(),
            allergies = allergies.orNull()
        ),
        status = status,
        caste = caste.orNull(),
        aaparId = aaparId.orNull(),
        isSibling = isSibling,
        previousClass = previousClass.orNull(),
        previousMaxMarks = previousMaxMarks.orNull(),
        previousObtainMarks = previousObtainMarks.orNull(),
        specialCategory = specialCategory.orNull(),
        bankName = bankName.orNull(),
        ifscCode = ifscCode.orNull(),
        accountNumber = accountNumber.orNull(),
        aadharNumber = aadharNumber.orNull(),
        panNumber = panNumber.orNull(),
        medicalReportUrl = original?.medicalReportUrl,
        previousMarksheetUrl = original?.previousMarksheetUrl,
        feeGroupId = feeGroupId,
        identityOne = identityOne.orNull(),
        identityTwo = identityTwo.orNull(),
        oralMaxMarks = oralMaxMarks.orNull(),
        oralObtainMarks = oralObtainMarks.orNull(),
        writtenMaxMarks = writtenMaxMarks.orNull(),
        writtenObtainMarks = writtenObtainMarks.orNull(),
        vehicleId = vehicleId,
        pickupPointId = if (vehicleId != null) pickupPointId else null,
        vehicleStartMonth = vehicleStartMonth.orNull(),
        vehicleStopMonth = vehicleStopMonth.orNull(),
        siblingAdmissionNos = if (isSibling) siblings.mapNotNull { it.admissionNo.orNull() } else emptyList()
    )
}
