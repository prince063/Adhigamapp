package com.example.adhigam.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.ExamType
import com.example.adhigam.data.model.MarksEntry
import com.example.adhigam.data.model.TeacherSubject
import com.example.adhigam.data.repository.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// "80" rather than "80.0" in the text fields.
fun formatMarks(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// What the teacher has typed for one student; parsed and checked against the max marks.
data class MarksRow(val entry: MarksEntry, val written: String, val practical: String)

enum class MarksError { NotANumber, AboveMax }

data class ExamMarksUiState(
    val isLoadingOptions: Boolean = true,
    val optionsError: String? = null,
    val examTypes: List<ExamType> = emptyList(),
    val teacherSubjects: List<TeacherSubject> = emptyList(),
    val examTypeId: Int? = null,
    val classType: String? = null,
    val section: String? = null,
    val subject: String? = null,
    val isLoadingStudents: Boolean = false,
    val rows: List<MarksRow> = emptyList(),
    val writtenMax: String = "",
    val practicalMax: String = "",
    val query: String = "",
    val hasChanges: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null
) {
    // Teachers only get the subjects assigned to them for the chosen class + section, like the web page.
    val subjectOptions: List<String>
        get() = teacherSubjects
            .filter { it.classType == classType && (section == null || it.section == section) }
            .map { it.subject }
            .distinct()
    val examType: ExamType? get() = examTypes.firstOrNull { it.id == examTypeId }
    val filtersComplete: Boolean get() = examTypeId != null && classType != null && section != null && subject != null
    val writtenMaxValue: Double? get() = writtenMax.toDoubleOrNull()?.takeIf { it >= 0 }
    val practicalMaxValue: Double? get() = practicalMax.ifBlank { "0" }.toDoubleOrNull()?.takeIf { it >= 0 }
    val hasPractical: Boolean get() = (practicalMaxValue ?: 0.0) > 0.0
    val totalMax: Double get() = (writtenMaxValue ?: 0.0) + (if (hasPractical) practicalMaxValue ?: 0.0 else 0.0)

    fun writtenError(row: MarksRow): MarksError? = checkMarks(row.written, writtenMaxValue)
    fun practicalError(row: MarksRow): MarksError? = if (hasPractical) checkMarks(row.practical, practicalMaxValue) else null

    fun total(row: MarksRow): Double? {
        val written = row.written.toDoubleOrNull()
        val practical = if (hasPractical) row.practical.toDoubleOrNull() else 0.0
        if (row.written.isBlank() && (!hasPractical || row.practical.isBlank())) return null
        return (written ?: 0.0) + (practical ?: 0.0)
    }

    val enteredCount: Int get() = rows.count { total(it) != null }
    val hasErrors: Boolean
        get() = writtenMaxValue == null || practicalMaxValue == null ||
            rows.any { writtenError(it) != null || practicalError(it) != null }
    val classAverage: Double?
        get() = rows.mapNotNull { total(it) }.takeIf { it.isNotEmpty() }?.average()
    val visibleRows: List<MarksRow>
        get() = if (query.isBlank()) rows
        else rows.filter { it.entry.studentName.contains(query, ignoreCase = true) || it.entry.admissionNo.contains(query) }

    private fun checkMarks(text: String, max: Double?): MarksError? {
        if (text.isBlank()) return null
        val value = text.toDoubleOrNull() ?: return MarksError.NotANumber
        if (value < 0) return MarksError.NotANumber
        if (max != null && value > max) return MarksError.AboveMax
        return null
    }
}

// Mirrors exam-marks.html: pick exam type, class, section and subject, load the students,
// enter written / practical marks and save them all at once.
class ExamMarksViewModel(private val repository: StaffRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamMarksUiState())
    val uiState: StateFlow<ExamMarksUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    fun loadOptions() {
        _uiState.update { it.copy(isLoadingOptions = true, optionsError = null) }
        viewModelScope.launch {
            try {
                val examTypes = repository.getExamTypes()
                val subjects = repository.getTeacherSubjects()
                _uiState.update { it.copy(isLoadingOptions = false, examTypes = examTypes, teacherSubjects = subjects) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingOptions = false, optionsError = e.message ?: "Could not load exam types") }
            }
        }
    }

    // Changing any filter clears the loaded list, so marks are never saved under the wrong exam or subject.
    fun selectExamType(id: Int) = _uiState.update { it.copy(examTypeId = id).cleared() }

    fun selectClass(classType: String) = _uiState.update { state ->
        state.copy(classType = classType).let { it.copy(subject = it.subject?.takeIf { s -> s in it.subjectOptions }) }.cleared()
    }

    fun selectSection(section: String) = _uiState.update { state ->
        state.copy(section = section).let { it.copy(subject = it.subject?.takeIf { s -> s in it.subjectOptions }) }.cleared()
    }

    fun selectSubject(subject: String) = _uiState.update { it.copy(subject = subject).cleared() }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setWrittenMax(text: String) = _uiState.update { it.copy(writtenMax = text.marksInput(), hasChanges = true) }

    fun setPracticalMax(text: String) = _uiState.update { it.copy(practicalMax = text.marksInput(), hasChanges = true) }

    fun setWritten(admissionNo: String, text: String) = updateRow(admissionNo) { it.copy(written = text.marksInput()) }

    fun setPractical(admissionNo: String, text: String) = updateRow(admissionNo) { it.copy(practical = text.marksInput()) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun loadStudents() = loadStudents(successMessage = null)

    // successMessage is shown once the list has loaded (used after saving).
    private fun loadStudents(successMessage: String?) {
        val state = _uiState.value
        if (!state.filtersComplete) {
            // Same message as the web page.
            _uiState.update { it.copy(message = "All filters are required") }
            return
        }
        _uiState.update { it.copy(isLoadingStudents = true, rows = emptyList(), query = "") }
        viewModelScope.launch {
            try {
                val entries = repository.getMarks(state.examTypeId!!, state.classType!!, state.section!!, state.subject!!)
                val first = entries.firstOrNull()
                _uiState.update {
                    it.copy(
                        isLoadingStudents = false,
                        rows = entries.map { e ->
                            MarksRow(
                                entry = e,
                                written = e.writtenObtained?.let(::formatMarks).orEmpty(),
                                practical = e.practicalObtained?.let(::formatMarks).orEmpty()
                            )
                        },
                        writtenMax = first?.writtenMax?.let(::formatMarks).orEmpty(),
                        practicalMax = first?.practicalMax?.let(::formatMarks).orEmpty(),
                        hasChanges = false,
                        message = if (entries.isEmpty()) "No students found for this class/section" else successMessage
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingStudents = false, message = e.message ?: "Could not load students") }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.rows.isEmpty() || state.isSaving || state.hasErrors) return
        val writtenMax = state.writtenMaxValue ?: return
        val practicalMax = if (state.hasPractical) state.practicalMaxValue ?: 0.0 else 0.0
        // Blank fields are saved as 0, like the web page.
        val entries = state.rows.map { row ->
            row.entry.copy(
                writtenMax = writtenMax,
                writtenObtained = row.written.toDoubleOrNull() ?: 0.0,
                practicalMax = practicalMax,
                practicalObtained = if (state.hasPractical) row.practical.toDoubleOrNull() ?: 0.0 else 0.0
            )
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.saveMarks(state.examTypeId!!, state.classType!!, state.section!!, state.subject!!, entries)
                _uiState.update { it.copy(isSaving = false, hasChanges = false) }
                // Reload so rows carry the marks ids the server created (a second save then updates, not duplicates).
                loadStudents(successMessage = "Marks saved successfully")
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = e.message ?: "Could not save marks") }
            }
        }
    }

    private fun updateRow(admissionNo: String, change: (MarksRow) -> MarksRow) = _uiState.update { state ->
        state.copy(
            rows = state.rows.map { if (it.entry.admissionNo == admissionNo) change(it) else it },
            hasChanges = true
        )
    }

    private fun ExamMarksUiState.cleared() = copy(rows = emptyList(), query = "", hasChanges = false)

    // Digits and one decimal point, at most 5 characters (e.g. "100", "72.5").
    private fun String.marksInput(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        val oneDot = if (firstDot < 0) filtered else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
        return oneDot.take(5)
    }
}
