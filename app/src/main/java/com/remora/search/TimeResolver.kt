package com.remora.search

import java.util.Calendar

data class ResolvedSearchQuery(
    val cleanQuery: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    /** Human-readable description of the resolved range, shown as a chip. Null when no time phrase was found. */
    val label: String? = null
)

object TimeResolver {

    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private fun strip(query: String, vararg phrases: String): String {
        var out = query
        // Longest phrases must be listed first so "ngày hôm qua" is removed before "hôm qua".
        for (p in phrases) out = out.replace(p, "", ignoreCase = true)
        return out.replace(Regex("\\s+"), " ").trim()
    }

    private fun startOfDay(now: Long, daysBack: Int = 0): Calendar = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -daysBack)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun resolve(rawQuery: String, now: Long = System.currentTimeMillis()): ResolvedSearchQuery {
        val query = rawQuery.trim()
        val lower = query.lowercase()

        // 1. "hôm qua" / "ngày hôm qua"
        if (lower.contains("hôm qua")) {
            val start = startOfDay(now, daysBack = 1).timeInMillis
            return ResolvedSearchQuery(
                strip(query, "ngày hôm qua", "hôm qua"), start, start + ONE_DAY_MS - 1, "hôm qua"
            )
        }

        // 2. "tuần trước" (7–14 days ago)
        if (lower.contains("tuần trước")) {
            return ResolvedSearchQuery(
                strip(query, "tuần trước"), now - 14 * ONE_DAY_MS, now - 7 * ONE_DAY_MS, "tuần trước · 7–14 ngày"
            )
        }

        // 3. "tháng trước" = previous calendar month
        if (lower.contains("tháng trước")) {
            val start = startOfDay(now).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -1)
            }
            val end = (start.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
            val label = "tháng trước · tháng ${start.get(Calendar.MONTH) + 1}"
            return ResolvedSearchQuery(strip(query, "tháng trước"), start.timeInMillis, end.timeInMillis, label)
        }

        // 4. "hôm bữa" / "mấy hôm trước" / "dạo này" (~ the last 3 weeks)
        if (lower.contains("hôm bữa") || lower.contains("mấy hôm trước") || lower.contains("dạo này")) {
            return ResolvedSearchQuery(
                strip(query, "mấy hôm trước", "hôm bữa", "dạo này"),
                now - 21 * ONE_DAY_MS, now, "hôm bữa · trong 3 tuần qua"
            )
        }

        // 5. "hôm nay"
        if (lower.contains("hôm nay")) {
            return ResolvedSearchQuery(
                strip(query, "ngày hôm nay", "hôm nay"), startOfDay(now).timeInMillis, now, "hôm nay"
            )
        }

        return ResolvedSearchQuery(cleanQuery = query, startTimeMs = null, endTimeMs = null, label = null)
    }
}
