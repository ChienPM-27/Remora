package com.remora.search

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale

data class ResolvedSearchQuery(
    val cleanQuery: String,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    /** Human-readable description of the resolved range, shown as a chip. Null when no time phrase was found. */
    val label: String? = null
)

object TimeResolver {

    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private class TimeWindow(val startMs: Long, val endMs: Long, val label: String)

    private data class TimeRule(
        /** Aliases of one time phrase. */
        val phrases: List<String>,
        val resolve: (now: Long) -> TimeWindow,
    )

    // Declaration order only breaks ties between rules whose longest phrase has the same length;
    // those rules never share substrings (e.g. "ngày hôm qua" vs "ngày hôm nay"), so the tie-break
    // is not observable. Which rule wins is decided by phrase length, see the sorting in [rules].
    private val rules: List<TimeRule> = listOf(
        // Rolling window 14–21 days ago. The 14-char phrase must be tried before "tuần trước",
        // otherwise "hai tuần trước" would be captured by the generic rule (FR-014 bug).
        TimeRule(listOf("hai tuần trước")) { now ->
            TimeWindow(now - 21 * ONE_DAY_MS, now - 14 * ONE_DAY_MS, "hai tuần trước · 14–21 ngày")
        },
        // "hôm bữa" / "mấy hôm trước" / "dạo này": roughly the last 3 weeks.
        TimeRule(listOf("mấy hôm trước", "hôm bữa", "dạo này")) { now ->
            TimeWindow(now - 21 * ONE_DAY_MS, now, "hôm bữa · trong 3 tuần qua")
        },
        // "hôm qua" / "ngày hôm qua": start-of-day to end-of-day of yesterday.
        TimeRule(listOf("ngày hôm qua", "hôm qua")) { now ->
            val start = startOfDay(now, daysBack = 1).timeInMillis
            TimeWindow(start, start + ONE_DAY_MS - 1, "hôm qua")
        },
        // "hôm nay" / "ngày hôm nay": start of today until now.
        TimeRule(listOf("ngày hôm nay", "hôm nay")) { now ->
            TimeWindow(startOfDay(now).timeInMillis, now, "hôm nay")
        },
        // "tháng trước": previous calendar month.
        TimeRule(listOf("tháng trước")) { now ->
            val start = startOfDay(now).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -1)
            }
            val end = (start.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
            TimeWindow(start.timeInMillis, end.timeInMillis, "tháng trước · tháng ${start.get(Calendar.MONTH) + 1}")
        },
        // "tuần trước": approved rolling window [now-14d, now-7d] (spec FR-014), NOT the previous calendar week.
        TimeRule(listOf("tuần trước")) { now ->
            TimeWindow(now - 14 * ONE_DAY_MS, now - 7 * ONE_DAY_MS, "tuần trước · 7–14 ngày")
        },
        // "tuần này": calendar week, first day per system locale, from start of that week to now.
        TimeRule(listOf("tuần này")) { now ->
            TimeWindow(startOfCurrentWeek(now), now, "tuần này")
        },
    )
        // Alias lists longest-first so stripping never leaves half a phrase ("ngày " after "hôm qua").
        .map { rule -> rule.copy(phrases = rule.phrases.sortedByDescending { phrase -> phrase.length }) }
        // Most specific (longest) phrases are tried first: "hai tuần trước" wins over "tuần trước",
        // "ngày hôm qua" over "hôm qua". sortedByDescending is stable, ties keep declaration order.
        .sortedByDescending { rule -> rule.phrases.maxOf { phrase -> phrase.length } }

    fun resolve(rawQuery: String, now: Long = System.currentTimeMillis()): ResolvedSearchQuery {
        val query = rawQuery.trim()
        val lower = query.lowercase()

        val rule = rules.firstOrNull { r -> r.phrases.any { lower.contains(it) } }
            ?: return ResolvedSearchQuery(cleanQuery = query, startTimeMs = null, endTimeMs = null, label = null)

        val window = rule.resolve(now)
        // Only the matched rule's phrases are stripped; other rules' phrases must survive.
        return ResolvedSearchQuery(
            strip(query, *rule.phrases.toTypedArray()), window.startMs, window.endMs, window.label
        )
    }

    /** Removes the matched rule's phrases (longest first), collapses whitespace and trims. */
    private fun strip(query: String, vararg phrases: String): String {
        var out = query
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

    /**
     * Millis at 00:00 local time of the first day of the current calendar week.
     * Calendar week, first day per system locale:
     * `WeekFields.of(Locale.getDefault()).firstDayOfWeek` (Monday in vi-VN, Sunday in en-US, ...).
     */
    private fun startOfCurrentWeek(now: Long): Long {
        val zone = ZoneId.systemDefault()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(firstDay))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
}
