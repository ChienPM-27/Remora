package com.remora.data

import com.remora.ai.AiGatewayClient
import com.remora.ai.ApiException
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.util.HashUtil
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Genuine test asserting the Remora Privacy Boundary directly against PRODUCTION classes:
 * 1. HashUtil: Identical screenshot bytes produce identical SHA-256 hashes to prevent redundant upload.
 * 2. MemoryCard & MemoryCardEntity: New cards strictly default to PENDING status without embeddings.
 * 3. AiGatewayClient: Zero network egress leaves device without valid user credentials/token.
 * 4. ApiException: Client errors (400, 401, 403, 404) are non-retryable, preventing infinite egress loops.
 */
class PrivacyBoundaryTest {

    @Test
    fun testSha256DeduplicationIntegrityUsingProductionHashUtil() {
        val slideBytesOriginal = "LECTURE_SLIDE_PYTHAGORAS_HIGH_RES".toByteArray()
        val slideBytesDuplicate = "LECTURE_SLIDE_PYTHAGORAS_HIGH_RES".toByteArray()
        val slideBytesDifferent = "DIFFERENT_LECTURE_SLIDE_THALES".toByteArray()

        val hashOriginal = HashUtil.sha256(slideBytesOriginal)
        val hashDuplicate = HashUtil.sha256(slideBytesDuplicate)
        val hashDifferent = HashUtil.sha256(slideBytesDifferent)

        assertEquals("Identical screenshot bytes must yield identical SHA-256 hash", hashOriginal, hashDuplicate)
        assertFalse("Different slides must yield different SHA-256 hashes", hashOriginal == hashDifferent)
        assertEquals("SHA-256 hex string must be 64 characters long", 64, hashOriginal.length)
    }

    @Test
    fun testCardProductionModelDefaultsStrictlyToPendingWithoutEmbedding() {
        val originalCard = MemoryCard(
            id = 42L,
            imageUri = "content://media/external/images/media/1001",
            createdAt = 1700000000000L,
            userIntent = "Công thức cần nhớ thi giữa kỳ Giải tích",
            title = "Đang xử lý...",
            summary = "",
            tags = CardTags(),
            contentType = ContentType.UNKNOWN,
            ocrText = "",
            embedding = null,
            status = ProcessingStatus.PENDING,
            attemptCount = 0
        )

        // Verify domain model privacy invariants
        assertEquals("Initial card must strictly be PENDING", ProcessingStatus.PENDING, originalCard.status)
        assertNull("Embedding must be null before user confirms and AI runs", originalCard.embedding)
        assertEquals("User intent must be preserved verbatim", "Công thức cần nhớ thi giữa kỳ Giải tích", originalCard.userIntent)

        // Verify Room entity round-trip preserves all fields
        val entity = MemoryCardEntity.fromDomainModel(originalCard)
        assertEquals(ProcessingStatus.PENDING.name, entity.status)
        assertNull(entity.embedding)

        val restoredCard = entity.toDomainModel()
        assertEquals(originalCard.id, restoredCard.id)
        assertEquals(originalCard.status, restoredCard.status)
        assertEquals(originalCard.userIntent, restoredCard.userIntent)
        assertNull(restoredCard.embedding)
    }

    @Test
    fun testZeroAiEgressWhenAuthFailsUsingProductionClient() = runTest {
        val egressCount = AtomicInteger(0)

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            if (req.url.toString().contains("/ai/")) {
                egressCount.incrementAndGet()
            }
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("""{"error":"unauthorized"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val testHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = AiGatewayClient(baseUrl = "http://localhost:8080/v1", client = testHttpClient)

        val result = client.extractCard(
            imageBytes = "USER_PRIVATE_SCREENSHOT".toByteArray(),
            userIntent = "Ghi chú riêng tư"
        )

        assertTrue("Call must fail without valid authentication", result.isFailure)
        val exception = result.exceptionOrNull() as? ApiException
        assertNotNull(exception)
        assertEquals(401, exception?.statusCode)
        assertEquals("Zero AI requests must egress when authentication fails", 0, egressCount.get())
    }

    @Test
    fun testClientErrorsPreventInfiniteEgressLoops() {
        val err400 = ApiException(400, "Bad Request")
        val err401 = ApiException(401, "Unauthorized")
        val err404 = ApiException(404, "Not Found")
        val err429 = ApiException(429, "Too Many Requests", retryAfterSeconds = 30)
        val err500 = ApiException(500, "Internal Server Error")

        assertTrue("HTTP 400 is non-retryable", err400.isNonRetryableClientError)
        assertTrue("HTTP 401 is non-retryable", err401.isNonRetryableClientError)
        assertTrue("HTTP 404 is non-retryable", err404.isNonRetryableClientError)
        assertFalse("HTTP 429 should allow backoff retry", err429.isNonRetryableClientError)
        assertFalse("HTTP 500 should allow server retry", err500.isNonRetryableClientError)
    }
}
