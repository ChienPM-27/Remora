package com.remora.resurfacing

import com.remora.ai.AiGatewayClient
import com.remora.ai.AiSession
import com.remora.ai.MemorySessionStore
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class Stage2BatchEvaluatorTest {
    private fun card(id: Long) = MemoryCard(
        id = id, imageUri = "file:///private/$id.png", createdAt = id,
        title = "Theorem $id", summary = "A concise summary", userIntent = "Review the proof",
        ocrText = "PRIVATE_OCR_MARKER", status = ProcessingStatus.DONE,
        embedding = FloatArray(768) { if (it == 0) 1f else 0f })

    private fun client(responseBody: String, calls: AtomicInteger, payload: AtomicReference<String> = AtomicReference()): AiGatewayClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            assertEquals("/v1/ai/resurface-batch", chain.request().url.encodedPath)
            payload.set(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Test")
                .body(responseBody.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return AiGatewayClient("http://localhost/v1", http,
            MemorySessionStore(AiSession("http://localhost/v1", "test-token", Long.MAX_VALUE)))
    }

    private fun result(id: String, group: String = "COMPLEMENTARY", relation: String = "EXPLANATION", confidence: String = "0.92") =
        """{"candidate_id":$id,"group":"$group","relation":"$relation","new_info":"An earlier proof","reason":"Explains this exercise","benefit":"Use the theorem to solve it","confidence":$confidence}"""

    @Test
    fun oneTextOnlyBatchRejectsUnknownRepeatedAndInvalidCandidateResults() = runTest {
        val calls = AtomicInteger()
        val payload = AtomicReference<String>()
        val rows = listOf(
            result("101"), result("999"), result("\"102\""), result("102.0"),
            result("101", "ACTIONABLE", "CONTRADICTION"), result("102", confidence = "1.01"),
            result("102", confidence = "1e39"), result("102", confidence = "0.80"),
            result("102", "SIMILAR", "EXPLANATION"), result("102", "INVALID"),
            result("102", "SIMILAR", "SUPPLEMENT")
        )
        val evaluator = Stage2BatchEvaluator(client("""{"results":[${rows.joinToString(",") }]}""", calls, payload))
        val items = evaluator.evaluateBatch(card(1000), listOf(card(101), card(102)))

        assertEquals(listOf(101L, 102L), items.map { it.candidateCard.id })
        assertEquals(1, calls.get())
        val request = Json.parseToJsonElement(payload.get()).jsonObject
        assertEquals(1000L, request.getValue("new_card").jsonObject.getValue("candidate_id").jsonPrimitive.long)
        assertEquals(listOf(101L, 102L), request.getValue("candidates").jsonArray.map {
            it.jsonObject.getValue("candidate_id").jsonPrimitive.long
        })
        listOf("ocr_text", "PRIVATE_OCR_MARKER", "image_uri", "embedding", "file://").forEach {
            assertFalse("The batch must contain only concise text metadata", payload.get().contains(it))
        }
    }

    @Test
    fun aRealEmptyHttpResponseIsCheckpointedAndNeverRequestedAgain() = runTest {
        val store = ResurfacingTestStore()
        store.put(MemoryCardEntity.fromDomainModel(card(1000)).copy(extractionComplete = true))
        store.put(MemoryCardEntity.fromDomainModel(card(101)).copy(extractionComplete = true, resurfacingStatus = "DONE"))
        val calls = AtomicInteger()
        val manager = ResurfacingManager(store, store, Stage1CandidateSelector(store),
            Stage2BatchEvaluator(client("""{"results":[]}""", calls)))

        assertTrue(manager.runResurfacingPipeline(1000).completedNow)
        assertFalse(manager.runResurfacingPipeline(1000).completedNow)
        assertEquals(1, calls.get())
        assertEquals("DONE", store.getCardById(1000)?.resurfacingStatus)
        assertTrue(store.results.value.isEmpty())
    }

    @Test
    fun missingResultsFailWithBoundedRetryWhileExtractionSurvives() = runTest {
        val store = ResurfacingTestStore()
        val source = MemoryCardEntity.fromDomainModel(card(1000)).copy(extractionComplete = true)
        store.put(source)
        store.put(MemoryCardEntity.fromDomainModel(card(101)))
        val calls = AtomicInteger()
        val manager = ResurfacingManager(store, store, Stage1CandidateSelector(store), Stage2BatchEvaluator(client("{}", calls)))

        repeat(2) { assertEquals(ResurfacingOutcome.RETRY, manager.runResurfacingPipeline(1000).outcome) }
        assertEquals(ResurfacingOutcome.FAILED, manager.runResurfacingPipeline(1000).outcome)
        manager.runResurfacingPipeline(1000)
        assertEquals(3, calls.get())
        assertEquals(source.copy(resurfacingStatus = "FAILED", resurfacingAttemptCount = 3), store.getCardById(1000))
        assertTrue(store.results.value.isEmpty())
    }
}
