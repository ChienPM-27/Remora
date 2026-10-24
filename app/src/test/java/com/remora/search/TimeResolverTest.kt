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
