package com.example.adhigam.ui.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val IndianLocale: Locale = Locale.forLanguageTag("en-IN")
private val ShortDate = DateTimeFormatter.ofPattern("d MMM", IndianLocale)
private val FullDate = DateTimeFormatter.ofPattern("d MMM yyyy", IndianLocale)
private val ClockTime = DateTimeFormatter.ofPattern("HH:mm", IndianLocale)
private val ShortDateTime = DateTimeFormatter.ofPattern("d MMM, h:mm a", IndianLocale)

fun formatRupees(amount: Long): String =
    NumberFormat.getCurrencyInstance(IndianLocale).apply { maximumFractionDigits = 0 }.format(amount)

// Indian short form for chart axes: ₹950, ₹12.5K, ₹4.1L, ₹1.2Cr.
fun formatRupeesCompact(amount: Long): String {
    fun trim(value: Double) = String.format(IndianLocale, "%.1f", value).removeSuffix(".0")
    return when {
        amount >= 10_000_000 -> "₹${trim(amount / 10_000_000.0)}Cr"
        amount >= 100_000 -> "₹${trim(amount / 100_000.0)}L"
        amount >= 1_000 -> "₹${trim(amount / 1_000.0)}K"
        else -> "₹$amount"
    }
}

fun formatShortDate(date: LocalDate): String = date.format(ShortDate)

fun formatFullDate(date: LocalDate): String = date.format(FullDate)

fun formatClock(time: LocalTime): String = time.format(ClockTime)

fun formatDateTime(dateTime: LocalDateTime): String = dateTime.format(ShortDateTime)

// "Today", "Tomorrow", "in 3 days", or a date for anything further out.
fun formatDueDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> "Overdue"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days <= 7 -> "in $days days"
        else -> formatShortDate(date)
    }
}

fun greetingForNow(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
