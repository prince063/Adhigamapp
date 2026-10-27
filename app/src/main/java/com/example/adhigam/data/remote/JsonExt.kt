package com.example.adhigam.data.remote

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

// Helpers for reading the backend's JSON, where missing values come as JSON null (which
// optString would turn into the text "null") and procedures name columns in either case.

// The first non-blank value among the given keys, or null.
fun JSONObject.str(vararg keys: String): String? {
    for (key in keys) {
        if (!has(key) || isNull(key)) continue
        val value = opt(key)?.toString()?.trim()
        if (!value.isNullOrEmpty() && value != "null") return value
    }
    return null
}

fun JSONObject.int(vararg keys: String): Int? = str(*keys)?.toDoubleOrNull()?.toInt()

// Dates arrive as "2013-05-14" or "2013-05-14T00:00:00".
fun JSONObject.date(vararg keys: String): LocalDate? =
    str(*keys)?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// Date-times arrive as "2026-10-15T10:20:00", "2026-10-15 10:20:00" or with fractional seconds.
fun JSONObject.dateTime(vararg keys: String): LocalDateTime? =
    str(*keys)?.replace(' ', 'T')?.take(19)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
