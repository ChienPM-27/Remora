package com.remora.gateway

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GatewayTest {

    @Test
    fun testIpRateLimiter() {
        val limiter = IpRateLimiter(maxAttempts = 3, windowMs = 10_000L)
        val ip = "192.168.1.50"

        assertTrue(limiter.isAllowed(ip), "1st attempt allowed")
        assertTrue(limiter.isAllowed(ip), "2nd attempt allowed")
        assertTrue(limiter.isAllowed(ip), "3rd attempt allowed")
        assertFalse(limiter.isAllowed(ip), "4th attempt should be blocked")
    }

    @Test
    fun testDecoupledQuotasByType() {
        val customLimits = mapOf(
            QuotaType.EXTRACT_CARD to 2,
            QuotaType.RESURFACE_BATCH to 2,
            QuotaType.EMBEDDINGS to 5
        )
        val quotaManager = UserQuotaManager(limits = customLimits)
        val userId = "usr_student_test"

        // Consume 2 extract card quotas
        assertTrue(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD))
        assertTrue(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD))
        assertFalse(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD), "Extract card limit reached")

        // Embeddings and Resurface batch MUST NOT be blocked by extract card exhaustion
        assertEquals(5, quotaManager.getRemaining(userId, QuotaType.EMBEDDINGS))
        assertTrue(quotaManager.tryConsume(userId, QuotaType.EMBEDDINGS))
        assertEquals(4, quotaManager.getRemaining(userId, QuotaType.EMBEDDINGS))

        assertEquals(2, quotaManager.getRemaining(userId, QuotaType.RESURFACE_BATCH))
        assertTrue(quotaManager.tryConsume(userId, QuotaType.RESURFACE_BATCH))
        assertEquals(1, quotaManager.getRemaining(userId, QuotaType.RESURFACE_BATCH))
    }

    @Test
    fun testDailyAutoReset() {
        val currentDate = AtomicReference(LocalDate.of(2026, 10, 23))
        val customLimits = mapOf(QuotaType.EXTRACT_CARD to 1)
        val quotaManager = UserQuotaManager(
            limits = customLimits,
            clock = { currentDate.get() }
        )
        val userId = "usr_student_reset"

        // Day 1
        assertTrue(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD))
        assertFalse(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD), "Exhausted on day 1")
        assertEquals(0, quotaManager.getRemaining(userId, QuotaType.EXTRACT_CARD))

        // Advance to Day 2 (midnight passed)
        currentDate.set(LocalDate.of(2026, 10, 24))

        // Should automatically reset for the new day
        assertEquals(1, quotaManager.getRemaining(userId, QuotaType.EXTRACT_CARD))
        assertTrue(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD), "Allowed on day 2 after auto-reset")
    }

    @Test
    fun testManualQuotaReset() {
        val customLimits = mapOf(QuotaType.EXTRACT_CARD to 1)
        val quotaManager = UserQuotaManager(limits = customLimits)
        val userId = "usr_manual_reset"

        assertTrue(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD))
        assertFalse(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD))

        // Admin manual reset
        quotaManager.resetQuota(userId)
        assertEquals(1, quotaManager.getRemaining(userId, QuotaType.EXTRACT_CARD))
        assertTrue(quotaManager.tryConsume(userId, QuotaType.EXTRACT_CARD), "Allowed after manual reset")
    }
}
