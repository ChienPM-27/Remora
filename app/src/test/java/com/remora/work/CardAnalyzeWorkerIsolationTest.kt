package com.remora.work

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CardAnalyzeWorkerIsolationTest {

    private fun createTestCard(id: Long = 1L, status: ProcessingStatus = ProcessingStatus.DONE): MemoryCard {
        return MemoryCard(
            id = id,
            imageUri = "content://media/$id",
            createdAt = 1000L,
            userIntent = "Toán 12",
            title = "Test Card",
            summary = "Summary",
            tags = CardTags("Toán", listOf("Đại số")),
            contentType = ContentType.THEORY,
            ocrText = "OCR",
            embedding = FloatArray(768) { 0.1f },
            status = status
        )
    }

    @Test
    fun executeResurfacingWithIsolation_success_completesWithoutError() = runTest {
        val card = createTestCard()
        var executed = false

        executeResurfacingWithIsolation(card) {
            executed = true
        }

        assertTrue("Pipeline block should have executed", executed)
        assertEquals(ProcessingStatus.DONE, card.status)
    }

    @Test
    fun executeResurfacingWithIsolation_rethrowsCancellationException() = runTest {
        val card = createTestCard()

        try {
            executeResurfacingWithIsolation(card) {
                throw CancellationException("Worker preempted by system")
            }
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Worker preempted by system", e.message)
            // Verify card status remains DONE
            assertEquals(ProcessingStatus.DONE, card.status)
        }
    }

    @Test
    fun executeResurfacingWithIsolation_swallowsStandardExceptions() = runTest {
        val card = createTestCard()

        // Should NOT throw exception
        executeResurfacingWithIsolation(card) {
            throw RuntimeException("Gemini API 500 internal server error during resurfacing")
        }

        // Card still remains DONE
        assertEquals(ProcessingStatus.DONE, card.status)
    }

    @Test
    fun executeResurfacingWithIsolation_timesOutWhenCallExceedsLimit() = runTest {
        val card = createTestCard()

        try {
            executeResurfacingWithIsolation(card, timeoutMs = 50L) {
                delay(500L) // Exceeds 50ms timeout
            }
            fail("Expected timeout cancellation exception to be rethrown")
        } catch (e: CancellationException) {
            // TimeoutCancellationException is a subclass of CancellationException
            assertTrue(e is kotlinx.coroutines.TimeoutCancellationException)
            assertEquals(ProcessingStatus.DONE, card.status)
        }
    }
}
