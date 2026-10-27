package com.example.adhigam.ui.receipt

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.R
import com.example.adhigam.data.model.FeeReceipt
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.staff.LoadStateContent
import com.example.adhigam.ui.staff.LoadViewModel
import com.example.adhigam.ui.util.formatFullDate
import com.example.adhigam.ui.util.formatRupees

class ReceiptViewModel(loader: suspend () -> FeeReceipt) : LoadViewModel<FeeReceipt>(loader)

// Paper colours: a receipt looks like printed paper in both light and dark theme.
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0F172A)
private val InkMuted = Color(0xFF64748B)
private val RuleColor = Color(0xFFE2E8F0)
private val Brand = Color(0xFF1E3FA5)
private val PaidGreen = Color(0xFF16A34A)

// Mirrors payment-receipt.html. key identifies the receipt (e.g. student + invoice) so each one
// gets its own ViewModel.
@Composable
fun ReceiptScreen(
    key: String,
    loader: suspend () -> FeeReceipt,
    modifier: Modifier = Modifier,
    viewModel: ReceiptViewModel = viewModel(key = "receipt-$key") { ReceiptViewModel(loader) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { receipt ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ReceiptPaper(receipt) }
            item {
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Fee receipt #${receipt.invoiceNo}")
                            putExtra(Intent.EXTRA_TEXT, receiptText(receipt))
                        }
                        context.startActivity(Intent.createChooser(send, "Share receipt"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Receipt", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ReceiptPaper(receipt: FeeReceipt) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
    ) {
        Column {
            // Brand strip along the top, like the web receipt.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Brand)
            )
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.school_logo),
                        contentDescription = "School logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.dp, RuleColor, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(receipt.schoolName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(receipt.schoolAddress, color = InkMuted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Fee Payment Receipt",
                        color = Brand,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Parent Copy", color = InkMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = RuleColor)
                Spacer(Modifier.height(12.dp))

                InfoGrid(
                    listOf(
                        "Student Name" to receipt.studentName,
                        "Admission No" to receipt.admissionNo,
                        "Class / Section" to "${classDisplayName(receipt.classType)} / ${receipt.section}",
                        "Father Name" to (receipt.fatherName ?: "—"),
                        "Invoice No" to receipt.invoiceNo,
                        "Session" to receipt.session,
                        "Paid Date" to formatFullDate(receipt.paidDate),
                        "Payment Mode" to receipt.paymentMode
                    )
                )

                Spacer(Modifier.height(16.dp))
                ItemsTable(receipt)

                if (!receipt.transactionId.isNullOrBlank() || !receipt.remarks.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    receipt.transactionId?.takeIf { it.isNotBlank() }?.let {
                        Text("Txn: $it", color = InkMuted, fontSize = 12.sp)
                    }
                    receipt.remarks?.takeIf { it.isNotBlank() }?.let {
                        Text("Remarks: $it", color = InkMuted, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(36.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "Thank you for your payment!",
                        color = PaidGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HorizontalDivider(Modifier.width(120.dp), color = InkMuted)
                        Spacer(Modifier.height(4.dp))
                        Text("Authorised Signature", color = InkMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        // Faint "PAID" stamp across the receipt, as on the web.
        Text(
            "PAID",
            color = PaidGreen.copy(alpha = 0.08f),
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(-25f)
        )
    }
}

@Composable
private fun InfoGrid(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = InkMuted, fontSize = 11.sp)
                        Text(
                            value,
                            color = if (label == "Invoice No") Brand else Ink,
                            fontSize = 14.sp,
                            fontWeight = if (label == "Invoice No") FontWeight.Bold else FontWeight.SemiBold,
                            fontFamily = if (label == "Invoice No") FontFamily.Monospace else null
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ItemsTable(receipt: FeeReceipt) {
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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TableHead("#", Modifier.width(24.dp))
            TableHead("Fee Name", Modifier.weight(1f))
            TableHead("Month", Modifier.weight(0.7f))
            TableHead("Amount", Modifier.weight(0.7f), TextAlign.End)
        }
        if (receipt.items.isEmpty()) {
            Text(
                "Payment of ${formatRupees(receipt.total)}",
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        receipt.items.forEachIndexed { i, item ->
            HorizontalDivider(color = RuleColor)
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", color = InkMuted, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.feeName, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(item.feeGroup, color = InkMuted, fontSize = 11.sp)
                }
                Text(item.month, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(0.7f))
                Text(
                    formatRupees(item.amount),
                    color = Ink,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.7f)
                )
            }
        }
        HorizontalDivider(color = RuleColor)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("Total Paid", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(formatRupees(receipt.total), color = PaidGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TableHead(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = align, modifier = modifier)
}

// Plain-text version for sharing on WhatsApp, SMS or email.
private fun receiptText(r: FeeReceipt): String = buildString {
    appendLine(r.schoolName)
    appendLine("Fee Payment Receipt")
    appendLine()
    appendLine("Invoice No: ${r.invoiceNo}")
    appendLine("Student: ${r.studentName} (Adm No ${r.admissionNo})")
    appendLine("Class / Section: ${classDisplayName(r.classType)} / ${r.section}")
    appendLine("Paid on: ${formatFullDate(r.paidDate)} • ${r.paymentMode}")
    appendLine("Session: ${r.session}")
    appendLine()
    r.items.forEach { appendLine("${it.feeName} (${it.month}): ${formatRupees(it.amount)}") }
    appendLine("Total Paid: ${formatRupees(r.total)}")
    r.transactionId?.takeIf { it.isNotBlank() }?.let { appendLine("Txn: $it") }
    appendLine()
    append("Thank you for your payment!")
}
