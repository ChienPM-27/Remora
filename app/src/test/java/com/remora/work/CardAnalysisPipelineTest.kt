package com.remora.work

import com.remora.ai.model.ExtractCardResponseDto
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CardAnalysisPipelineTest {
    private class Fixture {
        var card: MemoryCardEntity? = MemoryCardEntity(id = 1, imageUri = "file://owned", createdAt = 1, userIntent = "My intent")
        var extractions = 0
        var embeddings = 0
        var embeddingFailure: Exception? = null
        var deleteDuringExtraction = false
        val pipeline = CardAnalysisPipeline(
            load = { card }, persist = { value -> if (card != null) card = value },
            extract = {
                extractions++
                if (deleteDuringExtraction) card = null
                ExtractCardResponseDto("Title", "Summary", ocr_text = "Original text")
            }, embed = { text ->
                assertTrue(text.startsWith("My intent"))
                embeddings++
                embeddingFailure?.let { throw it }
                FloatArray(768) { if (it == 0) 1f else 0f }
            }
        )
    }

    @Test
    fun failedEmbeddingResumesWithoutUploadingAgain() = runTest {
        val f = Fixture()
        f.embeddingFailure = IOException("sensitive detail")
        assertEquals(AnalysisOutcome.RETRY, f.pipeline.run(1))
        assertTrue(requireNotNull(f.card).extractionComplete)
        assertEquals("Original text", f.card?.ocrText)
        assertEquals("NETWORK_UNAVAILABLE", f.card?.errorCode)
        f.embeddingFailure = null
        assertEquals(AnalysisOutcome.COMPLETE, f.pipeline.run(1))
        assertEquals("DONE", f.card?.status)
        assertEquals(1, f.extractions)
        f.pipeline.run(1)
        assertEquals(2, f.embeddings)
    }

    @Test
    fun cancellationKeepsTheSuccessfulCheckpoint() = runTest {
        val f = Fixture()
        f.embeddingFailure = CancellationException("cancelled")
        try { f.pipeline.run(1); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals("PENDING", f.card?.status)
        assertTrue(requireNotNull(f.card).extractionComplete)
        assertNull(f.card?.errorCode)
    }

    @Test
    fun retryBudgetStopsAtThreeAttempts() = runTest {
        val f = Fixture()
        f.embeddingFailure = IOException()
        repeat(2) { assertEquals(AnalysisOutcome.RETRY, f.pipeline.run(1)) }
        assertEquals(AnalysisOutcome.FAILED, f.pipeline.run(1))
        f.pipeline.run(1)
        assertEquals(3, f.embeddings)
        assertEquals(1, f.extractions)
        assertEquals("FAILED", f.card?.status)
    }

    @Test
    fun deletedCardsAreNeverRecreatedOrEmbedded() = runTest {
        val f = Fixture()
        f.deleteDuringExtraction = true
        f.pipeline.run(1)
        assertNull(f.card)
        assertEquals(0, f.embeddings)
        f.pipeline.run(1)
        assertEquals(1, f.extractions)
    }
}
