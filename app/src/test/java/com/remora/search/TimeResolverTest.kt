package com.remora.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeResolverTest {

    @Test
    fun testResolveHomQua() {
        val now = 1700000000000L // arbitrary fixed epoch
        val result = TimeResolver.resolve("slide đạo hàm hôm qua chụp", now = now)

        assertEquals("slide đạo hàm chụp", result.cleanQuery.trim())
        assertNotNull(result.startTimeMs)
        assertNotNull(result.endTimeMs)
        assertTrue(result.startTimeMs!! < result.endTimeMs!!)
    }

    @Test
    fun testResolveTuanTruoc() {
        val now = 1700000000000L
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
        val day = 24 * 60 * 60 * 1000L
        val now = 1700000000000L
        val result = TimeResolver.resolve("tuần trước", now = now)

        assertEquals(now - 14 * day, result.startTimeMs)
        assertEquals(now - 7 * day, result.endTimeMs)
    }

    @Test
    fun testTuanTruocBoundariesInclude14To7DaysOldAndExcludeOthers() {
        val day = 24 * 60 * 60 * 1000L
        val now = 1700000000000L
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
        val day = 24 * 60 * 60 * 1000L
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
        val now = 1700000000000L
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
        val result = TimeResolver.resolve("hôm qua", now = 1700000000000L)
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
        val now = 1700000000000L // 2023-11-14 UTC
        val result = TimeResolver.resolve("bài tập tháng trước", now = now)
        assertEquals("bài tập", result.cleanQuery)
        assertTrue(result.endTimeMs!! < now)
        val spanDays = (result.endTimeMs!! - result.startTimeMs!!) / (1000 * 60 * 60 * 24)
        assertTrue(spanDays in 27..30)
    }
}
