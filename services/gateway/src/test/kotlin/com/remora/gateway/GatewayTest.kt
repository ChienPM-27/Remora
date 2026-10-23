package com.remora.gateway

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
    fun testUserQuotaManager() {
        val quotaManager = UserQuotaManager(defaultDailyLimit = 2)
        val userId = "usr_test_student"

        assertEquals(2, quotaManager.getRemaining(userId))
        assertEquals(0, quotaManager.getUsed(userId))

        assertTrue(quotaManager.tryConsume(userId), "1st consumption allowed")
        assertEquals(1, quotaManager.getRemaining(userId))
        assertEquals(1, quotaManager.getUsed(userId))

        assertTrue(quotaManager.tryConsume(userId), "2nd consumption allowed")
        assertEquals(0, quotaManager.getRemaining(userId))
        assertEquals(2, quotaManager.getUsed(userId))

        assertFalse(quotaManager.tryConsume(userId), "3rd consumption blocked due to quota limit")
    }
}
