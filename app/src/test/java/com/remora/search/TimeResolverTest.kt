package com.remora.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

class TimeResolverTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1700000000000L // arbitrary fixed epoch

    // ------------------------------------------------------------------
    // Existing behavior that must not regress
    // ------------------------------------------------------------------

    @Test
    fun testResolveHomQua() {
        val result = TimeResolver.resolve("slide đạo hàm hôm qua chụp", now = now)

        assertEquals("slide đạo hàm chụp", result.cleanQuery.trim())
        assertNotNull(result.startTimeMs)
        assertNotNull(result.endTimeMs)
        assertTrue(result.startTimeMs!! < result.endTimeMs!!)
    }

    @Test
    fun testResolveTuanTruoc() {
        val result = TimeResolver.resolve("bài tập ma trận tuần trước", now = now)

        assertEquals("bài tập ma trận", result.cleanQuery.trim())
        assertNotNull(result.startTimeMs)
        assertNotNull(result.endTimeMs)
        assertTrue(result.endTimeMs!! <= now)
    }

    @Test
    fun testTuanTruocIsRollingWindowFrom14To7DaysAgo() {
        // Approved semantics (spec FR-014): rolling window [now-14d, now-7d], NOT the previous
        // calendar week. Fixed clock keeps the boundaries exact and repeatable.
        val result = TimeResolver.resolve("tuần trước", now = now)

        assertEquals(now - 14 * day, result.startTimeMs)
        assertEquals(now - 7 * day, result.endTimeMs)
    }

    @Test
    fun testTuanTruocBoundariesInclude14To7DaysOldAndExcludeOthers() {
        val range = TimeResolver.resolve("tuần trước", now = now)

        val exactly14DaysOld = now - 14 * day
        val exactly7DaysOld = now - 7 * day
        assertTrue("Card saved exactly 14 days ago is inside (inclusive lower bound)", exactly14DaysOld in range.startTimeMs!!..range.endTimeMs!!)
        assertTrue("Card saved exactly 7 days ago is inside (inclusive upper bound)", exactly7DaysOld in range.startTimeMs!!..range.endTimeMs!!)

        // Cards inside the rolling window: 8 and 10 days ago.
        assertTrue((now - 8 * day) in range.startTimeMs!!..range.endTimeMs!!)
        assertTrue((now - 10 * day) in range.startTimeMs!!..range.endTimeMs!!)

        // Cards outside: 6 days ago (too recent) and 20 days ago (too old).
        assertTrue("Card saved 6 days ago is newer than the window", (now - 6 * day) > range.endTimeMs!!)
        assertTrue("Card saved 20 days ago is older than the window", (now - 20 * day) < range.startTimeMs!!)
    }

    @Test
    fun testTuanTruocBoundaryIsStableAcrossClock() {
        val morning = 1699970000000L
        val evening = 1700040000000L
        val r1 = TimeResolver.resolve("x tuần trước", now = morning)
        val r2 = TimeResolver.resolve("x tuần trước", now = evening)
        assertEquals(morning - 14 * day, r1.startTimeMs)
        assertEquals(evening - 14 * day, r2.startTimeMs)
        assertEquals("x", r1.cleanQuery)
        assertEquals("x", r2.cleanQuery)
    }

    @Test
    fun testResolveHomBua() {
        val result = TimeResolver.resolve("cái slide về phân phối chuẩn hôm bữa chụp", now = now)

        assertEquals("cái slide về phân phối chuẩn chụp", result.cleanQuery.trim())
        assertNotNull(result.startTimeMs)
        // ~21 days back
        val diffDays = (now - result.startTimeMs!!) / (1000 * 60 * 60 * 24)
        assertTrue(diffDays in 20..22)
    }

    @Test
    fun testQueryWithoutTimeExpression() {
        val result = TimeResolver.resolve("định lý thales trong tam giác")
        assertEquals("định lý thales trong tam giác", result.cleanQuery)
        assertNull(result.startTimeMs)
        assertNull(result.endTimeMs)
    }

    @Test
    fun timeOnlyQueryLeavesBlankCleanQueryAndALabel() {
        val result = TimeResolver.resolve("hôm qua", now = now)
        assertEquals("", result.cleanQuery)
        assertEquals("hôm qua", result.label)
        assertNotNull(result.startTimeMs)
    }

    @Test
    fun labelsAreNullWithoutTimePhrase() {
        assertNull(TimeResolver.resolve("pttk").label)
    }

    @Test
    fun resolveThangTruocCoversPreviousCalendarMonth() {
        val result = TimeResolver.resolve("bài tập tháng trước", now = now)
        assertEquals("bài tập", result.cleanQuery)
        assertTrue(result.endTimeMs!! < now)
        val spanDays = (result.endTimeMs!! - result.startTimeMs!!) / (1000 * 60 * 60 * 24)
        assertTrue(spanDays in 27..30)
    }

    // ------------------------------------------------------------------
    // Exact boundaries (both ends in ms) for every supported phrase
    // ------------------------------------------------------------------

    @Test
    fun testHomNayExactBoundaries() {
        val result = TimeResolver.resolve("hôm nay", now = now)
        val expectedStart = startOfToday(now)

        assertEquals(expectedStart, result.startTimeMs)
        assertEquals(now, result.endTimeMs)
        assertEquals("hôm nay", result.label)
    }

    @Test
    fun testHomQuaExactBoundaries() {
        val result = TimeResolver.resolve("hôm qua", now = now)
        val expectedStart = startOfToday(now) - day

        assertEquals(expectedStart, result.startTimeMs)
        // End of day is inclusive: last millisecond of yesterday.
        assertEquals(expectedStart + day - 1, result.endTimeMs)
    }

    @Test
    fun testNgayHomQuaAliasStripsCompletelyAndResolvesLikeHomQua() {
        val result = TimeResolver.resolve("ngày hôm qua", now = now)
        val expectedStart = startOfToday(now) - day

        // The longer alias must be stripped as a whole, not leaving a dangling "ngày".
        assertEquals("", result.cleanQuery)
        assertEquals(expectedStart, result.startTimeMs)
        assertEquals(expectedStart + day - 1, result.endTimeMs)
    }

    @Test
    fun testThangTruocExactBoundaries() {
        val result = TimeResolver.resolve("tháng trước", now = now)
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val expectedStart = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val expectedEnd = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        assertEquals(expectedStart, result.startTimeMs)
        assertEquals(expectedEnd, result.endTimeMs)
    }

    @Test
    fun testHomBuaGroupExactBoundariesForEachAlias() {
        for (phrase in listOf("hôm bữa", "mấy hôm trước", "dạo này")) {
            val result = TimeResolver.resolve(phrase, now = now)
            assertEquals("phrase: $phrase", now - 21 * day, result.startTimeMs)
            assertEquals("phrase: $phrase", now, result.endTimeMs)
            assertEquals("phrase: $phrase", "hôm bữa · trong 3 tuần qua", result.label)
        }
    }

    // ------------------------------------------------------------------
    // "hai tuần trước": longest-phrase-first must beat "tuần trước"
    // ------------------------------------------------------------------

    @Test
    fun testHaiTuanTruocIsNotCapturedByTuanTruocRule() {
        val result = TimeResolver.resolve("hai tuần trước", now = now)

        assertEquals(now - 21 * day, result.startTimeMs)
        assertEquals(now - 14 * day, result.endTimeMs)
        // Not the "tuần trước" window [now-14d, now-7d].
        assertTrue("end must be the 14-day mark, not the 7-day one", result.endTimeMs != now - 7 * day)
        assertEquals("hai tuần trước · 14–21 ngày", result.label)
        assertEquals("", result.cleanQuery)
    }

    @Test
    fun testHaiTuanTruocBoundariesInclude21To14DaysOldAndExcludeOthers() {
        val range = TimeResolver.resolve("hai tuần trước", now = now)

        assertTrue("Card saved exactly 21 days ago is inside (inclusive lower bound)", (now - 21 * day) in range.startTimeMs!!..range.endTimeMs!!)
        assertTrue("Card saved exactly 14 days ago is inside (inclusive upper bound)", (now - 14 * day) in range.startTimeMs!!..range.endTimeMs!!)
        assertTrue((now - 18 * day) in range.startTimeMs!!..range.endTimeMs!!)

        // Outside: 13 days ago (belongs to "tuần trước") and 22 days ago (too old).
        assertTrue("Card saved 13 days ago is newer than the window", (now - 13 * day) > range.endTimeMs!!)
        assertTrue("Card saved 22 days ago is older than the window", (now - 22 * day) < range.startTimeMs!!)
    }

    @Test
    fun testHaiTuanTruocWithNowAtExactMidnight() {
        // Fixed `now` sitting exactly on local midnight, to catch day-boundary drift:
        // the rolling window must stay pure millisecond math and not snap to day starts.
        val zone = ZoneId.systemDefault()
        val midnightNow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val result = TimeResolver.resolve("hai tuần trước", now = midnightNow)

        assertEquals(midnightNow - 21 * day, result.startTimeMs)
        assertEquals(midnightNow - 14 * day, result.endTimeMs)
    }

    // ------------------------------------------------------------------
    // "tuần này": calendar week, first day per system locale
    // ------------------------------------------------------------------

    @Test
    fun testTuanNayStartsAtLocaleFirstDayOfWeekAndEndsNow() {
        val result = TimeResolver.resolve("tuần này", now = now)

        assertEquals(now, result.endTimeMs)
        assertEquals("tuần này", result.label)

        // Independent derivation: walk back day by day to the locale's first day of the week.
        val zone = ZoneId.systemDefault()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        var d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        while (d.dayOfWeek != firstDay) d = d.minusDays(1)
        val expectedStart = d.atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(expectedStart, result.startTimeMs)
        assertTrue("start of week must not be after now", result.startTimeMs!! <= now)
        assertTrue("start of week is at most 7 days back", now - result.startTimeMs!! < 7 * day)
    }

    // ------------------------------------------------------------------
    // Combined time phrase + topic keywords
    // ------------------------------------------------------------------

    @Test
    fun testDeCuanTuanTruocKeepsTopicAndWindow() {
        val result = TimeResolver.resolve("đề cương tuần trước", now = now)

        assertEquals("đề cương", result.cleanQuery)
        assertEquals(now - 14 * day, result.startTimeMs)
        assertEquals(now - 7 * day, result.endTimeMs)
    }

    @Test
    fun testBaiGiangHomQuaKeepsTopicAndWindow() {
        val result = TimeResolver.resolve("bài giảng hôm qua", now = now)

        assertEquals("bài giảng", result.cleanQuery)
        val expectedStart = startOfToday(now) - day
        assertEquals(expectedStart, result.startTimeMs)
        assertEquals(expectedStart + day - 1, result.endTimeMs)
    }

    @Test
    fun testDeCuanHaiTuanTruocKeepsTopicAndFullPhraseRemoved() {
        val result = TimeResolver.resolve("đề cương hai tuần trước", now = now)

        // The whole phrase "hai tuần trước" is removed; no "tuần" leftover remains.
        assertEquals("đề cương", result.cleanQuery)
        assertEquals(now - 21 * day, result.startTimeMs)
        assertEquals(now - 14 * day, result.endTimeMs)
    }

    @Test
    fun testBaiGiangTuanNayKeepsTopicAndEndsNow() {
        val result = TimeResolver.resolve("bài giảng tuần này", now = now)

        assertEquals("bài giảng", result.cleanQuery)
        assertEquals(now, result.endTimeMs)
        assertNotNull(result.startTimeMs)
    }

    // ------------------------------------------------------------------
    // Time-only queries for the new phrases
    // ------------------------------------------------------------------

    @Test
    fun timeOnlyQueriesForNewPhrasesLeaveBlankCleanQuery() {
        assertEquals("", TimeResolver.resolve("tuần này", now = now).cleanQuery)
        assertEquals("", TimeResolver.resolve("hai tuần trước", now = now).cleanQuery)
    }

    /** 00:00 local time of the day containing [millis], derived independently of TimeResolver. */
    private fun startOfToday(millis: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
