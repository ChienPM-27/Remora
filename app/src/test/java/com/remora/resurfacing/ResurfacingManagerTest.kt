package com.remora.resurfacing

import com.remora.ai.AiGatewayClient
import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.dao.MemoryCardDao
import com.remora.data.local.dao.ResurfacingResultDao
import com.remora.data.local.entity.MemoryCardEntity
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * T040/T043: Resurfacing outcome contract and idempotent persistence.
 * - Service errors surface as Result.failure and never delete/overwrite stored rows.
 * - A valid empty batch response is Result.success(emptyList()) (negative case).
 * - Re-running the pipeline for a card with stored results makes no new LLM call.
 * - Persisted rows are replaced, not appended.
 */
class ResurfacingManagerTest {

    private class FakeMemoryCardDao(cards: List<MemoryCard>) : MemoryCardDao {
        private val store = MutableStateFlow(cards.map { MemoryCardEntity.fromDomainModel(it) }.toMutableList())

        override fun getAllCardsFlow(): Flow<List<MemoryCardEntity>> = store
        override suspend fun getCardById(id: Long): MemoryCardEntity? = store.value.firstOrNull { it.id == id }
        override suspend fun getCardByImageUri(imageUri: String): MemoryCardEntity? =
            store.value.firstOrNull { it.imageUri == imageUri }
        override suspend fun countCardsByImageUri(imageUri: String): Int =
            store.value.count { it.imageUri == imageUri }
        override suspend fun insertCard(card: MemoryCardEntity): Long {
            val id = (store.value.maxOfOrNull { it.id } ?: 0L) + 1L
            store.value = (store.value + card.copy(id = id)).toMutableList()
            return id
        }
        override suspend fun updateCard(card: MemoryCardEntity) {
            store.value = store.value.map { if (it.id == card.id) card else it }.toMutableList()
        }
        override suspend fun deleteCard(id: Long) {
            store.value = store.value.filterNot { it.id == id }.toMutableList()
        }
        override suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity> = store.value
        override suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity> =
            store.value.filter { it.id != excludeId }
        override suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity> = store.value
        override suspend fun getCardCount(): Int = store.value.size
        override suspend fun getDoneCardCount(): Int = store.value.size
        override suspend fun deleteAllCards() { store.value = mutableListOf() }
    }

    private class FakeResurfacingResultDao : ResurfacingResultDao {
        val rows = mutableListOf<ResurfacingResultEntity>()
        override suspend fun insertResults(results: List<ResurfacingResultEntity>) { rows += results }
        override fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>> =
            MutableStateFlow(rows.filter { it.newCardId == newCardId })
        override fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>> = MutableStateFlow(rows.take(20))
        override suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity> =
            rows.filter { it.newCardId == newCardId }
        override fun getLatestSourceCardIdFlow(): Flow<Long?> = MutableStateFlow(rows.maxByOrNull { it.id }?.newCardId)
        override suspend fun getLatestSourceCardId(): Long? = rows.maxByOrNull { it.id }?.newCardId
        override suspend fun deleteResultsForCard(newCardId: Long) { rows.removeAll { it.newCardId == newCardId } }
        override suspend fun deleteAllResults() { rows.clear() }
    }

    /** Serves login-demo and resurface-batch with canned status/body; counts LLM calls. */
    private class StubAiInterceptor(
        var resurfaceStatus: Int = 200,
        var resurfaceBody: String = """{"results":[]}"""
    ) : Interceptor {
        val llmCalls = AtomicInteger(0)
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            return when {
                req.url.encodedPath.endsWith("/auth/login-demo") -> respond(req, 200, """{"access_token":"test-token"}""")
                req.url.encodedPath.endsWith("/resurface-batch") -> {
                    llmCalls.incrementAndGet()
                    respond(req, resurfaceStatus, resurfaceBody)
                }
                else -> respond(req, 200, "{}")
            }
        }

        private fun respond(req: okhttp3.Request, code: Int, body: String): Response = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("stub")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun card(id: Long, title: String) = MemoryCard(
        id = id,
        imageUri = "content://media/$id",
        createdAt = 1_700_000_000_000L + id,
        title = title,
        summary = "Tóm tắt $title",
        tags = CardTags(subject = "Toán", topics = listOf("Đại số")),
        contentType = ContentType.THEORY,
        ocrText = "Nội dung OCR riêng tư $title",
        embedding = FloatArray(768) { ((id % 7) + 1) * 0.01f },
        status = ProcessingStatus.DONE
    )

    private fun manager(
        cardDao: FakeMemoryCardDao,
        resultDao: FakeResurfacingResultDao,
        stub: StubAiInterceptor
    ): ResurfacingManager {
        val client = AiGatewayClient(
            baseUrl = "http://localhost/v1",
            client = OkHttpClient.Builder().addInterceptor(stub).build(),
            demoUsername = "demo",
            demoPassword = "demo"
        )
        return ResurfacingManager(
            memoryCardDao = cardDao,
            resurfacingResultDao = resultDao,
            stage1Selector = Stage1CandidateSelector(cardDao),
            stage2Evaluator = Stage2BatchEvaluator(client)
        )
    }

    private val validBatchBody =
        """{"results":[{"candidate_id":2,"group":"SIMILAR","relation":"SUPPLEMENT",""" +
            """"new_info":"Bổ sung","benefit":"Ôn lại","reason":"Cùng chủ đề","confidence":0.9}]}"""

    @Test
    fun serviceErrorIsFailureAndWritesNothing() = runTest {
        val resultDao = FakeResurfacingResultDao()
        val stub = StubAiInterceptor(resurfaceStatus = 503, resurfaceBody = """{"code":"AI_PROCESSING_ERROR"}""")
        val mgr = manager(FakeMemoryCardDao(listOf(card(1, "Thẻ mới"), card(2, "Ứng viên"))), resultDao, stub)

        val outcome = mgr.runResurfacingPipeline(card(1, "Thẻ mới"))
        assertTrue("503 from AI service must surface as failure, not as a negative case", outcome.isFailure)
        assertEquals("No rows may be written on a service error", 0, resultDao.rows.size)
        assertEquals("The LLM was called exactly once for the failed attempt", 1, stub.llmCalls.get())
    }

    @Test
    fun emptyBatchResponseIsSuccessNegativeCase() = runTest {
        val resultDao = FakeResurfacingResultDao()
        val stub = StubAiInterceptor(resurfaceStatus = 200, resurfaceBody = """{"results":[]}""")
        val mgr = manager(FakeMemoryCardDao(listOf(card(1, "Thẻ mới"), card(2, "Không liên quan"))), resultDao, stub)

        val outcome = mgr.runResurfacingPipeline(card(1, "Thẻ mới"))
        assertTrue("Empty batch is a valid negative case, not an error", outcome.isSuccess)
        assertEquals(0, outcome.getOrThrow().size)
        assertEquals("Negative case stores nothing", 0, resultDao.rows.size)
    }

    @Test
    fun rerunWithStoredResultsMakesNoLlmCall() = runTest {
        val cardDao = FakeMemoryCardDao(listOf(card(1, "Thẻ mới"), card(2, "Ứng viên")))
        val resultDao = FakeResurfacingResultDao()
        resultDao.rows += ResurfacingResultEntity(
            newCardId = 1, candidateCardId = 2, group = "SIMILAR", relation = "SUPPLEMENT",
            newInfo = "i", benefit = "b", reason = "r", confidence = 0.9f
        )
        val stub = StubAiInterceptor(resurfaceStatus = 200, resurfaceBody = validBatchBody)
        val mgr = manager(cardDao, resultDao, stub)

        val outcome = mgr.runResurfacingPipeline(card(1, "Thẻ mới"))
        assertTrue(outcome.isSuccess)
        assertEquals("Cached results returned", 1, outcome.getOrThrow().size)
        assertEquals("Cost rule: no new LLM call when a result is already stored", 0, stub.llmCalls.get())
    }

    @Test
    fun persistUsesReplacementNotAppend() = runTest {
        val cardDao = FakeMemoryCardDao(listOf(card(1, "Thẻ mới"), card(2, "Ứng viên")))
        val resultDao = FakeResurfacingResultDao()
        val stub = StubAiInterceptor(resurfaceStatus = 200, resurfaceBody = validBatchBody)
        val mgr = manager(cardDao, resultDao, stub)

        val first = mgr.runResurfacingPipeline(card(1, "Thẻ mới"))
        assertTrue(first.isSuccess)
        assertEquals(1, resultDao.rows.size)
        assertEquals(1, stub.llmCalls.get())

        // User-triggered retry after a fresh analysis (stored rows cleared by re-analysis flow).
        resultDao.deleteResultsForCard(1)
        val second = mgr.runResurfacingPipeline(card(1, "Thẻ mới"))
        assertTrue(second.isSuccess)
        assertEquals("Rows are replaced, never appended", 1, resultDao.rows.size)
        assertEquals("Second run performed a new LLM call", 2, stub.llmCalls.get())
    }
}
