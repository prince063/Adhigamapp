package com.example.adhigam.ui.staff

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.R
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Marksheet
import com.example.adhigam.data.model.classDisplayName

class MarksheetViewModel(encryptedId: String, examTypeId: Int) :
    LoadViewModel<Marksheet>({ AppContainer.studentMarksRepository.getMarksheet(encryptedId, examTypeId) })

// Printed-paper colours, same as the fee receipt.
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0F172A)
private val InkMuted = Color(0xFF64748B)
private val RuleColor = Color(0xFFE2E8F0)
private val Brand = Color(0xFF1E3FA5)
private val PassGreen = Color(0xFF16A34A)
private val FailRed = Color(0xFFDC2626)

// Mirrors marksheet-preview.html: one student's report for one exam.
@Composable
fun MarksheetScreen(
    encryptedId: String,
    examTypeId: Int,
    modifier: Modifier = Modifier,
    viewModel: MarksheetViewModel = viewModel(key = "marksheet-$encryptedId-$examTypeId") {
        MarksheetViewModel(encryptedId, examTypeId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { sheet ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { MarksheetPaper(sheet) }
            item {
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Marksheet – ${sheet.studentName} – ${sheet.examName}")
                            putExtra(Intent.EXTRA_TEXT, marksheetText(sheet))
                        }
                        context.startActivity(Intent.createChooser(send, "Share marksheet"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Marksheet", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MarksheetPaper(sheet: Marksheet) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Brand)
        )
        Column(Modifier.padding(20.dp)) {
            // Header
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.school_logo),
                    contentDescription = "School logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(1.dp, RuleColor, CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                if (sheet.schoolName.isNotBlank()) {
                    Text(sheet.schoolName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                if (sheet.schoolAddress.isNotBlank()) {
                    Text(sheet.schoolAddress, color = InkMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "MARKSHEET / PROGRESS REPORT",
                    color = Brand,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text("Academic Performance Report • ${sheet.examName}", color = InkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = RuleColor)
            Spacer(Modifier.height(12.dp))

            // Student info, two per row
            val info = listOf(
                "Student Name" to sheet.studentName,
                "Father Name" to (sheet.fatherName ?: "—"),
                "Admission No" to sheet.admissionNo,
                "Class" to classDisplayName(sheet.classType),
                "Section" to sheet.section,
                "Academic Year" to sheet.academicYear
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                info.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(label, color = InkMuted, fontSize = 11.sp)
                                Text(
                                    value,
                                    color = Ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = if (label == "Admission No") FontFamily.Monospace else null
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            MarksTable(sheet)

            sheet.remarks?.let {
                Spacer(Modifier.height(14.dp))
                Row {
                    Text("Remarks: ", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(it, color = Ink, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(40.dp))
            Row {
                Signature("Class Teacher", Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
                Signature("Principal", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarksTable(sheet: Marksheet) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, RuleColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Head("#", Modifier.width(20.dp))
            Head("Subject", Modifier.weight(1.4f))
            Head("Written", Modifier.weight(1f), TextAlign.Center)
            Head("Practical", Modifier.weight(1f), TextAlign.Center)
            Head("Total", Modifier.weight(1f), TextAlign.Center)
            Head("Result", Modifier.weight(0.9f), TextAlign.End)
        }
        sheet.rows.forEachIndexed { i, row ->
            HorizontalDivider(color = RuleColor)
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cell("${i + 1}", Modifier.width(20.dp), color = InkMuted)
                Text(
                    row.subject,
                    color = Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.4f)
                )
                Cell("${formatMarks(row.writtenObtained)}/${formatMarks(row.writtenMax)}", Modifier.weight(1f), TextAlign.Center)
                Cell(
                    if (row.practicalMax > 0) "${formatMarks(row.practicalObtained)}/${formatMarks(row.practicalMax)}" else "—",
                    Modifier.weight(1f),
                    TextAlign.Center
                )
                Cell(
                    "${formatMarks(row.total)}/${formatMarks(row.max)}",
                    Modifier.weight(1f),
                    TextAlign.Center,
                    bold = true
                )
                Cell(
                    if (row.isPass) "PASS" else "FAIL",
                    Modifier.weight(0.9f),
                    TextAlign.End,
                    color = if (row.isPass) PassGreen else FailRed,
                    bold = true
                )
            }
        }
        HorizontalDivider(color = RuleColor)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Grand Total / Percentage", color = InkMuted, fontSize = 11.sp)
                Text(
                    "${formatMarks(sheet.grandTotal)} / ${formatMarks(sheet.grandMax)} (${"%.1f".format(sheet.percent)}%)",
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (sheet.isPass) "PASS" else "FAILED",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sheet.isPass) PassGreen else FailRed)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun Head(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = align, modifier = modifier)
}

@Composable
private fun Cell(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    color: Color = Ink,
    bold: Boolean = false
) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        modifier = modifier
    )
}

@Composable
private fun Signature(label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = InkMuted)
        Spacer(Modifier.height(4.dp))
        Text(label, color = InkMuted, fontSize = 11.sp)
    }
}

private fun marksheetText(s: Marksheet): String = buildString {
    if (s.schoolName.isNotBlank()) appendLine(s.schoolName)
    appendLine("Marksheet – ${s.examName} (${s.academicYear})")
    appendLine()
    appendLine("${s.studentName} • Adm No ${s.admissionNo} • ${classDisplayName(s.classType)}-${s.section}")
    appendLine()
    s.rows.forEach {
        appendLine("${it.subject}: ${formatMarks(it.total)}/${formatMarks(it.max)} ${if (it.isPass) "PASS" else "FAIL"}")
    }
    appendLine()
    appendLine("Grand Total: ${formatMarks(s.grandTotal)}/${formatMarks(s.grandMax)} (${"%.1f".format(s.percent)}%)")
    append("Result: ${if (s.isPass) "PASS" else "FAILED"}")
    s.remarks?.let { append("\nRemarks: $it") }
}
