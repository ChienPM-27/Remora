package com.remora.resurfacing

import com.remora.ai.ApiException
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.StoredEmbedding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ResurfacingManagerTest {
    private class Fixture {
        val store = ResurfacingTestStore()
        val source = card(1000)
        val candidate = card(101)
        var selections = 0
        var evaluations = 0
        var failure: Exception? = null
        var items = emptyList<ResurfacedItem>()
        var onEvaluate: suspend () -> Unit = {}
        val manager = ResurfacingManager(store, store,
            selectCandidates = { source ->
                selections++
                store.getCandidatesExcluding(source.id).map { it.toDomainModel() }
            },
            evaluateBatch = { _, _ ->
                evaluations++
                onEvaluate()
                failure?.let { throw it }
                items
            })

        init { store.put(source); store.put(candidate.copy(resurfacingStatus = "DONE")) }

        fun link(card: MemoryCard = candidate.toDomainModel()) = ResurfacedItem(
            card, ResurfaceGroup.COMPLEMENTARY, ResurfaceRelation.EXPLANATION,
            reason = "The earlier proof explains this exercise",
            benefit = "Use the proof to choose the correct formula", confidence = 0.92f)

        companion object {
            fun card(id: Long) = MemoryCardEntity(
                id = id, imageUri = "file:///owned/$id.png", createdAt = id,
                status = "DONE", extractionComplete = true, attemptCount = 2,
                title = "Original title $id", summary = "Retained summary", ocrText = "Retained OCR",
                userIntent = "Original intent", embedding = StoredEmbedding.encode(FloatArray(768) { if (it == 0) 1f else 0f }))
        }
    }

    @Test
    fun noCandidatesCheckpointsAnEmptyBatchWithoutCallingAi() = runTest {
        val f = Fixture()
        f.store.deleteCard(f.candidate.id)
        val first = f.manager.runResurfacingPipeline(f.source.id)
        val repeated = f.manager.runResurfacingPipeline(f.source.id)

        assertEquals(ResurfacingOutcome.COMPLETE, first.outcome)
        assertTrue(first.completedNow)
        assertTrue(first.items.isEmpty())
        assertFalse(repeated.completedNow)
        assertEquals(1, f.selections)
        assertEquals(0, f.evaluations)
        assertEquals("DONE", f.store.getCardById(f.source.id)?.resurfacingStatus)
    }

    @Test
    fun aValidNegativeEvaluationIsCachedAcrossManagerInstances() = runTest {
        val f = Fixture()
        val first = f.manager.runResurfacingPipeline(f.source.id)
        val restarted = ResurfacingManager(f.store, f.store,
            selectCandidates = { error("A completed empty batch must be reused") },
            evaluateBatch = { _, _ -> error("No repeated AI call") })

        assertTrue(first.completedNow)
        assertTrue(restarted.runResurfacingPipeline(f.source.id).items.isEmpty())
        assertEquals(1, f.evaluations)
        assertEquals(1, f.store.getCardById(f.source.id)?.resurfacingAttemptCount)
    }

    @Test
    fun successfulLinksAndTheirCheckpointAreReusedWithoutChangingExtraction() = runTest {
        val f = Fixture()
        f.items = listOf(f.link())
        val first = f.manager.runResurfacingPipeline(f.source.id)
        val repeated = f.manager.runResurfacingPipeline(f.source.id)

        assertEquals(f.items, first.items)
        assertEquals(first.items, repeated.items)
        assertTrue(first.completedNow)
        assertFalse(repeated.completedNow)
        assertEquals(1, f.evaluations)
        assertEquals(1, f.store.results.value.size)
        assertEquals(f.source.copy(resurfacingStatus = "DONE", resurfacingAttemptCount = 1), f.store.getCardById(f.source.id))
    }

    @Test
    fun offlineFailureStopsAfterThreeAttemptsAndManualRetryOnlyResetsConnections() = runTest {
        val f = Fixture()
        f.failure = IOException("Private upstream details")
        repeat(2) { assertEquals(ResurfacingOutcome.RETRY, f.manager.runResurfacingPipeline(f.source.id).outcome) }
        assertEquals(ResurfacingOutcome.FAILED, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals(ResurfacingOutcome.FAILED, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals(3, f.evaluations)
        assertEquals(f.source.copy(resurfacingStatus = "FAILED", resurfacingAttemptCount = 3), f.store.getCardById(f.source.id))

        f.store.resetFailed(f.source.id)
        f.failure = null
        f.items = listOf(f.link())
        assertEquals(ResurfacingOutcome.COMPLETE, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals(4, f.evaluations)
        assertEquals(f.source.copy(resurfacingStatus = "DONE", resurfacingAttemptCount = 1), f.store.getCardById(f.source.id))
    }

    @Test
    fun rateLimitsRemainPendingUntilTheRetryBudgetIsExhausted() = runTest {
        val f = Fixture()
        f.failure = ApiException(429, "HTTP_429")
        assertEquals(ResurfacingOutcome.RETRY, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals("PENDING", f.store.getCardById(f.source.id)?.resurfacingStatus)
        f.manager.runResurfacingPipeline(f.source.id)
        assertEquals(ResurfacingOutcome.FAILED, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals(3, f.evaluations)
        assertEquals("DONE", f.store.getCardById(f.source.id)?.status)
    }

    @Test
    fun nonRetryableAuthorizationFailureDoesNotLoop() = runTest {
        val f = Fixture()
        f.failure = ApiException(401, "HTTP_401")
        repeat(2) { assertEquals(ResurfacingOutcome.FAILED, f.manager.runResurfacingPipeline(f.source.id).outcome) }
        assertEquals(1, f.evaluations)
    }

    @Test
    fun cancellationPropagatesWithoutWritingResultsOrFailure() = runTest {
        val f = Fixture()
        f.failure = CancellationException("Cancelled")
        try {
            f.manager.runResurfacingPipeline(f.source.id)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }

        assertEquals("PENDING", f.store.getCardById(f.source.id)?.resurfacingStatus)
        assertEquals(1, f.store.getCardById(f.source.id)?.resurfacingAttemptCount)
        assertTrue(f.store.results.value.isEmpty())
    }

    @Test
    fun sourceDeletionDuringEvaluationCannotRestoreTheCardOrItsLinks() = runTest {
        val f = Fixture()
        f.items = listOf(f.link())
        f.onEvaluate = { f.store.deleteCard(f.source.id) }
        val result = f.manager.runResurfacingPipeline(f.source.id)

        assertEquals(ResurfacingOutcome.COMPLETE, result.outcome)
        assertFalse(result.completedNow)
        assertNull(f.store.getCardById(f.source.id))
        assertTrue(f.store.results.value.isEmpty())
    }

    @Test
    fun sourceDeletionDuringSelectionPreventsTheAiCall() = runTest {
        val f = Fixture()
        val manager = ResurfacingManager(f.store, f.store, selectCandidates = {
            f.store.deleteCard(f.source.id)
            listOf(f.candidate.toDomainModel())
        }, evaluateBatch = { _, _ -> error("Deleted sources must not reach AI") })

        assertEquals(ResurfacingOutcome.COMPLETE, manager.runResurfacingPipeline(f.source.id).outcome)
        assertNull(f.store.getCardById(f.source.id))
        assertTrue(f.store.results.value.isEmpty())
    }

    @Test
    fun candidateDeletionDuringEvaluationProducesACompletedEmptyBatch() = runTest {
        val f = Fixture()
        f.items = listOf(f.link())
        f.onEvaluate = { f.store.deleteCard(f.candidate.id) }
        val result = f.manager.runResurfacingPipeline(f.source.id)

        assertTrue(result.completedNow)
        assertTrue(result.items.isEmpty())
        assertTrue(f.store.results.value.isEmpty())
        assertEquals("DONE", f.store.getCardById(f.source.id)?.resurfacingStatus)
    }

    @Test
    fun lateFailureNeverOverwritesAnAlreadyCompletedCheckpoint() = runTest {
        val f = Fixture()
        f.onEvaluate = { f.store.completeBatch(f.source.id, emptyList()) }
        f.failure = IOException()

        assertEquals(ResurfacingOutcome.COMPLETE, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals("DONE", f.store.getCardById(f.source.id)?.resurfacingStatus)
    }

    @Test
    fun duplicateConcurrentInvocationsShareTheStoredOutcome() = runTest {
        val f = Fixture()
        val release = CompletableDeferred<Unit>()
        f.items = listOf(f.link())
        f.onEvaluate = { release.await() }
        val first = async { f.manager.runResurfacingPipeline(f.source.id) }
        val second = async { f.manager.runResurfacingPipeline(f.source.id) }
        runCurrent()
        assertEquals(1, f.evaluations)
        release.complete(Unit)

        assertEquals(first.await().items, second.await().items)
        assertEquals(1, f.evaluations)
        assertEquals(1, f.store.results.value.size)
    }

    @Test
    fun invalidResultsCannotReachPersistenceEvenIfAnEvaluatorReturnsThem() = runTest {
        val f = Fixture()
        f.items = listOf(f.link(), f.link().copy(group = ResurfaceGroup.SIMILAR, relation = ResurfaceRelation.SUPPLEMENT),
            f.link(f.source.toDomainModel()), f.link(Fixture.card(999).toDomainModel()),
            f.link().copy(confidence = Float.NaN))

        assertEquals(listOf(f.link()), f.manager.runResurfacingPipeline(f.source.id).items)
        assertEquals(1, f.store.results.value.size)
    }

    @Test
    fun missingUnprocessedAndFailedSourcesDoNotStartEvaluation() = runTest {
        val f = Fixture()
        assertEquals(ResurfacingOutcome.COMPLETE, f.manager.runResurfacingPipeline(999).outcome)
        f.store.put(f.source.copy(status = "PENDING"))
        assertEquals(ResurfacingOutcome.COMPLETE, f.manager.runResurfacingPipeline(f.source.id).outcome)
        f.store.put(f.source.copy(resurfacingStatus = "FAILED"))
        assertEquals(ResurfacingOutcome.FAILED, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals(0, f.selections)
        assertEquals(0, f.evaluations)
    }

    @Test
    fun interruptedWorkWithAnExhaustedBudgetDoesNotMakeAFourthCall() = runTest {
        val f = Fixture()
        f.store.put(f.source.copy(resurfacingAttemptCount = 3))

        assertEquals(ResurfacingOutcome.FAILED, f.manager.runResurfacingPipeline(f.source.id).outcome)
        assertEquals("FAILED", f.store.getCardById(f.source.id)?.resurfacingStatus)
        assertEquals(0, f.evaluations)
    }
}
