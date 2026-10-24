package com.remora.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    private val now = 1_700_000_000_000L
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    @Test
    fun relativeTimeBuckets() {
        assertEquals("vừa xong", relativeTime(now - 20_000, now))
        assertEquals("5 phút trước", relativeTime(now - 5 * min, now))
        assertEquals("3 giờ trước", relativeTime(now - 3 * hour, now))
        assertEquals("2 ngày trước", relativeTime(now - 2 * day, now))
        assertEquals("2 tuần trước", relativeTime(now - 15 * day, now))
    }

    @Test
    fun futureTimestampDoesNotGoNegative() {
        assertEquals("vừa xong", relativeTime(now + 5 * min, now))
    }
}
