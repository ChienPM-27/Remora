package com.remora.work

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun executeResurfacingWithIsolation_timesOutWhenCallExceedsLimit_doesNotThrow() = runTest {
        val card = createTestCard()

        // When resurfacing times out, executeResurfacingWithIsolation catches TimeoutCancellationException,
        // logs the event, and does NOT rethrow. Card status remains DONE.
        executeResurfacingWithIsolation(card, timeoutMs = 50L) {
            delay(500L) // Exceeds 50ms timeout
        }

        assertEquals(ProcessingStatus.DONE, card.status)
    }

    @Test
    fun shouldSkipAnalysis_whenDoneWithEmbedding_returnsTrue() {
        val doneCard = createTestCard(id = 1L, status = ProcessingStatus.DONE)
        assertTrue(shouldSkipAnalysis(doneCard))
    }

    @Test
    fun shouldSkipAnalysis_whenDoneWithoutEmbedding_returnsFalse() {
        val doneCardNoEmbedding = createTestCard(id = 2L, status = ProcessingStatus.DONE).copy(embedding = null)
        assertFalse(shouldSkipAnalysis(doneCardNoEmbedding))

        val doneCardEmptyEmbedding = createTestCard(id = 3L, status = ProcessingStatus.DONE).copy(embedding = floatArrayOf())
        assertFalse(shouldSkipAnalysis(doneCardEmptyEmbedding))
    }

    @Test
    fun shouldSkipAnalysis_whenPendingOrFailed_returnsFalse() {
        val pendingCard = createTestCard(id = 4L, status = ProcessingStatus.PENDING)
        assertFalse(shouldSkipAnalysis(pendingCard))

        val failedCard = createTestCard(id = 5L, status = ProcessingStatus.FAILED)
        assertFalse(shouldSkipAnalysis(failedCard))
    }

    @Test
    fun analyzeCardIfRequired_whenDoneWithEmbedding_doesNotCallExtractCard() = runTest {
        val doneCard = createTestCard(id = 10L, status = ProcessingStatus.DONE)
        var extractCardCalled = false

        val executed = analyzeCardIfRequired(doneCard) {
            extractCardCalled = true
        }

        assertFalse("Should not call extractCard for DONE card with embedding", extractCardCalled)
        assertFalse(executed)
    }

    @Test
    fun analyzeCardIfRequired_whenPendingOrFailed_callsExtractCard() = runTest {
        val pendingCard = createTestCard(id = 11L, status = ProcessingStatus.PENDING)
        var extractCardCalled = false

        val executedPending = analyzeCardIfRequired(pendingCard) {
            extractCardCalled = true
        }
        assertTrue("Should call extractCard for PENDING card", extractCardCalled)
        assertTrue(executedPending)

        extractCardCalled = false
        val failedCard = createTestCard(id = 12L, status = ProcessingStatus.FAILED)
        val executedFailed = analyzeCardIfRequired(failedCard) {
            extractCardCalled = true
        }
        assertTrue("Should call extractCard for FAILED card", extractCardCalled)
        assertTrue(executedFailed)
    }
}
