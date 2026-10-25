package com.remora.resurfacing

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.core.model.entity.ResurfaceGroup
import com.remora.core.model.entity.ResurfaceRelation
import com.remora.core.model.resurface.ResurfacedItem
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ResurfacingFlowTest {

    @Test
    fun testResurfaceResultEntityMapping() {
        val candidate = MemoryCard(
            id = 101,
            imageUri = "content://media/101",
            createdAt = 1000L,
            userIntent = "Bài tập Thales",
            title = "Tính độ dài đoạn thẳng bằng Thales",
            summary = "Áp dụng định lý Thales để tính cạnh tương ứng",
            tags = CardTags("Hình học", listOf("Thales")),
            contentType = ContentType.PROBLEM,
            ocrText = "Tính DE biết BC = 10",
            embedding = FloatArray(768) { 0.1f },
            status = ProcessingStatus.DONE
        )

        val item = ResurfacedItem(
            candidateCard = candidate,
            group = ResurfaceGroup.COMPLEMENTARY,
            relation = ResurfaceRelation.EXPLANATION,
            newInfo = "Lý thuyết bổ trợ cho bài tập",
            benefit = "Ôn lại phương pháp tính",
            reason = "Cùng chủ đề Thales",
            confidence = 0.92f
        )

        val entity = ResurfacingResultEntity(
            newCardId = 999L,
            candidateCardId = item.candidateCard.id,
            group = item.group.name,
            relation = item.relation.name,
            newInfo = item.newInfo,
            benefit = item.benefit,
            reason = item.reason,
            confidence = item.confidence
        )

        assertEquals(999L, entity.newCardId)
        assertEquals(101L, entity.candidateCardId)
        assertEquals("COMPLEMENTARY", entity.group)
        assertEquals("EXPLANATION", entity.relation)
        assertTrue(entity.confidence > 0.80f)
    }

    @Test
    fun testResurfacingManagerDeletesOldResultsBeforeInsert() = runTest {
        val callOrder = mutableListOf<String>()
        val storedResults = mutableListOf<ResurfacingResultEntity>()

        // Pre-populate with existing results for card 999 to simulate retry/re-run
        storedResults.add(
            ResurfacingResultEntity(
                id = 1L,
                newCardId = 999L,
                candidateCardId = 50L,
                group = "SIMILAR",
                relation = "DUPLICATE",
                confidence = 0.90f
            )
        )

        val fakeResurfaceDao = object : ResurfacingResultDao {
            override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> =
                flowOf(storedResults.filter { it.newCardId == newCardId })

            override fun getLatestSourceCardIdFlow(): Flow<Long?> = flowOf(999L)
            override suspend fun getLatestSourceCardId(): Long? = 999L

            override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> =
                flowOf(storedResults)

            override suspend fun insertResults(results: List<ResurfacingResultEntity>) {
                callOrder.add("insert")
                storedResults.addAll(results)
            }

            override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> =
                storedResults.filter { it.newCardId == newCardId }

            override suspend fun deleteResultsForCard(newCardId: Long) {
                callOrder.add("delete")
                storedResults.removeAll { it.newCardId == newCardId }
            }

            override suspend fun deleteAllResults() {
                storedResults.clear()
            }
        }

        val fakeMemoryCardDao = object : MemoryCardDao {
            override suspend fun insertCard(card: MemoryCardEntity): Long = 1L
            override suspend fun updateCard(card: MemoryCardEntity) {}
            override suspend fun deleteCard(id: Long) {}
            override suspend fun getCardById(id: Long): MemoryCardEntity? = null
            override suspend fun getCardByImageUri(imageUri: String): MemoryCardEntity? = null
            override suspend fun countCardsByImageUri(imageUri: String): Int = 0
            override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = flowOf(emptyList())
            override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = emptyList()
            override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = emptyList()
            override suspend fun getCardCount(): Int = 0
            override suspend fun getDoneCardCount(): Int = 0
            override suspend fun deleteAllCards() {}
        }

        val newCard = MemoryCard(
            id = 999L,
            imageUri = "content://media/999",
            title = "New Card",
            status = ProcessingStatus.DONE,
            embedding = FloatArray(768) { 0.1f }
        )

        val candidateCard = MemoryCard(
            id = 101L,
            imageUri = "content://media/101",
            title = "Candidate Card",
            status = ProcessingStatus.DONE,
            embedding = FloatArray(768) { 0.1f }
        )

        val fakeSelector = object : Stage1CandidateSelector(fakeMemoryCardDao) {
            override suspend fun selectTopCandidates(newCard: MemoryCard, topN: Int): List<MemoryCard> {
                return listOf(candidateCard)
            }
        }

        val fakeEvaluator = object : Stage2BatchEvaluator(com.remora.ai.AiGatewayClient("http://localhost")) {
            override suspend fun evaluateBatch(
                newCard: MemoryCard,
                candidates: List<MemoryCard>
            ): List<ResurfacedItem> {
                return listOf(
                    ResurfacedItem(
                        candidateCard = candidateCard,
                        group = ResurfaceGroup.ACTIONABLE,
                        relation = ResurfaceRelation.SUPPLEMENT,
                        newInfo = "Updated info",
                        benefit = "Updated benefit",
                        reason = "Updated reason",
                        confidence = 0.95f
                    )
                )
            }
        }

        val manager = ResurfacingManager(
            memoryCardDao = fakeMemoryCardDao,
            resurfacingResultDao = fakeResurfaceDao,
            stage1Selector = fakeSelector,
            stage2Evaluator = fakeEvaluator
        )

        val results = manager.runResurfacingPipeline(newCard)

        assertEquals(1, results.size)
        // Verify delete was called before insert:
        assertEquals(listOf("delete", "insert"), callOrder)
        // Verify old result (candidateCardId = 50) was deleted and only new result (101) exists
        assertEquals(1, storedResults.size)
        assertEquals(101L, storedResults[0].candidateCardId)
        assertEquals(999L, storedResults[0].newCardId)
    }
}
