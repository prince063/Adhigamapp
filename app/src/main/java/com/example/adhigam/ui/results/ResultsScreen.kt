package com.example.adhigam.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.ExamType
import com.example.adhigam.data.model.StudentMarkRow
import com.example.adhigam.data.model.StudentResults
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.staff.LoadStateContent
import com.example.adhigam.ui.staff.LoadViewModel
import com.example.adhigam.ui.staff.formatMarks
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen

class ResultsViewModel : LoadViewModel<StudentResults>({ AppContainer.resultsLabRepository.getResults() })

// Categorical slots 1–5 of the validated reference palette (blue, orange, aqua, yellow, magenta),
// light and dark steps. Assigned to subjects in a fixed order, never cycled.
private val SeriesLight = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4))
private val SeriesDark = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))

private const val PASS_PERCENT = 33

private fun percentOf(rows: List<StudentMarkRow>): Int {
    val max = rows.sumOf { it.max }
    return if (max > 0) Math.round(rows.sumOf { it.total } * 100 / max).toInt() else 0
}

// Mirrors the parent portal's "Academics & Performance": term marksheet results for declared exams
// and the subject progression chart.
@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel = viewModel { ResultsViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { results ->
        ResultsContent(results, isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsContent(
    results: StudentResults,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val declared = results.examTypes.filter { it.isResultDeclared && results.marks.any { m -> m.examTypeId == it.id } }
    // Latest declared exam is shown first; the chart and the chips share the selection.
    var selectedId by rememberSaveable { mutableStateOf(declared.lastOrNull()?.id) }
    val selectedRows = results.marks.filter { it.examTypeId == selectedId }
    val selectedExam = results.examTypes.firstOrNull { it.id == selectedId }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Term Marksheet Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ExamChips(results.examTypes, declared, selectedId, onSelect = { selectedId = it })
            }
            item {
                if (selectedExam == null || selectedRows.isEmpty()) {
                    ListCard { EmptyText("Results marksheet is currently not declared.") }
                } else {
                    ResultCard(selectedExam, selectedRows)
                }
            }
            if (declared.isNotEmpty()) {
                item {
                    ProgressionCard(
                        exams = declared,
                        marks = results.marks,
                        selectedId = selectedId,
                        onSelect = { selectedId = it }
                    )
                }
            }
        }
    }
}

// Every exam is listed; ones not yet declared are disabled and marked "(Pending)", like the web dropdown.
@Composable
private fun ExamChips(all: List<ExamType>, declared: List<ExamType>, selectedId: Int?, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(all, key = { it.id }) { exam ->
            val available = exam in declared
            FilterChip(
                selected = exam.id == selectedId,
                onClick = { onSelect(exam.id) },
                enabled = available,
                label = { Text(if (available) exam.name else "${exam.name} (Pending)") }
            )
        }
    }
}

@Composable
private fun ResultCard(exam: ExamType, rows: List<StudentMarkRow>) {
    val total = rows.sumOf { it.total }
    val max = rows.sumOf { it.max }
    val percent = if (max > 0) total * 100 / max else 0.0
    val pass = percent >= PASS_PERCENT
    ListCard {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Grand total ${formatMarks(total)}/${formatMarks(max)} • Average ${"%.1f".format(percent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(if (pass) "PASS" else "FAIL", if (pass) SuccessGreen else ErrorRed)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                Head("Subject", Modifier.weight(1.4f))
                Head("Written", Modifier.weight(1f))
                Head("Practical", Modifier.weight(1f))
                Head("Total", Modifier.weight(0.8f))
                Head("Result", Modifier.weight(0.8f))
            }
            rows.forEach { row ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.subject,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.4f)
                    )
                    Text("${formatMarks(row.writtenObtained)}/${formatMarks(row.writtenMax)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (row.practicalMax > 0) "${formatMarks(row.practicalObtained)}/${formatMarks(row.practicalMax)}" else "—",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(formatMarks(row.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                    Text(
                        if (row.isPass) "Pass" else "Fail",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.isPass) SuccessGreen else ErrorRed,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Head(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun ProgressionCard(exams: List<ExamType>, marks: List<StudentMarkRow>, selectedId: Int?, onSelect: (Int) -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = if (isDark) SeriesDark else SeriesLight
    // Subjects in a fixed order so each keeps its colour whatever exam is selected.
    val subjects = marks.map { it.subject }.distinct().take(palette.size)
    val series = subjects.mapIndexed { i, subject ->
        Triple(subject, palette[i], exams.map { exam -> marks.filter { it.examTypeId == exam.id && it.subject == subject }.takeIf { it.isNotEmpty() }?.let(::percentOf) })
    }
    val selectedIndex = exams.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    ListCard {
        Column(Modifier.padding(16.dp)) {
            Text("Subject Grade Progression", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Percentage per subject across declared exams • tap an exam to see its values",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            ProgressionChart(
                examNames = exams.map { it.name },
                series = series,
                selectedIndex = selectedIndex,
                onSelect = { onSelect(exams[it].id) }
            )
            Spacer(Modifier.height(12.dp))
            // Legend with the selected exam's value next to each subject, so identity never rests on colour alone.
            Text(
                exams.getOrNull(selectedIndex)?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                series.forEach { (subject, color, values) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(subject, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            values.getOrNull(selectedIndex)?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressionChart(
    examNames: List<String>,
    series: List<Triple<String, Color, List<Int?>>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val passColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val strongLabel = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val crosshair = MaterialTheme.colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val selectedStyle = axisStyle.copy(color = strongLabel, fontWeight = FontWeight.Bold)
    val description = series.joinToString { (subject, _, values) ->
        "$subject " + values.mapIndexed { i, v -> "${examNames[i]} ${v ?: "no marks"}%" }.joinToString()
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = "Subject progression: $description" }
            .pointerInput(examNames) {
                detectTapGestures { offset ->
                    val left = 36.dp.toPx()
                    val right = 16.dp.toPx()
                    val count = examNames.size
                    if (count == 0) return@detectTapGestures
                    val step = if (count > 1) (size.width - left - right) / (count - 1) else 0f
                    val index = if (count == 1) 0 else Math.round((offset.x - left) / step).coerceIn(0, count - 1)
                    onSelect(index)
                }
            }
    ) {
        val left = 36.dp.toPx()
        val right = 16.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val plotHeight = bottom - top
        val count = examNames.size
        val step = if (count > 1) (size.width - left - right) / (count - 1) else 0f
        fun x(i: Int) = if (count == 1) (left + size.width - right) / 2 else left + step * i
        fun y(percent: Int) = bottom - plotHeight * percent / 100f

        // Recessive gridlines at 0 / 50 / 100%.
        listOf(0, 50, 100).forEach { p ->
            drawLine(gridColor, Offset(left, y(p)), Offset(size.width - right, y(p)), strokeWidth = 1.dp.toPx())
            val label = textMeasurer.measure("$p%", axisStyle)
            drawText(label, topLeft = Offset(0f, y(p) - label.size.height / 2f))
        }
        // Pass mark.
        drawLine(
            passColor,
            Offset(left, y(PASS_PERCENT)),
            Offset(size.width - right, y(PASS_PERCENT)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
        val passLabel = textMeasurer.measure("Pass $PASS_PERCENT%", axisStyle.copy(color = passColor))
        drawText(passLabel, topLeft = Offset(size.width - right - passLabel.size.width, y(PASS_PERCENT) - passLabel.size.height - 2.dp.toPx()))

        // Crosshair on the selected exam.
        if (count > 0) {
            drawLine(crosshair, Offset(x(selectedIndex), top), Offset(x(selectedIndex), bottom), strokeWidth = 1.dp.toPx())
        }

        // 2dp lines with ≥8dp markers ringed in the surface colour so overlapping points stay readable.
        series.forEach { (_, color, values) ->
            val path = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v == null) {
                    started = false
                    return@forEachIndexed
                }
                if (!started) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
                started = true
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            values.forEachIndexed { i, v ->
                if (v != null) {
                    drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x(i), y(v)))
                    drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
                }
            }
        }

        // Exam names along the bottom.
        examNames.forEachIndexed { i, name ->
            val label = textMeasurer.measure(name, if (i == selectedIndex) selectedStyle else axisStyle)
            val lx = (x(i) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(lx, bottom + 6.dp.toPx()))
        }
    }
}
