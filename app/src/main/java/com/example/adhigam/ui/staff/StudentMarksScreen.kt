package com.example.adhigam.ui.staff

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentMarkRow
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen

// One student's marks across exams (web student-marks.html filtered by admission no):
// written / practical per subject, total and PASS / FAIL at 33%.
@Composable
fun StudentMarksScreen(
    encryptedId: String,
    onOpenMarksheet: (examTypeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentMarksViewModel = viewModel(key = "student-marks-$encryptedId") {
        StudentMarksViewModel(AppContainer.studentMarksRepository, encryptedId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { data ->
        StudentMarksContent(
            student = data.student,
            rows = data.rows,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onMarksheet = onOpenMarksheet,
            modifier = modifier
        )
    }
}

private fun percentOf(obtained: Double, max: Double): Int = if (max > 0) Math.round(obtained * 100 / max).toInt() else 0

@Composable
private fun percentColor(percent: Int): Color = when {
    percent >= 60 -> SuccessGreen
    percent >= 33 -> Amber600
    else -> ErrorRed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentMarksContent(
    student: StudentListItem,
    rows: List<StudentMarkRow>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMarksheet: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Exams in the order they were held (exam type id), each with its subject rows.
    val exams = rows.groupBy { it.examTypeId to it.examType }.toList().sortedBy { it.first.first }
    var selectedExam by rememberSaveable { mutableStateOf<Int?>(null) }
    val shown = exams.filter { selectedExam == null || it.first.first == selectedExam }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { MarksStudentHeader(student, rows) }
            if (exams.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = selectedExam == null, onClick = { selectedExam = null }, label = { Text("All Exam Types") })
                        }
                        items(exams, key = { it.first.first }) { (exam, _) ->
                            FilterChip(
                                selected = selectedExam == exam.first,
                                onClick = { selectedExam = exam.first },
                                label = { Text(exam.second) }
                            )
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item { ListCard { EmptyText("No marks found for this student") } }
            }
            items(shown, key = { it.first.first }) { (exam, subjects) ->
                ExamMarksCard(examName = exam.second, subjects = subjects, onMarksheet = { onMarksheet(exam.first) })
            }
        }
    }
}

@Composable
private fun MarksStudentHeader(student: StudentListItem, rows: List<StudentMarkRow>) {
    val total = rows.sumOf { it.total }
    val max = rows.sumOf { it.max }
    val percent = percentOf(total, max)
    val passed = rows.count { it.isPass }
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initialsOf(student.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(student.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Adm No ${student.admissionNo} • ${classDisplayName(student.classType)}-${student.section}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderStat("Overall", "$percent%", percentColor(percent), Modifier.weight(1f))
                    HeaderStat("Exams", rows.map { it.examTypeId }.distinct().size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    HeaderStat("Passed", "$passed/${rows.size}", if (passed == rows.size) SuccessGreen else ErrorRed, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
private fun ExamMarksCard(examName: String, subjects: List<StudentMarkRow>, onMarksheet: () -> Unit) {
    val total = subjects.sumOf { it.total }
    val max = subjects.sumOf { it.max }
    val percent = percentOf(total, max)
    val failed = subjects.count { !it.isPass }

    ListCard {
        Column(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(examName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatMarks(total)} / ${formatMarks(max)} • $percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    if (failed == 0) "PASS" else "$failed FAIL",
                    if (failed == 0) SuccessGreen else ErrorRed
                )
            }
            Spacer(Modifier.height(10.dp))
            // Column headings
            Row(Modifier.padding(horizontal = 16.dp)) {
                ColumnHead("Subject", Modifier.weight(1.4f))
                ColumnHead("Written", Modifier.weight(1f))
                ColumnHead("Practical", Modifier.weight(1f))
                ColumnHead("Total", Modifier.weight(1f))
            }
            subjects.forEach { row ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1.4f)) {
                        Text(row.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (row.isPass) "PASS" else "FAIL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (row.isPass) SuccessGreen else ErrorRed
                        )
                    }
                    MarksCell("${formatMarks(row.writtenObtained)}/${formatMarks(row.writtenMax)}", Modifier.weight(1f))
                    MarksCell(
                        if (row.practicalMax > 0) "${formatMarks(row.practicalObtained)}/${formatMarks(row.practicalMax)}" else "—",
                        Modifier.weight(1f)
                    )
                    Text(
                        "${formatMarks(row.total)}/${formatMarks(row.max)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = percentColor(percentOf(row.total, row.max)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TextButton(onClick = onMarksheet, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text("View Marksheet")
            }
        }
    }
}

@Composable
private fun ColumnHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun MarksCell(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
