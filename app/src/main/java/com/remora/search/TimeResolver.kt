package com.remora.search

import java.util.Calendar

data class ResolvedSearchQuery(
    val cleanQuery: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?
)

object TimeResolver {

    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    fun resolve(rawQuery: String, now: Long = System.currentTimeMillis()): ResolvedSearchQuery {
        val query = rawQuery.trim()
        val lower = query.lowercase()

        // 1. "hôm qua" / "ngày hôm qua"
        if (lower.contains("hôm qua") || lower.contains("ngày hôm qua")) {
            val clean = query.replace("ngày hôm qua", "", ignoreCase = true)
                .replace("hôm qua", "", ignoreCase = true)
                .replace(Regex("\\s+"), " ")
                .trim()

            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = calendar.timeInMillis
            val end = start + ONE_DAY_MS - 1
            return ResolvedSearchQuery(clean, start, end)
        }

        // 2. "tuần trước"
        if (lower.contains("tuần trước")) {
            val clean = query.replace("tuần trước", "", ignoreCase = true)
                .replace(Regex("\\s+"), " ")
                .trim()

            val start = now - (14 * ONE_DAY_MS)
            val end = now - (7 * ONE_DAY_MS)
            return ResolvedSearchQuery(clean, start, end)
        }

        // 3. "hôm bữa" / "mấy hôm trước" / "dạo này" (~2-3 tuần)
        if (lower.contains("hôm bữa") || lower.contains("mấy hôm trước") || lower.contains("dạo này")) {
            val clean = query.replace("hôm bữa", "", ignoreCase = true)
                .replace("mấy hôm trước", "", ignoreCase = true)
                .replace("dạo này", "", ignoreCase = true)
                .replace(Regex("\\s+"), " ")
                .trim()

            val start = now - (21 * ONE_DAY_MS)
            val end = now
            return ResolvedSearchQuery(clean, start, end)
        }

        // 4. "hôm nay"
        if (lower.contains("hôm nay") || lower.contains("ngày hôm nay")) {
            val clean = query.replace("ngày hôm nay", "", ignoreCase = true)
                .replace("hôm nay", "", ignoreCase = true)
                .replace(Regex("\\s+"), " ")
                .trim()

            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return ResolvedSearchQuery(clean, calendar.timeInMillis, now)
        }

        // Default: No time filter
        return ResolvedSearchQuery(cleanQuery = query, startTimeMs = null, endTimeMs = null)
    }
}
