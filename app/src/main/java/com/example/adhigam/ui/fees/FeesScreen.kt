package com.example.adhigam.ui.fees

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.FeeInstallment
import com.example.adhigam.data.model.FeeMonth
import com.example.adhigam.data.model.FeePayment
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.InstallmentStatus
import com.example.adhigam.data.model.PaymentMode
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.Indigo700
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import com.example.adhigam.ui.util.formatRupees
import java.time.LocalDate

private val HeaderDark = Color(0xFF1E1B4B)

// Mirrors the "Finance & Fees" tab of the web parent portal:
// summary, month-wise installments (pick an unpaid one to pay) and the invoice log.
@Composable
fun FeesScreen(
    showMessage: (String) -> Unit,
    onOpenReceipt: (invoiceNo: String, session: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeesViewModel = viewModel { FeesViewModel(AppContainer.studentRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.fees == null -> ErrorState(
            message = state.error ?: "Could not load fee details",
            onRetry = viewModel::refresh,
            modifier = modifier
        )
        else -> FeesContent(
            fees = state.fees!!,
            selectedMonth = state.selectedMonth,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onSelectMonth = viewModel::toggleMonth,
            onPay = { viewModel.openPayment() },
            onReceipt = { onOpenReceipt(it.invoiceNo, it.session) },
            modifier = modifier
        )
    }

    state.payingMonth?.let { month ->
        MockPaymentDialog(
            month = month,
            mode = state.payMode,
            isPaying = state.isPaying,
            error = state.payError,
            onModeChange = viewModel::setPayMode,
            onConfirm = { viewModel.confirmPayment(onPaid = showMessage) },
            onDismiss = viewModel::dismissPayment
        )
    }
}

// The web portal's "Secure Fee Portal Mock Payment Gateway" modal: there is no real gateway, confirming
// calls /student/pay-fee which marks the month as paid.
@Composable
private fun MockPaymentDialog(
    month: FeeMonth,
    mode: PaymentMode,
    isPaying: Boolean,
    error: String?,
    onModeChange: (PaymentMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mock Payment Gateway") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Installment: ${month.month}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatRupees(month.balance),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "SCAN QR TO SIMULATE CHECKOUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Indigo700, HeaderDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "MOCK UPI QR",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("UPI ID: school@upi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Payment method",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnlinePaymentModes.forEach {
                        FilterChip(
                            selected = it == mode,
                            onClick = { onModeChange(it) },
                            enabled = !isPaying,
                            label = { Text(it.label) }
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isPaying) {
                if (isPaying) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying…")
                } else {
                    Text("Simulate payment")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPaying) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeesContent(
    fees: FeeSummary,
    selectedMonth: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onPay: (FeeMonth) -> Unit,
    onReceipt: (FeePayment) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = fees.months.firstOrNull { it.month == selectedMonth }

    Column(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { SummaryCard(fees) }
                item {
                    SectionHeader(
                        title = "Fee installments",
                        subtitle = if (fees.pendingMonths.isEmpty()) "All installments paid"
                        else "Tap an unpaid month to pay • ${fees.pendingMonths.size} pending"
                    )
                    Spacer(Modifier.height(8.dp))
                    InstallmentsCard(fees.months, selectedMonth, onSelectMonth)
                }
                item {
                    SectionHeader(title = "Payment history", subtitle = "${fees.payments.size} transactions")
                    Spacer(Modifier.height(8.dp))
                    PaymentsCard(fees.payments, onReceipt)
                }
            }
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            // Keep the last selection on screen while the bar slides out.
            selected?.let { PayBar(it, onPay = { onPay(it) }) }
        }
    }
}

@Composable
private fun SummaryCard(fees: FeeSummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeaderDark, Indigo700)))
            .padding(20.dp)
    ) {
        Column {
            Text("Outstanding balance", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            Text(
                if (fees.balance > 0) formatRupees(fees.balance) else "All paid",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { fees.paidFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${Math.round(fees.paidFraction * 100)}% of session fees paid",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStat("Total fees", formatRupees(fees.totalDue), Modifier.weight(1f))
                SummaryStat("Paid", formatRupees(fees.totalPaid), Modifier.weight(1f))
                if (fees.totalConcession > 0) {
                    SummaryStat("Concession", formatRupees(fees.totalConcession), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun statusColor(status: InstallmentStatus): Color = when (status) {
    InstallmentStatus.Paid -> SuccessGreen
    InstallmentStatus.Partial -> Amber600
    InstallmentStatus.Unpaid -> ErrorRed
}

@Composable
private fun InstallmentsCard(months: List<FeeMonth>, selectedMonth: String?, onSelect: (String) -> Unit) {
    ListCard {
        if (months.isEmpty()) {
            EmptyText("No installments recorded")
        }
        months.forEachIndexed { index, month ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val payable = month.status != InstallmentStatus.Paid
            val isSelected = month.month == selectedMonth
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (payable) Modifier.clickable { onSelect(month.month) } else Modifier)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    !payable -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    isSelected -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    else -> Icon(
                        Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(month.month, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        month.items.joinToString(" • ") { "${it.feeName} ${formatRupees(it.amountDue)}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (month.status == InstallmentStatus.Partial) {
                        Text(
                            "Paid ${formatRupees(month.amountPaid)} of ${formatRupees(month.amountDue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber600
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatRupees(if (payable) month.balance else month.amountDue),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusPill(month.status.label, statusColor(month.status))
                }
            }
        }
    }
}

@Composable
private fun PaymentsCard(payments: List<FeePayment>, onReceipt: (FeePayment) -> Unit) {
    ListCard {
        if (payments.isEmpty()) {
            EmptyText("No invoices logged")
        }
        payments.forEachIndexed { index, payment ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Receipt, contentDescription = null, tint = SuccessGreen)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("#INV-${payment.invoiceNo}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatFullDate(payment.paidDate)} • ${payment.paymentMode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatRupees(payment.amount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
                IconButton(onClick = { onReceipt(payment) }) {
                    Icon(Icons.Outlined.Receipt, contentDescription = "View receipt", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PayBar(month: FeeMonth, onPay: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Selected installment: ${month.month}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Due ${formatRupees(month.balance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = onPay) { Text("Proceed to pay") }
        }
    }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun FeesContentPreview() {
    // Preview-only sample; the app loads /student/fee-ledger/self/me.
    val fees = FeeSummary(
        totalDue = 6000, totalPaid = 3500, balance = 2500,
        installments = listOf(
            FeeInstallment("April", "Tuition Fee", 3000, 3000, isPaid = true),
            FeeInstallment("May", "Tuition Fee", 3000, 500, isPaid = false)
        ),
        payments = listOf(FeePayment("1", LocalDate.now().minusDays(10), "Cash", 3500, "2026-27", null))
    )
    AdhigamTheme {
        FeesContent(
            fees = fees,
            selectedMonth = "October",
            isRefreshing = false,
            onRefresh = {},
            onSelectMonth = {},
            onPay = {},
            onReceipt = {}
        )
    }
}
