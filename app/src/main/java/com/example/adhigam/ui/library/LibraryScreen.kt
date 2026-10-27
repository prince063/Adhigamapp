package com.example.adhigam.ui.library

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.adhigam.data.model.LibraryBook
import com.example.adhigam.data.model.LibraryCheckout
import com.example.adhigam.data.model.LibraryReservation
import com.example.adhigam.data.model.MyLibrary
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatRupees
import com.example.adhigam.ui.util.formatShortDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun LibraryScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel { LibraryViewModel(AppContainer.libraryRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    val library = state.library
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        library == null -> ErrorState(state.error ?: "Could not load library", onRetry = viewModel::refresh, modifier = modifier)
        else -> LibraryContent(state, library, viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(state: LibraryUiState, library: MyLibrary, viewModel: LibraryViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        val count = when (tab) {
                            LibraryTab.MyBooks -> library.current.size
                            LibraryTab.Reservations -> library.reservations.count { it.isActive }
                            LibraryTab.Catalog -> 0
                        }
                        Text(if (count > 0) "${tab.label} ($count)" else tab.label, maxLines = 1)
                    }
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.tab) {
                    LibraryTab.MyBooks -> myBooks(library)
                    LibraryTab.Reservations -> reservations(library.reservations, state.busyIds, viewModel::cancelReservation)
                    LibraryTab.Catalog -> catalog(state, library, viewModel)
                }
            }
        }
    }
}

// ---- My Books --------------------------------------------------------------------------------

private fun LazyListScope.myBooks(library: MyLibrary) {
    val today = LocalDate.now()
    val current = library.current.sortedBy { it.dueDate }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryBox("Issued", current.size.toString(), null, Modifier.weight(1f))
            SummaryBox("Overdue", current.count { it.daysOverdue(today) > 0 }.toString(), ErrorRed, Modifier.weight(1f))
            SummaryBox("Fine due", formatRupees(current.sumOf { it.currentFine(today) }), ErrorRed, Modifier.weight(1f))
        }
    }
    item { GroupTitle("Checked-out books") }
    if (current.isEmpty()) {
        item { ListCard { EmptyText("No checked out books under this account.") } }
    }
    items(current, key = { it.id }) { CheckoutCard(it, today) }
    if (library.history.isNotEmpty()) {
        item { GroupTitle("Returned") }
        item { HistoryCard(library.history) }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, color: Color?, modifier: Modifier = Modifier) {
    val tint = color ?: MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BookIcon(tint: Color) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = tint)
    }
}

@Composable
private fun CheckoutCard(checkout: LibraryCheckout, today: LocalDate) {
    val overdue = checkout.daysOverdue(today)
    val daysLeft = ChronoUnit.DAYS.between(today, checkout.dueDate)
    val (label, color) = when {
        overdue > 0 -> "Overdue by $overdue day${if (overdue > 1) "s" else ""}" to ErrorRed
        daysLeft == 0L -> "Due today" to Amber600
        daysLeft <= 2 -> "Due in $daysLeft day${if (daysLeft > 1) "s" else ""}" to Amber600
        else -> "Due in $daysLeft days" to SuccessGreen
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookIcon(color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(checkout.bookTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Barcode ${checkout.accessionNo} • Issued ${formatShortDate(checkout.issueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Return by ${formatShortDate(checkout.dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(label, color)
                    val fine = checkout.currentFine(today)
                    if (fine > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("Fine ${formatRupees(fine)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<LibraryCheckout>) {
    ListCard {
        history.sortedByDescending { it.returnDate }.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.bookTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Issued ${formatShortDate(c.issueDate)} • Returned ${c.returnDate?.let(::formatShortDate) ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (c.fineAmount > 0) {
                    Text("Fine ${formatRupees(c.fineAmount)}", style = MaterialTheme.typography.labelMedium, color = ErrorRed)
                }
            }
        }
    }
}

// ---- Reservations ----------------------------------------------------------------------------

private fun LazyListScope.reservations(
    reservations: List<LibraryReservation>,
    busyIds: Set<String>,
    onCancel: (String) -> Unit
) {
    if (reservations.isEmpty()) {
        item { ListCard { EmptyText("No hold queues currently. Reserve a book from the Catalog.") } }
    }
    items(reservations, key = { it.id }) { r -> ReservationCard(r, isBusy = r.id in busyIds, onCancel = { onCancel(r.id) }) }
}

@Composable
private fun reservationStatus(r: LibraryReservation): Pair<String, Color> = when (r.status) {
    "pending" -> "Pending" to Amber600
    "notified" -> "Ready to collect" to SuccessGreen
    "fulfilled" -> "Collected" to MaterialTheme.colorScheme.primary
    "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.outline
    else -> r.status.replaceFirstChar { it.uppercase() } to MaterialTheme.colorScheme.outline
}

@Composable
private fun ReservationCard(r: LibraryReservation, isBusy: Boolean, onCancel: () -> Unit) {
    val (label, color) = reservationStatus(r)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookIcon(color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.bookTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Reserved on ${formatShortDate(r.requestDate)}" +
                        (if (r.status == "notified" && r.readyDate != null) " • Ready since ${formatShortDate(r.readyDate)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                StatusPill(label, color)
            }
            if (r.isActive) {
                Spacer(Modifier.width(8.dp))
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Cancel", color = ErrorRed)
                    }
                }
            }
        }
    }
}

// ---- Catalog ---------------------------------------------------------------------------------

private fun LazyListScope.catalog(state: LibraryUiState, library: MyLibrary, viewModel: LibraryViewModel) {
    item {
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Title, author or ISBN") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.categoryId == null, onClick = { viewModel.setCategory(null) }, label = { Text("All") }) }
            items(state.categories, key = { it.id }) { c ->
                FilterChip(selected = state.categoryId == c.id, onClick = { viewModel.setCategory(c.id) }, label = { Text(c.name) })
            }
        }
    }
    if (state.isSearching) {
        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
    if (state.books.isEmpty() && !state.isSearching) {
        item { ListCard { EmptyText("No books match your search") } }
    }
    val reserved = state.reservedBookIds()
    val issuedTitles = library.current.map { it.bookTitle }.toSet()
    items(state.books, key = { it.id }) { book ->
        BookCard(
            book = book,
            isReserved = book.id in reserved,
            isIssuedToMe = book.title in issuedTitles,
            isBusy = book.id in state.busyIds,
            onReserve = { viewModel.reserve(book) }
        )
    }
}

@Composable
private fun BookCard(book: LibraryBook, isReserved: Boolean, isIssuedToMe: Boolean, isBusy: Boolean, onReserve: () -> Unit) {
    val available = book.availableCopies > 0
    val availabilityColor = if (available) SuccessGreen else ErrorRed
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookIcon(MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(book.author, book.categoryName).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.location?.let {
                    Text("Location: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                StatusPill(
                    if (available) "${book.availableCopies} of ${book.totalCopies} available" else "All ${book.totalCopies} copies issued",
                    availabilityColor
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                isIssuedToMe -> Text("With you", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                isReserved -> Text("Reserved", style = MaterialTheme.typography.labelMedium, color = Amber600, fontWeight = FontWeight.SemiBold)
                isBusy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> FilledTonalButton(onClick = onReserve, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Reserve") }
            }
        }
    }
}
