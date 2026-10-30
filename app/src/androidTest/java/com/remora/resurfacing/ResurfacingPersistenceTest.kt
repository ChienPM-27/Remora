package com.remora.resurfacing

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.remora.data.SaveCoordinator
import com.remora.data.local.RemoraDatabase
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import com.remora.data.local.entity.StoredEmbedding
import com.remora.work.CardWorkScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ResurfacingPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: RemoraDatabase

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, RemoraDatabase::class.java).build() }
    @After fun tearDown() { db.close() }

    private suspend fun card(id: Long): MemoryCardEntity {
        val card = MemoryCardEntity(id = id, imageUri = "content://images/$id", createdAt = id,
            title = "Retained title $id", summary = "Retained summary", ocrText = "Retained text",
            userIntent = "Retained note", status = "DONE", extractionComplete = true, attemptCount = 2,
            embedding = StoredEmbedding.encode(FloatArray(768) { if (it == 0) 1f else 0f }))
        db.memoryCardDao().insertCard(card)
        return card
    }

    private fun link(source: Long, candidate: Long) = ResurfacingResultEntity(
        newCardId = source, candidateCardId = candidate, group = "COMPLEMENTARY", relation = "EXPLANATION",
        reason = "The old proof explains this exercise", benefit = "Use the theorem to solve it", confidence = 0.92f)

    @Test
    fun aCompletedEmptyRoomCheckpointIsReusedByANewManager() = runBlocking {
        card(1)
        val first = ResurfacingManager(db.memoryCardDao(), db.resurfacingResultDao(),
            selectCandidates = { emptyList() }, evaluateBatch = { _, _ -> error("No candidates to send") })
        assertTrue(first.runResurfacingPipeline(1).completedNow)
        val restarted = ResurfacingManager(db.memoryCardDao(), db.resurfacingResultDao(),
            selectCandidates = { error("The Room checkpoint must survive a new manager") },
            evaluateBatch = { _, _ -> error("No duplicate evaluation") })

        assertTrue(restarted.runResurfacingPipeline(1).items.isEmpty())
        assertEquals("DONE", db.memoryCardDao().getCardById(1)?.resurfacingStatus)
        assertEquals(1, db.memoryCardDao().getCardById(1)?.resurfacingAttemptCount)
        assertTrue(db.resurfacingResultDao().getResultsForCard(1).isEmpty())
    }

    @Test
    fun replacingASourceBatchLeavesOtherSourcesAndExtractionUntouched() = runBlocking {
        val source = card(1)
        card(2); card(3)
        val dao = db.resurfacingResultDao()
        dao.insertResults(listOf(link(1, 2)))
        assertTrue(dao.completeBatch(2, listOf(link(2, 3))))
        assertTrue(dao.completeBatch(1, listOf(link(1, 3))))

        assertEquals(listOf(3L), dao.getResultsForCard(1).map { it.candidateCardId })
        assertEquals(listOf(3L), dao.getResultsForCard(2).map { it.candidateCardId })
        assertEquals(source.toDomainModel(), db.memoryCardDao().getCardById(1)?.toDomainModel())
        assertEquals("DONE", db.memoryCardDao().getCardById(1)?.resurfacingStatus)
        assertFalse(dao.completeBatch(1, emptyList()))
        assertEquals(1, dao.getResultsForCard(1).size)
    }

    @Test
    fun insertFailureRollsBackTheCheckpointAndThePreviousRowsTogether() = runBlocking {
        card(1); card(2); card(3)
        val dao = db.resurfacingResultDao()
        dao.insertResults(listOf(link(1, 2)))
        val previous = dao.getResultsForCard(1)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_test_result BEFORE INSERT ON resurfacing_result BEGIN SELECT RAISE(ABORT, 'TEST_INSERT_FAILURE'); END")

        assertTrue(runCatching { dao.completeBatch(1, listOf(link(1, 3))) }.isFailure)
        assertEquals("PENDING", db.memoryCardDao().getCardById(1)?.resurfacingStatus)
        assertEquals(previous, dao.getResultsForCard(1))
    }

    @Test
    fun deletedSourcesAndCandidatesCannotLeaveDanglingResults() = runBlocking {
        card(1); card(2)
        val dao = db.resurfacingResultDao()
        db.memoryCardDao().deleteCard(2)
        assertTrue(dao.completeBatch(1, listOf(link(1, 2), link(1, 1), link(1, 999))))
        assertTrue(dao.getResultsForCard(1).isEmpty())
        assertEquals("DONE", db.memoryCardDao().getCardById(1)?.resurfacingStatus)
        db.memoryCardDao().deleteCard(1)
        assertFalse(dao.completeBatch(1, listOf(link(1, 2))))
        assertNull(db.memoryCardDao().getCardById(1))
    }

    @Test
    fun manualRetrySchedulesOnlyConnectionsAndRetainsAllAnalysisFields() = runBlocking {
        val original = card(1).copy(resurfacingStatus = "FAILED", resurfacingAttemptCount = 3)
        db.memoryCardDao().updateCard(original)
        val analyzed = mutableListOf<Long>()
        val resurfaced = mutableListOf<Long>()
        val restarted = mutableListOf<Long>()
        val scheduler = object : CardWorkScheduler {
            override fun analyze(cardId: Long) { analyzed += cardId }
            override fun resurface(cardId: Long) { resurfaced += cardId }
            override fun retryResurface(cardId: Long) { restarted += cardId }
            override fun cancel(cardId: Long) = Unit
            override fun cancelAll() = Unit
        }
        val coordinator = SaveCoordinator(context, db, scheduler)

        coordinator.retryResurfacing(1)

        val latest = requireNotNull(db.memoryCardDao().getCardById(1))
        assertEquals(original.toDomainModel(), latest.toDomainModel())
        assertEquals("PENDING", latest.resurfacingStatus)
        assertEquals(0, latest.resurfacingAttemptCount)
        assertTrue(analyzed.isEmpty())
        assertTrue(resurfaced.isEmpty())
        assertEquals(listOf(1L), restarted)
        db.resurfacingResultDao().completeBatch(1, emptyList())
        coordinator.retryResurfacing(1)
        coordinator.retryResurfacing(999)
        db.memoryCardDao().deleteCard(1)
        coordinator.retryResurfacing(1)
        assertTrue(resurfaced.isEmpty())
        assertEquals(listOf(1L), restarted)
    }
}
