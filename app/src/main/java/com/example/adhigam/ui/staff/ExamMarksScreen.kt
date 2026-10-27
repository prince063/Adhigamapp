package com.example.adhigam.ui.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.ExamType
import com.example.adhigam.data.model.MarksEntry
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.TeacherSubject
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen

@Composable
fun ExamMarksScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamMarksViewModel = viewModel { ExamMarksViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    when {
        state.isLoadingOptions -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.optionsError != null -> ErrorState(state.optionsError!!, onRetry = viewModel::loadOptions, modifier = modifier)
        else -> ExamMarksContent(
            state = state,
            actions = ExamMarksActions(
                onSelectExamType = viewModel::selectExamType,
                onSelectClass = viewModel::selectClass,
                onSelectSection = viewModel::selectSection,
                onSelectSubject = viewModel::selectSubject,
                onLoad = viewModel::loadStudents,
                onWrittenMax = viewModel::setWrittenMax,
                onPracticalMax = viewModel::setPracticalMax,
                onWritten = viewModel::setWritten,
                onPractical = viewModel::setPractical,
                onQuery = viewModel::setQuery,
                onSave = viewModel::save
            ),
            modifier = modifier
        )
    }
}

private class ExamMarksActions(
    val onSelectExamType: (Int) -> Unit,
    val onSelectClass: (String) -> Unit,
    val onSelectSection: (String) -> Unit,
    val onSelectSubject: (String) -> Unit,
    val onLoad: () -> Unit,
    val onWrittenMax: (String) -> Unit,
    val onPracticalMax: (String) -> Unit,
    val onWritten: (String, String) -> Unit,
    val onPractical: (String, String) -> Unit,
    val onQuery: (String) -> Unit,
    val onSave: () -> Unit
)

@Composable
private fun ExamMarksContent(state: ExamMarksUiState, actions: ExamMarksActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MarksFiltersCard(state, actions) }
            when {
                state.isLoadingStudents -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                state.rows.isEmpty() -> item {
                    ListCard { EmptyText("Select exam type, class, section and subject, then tap Load Students") }
                }
                else -> {
                    item { MaxMarksCard(state, actions) }
                    item {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = actions.onQuery,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search by name or admission no") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                    if (state.visibleRows.isEmpty()) {
                        item { ListCard { EmptyText("No student matches \"${state.query}\"") } }
                    }
                    items(state.visibleRows, key = { it.entry.admissionNo }) { row ->
                        MarksStudentCard(
                            number = state.rows.indexOf(row) + 1,
                            row = row,
                            state = state,
                            onWritten = { actions.onWritten(row.entry.admissionNo, it) },
                            onPractical = { actions.onPractical(row.entry.admissionNo, it) }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.rows.isNotEmpty() && !state.isLoadingStudents,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            MarksSaveBar(state, onSave = actions.onSave)
        }
    }
}

@Composable
private fun MarksFiltersCard(state: ExamMarksUiState, actions: ExamMarksActions) {
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val noExamTypes = !state.isLoadingOptions && state.examTypes.isEmpty()
            SelectField(
                label = "Exam Type *",
                placeholder = if (noExamTypes) "No exam types created" else "Select Exam Type",
                value = state.examType?.let(::examTypeLabel),
                options = state.examTypes,
                optionLabel = ::examTypeLabel,
                onSelect = { actions.onSelectExamType(it.id) }
            )
            if (noExamTypes) {
                // GET /master/exam-types is empty until an admin adds them (web: Exam Type List).
                Text(
                    "No exam types have been added yet. An admin needs to add them (e.g. Half Yearly, Annual) on the web portal's Exam Type List page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = "Class *",
                    placeholder = "Select Class",
                    value = state.classType?.let(::classDisplayName),
                    options = SchoolClasses,
                    optionLabel = ::classDisplayName,
                    onSelect = actions.onSelectClass,
                    modifier = Modifier.weight(1.3f)
                )
                SelectField(
                    label = "Section *",
                    placeholder = "Select",
                    value = state.section,
                    options = SchoolSections,
                    optionLabel = { it },
                    onSelect = actions.onSelectSection,
                    modifier = Modifier.weight(1f)
                )
            }
            val noSubjects = state.classType != null && state.section != null && state.subjectOptions.isEmpty()
            SelectField(
                label = "Subject *",
                placeholder = when {
                    state.classType == null -> "Select a class first"
                    noSubjects -> "No subject assigned to you"
                    else -> "Select Subject"
                },
                value = state.subject,
                options = state.subjectOptions,
                optionLabel = { it },
                onSelect = actions.onSelectSubject
            )
            if (noSubjects) {
                Text(
                    "You are not assigned any subject in ${classDisplayName(state.classType!!)}-${state.section}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.examType?.isResultDeclared == true) {
                InfoLine("Result for ${state.examType!!.name} is already declared — saved changes will show on report cards.", Amber600)
            }
            Button(
                onClick = actions.onLoad,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Load Students", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun examTypeLabel(type: ExamType): String =
    if (type.isResultDeclared) "${type.name} (Result declared)" else type.name

@Composable
private fun InfoLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MaxMarksCard(state: ExamMarksUiState, actions: ExamMarksActions) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${state.subject} • ${classDisplayName(state.classType.orEmpty())}-${state.section} • ${state.examType?.name.orEmpty()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MarksField(
                    label = "Written Max",
                    value = state.writtenMax,
                    onValueChange = actions.onWrittenMax,
                    isError = state.writtenMaxValue == null,
                    modifier = Modifier.weight(1f)
                )
                MarksField(
                    label = "Practical Max",
                    value = state.practicalMax,
                    onValueChange = actions.onPracticalMax,
                    isError = state.practicalMaxValue == null,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoLine("Max marks apply to every student. Set Practical Max to 0 if the subject has no practical.", MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarksStat("Entered", "${state.enteredCount}/${state.rows.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MarksStat("Out of", formatMarks(state.totalMax), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                MarksStat(
                    "Class avg",
                    state.classAverage?.let { avg -> formatMarks(Math.round(avg * 10) / 10.0) } ?: "—",
                    SuccessGreen,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MarksStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MarksField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    errorText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        isError = isError,
        supportingText = if (errorText != null) { { Text(errorText) } } else null,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
    )
}

private fun errorMessage(error: MarksError?): String? = when (error) {
    MarksError.NotANumber -> "Enter a valid number"
    MarksError.AboveMax -> "More than max"
    null -> null
}

@Composable
private fun MarksStudentCard(
    number: Int,
    row: MarksRow,
    state: ExamMarksUiState,
    onWritten: (String) -> Unit,
    onPractical: (String) -> Unit
) {
    val total = state.total(row)
    val totalMax = state.totalMax
    val percent = if (total != null && totalMax > 0) Math.round(total * 100 / totalMax).toInt() else null
    val writtenError = state.writtenError(row)
    val practicalError = state.practicalError(row)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(number.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.entry.studentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Adm No: ${row.entry.admissionNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (total != null && writtenError == null && practicalError == null) {
                    StatusPill(
                        "${formatMarks(total)}/${formatMarks(totalMax)}" + (percent?.let { " • $it%" } ?: ""),
                        when {
                            percent == null -> MaterialTheme.colorScheme.primary
                            percent >= 60 -> SuccessGreen
                            percent >= 33 -> Amber600
                            else -> ErrorRed
                        }
                    )
                } else if (total == null) {
                    StatusPill("Not entered", MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MarksField(
                    label = "Written",
                    value = row.written,
                    onValueChange = onWritten,
                    suffix = state.writtenMaxValue?.let { "/ ${formatMarks(it)}" },
                    isError = writtenError != null,
                    errorText = errorMessage(writtenError),
                    modifier = Modifier.weight(1f)
                )
                if (state.hasPractical) {
                    MarksField(
                        label = "Practical",
                        value = row.practical,
                        onValueChange = onPractical,
                        suffix = state.practicalMaxValue?.let { "/ ${formatMarks(it)}" },
                        isError = practicalError != null,
                        errorText = errorMessage(practicalError),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarksSaveBar(state: ExamMarksUiState, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.enteredCount} of ${state.rows.size} entered",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        state.hasErrors -> "Fix the highlighted marks"
                        state.hasChanges -> "Not saved yet"
                        else -> "All changes saved"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.hasErrors || state.hasChanges) MaterialTheme.colorScheme.error else SuccessGreen
                )
            }
            Button(onClick = onSave, enabled = !state.isSaving && !state.hasErrors) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Text("Save All Marks")
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun ExamMarksPreview() {
    val entries = listOf(
        MarksEntry(0, 1, "1801", "Aarav Sharma", 80.0, 64.0, 20.0, 17.0),
        MarksEntry(0, 2, "1802", "Ananya Verma", 80.0, null, 20.0, null),
        MarksEntry(0, 3, "1803", "Kabir Khan", 80.0, 85.0, 20.0, 12.0)
    )
    AdhigamTheme {
        ExamMarksContent(
            state = ExamMarksUiState(
                isLoadingOptions = false,
                examTypes = listOf(ExamType(2, "Half Yearly", false)),
                teacherSubjects = listOf(TeacherSubject("VIII", "A", "Mathematics")),
                examTypeId = 2,
                classType = "VIII",
                section = "A",
                subject = "Mathematics",
                rows = entries.map {
                    MarksRow(it, it.writtenObtained?.let(::formatMarks).orEmpty(), it.practicalObtained?.let(::formatMarks).orEmpty())
                },
                writtenMax = "80",
                practicalMax = "20",
                hasChanges = true
            ),
            actions = ExamMarksActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
        )
    }
}
