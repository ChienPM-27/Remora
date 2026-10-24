package com.remora.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VI = Locale("vi", "VN")

fun formatFullDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", VI).format(Date(epochMs))

fun formatShortDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy", VI).format(Date(epochMs))

/** "vừa xong", "5 phút trước", "3 ngày trước", "2 tuần trước", otherwise dd/MM/yyyy. */
fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - epochMs).coerceAtLeast(0L)
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "vừa xong"
        minutes < 60 -> "$minutes phút trước"
        hours < 24 -> "$hours giờ trước"
        days < 7 -> "$days ngày trước"
        days < 30 -> "${days / 7} tuần trước"
        else -> formatShortDate(epochMs)
    }
}
