package com.example.adhigam.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.FeeHeadKey
import com.example.adhigam.data.model.FeeMonth
import com.example.adhigam.data.model.FeePayment
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.InstallmentStatus
import com.example.adhigam.data.model.PaymentMode
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.StudentListItem
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.SectionTitle
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import com.example.adhigam.ui.util.formatRupees

@Composable
fun FeeCollectionScreen(
    onComingSoon: (String) -> Unit,
    onOpenReceipt: (studentId: String, invoiceNo: String, session: String) -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialStudentId: String? = null,
    viewModel: FeeCollectionViewModel = viewModel { FeeCollectionViewModel(AppContainer.feeCollectionRepository, initialStudentId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val student = state.student
        if (student == null && state.isLoadingFees) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (student == null) {
            item { FindStudentCard(state, viewModel) }
            items(state.results, key = { it.encryptedId }) { result ->
                SearchResultRow(result, onClick = { viewModel.selectStudent(result) })
            }
            if (state.results.isEmpty()) {
                item {
                    ListCard {
                        EmptyText("Search and select a student to view and collect their fees.")
                    }
                }
            }
        } else {
            item { StudentDetailsCard(student, onChange = viewModel::changeStudent) }
            val fees = state.fees
            when {
                state.isLoadingFees && fees == null -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                state.feesError != null && fees == null -> item {
                    Box(Modifier.height(240.dp)) { ErrorState(state.feesError!!, onRetry = viewModel::loadFees) }
                }
                fees != null -> {
                    item { FeeSummaryCard(fees) }
                    if (fees.pendingMonths.isNotEmpty()) {
                        item { OutstandingDues(fees, onPay = viewModel::openPay) }
                    }
                    item {
                        SectionTitle(
                            "Fees by month",
                            actionLabel = if (fees.balance > 0) "Bulk Pay" else null,
                            onAction = { viewModel.openPay(null) }
                        )
                    }
                    items(fees.months, key = { it.month }) { month ->
                        MonthFeeCard(
                            month = month,
                            expanded = month.month in state.expandedMonths,
                            onToggle = { viewModel.toggleMonth(month.month) },
                            onCollect = { viewModel.openPay(month.month) }
                        )
                    }
                    item {
                        SectionTitle("Past Collections")
                        Spacer(Modifier.height(8.dp))
                        PastCollections(fees.payments.take(6), onReceipt = { onOpenReceipt(student.encryptedId, it.invoiceNo, it.session) })
                    }
                }
            }
        }
    }

    state.pay?.let { pay ->
        PaySheet(
            pay = pay,
            outstanding = state.fees?.balance ?: 0,
            onDismiss = viewModel::dismissPay,
            onToggleHead = viewModel::toggleHead,
            onAmount = viewModel::setAmount,
            onChange = viewModel::updatePayForm,
            onSubmit = viewModel::submitPayment
        )
    }
}

@Composable
private fun FindStudentCard(state: FeeCollectionUiState, viewModel: FeeCollectionViewModel) {
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Find Student", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = "Class",
                    placeholder = "All Classes",
                    value = state.classType?.let(::classDisplayName),
                    options = listOf<String?>(null) + SchoolClasses,
                    optionLabel = { it?.let(::classDisplayName) ?: "All Classes" },
                    onSelect = viewModel::setClass,
                    modifier = Modifier.weight(1.3f)
                )
                SelectField(
                    label = "Section",
                    placeholder = "All Sections",
                    value = state.section,
                    options = listOf<String?>(null) + SchoolSections,
                    optionLabel = { it ?: "All Sections" },
                    onSelect = viewModel::setSection,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name, mobile or admission no…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (state.isSearching) {
                    { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            if (state.searchHint.isNotEmpty()) {
                Text(state.searchHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun studentStatusColor(status: StudentStatus): Color = when (status) {
    StudentStatus.Approved -> SuccessGreen
    StudentStatus.Pending -> Amber600
    StudentStatus.Rejected -> ErrorRed
}

@Composable
private fun SearchResultRow(student: StudentListItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(student.name, size = 38)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    student.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Adm# ${student.admissionNo} • ${classDisplayName(student.classType)} ${student.section} • ${student.mobile}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (student.dueFee > 0) {
                Spacer(Modifier.width(8.dp))
                Text(formatRupees(student.dueFee), style = MaterialTheme.typography.labelLarge, color = ErrorRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name),
            style = if (size > 44) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StudentDetailsCard(student: StudentListItem, onChange: () -> Unit) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(student.name, size = 52)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(student.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${classDisplayName(student.classType)} – ${student.section}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusPill(student.status.name, studentStatusColor(student.status))
                }
                TextButton(onClick = onChange) { Text("Change") }
            }
            Spacer(Modifier.height(12.dp))
            DetailRow("Admission No", student.admissionNo)
            DetailRow("Mobile", student.mobile)
            DetailRow("Father", student.fatherName ?: "—")
            DetailRow("Fee Group", student.feeGroup ?: "—")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FeeSummaryCard(fees: FeeSummary) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Text("Fee Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryBox("Total Due", fees.totalDue, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                SummaryBox("Paid", fees.totalPaid, SuccessGreen, Modifier.weight(1f))
                SummaryBox("Balance", fees.balance, ErrorRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(formatRupees(amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Unpaid months as chips; tapping one opens the pay sheet for that month.
@Composable
private fun OutstandingDues(fees: FeeSummary, onPay: (String) -> Unit) {
    Column {
        SectionTitle("Outstanding Dues")
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fees.pendingMonths, key = { it.month }) { month ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ErrorRed.copy(alpha = 0.08f))
                        .border(1.dp, ErrorRed.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { onPay(month.month) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(month.month, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(formatRupees(month.balance), style = MaterialTheme.typography.labelMedium, color = ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun installmentColor(status: InstallmentStatus): Color = when (status) {
    InstallmentStatus.Paid -> SuccessGreen
    InstallmentStatus.Partial -> Amber600
    InstallmentStatus.Unpaid -> ErrorRed
}

@Composable
private fun MonthFeeCard(month: FeeMonth, expanded: Boolean, onToggle: () -> Unit, onCollect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(month.month, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        StatusPill(month.status.label, installmentColor(month.status))
                    }
                    Text(
                        if (month.status == InstallmentStatus.Paid) "Paid ${formatRupees(month.amountPaid)}"
                        else "Balance ${formatRupees(month.balance)} of ${formatRupees(month.amountDue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (month.status != InstallmentStatus.Paid) {
                    FilledTonalButton(onClick = onCollect, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Text("Collect")
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide fee heads" else "Show fee heads",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    month.items.forEach { head ->
                        Row {
                            Text(head.feeName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (head.isPaid) "${formatRupees(head.amountDue)} paid"
                                else "${formatRupees(head.balance)} due",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (head.isPaid) SuccessGreen else ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastCollections(payments: List<FeePayment>, onReceipt: (FeePayment) -> Unit) {
    ListCard {
        if (payments.isEmpty()) {
            EmptyText("No payments recorded yet.")
        }
        payments.forEachIndexed { index, payment ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(payment.paymentMode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatFullDate(payment.paidDate)} • #${payment.invoiceNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("+${formatRupees(payment.amount)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                IconButton(onClick = { onReceipt(payment) }) {
                    Icon(Icons.Outlined.Receipt, contentDescription = "Print receipt", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PaySheet(
    pay: PayFormState,
    outstanding: Long,
    onDismiss: () -> Unit,
    onToggleHead: (FeeHeadKey) -> Unit,
    onAmount: (String) -> Unit,
    onChange: ((PayFormState) -> PayFormState) -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Collect Fee — ${pay.month ?: "Multiple Months"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Fee heads; ticking one re-totals the amount.
            Column {
                Text("Select Fee Heads to Pay", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                pay.heads.forEach { head ->
                    val key = FeeHeadKey(head.month, head.feeName)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleHead(key) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = key in pay.selected, onCheckedChange = { onToggleHead(key) })
                        Text(
                            (if (pay.month == null) "[${head.month}] " else "") +
                                "${head.feeName} (Due: ${formatRupees(head.balance)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Column {
                OutlinedTextField(
                    value = pay.amount,
                    onValueChange = onAmount,
                    label = { Text("Amount to Collect *") },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selected: ${formatRupees(pay.selectedBalance)} • Total outstanding: ${formatRupees(outstanding)}. " +
                        "A smaller amount is recorded as a partial payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text("Payment Mode *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMode.entries.forEach { mode ->
                        FilterChip(
                            selected = pay.mode == mode,
                            onClick = { onChange { it.copy(mode = mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            // Extra fields per mode, as on the web form.
            val mode = pay.mode
            if (mode == PaymentMode.NetBanking || mode == PaymentMode.Cheque || mode == PaymentMode.Card) {
                PayTextField("Bank Name", pay.bankName, placeholder = "e.g. SBI") { v -> onChange { it.copy(bankName = v) } }
            }
            if (mode == PaymentMode.NetBanking) {
                SelectField(
                    label = "Transfer Type",
                    placeholder = "Select",
                    value = pay.netbankingType.ifBlank { null },
                    options = listOf("NEFT", "RTGS", "IMPS"),
                    optionLabel = { it },
                    onSelect = { v -> onChange { it.copy(netbankingType = v) } }
                )
            }
            if (mode == PaymentMode.Cheque) {
                PayTextField("Cheque No.", pay.chequeNo) { v -> onChange { it.copy(chequeNo = v) } }
                PayTextField("Account No.", pay.accountNo) { v -> onChange { it.copy(accountNo = v) } }
            }
            if (mode == PaymentMode.UPI) {
                PayTextField("UPI ID / Number", pay.upiId, placeholder = "e.g. 9876543210@upi") { v -> onChange { it.copy(upiId = v) } }
            }
            if (mode == PaymentMode.Card) {
                SelectField(
                    label = "Card Type",
                    placeholder = "Select",
                    value = pay.cardType.ifBlank { null },
                    options = listOf("Visa", "Mastercard", "RuPay", "Amex"),
                    optionLabel = { it },
                    onSelect = { v -> onChange { it.copy(cardType = v) } }
                )
                PayTextField("Name of Payee", pay.payeeName) { v -> onChange { it.copy(payeeName = v) } }
            }
            PayTextField("Transaction / Ref ID", pay.transactionId, placeholder = "Optional") { v -> onChange { it.copy(transactionId = v) } }
            PayTextField("Remarks", pay.remarks, placeholder = "Optional note…") { v -> onChange { it.copy(remarks = v) } }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !pay.isSubmitting, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = onSubmit,
                    enabled = !pay.isSubmitting,
                    modifier = Modifier
                        .weight(2f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (pay.isSubmitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Processing…")
                    } else {
                        Text("Record Payment", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PayTextField(label: String, value: String, placeholder: String? = null, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}
