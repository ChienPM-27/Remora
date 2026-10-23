package com.remora.work

import com.remora.ai.ApiException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerErrorClassificationTest {

    @Test
    fun testClientErrorsAreNonRetryable() {
        val err400 = ApiException(400, "Bad Request")
        assertTrue("HTTP 400 is non-retryable client error", err400.isNonRetryableClientError)

        val err401 = ApiException(401, "Unauthorized")
        assertTrue("HTTP 401 is non-retryable client error", err401.isNonRetryableClientError)

        val err403 = ApiException(403, "Forbidden")
        assertTrue("HTTP 403 is non-retryable client error", err403.isNonRetryableClientError)
    }

    @Test
    fun testServerAndRateLimitErrorsAreRetryable() {
        val err429 = ApiException(429, "Rate Limited", retryAfterSeconds = 15)
        assertTrue("HTTP 429 is rate limited", err429.isRateLimited)
        assertFalse("HTTP 429 is not non-retryable client error", err429.isNonRetryableClientError)

        val err500 = ApiException(500, "Internal Server Error")
        assertTrue("HTTP 500 is server error", err500.isServerError)
        assertFalse("HTTP 500 is not client error", err500.isNonRetryableClientError)

        val err503 = ApiException(503, "Service Unavailable")
        assertTrue("HTTP 503 is server error", err503.isServerError)
    }
}
